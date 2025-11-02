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
  }
  public static class EntityDecl {
    public String name;
    public final List<ComponentDecl> components = new ArrayList<>();
  }
  public static class ComponentDecl {
    public String type;
    public final Map<String,Object> props = new HashMap<>();
  }
}
