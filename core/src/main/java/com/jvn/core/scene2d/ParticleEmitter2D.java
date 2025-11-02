package com.jvn.core.scene2d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ParticleEmitter2D extends Entity2D {
  public static class Particle {
    double x, y, vx, vy;
    double life, maxLife;
    double size, startSize, endSize;
    double r, g, b, a;
    double rotation, rotationSpeed;
  }

  private final List<Particle> particles = new ArrayList<>();
  private final Random rnd = new Random();
  
  // Emission settings
  private double emissionRate = 10; // particles per second
  private double emissionAccum = 0;
  private int maxParticles = 500;
  private boolean emitting = true;
  
  // Particle settings
  private double minLife = 1.0;
  private double maxLife = 3.0;
  private double minSize = 2.0;
  private double maxSize = 8.0;
  private double endSizeScale = 0.1; // size at end of life relative to start
  
  // Velocity settings
  private double minSpeed = 50;
  private double maxSpeed = 150;
  private double minAngle = 0;
  private double maxAngle = 360;
  private double gravityY = 100;
  
  // Color settings
  private double startR = 1, startG = 0.5, startB = 0.2, startA = 1;
  private double endR = 1, endG = 0.2, endB = 0.1, endA = 0;
  
  // Visual settings
  private boolean useAdditive = true;
  private String texture = null;
  
  public ParticleEmitter2D() {}
  
  public void setEmissionRate(double rate) { this.emissionRate = rate; }
  public double getEmissionRate() { return emissionRate; }
  public void setMaxParticles(int max) { this.maxParticles = max; }
  public void setEmitting(boolean emit) { this.emitting = emit; }
  public void setLifeRange(double min, double max) { this.minLife = min; this.maxLife = max; }
  public double getMinLife() { return minLife; }
  public double getMaxLife() { return maxLife; }
  public void setSizeRange(double min, double max, double endScale) { 
    this.minSize = min; this.maxSize = max; this.endSizeScale = endScale; 
  }
  public double getMinSize() { return minSize; }
  public double getMaxSize() { return maxSize; }
  public double getEndSizeScale() { return endSizeScale; }
  public void setSpeedRange(double min, double max) { this.minSpeed = min; this.maxSpeed = max; }
  public double getMinSpeed() { return minSpeed; }
  public double getMaxSpeed() { return maxSpeed; }
  public void setAngleRange(double min, double max) { this.minAngle = min; this.maxAngle = max; }
  public double getMinAngle() { return minAngle; }
  public double getMaxAngle() { return maxAngle; }
  public void setGravity(double gy) { this.gravityY = gy; }
  public double getGravityY() { return gravityY; }
  public void setStartColor(double r, double g, double b, double a) {
    this.startR = r; this.startG = g; this.startB = b; this.startA = a;
  }
  public double getStartR() { return startR; }
  public double getStartG() { return startG; }
  public double getStartB() { return startB; }
  public double getStartA() { return startA; }
  public void setEndColor(double r, double g, double b, double a) {
    this.endR = r; this.endG = g; this.endB = b; this.endA = a;
  }
  public double getEndR() { return endR; }
  public double getEndG() { return endG; }
  public double getEndB() { return endB; }
  public double getEndA() { return endA; }
  public void setTexture(String path) { this.texture = path; }
  public String getTexture() { return texture; }
  public void setAdditive(boolean add) { this.useAdditive = add; }
  public boolean isAdditive() { return useAdditive; }
  
  public void burst(int count) {
    for (int i = 0; i < count && particles.size() < maxParticles; i++) {
      emit();
    }
  }
  
  private void emit() {
    Particle p = new Particle();
    p.x = 0;
    p.y = 0;
    
    double angle = Math.toRadians(minAngle + rnd.nextDouble() * (maxAngle - minAngle));
    double speed = minSpeed + rnd.nextDouble() * (maxSpeed - minSpeed);
    p.vx = Math.cos(angle) * speed;
    p.vy = Math.sin(angle) * speed;
    
    p.maxLife = minLife + rnd.nextDouble() * (maxLife - minLife);
    p.life = 0;
    
    p.startSize = minSize + rnd.nextDouble() * (maxSize - minSize);
    p.endSize = p.startSize * endSizeScale;
    p.size = p.startSize;
    
    p.r = startR;
    p.g = startG;
    p.b = startB;
    p.a = startA;
    
    p.rotation = rnd.nextDouble() * 360;
    p.rotationSpeed = (rnd.nextDouble() - 0.5) * 360;
    
    particles.add(p);
  }
  
  @Override
  public void update(long deltaMs) {
    double dt = deltaMs / 1000.0;
    
    // Emission
    if (emitting) {
      emissionAccum += emissionRate * dt;
      while (emissionAccum >= 1.0 && particles.size() < maxParticles) {
        emit();
        emissionAccum -= 1.0;
      }
    }
    
    // Update particles
    Iterator<Particle> it = particles.iterator();
    while (it.hasNext()) {
      Particle p = it.next();
      
      p.life += dt;
      if (p.life >= p.maxLife) {
        it.remove();
        continue;
      }
      
      // Physics
      p.x += p.vx * dt;
      p.y += p.vy * dt;
      p.vy += gravityY * dt;
      p.rotation += p.rotationSpeed * dt;
      
      // Interpolation
      double t = p.life / p.maxLife;
      p.size = p.startSize + (p.endSize - p.startSize) * t;
      p.r = startR + (endR - startR) * t;
      p.g = startG + (endG - startG) * t;
      p.b = startB + (endB - startB) * t;
      p.a = startA + (endA - startA) * t;
    }
  }
  
  @Override
  public void render(Blitter2D b) {
    if (particles.isEmpty()) return;
    
    b.push();
    if (useAdditive) {
      // Note: Blitter2D doesn't have blend mode support yet,
      // but we can simulate with alpha
    }
    
    for (Particle p : particles) {
      b.push();
      b.translate(p.x, p.y);
      b.rotateDeg(p.rotation);
      b.setGlobalAlpha(p.a);
      
      if (texture != null) {
        double hs = p.size / 2;
        b.drawImage(texture, -hs, -hs, p.size, p.size);
      } else {
        b.setFill(p.r, p.g, p.b, p.a);
        b.fillCircle(0, 0, p.size / 2);
      }
      
      b.pop();
    }
    
    b.pop();
  }
  
  public int getParticleCount() { return particles.size(); }
  public void clear() { particles.clear(); }
}
