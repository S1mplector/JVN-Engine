package com.jvn.core.input;

import java.util.Locale;
import java.util.Objects;

/**
 * Backend-agnostic input identifier that can represent keyboard keys,
 * mouse buttons, or gamepad controls.
 */
public final class InputCode {
  public enum Device { KEYBOARD, MOUSE_BUTTON, GAMEPAD_BUTTON, GAMEPAD_AXIS }

  private final Device device;
  private final String name;
  private final int index;
  private final int gamepad;

  private InputCode(Device device, String name, int index, int gamepad) {
    this.device = device;
    this.name = name;
    this.index = index;
    this.gamepad = gamepad;
  }

  public static InputCode key(String keyName) {
    return new InputCode(Device.KEYBOARD, canonicalKey(keyName), -1, 0);
  }

  public static InputCode mouse(int button) {
    return new InputCode(Device.MOUSE_BUTTON, null, button, 0);
  }

  public static InputCode gamepadButton(int padIndex, String buttonName) {
    return new InputCode(Device.GAMEPAD_BUTTON, canonicalKey(buttonName), -1, Math.max(0, padIndex));
  }

  public static InputCode gamepadAxis(int padIndex, String axisName) {
    return new InputCode(Device.GAMEPAD_AXIS, canonicalKey(axisName), -1, Math.max(0, padIndex));
  }

  public Device device() { return device; }
  public String name() { return name; }
  public int index() { return index; }
  public int gamepad() { return gamepad; }

  public String displayName() {
    return switch (device) {
      case KEYBOARD -> "Key:" + (name == null ? "" : name);
      case MOUSE_BUTTON -> "Mouse:" + index;
      case GAMEPAD_BUTTON -> "Pad" + gamepad + ":Btn:" + (name == null ? "" : name);
      case GAMEPAD_AXIS -> "Pad" + gamepad + ":Axis:" + (name == null ? "" : name);
    };
  }

  public String encode() {
    return device.name() + "|" + gamepad + "|" + (index >= 0 ? index : "") + "|" + (name == null ? "" : name);
  }

  public static InputCode decode(String raw) {
    if (raw == null || raw.isBlank()) return null;
    String[] parts = raw.split("\\|", -1);
    if (parts.length < 4) return null;
    Device d;
    try { d = Device.valueOf(parts[0]); } catch (Exception e) { return null; }
    int pad = parseIntSafe(parts[1]);
    int idx = parseIntSafe(parts[2]);
    String nm = parts[3].isEmpty() ? null : parts[3];
    return new InputCode(d, nm, idx, pad);
  }

  private static int parseIntSafe(String s) {
    try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
  }

  private static String canonicalKey(String key) {
    if (key == null) return "";
    return key.trim().toUpperCase(Locale.ROOT);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof InputCode that)) return false;
    return index == that.index && gamepad == that.gamepad && device == that.device && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(device, name, index, gamepad);
  }

  @Override
  public String toString() {
    return displayName();
  }
}
