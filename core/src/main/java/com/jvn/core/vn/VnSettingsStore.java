package com.jvn.core.vn;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class VnSettingsStore {
  private final Path settingsPath;

  public VnSettingsStore() {
    this(System.getProperty("user.home") + "/.jvn/settings.properties");
  }

  public VnSettingsStore(String path) {
    this.settingsPath = Paths.get(path);
  }

  public VnSettings load() {
    VnSettings s = new VnSettings();
    try {
      ensureDir();
      if (Files.exists(settingsPath)) {
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(settingsPath.toFile())) {
          p.load(in);
        }
        try { s.setTextSpeed(Integer.parseInt(p.getProperty("text_speed", Integer.toString(s.getTextSpeed())))); } catch (Exception ignored) {}
        try { s.setBgmVolume(Float.parseFloat(p.getProperty("bgm_volume", Float.toString(s.getBgmVolume())))); } catch (Exception ignored) {}
        try { s.setSfxVolume(Float.parseFloat(p.getProperty("sfx_volume", Float.toString(s.getSfxVolume())))); } catch (Exception ignored) {}
        try { s.setVoiceVolume(Float.parseFloat(p.getProperty("voice_volume", Float.toString(s.getVoiceVolume())))); } catch (Exception ignored) {}
        try { s.setAutoPlayDelay(Long.parseLong(p.getProperty("auto_play_delay", Long.toString(s.getAutoPlayDelay())))); } catch (Exception ignored) {}
        try { s.setSkipUnreadText(Boolean.parseBoolean(p.getProperty("skip_unread_text", Boolean.toString(s.isSkipUnreadText())))); } catch (Exception ignored) {}
        try { s.setSkipAfterChoices(Boolean.parseBoolean(p.getProperty("skip_after_choices", Boolean.toString(s.isSkipAfterChoices())))); } catch (Exception ignored) {}
      }
    } catch (Exception ignored) {
    }
    return s;
  }

  public void save(VnSettings s) {
    if (s == null) return;
    try {
      ensureDir();
      Properties p = new Properties();
      p.setProperty("text_speed", Integer.toString(s.getTextSpeed()));
      p.setProperty("bgm_volume", Float.toString(s.getBgmVolume()));
      p.setProperty("sfx_volume", Float.toString(s.getSfxVolume()));
      p.setProperty("voice_volume", Float.toString(s.getVoiceVolume()));
      p.setProperty("auto_play_delay", Long.toString(s.getAutoPlayDelay()));
      p.setProperty("skip_unread_text", Boolean.toString(s.isSkipUnreadText()));
      p.setProperty("skip_after_choices", Boolean.toString(s.isSkipAfterChoices()));
      try (FileOutputStream out = new FileOutputStream(settingsPath.toFile())) {
        p.store(out, "JVN Settings");
      }
    } catch (Exception ignored) {
    }
  }

  private void ensureDir() throws Exception {
    Path dir = settingsPath.getParent();
    if (dir != null) Files.createDirectories(dir);
  }
}
