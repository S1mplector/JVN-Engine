package com.jvn.billiards.app;

import com.jvn.billiards.scene.BilliardsHybridScene;
import com.jvn.core.graphics.Camera2D;
import com.jvn.core.input.Input;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.runtime.JesScene2D;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class BilliardsGameApp extends Application {
  private Canvas canvas;
  private FxBlitter2D blitter;
  private final Input input = new Input();
  private final Camera2D camera = new Camera2D();
  private BilliardsHybridScene scene2D;
  private AnimationTimer timer;

  public static void main(String[] args) { launch(args); }

  @Override
  public void start(Stage stage) {
    canvas = new Canvas(1200, 700);
    GraphicsContext gc = canvas.getGraphicsContext2D();
    blitter = new FxBlitter2D(gc);

    scene2D = new BilliardsHybridScene();
    scene2D.setInput(input);
    scene2D.setCamera(camera);

    // Try to import JES decorations (labels/timeline/bindings) from sample
    try {
      java.io.File wd = new java.io.File(System.getProperty("user.dir"));
      java.io.File root = wd.getParentFile() != null ? wd.getParentFile() : wd;
      java.io.File jes = new java.io.File(root, "samples/billiards.jes");
      if (jes.exists()) {
        try (java.io.InputStream in = new java.io.FileInputStream(jes)) {
          JesScene2D s = JesLoader.load(in);
          scene2D.importFromJesScene(s, true);
        }
      }
    } catch (Exception ignore) {}

    StackPane root = new StackPane(canvas);
    Scene fxScene = new Scene(root, 1200, 700);
    stage.setTitle("JVN Billiards");
    stage.setScene(fxScene);
    stage.show();

    // Input wiring
    canvas.setFocusTraversable(true);
    canvas.setOnMouseMoved(e -> input.setMousePosition(e.getX(), e.getY()));
    canvas.setOnMouseDragged(e -> input.setMousePosition(e.getX(), e.getY()));
    canvas.setOnScroll(e -> input.addScrollDeltaY(e.getDeltaY()));
    canvas.setOnMousePressed(e -> input.mouseDown(mapButton(e.getButton())));
    canvas.setOnMouseReleased(e -> input.mouseUp(mapButton(e.getButton())));

    fxScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, e -> input.keyDown(mapKey(e.getCode())));
    fxScene.addEventFilter(javafx.scene.input.KeyEvent.KEY_RELEASED, e -> input.keyUp(mapKey(e.getCode())));

    // Resize
    fxScene.widthProperty().addListener((o, ov, nv) -> canvas.setWidth(nv.doubleValue()));
    fxScene.heightProperty().addListener((o, ov, nv) -> canvas.setHeight(nv.doubleValue()));

    // Loop
    timer = new AnimationTimer() {
      long last = -1;
      @Override public void handle(long now) {
        if (last < 0) { last = now; return; }
        long dt = (now - last) / 1_000_000L;
        last = now;
        scene2D.update(dt);
        blitter.setViewport(canvas.getWidth(), canvas.getHeight());
        scene2D.render(blitter, canvas.getWidth(), canvas.getHeight());
        if (scene2D.getInput() != null) scene2D.getInput().endFrame();
      }
    };
    timer.start();
  }

  private static int mapButton(MouseButton b) {
    if (b == MouseButton.PRIMARY) return 1;
    if (b == MouseButton.MIDDLE) return 2;
    if (b == MouseButton.SECONDARY) return 3;
    return 0;
  }

  private static String mapKey(KeyCode code) {
    if (code == null) return "";
    String name = code.getName();
    if (name == null || name.isBlank()) name = code.toString();
    return name.toUpperCase();
  }
}
