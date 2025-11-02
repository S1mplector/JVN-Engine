package com.jvn.billiards.render;

import com.jvn.billiards.engine.BilliardsWorld;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Blitter2D;

import java.util.Map;

public class BilliardsRenderer2D {
  public void render(Blitter2D b, BilliardsWorld world) {
    var cfg = world.getConfig();
    // table
    b.push();
    b.setFill(0.04, 0.25, 0.08, 1);
    b.fillRect(0, 0, cfg.tableWidth, cfg.tableHeight);
    b.setStroke(0.2,0.2,0.2,1);
    b.setStrokeWidth(0.01);
    b.strokeRect(0,0,cfg.tableWidth,cfg.tableHeight);

    // pockets
    b.setFill(0.1,0.1,0.1,1);
    double pr = cfg.pocketRadius;
    double[][] centers = new double[][]{
        {0, 0}, {cfg.tableWidth/2.0, 0}, {cfg.tableWidth, 0},
        {0, cfg.tableHeight}, {cfg.tableWidth/2.0, cfg.tableHeight}, {cfg.tableWidth, cfg.tableHeight}
    };
    for (double[] c : centers) b.fillCircle(c[0], c[1], pr);

    // balls
    b.setFill(1,1,1,1);
    for (Map.Entry<Integer, RigidBody2D> e : world.getWorld().getBodies().stream()
        .filter(rb -> rb.getShapeType() == RigidBody2D.ShapeType.CIRCLE)
        .collect(java.util.stream.Collectors.toMap(rb -> world.getWorld().getBodies().indexOf(rb), rb -> rb)).entrySet()) {
      RigidBody2D ball = e.getValue();
      double r = ball.getCircle().r;
      b.fillCircle(ball.getCircle().x, ball.getCircle().y, r);
    }
    b.pop();
  }
}
