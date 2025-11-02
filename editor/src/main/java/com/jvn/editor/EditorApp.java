package com.jvn.editor;

import java.io.InputStream;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Properties;

import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.vn.VnScenario;
import com.jvn.core.vn.script.VnScriptParser;
import com.jvn.editor.commands.CommandStack;
import com.jvn.editor.ui.InspectorView;
import com.jvn.editor.ui.JesCodeEditor;
import com.jvn.editor.ui.JavaCodeEditor;
import com.jvn.editor.ui.VnsCodeEditor;
import com.jvn.editor.ui.VnPreviewView;
import com.jvn.editor.ui.ProjectExplorerView;
import com.jvn.editor.ui.SceneGraphView;
import com.jvn.editor.ui.ViewportView;
import com.jvn.scripting.jes.JesExporter;
import com.jvn.scripting.jes.JesLoader;
import com.jvn.scripting.jes.runtime.JesScene2D;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.ContentDisplay;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class EditorApp extends Application {
  // UI
  private ViewportView viewport;
  private JesScene2D current;
  private AnimationTimer timer;
  private Label status;
  private File lastOpened;
  private Entity2D selected;
  private InspectorView inspectorView;
  // Input is owned by ViewportView
  private TabPane tabs;
  private JesCodeEditor codeView;
  private JavaCodeEditor javaCodeView;
  private VnsCodeEditor vnsCodeView;
  private VnPreviewView vnView;
  private Tab tabCanvas;
  private Tab tabCode;
  private Tab tabJava;
  private Tab tabVnsCode;
  private Tab tabVnPreview;
  private boolean showGrid = true;
  
  private SceneGraphView sgView;
  private ProjectExplorerView projView;
  private final CommandStack commands = new CommandStack();
  private Tab tabProject;
  private Tab tabScene;
  private File projectRoot;

  public static void main(String[] args) {
    launch(args);
  }

  private void doOpenVns(Stage stage) {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Open VNS Script");
      fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("VNS scripts", "*.vns"));
      File f = fc.showOpenDialog(stage);
      if (f == null) return;
      openVnsFile(f);
    } catch (Exception ex) {
      status.setText("Load failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to load: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doRunProject(Stage stage) {
    File root = ensureProjectRoot(stage);
    if (root == null) return;
    Properties mf = loadManifest(root);
    if (mf == null) { status.setText("jvn.project not found"); return; }
    String type = mf.getProperty("type", "gradle").trim();
    if ("gradle".equalsIgnoreCase(type)) {
      String path = mf.getProperty("path", ":billiards-game").trim();
      String task = mf.getProperty("task", "run").trim();
      String args = mf.getProperty("args", "-x test");
      runGradle(root, composeGradleTask(path, task), args == null ? new String[]{} : args.split("\\s+"), "Run Project");
    } else if ("jes".equalsIgnoreCase(type)) {
      // For JES-only projects: open the entry script and set as project
      String entry = mf.getProperty("entry", "scripts/main.jes");
      File f = new File(root, entry);
      if (f.exists()) openJesFile(f);
      this.projectRoot = root;
      if (projView != null) projView.setRootDirectory(root);
      status.setText("Opened JES project: " + root.getName());
    } else {
      status.setText("Unknown project type: " + type);
    }
  }

  private File ensureProjectRoot(Stage stage) {
    File root = this.projectRoot;
    if (root == null) {
      DirectoryChooser dc = new DirectoryChooser();
      dc.setTitle("Select Project Root");
      root = dc.showDialog(stage);
      if (root == null) return null;
      this.projectRoot = root;
      if (projView != null) projView.setRootDirectory(root);
    }
    return root;
  }

  private Properties loadManifest(File dir) {
    try (FileInputStream fis = new FileInputStream(new File(dir, "jvn.project"))) {
      Properties p = new Properties();
      p.load(fis);
      return p;
    } catch (Exception ignore) { return null; }
  }

  private String composeGradleTask(String path, String task) {
    String p = path == null ? "" : path.trim();
    String t = task == null ? "run" : task.trim();
    if (t.startsWith(":")) return t; // full task provided
    if (p.isEmpty()) return t;
    if (!p.startsWith(":")) p = ":" + p;
    return p + ":" + t;
  }

  private void runGradle(File root, String task, String[] args, String title) {
    File gradlew = new File(root, "gradlew");
    try {
      java.util.List<String> cmd = new java.util.ArrayList<>();
      if (gradlew.exists()) cmd.add(gradlew.getAbsolutePath()); else cmd.add("gradle");
      cmd.add(task);
      if (args != null) java.util.Collections.addAll(cmd, args);
      ProcessBuilder pb = new ProcessBuilder(cmd);
      pb.directory(root);
      pb.redirectErrorStream(true);
      Process p = pb.start();
      javafx.stage.Stage logStage = new javafx.stage.Stage();
      javafx.scene.control.TextArea ta = new javafx.scene.control.TextArea();
      ta.setEditable(false);
      logStage.setTitle(title);
      logStage.setScene(new javafx.scene.Scene(new javafx.scene.layout.BorderPane(ta), 800, 500));
      logStage.show();
      Thread t = new Thread(() -> {
        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream()))) {
          String line;
          while ((line = r.readLine()) != null) {
            String ln = line;
            javafx.application.Platform.runLater(() -> ta.appendText(ln + "\n"));
          }
        } catch (Exception ignored) {}
      });
      t.setDaemon(true);
      t.start();
    } catch (Exception ex) {
      status.setText("Run failed");
    }
  }

  private void doNewProject(Stage stage) {
    DirectoryChooser dc = new DirectoryChooser();
    dc.setTitle("Choose Location for New Project");
    File base = dc.showDialog(stage);
    if (base == null) return;
    javafx.scene.control.TextInputDialog nameDlg = new javafx.scene.control.TextInputDialog("MyProject");
    nameDlg.setHeaderText(null); nameDlg.setTitle("New Project"); nameDlg.setContentText("Project name:");
    var nameRes = nameDlg.showAndWait(); if (nameRes.isEmpty()) return; String name = nameRes.get().trim(); if (name.isEmpty()) return;
    javafx.scene.control.ChoiceDialog<String> typeDlg = new javafx.scene.control.ChoiceDialog<>("jes", java.util.List.of("jes","gradle"));
    typeDlg.setHeaderText(null); typeDlg.setTitle("Project Type"); typeDlg.setContentText("Type:");
    var typeRes = typeDlg.showAndWait(); if (typeRes.isEmpty()) return; String type = typeRes.get();
    File dir = new File(base, name); dir.mkdirs();
    try {
      Properties p = new Properties();
      p.setProperty("name", name);
      if ("jes".equalsIgnoreCase(type)) {
        p.setProperty("type", "jes");
        p.setProperty("entry", "scripts/main.jes");
        new File(dir, "scripts").mkdirs();
        try (FileWriter fw = new FileWriter(new File(dir, "scripts/main.jes"))) {
          fw.write("scene \"" + name + "\" {\n  entity \"title\" {\n    component Label2D { text: \"Hello, JVN!\" x: 20 y: 24 size: 18 bold: true color: rgb(1,1,1,1) }\n  }\n  on key \"D\" do toggleDebug\n}\n");
        }
      } else {
        p.setProperty("type", "gradle");
        javafx.scene.control.TextInputDialog pathDlg = new javafx.scene.control.TextInputDialog(":app");
        pathDlg.setHeaderText(null); pathDlg.setTitle("Gradle Module Path"); pathDlg.setContentText("Module path (e.g. :billiards-game):");
        var pathRes = pathDlg.showAndWait(); if (pathRes.isEmpty()) return; String path = pathRes.get().trim();
        p.setProperty("path", path);
        p.setProperty("task", "run");
        p.setProperty("args", "-x test");
      }
      try (FileOutputStream fos = new FileOutputStream(new File(dir, "jvn.project"))) { p.store(fos, "JVN Project Manifest"); }
      this.projectRoot = dir; if (projView != null) projView.setRootDirectory(dir);
      status.setText("Created project: " + name);
    } catch (Exception ex) {
      status.setText("Create project failed");
    }
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
      if (current != null) { current.setInput(viewport.getInput()); current.setCamera(viewport.getCamera()); viewport.setScene(current); }
      try { String code = Files.readString(f.toPath()); codeView.setText(code); } catch (Exception ignore) {}
      selected = null;
      inspectorView.setScene(current);
      inspectorView.setSelection(null);
      buildSceneGraph();
      tabs.getSelectionModel().selectFirst();
    } catch (Exception ex) {
      status.setText("Load failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to load: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void fitCameraToEntity(Entity2D e) { if (viewport != null) viewport.fitToEntity(e); }

  private void resetCamera() { if (viewport != null) { viewport.getCamera().setPosition(0,0); viewport.getCamera().setZoom(1.0); } }

  private void fitCameraToContent() { if (viewport != null) viewport.fitToContent(); }

  // legacy helpers removed; ViewportView now owns camera, render, pick

  @Override
  public void start(Stage primaryStage) {
    primaryStage.setTitle("JVN Editor");
    BorderPane root = new BorderPane();

    // Menu
    MenuBar mb = new MenuBar();
    Menu menuFile = new Menu("File");
    MenuItem miNewProject = new MenuItem("New Project...");
    miNewProject.setOnAction(e -> doNewProject(primaryStage));
    MenuItem miOpenProject = new MenuItem("Open Project...");
    miOpenProject.setOnAction(e -> doOpenProject(primaryStage));
    MenuItem miOpen = new MenuItem("Open JES...");
    miOpen.setOnAction(e -> doOpen(primaryStage));
    MenuItem miOpenVns = new MenuItem("Open VNS...");
    miOpenVns.setOnAction(e -> doOpenVns(primaryStage));
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
    menuFile.getItems().addAll(miNewProject, miOpenProject, new SeparatorMenuItem(), miOpen, miOpenVns, miReload, miSave, miSaveAs);

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
    MenuItem miToggleGrid = new MenuItem("Toggle Grid");
    miToggleGrid.setOnAction(e -> { showGrid = !showGrid; });
    miToggleGrid.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN));
    menuView.getItems().addAll(miFit, miReset, miToggleGrid);
    miToggleGrid.setOnAction(e -> { showGrid = !showGrid; if (viewport != null) viewport.setShowGrid(showGrid); });

    Menu menuSamples = new Menu("Samples");
    Menu menuProject = new Menu("Project");
    MenuItem miRun = new MenuItem("Run Project");
    miRun.setOnAction(e -> doRunProject(primaryStage));
    menuProject.getItems().addAll(miRun);
    Menu menuEdit = new Menu("Edit");
    MenuItem miUndo = new MenuItem("Undo");
    miUndo.setOnAction(e -> { commands.undo(); status.setText("Undo"); inspectorView.setSelection(selected); });
    miUndo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN));
    MenuItem miRedo = new MenuItem("Redo");
    miRedo.setOnAction(e -> { commands.redo(); status.setText("Redo"); inspectorView.setSelection(selected); });
    miRedo.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.SHORTCUT_DOWN, KeyCombination.SHIFT_DOWN));
    menuEdit.getItems().addAll(miUndo, miRedo);
    MenuItem miBilliards = new MenuItem("Open billiards.jes");
    miBilliards.setOnAction(e -> openSample("/Users/ilgazmehmetoglu/Desktop/Projects/Percept2 Engine/samples/billiards.jes"));
    MenuItem miShowcase = new MenuItem("Open showcase.jes");
    miShowcase.setOnAction(e -> openSample("/Users/ilgazmehmetoglu/Desktop/Projects/Percept2 Engine/samples/showcase.jes"));
    menuSamples.getItems().addAll(miBilliards, miShowcase);

    mb.getMenus().addAll(menuFile, menuEdit, menuCode, menuView, menuProject, menuSamples);

    // Toolbar
    HBox toolbar = new HBox(8);
    Button btnOpen = new Button("Open"); btnOpen.setOnAction(e -> doOpen(primaryStage));
    Button btnReload = new Button("Reload"); btnReload.setOnAction(e -> doReload());
    Button btnApply = new Button("Apply Code"); btnApply.setOnAction(e -> applyCodeFromEditor());
    Button btnFit = new Button("Fit"); btnFit.setOnAction(e -> fitCameraToContent());
    Button btnReset = new Button("Reset"); btnReset.setOnAction(e -> resetCamera());
    // Icons to the right of text
    btnOpen.setGraphic(icon("icon", "icon-open"));
    btnOpen.setContentDisplay(ContentDisplay.RIGHT);
    btnOpen.setGraphicTextGap(6);
    btnReload.setGraphic(icon("icon", "icon-reload"));
    btnReload.setContentDisplay(ContentDisplay.RIGHT);
    btnReload.setGraphicTextGap(6);
    btnApply.setGraphic(icon("icon", "icon-apply"));
    btnApply.setContentDisplay(ContentDisplay.RIGHT);
    btnApply.setGraphicTextGap(6);
    btnFit.setGraphic(icon("icon", "icon-fit"));
    btnFit.setContentDisplay(ContentDisplay.RIGHT);
    btnFit.setGraphicTextGap(6);
    btnReset.setGraphic(icon("icon", "icon-reset"));
    btnReset.setContentDisplay(ContentDisplay.RIGHT);
    btnReset.setGraphicTextGap(6);
    btnApply.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER && e.isShortcutDown()) applyCodeFromEditor(); });
    status = new Label("Ready");
    Region logo = icon("jvn-logo");
    toolbar.getChildren().addAll(logo, btnOpen, btnReload, btnApply, btnFit, btnReset, status);

    // Viewport component
    viewport = new ViewportView();
    viewport.setOnSelected(ent -> { selected = ent; inspectorView.setSelection(ent); });
    viewport.setOnStatus(s -> status.setText(s));
    viewport.setCommandStack(commands);

    // Layout
    BorderPane top = new BorderPane();
    top.setTop(mb);
    top.setCenter(toolbar);
    root.setTop(top);
    // Center: TabPane with Canvas and Code editors
    codeView = new JesCodeEditor();
    javaCodeView = new JavaCodeEditor();
    vnsCodeView = new VnsCodeEditor();
    vnView = new VnPreviewView();
    tabs = new TabPane();
    tabCanvas = new Tab("Canvas", viewport); tabCanvas.setClosable(false);
    tabCode = new Tab("JES Code", codeView); tabCode.setClosable(false);
    tabJava = new Tab("Java Code", javaCodeView); tabJava.setClosable(false);
    tabVnsCode = new Tab("VNS Code", vnsCodeView); tabVnsCode.setClosable(false);
    tabVnPreview = new Tab("VN Preview", vnView); tabVnPreview.setClosable(false);
    tabs.getTabs().addAll(tabCanvas, tabCode, tabJava, tabVnsCode, tabVnPreview);
    root.setCenter(tabs);
    inspectorView = new InspectorView(s -> status.setText(s));
    inspectorView.setCommandStack(commands);
    inspectorView.setMinWidth(280);
    inspectorView.setPrefWidth(320);
    ScrollPane inspectorScroll = new ScrollPane(inspectorView);
    inspectorScroll.setFitToWidth(true);
    root.setRight(inspectorScroll);
    sgView = new SceneGraphView();
    sgView.setMinWidth(200);
    sgView.setPrefWidth(240);
    projView = new ProjectExplorerView();
    projView.setOnOpenFile(f -> {
      if (f == null) return;
      String name = f.getName().toLowerCase();
      if (name.endsWith(".jes") || name.endsWith(".txt")) {
        openJesFile(f);
      } else if (name.endsWith(".vns")) {
        openVnsFile(f);
      } else if (name.endsWith(".java")) {
        openJavaFile(f);
      } else {
        try { java.awt.Desktop.getDesktop().open(f); } catch (Exception ignored) {}
      }
    });
    TabPane sideTabs = new TabPane();
    tabProject = new Tab("Project", projView); tabProject.setClosable(false);
    tabScene = new Tab("Scene", sgView); tabScene.setClosable(false);
    sideTabs.getTabs().addAll(tabProject, tabScene);
    sideTabs.setPrefWidth(260);
    root.setLeft(sideTabs);

    Scene scene = new Scene(root, 1200, 800);
    // Load editor stylesheet (icons, theme, etc.)
    try {
      String css = EditorApp.class.getResource("/com/jvn/editor/editor.css").toExternalForm();
      scene.getStylesheets().add(css);
    } catch (Exception ignore) {}
    primaryStage.setScene(scene);
    primaryStage.show();
    viewport.setFocusTraversable(true);
    scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> { if (tabs.getSelectionModel().getSelectedItem() == tabCanvas) viewport.getInput().keyDown(mapKey(e.getCode())); });
    scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> { if (tabs.getSelectionModel().getSelectedItem() == tabCanvas) viewport.getInput().keyUp(mapKey(e.getCode())); });

    // Resize handling
    scene.widthProperty().addListener((o,ov,nv) -> { viewport.setSize(nv.doubleValue(), viewport.getHeight()); if (vnView != null) vnView.setSize(nv.doubleValue(), vnView.getHeight()); });
    scene.heightProperty().addListener((o,ov,nv) -> { viewport.setSize(viewport.getWidth(), nv.doubleValue() - 60); if (vnView != null) vnView.setSize(vnView.getWidth(), nv.doubleValue() - 60); });

    // Timer
    timer = new AnimationTimer() {
      long last = -1;
      @Override public void handle(long now) {
        if (last < 0) { last = now; return; }
        long dt = (now - last) / 1_000_000L;
        last = now;
        viewport.render(dt);
        if (vnView != null) vnView.render(dt);
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
      openJesFile(f);
    } catch (Exception ex) {
      status.setText("Load failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to load: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doOpenProject(Stage stage) {
    DirectoryChooser dc = new DirectoryChooser();
    dc.setTitle("Open Project Directory");
    File dir = dc.showDialog(stage);
    if (dir == null) return;
    this.projectRoot = dir;
    if (projView != null) projView.setRootDirectory(dir);
    status.setText("Project: " + dir.getName());
  }

  private void openJesFile(File f) {
    if (f == null) return;
    try (InputStream in = new FileInputStream(f)) {
      current = JesLoader.load(in);
      lastOpened = f;
      status.setText("Loaded: " + f.getName());
      if (current != null) current.setInput(viewport.getInput());
      if (current != null) current.setCamera(viewport.getCamera());
      viewport.setScene(current);
      try { String code = Files.readString(f.toPath()); codeView.setText(code); } catch (Exception ignore) {}
      selected = null;
      inspectorView.setScene(current);
      inspectorView.setSelection(null);
      buildSceneGraph();
      if (projectRoot == null && f.getParentFile() != null) {
        File dir = f.getParentFile();
        for (int i = 0; i < 3 && dir != null; i++) dir = dir.getParentFile();
        if (dir != null) { projectRoot = dir; if (projView != null) projView.setRootDirectory(projectRoot); }
      }
    } catch (Exception ex) {
      status.setText("Load failed: " + ex.getMessage());
    }
  }

  private void openVnsFile(File f) {
    if (f == null) return;
    try {
      String code = Files.readString(f.toPath());
      if (vnsCodeView != null) vnsCodeView.setText(code);
      VnScriptParser parser = new VnScriptParser();
      VnScenario scenario = parser.parse(new FileInputStream(f));
      if (vnView != null) vnView.setScenario(scenario);
      lastOpened = f;
      status.setText("Loaded: " + f.getName());
      tabs.getSelectionModel().select(tabVnPreview);
    } catch (Exception ex) {
      status.setText("Load failed: " + ex.getMessage());
    }
  }

  private void doReload() {
    if (lastOpened == null) return;
    String nm = lastOpened.getName().toLowerCase();
    if (nm.endsWith(".java")) {
      try { String code = Files.readString(lastOpened.toPath()); javaCodeView.setText(code); tabs.getSelectionModel().select(tabJava); status.setText("Reloaded: " + lastOpened.getName()); } catch (Exception ex) { status.setText("Reload failed"); }
      return;
    }
    if (nm.endsWith(".vns")) {
      try {
        String code = Files.readString(lastOpened.toPath());
        if (vnsCodeView != null) vnsCodeView.setText(code);
        VnScriptParser parser = new VnScriptParser();
        VnScenario scenario = parser.parse(new FileInputStream(lastOpened));
        if (vnView != null) vnView.setScenario(scenario);
        status.setText("Reloaded: " + lastOpened.getName());
        tabs.getSelectionModel().select(tabVnPreview);
      } catch (Exception ex) {
        status.setText("Reload failed");
      }
      return;
    }
    try (InputStream in = new FileInputStream(lastOpened)) {
      current = JesLoader.load(in);
      status.setText("Reloaded: " + lastOpened.getName());
      if (current != null) current.setInput(viewport.getInput());
      if (current != null) current.setCamera(viewport.getCamera());
      viewport.setScene(current);
      try { String code = Files.readString(lastOpened.toPath()); codeView.setText(code); } catch (Exception ignore) {}
      selected = null;
      inspectorView.setScene(current);
      inspectorView.setSelection(null);
      buildSceneGraph();
    } catch (Exception ex) {
      status.setText("Reload failed");
    }
  }

  private void applyCodeFromEditor() {
    try {
      Tab sel = tabs.getSelectionModel().getSelectedItem();
      if (sel == tabVnsCode || (lastOpened != null && lastOpened.getName().toLowerCase().endsWith(".vns"))) {
        String vnsText = vnsCodeView.getText();
        if (vnsText == null || vnsText.isBlank()) return;
        VnScriptParser parser = new VnScriptParser();
        VnScenario scenario = parser.parseFromString(vnsText);
        if (vnView != null) {
          vnView.setScenario(scenario);
          status.setText("Applied VNS code");
          tabs.getSelectionModel().select(tabVnPreview);
        }
      } else {
        String code = codeView.getText();
        if (code == null || code.isBlank()) return;
        current = JesLoader.load(code);
        if (current != null) {
          current.setInput(viewport.getInput());
          current.setCamera(viewport.getCamera());
          viewport.setScene(current);
          selected = null;
          inspectorView.setScene(current);
          inspectorView.setSelection(null);
          buildSceneGraph();
          status.setText("Applied code to scene");
          tabs.getSelectionModel().selectFirst();
        }
      }
    } catch (Exception ex) {
      status.setText("Apply failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to apply code: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doSave(Stage stage) {
    if (lastOpened == null) { doSaveAs(stage); return; }
    String nm = lastOpened.getName().toLowerCase();
    try {
      if (nm.endsWith(".java")) {
        String content = javaCodeView.getText();
        try (FileWriter fw = new FileWriter(lastOpened)) { fw.write(content); }
        status.setText("Saved: " + lastOpened.getName());
      } else if (nm.endsWith(".vns")) {
        String content = vnsCodeView.getText();
        try (FileWriter fw = new FileWriter(lastOpened)) { fw.write(content); }
        status.setText("Saved: " + lastOpened.getName());
      } else {
        if (current == null) return;
        String sceneName = stripExt(lastOpened.getName());
        String content = JesExporter.export(current, sceneName);
        try (FileWriter fw = new FileWriter(lastOpened)) { fw.write(content); }
        if (codeView != null) codeView.setText(content);
        status.setText("Saved: " + lastOpened.getName());
      }
    } catch (Exception ex) {
      status.setText("Save failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to save: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doSaveAs(Stage stage) {
    try {
      FileChooser fc = new FileChooser();
      fc.setTitle("Save File");
      if (lastOpened != null) fc.setInitialFileName(lastOpened.getName());
      File f = fc.showSaveDialog(stage);
      if (f == null) return;
      String nm = f.getName().toLowerCase();
      if (nm.endsWith(".java")) {
        String content = javaCodeView.getText();
        try (FileWriter fw = new FileWriter(f)) { fw.write(content); }
        lastOpened = f;
        status.setText("Saved: " + f.getName());
      } else if (nm.endsWith(".vns")) {
        String content = vnsCodeView.getText();
        try (FileWriter fw = new FileWriter(f)) { fw.write(content); }
        lastOpened = f;
        status.setText("Saved: " + f.getName());
      } else {
        if (!(nm.endsWith(".jes") || nm.endsWith(".txt"))) {
          f = new File(f.getAbsolutePath() + ".jes");
        }
        String sceneName = stripExt(f.getName());
        String content = JesExporter.export(current, sceneName);
        try (FileWriter fw = new FileWriter(f)) { fw.write(content); }
        lastOpened = f;
        if (codeView != null) codeView.setText(content);
        status.setText("Saved: " + f.getName());
      }
    } catch (Exception ex) {
      status.setText("Save As failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to save as: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void openJavaFile(File f) {
    try {
      String code = Files.readString(f.toPath());
      javaCodeView.setText(code);
      lastOpened = f;
      tabs.getSelectionModel().select(tabJava);
      status.setText("Loaded: " + f.getName());
    } catch (Exception ex) {
      status.setText("Open failed");
    }
  }

  private static String stripExt(String name) {
    if (name == null) return "scene";
    int i = name.lastIndexOf('.');
    return (i > 0) ? name.substring(0, i) : name;
  }

  private void render(long deltaMs) { viewport.render(deltaMs); }

  private void drawSelectionOverlay() { /* handled by viewport */ }

  private void pick(double x, double y) { /* handled by viewport */ }

  private void handleKeyboardCamera(long deltaMs) { /* handled by viewport */ }

  

  private void buildSceneGraph() {
    if (sgView == null) return;
    sgView.setContext(
      current,
      ent -> { selected = ent; inspectorView.setSelection(ent); },
      this::fitCameraToEntity,
      s -> status.setText(s)
    );
    sgView.refresh();
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

  private Region icon(String... styleClasses) {
    Region r = new Region();
    if (styleClasses != null) r.getStyleClass().addAll(styleClasses);
    return r;
  }
}
