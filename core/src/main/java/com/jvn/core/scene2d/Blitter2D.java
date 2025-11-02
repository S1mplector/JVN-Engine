package com.jvn.core.scene2d;

public interface Blitter2D {
  void clear(double r, double g, double b, double a);

  void setFill(double r, double g, double b, double a);
  void setStroke(double r, double g, double b, double a);
  void setStrokeWidth(double w);
  void setGlobalAlpha(double a);
  void setFont(String family, double size, boolean bold);

  void push();
  void pop();
  void translate(double x, double y);
  void rotateDeg(double degrees);
  void scale(double sx, double sy);

  void fillRect(double x, double y, double w, double h);
  void strokeRect(double x, double y, double w, double h);

  void fillCircle(double cx, double cy, double radius);
  void strokeCircle(double cx, double cy, double radius);

  void drawLine(double x1, double y1, double x2, double y2);

  void drawImage(String classpath, double x, double y, double w, double h);
  void drawImageRegion(String classpath, double sx, double sy, double sw, double sh,
                       double dx, double dy, double dw, double dh);

  void drawText(String text, double x, double y, double size, boolean bold);
  double measureTextWidth(String text, double size, boolean bold);

  // Optional vector path API (default no-op) for advanced backends
  default void beginPath() {}
  default void moveTo(double x, double y) {}
  default void lineTo(double x, double y) {}
  default void closePath() {}
  default void fillPath() {}
  default void strokePath() {}

  // Optional stroke settings
  default void setStrokeCap(String cap) {}
  default void setStrokeJoin(String join) {}
  default void setMiterLimit(double limit) {}
  default void setDash(double[] dashes, double phase) {}

  // Optional clipping
  default void setClipRect(double x, double y, double w, double h) {}

  // Optional polygon helpers
  default void fillPolygon(double[] xy) {}
  default void strokePolygon(double[] xy) {}

  // Optional arc helpers (degrees)
  default void fillArc(double cx, double cy, double r, double startDeg, double sweepDeg) {}
  default void strokeArc(double cx, double cy, double r, double startDeg, double sweepDeg) {}

  // Optional gradient fills
  // positions: array of stops in [0..1]; colorsRgba: flattened [r,g,b,a] per stop (length = positions.length * 4)
  default void setFillLinearGradient(double x1, double y1, double x2, double y2, double[] positions, double[] colorsRgba) {}
  default void setFillRadialGradient(double cx, double cy, double r, double[] positions, double[] colorsRgba) {}

  // Optional text alignment; hAlign: left|center|right, vAlign: baseline|top|middle|bottom
  default void setTextAlign(String hAlign, String vAlign) {}
}

