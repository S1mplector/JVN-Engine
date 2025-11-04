package com.jvn.editor.ui;

import com.jvn.core.menu.MainMenuScene;
import com.jvn.core.vn.VnSettings;
import com.jvn.core.vn.save.VnSaveManager;
import com.jvn.fx.menu.MenuRenderer;
import com.jvn.fx.menu.MenuTheme;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;

import java.io.StringReader;
import java.util.Properties;

public class MenuThemePreviewView extends StackPane {
  private final Canvas canvas = new Canvas(1200, 740);
  private final GraphicsContext gc = canvas.getGraphicsContext2D();
  private final MenuRenderer renderer = new MenuRenderer(gc, MenuTheme.defaults());
  private final MainMenuScene previewScene = new MainMenuScene(null, new VnSettings(), new VnSaveManager(), "demo.vns", null);
  private MenuTheme theme = MenuTheme.defaults();

  public MenuThemePreviewView() {
    getChildren().add(canvas);
    // keep focus off; no input handling here
  }

  public void setThemeFromText(String text) {
    try {
      Properties p = new Properties();
      if (text != null && !text.isBlank()) {
        p.load(new StringReader(text));
      }
      MenuTheme t = MenuTheme.defaults();
      t.apply(p);
      this.theme = t;
      this.renderer.setTheme(t);
    } catch (Exception ignored) {
      // keep defaults
    }
  }

  public void setSize(double w, double h) {
    canvas.setWidth(Math.max(1, w));
    canvas.setHeight(Math.max(1, h));
  }

  public void render(long deltaMs) {
    double w = canvas.getWidth();
    double h = canvas.getHeight();
    renderer.renderMainMenu(previewScene, w, h);
  }
}
