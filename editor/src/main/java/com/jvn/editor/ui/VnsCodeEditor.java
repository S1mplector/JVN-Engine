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
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class VnsCodeEditor extends BorderPane {
  private final CodeArea codeArea = new CodeArea();
  private CodeAutoCompleter completer;
  private File projectRoot;

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
    Button bSettings = new Button("Settings");
    bSettings.setOnAction(e -> insertSnippet("[settings]" + System.lineSeparator()));
    Button bMainMenu = new Button("MainMenu");
    bMainMenu.setOnAction(e -> insertSnippet("[mainmenu demo.vns]" + System.lineSeparator()));
    Button bMenuSave = new Button("MenuSave");
    bMenuSave.setOnAction(e -> insertSnippet("[menu save]" + System.lineSeparator()));
    Button bMenuLoad = new Button("MenuLoad");
    bMenuLoad.setOnAction(e -> insertSnippet("[menu load demo.vns]" + System.lineSeparator()));
    Button bLoad = new Button("Load");
    bLoad.setOnAction(e -> insertSnippet("[load arc2.vns label start]" + System.lineSeparator()));
    Button bGotoArc = new Button("GotoArc");
    bGotoArc.setOnAction(e -> insertSnippet("[goto arcName:labelName]" + System.lineSeparator()));
    Button bJesCall = new Button("JES Call");
    bJesCall.setOnAction(e -> insertSnippet("[jes call flash color=#ff0 dur=300]" + System.lineSeparator()));
    ToolBar bar = new ToolBar(bDialogue, bChoice, bBackground, bJump, bSet, bIf, bHud, bJava, bSettings, bMainMenu, bMenuSave, bMenuLoad, bLoad, bGotoArc, bJesCall);
    setTop(bar);

    var css = VnsCodeEditor.class.getResource("/com/jvn/editor/editor.css");
    if (css != null) {
      getStylesheets().add(css.toExternalForm());
      codeArea.getStylesheets().add(css.toExternalForm());
    }

    completer = new CodeAutoCompleter(codeArea, ctx -> provideSuggestions(ctx));
  }

  public String getText() { return codeArea.getText(); }
  public void setText(String s) { codeArea.replaceText(s == null ? "" : s); }
  public void setProjectRoot(File root) { this.projectRoot = root; if (completer != null) completer.setProjectRoot(root); }

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

  private List<CodeAutoCompleter.Suggestion> provideSuggestions(CodeAutoCompleter.Context ctx) {
    String p = ctx.prefix == null ? "" : ctx.prefix;
    String pl = p.toLowerCase();
    List<CodeAutoCompleter.Suggestion> out = new ArrayList<>();
    if (pl.startsWith("@")) {
      out.add(new CodeAutoCompleter.Suggestion("@scenario "));
      out.add(new CodeAutoCompleter.Suggestion("@character "));
      out.add(new CodeAutoCompleter.Suggestion("@background "));
      out.add(new CodeAutoCompleter.Suggestion("@label "));
    }
    if (pl.startsWith("[")) {
      out.add(new CodeAutoCompleter.Suggestion("[background "));
      out.add(new CodeAutoCompleter.Suggestion("[jump "));
      out.add(new CodeAutoCompleter.Suggestion("[set "));
      out.add(new CodeAutoCompleter.Suggestion("[if "));
      out.add(new CodeAutoCompleter.Suggestion("[call hud "));
      out.add(new CodeAutoCompleter.Suggestion("[java "));
      out.add(new CodeAutoCompleter.Suggestion("[mainmenu "));
      out.add(new CodeAutoCompleter.Suggestion("[load "));
      out.add(new CodeAutoCompleter.Suggestion("[goto "));
      out.add(new CodeAutoCompleter.Suggestion("[jes call "));
    }
    // Labels from current document
    for (String lab : extractLabels(ctx.text)) {
      if (lab.toLowerCase().startsWith(pl)) out.add(new CodeAutoCompleter.Suggestion(lab));
    }
    // Asset IDs for backgrounds
    for (String id : CodeAutoCompleter.listAssetIds(projectRoot, "assets/backgrounds", ".png", ".jpg", ".jpeg", ".webp")) {
      String nm = id.contains("/") ? id.substring(id.lastIndexOf('/')+1) : id;
      if (nm.toLowerCase().startsWith(pl) || id.toLowerCase().startsWith(pl)) {
        out.add(new CodeAutoCompleter.Suggestion(id));
      }
    }
    // Make unique
    if (out.size() > 1) {
      List<String> seen = new ArrayList<>();
      out.removeIf(sug -> { String k = sug.insert; if (seen.contains(k)) return true; seen.add(k); return false; });
    }
    return out;
  }

  private static List<String> extractLabels(String text) {
    try {
      List<String> res = new ArrayList<>();
      if (text == null) return res;
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?m)^@label\\s+(\\S+)").matcher(text);
      while (m.find()) res.add(m.group(1));
      return res;
    } catch (Exception ignore) { return java.util.List.of(); }
  }
}
