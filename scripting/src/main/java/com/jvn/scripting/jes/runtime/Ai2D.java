package com.jvn.scripting.jes.runtime;

public class Ai2D {
  private String type;
  private String target;
  private double aggroRange;
  private double attackRange;
  private double attackIntervalMs;
  private double attackAmount;
  private double moveSpeed;
  private double attackCooldownMs;

  public String getType() { return type; }
  public void setType(String type) { this.type = type; }

  public String getTarget() { return target; }
  public void setTarget(String target) { this.target = target; }

  public double getAggroRange() { return aggroRange; }
  public void setAggroRange(double aggroRange) { this.aggroRange = aggroRange; }

  public double getAttackRange() { return attackRange; }
  public void setAttackRange(double attackRange) { this.attackRange = attackRange; }

  public double getAttackIntervalMs() { return attackIntervalMs; }
  public void setAttackIntervalMs(double attackIntervalMs) { this.attackIntervalMs = attackIntervalMs; }

  public double getAttackAmount() { return attackAmount; }
  public void setAttackAmount(double attackAmount) { this.attackAmount = attackAmount; }

  public double getMoveSpeed() { return moveSpeed; }
  public void setMoveSpeed(double moveSpeed) { this.moveSpeed = moveSpeed; }

  public double getAttackCooldownMs() { return attackCooldownMs; }
  public void setAttackCooldownMs(double attackCooldownMs) { this.attackCooldownMs = attackCooldownMs; }
}
