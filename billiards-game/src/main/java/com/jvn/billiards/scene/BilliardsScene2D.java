package com.jvn.billiards.scene;

import com.jvn.billiards.api.BilliardsConfig;
import com.jvn.billiards.engine.BilliardsGame;
import com.jvn.billiards.engine.BilliardsWorld;
import com.jvn.billiards.input.CueController;
import com.jvn.billiards.render.BilliardsRenderer2D;
import com.jvn.billiards.rules.EightBallRules;
import com.jvn.core.input.Input;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Scene2DBase;

public class BilliardsScene2D extends Scene2DBase {
  private final BilliardsGame game;
  private final BilliardsRenderer2D renderer;
  private final CueController cue;

  private double pixelsPerMeter = 200.0;
  private double originX = 0.0;
  private double originY = 0.0;

  public BilliardsScene2D() {
    this.game = new BilliardsGame(BilliardsConfig.defaultEightBall(), new EightBallRules());
    this.renderer = new BilliardsRenderer2D();
    this.cue = new CueController();
  }

  @Override
  public void update(long deltaMs) {
    game.update(deltaMs);
  }

  @Override
  public void render(Blitter2D b, double width, double height) {
    var cfg = game.getWorld().getConfig();

    double tablePxW = cfg.tableWidth * pixelsPerMeter;
    double tablePxH = cfg.tableHeight * pixelsPerMeter;
    originX = (width - tablePxW) * 0.5;
    originY = (height - tablePxH) * 0.5;

    b.clear(0.05, 0.05, 0.05, 1.0);

    // World render (meters)
    b.push();
    b.translate(originX, originY);
    b.scale(pixelsPerMeter, pixelsPerMeter);
    renderer.render(b, game.getWorld());

    // Aim line and strike handling (in world space)
    Input in = getInput();
    if (in != null) {
      double mx = in.getMouseX();
      double my = in.getMouseY();
      double wx = (mx - originX) / pixelsPerMeter;
      double wy = (my - originY) / pixelsPerMeter;

      var cueBall = game.getWorld().getBall(BilliardsWorld.CUE_BALL);
      if (cueBall != null) {
        double bx = cueBall.getCircle().x;
        double by = cueBall.getCircle().y;
        double dx = wx - bx;
        double dy = wy - by;
        cue.setAimDirection(dx, dy);

        double len = Math.hypot(dx, dy);
        double ux = len == 0 ? 1 : dx / len;
        double uy = len == 0 ? 0 : dy / len;

        b.setStroke(1, 1, 0, 0.8);
        b.setStrokeWidth(0.01);
        b.drawLine(bx, by, bx + ux * 0.6, by + uy * 0.6);
      }

      // Power adjust: scroll or keys
      double scroll = in.getScrollDeltaY();
      if (scroll != 0) {
        cue.setPower(cue.getPower() + Math.signum(scroll) * 0.05);
      }
      if (in.wasKeyPressed("Up") || in.wasKeyPressed("W")) cue.setPower(cue.getPower() + 0.05);
      if (in.wasKeyPressed("Down") || in.wasKeyPressed("S")) cue.setPower(cue.getPower() - 0.05);

      // Strike
      if (in.wasMousePressed(1) || in.wasKeyPressed("Space")) {
        game.beginShot();
        cue.strike(game.getWorld(), game.getWorld().getConfig().breakSpeed);
      }
    }
    b.pop();

    // HUD (screen space)
    drawHud(b, width, height, cfg);
  }

  private void drawHud(Blitter2D b, double width, double height, BilliardsConfig cfg) {
    double barW = Math.min(220, width * 0.3);
    double barH = 14;
    double x = 20;
    double y = height - 30;
    double p = Math.max(0, Math.min(1, cue.getPower()));

    b.setFill(0, 0, 0, 0.4);
    b.fillRect(x - 6, y - 20, barW + 12, 28);

    b.setStroke(1, 1, 1, 0.8);
    b.setStrokeWidth(2);
    b.strokeRect(x, y, barW, barH);

    b.setFill(0.1, 0.8, 0.2, 0.9);
    b.fillRect(x + 1, y + 1, (barW - 2) * p, barH - 2);

    b.setFill(1, 1, 1, 1);
    b.drawText("Power", x, y - 6, 12, true);
    String v = String.format("%.0f%%", p * 100);
    double tw = b.measureTextWidth(v, 12, true);
    b.drawText(v, x + barW - tw, y - 6, 12, true);
  }
}
