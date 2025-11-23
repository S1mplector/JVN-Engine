package com.jvn.core.input;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Input {
  private final Set<InputCode> down = new HashSet<>();
  private final Set<InputCode> pressed = new HashSet<>();
  private final Set<InputCode> released = new HashSet<>();
  private final Map<InputCode, Double> axisValues = new HashMap<>();

  private double mouseX;
  private double mouseY;
  private double scrollDeltaY;

  public void keyDown(String key) {
    if (key == null) return;
    keyDown(InputCode.key(key));
  }

  public void keyDown(InputCode code) {
    if (code == null) return;
    if (down.add(code)) pressed.add(code);
  }

  public void keyUp(String key) {
    if (key == null) return;
    keyUp(InputCode.key(key));
  }

  public void keyUp(InputCode code) {
    if (code == null) return;
    if (down.remove(code)) released.add(code);
  }

  public boolean isKeyDown(String key) { return isDown(InputCode.key(key)); }
  public boolean wasKeyPressed(String key) { return wasPressed(InputCode.key(key)); }
  public boolean wasKeyReleased(String key) { return wasReleased(InputCode.key(key)); }

  public void mouseDown(int button) { handleButton(InputCode.mouse(button), true); }
  public void mouseUp(int button) { handleButton(InputCode.mouse(button), false); }

  public boolean isMouseDown(int button) { return isDown(InputCode.mouse(button)); }
  public boolean wasMousePressed(int button) { return wasPressed(InputCode.mouse(button)); }
  public boolean wasMouseReleased(int button) { return wasReleased(InputCode.mouse(button)); }

  public void gamepadButtonDown(int pad, String button) { handleButton(InputCode.gamepadButton(pad, button), true); }
  public void gamepadButtonUp(int pad, String button) { handleButton(InputCode.gamepadButton(pad, button), false); }

  public void setGamepadAxis(int pad, String axis, double value) {
    InputCode code = InputCode.gamepadAxis(pad, axis);
    axisValues.put(code, value);
  }

  public double getGamepadAxis(int pad, String axis) {
    InputCode code = InputCode.gamepadAxis(pad, axis);
    return axisValues.getOrDefault(code, 0.0);
  }

  public boolean isDown(InputCode code) { return down.contains(code); }
  public boolean wasPressed(InputCode code) { return pressed.contains(code); }
  public boolean wasReleased(InputCode code) { return released.contains(code); }

  public void setMousePosition(double x, double y) { this.mouseX = x; this.mouseY = y; }
  public double getMouseX() { return mouseX; }
  public double getMouseY() { return mouseY; }

  public void addScrollDeltaY(double dy) { this.scrollDeltaY += dy; }
  public double getScrollDeltaY() { return scrollDeltaY; }

  public void endFrame() {
    pressed.clear();
    released.clear();
    scrollDeltaY = 0;
  }

  public void reset() {
    down.clear();
    pressed.clear();
    released.clear();
    axisValues.clear();
    mouseX = 0;
    mouseY = 0;
    scrollDeltaY = 0;
  }

  private void handleButton(InputCode code, boolean downEvent) {
    if (code == null) return;
    if (downEvent) {
      if (down.add(code)) pressed.add(code);
    } else {
      if (down.remove(code)) released.add(code);
    }
  }
}
