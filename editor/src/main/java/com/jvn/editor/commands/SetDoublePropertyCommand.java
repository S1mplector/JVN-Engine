package com.jvn.editor.commands;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;

public class SetDoublePropertyCommand implements Command {
  private final DoubleSupplier getter;
  private final DoubleConsumer setter;
  private final double newVal;
  private final double oldVal;

  public SetDoublePropertyCommand(DoubleSupplier getter, DoubleConsumer setter, double newVal) {
    this.getter = getter;
    this.setter = setter;
    this.newVal = newVal;
    this.oldVal = getter.getAsDouble();
  }

  @Override public void execute() { setter.accept(newVal); }
  @Override public void undo() { setter.accept(oldVal); }
}
