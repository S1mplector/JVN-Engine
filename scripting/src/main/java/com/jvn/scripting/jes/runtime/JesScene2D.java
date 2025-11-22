package com.jvn.scripting.jes.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.jvn.core.animation.Easing;
import com.jvn.core.input.Input;
import com.jvn.core.physics.PhysicsWorld2D;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.CharacterEntity2D;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.scene2d.TileMap2D;
import com.jvn.scripting.jes.ast.JesAst;

public class JesScene2D extends Scene2DBase {
  private final PhysicsWorld2D world = new PhysicsWorld2D();
  private boolean debug = false;
  private PhysicsDebugOverlay2D debugOverlay;
  private final List<Binding> bindings = new ArrayList<>();
  private final Map<String, Entity2D> named = new HashMap<>();
  private final Map<String, Consumer<Map<String,Object>>> callHandlers = new HashMap<>();

  private List<JesAst.TimelineAction> timeline = new ArrayList<>();
  private int tlIndex = 0;
  private double tlElapsedMs = 0;
  private final Map<Integer, ActionRuntime> actionState = new HashMap<>();
  private BiConsumer<String, Map<String,Object>> actionHandler;

  private String playerName;
  private double gridW = 16.0;
  private double gridH = 16.0;
  private String playerFacing = "down";
  private final List<TileMap2D> collisionTilemaps = new ArrayList<>();
  private static class TriggerLayer {
    final TileMap2D tilemap;
    final String call;
    final Map<String,Object> props;
    final String mapName;
    TriggerLayer(String mapName, TileMap2D tilemap, String call, Map<String,Object> props) {
      this.mapName = mapName;
      this.tilemap = tilemap;
      this.call = call;
      this.props = props;
    }
  }
  private final List<TriggerLayer> triggerLayers = new ArrayList<>();

  public static class Binding {
    public final String key;
    public final String action;
    public final Map<String,Object> props;
    public Binding(String key, String action, Map<String,Object> props) {
      this.key = key; this.action = action; this.props = props == null ? new HashMap<>() : props;
    }
  }
  private static class ActionRuntime {
    boolean started;
    double sx, sy, sRot, ssx, ssy;
    Easing.Type easing = Easing.Type.LINEAR;
    double sAlpha;
    double sZoom;
  }

  public PhysicsWorld2D getWorld() { return world; }
  public void addCollisionTilemap(TileMap2D tm) { if (tm != null) collisionTilemaps.add(tm); }
  public void addTriggerLayer(TileMap2D tm, String call, Map<String,Object> props) {
    addTriggerLayer(null, tm, call, props);
  }
  public void addTriggerLayer(String mapName, TileMap2D tm, String call, Map<String,Object> props) {
    if (tm == null || call == null || call.isBlank()) return;
    Map<String,Object> copy = (props == null) ? new HashMap<>() : new HashMap<>(props);
    triggerLayers.add(new TriggerLayer(mapName, tm, call, copy));
  }
  public void setDebug(boolean d) { this.debug = d; }
  public void addBinding(String key, String action, Map<String,Object> props) { bindings.add(new Binding(key, action, props)); }
  public void registerEntity(String name, Entity2D e) { if (name != null && !name.isBlank() && e != null && !named.containsKey(name)) named.put(name, e); }
  public void setTimeline(List<JesAst.TimelineAction> tl) { this.timeline = tl == null ? new ArrayList<>() : new ArrayList<>(tl); this.tlIndex = 0; this.tlElapsedMs = 0; this.actionState.clear(); }
  public java.util.Set<String> names() { return named.keySet(); }
  public Entity2D find(String name) { return named.get(name); }
  public Map<String, Entity2D> exportNamed() { return java.util.Collections.unmodifiableMap(new java.util.HashMap<>(named)); }
  public java.util.List<Binding> exportBindings() { return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(bindings)); }
  public java.util.List<JesAst.TimelineAction> exportTimeline() { return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(timeline)); }
  public void registerCall(String name, Consumer<Map<String,Object>> handler) { if (name != null && !name.isBlank() && handler != null) callHandlers.put(name, handler); }
  public void setActionHandler(BiConsumer<String, Map<String,Object>> handler) { this.actionHandler = handler; }
  public void invokeCall(String name, Map<String,Object> props) {
    if (name == null || name.isBlank()) return;
    Map<String,Object> actualProps = (props == null) ? new HashMap<>() : new HashMap<>(props);
    // Built-in map warp helper: can be triggered directly from triggers using triggerCall: "warpMap"
    if ("warpMap".equals(name)) {
      warpMap(actualProps);
    }
    Consumer<Map<String,Object>> h = callHandlers.get(name);
    if (h != null) {
      try { h.accept(actualProps); } catch (Exception ignored) {}
    } else if (actionHandler != null) {
      try { actionHandler.accept(name, actualProps); } catch (Exception ignored) {}
    }
  }
  public void setPlayerName(String name) { this.playerName = name; }
  public void setGridSize(double w, double h) { this.gridW = w; this.gridH = h; }
  public boolean rename(String oldName, String newName) {
    if (oldName == null || newName == null || newName.isBlank() || oldName.equals(newName)) return false;
    if (!named.containsKey(oldName) || named.containsKey(newName)) return false;
    Entity2D e = named.remove(oldName);
    if (e == null) return false;
    named.put(newName, e);
    return true;
  }
  public boolean removeEntity(String name) {
    if (name == null) return false;
    Entity2D e = named.remove(name);
    if (e != null) { remove(e); return true; }
    return false;
  }

  @Override
  public void update(long deltaMs) {
    super.update(deltaMs);
    world.step(deltaMs);

    Input in = getInput();
    if (in != null) {
      for (Binding b : bindings) {
        if (in.wasKeyPressed(b.key)) handleAction(b);
      }
    }

    updateTimeline(deltaMs);
  }

  @Override
  public void render(Blitter2D b, double width, double height) {
    super.render(b, width, height);
    // Optional physics debug could be drawn here if needed
  }

  private void updateTimeline(long deltaMs) {
    if (timeline == null || tlIndex >= timeline.size()) return;
    JesAst.TimelineAction a = timeline.get(tlIndex);
    switch (a.type) {
      case "wait" -> {
        double ms = toNum(a.props.get("ms"), 0);
        tlElapsedMs += deltaMs;
        if (tlElapsedMs >= ms) { tlIndex++; tlElapsedMs = 0; }
      }
      case "call" -> {
        Consumer<Map<String,Object>> h = callHandlers.get(a.target);
        if (h != null) {
          try { h.accept(a.props == null ? java.util.Collections.emptyMap() : a.props); } catch (Exception ignored) {}
        }
        tlIndex++;
        tlElapsedMs = 0;
      }
      case "move" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        double tx = toNum(a.props.get("x"), e.getX());
        double ty = toNum(a.props.get("y"), e.getY());
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) { 
          st.started = true; 
          st.sx = e.getX(); 
          st.sy = e.getY();
          String easingStr = toStr(a.props.get("easing"), "LINEAR");
          try { st.easing = Easing.Type.valueOf(easingStr.toUpperCase()); } catch (Exception ignored) {}
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = Easing.apply(st.easing, p);
        e.setPosition(st.sx + (tx - st.sx) * ep, st.sy + (ty - st.sy) * ep);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "walkToTile" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        if (gridW == 0 || gridH == 0) { tlIndex++; tlElapsedMs = 0; return; }
        double txTile = toNum(a.props.get("tx"), Double.NaN);
        double tyTile = toNum(a.props.get("ty"), Double.NaN);
        double tx;
        double ty;
        if (Double.isNaN(txTile) || Double.isNaN(tyTile)) {
          tx = toNum(a.props.get("x"), e.getX());
          ty = toNum(a.props.get("y"), e.getY());
        } else {
          tx = txTile * gridW;
          ty = tyTile * gridH;
        }
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sx = e.getX();
          st.sy = e.getY();
          String easingStr = toStr(a.props.get("easing"), "LINEAR");
          try { st.easing = Easing.Type.valueOf(easingStr.toUpperCase()); } catch (Exception ignored) {}
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = Easing.apply(st.easing, p);
        e.setPosition(st.sx + (tx - st.sx) * ep, st.sy + (ty - st.sy) * ep);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "rotate" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        double tdeg = toNum(a.props.get("deg"), e.getRotationDeg());
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) { 
          st.started = true; 
          st.sRot = e.getRotationDeg();
          String easingStr = toStr(a.props.get("easing"), "LINEAR");
          try { st.easing = Easing.Type.valueOf(easingStr.toUpperCase()); } catch (Exception ignored) {}
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = Easing.apply(st.easing, p);
        e.setRotationDeg(st.sRot + (tdeg - st.sRot) * ep);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "scale" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        double tsx = toNum(a.props.get("sx"), e.getScaleX());
        double tsy = toNum(a.props.get("sy"), e.getScaleY());
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) { 
          st.started = true; 
          st.ssx = e.getScaleX(); 
          st.ssy = e.getScaleY();
          String easingStr = toStr(a.props.get("easing"), "LINEAR");
          try { st.easing = Easing.Type.valueOf(easingStr.toUpperCase()); } catch (Exception ignored) {}
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = Easing.apply(st.easing, p);
        e.setScale(st.ssx + (tsx - st.ssx) * ep, st.ssy + (tsy - st.ssy) * ep);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "fade" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        double targetAlpha = toNum(a.props.get("alpha"), getAlpha(e));
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sAlpha = getAlpha(e);
          String easingStr = toStr(a.props.get("easing"), "LINEAR");
          try { st.easing = Easing.Type.valueOf(easingStr.toUpperCase()); } catch (Exception ignored) {}
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = Easing.apply(st.easing, p);
        double aVal = st.sAlpha + (targetAlpha - st.sAlpha) * ep;
        setAlpha(e, aVal);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "visible" -> {
        Entity2D e = named.get(a.target);
        if (e != null) {
          boolean vis = toBool(a.props.get("value"), true);
          e.setVisible(vis);
        }
        tlIndex++;
        tlElapsedMs = 0;
      }
      case "cameraMove" -> {
        com.jvn.core.graphics.Camera2D cam = getCamera();
        if (cam == null) { tlIndex++; tlElapsedMs = 0; return; }
        double tx = toNum(a.props.get("x"), cam.getX());
        double ty = toNum(a.props.get("y"), cam.getY());
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sx = cam.getX();
          st.sy = cam.getY();
          String easingStr = toStr(a.props.get("easing"), "LINEAR");
          try { st.easing = Easing.Type.valueOf(easingStr.toUpperCase()); } catch (Exception ignored) {}
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = Easing.apply(st.easing, p);
        double nx = st.sx + (tx - st.sx) * ep;
        double ny = st.sy + (ty - st.sy) * ep;
        cam.setPosition(nx, ny);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "cameraZoom" -> {
        com.jvn.core.graphics.Camera2D cam = getCamera();
        if (cam == null) { tlIndex++; tlElapsedMs = 0; return; }
        double tz = toNum(a.props.get("zoom"), cam.getZoom());
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sZoom = cam.getZoom();
          String easingStr = toStr(a.props.get("easing"), "LINEAR");
          try { st.easing = Easing.Type.valueOf(easingStr.toUpperCase()); } catch (Exception ignored) {}
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = Easing.apply(st.easing, p);
        double nz = st.sZoom + (tz - st.sZoom) * ep;
        cam.setZoom(nz);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "cameraShake" -> {
        com.jvn.core.graphics.Camera2D cam = getCamera();
        if (cam == null) { tlIndex++; tlElapsedMs = 0; return; }
        double ampX = toNum(a.props.get("ampX"), 16);
        double ampY = toNum(a.props.get("ampY"), 16);
        double dur = toNum(a.props.get("dur"), 300);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sx = cam.getX();
          st.sy = cam.getY();
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double intensity = 1.0 - p;
        double ox = (Math.random() * 2.0 - 1.0) * ampX * intensity;
        double oy = (Math.random() * 2.0 - 1.0) * ampY * intensity;
        cam.setPosition(st.sx + ox, st.sy + oy);
        if (p >= 1.0) {
          cam.setPosition(st.sx, st.sy);
          tlIndex++;
          tlElapsedMs = 0;
          actionState.remove(tlIndex-1);
        }
      }
      case "spawnCircle" -> {
        spawnCircle(a.props == null ? java.util.Collections.emptyMap() : a.props);
        tlIndex++;
        tlElapsedMs = 0;
      }
      case "spawnBox" -> {
        spawnBox(a.props == null ? java.util.Collections.emptyMap() : a.props);
        tlIndex++;
        tlElapsedMs = 0;
      }
      default -> { tlIndex++; tlElapsedMs = 0; }
    }
  }

  private void handleAction(Binding b) {
    boolean handled = switch (b.action) {
      case "toggleDebug" -> { toggleDebugOverlay(); yield true; }
      case "spawnCircle" -> { spawnCircle(b.props); yield true; }
      case "spawnBox" -> { spawnBox(b.props); yield true; }
      case "moveHero" -> { moveHero(b.props); yield true; }
      case "interact" -> { interact(); yield true; }
      default -> false;
    };
    if (!handled && actionHandler != null) {
      try { actionHandler.accept(b.action, b.props); } catch (Exception ignored) {}
    }
  }

  private void toggleDebugOverlay() {
    if (debugOverlay == null) {
      debugOverlay = new PhysicsDebugOverlay2D(this);
      add(debugOverlay);
    }
    debug = !debug;
    if (debugOverlay != null) debugOverlay.setVisible(debug);
  }

  private void spawnCircle(Map<String,Object> props) {
    Input in = getInput(); if (in == null) return;
    double x = toNum(props.get("x"), in.getMouseX());
    double y = toNum(props.get("y"), in.getMouseY());
    double r = toNum(props.get("r"), 10);
    double mass = toNum(props.get("mass"), 1);
    double rest = toNum(props.get("restitution"), 0.4);
    RigidBody2D body = RigidBody2D.circle(x, y, r);
    body.setMass(mass); body.setRestitution(rest);
    world.addBody(body);
    PhysicsBodyEntity2D vis = new PhysicsBodyEntity2D(body);
    add(vis);
  }
  
  private boolean isBlockedWorld(double x, double y) {
    if (collisionTilemaps.isEmpty() || gridW == 0 || gridH == 0) return false;
    int tx = (int) Math.floor(x / gridW);
    int ty = (int) Math.floor(y / gridH);
    return isBlockedTile(tx, ty);
  }

  private boolean isBlockedTile(int tx, int ty) {
    if (collisionTilemaps.isEmpty()) return false;
    for (TileMap2D tm : collisionTilemaps) {
      if (tx < 0 || ty < 0 || tx >= tm.getCols() || ty >= tm.getRows()) {
        return true;
      }
      if (tm.getTile(tx, ty) >= 0) return true;
    }
    return false;
  }

  private void checkTriggersAt(double x, double y) {
    if (triggerLayers.isEmpty() || gridW == 0 || gridH == 0) return;
    int tx = (int) Math.floor(x / gridW);
    int ty = (int) Math.floor(y / gridH);
    for (TriggerLayer tl : triggerLayers) {
      TileMap2D tm = tl.tilemap;
      if (tm == null) continue;
      int tile = tm.getTile(tx, ty);
      if (tile < 0) continue;
      Map<String,Object> props = new HashMap<>(tl.props);
      props.put("tileX", (double) tx);
      props.put("tileY", (double) ty);
      props.put("tile", (double) tile);
      if (tl.mapName != null && !tl.mapName.isBlank()) {
        props.put("map", tl.mapName);
      }
      invokeCall(tl.call, props);
    }
  }

  private void moveHero(Map<String,Object> props) {
    if (playerName == null || playerName.isBlank()) return;
    Entity2D e = named.get(playerName);
    if (e == null) return;
    String dir = toStr(props.get("dir"), "down");
    double dx = 0;
    double dy = 0;
    String dLower = dir == null ? "" : dir.toLowerCase();
    switch (dLower) {
      case "up" -> dy = -gridH;
      case "down" -> dy = gridH;
      case "left" -> dx = -gridW;
      case "right" -> dx = gridW;
      default -> {}
    }
    if (dx == 0 && dy == 0) return;
    if (!dLower.isEmpty()) playerFacing = dLower;
    double nx = e.getX() + dx;
    double ny = e.getY() + dy;
    if (isBlockedWorld(nx, ny)) return;
    e.setPosition(nx, ny);
    checkTriggersAt(nx, ny);
    if (e instanceof CharacterEntity2D ch) {
      String animName = switch (dLower) {
        case "up" -> "up";
        case "down" -> "down";
        case "left" -> "left";
        case "right" -> "right";
        default -> null;
      };
      if (animName != null) ch.setCurrentAnimation(animName);
    }
  }

  private void interact() {
    if (playerName == null || playerName.isBlank()) return;
    if (gridW == 0 || gridH == 0) return;
    Entity2D hero = named.get(playerName);
    if (hero == null) return;
    int heroTx = (int) Math.floor(hero.getX() / gridW);
    int heroTy = (int) Math.floor(hero.getY() / gridH);
    int tx = heroTx;
    int ty = heroTy;
    String d = playerFacing == null ? "down" : playerFacing;
    switch (d) {
      case "up" -> ty--;
      case "down" -> ty++;
      case "left" -> tx--;
      case "right" -> tx++;
      default -> {}
    }
    String npcName = null;
    CharacterEntity2D npcEntity = null;
    for (Map.Entry<String, Entity2D> entry : named.entrySet()) {
      String name = entry.getKey();
      if (name == null || name.equals(playerName)) continue;
      Entity2D ent = entry.getValue();
      if (!(ent instanceof CharacterEntity2D)) continue;
      int ex = (int) Math.floor(ent.getX() / gridW);
      int ey = (int) Math.floor(ent.getY() / gridH);
      if (ex == tx && ey == ty) {
        npcName = name;
        npcEntity = (CharacterEntity2D) ent;
        break;
      }
    }
    if (npcName != null) {
      Map<String,Object> props = new HashMap<>();
      props.put("npc", npcName);
      if (npcEntity != null) {
        String did = npcEntity.getDialogueId();
        if (did != null && !did.isBlank()) props.put("dialogueId", did);
      }
      invokeCall("interactNpc", props);
    }
  }

  private void warpMap(Map<String,Object> props) {
    if (playerName == null || playerName.isBlank()) return;
    if (gridW == 0 || gridH == 0) return;
    Entity2D hero = named.get(playerName);
    if (hero == null) return;

    double wx;
    double wy;

    // Prefer explicit target tile coordinates
    if (props.containsKey("toTileX") || props.containsKey("toTileY")) {
      double tx = toNum(props.get("toTileX"), hero.getX() / gridW);
      double ty = toNum(props.get("toTileY"), hero.getY() / gridH);
      wx = tx * gridW;
      wy = ty * gridH;
    } else if (props.containsKey("toX") || props.containsKey("toY")) {
      wx = toNum(props.get("toX"), hero.getX());
      wy = toNum(props.get("toY"), hero.getY());
    } else if (props.containsKey("tileX") || props.containsKey("tileY")) {
      double tx = toNum(props.get("tileX"), hero.getX() / gridW);
      double ty = toNum(props.get("tileY"), hero.getY() / gridH);
      wx = tx * gridW;
      wy = ty * gridH;
    } else {
      return; // no usable coordinates
    }

    hero.setPosition(wx, wy);
  }

  private void spawnBox(Map<String,Object> props) {
    Input in = getInput(); if (in == null) return;
    double x = toNum(props.get("x"), in.getMouseX());
    double y = toNum(props.get("y"), in.getMouseY());
    double w = toNum(props.get("w"), 40);
    double h = toNum(props.get("h"), 40);
    double mass = toNum(props.get("mass"), 1);
    double rest = toNum(props.get("restitution"), 0.2);
    RigidBody2D body = RigidBody2D.box(x, y, w, h);
    body.setMass(mass); body.setRestitution(rest);
    world.addBody(body);
    PhysicsBodyEntity2D vis = new PhysicsBodyEntity2D(body);
    add(vis);
  }

  private static double toNum(Object o, double def) {
    return o instanceof Number n ? n.doubleValue() : def;
  }
  
  private static String toStr(Object o, String def) {
    return o instanceof String s ? s : def;
  }

  private static boolean toBool(Object o, boolean def) {
    return o instanceof Boolean b ? b : def;
  }

  private static double getAlpha(Entity2D e) {
    if (e instanceof com.jvn.core.scene2d.Sprite2D s) return s.getAlpha();
    if (e instanceof com.jvn.core.scene2d.Label2D l) return l.getAlpha();
    if (e instanceof com.jvn.core.scene2d.Panel2D p) return p.getFillA();
    return 1.0;
  }

  private static void setAlpha(Entity2D e, double a) {
    double aa = Math.max(0.0, Math.min(1.0, a));
    if (e instanceof com.jvn.core.scene2d.Sprite2D s) s.setAlpha(aa);
    else if (e instanceof com.jvn.core.scene2d.Label2D l) l.setColor(l.getColorR(), l.getColorG(), l.getColorB(), aa);
    else if (e instanceof com.jvn.core.scene2d.Panel2D p) p.setFill(p.getFillR(), p.getFillG(), p.getFillB(), aa);
  }
}
