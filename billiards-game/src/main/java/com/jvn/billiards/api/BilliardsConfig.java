package com.jvn.billiards.api;

public class BilliardsConfig {
  public final double tableWidth;
  public final double tableHeight;
  public final double ballRadius;
  public final double ballMass;
  public final double pocketRadius;
  public final double cushionRestitution;
  public final double linearDampingPerSecond;
  public final double breakSpeed;

  public BilliardsConfig(double tableWidth, double tableHeight,
                         double ballRadius, double ballMass,
                         double pocketRadius, double cushionRestitution,
                         double linearDampingPerSecond, double breakSpeed) {
    this.tableWidth = tableWidth;
    this.tableHeight = tableHeight;
    this.ballRadius = ballRadius;
    this.ballMass = ballMass;
    this.pocketRadius = pocketRadius;
    this.cushionRestitution = cushionRestitution;
    this.linearDampingPerSecond = linearDampingPerSecond;
    this.breakSpeed = breakSpeed;
  }

  public static BilliardsConfig defaultEightBall() {
    return new BilliardsConfig(
        2.84, 1.42,      // table size (meters, 9ft table)
        0.028575, 0.17,  // ball radius (57.15mm diameter), mass ~170g
        0.06,            // pocket radius (approx)
        0.92,            // cushion restitution (0..1)
        0.95,            // linear damping per second (0..1)
        4.0              // break speed m/s for cue ball
    );
  }
}
