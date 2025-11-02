package com.jvn.billiards.engine;

import com.jvn.billiards.api.*;

public class BilliardsGame {
  private final BilliardsWorld world;
  private final BilliardsRules rules;
  private final ShotStats currentShot = new ShotStats();
  private BilliardsGameListener listener;
  private boolean shotInProgress = false;
  private boolean wasMoving = false;

  public BilliardsGame(BilliardsConfig cfg, BilliardsRules rules) {
    this.world = new BilliardsWorld(cfg);
    this.rules = rules;
    this.world.setListener(ballId -> {
      currentShot.addPocketed(ballId);
      if (listener != null) listener.onBallPocketed(rules.getCurrentPlayer(), ballId);
    });
    this.rules.startRack(world);
  }

  public BilliardsWorld getWorld() { return world; }
  public void setListener(BilliardsGameListener l) { this.listener = l; }
  public int getCurrentPlayer() { return rules.getCurrentPlayer(); }

  public void beginShot() {
    if (shotInProgress) return;
    shotInProgress = true;
    wasMoving = false;
    currentShot.getPocketedBalls().clear();
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
    ShotOutcome outcome = rules.onShotEnd(currentShot);
    shotInProgress = false;
    wasMoving = false;
    if (listener != null) listener.onShotEnded(rules.getCurrentPlayer(), outcome, currentShot);

    switch (outcome) {
      case WIN -> { if (listener != null) listener.onGameOver(rules.getCurrentPlayer()); }
      case LOSS -> { if (listener != null) listener.onGameOver(1 - rules.getCurrentPlayer()); }
      case TURN_SWITCH, FOUL -> { rules.nextPlayer(); if (listener != null) listener.onTurnChanged(rules.getCurrentPlayer()); }
      default -> {}
    }
  }
}
