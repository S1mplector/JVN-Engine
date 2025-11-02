package com.jvn.editor;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.animation.AnimationTimer;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;

import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.fx.scene2d.FxBlitter2D;

public class EditorApp extends Application {
  private Canvas canvas;
  private FxBlitter2D blitter;
  private JesScene2D current;
  private AnimationTimer timer;
  private Label status;
  private File lastOpened;

  public static void main(String[] args) {
    launch(args);
  }

  @Override
  public void start(Stage primaryStage) {
    primaryStage.setTitle("JVN Editor");
    BorderPane root = new BorderPane();

    // Menu
    MenuBar mb = new MenuBar();
    Menu menuFile = new Menu("File");
    MenuItem miOpen = new MenuItem("Open JES...");
    miOpen.setOnAction(e -> doOpen(primaryStage));
    MenuItem miReload = new MenuItem("Reload");
    miReload.setOnAction(e -> doReload());
    menuFile.getItems().addAll(miOpen, miReload);
    mb.getMenus().addAll(menuFile);

    // Toolbar
    HBox toolbar = new HBox(8);
    Button btnOpen = new Button("Open"); btnOpen.setOnAction(e -> doOpen(primaryStage));
    Button btnReload = new Button("Reload"); btnReload.setOnAction(e -> doReload());
    status = new Label("Ready");
    toolbar.getChildren().addAll(btnOpen, btnReload, status);

    // Canvas viewport
    canvas = new Canvas(1200, 740);
    GraphicsContext gc = canvas.getGraphicsContext2D();
    blitter = new FxBlitter2D(gc);

    // Layout
    BorderPane top = new BorderPane();
    top.setTop(mb);
    top.setCenter(toolbar);
    root.setTop(top);
    root.setCenter(canvas);

    Scene scene = new Scene(root, 1200, 800);
    primaryStage.setScene(scene);
    primaryStage.show();

    // Resize handling
    scene.widthProperty().addListener((o,ov,nv) -> canvas.setWidth(nv.doubleValue()));
    scene.heightProperty().addListener((o,ov,nv) -> canvas.setHeight(nv.doubleValue() - 60));

    // Timer
    timer = new AnimationTimer() {
      long last = -1;
      @Override public void handle(long now) {
        if (last < 0) { last = now; return; }
        long dt = (now - last) / 1_000_000L;
        last = now;
        render(dt);
      }
    };
    timer.start();
  }

  private void doOpen(Stage stage) {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Open JES Script");
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JES scripts", "*.jes", "*.txt"));
      File f = fc.showOpenDialog(stage);
      if (f == null) return;
      try (InputStream in = new FileInputStream(f)) {
        current = JesLoader.load(in);
      }
      lastOpened = f;
      status.setText("Loaded: " + f.getName());
    } catch (Exception ex) {
      status.setText("Load failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to load: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doReload() {
    if (lastOpened == null) return;
    try (InputStream in = new FileInputStream(lastOpened)) {
      current = JesLoader.load(in);
      status.setText("Reloaded: " + lastOpened.getName());
    } catch (Exception ex) {
      status.setText("Reload failed");
    }
  }

  private void render(long deltaMs) {
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    GraphicsContext gc = canvas.getGraphicsContext2D();
    gc.setFill(javafx.scene.paint.Color.color(0.08,0.08,0.1));
    gc.fillRect(0, 0, w, h);
    blitter.setViewport(w, h);
    if (current != null) {
      current.update(deltaMs);
      current.render(blitter, w, h);
    } else {
      gc.setFill(javafx.scene.paint.Color.WHITE);
      gc.fillText("Open a JES file to preview", 20, 30);
    }
  }
}
