package com.jvn.core.vn;

public class VnInteropResult {
  private final boolean advance;

  private VnInteropResult(boolean advance) { this.advance = advance; }
  public boolean shouldAdvance() { return advance; }

  public static VnInteropResult advance() { return new VnInteropResult(true); }
  public static VnInteropResult stay() { return new VnInteropResult(false); }
}
