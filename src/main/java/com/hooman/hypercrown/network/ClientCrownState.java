package com.hooman.hypercrown.network;

import com.hooman.hypercrown.ability.Ability;

import java.util.List;
import java.util.UUID;

/** Plain client-side cache. Has no client-only imports, so it is safe to load on a dedicated server. */
public final class ClientCrownState {
    private static final int[] REMAINING = new int[Ability.values().length];

    private static UUID localPlayer;
    private static boolean canonicalHypercrown;

    public static void update(List<Integer> list, boolean canonical, UUID player) {
        canonicalHypercrown = canonical;
        localPlayer = player;
        for (int i = 0; i < REMAINING.length && i < list.size(); i++) REMAINING[i] = list.get(i);
    }

    /** Smooth local countdown between syncs. */
    public static void tick() {
        for (int i = 0; i < REMAINING.length; i++) if (REMAINING[i] > 0) REMAINING[i]--;
    }

    /**
     * Client-side mirror of the server's canonical-crown check for the local player. Other players
     * return true: the client has no record for them and only uses their state cosmetically.
     */
    public static boolean hypercrownAllowed(UUID player) {
        return !player.equals(localPlayer) || canonicalHypercrown;
    }

    public static int remaining(Ability a) { return REMAINING[a.ordinal()]; }

    public static boolean anyCoolingDown() {
        for (Ability a : Ability.ACTIVES) if (REMAINING[a.ordinal()] > 0) return true;
        return false;
    }
}
