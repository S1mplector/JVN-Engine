package com.jvn.editor.ui;

import javafx.scene.layout.BorderPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.List;
import java.io.File;

public class JesCodeEditor extends BorderPane {
  private final CodeArea codeArea = new CodeArea();
  private CodeAutoCompleter completer;
  private File projectRoot;

  private static final String[] KEYWORDS = new String[] {
    "scene","entity","component","on","key","do","timeline",
    "wait","move","rotate","scale","call",
    // common props / literals
    "true","false","rgb","rgba","shape","circle","box",
    "static","sensor","text","image","align","additive"
  };

  private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
  private static final String PAREN_PATTERN = "[(){}]";
  private static final String COLON_COMMA_PATTERN = "[: ,]";
  private static final String STRING_PATTERN = "\"([^\\\"]|\\\\.)*\"";
  private static final String NUMBER_PATTERN = "-?\\b\\d+(?:\\.\\d+)?\\b";
  private static final String COMMENT_PATTERN = "//[^\\n]*";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<COMMENT>" + COMMENT_PATTERN + ")"
    + "|(?<STRING>" + STRING_PATTERN + ")"
    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
    + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
    + "|(?<PAREN>" + PAREN_PATTERN + ")"
    + "|(?<PUNCT>" + COLON_COMMA_PATTERN + ")"
  );

  public JesCodeEditor() {
    codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
    codeArea.textProperty().addListener((obs, oldText, newText) -> applyHighlighting(newText));
    applyHighlighting("");

    VirtualizedScrollPane<CodeArea> sp = new VirtualizedScrollPane<>(codeArea);
    setCenter(sp);

    var css = JesCodeEditor.class.getResource("/com/jvn/editor/editor.css");
    if (css != null) {
      getStylesheets().add(css.toExternalForm());
      codeArea.getStylesheets().add(css.toExternalForm());
    }

    completer = new CodeAutoCompleter(codeArea, ctx -> provideSuggestions(ctx));
  }

  public String getText() { return codeArea.getText(); }
  public void setText(String s) { codeArea.replaceText(s == null ? "" : s); }
  public void setProjectRoot(File root) { this.projectRoot = root; if (completer != null) completer.setProjectRoot(root); }

  private void applyHighlighting(String text) {
    codeArea.setStyleSpans(0, computeHighlighting(text == null ? "" : text));
  }

  private static StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher matcher = PATTERN.matcher(text);
    int lastKwEnd = 0;
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    while (matcher.find()) {
      String styleClass =
        matcher.group("COMMENT") != null ? "comment" :
        matcher.group("STRING") != null ? "string" :
        matcher.group("NUMBER") != null ? "number" :
        matcher.group("KEYWORD") != null ? "keyword" :
        matcher.group("PAREN") != null ? "punct" :
        matcher.group("PUNCT") != null ? "punct" : null;
      assert styleClass != null;
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastKwEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
    return spansBuilder.create();
  }

  private List<CodeAutoCompleter.Suggestion> provideSuggestions(CodeAutoCompleter.Context ctx) {
    String p = ctx.prefix == null ? "" : ctx.prefix;
    String pl = p.toLowerCase();
    List<CodeAutoCompleter.Suggestion> out = new ArrayList<>();
    // keywords
    for (String kw : KEYWORDS) if (kw.startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(kw));
    // if inside quotes and line hints an image value, suggest asset ids
    String line = currentLine(ctx.text, ctx.caret).toLowerCase();
    boolean wantsImage = line.contains("image") || line.contains("texture");
    if (wantsImage) {
      for (String dir : List.of("assets/ui", "assets/backgrounds", "assets/cg", "assets/characters")) {
        for (String id : CodeAutoCompleter.listAssetIds(projectRoot, dir, ".png", ".jpg", ".jpeg", ".webp")) {
          String nm = id.contains("/") ? id.substring(id.lastIndexOf('/')+1) : id;
          if (nm.toLowerCase().startsWith(pl) || id.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(id));
        }
      }
    }
    // de-dup
    if (out.size() > 1) {
      List<String> seen = new ArrayList<>();
      out.removeIf(sug -> { String k = sug.insert; if (seen.contains(k)) return true; seen.add(k); return false; });
    }
    return out;
  }

  private static String currentLine(String text, int caret) {
    if (text == null) return "";
    int s = text.lastIndexOf('\n', Math.max(0, caret-1));
    int e = text.indexOf('\n', caret);
    if (s < 0) s = 0; else s = s + 1;
    if (e < 0) e = text.length();
    return text.substring(s, Math.min(e, text.length()));
  }
}
