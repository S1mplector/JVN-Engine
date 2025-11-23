package com.jvn.core.config;

public class ApplicationConfig {
  private final String title;
  private final int width;
  private final int height;
  private final long fixedUpdateMs;
  private final int fixedUpdateMaxSteps;

  private ApplicationConfig(Builder b) {
    this.title = b.title;
    this.width = b.width;
    this.height = b.height;
    this.fixedUpdateMs = b.fixedUpdateMs;
    this.fixedUpdateMaxSteps = b.fixedUpdateMaxSteps;
  }

  public String title() { return title; }
  public int width() { return width; }
  public int height() { return height; }
  public long fixedUpdateMs() { return fixedUpdateMs; }
  public int fixedUpdateMaxSteps() { return fixedUpdateMaxSteps; }

  public static Builder builder() { return new Builder(); }

  public static final class Builder {
    private String title = "JVN";
    private int width = 960;
    private int height = 540;
    private long fixedUpdateMs = 0;
    private int fixedUpdateMaxSteps = 5;

    public Builder title(String title) { this.title = title; return this; }
    public Builder width(int width) { this.width = width; return this; }
    public Builder height(int height) { this.height = height; return this; }
    /**
     * Enable a fixed update step (ms) and maximum substeps per frame; set stepMs to 0 to disable.
     */
    public Builder fixedUpdate(long stepMs, int maxSteps) {
      this.fixedUpdateMs = Math.max(0, stepMs);
      this.fixedUpdateMaxSteps = Math.max(1, maxSteps);
      return this;
    }
    public ApplicationConfig build() { return new ApplicationConfig(this); }
  }
}
