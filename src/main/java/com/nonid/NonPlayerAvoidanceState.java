package com.nonid;

import net.minecraft.server.network.ServerPlayerEntity;

public interface NonPlayerAvoidanceState {
   boolean shouldSuppressPlayerAvoidance(ServerPlayerEntity var1);
}
