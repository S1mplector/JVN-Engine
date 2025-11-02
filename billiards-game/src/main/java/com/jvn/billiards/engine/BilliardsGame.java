package com.jvn.billiards.engine;

import com.jvn.billiards.api.*;
import com.jvn.core.physics.PhysicsWorld2D;

public class BilliardsGame {
  private final BilliardsWorld world;
  private final BilliardsRules rules;
  private final ShotStats currentShot = new ShotStats();
  private BilliardsGameListener listener;
  private boolean shotInProgress = false;
  private boolean wasMoving = false;
  private Integer firstContactBallId = null;
  private boolean anyContact = false;
  private boolean railAfterContact = false;

  public BilliardsGame(BilliardsConfig cfg, BilliardsRules rules) {
    this.world = new BilliardsWorld(cfg);
    this.rules = rules;
    this.world.setListener(ballId -> {
      currentShot.addPocketed(ballId);
      if (listener != null) listener.onBallPocketed(rules.getCurrentPlayer(), ballId);
    });
    // Collision telemetry for rules
    this.world.getWorld().setCollisionListener(new PhysicsWorld2D.CollisionListener() {
      @Override public void onBodiesCollide(com.jvn.core.physics.RigidBody2D a, com.jvn.core.physics.RigidBody2D b, double nx, double ny) {
        if (!shotInProgress) return;
        int ida = world.getBallIdForBody(a);
        int idb = world.getBallIdForBody(b);
        // First contact: cue vs any object ball
        if (firstContactBallId == null) {
          if (ida == BilliardsWorld.CUE_BALL && idb > 0) firstContactBallId = idb;
          else if (idb == BilliardsWorld.CUE_BALL && ida > 0) firstContactBallId = ida;
        }
        if (firstContactBallId != null) anyContact = true;
      }
      @Override public void onBoundsCollide(com.jvn.core.physics.RigidBody2D b, String side) {
        if (!shotInProgress) return;
        if (anyContact) railAfterContact = true;
      }
      @Override public void onStaticCollide(com.jvn.core.physics.RigidBody2D b, com.jvn.core.math.Rect tile, double nx, double ny) {
        if (!shotInProgress) return;
        if (anyContact) railAfterContact = true;
      }
    });
    this.rules.startRack(world);
  }

  public BilliardsWorld getWorld() { return world; }
  public void setListener(BilliardsGameListener l) { this.listener = l; }
  public int getCurrentPlayer() { return rules.getCurrentPlayer(); }
  public BallGroup getAssignedGroup(int player) { return rules.getAssignedGroup(player); }

  public void beginShot() {
    if (shotInProgress) return;
    shotInProgress = true;
    wasMoving = false;
    currentShot.getPocketedBalls().clear();
    firstContactBallId = null;
    anyContact = false;
    railAfterContact = false;
    if (listener != null) listener.onShotBegan(rules.getCurrentPlayer());
    rules.onShotBegin();
  }

  public void strikeCue(double dx, double dy, double speed) {
    beginShot();
    world.strikeCue(dx, dy, speed);
  }

  public void update(long deltaMs) {
    world.update(deltaMs);
    if (!shotInProgress) return;
    boolean moving = world.isAnyBallMoving(0.02);
    if (moving) {
      wasMoving = true;
      return;
    }
    if (!wasMoving) return; // wait until balls moved at least once

    // Shot ended
    // Compute fouls prior to rules evaluation
    if (firstContactBallId == null) {
      currentShot.setNoRailFoul();
    } else {
      boolean anyPocket = !currentShot.getPocketedBalls().isEmpty();
      if (!anyPocket && !railAfterContact) currentShot.setNoRailFoul();
      // Wrong first contact if groups are assigned
      BallGroup g = rules.getAssignedGroup(rules.getCurrentPlayer());
      if (g == BallGroup.SOLID || g == BallGroup.STRIPE) {
        boolean fcSolid = firstContactBallId >= 1 && firstContactBallId <= 7;
        boolean fcStripe = firstContactBallId >= 9 && firstContactBallId <= 15;
        if ((g == BallGroup.SOLID && !fcSolid) || (g == BallGroup.STRIPE && !fcStripe)) {
          // hitting 8 first is illegal until group cleared; handled in rules via outcome
          if (firstContactBallId != 8) currentShot.setWrongFirstContactFoul();
        }
      } else {
        // Open table: disallow hitting 8 first
        if (firstContactBallId == 8) currentShot.setWrongFirstContactFoul();
      }
    }

    ShotOutcome outcome = rules.onShotEnd(currentShot);
    shotInProgress = false;
    wasMoving = false;
    if (listener != null) listener.onShotEnded(rules.getCurrentPlayer(), outcome, currentShot);

    switch (outcome) {
      case WIN -> { if (listener != null) listener.onGameOver(rules.getCurrentPlayer()); }
      case LOSS -> { if (listener != null) listener.onGameOver(1 - rules.getCurrentPlayer()); }
      case TURN_SWITCH, FOUL -> {
        // Ball-in-hand: respawn cue to default spot if fouled
        if (outcome == ShotOutcome.FOUL || currentShot.isFoulScratch() || currentShot.isFoulNoRailAfterContact() || currentShot.isFoulWrongFirstContact()) {
          world.respawnCueDefault();
        }
        rules.nextPlayer();
        if (listener != null) listener.onTurnChanged(rules.getCurrentPlayer());
      }
      default -> {}
    }
  }
}
