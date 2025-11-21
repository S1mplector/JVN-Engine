package com.jvn.editor.ui;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.jvn.scripting.jes.JesParser;
import com.jvn.scripting.jes.JesToken;
import com.jvn.scripting.jes.JesTokenizer;
import com.jvn.scripting.jes.ast.JesAst;

import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.ToolBar;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;

public class TilemapEditorView extends BorderPane {
  private File projectRoot;
  private File jesFile;

  private ComboBox<String> layerBox;
  private Spinner<Integer> indexSpinner;
  private Label statusLabel;
  private Canvas canvas;
  private ScrollPane scroll;

  private JesAst.MapDecl currentMap;
  private List<JesAst.MapLayerDecl> layers = new ArrayList<>();
  private JesAst.MapLayerDecl currentLayer;

  private int cols;
  private int rows;
  private int[][] tiles;
  private int selectedIndex = 0;
  private double cellSize = 24.0;

  public TilemapEditorView() {
    layerBox = new ComboBox<>();
    layerBox.setPrefWidth(160);
    layerBox.setPromptText("Layer");
    layerBox.setOnAction(e -> {
      selectLayer(layerBox.getSelectionModel().getSelectedIndex());
    });

    indexSpinner = new Spinner<>();
    indexSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 999, 0));
    indexSpinner.valueProperty().addListener((o, ov, nv) -> {
      if (nv != null) selectedIndex = nv;
    });

    Button reloadButton = new Button("Reload");
    reloadButton.setOnAction(e -> reloadFromJes());

    Button saveButton = new Button("Save Layer");
    saveButton.setOnAction(e -> saveLayer());

    statusLabel = new Label("No JES context");

    HBox tools = new HBox(8, new Label("Layer:"), layerBox, new Label("Tile:"), indexSpinner, reloadButton, saveButton, statusLabel);
    tools.setPadding(new Insets(4));

    canvas = new Canvas(400, 300);
    canvas.setOnMousePressed(e -> handlePaint(e.getX(), e.getY()));
    canvas.setOnMouseDragged(e -> handlePaint(e.getX(), e.getY()));
    Tooltip.install(canvas, new Tooltip("Click or drag to paint tiles"));

    scroll = new ScrollPane(canvas);
    scroll.setFitToWidth(true);
    scroll.setFitToHeight(true);

    ToolBar tb = new ToolBar();
    tb.getItems().add(tools);

    setTop(tb);
    setCenter(scroll);
  }

  public void setProjectRoot(File root) {
    this.projectRoot = root;
  }

  public void setContext(File projectRoot, File jesFile) {
    this.projectRoot = projectRoot;
    this.jesFile = jesFile;
    reloadFromJes();
  }

  public void clearContext() {
    this.jesFile = null;
    this.currentMap = null;
    this.layers.clear();
    this.currentLayer = null;
    this.tiles = null;
    redraw();
    if (statusLabel != null) statusLabel.setText("No JES context");
  }

  private void reloadFromJes() {
    if (jesFile == null || !jesFile.exists()) {
      clearContext();
      return;
    }
    try {
      String code = Files.readString(jesFile.toPath());
      List<JesToken> toks = JesTokenizer.tokenize(new java.io.ByteArrayInputStream(code.getBytes(StandardCharsets.UTF_8)));
      JesParser parser = new JesParser(toks);
      JesAst.Program prog = parser.parseProgram();
      if (prog.scenes.isEmpty()) {
        clearContext();
        return;
      }
      JesAst.SceneDecl scene = prog.scenes.get(0);
      if (scene.maps.isEmpty()) {
        clearContext();
        statusLabel.setText("Scene has no maps");
        return;
      }
      currentMap = scene.maps.get(0);
      layers = new ArrayList<>();
      List<String> names = new ArrayList<>();
      for (JesAst.MapLayerDecl l : currentMap.layers) {
        if (l == null) continue;
        Object d = l.props.get("data");
        if (d instanceof String) {
          layers.add(l);
          names.add(l.name == null ? "layer" : l.name);
        }
      }
      layerBox.getItems().setAll(names);
      if (!layers.isEmpty()) {
        layerBox.getSelectionModel().select(0);
        selectLayer(0);
      } else {
        currentLayer = null;
        tiles = null;
        redraw();
        statusLabel.setText("Map has no data layers");
      }
    } catch (Exception ex) {
      clearContext();
      statusLabel.setText("Parse error");
    }
  }

  private void selectLayer(int index) {
    if (index < 0 || index >= layers.size()) return;
    currentLayer = layers.get(index);
    loadLayerData();
  }

  private void loadLayerData() {
    if (currentLayer == null) {
      tiles = null;
      redraw();
      return;
    }
    String dataPath = null;
    Object d = currentLayer.props.get("data");
    if (d instanceof String s) dataPath = s;
    if (projectRoot == null || dataPath == null || dataPath.isBlank()) {
      tiles = null;
      redraw();
      statusLabel.setText("Missing data path");
      return;
    }
    File f = new File(projectRoot, dataPath);
    if (!f.exists()) {
      int w = (int) getMapProp(currentMap, "width", 32);
      int h = (int) getMapProp(currentMap, "height", 32);
      cols = Math.max(1, w);
      rows = Math.max(1, h);
      tiles = new int[rows][cols];
      for (int y = 0; y < rows; y++) for (int x = 0; x < cols; x++) tiles[y][x] = -1;
      redraw();
      statusLabel.setText("Created new layer grid");
      return;
    }
    try {
      List<String> lines = Files.readAllLines(f.toPath());
      rows = lines.size();
      cols = 0;
      for (String line : lines) {
        String[] parts = line.split(",");
        if (parts.length > cols) cols = parts.length;
      }
      if (rows <= 0 || cols <= 0) {
        rows = 1;
        cols = 1;
      }
      tiles = new int[rows][cols];
      for (int y = 0; y < rows; y++) {
        String line = lines.get(y);
        String[] parts = line.split(",");
        for (int x = 0; x < cols; x++) {
          int val = -1;
          if (x < parts.length) {
            String s = parts[x].trim();
            if (!s.isEmpty()) {
              try { val = Integer.parseInt(s); } catch (NumberFormatException ignore) {}
            }
          }
          tiles[y][x] = val;
        }
      }
      redraw();
      statusLabel.setText("Loaded layer");
    } catch (Exception ex) {
      tiles = null;
      redraw();
      statusLabel.setText("Failed to load CSV");
    }
  }

  private double getMapProp(JesAst.MapDecl m, String key, double def) {
    if (m == null || m.props == null) return def;
    Object v = m.props.get(key);
    if (v instanceof Number n) return n.doubleValue();
    return def;
  }

  private void saveLayer() {
    if (currentLayer == null || tiles == null) return;
    if (projectRoot == null) return;
    Object d = currentLayer.props.get("data");
    if (!(d instanceof String s) || s.isBlank()) return;
    File f = new File(projectRoot, s);
    try {
      File parent = f.getParentFile();
      if (parent != null && !parent.exists()) parent.mkdirs();
      try (FileWriter fw = new FileWriter(f, false)) {
        for (int y = 0; y < rows; y++) {
          StringBuilder sb = new StringBuilder();
          for (int x = 0; x < cols; x++) {
            if (x > 0) sb.append(',');
            int v = tiles[y][x];
            if (v >= 0) sb.append(v);
          }
          sb.append('\n');
          fw.write(sb.toString());
        }
      }
      statusLabel.setText("Saved " + f.getName());
    } catch (Exception ex) {
      statusLabel.setText("Save failed");
    }
  }

  private void handlePaint(double px, double py) {
    if (tiles == null) return;
    int tx = (int) (px / cellSize);
    int ty = (int) (py / cellSize);
    if (ty < 0 || ty >= rows || tx < 0 || tx >= cols) return;
    tiles[ty][tx] = selectedIndex;
    redraw();
  }

  private void redraw() {
    GraphicsContext g = canvas.getGraphicsContext2D();
    if (tiles == null) {
      canvas.setWidth(400);
      canvas.setHeight(300);
      g.setFill(Color.color(0.1, 0.1, 0.12));
      g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
      g.setFill(Color.color(0.8, 0.8, 0.8));
      g.fillText("No map layer", 16, 24);
      return;
    }
    canvas.setWidth(cols * cellSize);
    canvas.setHeight(rows * cellSize);
    g.setFill(Color.color(0.06, 0.06, 0.08));
    g.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
    for (int y = 0; y < rows; y++) {
      for (int x = 0; x < cols; x++) {
        double sx = x * cellSize;
        double sy = y * cellSize;
        int v = tiles[y][x];
        if (v >= 0) {
          double hue = (v * 37) % 360;
          Color c = Color.hsb(hue, 0.6, 0.9);
          g.setFill(c);
        } else {
          g.setFill(Color.color(0.12, 0.12, 0.16));
        }
        g.fillRect(sx, sy, cellSize, cellSize);
        g.setStroke(Color.color(0.2, 0.2, 0.25));
        g.strokeRect(sx, sy, cellSize, cellSize);
      }
    }
  }
}
