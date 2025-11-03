package com.jvn.editor.ui;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;
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
import javafx.scene.shape.Circle;
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
    final Circle inHandle;
    final Circle outHandle;
    double dragDX, dragDY;
    java.util.function.Consumer<javafx.scene.input.MouseEvent> mousePressedHook;
    java.util.function.Consumer<javafx.scene.input.MouseEvent> mouseReleasedHook;
    NodeView(StoryTimelineView.Arc arc) {
      this.arc = arc;
      this.rect = new Rectangle(140, 44, Color.web("#2b2f3a"));
      rect.setArcWidth(8); rect.setArcHeight(8);
      rect.setStroke(Color.web("#475069"));
      rect.setStrokeWidth(1.0);
      this.label = new Text(arc.name == null ? "(unnamed)" : arc.name);
      label.setFill(Color.web("#e6e9f0"));
      label.setTranslateX(10); label.setTranslateY(26);
      // Link handles (always visible)
      this.inHandle = new Circle(6, Color.web("#4a5568"));
      this.outHandle = new Circle(6, Color.web("#4a5568"));
      inHandle.setStroke(Color.web("#94a3b8")); inHandle.setStrokeWidth(1.0);
      outHandle.setStroke(Color.web("#94a3b8")); outHandle.setStrokeWidth(1.0);
      inHandle.setCenterX(4); inHandle.setCenterY(rect.getHeight()/2);
      outHandle.setCenterX(rect.getWidth()-4); outHandle.setCenterY(rect.getHeight()/2);
      inHandle.setCursor(Cursor.CROSSHAIR);
      outHandle.setCursor(Cursor.CROSSHAIR);
      getChildren().addAll(rect, label, inHandle, outHandle);
      setCursor(Cursor.HAND);
      setLayoutX(arc.x);
      setLayoutY(arc.y);
      enableDrag();
      // Handlers are managed via NodeView's press/release with target checks
      this.setPickOnBounds(false);
    }
    private void enableDrag() {
      setOnMousePressed(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        Pane parent = (Pane) getParent();
        Point2D p = parent.sceneToLocal(e.getSceneX(), e.getSceneY());
        dragDX = p.getX() - getLayoutX();
        dragDY = p.getY() - getLayoutY();
        if (e.getTarget() == outHandle && mousePressedHook != null) {
          mousePressedHook.accept(e);
        }
      });
      setOnMouseDragged(e -> {
        if (e.getButton() != MouseButton.PRIMARY) return;
        if (e.getTarget() == outHandle) return; // don't drag node while starting a link
        Pane parent = (Pane) getParent();
        Point2D p = parent.sceneToLocal(e.getSceneX(), e.getSceneY());
        double nx = p.getX() - dragDX;
        double ny = p.getY() - dragDY;
        setLayoutX(nx); setLayoutY(ny);
        arc.x = nx; arc.y = ny;
        if (onMoved != null) onMoved.run();
      });
      setOnMouseReleased(e -> { if (mouseReleasedHook != null) mouseReleasedHook.accept(e); });
    }
    Runnable onMoved;
  }

  private final Map<String, NodeView> nodeMap = new HashMap<>();
  private final List<Group> linkViews = new ArrayList<>();
  private final List<Group> clusterViews = new ArrayList<>();
  private List<StoryTimelineView.Arc> arcs = new ArrayList<>();
  private List<StoryTimelineView.Link> links = new ArrayList<>();
  private NodeView linkingFrom;
  private Line tempLine;
  private boolean requireShiftToLink = true;

  private Consumer<StoryTimelineView.Arc> onRunArc;
  private Consumer<StoryTimelineView.Link> onRunLink;
  private Runnable onGraphChanged;
  private Runnable onLayoutCommitted;
  private Consumer<StoryTimelineView.Arc> onDeleteArc;

  public StoryGraphPane() {
    setPadding(new Insets(8));
    setPrefSize(1200, 800);
    setMinSize(600, 400);
    setOnMouseMoved(e -> {
      if (tempLine != null) {
        Point2D p = sceneToLocal(e.getSceneX(), e.getSceneY());
        tempLine.setEndX(p.getX());
        tempLine.setEndY(p.getY());
      }
    });
    setOnMouseReleased(e -> cancelLinking());
  }

  public void setOnRunArc(Consumer<StoryTimelineView.Arc> c) { this.onRunArc = c; }
  public void setOnRunLink(Consumer<StoryTimelineView.Link> c) { this.onRunLink = c; }
  public void setOnGraphChanged(Runnable r) { this.onGraphChanged = r; }
  public void setOnLayoutCommitted(Runnable r) { this.onLayoutCommitted = r; }
  public void setOnDeleteArc(Consumer<StoryTimelineView.Arc> c) { this.onDeleteArc = c; }
  public void setSimpleLinkMode(boolean enabled) { this.requireShiftToLink = !enabled; }

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
    nodeMap.clear(); linkViews.clear(); clusterViews.clear();

    // Build cluster backgrounds first (based on arc positions)
    drawClusters();

    // Build nodes
    for (StoryTimelineView.Arc a : arcs) {
      if (Double.isNaN(a.x)) a.x = 40; if (Double.isNaN(a.y)) a.y = 40;
      NodeView nv = new NodeView(a);
      nv.onMoved = this::updateLinks;
      nv.mousePressedHook = e -> {
        if (e.getButton() == MouseButton.PRIMARY) {
          if (!requireShiftToLink || e.isShiftDown()) {
            startLinking(nv, e.getSceneX(), e.getSceneY());
          }
        }
      };
      nv.mouseReleasedHook = e -> { if (linkingFrom != null) finishLinking(nv); };
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
      MenuItem miCluster = new MenuItem("Set Cluster...");
      miCluster.setOnAction(e -> {
        javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog(a.cluster == null ? "" : a.cluster);
        dlg.setHeaderText(null); dlg.setTitle("Cluster"); dlg.setContentText("Cluster name:");
        var r = dlg.showAndWait();
        if (r.isPresent()) {
          a.cluster = r.get().trim();
          refresh();
          if (onGraphChanged != null) onGraphChanged.run();
        }
      });
      MenuItem miDelete = new MenuItem("Delete Arc");
      miDelete.setOnAction(e -> { if (onDeleteArc != null) onDeleteArc.accept(a); if (onGraphChanged != null) onGraphChanged.run(); });
      cm.getItems().addAll(miOpen, miRun, miCopyGoto, miCluster, miDelete);
      nv.setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.SECONDARY) {
          cm.show(nv, e.getScreenX(), e.getScreenY());
        } else {
          cm.hide();
          if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
            // Rename arc on double click
            String old = nv.arc.name;
            javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog(old == null ? "" : old);
            dlg.setHeaderText(null); dlg.setTitle("Rename Arc"); dlg.setContentText("Arc name:");
            var res = dlg.showAndWait();
            if (res.isPresent()) {
              String nn = res.get().trim();
              if (!nn.isEmpty() && !nn.equals(old)) {
                nv.arc.name = nn;
                nv.label.setText(nn);
                updateLinkArcNames(old, nn);
                refresh();
                if (onGraphChanged != null) onGraphChanged.run();
              }
            }
          }
        }
      });
      nodeMap.put(a.name, nv);
      getChildren().add(nv);
    }

    // Build links under nodes (lines first, then arrow head)
    for (StoryTimelineView.Link l : links) {
      NodeView from = nodeMap.get(l.fromArc);
      NodeView to = nodeMap.get(l.toArc);
      if (from == null || to == null) continue;
      Group g = drawArrow(from, to);
      ContextMenu cm = new ContextMenu();
      MenuItem miRun = new MenuItem("Run Link");
      miRun.setOnAction(e -> { if (onRunLink != null) onRunLink.accept(toLink(from, to)); });
      MenuItem miDelete = new MenuItem("Delete Link");
      miDelete.setOnAction(e -> { links.removeIf(li -> li.fromArc.equals(from.arc.name) && li.toArc.equals(to.arc.name) && Objects.equals(li.toLabel, to.arc.entryLabel)); refresh(); if (onGraphChanged != null) onGraphChanged.run(); });
      cm.getItems().addAll(miRun, miDelete);
      g.setOnMouseClicked(e -> {
        if (e.getButton() == MouseButton.PRIMARY) {
          if (onRunLink != null) onRunLink.accept(toLink(from, to));
        } else if (e.getButton() == MouseButton.SECONDARY) {
          cm.show(g, e.getScreenX(), e.getScreenY());
        }
      });
      linkViews.add(g);
      getChildren().add(clusterViews.size(), g); // above clusters, behind nodes
    }
  }

  private void drawClusters() {
    Map<String, double[]> bounds = new HashMap<>();
    for (StoryTimelineView.Arc a : arcs) {
      if (a == null || a.cluster == null || a.cluster.isBlank()) continue;
      double x1 = a.x, y1 = a.y, x2 = a.x + 140, y2 = a.y + 44;
      double[] b = bounds.get(a.cluster);
      if (b == null) { b = new double[]{x1, y1, x2, y2}; bounds.put(a.cluster, b); }
      else { b[0] = Math.min(b[0], x1); b[1] = Math.min(b[1], y1); b[2] = Math.max(b[2], x2); b[3] = Math.max(b[3], y2); }
    }
    for (Map.Entry<String, double[]> e : bounds.entrySet()) {
      String name = e.getKey(); double[] b = e.getValue();
      double pad = 16;
      Rectangle bg = new Rectangle(b[0]-pad, b[1]-pad, (b[2]-b[0])+2*pad, (b[3]-b[1])+2*pad);
      bg.setArcWidth(12); bg.setArcHeight(12);
      bg.setFill(Color.web("#334155", 0.25));
      bg.setStroke(Color.web("#94a3b8")); bg.setStrokeWidth(1.0);
      Text t = new Text(name);
      t.setFill(Color.web("#cbd5e1"));
      t.setX(bg.getX()+8); t.setY(bg.getY()-6);
      Group g = new Group(bg, t);
      clusterViews.add(g);
      getChildren().add(g);
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

  private StoryTimelineView.Link toLink(NodeView from, NodeView to) {
    StoryTimelineView.Link l = new StoryTimelineView.Link();
    l.fromArc = from.arc.name;
    l.fromLabel = "";
    l.toArc = to.arc.name;
    l.toLabel = to.arc.entryLabel == null ? "" : to.arc.entryLabel;
    return l;
  }

  private void updateLinks() {
    // Rebuild links for simplicity
    setModel(arcs, links);
  }

  private void startLinking(NodeView from, double sceneX, double sceneY) {
    linkingFrom = from;
    tempLine = new Line();
    tempLine.setStroke(Color.web("#9fb3ff"));
    tempLine.getStrokeDashArray().setAll(8.0, 8.0);
    double sx = from.getLayoutX() + from.rect.getWidth() / 2.0;
    double sy = from.getLayoutY() + from.rect.getHeight() / 2.0;
    tempLine.setStartX(sx); tempLine.setStartY(sy);
    Point2D p = sceneToLocal(sceneX, sceneY);
    tempLine.setEndX(p.getX()); tempLine.setEndY(p.getY());
    getChildren().add(0, tempLine);
  }

  private void cancelLinking() {
    if (tempLine != null) getChildren().remove(tempLine);
    tempLine = null; linkingFrom = null;
  }

  private void finishLinking(NodeView target) {
    if (linkingFrom == null || target == null || linkingFrom == target) { cancelLinking(); return; }
    javafx.scene.control.TextInputDialog dlg = new javafx.scene.control.TextInputDialog("");
    dlg.setHeaderText(null); dlg.setTitle("Link Label (optional)"); dlg.setContentText("To Label:");
    var res = dlg.showAndWait(); String toLabel = res.isPresent() ? res.get().trim() : "";
    StoryTimelineView.Link l = new StoryTimelineView.Link();
    l.fromArc = linkingFrom.arc.name; l.fromLabel = ""; l.toArc = target.arc.name; l.toLabel = toLabel;
    links.add(l);
    cancelLinking();
    refresh();
    if (onGraphChanged != null) onGraphChanged.run();
  }

  private void updateLinkArcNames(String oldName, String newName) {
    if (oldName == null || newName == null) return;
    for (StoryTimelineView.Link l : links) {
      if (oldName.equals(l.fromArc)) l.fromArc = newName;
      if (oldName.equals(l.toArc)) l.toArc = newName;
    }
  }

  { // initializer to add release hook to notify layout commit for all nodes created later via refresh
  }

  @Override
  protected void layoutChildren() {
    super.layoutChildren();
    // Attach release hook to existing NodeViews to commit layout changes
    for (NodeView nv : nodeMap.values()) {
      if (nv != null) {
        nv.mouseReleasedHook = e -> {
          if (linkingFrom != null) {
            finishLinking(nv);
          } else {
            // snap to grid
            double step = 20.0;
            double nx = Math.round(nv.getLayoutX()/step)*step;
            double ny = Math.round(nv.getLayoutY()/step)*step;
            nv.setLayoutX(nx); nv.setLayoutY(ny);
            nv.arc.x = nx; nv.arc.y = ny;
            if (nv.onMoved != null) nv.onMoved.run();
          }
          if (onLayoutCommitted != null) onLayoutCommitted.run();
        };
      }
    }
  }
}
