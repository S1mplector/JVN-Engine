package com.jvn.core.input;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Serializable snapshot of action bindings so users can persist/reload preferences.
 */
public class ActionBindingProfile {
  private final Map<String, Set<InputCode>> bindings = new HashMap<>();

  public Map<String, Set<InputCode>> bindings() { return bindings; }

  public ActionBindingProfile add(String action, InputCode code) {
    if (action == null || code == null) return this;
    bindings.computeIfAbsent(action, k -> new HashSet<>()).add(code);
    return this;
  }

  public String serialize() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Set<InputCode>> e : bindings.entrySet()) {
      StringJoiner join = new StringJoiner(",");
      for (InputCode code : e.getValue()) {
        join.add(code.encode());
      }
      sb.append(e.getKey()).append('=').append(join.toString()).append('\n');
    }
    return sb.toString();
  }

  public static ActionBindingProfile deserialize(String data) {
    ActionBindingProfile profile = new ActionBindingProfile();
    if (data == null || data.isBlank()) return profile;
    String[] lines = data.split("\\R");
    for (String line : lines) {
      if (line == null || line.isBlank()) continue;
      int idx = line.indexOf('=');
      if (idx <= 0) continue;
      String action = line.substring(0, idx);
      String codes = line.substring(idx + 1);
      for (String raw : codes.split(",")) {
        InputCode code = InputCode.decode(raw);
        if (code != null) profile.add(action, code);
      }
    }
    return profile;
  }

  public List<String> actions() { return new ArrayList<>(bindings.keySet()); }
}
