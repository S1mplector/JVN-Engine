package com.jvn.core.input;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility to persist/retrieve ActionBindingProfile from disk.
 */
public class ActionBindingProfileStore {
  private final Path path;

  public ActionBindingProfileStore(String path) {
    this.path = Paths.get(path == null || path.isBlank() ? defaultPath() : path);
  }

  public ActionBindingProfile load() {
    try {
      if (!Files.exists(path)) return new ActionBindingProfile();
      String data = Files.readString(path);
      return ActionBindingProfile.deserialize(data);
    } catch (IOException e) {
      return new ActionBindingProfile();
    }
  }

  public void save(ActionBindingProfile profile) throws IOException {
    if (profile == null) return;
    Files.createDirectories(path.getParent());
    Files.writeString(path, profile.serialize());
  }

  public String getPath() { return path.toString(); }

  public static String defaultPath() {
    return System.getProperty("user.home") + "/.jvn/input-bindings.properties";
  }
}
