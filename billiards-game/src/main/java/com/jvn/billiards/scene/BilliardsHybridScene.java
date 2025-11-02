package com.jvn.billiards.scene;

import com.jvn.billiards.api.BilliardsConfig;
import com.jvn.billiards.engine.BilliardsGame;
import com.jvn.billiards.engine.BilliardsWorld;
import com.jvn.billiards.input.CueController;
import com.jvn.billiards.render.BilliardsRenderer2D;
import com.jvn.core.input.Input;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.core.audio.AudioFacade;
import com.jvn.audio.simp3.Simp3AudioService;

public class BilliardsHybridScene extends JesScene2D {
  private final BilliardsGame game;
  private final BilliardsRenderer2D renderer;
  private final CueController cue;
  private final AudioFacade audio;

  private double pixelsPerMeter = 200.0;
  private double originX = 0.0;
  private double originY = 0.0;
  private boolean placingCue = false;

  public BilliardsHybridScene() {
    this.game = new BilliardsGame(BilliardsConfig.defaultEightBall(), new com.jvn.billiards.rules.EightBallRules());
    this.renderer = new BilliardsRenderer2D();
    this.cue = new CueController();
    this.audio = new Simp3AudioService();

    // Set game events -> audio hooks
    this.game.setListener(new com.jvn.billiards.api.BilliardsGameListener() {
      @Override public void onShotBegan(int playerIndex) { /* optional */ }
      @Override public void onBallPocketed(int playerIndex, int ballId) { audio.playSfx("pocket"); }
      @Override public void onShotEnded(int playerIndex, com.jvn.billiards.api.ShotOutcome outcome, com.jvn.billiards.api.ShotStats stats) {
        switch (outcome) {
          case FOUL -> setLabel("help", "Foul - Ball in hand");
          case TURN_SWITCH -> setLabel("help", "Turn switched");
          case WIN -> setLabel("help", "Player " + (playerIndex + 1) + " wins!");
          case LOSS -> setLabel("help", "Player " + (2 - playerIndex) + " wins!");
          default -> {}
        }
      }
      @Override public void onTurnChanged(int playerIndex) {
        var g = game.getAssignedGroup(playerIndex);
        String gtxt = (g == com.jvn.billiards.api.BallGroup.SOLID ? "Solids" : (g == com.jvn.billiards.api.BallGroup.STRIPE ? "Stripes" : "Open"));
        setLabel("turn", "Player " + (playerIndex + 1) + " - " + gtxt);
      }
      @Override public void onGameOver(int winningPlayerIndex) { audio.playSfx("win"); setLabel("help", "Player " + (winningPlayerIndex + 1) + " wins!"); }
    });

    this.setActionHandler((action, props) -> {
      if ("strike".equalsIgnoreCase(action)) {
        double p = toNum(props == null ? null : props.get("power"), cue.getPower());
        cue.setPower(p);
        game.beginShot();
        audio.playSfx("strike");
        cue.strike(game.getWorld(), game.getWorld().getConfig().breakSpeed);
      } else if ("respawnCue".equalsIgnoreCase(action)) {
        game.getWorld().respawnCueDefault();
      } else if ("setPower".equalsIgnoreCase(action)) {
        double p = toNum(props == null ? null : props.get("p"), cue.getPower());
        cue.setPower(p);
      } else if ("playSfx".equalsIgnoreCase(action)) {
        Object name = props == null ? null : props.get("name");
        if (name != null) audio.playSfx(String.valueOf(name));
      } else if ("placeCue".equalsIgnoreCase(action) || "togglePlaceCue".equalsIgnoreCase(action)) {
        placingCue = !placingCue;
      }
    });

    this.registerCall("respawnCue", m -> game.getWorld().respawnCueDefault());
    this.registerCall("playSfx", m -> { Object n = m == null ? null : m.get("name"); if (n != null) audio.playSfx(String.valueOf(n)); });
    this.registerCall("setPower", m -> { double p = toNum(m == null ? null : m.get("p"), cue.getPower()); cue.setPower(p); });
    this.registerCall("placeCue", m -> placingCue = true);
  }

  public BilliardsGame getGame() { return game; }

  @Override
  public void update(long deltaMs) {
    super.update(deltaMs);
    game.update(deltaMs);
  }

  @Override
  public void render(Blitter2D b, double width, double height) {
    // Clear background
    b.clear(0.05, 0.05, 0.05, 1.0);

    // Compute table origin in screen space
    var cfg = game.getWorld().getConfig();
    double tablePxW = cfg.tableWidth * pixelsPerMeter;
    double tablePxH = cfg.tableHeight * pixelsPerMeter;
    originX = (width - tablePxW) * 0.5;
    originY = (height - tablePxH) * 0.5;

    // Render world (meters)
    b.push();
    b.translate(originX, originY);
    b.scale(pixelsPerMeter, pixelsPerMeter);
    renderer.render(b, game.getWorld());

    // Aim line and strike handling (world space)
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
        b.setDash(new double[]{0.04, 0.04}, 0);
        b.drawLine(bx, by, bx + ux * 0.8, by + uy * 0.8);
        b.setDash(null, 0);
      }

      double scroll = in.getScrollDeltaY();
      if (scroll != 0) cue.setPower(cue.getPower() + Math.signum(scroll) * 0.05);
      if (in.wasKeyPressed("UP") || in.wasKeyPressed("W")) cue.setPower(cue.getPower() + 0.05);
      if (in.wasKeyPressed("DOWN") || in.wasKeyPressed("S")) cue.setPower(cue.getPower() - 0.05);

      // Ball-in-hand placement takes priority
      if (placingCue && in.wasMousePressed(1)) {
        // place cue at mouse position (world coords), clamped to table bounds
        var w = game.getWorld();
        var cfg2 = w.getConfig();
        double r = cfg2.ballRadius;
        double px = Math.max(r, Math.min(cfg2.tableWidth - r, wx));
        double py = Math.max(r, Math.min(cfg2.tableHeight - r, wy));
        // ensure cue exists then set
        w.respawnCueDefault();
        var cueBody = w.getBall(BilliardsWorld.CUE_BALL);
        if (cueBody != null) { cueBody.setVelocity(0,0); cueBody.setPosition(px, py); }
        placingCue = false;
      } else if (in.wasMousePressed(1) || in.wasKeyPressed("SPACE")) {
        game.beginShot();
        audio.playSfx("strike");
        cue.strike(game.getWorld(), game.getWorld().getConfig().breakSpeed);
      }
      if (in.wasKeyPressed("R")) {
        game.getWorld().respawnCueDefault();
      }
    }
    b.pop();

    // HUD
    drawHud(b, width, height, cfg);

    // Render any JES UI entities (screen space)
    super.render(b, width, height);
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

    int player = game.getCurrentPlayer();
    var g = game.getAssignedGroup(player);
    String gtxt = (g == com.jvn.billiards.api.BallGroup.SOLID ? "Solids" : (g == com.jvn.billiards.api.BallGroup.STRIPE ? "Stripes" : "Open Table"));
    String info = "Player " + (player + 1) + " - " + gtxt;
    b.drawText(info, 20, 24, 14, true);
    if (placingCue) {
      b.setFill(0, 0, 0, 0.6);
      double bw = 240, bh = 30; double bx = 20, by = 40;
      b.fillRect(bx - 8, by - 18, bw + 16, bh);
      b.setFill(1, 1, 0.6, 1);
      b.drawText("Ball-in-hand: Click to place", bx, by, 14, true);
    }
  }

  public void importFromJesScene(JesScene2D src, boolean labelsOnly) {
    if (src == null) return;
    // Import timeline and bindings
    this.setTimeline(src.exportTimeline());
    for (com.jvn.scripting.jes.runtime.JesScene2D.Binding b : src.exportBindings()) {
      if ("toggleDebug".equalsIgnoreCase(b.action)) this.addBinding(b.key, b.action, b.props);
    }
    // Import selected entities (labels-only default)
    for (java.util.Map.Entry<String, Entity2D> e : src.exportNamed().entrySet()) {
      Entity2D ent = e.getValue();
      if (labelsOnly && !(ent instanceof Label2D)) continue;
      this.add(ent);
      this.registerEntity(e.getKey(), ent);
    }
  }

  private void setLabel(String name, String text) {
    if (name == null) return;
    Entity2D e = find(name);
    if (e instanceof Label2D l) l.setText(text == null ? "" : text);
  }

  private static double toNum(Object v, double def) { return v instanceof Number n ? n.doubleValue() : def; }
}
