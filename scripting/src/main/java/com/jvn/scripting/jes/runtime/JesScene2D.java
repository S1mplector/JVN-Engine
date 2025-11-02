package com.jvn.scripting.jes.runtime;

import com.jvn.core.physics.PhysicsWorld2D;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.input.Input;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.scripting.jes.ast.JesAst;
import com.jvn.core.animation.Easing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JesScene2D extends Scene2DBase {
  private final PhysicsWorld2D world = new PhysicsWorld2D();
  private boolean debug = false;
  private PhysicsDebugOverlay2D debugOverlay;
  private final List<Binding> bindings = new ArrayList<>();
  private final Map<String, Entity2D> named = new HashMap<>();

  private List<JesAst.TimelineAction> timeline = new ArrayList<>();
  private int tlIndex = 0;
  private double tlElapsedMs = 0;
  private final Map<Integer, ActionRuntime> actionState = new HashMap<>();

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
  }

  public PhysicsWorld2D getWorld() { return world; }
  public void setDebug(boolean d) { this.debug = d; }
  public void addBinding(String key, String action, Map<String,Object> props) { bindings.add(new Binding(key, action, props)); }
  public void registerEntity(String name, Entity2D e) { if (name != null && !name.isBlank() && e != null && !named.containsKey(name)) named.put(name, e); }
  public void setTimeline(List<JesAst.TimelineAction> tl) { this.timeline = tl == null ? new ArrayList<>() : new ArrayList<>(tl); this.tlIndex = 0; this.tlElapsedMs = 0; this.actionState.clear(); }
  public java.util.Set<String> names() { return named.keySet(); }
  public Entity2D find(String name) { return named.get(name); }
  public Map<String, Entity2D> exportNamed() { return java.util.Collections.unmodifiableMap(new java.util.HashMap<>(named)); }
  public java.util.List<Binding> exportBindings() { return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(bindings)); }
  public java.util.List<JesAst.TimelineAction> exportTimeline() { return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(timeline)); }

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
        // Placeholder: no-op
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
      default -> { tlIndex++; tlElapsedMs = 0; }
    }
  }

  private void handleAction(Binding b) {
    switch (b.action) {
      case "toggleDebug" -> toggleDebugOverlay();
      case "spawnCircle" -> spawnCircle(b.props);
      case "spawnBox" -> spawnBox(b.props);
      default -> {}
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
}
