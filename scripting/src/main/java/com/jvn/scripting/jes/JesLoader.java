package com.jvn.scripting.jes;

import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.scripting.jes.ast.JesAst;

import java.io.InputStream;
import java.util.List;

public class JesLoader {
  public static Scene2DBase load(InputStream in) throws Exception {
    List<JesToken> toks = JesTokenizer.tokenize(in);
    JesAst.Program prog = new JesParser(toks).parseProgram();
    if (prog.scenes.isEmpty()) throw new IllegalArgumentException("No scene defined");
    JesAst.SceneDecl s = prog.scenes.get(0);
    Scene2DBase scene = new Scene2DBase();
    // Minimal interpreter: only Panel2D
    for (JesAst.EntityDecl e : s.entities) {
      e.components.forEach(c -> {
        if ("Panel2D".equals(c.type)) {
          double x = num(c, "x", 0);
          double y = num(c, "y", 0);
          double w = num(c, "w", 1);
          double h = num(c, "h", 1);
          Panel2D p = new Panel2D(w, h);
          Object fill = c.props.get("fill");
          if (fill instanceof double[] arr) {
            p.setFill(arr[0], arr[1], arr[2], arr[3]);
          }
          p.setPosition(x, y);
          scene.add(p);
        }
      });
    }
    return scene;
  }

  private static double num(JesAst.ComponentDecl c, String key, double def) {
    Object v = c.props.get(key);
    return v instanceof Number n ? n.doubleValue() : def;
  }
}
