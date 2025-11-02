package com.jvn.scripting.jes.runtime;

import com.jvn.core.physics.PhysicsWorld2D;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.input.Input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JesScene2D extends Scene2DBase {
  private final PhysicsWorld2D world = new PhysicsWorld2D();
  private boolean debug = false;
  private PhysicsDebugOverlay2D debugOverlay;
  private final List<Binding> bindings = new ArrayList<>();

  public static class Binding {
    public final String key;
    public final String action;
    public final Map<String,Object> props;
    public Binding(String key, String action, Map<String,Object> props) {
      this.key = key; this.action = action; this.props = props == null ? new HashMap<>() : props;
    }
  }

  public PhysicsWorld2D getWorld() { return world; }
  public void setDebug(boolean d) { this.debug = d; }
  public void addBinding(String key, String action, Map<String,Object> props) { bindings.add(new Binding(key, action, props)); }

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
  }

  @Override
  public void render(Blitter2D b, double width, double height) {
    super.render(b, width, height);
    // Optional physics debug could be drawn here if needed
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
    double r = toNum(props.get("r"), 10) / 100.0; // assume pixels->meters approx
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
    double w = toNum(props.get("w"), 40) / 100.0;
    double h = toNum(props.get("h"), 40) / 100.0;
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
}
