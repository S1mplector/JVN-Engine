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
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TextField;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.ListCell;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.animation.AnimationTimer;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import javafx.scene.paint.Color;

import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.JesExporter;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.scripting.jes.runtime.PhysicsBodyEntity2D;
import com.jvn.core.input.Input;
import com.jvn.core.graphics.Camera2D;
import com.jvn.editor.ui.JesCodeEditor;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyCodeCombination;

public class EditorApp extends Application {
  private Canvas canvas;
  private FxBlitter2D blitter;
  private JesScene2D current;
  private AnimationTimer timer;
  private Label status;
  private File lastOpened;
  private Entity2D selected;
  private VBox inspector;
  private VBox sceneGraph;
  private ListView<String> sceneList;
  private Input input = new Input();
  private TabPane tabs;
  private JesCodeEditor codeView;
  private Camera2D camera = new Camera2D();
  private boolean panning = false;
  private double panLastX, panLastY;
  private TextField sceneFilter;
  private javafx.collections.ObservableList<String> sceneItems;
  private java.util.List<String> allNames;

  public static void main(String[] args) {
    launch(args);
  }

  private void openSample(String absolutePath) {
    try {
      File f = new File(absolutePath);
      if (!f.exists()) {
        Alert a = new Alert(Alert.AlertType.ERROR, "Sample not found: " + absolutePath);
        a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
        return;
      }
      try (InputStream in = new FileInputStream(f)) {
        current = JesLoader.load(in);
      }
      lastOpened = f;
      status.setText("Loaded: " + f.getName());
      if (current != null) { current.setInput(input); current.setCamera(camera); }
      try { String code = Files.readString(f.toPath()); codeView.setText(code); } catch (Exception ignore) {}
      selected = null;
      buildInspector();
      buildSceneGraph();
      tabs.getSelectionModel().selectFirst();
    } catch (Exception ex) {
      status.setText("Load failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to load: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void fitCameraToEntity(Entity2D e) {
    if (e == null) return;
    Rect r = null;
    if (e instanceof Panel2D p) r = new Rect(e.getX(), e.getY(), p.getWidth(), p.getHeight());
    else if (e instanceof com.jvn.core.scene2d.Sprite2D s) {
      double sx = e.getX() - s.getOriginX() * s.getWidth();
      double sy = e.getY() - s.getOriginY() * s.getHeight();
      r = new Rect(sx, sy, s.getWidth(), s.getHeight());
    } else if (e instanceof PhysicsBodyEntity2D pb) {
      var b = pb.getBody(); if (b != null) {
        if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
          double cx = b.getCircle().x, cy = b.getCircle().y, rr = b.getCircle().r;
          r = new Rect(cx - rr, cy - rr, rr * 2, rr * 2);
        } else { var a = b.getAabb(); r = new Rect(a.x, a.y, a.w, a.h); }
      }
    } else if (e instanceof Label2D) { r = new Rect(e.getX() - 50, e.getY() - 15, 100, 30); }
    if (r == null) return;
    double w = canvas.getWidth(), h = canvas.getHeight(), pad = 40;
    double zx = (w - pad) / Math.max(1, r.w);
    double zy = (h - pad) / Math.max(1, r.h);
    double z = Math.max(0.05, Math.min(zx, zy));
    camera.setZoom(z);
    camera.setPosition(r.x + r.w / 2.0 - w / (2.0 * z), r.y + r.h / 2.0 - h / (2.0 * z));
  }

  private void resetCamera() {
    camera.setPosition(0, 0);
    camera.setZoom(1.0);
  }

  private void fitCameraToContent() {
    if (current == null) return;
    var bounds = computeSceneBounds();
    if (bounds == null) return;
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    double pad = 40;
    double zx = (w - pad) / Math.max(1, bounds.w);
    double zy = (h - pad) / Math.max(1, bounds.h);
    double z = Math.max(0.05, Math.min(zx, zy));
    camera.setZoom(z);
    camera.setPosition(bounds.x + bounds.w / 2.0 - w / (2.0 * z), bounds.y + bounds.h / 2.0 - h / (2.0 * z));
  }

  private static class Rect { double x,y,w,h; Rect(double x,double y,double w,double h){this.x=x;this.y=y;this.w=w;this.h=h;} }

  private Rect computeSceneBounds() {
    if (current == null) return null;
    double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
    double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
    for (Entity2D e : ((Scene2DBase)current).getChildren()) {
      if (!e.isVisible()) continue;
      if (e instanceof Panel2D p) {
        minX = Math.min(minX, e.getX());
        minY = Math.min(minY, e.getY());
        maxX = Math.max(maxX, e.getX() + p.getWidth());
        maxY = Math.max(maxY, e.getY() + p.getHeight());
      } else if (e instanceof com.jvn.core.scene2d.Sprite2D s) {
        double sx = e.getX() - s.getOriginX() * s.getWidth();
        double sy = e.getY() - s.getOriginY() * s.getHeight();
        minX = Math.min(minX, sx);
        minY = Math.min(minY, sy);
        maxX = Math.max(maxX, sx + s.getWidth());
        maxY = Math.max(maxY, sy + s.getHeight());
      } else if (e instanceof PhysicsBodyEntity2D pb) {
        var b = pb.getBody(); if (b != null) {
          if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
            double cx = b.getCircle().x, cy = b.getCircle().y, r = b.getCircle().r;
            minX = Math.min(minX, cx - r); minY = Math.min(minY, cy - r);
            maxX = Math.max(maxX, cx + r); maxY = Math.max(maxY, cy + r);
          } else {
            var a = b.getAabb();
            minX = Math.min(minX, a.x); minY = Math.min(minY, a.y);
            maxX = Math.max(maxX, a.x + a.w); maxY = Math.max(maxY, a.y + a.h);
          }
        }
      } else if (e instanceof Label2D) {
        // approximate label bounds
        minX = Math.min(minX, e.getX() - 50);
        minY = Math.min(minY, e.getY() - 15);
        maxX = Math.max(maxX, e.getX() + 50);
        maxY = Math.max(maxY, e.getY());
      }
    }
    if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(maxX) || !Double.isFinite(maxY)) return null;
    return new Rect(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
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
    MenuItem miSave = new MenuItem("Save");
    miSave.setOnAction(e -> doSave(primaryStage));
    MenuItem miSaveAs = new MenuItem("Save As...");
    miSaveAs.setOnAction(e -> doSaveAs(primaryStage));
    miOpen.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.SHORTCUT_DOWN));
    miReload.setAccelerator(new KeyCodeCombination(KeyCode.R, KeyCombination.SHORTCUT_DOWN));
    miSave.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN));
    miSaveAs.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    menuFile.getItems().addAll(miOpen, miReload, miSave, miSaveAs);

    Menu menuCode = new Menu("Code");
    MenuItem miApplyCode = new MenuItem("Apply Code");
    miApplyCode.setOnAction(e -> applyCodeFromEditor());
    miApplyCode.setAccelerator(new KeyCodeCombination(KeyCode.ENTER, KeyCombination.SHORTCUT_DOWN));
    menuCode.getItems().addAll(miApplyCode);

    Menu menuView = new Menu("View");
    MenuItem miFit = new MenuItem("Fit to Content");
    miFit.setOnAction(e -> fitCameraToContent());
    miFit.setAccelerator(new KeyCodeCombination(KeyCode.F, KeyCombination.SHORTCUT_DOWN));
    MenuItem miReset = new MenuItem("Reset Camera");
    miReset.setOnAction(e -> resetCamera());
    miReset.setAccelerator(new KeyCodeCombination(KeyCode.DIGIT0, KeyCombination.SHORTCUT_DOWN));
    menuView.getItems().addAll(miFit, miReset);

    Menu menuSamples = new Menu("Samples");
    MenuItem miBilliards = new MenuItem("Open billiards.jes");
    miBilliards.setOnAction(e -> openSample("/Users/ilgazmehmetoglu/Desktop/Projects/Percept2 Engine/samples/billiards.jes"));
    MenuItem miShowcase = new MenuItem("Open showcase.jes");
    miShowcase.setOnAction(e -> openSample("/Users/ilgazmehmetoglu/Desktop/Projects/Percept2 Engine/samples/showcase.jes"));
    menuSamples.getItems().addAll(miBilliards, miShowcase);

    mb.getMenus().addAll(menuFile, menuCode, menuView, menuSamples);

    // Toolbar
    HBox toolbar = new HBox(8);
    Button btnOpen = new Button("Open"); btnOpen.setOnAction(e -> doOpen(primaryStage));
    Button btnReload = new Button("Reload"); btnReload.setOnAction(e -> doReload());
    Button btnApply = new Button("Apply Code"); btnApply.setOnAction(e -> applyCodeFromEditor());
    Button btnFit = new Button("Fit"); btnFit.setOnAction(e -> fitCameraToContent());
    Button btnReset = new Button("Reset"); btnReset.setOnAction(e -> resetCamera());
    btnApply.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER && e.isShortcutDown()) applyCodeFromEditor(); });
    status = new Label("Ready");
    toolbar.getChildren().addAll(btnOpen, btnReload, btnApply, btnFit, btnReset, status);

    // Canvas viewport
    canvas = new Canvas(1200, 740);
    GraphicsContext gc = canvas.getGraphicsContext2D();
    blitter = new FxBlitter2D(gc);
    canvas.setOnMouseClicked(e -> {
      pick(e.getX(), e.getY());
      buildInspector();
    });
    canvas.setOnMouseMoved(e -> { input.setMousePosition(e.getX(), e.getY()); });
    canvas.setOnMouseDragged(e -> {
      input.setMousePosition(e.getX(), e.getY());
      if (panning) {
        double dx = e.getX() - panLastX;
        double dy = e.getY() - panLastY;
        panLastX = e.getX(); panLastY = e.getY();
        camera.setPosition(camera.getX() - dx / camera.getZoom(), camera.getY() - dy / camera.getZoom());
      }
    });
    canvas.setOnScroll(e -> {
      input.addScrollDeltaY(e.getDeltaY());
      double z = camera.getZoom();
      double worldX = camera.getX() + e.getX() / z;
      double worldY = camera.getY() + e.getY() / z;
      double factor = Math.pow(1.05, e.getDeltaY() / 40.0);
      double newZ = z * factor;
      camera.setZoom(newZ);
      camera.setPosition(worldX - e.getX() / newZ, worldY - e.getY() / newZ);
    });
    canvas.setOnMousePressed(e -> {
      input.mouseDown(mapButton(e.getButton()));
      if (e.getButton() == MouseButton.MIDDLE || e.getButton() == MouseButton.SECONDARY) {
        panning = true; panLastX = e.getX(); panLastY = e.getY();
      }
    });
    canvas.setOnMouseReleased(e -> {
      input.mouseUp(mapButton(e.getButton()));
      if (e.getButton() == MouseButton.MIDDLE || e.getButton() == MouseButton.SECONDARY) {
        panning = false;
      }
    });

    // Layout
    BorderPane top = new BorderPane();
    top.setTop(mb);
    top.setCenter(toolbar);
    root.setTop(top);
    // Center: TabPane with Canvas and JES Code editor
    codeView = new JesCodeEditor();
    tabs = new TabPane();
    Tab tabCanvas = new Tab("Canvas", canvas); tabCanvas.setClosable(false);
    Tab tabCode = new Tab("JES Code", codeView); tabCode.setClosable(false);
    tabs.getTabs().addAll(tabCanvas, tabCode);
    root.setCenter(tabs);
    inspector = new VBox(8);
    inspector.setMinWidth(280);
    inspector.setPrefWidth(320);
    ScrollPane inspectorScroll = new ScrollPane(inspector);
    inspectorScroll.setFitToWidth(true);
    root.setRight(inspectorScroll);
    sceneGraph = new VBox(8);
    sceneGraph.setMinWidth(200);
    sceneGraph.setPrefWidth(240);
    root.setLeft(sceneGraph);

    Scene scene = new Scene(root, 1200, 800);
    primaryStage.setScene(scene);
    primaryStage.show();
    canvas.setFocusTraversable(true);
    scene.addEventHandler(KeyEvent.KEY_PRESSED, e -> input.keyDown(mapKey(e.getCode())));
    scene.addEventHandler(KeyEvent.KEY_RELEASED, e -> input.keyUp(mapKey(e.getCode())));

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
      if (current != null) current.setInput(input);
      if (current != null) current.setCamera(camera);
      try { String code = Files.readString(f.toPath()); codeView.setText(code); } catch (Exception ignore) {}
      selected = null;
      buildInspector();
      buildSceneGraph();
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
      if (current != null) current.setInput(input);
      if (current != null) current.setCamera(camera);
      try { String code = Files.readString(lastOpened.toPath()); codeView.setText(code); } catch (Exception ignore) {}
      selected = null;
      buildInspector();
      buildSceneGraph();
    } catch (Exception ex) {
      status.setText("Reload failed");
    }
  }

  private void applyCodeFromEditor() {
    try {
      String code = codeView.getText();
      if (code == null || code.isBlank()) return;
      current = JesLoader.load(code);
      if (current != null) {
        current.setInput(input);
        current.setCamera(camera);
        selected = null;
        buildInspector();
        buildSceneGraph();
        status.setText("Applied code to scene");
        tabs.getSelectionModel().selectFirst();
      }
    } catch (Exception ex) {
      status.setText("Apply failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to apply code: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doSave(Stage stage) {
    if (current == null) return;
    if (lastOpened == null) { doSaveAs(stage); return; }
    try {
      String sceneName = stripExt(lastOpened.getName());
      String content = JesExporter.export(current, sceneName);
      try (FileWriter fw = new FileWriter(lastOpened)) { fw.write(content); }
      if (codeView != null) codeView.setText(content);
      status.setText("Saved: " + lastOpened.getName());
    } catch (Exception ex) {
      status.setText("Save failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to save: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doSaveAs(Stage stage) {
    if (current == null) return;
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Save JES Script");
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("JES scripts", "*.jes", "*.txt"));
      if (lastOpened != null) fc.setInitialFileName(lastOpened.getName());
      File f = fc.showSaveDialog(stage);
      if (f == null) return;
      String fname = f.getName();
      if (!(fname.endsWith(".jes") || fname.endsWith(".txt"))) {
        f = new File(f.getAbsolutePath() + ".jes");
      }
      String sceneName = stripExt(f.getName());
      String content = JesExporter.export(current, sceneName);
      try (FileWriter fw = new FileWriter(f)) { fw.write(content); }
      lastOpened = f;
      if (codeView != null) codeView.setText(content);
      status.setText("Saved: " + f.getName());
    } catch (Exception ex) {
      status.setText("Save As failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to save as: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private static String stripExt(String name) {
    if (name == null) return "scene";
    int i = name.lastIndexOf('.');
    return (i > 0) ? name.substring(0, i) : name;
  }

  private void render(long deltaMs) {
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    GraphicsContext gc = canvas.getGraphicsContext2D();
    gc.setFill(javafx.scene.paint.Color.color(0.08,0.08,0.1));
    gc.fillRect(0, 0, w, h);
    // Grid overlay
    if (camera != null) {
      gc.setStroke(javafx.scene.paint.Color.color(1,1,1,0.06));
      double base = 50.0;
      double z = Math.max(0.0001, camera.getZoom());
      double step = base * z;
      if (step >= 8) {
        double ox = (-camera.getX() * z) % step;
        double oy = (-camera.getY() * z) % step;
        for (double x = ox; x <= w; x += step) gc.strokeLine(x, 0, x, h);
        for (double y = oy; y <= h; y += step) gc.strokeLine(0, y, w, y);
      }
    }
    blitter.setViewport(w, h);
    if (current != null) {
      current.update(deltaMs);
      current.render(blitter, w, h);
      drawSelectionOverlay();
      if (current.getInput() != null) current.getInput().endFrame();
    } else {
      gc.setFill(javafx.scene.paint.Color.WHITE);
      gc.fillText("Open a JES file to preview", 20, 30);
    }
  }

  private void drawSelectionOverlay() {
    if (selected == null) return;
    blitter.push();
    blitter.setStroke(0.2, 0.8, 1, 1);
    blitter.setStrokeWidth(1.0);
    if (selected instanceof Panel2D p) {
      blitter.strokeRect(selected.getX(), selected.getY(), p.getWidth(), p.getHeight());
    } else if (selected instanceof PhysicsBodyEntity2D pb) {
      RigidBody2D b = pb.getBody();
      if (b != null) {
        if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
          blitter.strokeCircle(b.getCircle().x, b.getCircle().y, b.getCircle().r);
        } else {
          var aabb = b.getAabb();
          blitter.strokeRect(aabb.x, aabb.y, aabb.w, aabb.h);
        }
      }
    }
    blitter.pop();
  }

  private void pick(double x, double y) {
    if (current == null) return;
    selected = null;
    java.util.List<Entity2D> list = ((Scene2DBase)current).getChildren();
    for (int i = list.size() - 1; i >= 0; i--) {
      Entity2D e = list.get(i);
      if (!e.isVisible()) continue;
      if (e instanceof Panel2D p) {
        if (x >= e.getX() && y >= e.getY() && x <= e.getX() + p.getWidth() && y <= e.getY() + p.getHeight()) { selected = e; break; }
      } else if (e instanceof PhysicsBodyEntity2D pb) {
        RigidBody2D b = pb.getBody(); if (b == null) continue;
        if (b.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
          double dx = x - b.getCircle().x, dy = y - b.getCircle().y; double rr = b.getCircle().r; if (dx*dx + dy*dy <= rr*rr) { selected = e; break; }
        } else {
          var a = b.getAabb(); if (x >= a.x && y >= a.y && x <= a.x + a.w && y <= a.y + a.h) { selected = e; break; }
        }
      } else if (e instanceof com.jvn.core.scene2d.Sprite2D s) {
        double sx = e.getX() - s.getOriginX() * s.getWidth();
        double sy = e.getY() - s.getOriginY() * s.getHeight();
        if (x >= sx && y >= sy && x <= sx + s.getWidth() && y <= sy + s.getHeight()) { selected = e; break; }
      } else if (e instanceof Label2D) {
        // Rough bounding box for labels
        double w = 100; double h = 30; // Estimate
        if (x >= e.getX() - w/2 && y >= e.getY() - h && x <= e.getX() + w/2 && y <= e.getY()) { selected = e; break; }
      }
    }
  }

  private void buildInspector() {
    inspector.getChildren().clear();
    if (selected == null) { inspector.getChildren().add(new Label("No selection")); return; }
    inspector.getChildren().add(new Label("Selected: " + selected.getClass().getSimpleName()));

    // Common position controls
    var posX = makeNumberField("x", selected.getX(), v -> { selected.setPosition(v, selected.getY()); });
    var posY = makeNumberField("y", selected.getY(), v -> { selected.setPosition(selected.getX(), v); });
    inspector.getChildren().addAll(posX, posY);

    if (selected instanceof Panel2D p) {
      var w = makeNumberField("width", p.getWidth(), v -> { p.setSize(v, p.getHeight()); });
      var h = makeNumberField("height", p.getHeight(), v -> { p.setSize(p.getWidth(), v); });
      inspector.getChildren().addAll(w, h);
    } else if (selected instanceof PhysicsBodyEntity2D pb) {
      RigidBody2D body = pb.getBody(); if (body != null) {
        var mass = makeNumberField("mass", body.getMass(), v -> body.setMass(v));
        var rest = makeNumberField("restitution", body.getRestitution(), v -> body.setRestitution(v));
        CheckBox cbStatic = new CheckBox("static"); cbStatic.setSelected(body.isStatic()); cbStatic.setOnAction(e -> body.setStatic(cbStatic.isSelected()));
        CheckBox cbSensor = new CheckBox("sensor"); cbSensor.setSelected(body.isSensor()); cbSensor.setOnAction(e -> body.setSensor(cbSensor.isSelected()));
        var vx = makeNumberField("vx", body.getVx(), v -> body.setVelocity(v, body.getVy()));
        var vy = makeNumberField("vy", body.getVy(), v -> body.setVelocity(body.getVx(), v));
        inspector.getChildren().addAll(mass, rest, cbStatic, cbSensor, vx, vy);
      }
    } else if (selected instanceof Label2D lbl) {
      HBox rowText = new HBox(6);
      Label ltext = new Label("text");
      TextField tf = new TextField(lbl.getText() == null ? "" : lbl.getText());
      tf.setOnAction(e -> { lbl.setText(tf.getText()); status.setText("Updated text"); });
      tf.setOnKeyReleased(e -> { if (e.getCode().toString().equals("ENTER")) { lbl.setText(tf.getText()); status.setText("Updated text"); } });
      rowText.getChildren().addAll(ltext, tf);

      var size = makeNumberField("size", lbl.getSize(), v -> { lbl.setFont(lbl.getFontFamily(), v, lbl.isBold()); });

      CheckBox cbBold = new CheckBox("bold");
      cbBold.setSelected(lbl.isBold());
      cbBold.setOnAction(e -> { lbl.setFont(lbl.getFontFamily(), lbl.getSize(), cbBold.isSelected()); });

      HBox rowAlign = new HBox(6);
      Label lAlign = new Label("align");
      ComboBox<Label2D.Align> cbAlign = new ComboBox<>();
      cbAlign.getItems().addAll(Label2D.Align.values());
      cbAlign.getSelectionModel().select(lbl.getAlign());
      cbAlign.setOnAction(e -> { lbl.setAlign(cbAlign.getValue()); });
      rowAlign.getChildren().addAll(lAlign, cbAlign);

      HBox rowColor = new HBox(6);
      Label lColor = new Label("color");
      ColorPicker cp = new ColorPicker(new Color(lbl.getColorR(), lbl.getColorG(), lbl.getColorB(), lbl.getAlpha()));
      cp.setOnAction(e -> { Color c = cp.getValue(); lbl.setColor(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity()); });
      rowColor.getChildren().addAll(lColor, cp);

      var alpha = makeNumberField("alpha", lbl.getAlpha(), v -> { lbl.setColor(lbl.getColorR(), lbl.getColorG(), lbl.getColorB(), v); });

      inspector.getChildren().addAll(rowText, size, cbBold, rowAlign, rowColor, alpha);
    } else if (selected instanceof com.jvn.core.scene2d.Sprite2D sprite) {
      HBox rowImage = new HBox(6);
      Label lImage = new Label("image");
      TextField tfImage = new TextField(sprite.getImagePath() == null ? "" : sprite.getImagePath());
      tfImage.setOnAction(e -> { sprite.setImagePath(tfImage.getText()); status.setText("Updated image path"); });
      rowImage.getChildren().addAll(lImage, tfImage);

      var width = makeNumberField("width", sprite.getWidth(), v -> { sprite.setSize(v, sprite.getHeight()); });
      var height = makeNumberField("height", sprite.getHeight(), v -> { sprite.setSize(sprite.getWidth(), v); });
      var alpha = makeNumberField("alpha", sprite.getAlpha(), v -> { sprite.setAlpha(v); });
      var originX = makeNumberField("originX", sprite.getOriginX(), v -> { sprite.setOrigin(v, sprite.getOriginY()); });
      var originY = makeNumberField("originY", sprite.getOriginY(), v -> { sprite.setOrigin(sprite.getOriginX(), v); });

      inspector.getChildren().addAll(rowImage, width, height, alpha, originX, originY);
    } else if (selected instanceof ParticleEmitter2D emitter) {
      var emRate = makeNumberField("emissionRate", emitter.getEmissionRate(), v -> emitter.setEmissionRate(v));
      var minLife = makeNumberField("minLife", emitter.getMinLife(), v -> emitter.setLifeRange(v, emitter.getMaxLife()));
      var maxLife = makeNumberField("maxLife", emitter.getMaxLife(), v -> emitter.setLifeRange(emitter.getMinLife(), v));
      var minSize = makeNumberField("minSize", emitter.getMinSize(), v -> emitter.setSizeRange(v, emitter.getMaxSize(), emitter.getEndSizeScale()));
      var maxSize = makeNumberField("maxSize", emitter.getMaxSize(), v -> emitter.setSizeRange(emitter.getMinSize(), v, emitter.getEndSizeScale()));
      var endScale = makeNumberField("endSizeScale", emitter.getEndSizeScale(), v -> emitter.setSizeRange(emitter.getMinSize(), emitter.getMaxSize(), v));
      var minSpeed = makeNumberField("minSpeed", emitter.getMinSpeed(), v -> emitter.setSpeedRange(v, emitter.getMaxSpeed()));
      var maxSpeed = makeNumberField("maxSpeed", emitter.getMaxSpeed(), v -> emitter.setSpeedRange(emitter.getMinSpeed(), v));
      var minAngle = makeNumberField("minAngle", emitter.getMinAngle(), v -> emitter.setAngleRange(v, emitter.getMaxAngle()));
      var maxAngle = makeNumberField("maxAngle", emitter.getMaxAngle(), v -> emitter.setAngleRange(emitter.getMinAngle(), v));
      var gravityY = makeNumberField("gravityY", emitter.getGravityY(), v -> emitter.setGravity(v));

      HBox rowStart = new HBox(6);
      Label lStart = new Label("startColor");
      ColorPicker cpStart = new ColorPicker(new Color(emitter.getStartR(), emitter.getStartG(), emitter.getStartB(), emitter.getStartA()));
      cpStart.setOnAction(e -> { Color c = cpStart.getValue(); emitter.setStartColor(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity()); });
      rowStart.getChildren().addAll(lStart, cpStart);

      HBox rowEnd = new HBox(6);
      Label lEnd = new Label("endColor");
      ColorPicker cpEnd = new ColorPicker(new Color(emitter.getEndR(), emitter.getEndG(), emitter.getEndB(), emitter.getEndA()));
      cpEnd.setOnAction(e -> { Color c = cpEnd.getValue(); emitter.setEndColor(c.getRed(), c.getGreen(), c.getBlue(), c.getOpacity()); });
      rowEnd.getChildren().addAll(lEnd, cpEnd);

      HBox rowTexture = new HBox(6);
      Label lTex = new Label("texture");
      TextField tfTex = new TextField(emitter.getTexture() == null ? "" : emitter.getTexture());
      tfTex.setOnAction(e -> emitter.setTexture(tfTex.getText()));
      rowTexture.getChildren().addAll(lTex, tfTex);

      CheckBox cbAdd = new CheckBox("additive"); cbAdd.setSelected(emitter.isAdditive()); cbAdd.setOnAction(e -> emitter.setAdditive(cbAdd.isSelected()));

      inspector.getChildren().addAll(emRate, minLife, maxLife, minSize, maxSize, endScale, minSpeed, maxSpeed, minAngle, maxAngle, gravityY, rowStart, rowEnd, rowTexture, cbAdd);
    }
  }

  private void buildSceneGraph() {
    if (sceneGraph == null) return;
    sceneGraph.getChildren().clear();
    if (current == null) { sceneGraph.getChildren().add(new Label("No scene")); return; }
    sceneGraph.getChildren().add(new Label("Scene Graph"));
    if (sceneFilter == null) {
      sceneFilter = new TextField(); sceneFilter.setPromptText("Filter by name...");
      sceneFilter.setOnKeyReleased(e -> applySceneFilter());
    }
    sceneList = new ListView<>();
    allNames = new java.util.ArrayList<>(current.names());
    java.util.Collections.sort(allNames);
    sceneItems = javafx.collections.FXCollections.observableArrayList(allNames);
    sceneList.setItems(sceneItems);
    sceneList.setOnMouseClicked(e -> {
      String name = sceneList.getSelectionModel().getSelectedItem();
      if (name != null) {
        selected = current.find(name);
        buildInspector();
        if (e.getClickCount() == 2) { fitCameraToEntity(selected); }
      }
    });
    // Context menu: rename, delete, fit to selection
    ContextMenu cm = new ContextMenu();
    MenuItem miRename = new MenuItem("Rename...");
    MenuItem miDelete = new MenuItem("Delete");
    MenuItem miFitSel = new MenuItem("Fit Selection");
    cm.getItems().addAll(miRename, miDelete, miFitSel);
    sceneList.setContextMenu(cm);
    miRename.setOnAction(ev -> {
      String oldName = sceneList.getSelectionModel().getSelectedItem(); if (oldName == null || current == null) return;
      javafx.scene.control.TextInputDialog d = new javafx.scene.control.TextInputDialog(oldName);
      d.setHeaderText(null); d.setTitle("Rename"); d.setContentText("New name:");
      d.showAndWait().ifPresent(newName -> {
        if (current.rename(oldName, newName)) { buildSceneGraph(); status.setText("Renamed " + oldName + " → " + newName); }
      });
    });
    miDelete.setOnAction(ev -> {
      String name = sceneList.getSelectionModel().getSelectedItem(); if (name == null || current == null) return;
      if (current.removeEntity(name)) { buildSceneGraph(); selected = null; buildInspector(); status.setText("Deleted " + name); }
    });
    miFitSel.setOnAction(ev -> {
      String name = sceneList.getSelectionModel().getSelectedItem(); if (name == null || current == null) return;
      Entity2D e = current.find(name); if (e != null) { fitCameraToEntity(e); }
    });
    // Color-coded type indicator per entity
    sceneList.setCellFactory(lv -> new ListCell<>() {
      @Override protected void updateItem(String name, boolean empty) {
        super.updateItem(name, empty);
        if (empty || name == null) { setText(null); setGraphic(null); return; }
        setText(name);
        Entity2D e = current == null ? null : current.find(name);
        if (e == null) { setGraphic(null); return; }
        Label badge = new Label();
        String txt;
        Color col;
        if (e instanceof Panel2D) { txt = "P"; col = Color.web("#2ea043"); }
        else if (e instanceof Label2D) { txt = "L"; col = Color.web("#58a6ff"); }
        else if (e instanceof com.jvn.core.scene2d.Sprite2D) { txt = "S"; col = Color.web("#d29922"); }
        else if (e instanceof PhysicsBodyEntity2D) { txt = "B"; col = Color.web("#e16e6e"); }
        else if (e instanceof ParticleEmitter2D) { txt = "E"; col = Color.web("#a371f7"); }
        else { txt = "?"; col = Color.web("#8b949e"); }
        badge.setText(txt);
        badge.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-padding: 2 6; -fx-background-radius: 4; -fx-font-weight: bold;");
        badge.setTextFill(col);
        setGraphic(badge);
      }
    });
    sceneGraph.getChildren().addAll(sceneFilter, sceneList);
  }

  private void applySceneFilter() {
    if (sceneItems == null || allNames == null) return;
    String q = sceneFilter == null ? "" : sceneFilter.getText();
    if (q == null) q = "";
    final String qq = q.toLowerCase();
    java.util.List<String> filtered = new java.util.ArrayList<>();
    for (String n : allNames) { if (n.toLowerCase().contains(qq)) filtered.add(n); }
    sceneItems.setAll(filtered);
  }

  private HBox makeNumberField(String label, double initial, java.util.function.DoubleConsumer onChange) {
    HBox row = new HBox(6);
    Label l = new Label(label);
    TextField tf = new TextField(Double.toString(initial));
    tf.setOnAction(e -> applyDouble(tf, onChange));
    tf.setOnKeyReleased(e -> { if (e.getCode().isLetterKey() || e.getCode().isDigitKey() || e.getCode().toString().equals("ENTER")) applyDouble(tf, onChange); });
    row.getChildren().addAll(l, tf);
    return row;
  }

  private void applyDouble(TextField tf, java.util.function.DoubleConsumer onChange) {
    try { double v = Double.parseDouble(tf.getText()); onChange.accept(v); status.setText("Updated " + tf.getText()); }
    catch (Exception ex) { /* ignore parse errors */ }
  }

  private String mapKey(KeyCode code) {
    if (code == null) return "";
    // Prefer letter/digit names (A..Z, DIGIT0..9) else use code name
    String name = code.getName();
    if (name == null || name.isBlank()) name = code.toString();
    return name.toUpperCase();
  }

  private int mapButton(MouseButton b) {
    if (b == MouseButton.PRIMARY) return 1;
    if (b == MouseButton.MIDDLE) return 2;
    if (b == MouseButton.SECONDARY) return 3;
    return 0;
  }
}
