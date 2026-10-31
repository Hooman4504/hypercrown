package com.hooman.hypercrown.event;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Optional claim-mod hook. Posted at activation and, for effects that travel (line gems, the tornado, the
 * Thunder Diamond beam), once per chunk along the path. {@link #user()} is null when the gem's user is offline.
 * When the allow-claimed-land server option is false,
 * claim integrations can cancel this event if the effect area touches a protected claim.
 */
public final class GemProtectionCheckEvent extends Event implements ICancellableEvent {
    private final ServerPlayer user;
    private final ServerLevel level;
    private final BlockPos center;
    private final int radius;

    public GemProtectionCheckEvent(ServerPlayer user, ServerLevel level, BlockPos center, int radius) {
        this.user = user;
        this.level = level;
        this.center = center.immutable();
        this.radius = radius;
    }

    public @org.jetbrains.annotations.Nullable ServerPlayer user() { return user; }
    public ServerLevel level() { return level; }
    public BlockPos center() { return center; }
    public int radius() { return radius; }
}
