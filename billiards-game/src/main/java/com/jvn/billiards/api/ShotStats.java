package com.jvn.billiards.api;

import java.util.ArrayList;
import java.util.List;

public class ShotStats {
  private final List<Integer> pocketedBalls = new ArrayList<>();
  private Integer firstContactBallId = null;
  private boolean foulScratch = false;
  private boolean foulNoRailAfterContact = false;
  private boolean foulWrongFirstContact = false;
  private boolean eightBallPocketed = false;

  public void addPocketed(int id) {
    pocketedBalls.add(id);
    if (id == 8) eightBallPocketed = true;
    if (id == 0) foulScratch = true;
  }

  public List<Integer> getPocketedBalls() { return pocketedBalls; }
  public boolean isEightBallPocketed() { return eightBallPocketed; }
  public boolean isFoulScratch() { return foulScratch; }
  public boolean isFoulNoRailAfterContact() { return foulNoRailAfterContact; }
  public boolean isFoulWrongFirstContact() { return foulWrongFirstContact; }

  public void setNoRailFoul() { this.foulNoRailAfterContact = true; }
  public void setWrongFirstContactFoul() { this.foulWrongFirstContact = true; }

  public Integer getFirstContactBallId() { return firstContactBallId; }
  public void setFirstContactBallId(Integer id) { if (firstContactBallId == null) this.firstContactBallId = id; }

  public boolean anyFoul() {
    return foulScratch || foulNoRailAfterContact || foulWrongFirstContact;
  }
}
