package com.jvn.editor.commands;

public class FunctionalCommand implements Command {
  private final Runnable apply;
  private final Runnable undo;

  public FunctionalCommand(Runnable apply, Runnable undo) {
    this.apply = apply;
    this.undo = undo;
  }

  @Override public void execute() { if (apply != null) apply.run(); }
  @Override public void undo() { if (undo != null) undo.run(); }
}
