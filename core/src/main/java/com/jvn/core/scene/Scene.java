package com.jvn.core.scene;

public interface Scene {
  default void onEnter() {}
  default void onExit() {}
  /**
   * Called when another scene is pushed on top of this one.
   * Use to pause audio/animation without tearing down state.
   */
  default void onPause() {}
  /**
   * Called when this scene becomes active again after a pop.
   */
  default void onResume() {}
  void update(long deltaMs);
}
