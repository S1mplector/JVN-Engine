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
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListView;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ScrollPane;
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
import javafx.scene.paint.Color;

import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.JesExporter;
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
  private VBox sceneGraph;
  private ListView<String> sceneList;
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
    MenuItem miSave = new MenuItem("Save");
    miSave.setOnAction(e -> doSave(primaryStage));
    MenuItem miSaveAs = new MenuItem("Save As...");
    miSaveAs.setOnAction(e -> doSaveAs(primaryStage));
    menuFile.getItems().addAll(miOpen, miReload, miSave, miSaveAs);
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
      selected = null;
      buildInspector();
      buildSceneGraph();
    } catch (Exception ex) {
      status.setText("Reload failed");
    }
  }

  private void doSave(Stage stage) {
    if (current == null) return;
    if (lastOpened == null) { doSaveAs(stage); return; }
    try {
      String sceneName = stripExt(lastOpened.getName());
      String content = JesExporter.export(current, sceneName);
      try (FileWriter fw = new FileWriter(lastOpened)) { fw.write(content); }
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
      } else if (e instanceof Label2D l) {
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
        inspector.getChildren().addAll(mass, rest);
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
    }
  }

  private void buildSceneGraph() {
    if (sceneGraph == null) return;
    sceneGraph.getChildren().clear();
    if (current == null) { sceneGraph.getChildren().add(new Label("No scene")); return; }
    sceneGraph.getChildren().add(new Label("Scene Graph"));
    sceneList = new ListView<>();
    var items = javafx.collections.FXCollections.<String>observableArrayList();
    var sorted = new java.util.TreeSet<String>(current.names());
    items.addAll(sorted);
    sceneList.setItems(items);
    sceneList.setOnMouseClicked(e -> {
      String name = sceneList.getSelectionModel().getSelectedItem();
      if (name != null) { selected = current.find(name); buildInspector(); }
    });
    sceneGraph.getChildren().add(sceneList);
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
