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
import java.util.function.Consumer;

public class TimelineCodeEditor extends BorderPane {
  private final CodeArea code = new CodeArea();
  private Consumer<String> onTextChanged;
  private boolean suppressEvent;

  private static final String[] KW = new String[] { "arc", "script", "entry", "at", "link", "cluster" };
  private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KW) + ")\\b";
  private static final String STRING_PATTERN = "\"([^\\\\\"]|\\\\.)*\"";
  private static final String NUMBER_PATTERN = "-?\\b\\d+(?:\\.\\d+)?\\b";
  private static final String ARROW_PATTERN = "->";
  private static final String COMMENT_PATTERN = "(?m)#.*$";
  private static final String PUNCT_PATTERN = "[,:]";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<COMMENT>" + COMMENT_PATTERN + ")"
    + "|(?<STRING>" + STRING_PATTERN + ")"
    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
    + "|(?<KEYWORD>" + KEYWORD_PATTERN + ")"
    + "|(?<ARROW>" + ARROW_PATTERN + ")"
    + "|(?<PUNCT>" + PUNCT_PATTERN + ")"
  );

  public TimelineCodeEditor() {
    code.setParagraphGraphicFactory(LineNumberFactory.get(code));
    code.textProperty().addListener((o,ov,nv) -> { applyHighlighting(nv); if (!suppressEvent && onTextChanged != null) onTextChanged.accept(nv); });
    applyHighlighting("");
    var sp = new VirtualizedScrollPane<>(code);
    setCenter(sp);
    var css = TimelineCodeEditor.class.getResource("/com/jvn/editor/editor.css");
    if (css != null) { getStylesheets().add(css.toExternalForm()); code.getStylesheets().add(css.toExternalForm()); }
  }

  public void setOnTextChanged(Consumer<String> c) { this.onTextChanged = c; }
  public String getText() { return code.getText(); }
  public void setText(String s) { code.replaceText(s == null ? "" : s); }
  public void setTextNoEvent(String s) { try { suppressEvent = true; setText(s); } finally { suppressEvent = false; } }

  private void applyHighlighting(String text) {
    code.setStyleSpans(0, computeHighlighting(text == null ? "" : text));
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
        matcher.group("ARROW") != null ? "punct" :
        matcher.group("PUNCT") != null ? "punct" : null;
      assert styleClass != null;
      spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
      spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
      lastKwEnd = matcher.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
    return spansBuilder.create();
  }
}
