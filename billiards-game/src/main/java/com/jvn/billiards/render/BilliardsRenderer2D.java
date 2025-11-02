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
    // Rails border
    b.setFill(0.25, 0.15, 0.05, 1);
    b.fillRect(-0.05, -0.05, cfg.tableWidth + 0.10, cfg.tableHeight + 0.10);
    // Cloth
    b.setFill(0.05, 0.30, 0.12, 1);
    b.fillRect(0, 0, cfg.tableWidth, cfg.tableHeight);
    // Outline
    b.setStroke(0.1,0.1,0.1,1);
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

    // balls (vector-only)
    for (RigidBody2D ball : world.getWorld().getBodies()) {
      if (ball.isSensor()) continue;
      if (ball.getShapeType() != RigidBody2D.ShapeType.CIRCLE) continue;
      int id = world.getBallIdForBody(ball);
      double r = ball.getCircle().r;
      double cx = ball.getCircle().x;
      double cy = ball.getCircle().y;

      if (id == BilliardsWorld.CUE_BALL) {
        // Cue: white ball with small black dot
        b.setFill(1,1,1,1);
        b.fillCircle(cx, cy, r);
        b.setFill(0,0,0,0.2);
        b.fillCircle(cx + r*0.15, cy - r*0.15, r*0.1);
      } else if (id == BilliardsWorld.EIGHT_BALL) {
        // Eight: black with white number circle
        b.setFill(0.05,0.05,0.05,1);
        b.fillCircle(cx, cy, r);
        b.setFill(1,1,1,1);
        b.fillCircle(cx, cy, r*0.35);
        b.setFill(0,0,0,1);
        b.drawText("8", cx - b.measureTextWidth("8", r*0.6, true)/2.0, cy + r*0.22, r*0.6, true);
      } else if (id > 0) {
        // Other balls: solids 1..7, stripes 9..15
        boolean stripe = id >= 9 && id <= 15;
        int base = stripe ? id - 8 : id;
        double[] col = ballColor(base);
        if (stripe) {
          // white base
          b.setFill(1,1,1,1);
          b.fillCircle(cx, cy, r);
          // colored stripe band
          b.setFill(col[0], col[1], col[2], 1);
          b.beginPath();
          b.moveTo(cx - r, cy - r*0.35);
          b.lineTo(cx + r, cy - r*0.35);
          b.lineTo(cx + r, cy + r*0.35);
          b.lineTo(cx - r, cy + r*0.35);
          b.closePath();
          b.fillPath();
          // number circle
          b.setFill(1,1,1,1);
          b.fillCircle(cx, cy, r*0.35);
          b.setFill(0,0,0,1);
          String txt = Integer.toString(id);
          b.drawText(txt, cx - b.measureTextWidth(txt, r*0.5, true)/2.0, cy + r*0.18, r*0.5, true);
        } else {
          // solid color
          b.setFill(col[0], col[1], col[2], 1);
          b.fillCircle(cx, cy, r);
          // white number circle
          b.setFill(1,1,1,1);
          b.fillCircle(cx, cy, r*0.35);
          b.setFill(0,0,0,1);
          String txt = Integer.toString(id);
          b.drawText(txt, cx - b.measureTextWidth(txt, r*0.5, true)/2.0, cy + r*0.18, r*0.5, true);
        }
      }
    }
    b.pop();
  }

  private static double[] ballColor(int n) {
    return switch (n) {
      case 1 -> new double[]{1.00, 0.85, 0.00}; // yellow
      case 2 -> new double[]{0.05, 0.25, 0.85}; // blue
      case 3 -> new double[]{0.85, 0.10, 0.10}; // red
      case 4 -> new double[]{0.55, 0.25, 0.75}; // purple
      case 5 -> new double[]{0.95, 0.50, 0.00}; // orange
      case 6 -> new double[]{0.05, 0.55, 0.15}; // green
      case 7 -> new double[]{0.45, 0.10, 0.10}; // maroon
      default -> new double[]{0.8, 0.8, 0.8};
    };
  }
}
