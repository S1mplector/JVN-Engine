package com.jvn.billiards.input;

import com.jvn.billiards.engine.BilliardsWorld;

public class CueController {
  private double aimDx = 1, aimDy = 0;
  private double power = 0.5;

  public void setAimDirection(double dx, double dy) {
    double len = Math.hypot(dx, dy);
    if (len == 0) { aimDx = 1; aimDy = 0; } else { aimDx = dx/len; aimDy = dy/len; }
  }

  public void setPower(double p) { power = Math.max(0, Math.min(1, p)); }
  public double getPower() { return power; }

  public void strike(BilliardsWorld world, double maxSpeed) {
    double s = Math.max(0, maxSpeed) * power;
    world.strikeCue(aimDx, aimDy, s);
  }
}
