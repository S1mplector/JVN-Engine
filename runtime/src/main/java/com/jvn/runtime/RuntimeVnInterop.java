package com.jvn.runtime;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.engine.Engine;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.menu.SettingsScene;
import com.jvn.core.scene.Scene;
import com.jvn.core.vn.*;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.runtime.JesScene2D;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class RuntimeVnInterop implements VnInterop {
  private final Engine engine;
  private final DefaultVnInterop base = new DefaultVnInterop();

  public RuntimeVnInterop(Engine engine) { this.engine = engine; }

  @Override
  public VnInteropResult handle(VnExternalCommand command, VnScene scene) {
    String provider = safe(command.getProvider());
    String payload = safe(command.getPayload());
    switch (provider.toLowerCase()) {
      case "jes":
        return handleJes(payload, scene);
      case "menu":
        return handleMenu(payload, scene);
      case "vns":
        return handleVns(payload, scene);
      default:
        return base.handle(command, scene);
    }
  }

  private VnInteropResult handleJes(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) return VnInteropResult.advance();
    String cmd = toks[0].toLowerCase();
    try {
      switch (cmd) {
        case "push": {
          String script = toks.length >= 2 ? toks[1] : null;
          JesScene2D js = loadJes(script);
          if (js != null) engine.scenes().push(js);
          return VnInteropResult.advance();
        }
        case "replace": {
          String script = toks.length >= 2 ? toks[1] : null;
          JesScene2D js = loadJes(script);
          if (js != null) engine.scenes().replace(js);
          return VnInteropResult.advance();
        }
        case "pop": {
          engine.scenes().pop();
          return VnInteropResult.advance();
        }
        case "call": {
          String name = toks.length >= 2 ? toks[1] : null;
          if (name != null) {
            Scene top = engine.scenes().peek();
            if (top instanceof JesScene2D jes) {
              // no generic arg map in payload yet; extend later
              jes.registerCall(name, m -> {}); // ensure name exists, then invoke
              java.util.function.Consumer<java.util.Map<String,Object>> h = null; // not used now
              // direct timeline call action is handled inside Jes scene via timeline; here we just signal
            }
          }
          return VnInteropResult.advance();
        }
      }
    } catch (Exception ignored) {}
    return VnInteropResult.advance();
  }

  private JesScene2D loadJes(String script) throws Exception {
    if (script == null || script.isBlank()) return null;
    AssetCatalog cat = new AssetCatalog();
    try (InputStream in = cat.open(AssetType.SCRIPT, script)) {
      return JesLoader.load(in);
    }
  }

  private VnInteropResult handleMenu(String payload, VnScene scene) {
    String[] toks = split(payload);
    if (toks.length == 0) return VnInteropResult.advance();
    String kind = toks[0].toLowerCase();
    switch (kind) {
      case "settings": {
        VnSettings s = scene.getState().getSettings();
        SettingsScene m = new SettingsScene(s, scene.getAudioFacade());
        engine.scenes().push(m);
        return VnInteropResult.advance();
      }
      case "main": {
        String script = toks.length >= 2 ? toks[1] : "demo.vns";
        MainMenuScene m = new MainMenuScene(engine, new VnSettings(), new com.jvn.core.vn.save.VnSaveManager(), script, scene.getAudioFacade());
        engine.scenes().push(m);
        return VnInteropResult.advance();
      }
      default:
        return VnInteropResult.advance();
    }
  }

  private VnInteropResult handleVns(String payload, VnScene scene) {
    // payload: push|replace scriptName [label LABEL]
    List<String> toks = new ArrayList<>(java.util.Arrays.asList(split(payload)));
    if (toks.isEmpty()) return VnInteropResult.advance();
    String cmd = toks.remove(0).toLowerCase();
    String script = toks.isEmpty() ? null : toks.remove(0);
    String label = null;
    if (!toks.isEmpty() && "label".equalsIgnoreCase(toks.get(0))) {
      toks.remove(0);
      if (!toks.isEmpty()) label = toks.remove(0);
    }
    if (script == null) return VnInteropResult.advance();
    try {
      VnScene newScene = loadVnScene(script, scene);
      if (newScene == null) return VnInteropResult.advance();
      if (label != null) newScene.getState().jumpToLabel(label);
      switch (cmd) {
        case "push":
          engine.scenes().push(newScene);
          break;
        case "replace":
        default:
          engine.scenes().replace(newScene);
          break;
      }
    } catch (Exception ignored) {}
    return VnInteropResult.advance();
  }

  private VnScene loadVnScene(String script, VnScene current) throws Exception {
    AssetCatalog assets = new AssetCatalog();
    try (InputStream in = assets.open(AssetType.SCRIPT, script)) {
      VnScenario sc = new VnScriptParser().parse(in);
      VnScene vn = new VnScene(sc);
      if (current.getAudioFacade() != null) vn.setAudioFacade(current.getAudioFacade());
      // carry settings
      copySettings(current.getState().getSettings(), vn.getState().getSettings());
      vn.setInterop(this);
      return vn;
    }
  }

  private void copySettings(VnSettings src, VnSettings dst) {
    if (src == null || dst == null) return;
    dst.setTextSpeed(src.getTextSpeed());
    dst.setBgmVolume(src.getBgmVolume());
    dst.setSfxVolume(src.getSfxVolume());
    dst.setVoiceVolume(src.getVoiceVolume());
    dst.setAutoPlayDelay(src.getAutoPlayDelay());
    dst.setSkipUnreadText(src.isSkipUnreadText());
    dst.setSkipAfterChoices(src.isSkipAfterChoices());
  }

  private static String safe(String s) { return s == null ? "" : s; }
  private static String[] split(String s) { return (s == null ? "" : s.trim()).isEmpty() ? new String[0] : s.trim().split("\\s+"); }
}
