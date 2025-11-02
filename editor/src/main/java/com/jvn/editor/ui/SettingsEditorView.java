package com.jvn.editor.ui;

import com.jvn.core.vn.VnSettings;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;

import java.io.*;
import java.util.Properties;

public class SettingsEditorView extends BorderPane {
  private File projectRoot;
  private final Slider slText = new Slider(10, 120, 40);
  private final Slider slBgm = new Slider(0, 1, 0.7);
  private final Slider slSfx = new Slider(0, 1, 0.7);
  private final Slider slVoice = new Slider(0, 1, 0.7);
  private final Slider slAuto = new Slider(500, 5000, 1500);
  private final CheckBox cbSkipUnread = new CheckBox("Skip Unread Text");
  private final CheckBox cbSkipAfterChoices = new CheckBox("Skip After Choices");

  public SettingsEditorView() {
    setPadding(new Insets(8));
    GridPane g = new GridPane();
    g.setHgap(8); g.setVgap(8);
    g.addRow(0, new Label("Text Speed (ms/char)"), slText);
    g.addRow(1, new Label("BGM Volume"), slBgm);
    g.addRow(2, new Label("SFX Volume"), slSfx);
    g.addRow(3, new Label("Voice Volume"), slVoice);
    g.addRow(4, new Label("Auto-play Delay (ms)"), slAuto);
    g.addRow(5, cbSkipUnread);
    g.addRow(6, cbSkipAfterChoices);

    ToolBar tb = new ToolBar();
    Button bLoad = new Button("Load"); bLoad.setOnAction(e -> load());
    Button bSave = new Button("Save"); bSave.setOnAction(e -> save());
    Button bDefaults = new Button("Defaults"); bDefaults.setOnAction(e -> setFromModel(new VnSettings()));
    tb.getItems().addAll(bLoad, bSave, bDefaults);

    setTop(tb);
    setCenter(g);
  }

  public void setProjectRoot(File dir) {
    this.projectRoot = dir;
    load();
  }

  public void setFromModel(VnSettings s) {
    if (s == null) return;
    slText.setValue(s.getTextSpeed());
    slBgm.setValue(s.getBgmVolume());
    slSfx.setValue(s.getSfxVolume());
    slVoice.setValue(s.getVoiceVolume());
    slAuto.setValue(s.getAutoPlayDelay());
    cbSkipUnread.setSelected(s.isSkipUnreadText());
    cbSkipAfterChoices.setSelected(s.isSkipAfterChoices());
  }

  public VnSettings toModel() {
    VnSettings s = new VnSettings();
    s.setTextSpeed((int) Math.round(slText.getValue()));
    s.setBgmVolume((float) slBgm.getValue());
    s.setSfxVolume((float) slSfx.getValue());
    s.setVoiceVolume((float) slVoice.getValue());
    s.setAutoPlayDelay(Math.round(slAuto.getValue()));
    s.setSkipUnreadText(cbSkipUnread.isSelected());
    s.setSkipAfterChoices(cbSkipAfterChoices.isSelected());
    return s;
  }

  private void save() {
    if (projectRoot == null) return;
    Properties p = new Properties();
    VnSettings s = toModel();
    p.setProperty("textSpeed", Integer.toString(s.getTextSpeed()));
    p.setProperty("bgm", Float.toString(s.getBgmVolume()));
    p.setProperty("sfx", Float.toString(s.getSfxVolume()));
    p.setProperty("voice", Float.toString(s.getVoiceVolume()));
    p.setProperty("autoPlayDelay", Long.toString(s.getAutoPlayDelay()));
    p.setProperty("skipUnread", Boolean.toString(s.isSkipUnreadText()));
    p.setProperty("skipAfterChoices", Boolean.toString(s.isSkipAfterChoices()));
    File f = new File(projectRoot, "vn.settings");
    try (FileOutputStream fos = new FileOutputStream(f)) { p.store(fos, "VN Settings"); } catch (Exception ignored) {}
  }

  private void load() {
    if (projectRoot == null) return;
    File f = new File(projectRoot, "vn.settings");
    if (!f.exists()) return;
    Properties p = new Properties();
    try (FileInputStream fis = new FileInputStream(f)) { p.load(fis); } catch (Exception ignored) {}
    VnSettings s = new VnSettings();
    try { s.setTextSpeed(Integer.parseInt(p.getProperty("textSpeed", "40"))); } catch (Exception ignored) {}
    try { s.setBgmVolume(Float.parseFloat(p.getProperty("bgm", "0.7"))); } catch (Exception ignored) {}
    try { s.setSfxVolume(Float.parseFloat(p.getProperty("sfx", "0.7"))); } catch (Exception ignored) {}
    try { s.setVoiceVolume(Float.parseFloat(p.getProperty("voice", "0.7"))); } catch (Exception ignored) {}
    try { s.setAutoPlayDelay(Long.parseLong(p.getProperty("autoPlayDelay", "1500"))); } catch (Exception ignored) {}
    s.setSkipUnreadText(Boolean.parseBoolean(p.getProperty("skipUnread", "false")));
    s.setSkipAfterChoices(Boolean.parseBoolean(p.getProperty("skipAfterChoices", "false")));
    setFromModel(s);
  }
}
