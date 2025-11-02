package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;

import java.util.*;
import java.util.function.Consumer;

/**
 * Lightweight graph view for StoryTimelineView. Nodes are draggable arc boxes and links are arrows.
 */
public class StoryGraphPane extends Pane {
  public static class NodeView extends Group {
    final StoryTimelineView.Arc arc;
    final Rectangle rect;
    final Text label;
    double dragDX, dragDY;
    NodeView(StoryTimelineView.Arc arc) {
      this.arc = arc;
      this.rect = new Rectangle(140, 44, Color.web("#2b2f3a"));
      rect.setArcWidth(8); rect.setArcHeight(8);
      rect.setStroke(Color.web("#475069"));
      rect.setStrokeWidth(1.0);
      this.label = new Text(arc.name == null ? "(unnamed)" : arc.name);
      label.setFill(Color.web("#e6e9f0"));
      label.setTranslateX(10); label.setTranslateY(26);
      getChildren().addAll(rect, label);
      setCursor(Cursor.HAND);
      setLayoutX(arc.x);
      setLayoutY(arc.y);
      enableDrag();
    }
    private void enableDrag() {
      setOnMousePressed(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        dragDX = e.getSceneX() - getLayoutX();
        dragDY = e.getSceneY() - getLayoutY();
      });
      setOnMouseDragged(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        double nx = e.getSceneX() - dragDX;
        double ny = e.getSceneY() - dragDY;
        setLayoutX(nx); setLayoutY(ny);
        arc.x = nx; arc.y = ny;
        if (onMoved != null) onMoved.run();
      });
    }
    Runnable onMoved;
  }

  private final Map<String, NodeView> nodeMap = new HashMap<>();
  private final List<Group> linkViews = new ArrayList<>();
  private List<StoryTimelineView.Arc> arcs = new ArrayList<>();
  private List<StoryTimelineView.Link> links = new ArrayList<>();

  private Consumer<StoryTimelineView.Arc> onRunArc;
  private Consumer<StoryTimelineView.Link> onRunLink;

  public StoryGraphPane() {
    setPadding(new Insets(8));
    setPrefSize(1200, 800);
    setMinSize(600, 400);
  }

  public void setOnRunArc(Consumer<StoryTimelineView.Arc> c) { this.onRunArc = c; }
  public void setOnRunLink(Consumer<StoryTimelineView.Link> c) { this.onRunLink = c; }

  public void setModel(List<StoryTimelineView.Arc> arcs, List<StoryTimelineView.Link> links) {
    this.arcs = (arcs == null) ? new ArrayList<>() : arcs;
    this.links = (links == null) ? new ArrayList<>() : links;
    refresh();
  }

  public void autoLayout() {
    if (arcs == null || arcs.isEmpty()) return;
    int cols = Math.max(1, (int) Math.ceil(Math.sqrt(arcs.size())));
    double gapX = 220, gapY = 140;
    for (int i = 0; i < arcs.size(); i++) {
      int r = i / cols; int c = i % cols;
      StoryTimelineView.Arc a = arcs.get(i);
      a.x = 40 + c * gapX; a.y = 40 + r * gapY;
    }
    refresh();
  }

  public void highlight(String term) {
    String t = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);
    for (NodeView nv : nodeMap.values()) {
      boolean hit = !t.isEmpty() && nv.arc.name != null && nv.arc.name.toLowerCase(Locale.ROOT).contains(t);
      nv.rect.setStroke(hit ? Color.web("#66d9ef") : Color.web("#475069"));
      nv.rect.setStrokeWidth(hit ? 2.0 : 1.0);
    }
  }

  private void refresh() {
    getChildren().clear();
    nodeMap.clear(); linkViews.clear();

    // Build nodes first
    for (StoryTimelineView.Arc a : arcs) {
      if (Double.isNaN(a.x)) a.x = 40; if (Double.isNaN(a.y)) a.y = 40;
      NodeView nv = new NodeView(a);
      nv.onMoved = this::updateLinks;
      ContextMenu cm = new ContextMenu();
      MenuItem miOpen = new MenuItem("Open");
      miOpen.setOnAction(e -> { if (onRunArc != null) onRunArc.accept(a); });
      MenuItem miRun = new MenuItem("Run from Entry");
      miRun.setOnAction(e -> { if (onRunArc != null) onRunArc.accept(a); });
      MenuItem miCopyGoto = new MenuItem("Copy Goto (entry)");
      miCopyGoto.setOnAction(e -> {
        String label = a.entryLabel == null ? "" : a.entryLabel;
        String snip = "[goto " + a.name + ":" + label + "]";
        javafx.scene.input.ClipboardContent cc = new javafx.scene.input.ClipboardContent();
        cc.putString(snip);
        javafx.scene.input.Clipboard.getSystemClipboard().setContent(cc);
      });
      cm.getItems().addAll(miOpen, miRun, miCopyGoto);
      nv.setOnMouseClicked(e -> { if (e.getButton() == MouseButton.SECONDARY) cm.show(nv, e.getScreenX(), e.getScreenY()); else cm.hide(); });
      nodeMap.put(a.name, nv);
      getChildren().add(nv);
    }

    // Build links under nodes (lines first, then arrow head)
    for (StoryTimelineView.Link l : links) {
      NodeView from = nodeMap.get(l.fromArc);
      NodeView to = nodeMap.get(l.toArc);
      if (from == null || to == null) continue;
      Group g = drawArrow(from, to);
      linkViews.add(g);
      getChildren().add(0, g); // behind nodes
    }
  }

  private Group drawArrow(NodeView from, NodeView to) {
    double sx = from.getLayoutX() + from.rect.getWidth() / 2.0;
    double sy = from.getLayoutY() + from.rect.getHeight() / 2.0;
    double ex = to.getLayoutX() + to.rect.getWidth() / 2.0;
    double ey = to.getLayoutY() + to.rect.getHeight() / 2.0;

    Line line = new Line(sx, sy, ex, ey);
    line.setStroke(Color.web("#7a8499"));

    Polygon arrow = new Polygon();
    double angle = Math.atan2(ey - sy, ex - sx);
    double len = 12;
    double aw = 6;
    double x1 = ex - len * Math.cos(angle) + aw * Math.sin(angle);
    double y1 = ey - len * Math.sin(angle) - aw * Math.cos(angle);
    double x2 = ex - len * Math.cos(angle) - aw * Math.sin(angle);
    double y2 = ey - len * Math.sin(angle) + aw * Math.cos(angle);
    arrow.getPoints().addAll(ex, ey, x1, y1, x2, y2);
    arrow.setFill(Color.web("#7a8499"));

    Group g = new Group(line, arrow);
    return g;
  }

  private void updateLinks() {
    for (int i = 0; i < getChildren().size(); i++) {
      if (!(getChildren().get(i) instanceof Group)) continue;
    }
    // Rebuild links for simplicity
    setModel(arcs, links);
  }
}
