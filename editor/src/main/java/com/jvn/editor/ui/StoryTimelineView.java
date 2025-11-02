package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class StoryTimelineView extends BorderPane {
  public static class Arc {
    public String name;
    public String script;
    public String entryLabel;
    public String toLine() { return "ARC|" + nn(name) + "|" + nn(script) + "|" + nn(entryLabel); }
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
  private File projectRoot;

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

    SplitPane sp = new SplitPane();
    sp.setOrientation(javafx.geometry.Orientation.VERTICAL);
    sp.getItems().addAll(new TitledPane("Arcs", arcs), new TitledPane("Links", links));
    sp.setDividerPositions(0.5);
    setCenter(sp);

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
    Button bCopyGoto = new Button("Copy Goto");
    bCopyGoto.setOnAction(e -> copyGoto());
    Button bSave = new Button("Save");
    bSave.setOnAction(e -> save());
    Button bLoad = new Button("Load");
    bLoad.setOnAction(e -> load());
    HBox bar = new HBox(6, bAddArc, bRemoveArc, bAddLink, bRemoveLink, bOpenArc, bCopyGoto, bSave, bLoad);
    bar.setPadding(new Insets(6));
    setTop(bar);
  }

  public void setProjectRoot(File dir) {
    this.projectRoot = dir;
    load();
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
    Arc a = new Arc(); a.name = name; a.script = f.getAbsolutePath(); a.entryLabel = label;
    arcs.getItems().add(a);
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
  }

  private void openArc() {
    Arc a = arcs.getSelectionModel().getSelectedItem(); if (a == null) return;
    try { java.awt.Desktop.getDesktop().open(new File(a.script)); } catch (Exception ignored) {}
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
          if (t.length >= 4) { Arc a = new Arc(); a.name = n(t[1]); a.script = n(t[2]); a.entryLabel = n(t[3]); alist.add(a); }
        } else if (line.startsWith("LINK|")) {
          String[] t = line.split("\\|", -1);
          if (t.length >= 5) { Link l = new Link(); l.fromArc = n(t[1]); l.fromLabel = n(t[2]); l.toArc = n(t[3]); l.toLabel = n(t[4]); llist.add(l); }
        }
      }
    } catch (Exception ignored) {}
    arcs.getItems().setAll(alist);
    links.getItems().setAll(llist);
  }

  private static String nn(String s) { return s == null ? "" : s; }
  private static String n(String s) { return s == null ? "" : s; }
}
