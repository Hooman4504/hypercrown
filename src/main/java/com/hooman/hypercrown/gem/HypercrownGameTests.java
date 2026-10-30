package com.hooman.hypercrown.gem;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.ability.Ability;
import com.hooman.hypercrown.ability.ActiveAbilities;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.data.CrownWorldData;
import com.hooman.hypercrown.item.CrownOwner;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import com.mojang.authlib.GameProfile;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.UUID;

/** Regression tests for Milestone 1 fixes. Run with {@code gradlew runGameTestServer}. */
@GameTestHolder(HypercrownMod.MODID)
@PrefixGameTestTemplate(false)
public final class HypercrownGameTests {
    private HypercrownGameTests() {}

    /** A mob saved while frozen by Time Quartz must come back with its AI and gravity once reloaded. */
    @GameTest(template = "test_floor")
    public static void timeQuartzFreezeRestoredAfterReload(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, 1, 1, 1);
        GemEffects.markFrozen(zombie);
        zombie.setNoAi(true);
        zombie.setNoGravity(true);

        CompoundTag saved = new CompoundTag();
        zombie.saveWithoutId(saved);
        zombie.discard();
        Zombie reloaded = EntityType.ZOMBIE.create(helper.getLevel());
        helper.assertTrue(reloaded != null, "zombie could not be created");
        reloaded.load(saved);
        helper.assertTrue(reloaded.isNoAi(), "precondition: frozen state was saved");

        GemEffects.onEntityJoin(reloaded);
        helper.assertFalse(reloaded.isNoAi(), "NoAI was not restored");
        helper.assertFalse(reloaded.isNoGravity(), "NoGravity was not restored");
        helper.assertFalse(reloaded.getPersistentData().contains("hypercrown_time_frozen"), "freeze marker left behind");
        helper.succeed();
    }

    /** Both powers on a Hypercrown key slot share a cooldown, so neither can be chained after the other. */
    @GameTest(template = "test_floor")
    public static void slotPartnersShareCooldown(GameTestHelper helper) {
        CrownPlayerData data = new CrownPlayerData();
        ActiveAbilities.startSlotCooldown(data, Ability.BLINK, 100, 200);
        helper.assertTrue(data.remaining(Ability.PHASE_DASH, 100) == 200, "partner was not locked");

        data.startCooldown(Ability.TIME_SNARE, 100, 600);
        ActiveAbilities.startSlotCooldown(data, Ability.ANCHOR, 100, 50);
        helper.assertTrue(data.remaining(Ability.TIME_SNARE, 100) == 600, "longer partner cooldown was shortened");
        helper.assertTrue(data.remaining(Ability.HYPERSTEP, 100) == 0, "unrelated ability was locked");
        helper.succeed();
    }

    /** The forge-level check rejects crowns that don't match the world record and accepts the one that does. */
    @GameTest(template = "test_floor")
    public static void canonicalCrownCheck(GameTestHelper helper) {
        // FakePlayer avoids the login event (and its client sync packet) that a mock connected player fires.
        ServerPlayer player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "crown_test"));
        CrownWorldData world = CrownWorldData.get(helper.getLevel().getServer());
        world.reset();
        try {
            UUID crownId = UUID.randomUUID();
            ItemStack crown = new ItemStack(ModRegistries.HYPERCROWN.get());
            crown.set(ModRegistries.OWNER.get(), new CrownOwner(player.getUUID(), "test"));
            crown.set(ModRegistries.CROWN_ID.get(), crownId);
            player.setItemSlot(EquipmentSlot.HEAD, crown);

            world.recordUncountedForge(player.getUUID(), UUID.randomUUID());
            helper.assertFalse(CrownHelper.isCanonicalHypercrown(player), "mismatched crown ID was accepted");

            world.recordUncountedForge(player.getUUID(), crownId);
            helper.assertTrue(CrownHelper.isCanonicalHypercrown(player), "canonical crown was rejected");
            helper.assertTrue(world.crownsForged() == 0, "uncounted forge used up the world limit");
        } finally {
            world.reset();
        }
        helper.succeed();
    }
}
