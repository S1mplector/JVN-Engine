package com.jvn.core.graphics;

/**
 * Utility for fitting a logical 2D resolution into an arbitrary viewport with letterboxing.
 */
public final class ViewportScaler2D {
  private ViewportScaler2D() {}

  public record Transform(double scale, double offsetX, double offsetY, double targetWidth, double targetHeight) {}

  public static Transform fit(double targetWidth, double targetHeight, double viewportWidth, double viewportHeight) {
    double tw = targetWidth <= 0 ? viewportWidth : targetWidth;
    double th = targetHeight <= 0 ? viewportHeight : targetHeight;
    double vw = viewportWidth <= 0 ? 1 : viewportWidth;
    double vh = viewportHeight <= 0 ? 1 : viewportHeight;
    double scale = Math.min(vw / tw, vh / th);
    double ox = (vw - tw * scale) * 0.5;
    double oy = (vh - th * scale) * 0.5;
    return new Transform(scale, ox, oy, tw, th);
  }
}
