package com.jvn.core.animation;

public class Easing {
  
  public enum Type {
    LINEAR,
    EASE_IN_QUAD, EASE_OUT_QUAD, EASE_IN_OUT_QUAD,
    EASE_IN_CUBIC, EASE_OUT_CUBIC, EASE_IN_OUT_CUBIC,
    EASE_IN_QUART, EASE_OUT_QUART, EASE_IN_OUT_QUART,
    EASE_IN_EXPO, EASE_OUT_EXPO, EASE_IN_OUT_EXPO,
    EASE_IN_SINE, EASE_OUT_SINE, EASE_IN_OUT_SINE,
    EASE_IN_ELASTIC, EASE_OUT_ELASTIC, EASE_IN_OUT_ELASTIC,
    EASE_IN_BACK, EASE_OUT_BACK, EASE_IN_OUT_BACK,
    EASE_IN_BOUNCE, EASE_OUT_BOUNCE, EASE_IN_OUT_BOUNCE
  }
  
  public static double apply(Type type, double t) {
    t = Math.max(0, Math.min(1, t)); // clamp to [0,1]
    
    return switch (type) {
      case LINEAR -> t;
      
      // Quadratic
      case EASE_IN_QUAD -> t * t;
      case EASE_OUT_QUAD -> t * (2 - t);
      case EASE_IN_OUT_QUAD -> t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
      
      // Cubic
      case EASE_IN_CUBIC -> t * t * t;
      case EASE_OUT_CUBIC -> (--t) * t * t + 1;
      case EASE_IN_OUT_CUBIC -> t < 0.5 ? 4 * t * t * t : (t - 1) * (2 * t - 2) * (2 * t - 2) + 1;
      
      // Quartic
      case EASE_IN_QUART -> t * t * t * t;
      case EASE_OUT_QUART -> 1 - (--t) * t * t * t;
      case EASE_IN_OUT_QUART -> t < 0.5 ? 8 * t * t * t * t : 1 - 8 * (--t) * t * t * t;
      
      // Exponential
      case EASE_IN_EXPO -> t == 0 ? 0 : Math.pow(2, 10 * (t - 1));
      case EASE_OUT_EXPO -> t == 1 ? 1 : 1 - Math.pow(2, -10 * t);
      case EASE_IN_OUT_EXPO -> {
        if (t == 0) yield 0;
        if (t == 1) yield 1;
        if (t < 0.5) yield Math.pow(2, 20 * t - 10) / 2;
        yield (2 - Math.pow(2, -20 * t + 10)) / 2;
      }
      
      // Sine
      case EASE_IN_SINE -> 1 - Math.cos((t * Math.PI) / 2);
      case EASE_OUT_SINE -> Math.sin((t * Math.PI) / 2);
      case EASE_IN_OUT_SINE -> -(Math.cos(Math.PI * t) - 1) / 2;
      
      // Elastic
      case EASE_IN_ELASTIC -> {
        double c4 = (2 * Math.PI) / 3;
        if (t == 0) yield 0;
        if (t == 1) yield 1;
        yield -Math.pow(2, 10 * t - 10) * Math.sin((t * 10 - 10.75) * c4);
      }
      case EASE_OUT_ELASTIC -> {
        double c4 = (2 * Math.PI) / 3;
        if (t == 0) yield 0;
        if (t == 1) yield 1;
        yield Math.pow(2, -10 * t) * Math.sin((t * 10 - 0.75) * c4) + 1;
      }
      case EASE_IN_OUT_ELASTIC -> {
        double c5 = (2 * Math.PI) / 4.5;
        if (t == 0) yield 0;
        if (t == 1) yield 1;
        if (t < 0.5) yield -(Math.pow(2, 20 * t - 10) * Math.sin((20 * t - 11.125) * c5)) / 2;
        yield (Math.pow(2, -20 * t + 10) * Math.sin((20 * t - 11.125) * c5)) / 2 + 1;
      }
      
      // Back
      case EASE_IN_BACK -> {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        yield c3 * t * t * t - c1 * t * t;
      }
      case EASE_OUT_BACK -> {
        double c1 = 1.70158;
        double c3 = c1 + 1;
        yield 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2);
      }
      case EASE_IN_OUT_BACK -> {
        double c1 = 1.70158;
        double c2 = c1 * 1.525;
        if (t < 0.5) yield (Math.pow(2 * t, 2) * ((c2 + 1) * 2 * t - c2)) / 2;
        yield (Math.pow(2 * t - 2, 2) * ((c2 + 1) * (t * 2 - 2) + c2) + 2) / 2;
      }
      
      // Bounce
      case EASE_IN_BOUNCE -> 1 - apply(Type.EASE_OUT_BOUNCE, 1 - t);
      case EASE_OUT_BOUNCE -> {
        double n1 = 7.5625;
        double d1 = 2.75;
        if (t < 1 / d1) yield n1 * t * t;
        else if (t < 2 / d1) yield n1 * (t -= 1.5 / d1) * t + 0.75;
        else if (t < 2.5 / d1) yield n1 * (t -= 2.25 / d1) * t + 0.9375;
        else yield n1 * (t -= 2.625 / d1) * t + 0.984375;
      }
      case EASE_IN_OUT_BOUNCE -> t < 0.5 
        ? (1 - apply(Type.EASE_OUT_BOUNCE, 1 - 2 * t)) / 2
        : (1 + apply(Type.EASE_OUT_BOUNCE, 2 * t - 1)) / 2;
    };
  }
  
  public static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }
  
  public static double smoothstep(double edge0, double edge1, double x) {
    double t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
    return t * t * (3 - 2 * t);
  }
}
