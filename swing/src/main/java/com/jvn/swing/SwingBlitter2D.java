package com.jvn.swing;

import com.jvn.core.scene2d.Blitter2D;

import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayDeque;
import java.util.Deque;

public class SwingBlitter2D implements Blitter2D {
  private Graphics2D g2;
  private Color fill = new Color(255,255,255,255);
  private Color stroke = new Color(255,255,255,255);
  private float strokeWidth = 1f;
  private float alpha = 1f;
  private BasicStroke basicStroke = new BasicStroke(strokeWidth, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
  private final Deque<AffineTransform> transforms = new ArrayDeque<>();
  private final Deque<Composite> composites = new ArrayDeque<>();
  private Path2D currentPath = null;

  public SwingBlitter2D(Graphics2D g2) {
    this.g2 = (Graphics2D) g2.create();
    this.g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    this.g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
  }

  public void dispose() {
    if (g2 != null) g2.dispose();
  }

  @Override
  public void clear(double r, double g, double b, double a) {
    Composite old = g2.getComposite();
    g2.setComposite(AlphaComposite.Src);
    g2.setColor(new Color((float) r, (float) g, (float) b, (float) a));
    java.awt.Rectangle clip = g2.getClipBounds();
    if (clip != null) g2.fillRect(clip.x, clip.y, clip.width, clip.height);
    else {
      g2.fillRect(0, 0, Integer.MAX_VALUE / 4, Integer.MAX_VALUE / 4);
    }
    g2.setComposite(old);
  }

  @Override
  public void setFill(double r, double g, double b, double a) {
    fill = new Color((float) r, (float) g, (float) b, (float) (a * alpha));
  }

  @Override
  public void setStroke(double r, double g, double b, double a) {
    stroke = new Color((float) r, (float) g, (float) b, (float) (a * alpha));
  }

  @Override
  public void setStrokeWidth(double w) {
    strokeWidth = (float) w;
    basicStroke = new BasicStroke(strokeWidth, basicStroke.getEndCap(), basicStroke.getLineJoin(), basicStroke.getMiterLimit(), basicStroke.getDashArray(), basicStroke.getDashPhase());
  }

  @Override
  public void setGlobalAlpha(double a) {
    alpha = (float) Math.max(0, Math.min(1, a));
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
  }

  @Override
  public void setFont(String family, double size, boolean bold) {
    int style = bold ? Font.BOLD : Font.PLAIN;
    g2.setFont(new Font(family == null ? "SansSerif" : family, style, (int) Math.round(size)));
  }

  @Override
  public void push() {
    transforms.push(g2.getTransform());
    composites.push(g2.getComposite());
  }

  @Override
  public void pop() {
    if (!transforms.isEmpty()) g2.setTransform(transforms.pop());
    if (!composites.isEmpty()) g2.setComposite(composites.pop());
  }

  @Override
  public void translate(double x, double y) { g2.translate(x, y); }

  @Override
  public void rotateDeg(double degrees) { g2.rotate(Math.toRadians(degrees)); }

  @Override
  public void scale(double sx, double sy) { g2.scale(sx, sy); }

  @Override
  public void fillRect(double x, double y, double w, double h) {
    g2.setColor(fill);
    g2.fill(new java.awt.geom.Rectangle2D.Double(x, y, w, h));
  }

  @Override
  public void strokeRect(double x, double y, double w, double h) {
    g2.setColor(stroke);
    g2.setStroke(basicStroke);
    g2.draw(new java.awt.geom.Rectangle2D.Double(x, y, w, h));
  }

  @Override
  public void fillCircle(double cx, double cy, double radius) {
    g2.setColor(fill);
    double d = radius * 2;
    g2.fill(new Ellipse2D.Double(cx - radius, cy - radius, d, d));
  }

  @Override
  public void strokeCircle(double cx, double cy, double radius) {
    g2.setColor(stroke);
    g2.setStroke(basicStroke);
    double d = radius * 2;
    g2.draw(new Ellipse2D.Double(cx - radius, cy - radius, d, d));
  }

  @Override
  public void drawLine(double x1, double y1, double x2, double y2) {
    g2.setColor(stroke);
    g2.setStroke(basicStroke);
    g2.draw(new java.awt.geom.Line2D.Double(x1, y1, x2, y2));
  }

  @Override
  public void drawImage(String classpath, double x, double y, double w, double h) {
    // No external assets expected; leave no-op or implement classpath resource draw if needed
  }

  @Override
  public void drawImageRegion(String classpath, double sx, double sy, double sw, double sh, double dx, double dy, double dw, double dh) {
    // No external assets expected
  }

  @Override
  public void drawText(String text, double x, double y, double size, boolean bold) {
    if (text == null) return;
    setFont(g2.getFont().getFamily(), size, bold);
    g2.setColor(fill);
    g2.drawString(text, (float) x, (float) y);
  }

  @Override
  public double measureTextWidth(String text, double size, boolean bold) {
    if (text == null) return 0;
    FontMetrics fm = g2.getFontMetrics(new Font(g2.getFont().getFamily(), bold ? Font.BOLD : Font.PLAIN, (int) Math.round(size)));
    return fm.stringWidth(text);
  }

  // Vector path extensions
  @Override
  public void beginPath() { currentPath = new Path2D.Double(); }

  @Override
  public void moveTo(double x, double y) { if (currentPath != null) currentPath.moveTo(x, y); }

  @Override
  public void lineTo(double x, double y) { if (currentPath != null) currentPath.lineTo(x, y); }

  @Override
  public void closePath() { if (currentPath != null) currentPath.closePath(); }

  @Override
  public void fillPath() { if (currentPath != null) { g2.setColor(fill); g2.fill(currentPath); } }

  @Override
  public void strokePath() { if (currentPath != null) { g2.setColor(stroke); g2.setStroke(basicStroke); g2.draw(currentPath); } }

  @Override
  public void setStrokeCap(String cap) {
    int c = switch (cap == null ? "square" : cap.toLowerCase()) {
      case "butt" -> BasicStroke.CAP_BUTT;
      case "round" -> BasicStroke.CAP_ROUND;
      default -> BasicStroke.CAP_SQUARE;
    };
    basicStroke = new BasicStroke(strokeWidth, c, basicStroke.getLineJoin(), basicStroke.getMiterLimit(), basicStroke.getDashArray(), basicStroke.getDashPhase());
  }

  @Override
  public void setStrokeJoin(String join) {
    int j = switch (join == null ? "miter" : join.toLowerCase()) {
      case "round" -> BasicStroke.JOIN_ROUND;
      case "bevel" -> BasicStroke.JOIN_BEVEL;
      default -> BasicStroke.JOIN_MITER;
    };
    basicStroke = new BasicStroke(strokeWidth, basicStroke.getEndCap(), j, basicStroke.getMiterLimit(), basicStroke.getDashArray(), basicStroke.getDashPhase());
  }

  @Override
  public void setMiterLimit(double limit) {
    basicStroke = new BasicStroke(strokeWidth, basicStroke.getEndCap(), basicStroke.getLineJoin(), (float) limit, basicStroke.getDashArray(), basicStroke.getDashPhase());
  }

  @Override
  public void setDash(double[] dashes, double phase) {
    float[] arr = null;
    if (dashes != null && dashes.length > 0) {
      arr = new float[dashes.length];
      for (int i = 0; i < dashes.length; i++) arr[i] = (float) dashes[i];
    }
    basicStroke = new BasicStroke(strokeWidth, basicStroke.getEndCap(), basicStroke.getLineJoin(), basicStroke.getMiterLimit(), arr, (float) phase);
  }

  @Override
  public void setClipRect(double x, double y, double w, double h) { g2.setClip(new java.awt.geom.Rectangle2D.Double(x, y, w, h)); }
}
