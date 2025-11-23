package com.jvn.core.input;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class ActionMap {
  private final Input input;
  private final Map<String, Set<InputCode>> bindings = new HashMap<>();

  public ActionMap(Input input) { this.input = input; }

  public ActionMap bindKey(String action, String keyName) {
    return bind(action, InputCode.key(keyName));
  }

  public ActionMap bindMouse(String action, int button) {
    return bind(action, InputCode.mouse(button));
  }

  public ActionMap bindGamepadButton(String action, int pad, String button) {
    return bind(action, InputCode.gamepadButton(pad, button));
  }

  public ActionMap bindGamepadAxis(String action, int pad, String axis) {
    return bind(action, InputCode.gamepadAxis(pad, axis));
  }

  public ActionMap unbindKey(String action, String keyName) {
    return unbind(action, InputCode.key(keyName));
  }

  public ActionMap unbindMouse(String action, int button) {
    return unbind(action, InputCode.mouse(button));
  }

  public ActionMap unbindGamepadButton(String action, int pad, String button) {
    return unbind(action, InputCode.gamepadButton(pad, button));
  }

  public ActionMap unbindGamepadAxis(String action, int pad, String axis) {
    return unbind(action, InputCode.gamepadAxis(pad, axis));
  }

  private ActionMap bind(String action, InputCode code) {
    if (action == null || code == null) return this;
    bindings.computeIfAbsent(action, k -> new HashSet<>()).add(code);
    return this;
  }

  private ActionMap unbind(String action, InputCode code) {
    Set<InputCode> set = bindings.get(action);
    if (set != null) set.remove(code);
    return this;
  }

  public boolean isDown(String action) {
    return test(action, input::isDown);
  }

  public boolean wasPressed(String action) {
    return test(action, input::wasPressed);
  }

  public boolean wasReleased(String action) {
    return test(action, input::wasReleased);
  }

  public ActionBindingProfile toProfile() {
    ActionBindingProfile profile = new ActionBindingProfile();
    bindings.forEach((action, codes) -> {
      for (InputCode c : codes) profile.add(action, c);
    });
    return profile;
  }

  public void loadProfile(ActionBindingProfile profile) {
    bindings.clear();
    if (profile == null) return;
    profile.bindings().forEach((action, codes) -> bindings.put(action, new HashSet<>(codes)));
  }

  private boolean test(String action, Predicate<InputCode> predicate) {
    Set<InputCode> set = bindings.get(action);
    if (set == null) return false;
    for (InputCode code : set) {
      if (predicate.test(code)) return true;
    }
    return false;
  }
}
