package com.jvn.billiards.rules;

import com.jvn.billiards.api.*;
import com.jvn.billiards.engine.BilliardsWorld;

import java.util.HashSet;
import java.util.Set;

public class EightBallRules implements BilliardsRules {
  private int currentPlayer = 0;
  private final BallGroup[] assigned = new BallGroup[]{BallGroup.UNKNOWN, BallGroup.UNKNOWN};
  private final Set<Integer> solids = new HashSet<>();
  private final Set<Integer> stripes = new HashSet<>();

  @Override
  public void startRack(BilliardsWorld world) {
    solids.clear();
    stripes.clear();
    for (int i = 1; i <= 7; i++) solids.add(i);
    for (int i = 9; i <= 15; i++) stripes.add(i);
  }

  @Override
  public void onShotBegin() {
  }

  @Override
  public void onBallPocketed(int ballId) {
  }

  @Override
  public ShotOutcome onShotEnd(ShotStats stats) {
    if (stats.isEightBallPocketed()) {
      // win if shooter has cleared assigned group, else loss
      if (assigned[currentPlayer] == BallGroup.SOLID && solids.isEmpty()) return ShotOutcome.WIN;
      if (assigned[currentPlayer] == BallGroup.STRIPE && stripes.isEmpty()) return ShotOutcome.WIN;
      return ShotOutcome.LOSS;
    }

    if (stats.anyFoul()) return ShotOutcome.FOUL;

    // Assign groups if unassigned and a non-cue/non-eight was pocketed
    if (assigned[0] == BallGroup.UNKNOWN && assigned[1] == BallGroup.UNKNOWN) {
      for (int id : stats.getPocketedBalls()) {
        if (id == 0 || id == 8) continue;
        BallGroup g = (id <= 7) ? BallGroup.SOLID : BallGroup.STRIPE;
        assigned[currentPlayer] = g;
        assigned[1 - currentPlayer] = (g == BallGroup.SOLID) ? BallGroup.STRIPE : BallGroup.SOLID;
        break;
      }
    }

    // Remove pocketed from remaining sets
    for (int id : stats.getPocketedBalls()) {
      solids.remove(id); stripes.remove(id);
    }

    // Continue if player pocketed at least one of their group
    boolean madeOwn = false;
    for (int id : stats.getPocketedBalls()) {
      if (assigned[currentPlayer] == BallGroup.SOLID && id >= 1 && id <= 7) madeOwn = true;
      if (assigned[currentPlayer] == BallGroup.STRIPE && id >= 9 && id <= 15) madeOwn = true;
    }
    return madeOwn ? ShotOutcome.TURN_CONTINUES : ShotOutcome.TURN_SWITCH;
  }

  @Override
  public int getCurrentPlayer() { return currentPlayer; }

  @Override
  public void nextPlayer() { currentPlayer = 1 - currentPlayer; }

  @Override
  public BallGroup getAssignedGroup(int playerIndex) { return assigned[playerIndex]; }
}
