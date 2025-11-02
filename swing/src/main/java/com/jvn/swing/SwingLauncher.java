package com.jvn.swing;

import com.jvn.core.engine.Engine;
import com.jvn.core.scene2d.Scene2D;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class SwingLauncher {
  public static void launch(Engine engine) {
    JFrame frame = new JFrame(engine != null && engine.getConfig() != null ? engine.getConfig().title() : "JVN");
    int w = engine != null && engine.getConfig() != null ? engine.getConfig().width() : 960;
    int h = engine != null && engine.getConfig() != null ? engine.getConfig().height() : 540;

    JPanel panel = new JPanel() {
      @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setColor(Color.BLACK);
        g2.fillRect(0, 0, getWidth(), getHeight());
        if (engine != null) {
          var scene = engine.scenes().peek();
          if (scene instanceof Scene2D s2d) {
            SwingBlitter2D bl = new SwingBlitter2D(g2);
            s2d.render(bl, getWidth(), getHeight());
            bl.dispose();
          } else {
            g2.setColor(Color.WHITE);
            g2.drawString("JVN Swing - No compatible scene loaded", 20, 30);
          }
        }
        g2.dispose();
      }
    };
    panel.setFocusable(true);
    panel.requestFocusInWindow();

    // Input wiring
    panel.addKeyListener(new KeyAdapter() {
      @Override public void keyPressed(KeyEvent e) {
        if (engine != null && engine.input() != null) engine.input().keyDown(KeyEvent.getKeyText(e.getKeyCode()));
      }
      @Override public void keyReleased(KeyEvent e) {
        if (engine != null && engine.input() != null) engine.input().keyUp(KeyEvent.getKeyText(e.getKeyCode()));
      }
    });
    panel.addMouseMotionListener(new MouseMotionAdapter() {
      @Override public void mouseMoved(MouseEvent e) {
        if (engine != null && engine.input() != null) engine.input().setMousePosition(e.getX(), e.getY());
      }
      @Override public void mouseDragged(MouseEvent e) {
        if (engine != null && engine.input() != null) engine.input().setMousePosition(e.getX(), e.getY());
      }
    });
    panel.addMouseListener(new MouseAdapter() {
      @Override public void mousePressed(MouseEvent e) {
        if (engine != null && engine.input() != null) engine.input().mouseDown(mapButton(e.getButton()));
      }
      @Override public void mouseReleased(MouseEvent e) {
        if (engine != null && engine.input() != null) engine.input().mouseUp(mapButton(e.getButton()));
      }
    });
    panel.addMouseWheelListener(e -> {
      if (engine != null && engine.input() != null) engine.input().addScrollDeltaY(-e.getPreciseWheelRotation());
    });

    frame.setContentPane(panel);
    frame.setSize(w, h);
    frame.setLocationRelativeTo(null);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    frame.setVisible(true);

    final long[] lastNs = { -1L };
    Timer timer = new Timer(16, evt -> {
      long now = System.nanoTime();
      if (lastNs[0] < 0) { lastNs[0] = now; return; }
      long deltaMs = (now - lastNs[0]) / 1_000_000L;
      lastNs[0] = now;
      if (engine != null) engine.update(deltaMs);
      panel.repaint();
    });
    timer.start();
  }

  private static int mapButton(int awtButton) {
    return switch (awtButton) {
      case MouseEvent.BUTTON1 -> 1;
      case MouseEvent.BUTTON2 -> 2;
      case MouseEvent.BUTTON3 -> 3;
      default -> 1;
    };
  }
}
