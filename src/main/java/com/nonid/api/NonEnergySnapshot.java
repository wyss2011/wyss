package com.nonid.api;

public record NonEnergySnapshot(int energy, int maxEnergy, float passiveGainMultiplier, float passiveGainDrift, boolean initialized) {
   public float fraction() {
      return this.maxEnergy <= 0 ? 0.0F : Math.clamp((float)this.energy / this.maxEnergy, 0.0F, 1.0F);
   }
}
