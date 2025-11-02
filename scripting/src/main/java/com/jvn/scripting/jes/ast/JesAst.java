package com.jvn.scripting.jes.ast;

import java.util.*;

public class JesAst {
  public static class Program {
    public final List<SceneDecl> scenes = new ArrayList<>();
  }
  public static class SceneDecl {
    public String name;
    public final Map<String,Object> props = new HashMap<>();
    public final List<EntityDecl> entities = new ArrayList<>();
    public final List<InputBinding> bindings = new ArrayList<>();
    public final List<TimelineAction> timeline = new ArrayList<>();
  }
  public static class EntityDecl {
    public String name;
    public final List<ComponentDecl> components = new ArrayList<>();
  }
  public static class ComponentDecl {
    public String type;
    public final Map<String,Object> props = new HashMap<>();
  }
  public static class InputBinding {
    public String key;
    public String action;
    public final Map<String,Object> props = new HashMap<>();
  }
  public static class TimelineAction {
    public String type; // wait, move, rotate, scale, call
    public String target; // optional entity name
    public final Map<String,Object> props = new HashMap<>();
  }
}
