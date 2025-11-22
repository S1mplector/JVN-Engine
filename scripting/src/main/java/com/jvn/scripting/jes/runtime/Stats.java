package com.jvn.scripting.jes.runtime;

public class Stats {
  private double maxHp;
  private double hp;
  private double maxMp;
  private double mp;
  private double atk;
  private double def;
  private double speed;
  private String deathCall;
  private boolean removeOnDeath;

  public double getMaxHp() { return maxHp; }

  public void setMaxHp(double maxHp) {
    this.maxHp = maxHp;
    if (maxHp > 0 && hp > maxHp) {
      hp = maxHp;
    }
  }

  public double getHp() { return hp; }

  public void setHp(double hp) {
    this.hp = hp;
    if (maxHp > 0 && this.hp > maxHp) {
      this.hp = maxHp;
    }
  }

  public double getMaxMp() { return maxMp; }

  public void setMaxMp(double maxMp) {
    this.maxMp = maxMp;
    if (maxMp > 0 && mp > maxMp) {
      mp = maxMp;
    }
  }

  public double getMp() { return mp; }

  public void setMp(double mp) {
    this.mp = mp;
    if (maxMp > 0 && this.mp > maxMp) {
      this.mp = maxMp;
    }
  }

  public double getAtk() { return atk; }

  public void setAtk(double atk) { this.atk = atk; }

  public double getDef() { return def; }

  public void setDef(double def) { this.def = def; }

  public double getSpeed() { return speed; }

  public void setSpeed(double speed) { this.speed = speed; }

  public String getDeathCall() { return deathCall; }

  public void setDeathCall(String deathCall) { this.deathCall = deathCall; }

  public boolean isRemoveOnDeath() { return removeOnDeath; }

  public void setRemoveOnDeath(boolean removeOnDeath) { this.removeOnDeath = removeOnDeath; }

  public boolean isDead() { return maxHp > 0 && hp <= 0; }
}
