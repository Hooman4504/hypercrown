package com.hooman.hypercrown.gem;

import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.event.GemProtectionCheckEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Spawn-protection, claim-hook, and loaded-chunk checks for effects that reach beyond their activation
 * point (line gems, the moving tornado, the Thunder Diamond crown beam). One instance per effect, so the
 * claim event is posted at most once per chunk.
 */
public final class GemProtection {
    private final ServerLevel level;
    private final @Nullable ServerPlayer user;
    private final Map<Long, Boolean> claimedChunks = new HashMap<>();

    public GemProtection(ServerLevel level, @Nullable ServerPlayer user) {
        this.level = level;
        this.user = user;
    }

    /** True if a gem may change blocks at {@code pos}, keeping {@code margin} blocks away from spawn protection. */
    public boolean allows(BlockPos pos, int margin) {
        if (!level.hasChunkAt(pos)) return false; // never load or generate chunks for an effect
        if (!HypercrownConfig.ALLOW_GEMS_IN_SPAWN.get() && inSpawnProtection(level, Vec3.atCenterOf(pos), margin))
            return false;
        if (HypercrownConfig.ALLOW_GEMS_IN_CLAIMED_LAND.get()) return true;
        ChunkPos chunk = new ChunkPos(pos);
        return claimedChunks.computeIfAbsent(chunk.toLong(), key -> {
            GemProtectionCheckEvent event = new GemProtectionCheckEvent(user, level, chunk.getMiddleBlockPosition(pos.getY()), 8);
            NeoForge.EVENT_BUS.post(event);
            return !event.isCanceled();
        });
    }

    /**
     * Walks a line from {@code start} and returns how far it may travel (at most {@code max}) before the first
     * protected or unloaded point, so line effects stop at the boundary instead of passing through it.
     */
    public double allowedLength(Vec3 start, Vec3 axis, double max, int margin) {
        Vec3 unit = axis.normalize();
        for (double t = 0; t <= max; t += 0.5) {
            if (!allows(BlockPos.containing(start.add(unit.scale(t))), margin)) return Math.max(0, t - 0.5);
        }
        return max;
    }

    public static boolean inSpawnProtection(ServerLevel level, Vec3 pos, int radius) {
        if (level != level.getServer().overworld()) return false;
        int configuredRadius = level.getServer().getSpawnProtectionRadius();
        if (configuredRadius <= 0) return false;
        BlockPos spawn = level.getSharedSpawnPos();
        double safeRadius = configuredRadius + radius;
        return Vec3.atCenterOf(spawn).distanceToSqr(pos) <= safeRadius * safeRadius;
    }
}
