package com.jvn.editor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.FileOutputStream;
import java.util.Properties;

import com.jvn.core.scene2d.Entity2D;
import com.jvn.editor.commands.CommandStack;
import com.jvn.editor.ui.InspectorView;
import com.jvn.editor.ui.FileEditorTab;
import com.jvn.editor.ui.ProjectExplorerView;
import com.jvn.editor.ui.StoryTimelineView;
import com.jvn.editor.ui.SettingsEditorView;
import com.jvn.editor.ui.SceneGraphView;
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
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class EditorApp extends Application {
  // UI
  private AnimationTimer timer;
  private Label status;
  private File lastOpened;
  private Entity2D selected;
  private InspectorView inspectorView;
  private TabPane filesTabs;
  private boolean showGrid = true;
  
  private SceneGraphView sgView;
  private ProjectExplorerView projView;
  private StoryTimelineView timelineView;
  private SettingsEditorView settingsEditor;
  private com.jvn.editor.ui.MenuThemeEditorView menuThemeEditor;
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
    } else if ("vn".equalsIgnoreCase(type)) {
      // Open entry VNS and start preview from its label
      String entryVns = mf.getProperty("entryVns", "scripts/prologue.vns");
      String entryLabel = mf.getProperty("entryLabel", "start");
      File f = new File(root, entryVns);
      if (f.exists()) {
        openVnsFile(f);
        this.projectRoot = root;
        if (projView != null) projView.setRootDirectory(root);
        if (timelineView != null) timelineView.setProjectRoot(root);
        if (settingsEditor != null) settingsEditor.setProjectRoot(root);
        javafx.application.Platform.runLater(() -> { FileEditorTab ft = getActiveFileTab(); if (ft != null) ft.runFromLabel(entryLabel); });
        status.setText("Opened VN project: " + root.getName());
      } else {
        status.setText("Entry VNS not found: " + entryVns);
      }
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
      if (timelineView != null) timelineView.setProjectRoot(root);
      if (settingsEditor != null) settingsEditor.setProjectRoot(root);
      if (menuThemeEditor != null) menuThemeEditor.setProjectRoot(root);
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
    javafx.scene.control.ChoiceDialog<String> typeDlg = new javafx.scene.control.ChoiceDialog<>("vn", java.util.List.of("vn","jes","gradle"));
    typeDlg.setHeaderText(null); typeDlg.setTitle("Project Type"); typeDlg.setContentText("Type:");
    var typeRes = typeDlg.showAndWait(); if (typeRes.isEmpty()) return; String type = typeRes.get();
    File dir = new File(base, name); dir.mkdirs();
    try {
      Properties p = new Properties();
      p.setProperty("name", name);
      if ("vn".equalsIgnoreCase(type)) {
        p.setProperty("type", "vn");
        p.setProperty("entryVns", "scripts/prologue.vns");
        p.setProperty("entryLabel", "start");
        p.setProperty("timeline", "story.timeline");
        // directories
        new File(dir, "scripts").mkdirs();
        new File(dir, "assets/characters").mkdirs();
        new File(dir, "assets/backgrounds").mkdirs();
        new File(dir, "assets/cg").mkdirs();
        new File(dir, "assets/ui").mkdirs();
        new File(dir, "assets/bgm").mkdirs();
        new File(dir, "assets/sfx").mkdirs();
        new File(dir, "assets/voices").mkdirs();
        // sample script
        try (FileWriter fw = new FileWriter(new File(dir, "scripts/prologue.vns"))) {
          fw.write("label start\n\n\"Welcome to " + name + "!\"\n\n[choice Begin->start]\n");
        }
        // sample timeline (DSL)
        try (FileWriter fw = new FileWriter(new File(dir, "story.timeline"))) {
          fw.write("# Timeline for " + name + "\n");
          fw.write("arc \"Prologue\" script \"scripts/prologue.vns\" entry \"start\" at 40,40\n");
        }
        // default settings
        try (FileOutputStream fos = new FileOutputStream(new File(dir, "vn.settings"))) {
          Properties sp = new Properties();
          sp.setProperty("textSpeed", "40");
          sp.setProperty("bgm", "0.7");
          sp.setProperty("sfx", "0.7");
          sp.setProperty("voice", "0.7");
          sp.setProperty("autoPlayDelay", "1500");
          sp.setProperty("skipUnread", "false");
          sp.setProperty("skipAfterChoices", "false");
          sp.store(fos, "VN Settings");
        }
        // default menu theme
        try (FileOutputStream fos = new FileOutputStream(new File(dir, "scripts/menu.theme"))) {
          Properties tp = new Properties();
          tp.setProperty("backgroundColor", "#0A0C12");
          tp.setProperty("titleColor", "#FFFFFF");
          tp.setProperty("itemColor", "#D3D3D3");
          tp.setProperty("itemSelectedColor", "#FFFF00");
          tp.setProperty("hintColor", "rgba(200,200,200,0.8)");
          tp.setProperty("accentColor", "#FFFF00");
          tp.setProperty("titleFontFamily", "Arial");
          tp.setProperty("titleFontWeight", "BOLD");
          tp.setProperty("titleFontSize", "32");
          tp.setProperty("itemFontFamily", "Arial");
          tp.setProperty("itemFontWeight", "NORMAL");
          tp.setProperty("itemFontSize", "20");
          tp.setProperty("hintFontFamily", "Arial");
          tp.setProperty("hintFontWeight", "NORMAL");
          tp.setProperty("hintFontSize", "14");
          tp.store(fos, "Menu Theme");
        }
      } else if ("jes".equalsIgnoreCase(type)) {
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
      if (timelineView != null) { timelineView.setProjectRoot(dir); timelineView.setTimelineFile(new File(dir, "story.timeline")); }
      if (settingsEditor != null) settingsEditor.setProjectRoot(dir);
      if (menuThemeEditor != null) menuThemeEditor.setProjectRoot(dir);
      status.setText("Created project: " + name);
    } catch (Exception ex) {
      status.setText("Create project failed");
    }
  }

  private void openSample(String absolutePath) {
    File f = new File(absolutePath);
    if (!f.exists()) {
      Alert a = new Alert(Alert.AlertType.ERROR, "Sample not found: " + absolutePath);
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
      return;
    }
    openFile(f);
  }

  // legacy helpers removed; ViewportView now owned by per-file tabs

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
    miToggleGrid.setOnAction(e -> { showGrid = !showGrid; FileEditorTab ft = getActiveFileTab(); if (ft != null) ft.setShowGrid(showGrid); });
    miToggleGrid.setAccelerator(new KeyCodeCombination(KeyCode.G, KeyCombination.SHORTCUT_DOWN));
    menuView.getItems().addAll(miFit, miReset, miToggleGrid);

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

    // Layout
    BorderPane top = new BorderPane();
    top.setTop(mb);
    top.setCenter(toolbar);
    root.setTop(top);
    // Center: per-file tabs with embedded preview
    filesTabs = new TabPane();
    filesTabs.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      updateContextForActiveTab();
    });
    root.setCenter(filesTabs);
    inspectorView = new InspectorView(s -> status.setText(s));
    inspectorView.setCommandStack(commands);
    inspectorView.setMinWidth(280);
    inspectorView.setPrefWidth(320);
    ScrollPane inspectorScroll = new ScrollPane(inspectorView);
    inspectorScroll.setFitToWidth(true);
    timelineView = new StoryTimelineView();
    settingsEditor = new SettingsEditorView();
    menuThemeEditor = new com.jvn.editor.ui.MenuThemeEditorView();
    TabPane rightTabs = new TabPane();
    Tab tabInspectorRight = new Tab("Inspector", inspectorScroll); tabInspectorRight.setClosable(false);
    Tab tabTimeline = new Tab("Timeline", timelineView); tabTimeline.setClosable(false);
    Tab tabSettings = new Tab("Settings", settingsEditor); tabSettings.setClosable(false);
    Tab tabMenuTheme = new Tab("Menu Theme", menuThemeEditor); tabMenuTheme.setClosable(false);
    rightTabs.getTabs().addAll(tabInspectorRight, tabTimeline, tabSettings, tabMenuTheme);
    rightTabs.setPrefWidth(360);
    root.setRight(rightTabs);
    timelineView.setOnRunArc(a -> {
      if (a == null || a.script == null) return;
      File f = resolveProjectFile(a.script);
      if (f != null && f.exists()) {
        openFile(f);
        FileEditorTab ft = getActiveFileTab();
        if (ft != null) ft.runFromLabel((a.entryLabel == null || a.entryLabel.isBlank()) ? null : a.entryLabel);
      } else {
        status.setText("Arc script not found: " + a.script);
      }
    });
    timelineView.setOnRunLink(l -> {
      if (l == null) return;
      StoryTimelineView.Arc ta = timelineView.findArc(l.toArc);
      if (ta == null) { status.setText("Arc not found: " + l.toArc); return; }
      File f = resolveProjectFile(ta.script);
      if (f != null && f.exists()) {
        openFile(f);
        FileEditorTab ft = getActiveFileTab();
        String label = (l.toLabel != null && !l.toLabel.isBlank()) ? l.toLabel : ta.entryLabel;
        if (ft != null) ft.runFromLabel((label == null || label.isBlank()) ? null : label);
      } else {
        status.setText("Arc script not found: " + ta.script);
      }
    });
    sgView = new SceneGraphView();
    sgView.setMinWidth(200);
    sgView.setPrefWidth(240);
    projView = new ProjectExplorerView();
    projView.setOnOpenFile(f -> {
      if (f == null) return;
      String name = f.getName().toLowerCase();
      if (name.endsWith(".jes") || name.endsWith(".txt") || name.endsWith(".vns") || name.endsWith(".java") || name.endsWith(".timeline")) {
        openFile(f);
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

    // Timer
    timer = new AnimationTimer() {
      long last = -1;
      @Override public void handle(long now) {
        if (last < 0) { last = now; return; }
        long dt = (now - last) / 1_000_000L;
        last = now;
        FileEditorTab ft = getActiveFileTab();
        if (ft != null) {
          ft.setSize(filesTabs.getWidth(), filesTabs.getHeight());
          ft.render(dt);
        }
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
    if (timelineView != null) { timelineView.setProjectRoot(dir); String tf = loadManifest(dir) != null ? loadManifest(dir).getProperty("timeline", "story.timeline") : "story.timeline"; timelineView.setTimelineFile(new File(dir, tf)); }
    if (settingsEditor != null) settingsEditor.setProjectRoot(dir);
    if (menuThemeEditor != null) menuThemeEditor.setProjectRoot(dir);
    applyProjectRootToTabs();
    status.setText("Project: " + dir.getName());
  }

  private void openJesFile(File f) { openFile(f); }

  private void openVnsFile(File f) { openFile(f); }

  private File resolveProjectFile(String p) {
    if (p == null) return null;
    File f = new File(p);
    if (f.isAbsolute() || projectRoot == null) return f;
    return new File(projectRoot, p);
  }

  private void doReload() {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return;
    ft.reloadFromDisk();
    updateContextForActiveTab();
  }

  private void applyCodeFromEditor() {
    try {
      FileEditorTab ft = getActiveFileTab();
      if (ft == null) return;
      ft.apply();
      status.setText("Applied");
      updateContextForActiveTab();
    } catch (Exception ex) {
      status.setText("Apply failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to apply code: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void doSave(Stage stage) {
    FileEditorTab ft = getActiveFileTab();
    if (ft == null) return;
    File f = ft.getFile();
    if (f == null) { doSaveAs(stage); return; }
    ft.saveTo(f);
  }

  private void doSaveAs(Stage stage) {
    try {
      FileEditorTab ft = getActiveFileTab(); if (ft == null) return;
      FileChooser fc = new FileChooser();
      fc.setTitle("Save File");
      if (ft.getFile() != null) fc.setInitialFileName(ft.getFile().getName());
      File f = fc.showSaveDialog(stage);
      if (f == null) return;
      ft.saveTo(f);
      openFile(f);
    } catch (Exception ex) {
      status.setText("Save As failed");
      Alert a = new Alert(Alert.AlertType.ERROR, "Failed to save as: " + ex.getMessage());
      a.setHeaderText(null); a.setTitle("Error"); a.showAndWait();
    }
  }

  private void openJavaFile(File f) { openFile(f); }

  private static String stripExt(String name) {
    if (name == null) return "scene";
    int i = name.lastIndexOf('.');
    return (i > 0) ? name.substring(0, i) : name;
  }

  private void buildSceneGraph() {
    updateContextForActiveTab();
  }

  private String mapKey(KeyCode code) { return code == null ? "" : (code.getName() == null || code.getName().isBlank() ? code.toString() : code.getName()).toUpperCase(); }
  private int mapButton(MouseButton b) { if (b == MouseButton.PRIMARY) return 1; if (b == MouseButton.MIDDLE) return 2; if (b == MouseButton.SECONDARY) return 3; return 0; }

  private Region icon(String... styleClasses) {
    Region r = new Region();
    if (styleClasses != null) r.getStyleClass().addAll(styleClasses);
    return r;
  }

  private void openFile(File f) {
    if (f == null) return;
    // Find existing tab
    for (Tab t : filesTabs.getTabs()) {
      if (t.getUserData() instanceof File ff && ff.equals(f)) {
        filesTabs.getSelectionModel().select(t);
        return;
      }
    }
    // Create new tab
    FileEditorTab editor = new FileEditorTab(f);
    if (projectRoot != null) editor.setProjectRoot(projectRoot);
    editor.setOnSelected(ent -> { selected = ent; inspectorView.setSelection(ent); });
    editor.setOnStatus(s -> status.setText(s));
    editor.setCommandStack(commands);
    Tab tab = new Tab(f.getName(), editor);
    tab.setClosable(true);
    tab.setUserData(f);
    tab.setOnClosed(e -> { /* nothing special for now */ });
    filesTabs.getTabs().add(tab);
    filesTabs.getSelectionModel().select(tab);
    lastOpened = f;
    status.setText("Loaded: " + f.getName());
    updateContextForActiveTab();
  }

  private void applyProjectRootToTabs() {
    if (filesTabs == null) return;
    for (Tab t : filesTabs.getTabs()) {
      if (t.getContent() instanceof com.jvn.editor.ui.FileEditorTab fet) {
        fet.setProjectRoot(projectRoot);
      }
    }
  }

  private FileEditorTab getActiveFileTab() {
    Tab t = filesTabs.getSelectionModel().getSelectedItem();
    if (t == null) return null;
    return (t.getContent() instanceof FileEditorTab) ? (FileEditorTab) t.getContent() : null;
  }

  private void updateContextForActiveTab() {
    FileEditorTab ft = getActiveFileTab();
    if (sgView == null || inspectorView == null) return;
    JesScene2D scene = (ft != null) ? ft.getJesScene() : null;
    inspectorView.setScene(scene);
    sgView.setContext(
      scene,
      ent -> { selected = ent; inspectorView.setSelection(ent); },
      this::fitCameraToEntity,
      s -> status.setText(s)
    );
    sgView.refresh();
  }

  private void fitCameraToEntity(Entity2D e) {
    FileEditorTab ft = getActiveFileTab();
    if (ft != null && ft.getViewport() != null) {
      ft.getViewport().fitToEntity(e);
    }
  }

  private void resetCamera() {
    FileEditorTab ft = getActiveFileTab();
    if (ft != null && ft.getViewport() != null) {
      ft.getViewport().getCamera().setPosition(0,0);
      ft.getViewport().getCamera().setZoom(1.0);
    }
  }

  private void fitCameraToContent() {
    FileEditorTab ft = getActiveFileTab();
    if (ft != null) ft.fitToContent();
  }
}
