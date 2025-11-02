package com.jvn.billiards.engine;

import com.jvn.billiards.api.BilliardsConfig;
import com.jvn.core.math.Rect;
import com.jvn.core.physics.PhysicsWorld2D;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Blitter2D;

import java.util.*;

public class BilliardsWorld {
  public interface Listener {
    void onBallPocketed(int ballId);
  }

  private final PhysicsWorld2D world = new PhysicsWorld2D();
  private final BilliardsConfig cfg;
  private final Map<Integer, RigidBody2D> balls = new HashMap<>();
  private final Map<RigidBody2D, Integer> bodyToId = new IdentityHashMap<>();
  private final Set<RigidBody2D> pocketSensors = new HashSet<>();
  private final Set<Integer> pocketed = new HashSet<>();
  private Listener listener;
  private double cueSpawnX;
  private double cueSpawnY;

  public static final int CUE_BALL = 0;
  public static final int EIGHT_BALL = 8;

  public BilliardsWorld(BilliardsConfig cfg) {
    this.cfg = cfg;
    initWorld();
  }

  public void setListener(Listener l) { this.listener = l; }
  public PhysicsWorld2D getWorld() { return world; }
  public RigidBody2D getBall(int id) { return balls.get(id); }
  public Set<Integer> getPocketedBalls() { return new HashSet<>(pocketed); }
  public BilliardsConfig getConfig() { return cfg; }
  public int getBallIdForBody(RigidBody2D body) { return bodyToId.getOrDefault(body, -1); }
  public void respawnCueDefault() {
    RigidBody2D cue = balls.get(CUE_BALL);
    if (cue == null) return;
    // If cue was pocketed, re-add it
    if (!world.getBodies().contains(cue)) {
      world.addBody(cue);
      pocketed.remove(CUE_BALL);
    }
    cue.setVelocity(0, 0);
    cue.setPosition(cueSpawnX, cueSpawnY);
  }
  public boolean isAnyBallMoving(double speedThreshold) {
    double th = Math.max(0, speedThreshold);
    for (Map.Entry<Integer, RigidBody2D> e : balls.entrySet()) {
      if (pocketed.contains(e.getKey())) continue;
      RigidBody2D b = e.getValue();
      double v = Math.hypot(b.getVx(), b.getVy());
      if (v > th) return true;
    }
    return false;
  }

  public void update(long deltaMs) {
    world.step(deltaMs);
    double dt = deltaMs / 1000.0;
    double damp = Math.pow(Math.max(0.0, Math.min(1.0, cfg.linearDampingPerSecond)), dt);
    for (Map.Entry<Integer, RigidBody2D> e : balls.entrySet()) {
      if (pocketed.contains(e.getKey())) continue;
      RigidBody2D b = e.getValue();
      b.setVelocity(b.getVx() * damp, b.getVy() * damp);
    }
  }

  public void strikeCue(double dx, double dy, double speed) {
    RigidBody2D cue = balls.get(CUE_BALL);
    if (cue == null) return;
    double len = Math.sqrt(dx*dx + dy*dy);
    if (len == 0) return;
    double s = Math.max(0, speed);
    cue.setVelocity(dx / len * s, dy / len * s);
  }

  public void debugRender(Blitter2D b) {
    b.push();
    b.setStroke(0, 1, 0, 1);
    b.setStrokeWidth(0.01);
    b.strokeRect(0, 0, cfg.tableWidth, cfg.tableHeight);
    b.setFill(0.1, 0.1, 0.1, 1);
    for (RigidBody2D p : pocketSensors) {
      double r = p.getCircle().r;
      b.fillCircle(p.getCircle().x, p.getCircle().y, r);
    }
    b.setFill(1, 1, 1, 1);
    for (Map.Entry<Integer, RigidBody2D> e : balls.entrySet()) {
      if (pocketed.contains(e.getKey())) continue;
      RigidBody2D ball = e.getValue();
      double r = ball.getCircle().r;
      b.fillCircle(ball.getCircle().x, ball.getCircle().y, r);
    }
    b.pop();
  }

  private void initWorld() {
    world.setBounds(new Rect(0, 0, cfg.tableWidth, cfg.tableHeight));
    world.setGravity(0, 0);
    world.clearStaticRects();
    world.getBodies().clear();
    pocketSensors.clear();
    balls.clear();
    pocketed.clear();

    addPockets();
    rackBalls();

    world.setSensorListener((sensor, other) -> {
      if (!pocketSensors.contains(sensor)) return;
      Integer id = bodyToId.get(other);
      if (id != null && !pocketed.contains(id)) {
        pocketed.add(id);
        world.removeBody(other);
        if (listener != null) listener.onBallPocketed(id);
      }
    });
  }

  private void addPockets() {
    double w = cfg.tableWidth, h = cfg.tableHeight;
    double pr = cfg.pocketRadius;
    double[][] centers = new double[][]{
        {0, 0}, {w/2.0, 0}, {w, 0},
        {0, h}, {w/2.0, h}, {w, h}
    };
    for (double[] c : centers) {
      RigidBody2D p = RigidBody2D.circle(c[0], c[1], pr);
      p.setSensor(true);
      p.setStatic(true);
      pocketSensors.add(p);
      world.addBody(p);
    }
  }

  private void rackBalls() {
    double r = cfg.ballRadius;
    double cx = cfg.tableWidth * 0.75;
    double cy = cfg.tableHeight * 0.5;

    int idx = 1;
    for (int row = 0; row < 5; row++) {
      for (int col = 0; col <= row; col++) {
        if (idx > 15) break;
        double x = cx + row * (r * 2 * Math.cos(Math.toRadians(30)));
        double y = cy - row * r + col * (2 * r);
        addBall(idx++, x, y, r);
      }
    }

    double cueX = cfg.tableWidth * 0.25;
    double cueY = cy;
    this.cueSpawnX = cueX; this.cueSpawnY = cueY;
    addBall(CUE_BALL, cueX, cueY, r);
  }

  private void addBall(int id, double x, double y, double r) {
    RigidBody2D b = RigidBody2D.circle(x, y, r);
    b.setMass(cfg.ballMass);
    b.setRestitution(cfg.cushionRestitution);
    balls.put(id, b);
    bodyToId.put(b, id);
    world.addBody(b);
  }
}
