package com.jvn.scripting.jes;

import com.jvn.core.scene2d.*;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.scripting.jes.runtime.PhysicsBodyEntity2D;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

public class JesExporter {
  
  public static String export(JesScene2D scene, String sceneName) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    
    pw.println("scene \"" + sceneName + "\" {");
    
    // Export entities
    int entityNum = 0;
    for (Map.Entry<String, Entity2D> entry : findAllNamedEntities(scene).entrySet()) {
      String name = entry.getKey();
      Entity2D entity = entry.getValue();
      
      if (name == null || name.isBlank()) {
        name = "entity_" + entityNum++;
      }
      
      pw.println("  entity \"" + name + "\" {");
      
      if (entity instanceof Panel2D p) {
        pw.println("    component Panel2D {");
        pw.println("      x: " + p.getX());
        pw.println("      y: " + p.getY());
        pw.println("      w: " + p.getWidth());
        pw.println("      h: " + p.getHeight());
        // Note: Panel2D doesn't expose color getters, would need to add them
        pw.println("    }");
      } else if (entity instanceof Label2D l) {
        pw.println("    component Label2D {");
        pw.println("      text: \"" + escapeString(l.getText()) + "\"");
        pw.println("      x: " + l.getX());
        pw.println("      y: " + l.getY());
        pw.println("      size: " + l.getSize());
        pw.println("      bold: " + l.isBold());
        pw.println("      color: rgb(" + l.getColorR() + "," + l.getColorG() + "," + l.getColorB() + "," + l.getAlpha() + ")");
        pw.println("      align: " + l.getAlign().name().toLowerCase());
        pw.println("    }");
      } else if (entity instanceof Sprite2D s) {
        pw.println("    component Sprite2D {");
        if (s.getImagePath() != null) {
          pw.println("      image: \"" + escapeString(s.getImagePath()) + "\"");
        }
        pw.println("      x: " + s.getX());
        pw.println("      y: " + s.getY());
        pw.println("      w: " + s.getWidth());
        pw.println("      h: " + s.getHeight());
        pw.println("      alpha: " + s.getAlpha());
        pw.println("      originX: " + s.getOriginX());
        pw.println("      originY: " + s.getOriginY());
        pw.println("    }");
      } else if (entity instanceof PhysicsBodyEntity2D pb) {
        RigidBody2D body = pb.getBody();
        if (body != null) {
          pw.println("    component PhysicsBody2D {");
          pw.println("      shape: " + (body.getShapeType() == RigidBody2D.ShapeType.CIRCLE ? "circle" : "box"));
          pw.println("      x: " + body.getX());
          pw.println("      y: " + body.getY());
          
          if (body.getShapeType() == RigidBody2D.ShapeType.CIRCLE) {
            pw.println("      r: " + body.getCircle().r);
          } else {
            pw.println("      w: " + body.getAabb().w);
            pw.println("      h: " + body.getAabb().h);
          }
          
          pw.println("      mass: " + body.getMass());
          pw.println("      restitution: " + body.getRestitution());
          pw.println("      static: " + body.isStatic());
          pw.println("      sensor: " + body.isSensor());
          
          if (body.getVx() != 0 || body.getVy() != 0) {
            pw.println("      vx: " + body.getVx());
            pw.println("      vy: " + body.getVy());
          }
          
          // Note: PhysicsBodyEntity2D doesn't expose color getters
          pw.println("    }");
        }
      } else if (entity instanceof ParticleEmitter2D pe) {
        pw.println("    component ParticleEmitter2D {");
        pw.println("      x: " + pe.getX());
        pw.println("      y: " + pe.getY());
        // Note: ParticleEmitter2D doesn't expose getters for all properties
        // Would need to add them for complete export
        pw.println("    }");
      }
      
      pw.println("  }");
    }
    
    // Export input bindings (not accessible from JesScene2D currently)
    // Would need to expose bindings list
    
    // Export timeline (not accessible from JesScene2D currently)  
    // Would need to expose timeline list
    
    pw.println("}");
    pw.flush();
    return sw.toString();
  }
  
  private static Map<String, Entity2D> findAllNamedEntities(JesScene2D scene) {
    // This would need access to the named entities map in JesScene2D
    // For now, return empty map
    return scene == null ? new java.util.HashMap<>() : scene.exportNamed();
  }
  
  private static String escapeString(String s) {
    if (s == null) return "";
    return s.replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
  }
}
