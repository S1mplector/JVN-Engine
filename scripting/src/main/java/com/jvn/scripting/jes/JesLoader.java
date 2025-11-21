package com.jvn.scripting.jes;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.CharacterEntity2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.core.scene2d.SpriteSheet;
import com.jvn.core.scene2d.TileMap2D;
import com.jvn.scripting.jes.ast.JesAst;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.scripting.jes.runtime.PhysicsBodyEntity2D;

public class JesLoader {
  public static JesScene2D load(InputStream in) throws Exception {
    List<JesToken> toks = JesTokenizer.tokenize(in);
    JesAst.Program prog = new JesParser(toks).parseProgram();
    if (prog.scenes.isEmpty()) throw new IllegalArgumentException("No scene defined");
    JesAst.SceneDecl s = prog.scenes.get(0);
    return buildScene(s);
  }

  public static JesScene2D load(String code) throws Exception {
    java.io.InputStream in = new java.io.ByteArrayInputStream(
      (code == null ? "" : code).getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );
    return load(in);
  }

  private static JesScene2D buildScene(JesAst.SceneDecl s) {
    JesScene2D scene = new JesScene2D();

    // Build tileset lookup
    Map<String, JesAst.TilesetDecl> tilesets = new HashMap<>();
    for (JesAst.TilesetDecl t : s.tilesets) {
      if (t != null && t.name != null) tilesets.put(t.name, t);
    }

    // Build tilemap layers from the first map, if any, and expose tile size
    final double[] mapTileW = new double[]{1.0};
    final double[] mapTileH = new double[]{1.0};
    final boolean[] hasMapInfo = new boolean[]{false};
    if (!s.maps.isEmpty() && !tilesets.isEmpty()) {
      JesAst.MapDecl m = s.maps.get(0);
      String tilesetName = str(m.props, "tileset", null);
      JesAst.TilesetDecl ts = tilesets.get(tilesetName);
      if (ts != null) {
        String image = str(ts.props, "image", null);
        int tileW = (int) num(ts.props, "tileW", 16);
        int tileH = (int) num(ts.props, "tileH", 16);
        int cols = (int) num(ts.props, "cols", 8);
        SpriteSheet sheet = new SpriteSheet(image, tileW, tileH, cols);

        int mapCols = (int) num(m.props, "width", 10);
        int mapRows = (int) num(m.props, "height", 10);
        double drawTileW = num(m.props, "tileW", tileW);
        double drawTileH = num(m.props, "tileH", tileH);
        mapTileW[0] = drawTileW;
        mapTileH[0] = drawTileH;
        hasMapInfo[0] = true;

        for (JesAst.MapLayerDecl l : m.layers) {
          TileMap2D tilemap = new TileMap2D(sheet, mapCols, mapRows, drawTileW, drawTileH);
          String dataPath = str(l.props, "data", null);
          loadLayerIntoTilemap(tilemap, dataPath);
          scene.add(tilemap);
          if (bool(l.props, "collision", false)) {
            tilemap.buildStaticColliders(scene.getWorld());
          }
        }
      }
    }

    for (JesAst.InputBinding b : s.bindings) {
      scene.addBinding(b.key, b.action, b.props);
    }
    scene.setTimeline(s.timeline);
    for (JesAst.EntityDecl e : s.entities) {
      e.components.forEach(c -> {
        switch (c.type) {
          case "Panel2D" -> {
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double w = num(c, "w", 1);
            double h = num(c, "h", 1);
            Panel2D p = new Panel2D(w, h);
            Object fill = c.props.get("fill");
            if (fill instanceof double[] arr) p.setFill(arr[0], arr[1], arr[2], arr[3]);
            p.setPosition(x, y);
            scene.add(p);
            scene.registerEntity(e.name, p);
          }
          case "Sprite2D" -> {
            String image = str(c, "image", null);
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double w = num(c, "w", 64);
            double h = num(c, "h", 64);
            double alpha = num(c, "alpha", 1.0);
            double ox = num(c, "originX", 0.0);
            double oy = num(c, "originY", 0.0);
            Sprite2D sp;
            boolean hasRegion = has(c, "sx") || has(c, "sw");
            if (hasRegion) {
              double sx = num(c, "sx", 0);
              double sy = num(c, "sy", 0);
              double sw = num(c, "sw", w);
              double sh = num(c, "sh", h);
              double dw = num(c, "dw", w);
              double dh = num(c, "dh", h);
              sp = new Sprite2D(image, dw, dh).region(image, sx, sy, sw, sh, dw, dh);
            } else {
              sp = new Sprite2D(image, w, h);
            }
            sp.setPosition(x, y);
            sp.setAlpha(alpha);
            sp.setOrigin(ox, oy);
            scene.add(sp);
            scene.registerEntity(e.name, sp);
          }
          case "Label2D" -> {
            String text = str(c, "text", "");
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double size = num(c, "size", 16);
            boolean bold = bool(c, "bold", false);
            Label2D lbl = new Label2D(text);
            lbl.setPosition(x, y);
            Object fill = c.props.get("color");
            if (fill instanceof double[] arr) lbl.setColor(arr[0], arr[1], arr[2], arr[3]);
            lbl.setFont("SansSerif", size, bold);
            String align = str(c, "align", "LEFT");
            try { lbl.setAlign(Label2D.Align.valueOf(align.toUpperCase())); } catch (Exception ignored) {}
            scene.add(lbl);
            scene.registerEntity(e.name, lbl);
          }
          case "ParticleEmitter2D" -> {
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double emissionRate = num(c, "emissionRate", 10);
            double minLife = num(c, "minLife", 1.0);
            double maxLife = num(c, "maxLife", 3.0);
            double minSize = num(c, "minSize", 2.0);
            double maxSize = num(c, "maxSize", 8.0);
            double endSizeScale = num(c, "endSizeScale", 0.1);
            double minSpeed = num(c, "minSpeed", 50);
            double maxSpeed = num(c, "maxSpeed", 150);
            double minAngle = num(c, "minAngle", 0);
            double maxAngle = num(c, "maxAngle", 360);
            double gravityY = num(c, "gravityY", 100);
            String texture = str(c, "texture", null);
            boolean additive = bool(c, "additive", true);
            ParticleEmitter2D emitter = new ParticleEmitter2D();
            emitter.setPosition(x, y);
            emitter.setEmissionRate(emissionRate);
            emitter.setLifeRange(minLife, maxLife);
            emitter.setSizeRange(minSize, maxSize, endSizeScale);
            emitter.setSpeedRange(minSpeed, maxSpeed);
            emitter.setAngleRange(minAngle, maxAngle);
            emitter.setGravity(gravityY);
            if (texture != null) emitter.setTexture(texture);
            emitter.setAdditive(additive);
            Object startColor = c.props.get("startColor"); if (startColor instanceof double[] arr1) emitter.setStartColor(arr1[0], arr1[1], arr1[2], arr1[3]);
            Object endColor = c.props.get("endColor"); if (endColor instanceof double[] arr2) emitter.setEndColor(arr2[0], arr2[1], arr2[2], arr2[3]);
            scene.add(emitter);
            scene.registerEntity(e.name, emitter);
          }
          case "PhysicsBody2D" -> {
            String shape = str(c, "shape", "circle").toLowerCase();
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double mass = num(c, "mass", 1);
            double restitution = num(c, "restitution", 0.2);
            boolean stat = bool(c, "static", false);
            boolean sensor = bool(c, "sensor", false);
            RigidBody2D body;
            if (shape.equals("box")) {
              double w = num(c, "w", 1);
              double h = num(c, "h", 1);
              body = RigidBody2D.box(x, y, w, h);
            } else {
              double r = num(c, "r", 0.5);
              body = RigidBody2D.circle(x, y, r);
            }
            body.setMass(mass);
            body.setRestitution(restitution);
            body.setStatic(stat);
            body.setSensor(sensor);
            double vx = num(c, "vx", 0); double vy = num(c, "vy", 0); body.setVelocity(vx, vy);
            scene.getWorld().addBody(body);
            PhysicsBodyEntity2D vis = new PhysicsBodyEntity2D(body);
            Object fill = c.props.get("color"); if (fill instanceof double[] arr) vis.setColor(arr[0], arr[1], arr[2], arr[3]);
            scene.add(vis);
            scene.registerEntity(e.name, vis);
          }
          case "Character2D" -> {
            String image = str(c, "spriteSheet", null);
            int frameW = (int) num(c, "frameW", 16);
            int frameH = (int) num(c, "frameH", 16);
            int cols = (int) num(c, "cols", 8);
            double drawW = num(c, "drawW", frameW);
            double drawH = num(c, "drawH", frameH);
            SpriteSheet sheet = new SpriteSheet(image, frameW, frameH, cols);
            CharacterEntity2D ch = new CharacterEntity2D(sheet, drawW, drawH);

            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            if (hasMapInfo[0] && (has(c, "startTileX") || has(c, "startTileY"))) {
              int tx = (int) num(c, "startTileX", 0);
              int ty = (int) num(c, "startTileY", 0);
              x = tx * mapTileW[0];
              y = ty * mapTileH[0];
            }
            ch.setPosition(x, y);

            double speed = num(c, "speed", 80.0);
            ch.setSpeed(speed);

            double ox = num(c, "originX", 0.5);
            double oy = num(c, "originY", 1.0);
            ch.setOrigin(ox, oy);

            String animSpec = str(c, "animations", null);
            ch.setAnimations(CharacterEntity2D.parseAnimations(animSpec));
            String startAnim = str(c, "startAnim", null);
            if (startAnim != null) ch.setCurrentAnimation(startAnim);

            double z = num(c, "z", 0.0);
            ch.setZ(z);

            scene.add(ch);
            scene.registerEntity(e.name, ch);
          }
          default -> {}
        }
      });
    }
    return scene;
  }

  private static double num(JesAst.ComponentDecl c, String key, double def) {
    Object v = c.props.get(key);
    return v instanceof Number n ? n.doubleValue() : def;
  }
  private static String str(JesAst.ComponentDecl c, String key, String def) {
    Object v = c.props.get(key);
    return v instanceof String s ? s : def;
  }
  private static boolean bool(JesAst.ComponentDecl c, String key, boolean def) {
    Object v = c.props.get(key);
    return v instanceof Boolean b ? b : def;
  }
  private static boolean has(JesAst.ComponentDecl c, String key) { return c.props.containsKey(key); }

  private static double num(Map<String,Object> props, String key, double def) {
    Object v = props.get(key);
    return v instanceof Number n ? n.doubleValue() : def;
  }

  private static String str(Map<String,Object> props, String key, String def) {
    Object v = props.get(key);
    return v instanceof String s ? s : def;
  }

  private static boolean bool(Map<String,Object> props, String key, boolean def) {
    Object v = props.get(key);
    return v instanceof Boolean b ? b : def;
  }

  private static void loadLayerIntoTilemap(TileMap2D tm, String path) {
    if (tm == null || path == null || path.isBlank()) return;
    try (InputStream in = JesLoader.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) return;
      try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        int y = 0;
        int rows = tm.getRows();
        int cols = tm.getCols();
        while ((line = br.readLine()) != null && y < rows) {
          String[] parts = line.split(",");
          for (int x = 0; x < parts.length && x < cols; x++) {
            String sVal = parts[x].trim();
            if (sVal.isEmpty()) continue;
            try {
              int idx = Integer.parseInt(sVal);
              tm.setTile(x, y, idx);
            } catch (NumberFormatException ignored) {}
          }
          y++;
        }
      }
    } catch (Exception ignored) {}
  }
}
