package com.jvn.scripting.jes;

import com.jvn.scripting.jes.ast.JesAst;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static com.jvn.scripting.jes.JesTokenType.*;

public class JesParser {
  private final List<JesToken> toks;
  private int i = 0;

  public JesParser(List<JesToken> toks) { this.toks = toks == null ? new ArrayList<>() : toks; }

  private JesToken peek() { return i < toks.size() ? toks.get(i) : new JesToken(EOF, "", -1, -1); }
  private JesToken prev() { return toks.get(Math.max(0, i-1)); }
  private boolean match(JesTokenType t) { if (peek().type == t) { i++; return true; } return false; }
  private JesToken expect(JesTokenType t, String msg) { if (match(t)) return prev(); throw error(msg + " at " + peek()); }
  private RuntimeException error(String m) { return new RuntimeException(m); }

  public JesAst.Program parseProgram() {
    JesAst.Program p = new JesAst.Program();
    while (peek().type != EOF) {
      if (match(IDENT) && "scene".equals(prev().lexeme)) {
        p.scenes.add(parseScene());
      } else {
        // Skip unknown token
        i++;
      }
    }
    return p;
  }

  private JesAst.SceneDecl parseScene() {
    JesAst.SceneDecl s = new JesAst.SceneDecl();
    s.name = expect(STRING, "scene name").lexeme;
    expect(LBRACE, "'{'");
    while (!match(RBRACE)) {
      if (match(IDENT) && "entity".equals(prev().lexeme)) {
        s.entities.add(parseEntity());
      } else if (match(IDENT) && "on".equals(prev().lexeme)) {
        s.bindings.add(parseBinding());
      } else if (match(IDENT) && "timeline".equals(prev().lexeme)) {
        expect(LBRACE, "'{'");
        while (!match(RBRACE)) {
          if (match(IDENT)) {
            String kind = prev().lexeme;
            s.timeline.add(parseTimelineAction(kind));
          } else {
            i++;
          }
        }
      } else if (prev().type == IDENT) {
        // scene-level prop: key : value
        String key = prev().lexeme;
        expect(COLON, ":");
        Object val = parseValue();
        s.props.put(key, val);
      } else {
        i++; // skip
      }
    }
    return s;
  }

  private JesAst.TimelineAction parseTimelineAction(String kind) {
    JesAst.TimelineAction a = new JesAst.TimelineAction();
    a.type = kind;
    switch (kind) {
      case "wait" -> {
        double ms = parseNum();
        a.props.put("ms", ms);
      }
      case "call" -> {
        String name = expect(STRING, "function name").lexeme;
        a.target = name;
      }
      case "move", "rotate", "scale" -> {
        String target = expect(STRING, "entity name").lexeme;
        a.target = target;
        expect(LBRACE, "'{'");
        while (!match(RBRACE)) {
          if (match(IDENT)) {
            String k = prev().lexeme;
            expect(COLON, ":");
            Object v = parseValue();
            a.props.put(k, v);
          } else { i++; }
        }
      }
      default -> {}
    }
    return a;
  }

  private JesAst.InputBinding parseBinding() {
    // already consumed 'on'
    expect(IDENT, "key"); // expect 'key'
    String keyName = expect(STRING, "key name").lexeme;
    expect(IDENT, "do"); // expect 'do'
    String action = expect(IDENT, "action").lexeme;
    JesAst.InputBinding b = new JesAst.InputBinding();
    b.key = keyName; b.action = action;
    if (match(LBRACE)) {
      while (!match(RBRACE)) {
        if (match(IDENT)) {
          String k = prev().lexeme;
          expect(COLON, ":");
          Object v = parseValue();
          b.props.put(k, v);
        } else i++;
      }
    }
    return b;
  }

  private JesAst.EntityDecl parseEntity() {
    JesAst.EntityDecl e = new JesAst.EntityDecl();
    e.name = expect(STRING, "entity name").lexeme;
    expect(LBRACE, "'{'");
    while (!match(RBRACE)) {
      if (match(IDENT) && "component".equals(prev().lexeme)) {
        e.components.add(parseComponent());
      } else {
        i++; // skip
      }
    }
    return e;
  }

  private JesAst.ComponentDecl parseComponent() {
    JesAst.ComponentDecl c = new JesAst.ComponentDecl();
    c.type = expect(IDENT, "component type").lexeme;
    expect(LBRACE, "'{'");
    while (!match(RBRACE)) {
      if (match(IDENT)) {
        String key = prev().lexeme;
        expect(COLON, ":");
        Object val = parseValue();
        c.props.put(key, val);
      } else {
        i++;
      }
    }
    return c;
  }

  private Object parseValue() {
    if (match(NUMBER)) {
      return Double.parseDouble(prev().lexeme);
    } else if (match(STRING)) {
      return prev().lexeme;
    } else if (match(IDENT) && "rgb".equals(prev().lexeme)) {
      expect(LPAREN, "(");
      double r = parseNum(); expect(COMMA, ",");
      double g = parseNum(); expect(COMMA, ",");
      double b = parseNum();
      double a = 1.0;
      if (match(COMMA)) a = parseNum();
      expect(RPAREN, ")");
      return new double[]{r,g,b,a};
    } else if (match(IDENT) && ("true".equalsIgnoreCase(prev().lexeme) || "false".equalsIgnoreCase(prev().lexeme))) {
      return Boolean.parseBoolean(prev().lexeme);
    }
    throw error("value expected");
  }

  private double parseNum() {
    JesToken t = expect(NUMBER, "number");
    return Double.parseDouble(t.lexeme);
  }
}
