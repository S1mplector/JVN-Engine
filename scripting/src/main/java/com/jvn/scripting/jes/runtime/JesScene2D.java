package com.jvn.scripting.jes.runtime;

import com.jvn.core.physics.PhysicsWorld2D;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Scene2DBase;

public class JesScene2D extends Scene2DBase {
  private final PhysicsWorld2D world = new PhysicsWorld2D();
  private boolean debug = false;

  public PhysicsWorld2D getWorld() { return world; }
  public void setDebug(boolean d) { this.debug = d; }

  @Override
  public void update(long deltaMs) {
    super.update(deltaMs);
    world.step(deltaMs);
  }

  @Override
  public void render(Blitter2D b, double width, double height) {
    super.render(b, width, height);
    // Optional physics debug could be drawn here if needed
  }
}
