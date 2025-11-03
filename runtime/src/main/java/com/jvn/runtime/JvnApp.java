package com.jvn.runtime;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.engine.Engine;
import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.core.localization.Localization;
import com.jvn.core.menu.MainMenuScene;
import com.jvn.fx.FxLauncher;
import com.jvn.fx.audio.FxAudioService;
import com.jvn.core.audio.AudioFacade;
import com.jvn.scripting.jes.JesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class JvnApp {
  private static final Logger log = LoggerFactory.getLogger(JvnApp.class);

  public static void main(String[] args) {
    ApplicationConfig.Builder builder = ApplicationConfig.builder().title("JVN Runtime").width(960).height(540);
    String scriptName = "demo.vns"; // default script under game/scripts/
    String locale = "en";
    boolean launchBilliards = false;
    String ui = "fx"; // fx | swing
    String audioBackend = "fx"; // fx | simp3 | auto
    String jesScript = null;

    for (int i = 0; i < args.length; i++) {
      String a = args[i];
      switch (a) {
        case "--title":
          if (i + 1 < args.length) builder.title(args[++i]);
          break;
        case "--width":
          if (i + 1 < args.length) builder.width(Integer.parseInt(args[++i]));
          break;
        case "--height":
          if (i + 1 < args.length) builder.height(Integer.parseInt(args[++i]));
          break;
        case "--script":
          if (i + 1 < args.length) scriptName = args[++i];
          break;
        case "--locale":
          if (i + 1 < args.length) locale = args[++i];
          break;
        case "--billiards":
          launchBilliards = true;
          break;
        case "--ui":
          if (i + 1 < args.length) ui = args[++i];
          break;
        case "--jes":
          if (i + 1 < args.length) jesScript = args[++i];
          break;
        case "--audio":
          if (i + 1 < args.length) audioBackend = args[++i];
          break;
        default:
          log.warn("Unknown argument: {}", a);
      }
    }

    ApplicationConfig cfg = builder.build();
    
    // Init localization
    Localization.init(locale, Thread.currentThread().getContextClassLoader());

    // Log asset availability on startup
    AssetCatalog assets = new AssetCatalog();
    try {
      int img = assets.listImages().size();
      int aud = assets.listAudio().size();
      int scr = assets.listScripts().size();
      int fnt = assets.listFonts().size();
      log.info("Assets -> images={}, audio={}, scripts={}, fonts={}", img, aud, scr, fnt);
    } catch (Exception e) {
      log.warn("Unable to list assets: {}", e.toString());
    }
    
    // Create engine and show scene
    Engine engine = new Engine(cfg);
    engine.setVnInteropFactory(e -> new RuntimeVnInterop(e));
    engine.start();

    if (jesScript != null) {
      try {
        AssetCatalog cat = new AssetCatalog();
        InputStream in = cat.open(com.jvn.core.assets.AssetType.SCRIPT, jesScript);
        var scene = JesLoader.load(in);
        engine.scenes().push(scene);
      } catch (Exception e) {
        log.warn("Failed to load JES script '{}': {}. Loading inline sample.", jesScript, e.toString());
        try {
          String sample = "scene \"Sample\" {\n" +
              "  entity \"panel\" {\n" +
              "    component Panel2D { x: 0.2 y: 0.2 w: 1.0 h: 0.6 fill: rgb(0.1,0.6,0.2,0.8) }\n" +
              "  }\n" +
              "}\n";
          var in2 = new ByteArrayInputStream(sample.getBytes());
          var scene = JesLoader.load(in2);
          engine.scenes().push(scene);
        } catch (Exception ex) {
          log.warn("Inline JES sample failed: {}", ex.toString());
        }
      }
    } else if (launchBilliards) {
      log.warn("Billiards module is not available; ignoring --billiards flag.");
    } else {
      VnSettings settingsModel = new VnSettings();
      VnSaveManager saveManager = new VnSaveManager();
      AudioFacade audio = null;
      if ("simp3".equalsIgnoreCase(audioBackend) || "auto".equalsIgnoreCase(audioBackend)) {
        try {
          Class<?> cls = Class.forName("com.jvn.audio.simp3.Simp3AudioService");
          Object inst = cls.getDeclaredConstructor().newInstance();
          audio = (AudioFacade) inst;
        } catch (Throwable t) {
          // Fallback to FX if adapter not on classpath
          audio = new FxAudioService();
        }
      } else {
        audio = new FxAudioService();
      }
      MainMenuScene menu = new MainMenuScene(engine, settingsModel, saveManager, scriptName, audio);
      engine.scenes().push(menu);
    }

    if ("swing".equalsIgnoreCase(ui)) {
      com.jvn.swing.SwingLauncher.launch(engine);
    } else {
      FxLauncher.launch(engine);
    }
  }
}
