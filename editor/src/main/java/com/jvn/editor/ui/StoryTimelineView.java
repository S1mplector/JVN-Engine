package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.control.TitledPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Slider;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.ScrollPane;
import javafx.stage.FileChooser;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class StoryTimelineView extends BorderPane {
  public static class Arc {
    public String name;
    public String script;
    public String entryLabel;
    public double x = 40;
    public double y = 40;
    public String toLine() { return "ARC|" + nn(name) + "|" + nn(script) + "|" + nn(entryLabel) + "|" + x + "|" + y; }
  }

  private void validate() {
    StringBuilder sb = new StringBuilder();
    // Check arcs
    for (Arc a : arcs.getItems()) {
      if (a == null) continue;
      File f = resolveFile(a.script);
      if (f == null || !f.exists()) {
        sb.append("Missing script for arc '").append(a.name).append("': ").append(a.script).append("\n");
        continue;
      }
      if (a.entryLabel != null && !a.entryLabel.isBlank()) {
        boolean ok = hasLabel(f, a.entryLabel);
        if (!ok) sb.append("Arc '").append(a.name).append("' missing entry label '").append(a.entryLabel).append("'\n");
      }
    }
    // Check links
    for (Link l : links.getItems()) {
      if (l == null) continue;
      Arc ta = findArc(l.toArc);
      if (ta == null) { sb.append("Link to unknown arc: ").append(l.toArc).append("\n"); continue; }
      File f = resolveFile(ta.script);
      if (f == null || !f.exists()) { sb.append("Link target arc script missing: ").append(ta.script).append("\n"); continue; }
      String lab = (l.toLabel != null && !l.toLabel.isBlank()) ? l.toLabel : ta.entryLabel;
      if (lab != null && !lab.isBlank()) {
        boolean ok = hasLabel(f, lab);
        if (!ok) sb.append("Link target label missing: ").append(l.toArc).append(":").append(lab).append("\n");
      }
    }
    if (sb.length() == 0) {
      Alert a = new Alert(Alert.AlertType.INFORMATION, "Timeline OK"); a.setHeaderText(null); a.setTitle("Validate"); a.showAndWait();
    } else {
      TextArea ta = new TextArea(sb.toString()); ta.setEditable(false); ta.setWrapText(true);
      Dialog<Void> dlg = new Dialog<>(); dlg.setTitle("Validation Issues"); dlg.getDialogPane().setContent(ta); dlg.getDialogPane().getButtonTypes().add(ButtonType.OK); dlg.showAndWait();
    }
  }

  private boolean hasLabel(File vnsFile, String label) {
    try (FileInputStream in = new FileInputStream(vnsFile)) {
      com.jvn.core.vn.script.VnScriptParser p = new com.jvn.core.vn.script.VnScriptParser();
      com.jvn.core.vn.VnScenario sc = p.parse(in);
      return sc.getLabelIndex(label) != null;
    } catch (Exception e) {
      return false;
    }
  }
  public static class Link {
    public String fromArc;
    public String fromLabel;
    public String toArc;
    public String toLabel;
    public String toLine() { return "LINK|" + nn(fromArc) + "|" + nn(fromLabel) + "|" + nn(toArc) + "|" + nn(toLabel); }
  }

  private final ListView<Arc> arcs = new ListView<>();
  private final ListView<Link> links = new ListView<>();
  private final StoryGraphPane graph = new StoryGraphPane();
  private final ScrollPane graphScroll = new ScrollPane(graph);
  private File projectRoot;
  private Consumer<Arc> onRunArc;
  private Consumer<Link> onRunLink;

  public StoryTimelineView() {
    arcs.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Arc a, boolean empty) {
        super.updateItem(a, empty);
        setText(empty || a == null ? null : a.name + "  [" + a.script + (a.entryLabel == null || a.entryLabel.isBlank() ? "" : (" :: " + a.entryLabel)) + "]");
      }
    });
    links.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Link l, boolean empty) {
        super.updateItem(l, empty);
        setText(empty || l == null ? null : l.fromArc + ":" + nn(l.fromLabel) + "  ->  " + l.toArc + ":" + nn(l.toLabel));
      }
    });

    // Graph on top, lists below
    SplitPane lists = new SplitPane();
    lists.setOrientation(javafx.geometry.Orientation.VERTICAL);
    lists.getItems().addAll(new TitledPane("Arcs", arcs), new TitledPane("Links", links));
    lists.setDividerPositions(0.5);
    graphScroll.setFitToWidth(true); graphScroll.setFitToHeight(true);
    SplitPane rootSplit = new SplitPane();
    rootSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
    rootSplit.getItems().addAll(graphScroll, lists);
    rootSplit.setDividerPositions(0.55);
    setCenter(rootSplit);

    Button bAddArc = new Button("Add Arc");
    bAddArc.setOnAction(e -> addArc());
    Button bRemoveArc = new Button("Remove Arc");
    bRemoveArc.setOnAction(e -> { Arc a = arcs.getSelectionModel().getSelectedItem(); if (a != null) arcs.getItems().remove(a); });
    Button bAddLink = new Button("Add Link");
    bAddLink.setOnAction(e -> addLink());
    Button bRemoveLink = new Button("Remove Link");
    bRemoveLink.setOnAction(e -> { Link l = links.getSelectionModel().getSelectedItem(); if (l != null) links.getItems().remove(l); });
    Button bOpenArc = new Button("Open Arc");
    bOpenArc.setOnAction(e -> openArc());
    Button bRunArc = new Button("Run Arc");
    bRunArc.setOnAction(e -> { Arc a = arcs.getSelectionModel().getSelectedItem(); if (a != null && onRunArc != null) onRunArc.accept(a); });
    Button bRunLink = new Button("Run Link");
    bRunLink.setOnAction(e -> { Link l = links.getSelectionModel().getSelectedItem(); if (l != null && onRunLink != null) onRunLink.accept(l); });
    Button bCopyGoto = new Button("Copy Goto");
    bCopyGoto.setOnAction(e -> copyGoto());
    Button bSave = new Button("Save");
    bSave.setOnAction(e -> save());
    Button bLoad = new Button("Load");
    bLoad.setOnAction(e -> load());
    Button bValidate = new Button("Validate");
    bValidate.setOnAction(e -> validate());
    TextField tfSearch = new TextField(); tfSearch.setPromptText("Search arcs...");
    tfSearch.textProperty().addListener((o, ov, nv) -> graph.highlight(nv));
    Button bAuto = new Button("Auto Layout"); bAuto.setOnAction(e -> { graph.autoLayout(); save(); });
    Slider zoom = new Slider(0.6, 2.0, 1.0); zoom.setPrefWidth(120);
    zoom.valueProperty().addListener((o, ov, nv) -> { double s = nv.doubleValue(); graph.setScaleX(s); graph.setScaleY(s); });
    FlowPane actions = new FlowPane(6, 6);
    actions.setPadding(new Insets(6));
    actions.getChildren().addAll(bAddArc, bRemoveArc, bAddLink, bRemoveLink, bOpenArc, bRunArc, bRunLink, bCopyGoto, new Label("Zoom"), zoom, tfSearch, bAuto, bSave, bLoad, bValidate);
    // Wrap to new rows instead of squeezing buttons to tiny squares
    actions.prefWrapLengthProperty().bind(widthProperty().subtract(24));
    setTop(actions);

    // Graph actions wiring
    graph.setOnRunArc(a -> { if (onRunArc != null) onRunArc.accept(a); });
    graph.setOnRunLink(l -> { if (onRunLink != null) onRunLink.accept(l); });
    arcs.getSelectionModel().selectedItemProperty().addListener((o, ov, nv) -> {
      if (nv != null) graph.highlight(nv.name);
    });
  }

  public void setProjectRoot(File dir) {
    this.projectRoot = dir;
    load();
    refreshGraph();
  }

  public void setOnRunArc(Consumer<Arc> c) { this.onRunArc = c; }
  public void setOnRunLink(Consumer<Link> c) { this.onRunLink = c; }
  public List<Arc> getArcs() { return new ArrayList<>(arcs.getItems()); }
  public List<Link> getLinks() { return new ArrayList<>(links.getItems()); }
  public Arc findArc(String name) {
    for (Arc a : arcs.getItems()) if (a != null && name != null && name.equals(a.name)) return a; return null;
  }

  private void addArc() {
    TextInputDialog dlg = new TextInputDialog("Arc");
    dlg.setHeaderText(null); dlg.setTitle("Arc Name"); dlg.setContentText("Name:");
    var res = dlg.showAndWait(); if (res.isEmpty()) return; String name = res.get().trim(); if (name.isEmpty()) return;
    FileChooser fc = new FileChooser();
    fc.setTitle("Select VNS Script");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("VNS scripts", "*.vns"));
    File f = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
    if (f == null) return;
    TextInputDialog ldlg = new TextInputDialog("");
    ldlg.setHeaderText(null); ldlg.setTitle("Entry Label"); ldlg.setContentText("Label (optional):");
    var lres = ldlg.showAndWait(); String label = lres.isEmpty() ? "" : lres.get().trim();
    Arc a = new Arc(); a.name = name; a.script = toRelative(f); a.entryLabel = label;
    arcs.getItems().add(a);
    refreshGraph();
  }

  private void addLink() {
    if (arcs.getItems().isEmpty()) return;
    GridPane g = new GridPane();
    g.setHgap(6); g.setVgap(6); g.setPadding(new Insets(8));
    ComboBox<Arc> fromArc = new ComboBox<>(); fromArc.getItems().setAll(arcs.getItems());
    TextField fromLabel = new TextField();
    ComboBox<Arc> toArc = new ComboBox<>(); toArc.getItems().setAll(arcs.getItems());
    TextField toLabel = new TextField();
    g.addRow(0, new Label("From Arc"), fromArc);
    g.addRow(1, new Label("From Label"), fromLabel);
    g.addRow(2, new Label("To Arc"), toArc);
    g.addRow(3, new Label("To Label"), toLabel);
    Dialog<ButtonType> dlg = new Dialog<>(); dlg.setTitle("Add Link"); dlg.getDialogPane().setContent(g); dlg.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    var res = dlg.showAndWait(); if (res.isEmpty() || res.get() != ButtonType.OK) return;
    Arc fa = fromArc.getValue(); Arc ta = toArc.getValue(); if (fa == null || ta == null) return;
    Link l = new Link(); l.fromArc = fa.name; l.fromLabel = fromLabel.getText(); l.toArc = ta.name; l.toLabel = toLabel.getText();
    links.getItems().add(l);
    refreshGraph();
  }

  private void openArc() {
    Arc a = arcs.getSelectionModel().getSelectedItem(); if (a == null) return;
    try { java.awt.Desktop.getDesktop().open(resolveFile(a.script)); } catch (Exception ignored) {}
  }

  private void copyGoto() {
    Link l = links.getSelectionModel().getSelectedItem(); if (l == null) return;
    String snip = "[goto " + l.toArc + ":" + nn(l.toLabel) + "]";
    javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
    cc.putString(snip);
    javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
  }

  private void save() {
    if (projectRoot == null) return;
    File f = new File(projectRoot, "story.timeline");
    try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
      for (Arc a : arcs.getItems()) pw.println(a.toLine());
      for (Link l : links.getItems()) pw.println(l.toLine());
    } catch (Exception ignored) {}
  }

  private void load() {
    if (projectRoot == null) return;
    File f = new File(projectRoot, "story.timeline");
    if (!f.exists()) return;
    List<Arc> alist = new ArrayList<>();
    List<Link> llist = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new FileReader(f))) {
      String line;
      while ((line = br.readLine()) != null) {
        if (line.startsWith("ARC|")) {
          String[] t = line.split("\\|", -1);
          if (t.length >= 4) {
            Arc a = new Arc(); a.name = n(t[1]); a.script = n(t[2]); a.entryLabel = n(t[3]);
            if (t.length >= 6) {
              try { a.x = Double.parseDouble(t[4]); } catch (Exception ignore) {}
              try { a.y = Double.parseDouble(t[5]); } catch (Exception ignore) {}
            }
            alist.add(a);
          }
        } else if (line.startsWith("LINK|")) {
          String[] t = line.split("\\|", -1);
          if (t.length >= 5) { Link l = new Link(); l.fromArc = n(t[1]); l.fromLabel = n(t[2]); l.toArc = n(t[3]); l.toLabel = n(t[4]); llist.add(l); }
        }
      }
    } catch (Exception ignored) {}
    arcs.getItems().setAll(alist);
    links.getItems().setAll(llist);
    refreshGraph();
  }

  private static String nn(String s) { return s == null ? "" : s; }
  private static String n(String s) { return s == null ? "" : s; }

  private String toRelative(File f) {
    try {
      if (projectRoot == null) return f.getAbsolutePath();
      String root = projectRoot.getCanonicalPath();
      String abs = f.getCanonicalPath();
      if (abs.startsWith(root)) {
        String rel = abs.substring(root.length());
        if (rel.startsWith(File.separator)) rel = rel.substring(1);
        return rel.replace('\\', '/');
      }
      return abs;
    } catch (Exception e) {
      return f.getPath();
    }
  }

  private File resolveFile(String p) {
    if (p == null) return null;
    File f = new File(p);
    if (f.isAbsolute() || projectRoot == null) return f;
    return new File(projectRoot, p);
  }

  private void refreshGraph() {
    graph.setModel(arcs.getItems(), links.getItems());
  }
}
