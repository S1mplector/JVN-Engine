package com.jvn.core.engine;

import com.jvn.core.config.ApplicationConfig;
import com.jvn.core.scene.Scene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EngineFixedStepTest {

  @Test
  void splitsDeltaIntoFixedSteps() {
    Engine engine = new Engine(ApplicationConfig.builder().build());
    CountingScene scene = new CountingScene();
    engine.scenes().push(scene);
    engine.setDeltaSmoothing(0);
    engine.setFixedUpdateStepMs(10, 5);
    engine.start();

    engine.update(35); // should produce three 10 ms ticks and leave remainder

    assertEquals(3, scene.updateCount);
    assertEquals(30, scene.totalDelta);
  }

  private static final class CountingScene implements Scene {
    int updateCount = 0;
    long totalDelta = 0;
    @Override public void update(long deltaMs) {
      updateCount++;
      totalDelta += deltaMs;
    }
  }
}
