package com.jvn.scripting.jes.runtime;

import com.jvn.core.scene2d.Panel2D;
import java.util.HashMap;
import java.util.Map;

/**
 * Minimal UI button entity for JES timelines/actions.
 */
public class UiButton2D extends Panel2D {
  private String call;
  private final Map<String,Object> props = new HashMap<>();

  public UiButton2D(double w, double h) {
    super(w, h);
  }

  public void setCall(String call) { this.call = call; }
  public String getCall() { return call; }
  public Map<String,Object> getProps() { return props; }
}
