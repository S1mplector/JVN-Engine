package com.jvn.editor.commands;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SetBooleanPropertyCommand implements Command {
  private final BooleanSupplier getter;
  private final Consumer<Boolean> setter;
  private final boolean newVal;
  private final boolean oldVal;

  public SetBooleanPropertyCommand(BooleanSupplier getter, Consumer<Boolean> setter, boolean newVal) {
    this.getter = getter;
    this.setter = setter;
    this.newVal = newVal;
    this.oldVal = getter.getAsBoolean();
  }

  @Override public void execute() { setter.accept(newVal); }
  @Override public void undo() { setter.accept(oldVal); }
}
