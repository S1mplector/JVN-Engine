package com.jvn.editor.commands;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class SetStringPropertyCommand implements Command {
  private final Supplier<String> getter;
  private final Consumer<String> setter;
  private final String newVal;
  private final String oldVal;

  public SetStringPropertyCommand(Supplier<String> getter, Consumer<String> setter, String newVal) {
    this.getter = getter;
    this.setter = setter;
    this.newVal = newVal == null ? "" : newVal;
    this.oldVal = safe(getter.get());
  }

  @Override public void execute() { setter.accept(newVal); }
  @Override public void undo() { setter.accept(oldVal); }

  private static String safe(String s) { return s == null ? "" : s; }
}
