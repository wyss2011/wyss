package com.nonid.api;

public record NonPlayerStateSnapshot(
   NonGender gender,
   NonLiquidTankSnapshot liquidTank,
   NonMessSnapshot mess,
   NonDestroyedSkinSnapshot destroyedSkin,
   NonPregnancySnapshot pregnancy,
   NonAccessoryEffectsSnapshot accessoryEffects
) {
}
