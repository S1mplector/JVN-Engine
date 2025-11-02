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
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.animation.AnimationTimer;
import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;

import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.fx.scene2d.FxBlitter2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.scripting.jes.runtime.PhysicsBodyEntity2D;
import com.jvn.core.input.Input;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;

public class EditorApp extends Application {
  private Canvas canvas;
  private FxBlitter2D blitter;
  private JesScene2D current;
  private AnimationTimer timer;
  private Label status;
  private File lastOpened;
  private Entity2D selected;
  private VBox inspector;
  private Input input = new Input();

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
    canvas.setOnMouseClicked(e -> {
      pick(e.getX(), e.getY());
      buildInspector();
    });
    canvas.setOnMouseMoved(e -> { input.setMousePosition(e.getX(), e.getY()); });
    canvas.setOnMouseDragged(e -> { input.setMousePosition(e.getX(), e.getY()); });
    canvas.setOnScroll(e -> { input.addScrollDeltaY(e.getDeltaY()); });
    canvas.setOnMousePressed(e -> { input.mouseDown(mapButton(e.getButton())); });
    canvas.setOnMouseReleased(e -> { input.mouseUp(mapButton(e.getButton())); });

    // Layout
    BorderPane top = new BorderPane();
    top.setTop(mb);
    top.setCenter(toolbar);
    root.setTop(top);
    root.setCenter(canvas);
    inspector = new VBox(8);
    inspector.setMinWidth(280);
    inspector.setPrefWidth(320);
    root.setRight(inspector);

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
      selected = null;
      buildInspector();
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
      selected = null;
      buildInspector();
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
        inspector.getChildren().addAll(mass, rest);
      }
    }
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
