package com.jvn.core.menu;

import com.jvn.core.audio.AudioFacade;
import com.jvn.core.scene.Scene;
import com.jvn.core.input.ActionBindingProfile;
import com.jvn.core.input.ActionBindingProfileStore;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.VnSettingsStore;

public class SettingsScene implements Scene {
  private final VnSettings settings;
  private final AudioFacade audio; // optional, to apply volumes live
  private int selected = 0;
  private ActionBindingProfile bindings;
  private String bindingStatus = "";

  public SettingsScene(VnSettings settings) { this(settings, null, null); }

  public SettingsScene(VnSettings settings, AudioFacade audio) { this(settings, audio, null); }

  public SettingsScene(VnSettings settings, AudioFacade audio, ActionBindingProfile bindings) {
    this.settings = settings;
    this.audio = audio;
    if (bindings != null) {
      this.bindings = bindings;
    } else if (settings != null && settings.getInputProfileSerialized() != null && !settings.getInputProfileSerialized().isBlank()) {
      this.bindings = ActionBindingProfile.deserialize(settings.getInputProfileSerialized());
    } else {
      this.bindings = new ActionBindingProfile();
    }
  }

  public VnSettings model() { return settings; }
  public int itemCount() { return 11; }

  public int getSelected() { return selected; }
  public void moveSelection(int delta) {
    int count = itemCount();
    selected = (selected + delta + count) % count;
  }
  public void setSelected(int idx) {
    int count = itemCount();
    if (idx < 0) idx = 0;
    if (idx >= count) idx = count - 1;
    selected = idx;
  }

  public void adjustCurrent(int delta) {
    switch (selected) {
      case 0 -> settings.setTextSpeed(settings.getTextSpeed() + delta);
      case 1 -> {
        settings.setBgmVolume(settings.getBgmVolume() + delta * 0.05f);
        if (audio != null) audio.setBgmVolume(settings.getBgmVolume());
      }
      case 2 -> {
        settings.setSfxVolume(settings.getSfxVolume() + delta * 0.05f);
        if (audio != null) audio.setSfxVolume(settings.getSfxVolume());
      }
      case 3 -> {
        settings.setVoiceVolume(settings.getVoiceVolume() + delta * 0.05f);
        if (audio != null) audio.setVoiceVolume(settings.getVoiceVolume());
      }
      case 4 -> settings.setAutoPlayDelay(settings.getAutoPlayDelay() + delta * 100L);
      case 5 -> settings.setSkipUnreadText(!settings.isSkipUnreadText());
      case 6 -> settings.setSkipAfterChoices(!settings.isSkipAfterChoices());
      case 7 -> settings.setPhysicsFixedStepMs(Math.max(0, settings.getPhysicsFixedStepMs() + delta * 5));
      case 8 -> settings.setPhysicsMaxSubSteps(Math.max(1, settings.getPhysicsMaxSubSteps() + delta));
      case 9 -> settings.setPhysicsDefaultFriction(settings.getPhysicsDefaultFriction() + delta * 0.05);
      case 10 -> {
        // save profile when adjusting right, load when adjusting left
        if (delta > 0) saveBindingsToDisk();
        else loadBindingsFromDisk();
      }
      default -> {}
    }
  }

  public void toggleCurrent() {
    if (selected == 5) settings.setSkipUnreadText(!settings.isSkipUnreadText());
    else if (selected == 6) settings.setSkipAfterChoices(!settings.isSkipAfterChoices());
    else if (selected == 10) loadBindingsFromDisk();
  }

  @Override
  public void update(long deltaMs) { }

  public void setValueByIndex(int idx, double value01) {
    double v = Math.max(0.0, Math.min(1.0, value01));
    switch (idx) {
      case 0 -> {
        // text speed 10..120 ms
        int min = 10, max = 120;
        int val = (int) Math.round(min + v * (max - min));
        settings.setTextSpeed(val);
      }
      case 1 -> settings.setBgmVolume((float) v);
      case 2 -> settings.setSfxVolume((float) v);
      case 3 -> settings.setVoiceVolume((float) v);
      case 4 -> {
        long min = 500, max = 5000;
        long val = Math.round(min + v * (max - min));
        settings.setAutoPlayDelay(val);
      }
      case 5 -> settings.setSkipUnreadText(v >= 0.5);
      case 6 -> settings.setSkipAfterChoices(v >= 0.5);
      case 7 -> settings.setPhysicsFixedStepMs(Math.round(v * 50)); // 0..50 ms
      case 8 -> settings.setPhysicsMaxSubSteps(1 + (int) Math.round(v * 7)); // 1..8
      case 9 -> settings.setPhysicsDefaultFriction(v);
      case 10 -> {
        // slider not used; interpret >0.5 as save
        if (v > 0.5) saveBindingsToDisk(); else loadBindingsFromDisk();
      }
      default -> {}
    }
    // Live-apply volumes
    if (audio != null) {
      audio.setBgmVolume(settings.getBgmVolume());
      audio.setSfxVolume(settings.getSfxVolume());
      audio.setVoiceVolume(settings.getVoiceVolume());
    }
  }

  @Override
  public void onExit() {
    try {
      new VnSettingsStore().save(settings);
    } catch (Exception ignored) {}
  }

  public String getBindingStatus() { return bindingStatus; }

  public void loadBindingsFromDisk() {
    ActionBindingProfileStore store = new ActionBindingProfileStore(settings.getInputProfilePath());
    bindings = store.load();
    settings.setInputProfileSerialized(bindings.serialize());
    bindingStatus = "Loaded from " + store.getPath();
  }

  public ActionBindingProfile getBindings() { return bindings; }

  private void saveBindingsToDisk() {
    ActionBindingProfileStore store = new ActionBindingProfileStore(settings.getInputProfilePath());
    try {
      store.save(bindings);
      settings.setInputProfileSerialized(bindings.serialize());
      bindingStatus = "Saved to " + store.getPath();
    } catch (Exception e) {
      bindingStatus = "Save failed";
    }
  }
}
