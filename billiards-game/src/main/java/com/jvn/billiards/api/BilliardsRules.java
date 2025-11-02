package com.jvn.billiards.api;

import com.jvn.billiards.engine.BilliardsWorld;

public interface BilliardsRules {
  void startRack(BilliardsWorld world);
  void onShotBegin();
  void onBallPocketed(int ballId);
  ShotOutcome onShotEnd(ShotStats stats);
  int getCurrentPlayer();
  void nextPlayer();
  BallGroup getAssignedGroup(int playerIndex);
}
