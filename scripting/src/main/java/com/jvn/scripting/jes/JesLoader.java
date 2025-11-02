package com.jvn.scripting.jes;

import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.scripting.jes.ast.JesAst;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.scripting.jes.runtime.PhysicsBodyEntity2D;

import java.io.InputStream;
import java.util.List;

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
}
