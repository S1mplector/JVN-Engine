package com.jvn.editor.ui;

import javafx.scene.layout.BorderPane;
import javafx.scene.control.Button;
import javafx.scene.control.ToolBar;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VnsCodeEditor extends BorderPane {
  private final CodeArea codeArea = new CodeArea();

  private static final String DIRECTIVE_PATTERN = "@(?:scenario|character|background|label)\b";
  private static final String BRACKET_PATTERN = "\\[[^\\]]+\\]";
  private static final String SPEAKER_PATTERN = "(?m)^(?:[^\\s:][^:]{0,30}):";
  private static final String CHOICE_PATTERN = "(?m)^>.*$";
  private static final String STRING_PATTERN = "\"([^\\\"]|\\\\.)*\"";
  private static final String NUMBER_PATTERN = "-?\\b\\d+(?:\\.\\d+)?\\b";
  private static final String COMMENT_PATTERN = "(?m)#.*$";
  private static final String PUNCT_PATTERN = "[\\[\\]()>: ,]";

  private static final Pattern PATTERN = Pattern.compile(
      "(?<COMMENT>" + COMMENT_PATTERN + ")"
    + "|(?<STRING>" + STRING_PATTERN + ")"
    + "|(?<NUMBER>" + NUMBER_PATTERN + ")"
    + "|(?<DIRECTIVE>" + DIRECTIVE_PATTERN + ")"
    + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
    + "|(?<SPEAKER>" + SPEAKER_PATTERN + ")"
    + "|(?<CHOICE>" + CHOICE_PATTERN + ")"
    + "|(?<PUNCT>" + PUNCT_PATTERN + ")"
  );

  public VnsCodeEditor() {
    codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
    codeArea.textProperty().addListener((obs, oldText, newText) -> applyHighlighting(newText));
    applyHighlighting("");

    VirtualizedScrollPane<CodeArea> sp = new VirtualizedScrollPane<>(codeArea);
    setCenter(sp);

    Button bDialogue = new Button("Dialogue");
    bDialogue.setOnAction(e -> insertSnippet("Speaker: Your line here" + System.lineSeparator()));
    Button bChoice = new Button("Choice");
    bChoice.setOnAction(e -> insertSnippet("> Choice text -> targetLabel" + System.lineSeparator()));
    Button bBackground = new Button("Background");
    bBackground.setOnAction(e -> insertSnippet("[background bgId]" + System.lineSeparator()));
    Button bJump = new Button("Jump");
    bJump.setOnAction(e -> insertSnippet("[jump labelName]" + System.lineSeparator()));
    Button bSet = new Button("Set Var");
    bSet.setOnAction(e -> insertSnippet("[set varName value]" + System.lineSeparator()));
    Button bIf = new Button("If");
    bIf.setOnAction(e -> insertSnippet("[if varName == value goto labelName]" + System.lineSeparator()));
    Button bHud = new Button("HUD");
    bHud.setOnAction(e -> insertSnippet("[call hud Hello]" + System.lineSeparator()));
    Button bJava = new Button("Java");
    bJava.setOnAction(e -> insertSnippet("[java com.example.Class#method arg1 arg2]" + System.lineSeparator()));
    ToolBar bar = new ToolBar(bDialogue, bChoice, bBackground, bJump, bSet, bIf, bHud, bJava);
    setTop(bar);

    var css = VnsCodeEditor.class.getResource("/com/jvn/editor/editor.css");
    if (css != null) {
      getStylesheets().add(css.toExternalForm());
      codeArea.getStylesheets().add(css.toExternalForm());
    }
  }

  public String getText() { return codeArea.getText(); }
  public void setText(String s) { codeArea.replaceText(s == null ? "" : s); }

  private void insertSnippet(String s) {
    int pos = codeArea.getCaretPosition();
    codeArea.insertText(pos, s);
  }

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
        matcher.group("DIRECTIVE") != null ? "keyword" :
        matcher.group("BRACKET") != null ? "keyword" :
        matcher.group("SPEAKER") != null ? "keyword" :
        matcher.group("CHOICE") != null ? "keyword" :
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
