package com.jvn.billiards.api;

public interface BilliardsGameListener {
  void onShotBegan(int playerIndex);
  void onBallPocketed(int playerIndex, int ballId);
  void onShotEnded(int playerIndex, ShotOutcome outcome, ShotStats stats);
  void onTurnChanged(int playerIndex);
  void onGameOver(int winningPlayerIndex);
}
