package com.jvn.audio.simp3;

import com.jvn.core.assets.AssetPaths;
import com.jvn.core.assets.AssetType;
import com.jvn.core.audio.AudioFacade;
import com.musicplayer.core.audio.HybridAudioEngine;
import com.musicplayer.core.audio.AudioEngine;
import com.musicplayer.data.models.Song;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Simp3AudioService implements AudioFacade {
  private static final Logger log = LoggerFactory.getLogger(Simp3AudioService.class);

  // BGM via HybridAudioEngine (broad format support)
  private AudioEngine bgmEngine = new HybridAudioEngine();
  private AudioEngine crossEngine = null;
  private volatile boolean loopBgm = false;
  private volatile double bgmVolume = 0.7;
  private volatile double voiceVolume = 1.0;
  private volatile double sfxVolume = 0.8;
  private volatile File bgmTempFile = null; // cached last temp file if from classpath
  private Song bgmSong = null;
  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "simp3-audio-fader"); t.setDaemon(true); return t; });

  // SFX/Voice via lightweight AudioEngine instances
  private final List<AudioEngine> sfxEngines = new ArrayList<>();
  private final List<AudioEngine> voiceEngines = new ArrayList<>();

  @Override
  public synchronized void playBgm(String trackId, boolean loop) {
    try {
      File audioFile = resolveToFile(AssetType.AUDIO, trackId);
      if (audioFile == null || !audioFile.exists()) {
        log.warn("BGM file not found for trackId={}", trackId);
        return;
      }
      this.loopBgm = loop;
      this.bgmSong = new Song(0, trackId, null, null, null, 0, audioFile.getAbsolutePath(), 0, 0);

      if (!bgmEngine.loadSong(bgmSong)) {
        log.warn("Failed to load BGM: {}", trackId);
        return;
      }
      bgmEngine.setOnSongEnded(() -> {
        if (loopBgm) {
          try {
            bgmEngine.seek(0);
            bgmEngine.play();
          } catch (Exception e) {
            log.debug("Loop failed, reloading song", e);
            try {
              bgmEngine.loadSong(bgmSong);
              bgmEngine.play();
            } catch (Exception ignored) {}
          }
        }
      });
      bgmEngine.setVolume(bgmVolume);
      bgmEngine.play();
    } catch (Exception e) {
      log.error("Error playing BGM {}", trackId, e);
    }
  }

  @Override
  public synchronized void stopBgm() {
    try {
      if (crossEngine != null) { safeStop(crossEngine); crossEngine = null; }
      if (bgmEngine != null) {
        safeStop(bgmEngine);
      }
    } catch (Exception e) {
      log.debug("stopBgm error", e);
    }
  }

  @Override
  public synchronized void playSfx(String sfxId) {
    try {
      File audioFile = resolveToFile(AssetType.AUDIO, sfxId);
      if (audioFile == null || !audioFile.exists()) return;
      AudioEngine eng = new HybridAudioEngine();
      Song song = new Song(0, sfxId, null, null, null, 0, audioFile.getAbsolutePath(), 0, 0);
      if (!eng.loadSong(song)) return;
      eng.setVolume(sfxVolume);
      eng.setOnSongEnded(() -> {
        try { eng.stop(); } catch (Exception ignored) {}
        try { eng.dispose(); } catch (Exception ignored) {}
        synchronized (Simp3AudioService.this) { sfxEngines.remove(eng); }
      });
      sfxEngines.add(eng);
      cleanupEngines(sfxEngines);
      eng.play();
    } catch (Exception e) {
      log.debug("playSfx error", e);
    }
  }

  @Override
  public synchronized void playVoice(String voiceId) {
    try {
      File audioFile = resolveToFile(AssetType.AUDIO, voiceId);
      if (audioFile == null || !audioFile.exists()) return;
      AudioEngine eng = new HybridAudioEngine();
      Song song = new Song(0, voiceId, null, null, null, 0, audioFile.getAbsolutePath(), 0, 0);
      if (!eng.loadSong(song)) return;
      eng.setVolume(voiceVolume);
      eng.setOnSongEnded(() -> {
        try { eng.stop(); } catch (Exception ignored) {}
        try { eng.dispose(); } catch (Exception ignored) {}
        synchronized (Simp3AudioService.this) { voiceEngines.remove(eng); }
      });
      voiceEngines.add(eng);
      cleanupEngines(voiceEngines);
      eng.play();
    } catch (Exception e) {
      log.debug("playVoice error", e);
    }
  }

  @Override
  public synchronized void setBgmVolume(float volume) {
    this.bgmVolume = clamp(volume);
    try { if (bgmEngine != null) bgmEngine.setVolume(bgmVolume); } catch (Exception ignored) {}
  }

  @Override
  public synchronized void setSfxVolume(float volume) {
    this.sfxVolume = clamp(volume);
    for (AudioEngine e : new ArrayList<>(sfxEngines)) {
      try { e.setVolume(sfxVolume); } catch (Exception ignored) {}
    }
  }

  @Override
  public synchronized void setVoiceVolume(float volume) { this.voiceVolume = clamp(volume); }

  // New advanced controls via default methods in AudioFacade
  @Override
  public synchronized void pauseBgm() { try { if (bgmEngine != null) bgmEngine.pause(); } catch (Exception ignored) {} }

  @Override
  public synchronized void resumeBgm() { try { if (bgmEngine != null) { bgmEngine.setVolume(bgmVolume); bgmEngine.play(); } } catch (Exception ignored) {} }

  @Override
  public synchronized void seekBgmSeconds(double seconds) { try { if (bgmEngine != null) bgmEngine.seek(Math.max(0.0, seconds)); } catch (Exception ignored) {} }

  @Override
  public synchronized void crossfadeBgm(String trackId, long ms, boolean loop) {
    try {
      File audioFile = resolveToFile(AssetType.AUDIO, trackId);
      if (audioFile == null || !audioFile.exists()) return;
      AudioEngine next = new HybridAudioEngine();
      Song song = new Song(0, trackId, null, null, null, 0, audioFile.getAbsolutePath(), 0, 0);
      if (!next.loadSong(song)) return;
      next.setVolume(0.0);
      next.play();

      final AudioEngine prev = this.bgmEngine;
      final double startVol = this.bgmVolume;
      final long duration = Math.max(0, ms);
      final long start = System.nanoTime();

      if (duration == 0) {
        safeStop(prev);
        this.bgmEngine = next;
        this.loopBgm = loop;
        this.bgmSong = song;
        next.setVolume(startVol);
        return;
      }

      this.crossEngine = next;
      this.loopBgm = loop;
      this.bgmSong = song;

      scheduler.scheduleAtFixedRate(() -> {
        try {
          long elapsedMs = (System.nanoTime() - start) / 1_000_000L;
          double p = Math.min(1.0, Math.max(0.0, elapsedMs / (double) duration));
          double outVol = startVol * (1.0 - p);
          double inVol = startVol * p;
          if (prev != null) prev.setVolume(outVol);
          if (next != null) next.setVolume(inVol);
          if (p >= 1.0) {
            // finalize
            safeStop(prev);
            this.bgmEngine = next;
            this.crossEngine = null;
            // cancel loop on next by assigning callback
            next.setOnSongEnded(() -> { if (this.loopBgm) { try { next.seek(0); next.play(); } catch (Exception ignored) {} } });
            throw new StopIteration();
          }
        } catch (StopIteration done) {
          throw done; // rethrow to outer catcher
        } catch (Throwable t) {
          log.debug("crossfade tick error", t);
        }
      }, 0, 33, TimeUnit.MILLISECONDS);
    } catch (Exception e) {
      log.debug("crossfadeBgm error", e);
    }
  }

  private static class StopIteration extends RuntimeException {}

  private void cleanupEngines(List<AudioEngine> list) {
    Iterator<AudioEngine> it = list.iterator();
    while (it.hasNext()) {
      AudioEngine e = it.next();
      try {
        if (!e.isPlaying()) {
          try { e.dispose(); } catch (Exception ignored) {}
          it.remove();
        }
      } catch (Exception ignored) {}
    }
  }

  private void safeStop(AudioEngine eng) {
    try { eng.stop(); } catch (Exception ignored) {}
    try { eng.dispose(); } catch (Exception ignored) {}
  }

  private File resolveToFile(AssetType type, String id) {
    try {
      // Try direct file on disk via classpath resource
      String built = AssetPaths.build(type, id);
      URL url = getClass().getClassLoader().getResource(built);
      if (url != null && "file".equalsIgnoreCase(url.getProtocol())) {
        return new File(url.toURI());
      }
      // Fallback: copy resource stream to temp file
      try (InputStream in = getClass().getClassLoader().getResourceAsStream(built)) {
        if (in == null) return null;
        if (bgmTempFile == null || !bgmTempFile.exists()) {
          bgmTempFile = Files.createTempFile("jvn_bgm_", "_audio").toFile();
          bgmTempFile.deleteOnExit();
        }
        try (FileOutputStream out = new FileOutputStream(bgmTempFile, false)) {
          in.transferTo(out);
        }
        return bgmTempFile;
      }
    } catch (Exception e) {
      log.debug("resolveToFile error", e);
      return null;
    }
  }

  private double clamp(double v) { return v < 0.0 ? 0.0 : Math.min(1.0, v); }
  private float clamp(float v) { return (float) clamp((double) v); }
}
