package com.hooman.hypercrown.event;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.data.CrownWorldData;
import com.hooman.hypercrown.item.CrownOwner;
import com.hooman.hypercrown.network.ModNetwork;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Boss tracking, the one-time awakening ritual, death handling, admin command. */
@EventBusSubscriber(modid = HypercrownMod.MODID)
public final class CrownLifecycleEvents {

    /** Credits every player within 128 blocks when a required boss dies (fair in multiplayer). */
    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) return;
        EntityType<?> t = dead.getType();
        if (t != EntityType.ENDER_DRAGON && t != EntityType.WITHER && t != EntityType.ELDER_GUARDIAN) return;
        for (ServerPlayer sp : level.players()) {
            if (sp.distanceToSqr(dead) > 128 * 128) continue;
            CrownPlayerData d = CrownHelper.data(sp);
            if (t == EntityType.ENDER_DRAGON) d.killedDragon = true;
            else if (t == EntityType.WITHER) d.killedWither = true;
            else d.killedElder = true;
            sp.displayClientMessage(Component.translatable("message.hypercrown.boss_credit", dead.getDisplayName())
                    .withStyle(ChatFormatting.LIGHT_PURPLE), false);
        }
    }

    /** Awakening ritual: right-click the Dragon Egg in the End with the Unawakened Crown. */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getItemStack().is(ModRegistries.UNAWAKENED_CROWN.get())) return;
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!level.getBlockState(pos).is(Blocks.DRAGON_EGG)) return;
        event.setCanceled(true); // stop the egg teleporting
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(level instanceof ServerLevel sl) || !(event.getEntity() instanceof ServerPlayer sp)) return;

        if (!HypercrownConfig.CROWN_ENABLED.get()) { fail(sp, Component.translatable("message.hypercrown.ritual.disabled")); return; }
        if (level.dimension() != Level.END) { fail(sp, Component.translatable("message.hypercrown.ritual.wrong_dimension")); return; }
        CrownPlayerData d = CrownHelper.data(sp);
        if (!(d.killedDragon && d.killedWither && d.killedElder)) {
            fail(sp, Component.translatable("message.hypercrown.ritual.unworthy",
                    status(d.killedDragon, EntityType.ENDER_DRAGON), status(d.killedWither, EntityType.WITHER),
                    status(d.killedElder, EntityType.ELDER_GUARDIAN)));
            return;
        }
        int xp = HypercrownConfig.RITUAL_XP_LEVELS.get();
        if (!sp.isCreative() && sp.experienceLevel < xp) { fail(sp, Component.translatable("message.hypercrown.ritual.xp", xp)); return; }
        CrownWorldData world = CrownWorldData.get(sp.getServer());
        if (world.crownsForged() >= HypercrownConfig.MAX_CROWNS.get()) {
            fail(sp, Component.translatable("message.hypercrown.ritual.already_forged"));
            return;
        }

        // --- The sacrifice ---
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);   // Dragon Egg consumed
        if (!sp.isCreative()) {
            sp.giveExperienceLevels(-xp);
            sp.setHealth(1.0f);
            sp.getFoodData().setFoodLevel(1);
            sp.getFoodData().setSaturation(0f);
        }
        ItemStack crown = new ItemStack(ModRegistries.HYPERCROWN.get());
        crown.set(ModRegistries.OWNER.get(), new CrownOwner(sp.getUUID(), sp.getGameProfile().getName()));
        sp.setItemInHand(event.getHand(), crown);
        world.recordForge(sp.getUUID());

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) { bolt.moveTo(Vec3.atBottomCenterOf(pos)); bolt.setVisualOnly(true); sl.addFreshEntity(bolt); }
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 150, 1, 1.5, 1, 0.5);
        sl.playSound(null, pos, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 2f, 1f);
        sp.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.hypercrown.forged", sp.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
    }

    private static Component status(boolean done, EntityType<?> type) {
        return type.getDescription().copy().withStyle(done ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private static void fail(ServerPlayer sp, Component msg) {
        sp.displayClientMessage(msg.copy().withStyle(ChatFormatting.RED), false);
    }

    /** keepOnDeath: pull the crown out of the death drops and return it on respawn. */
    @SubscribeEvent
    public static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !HypercrownConfig.KEEP_ON_DEATH.get()) return;
        CrownPlayerData d = CrownHelper.data(sp);
        event.getDrops().removeIf(ie -> {
            if (!ie.getItem().is(ModRegistries.HYPERCROWN.get())) return false;
            d.pendingReturn.add(ie.getItem().copy());
            return true;
        });
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        CrownPlayerData d = CrownHelper.data(sp);
        for (ItemStack s : d.pendingReturn) if (!sp.getInventory().add(s)) sp.drop(s, false);
        d.pendingReturn.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) ModNetwork.syncTo(sp);
    }

    /** /hypercrown status | reset (op level 2) */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("hypercrown").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(ctx -> {
                    CrownWorldData wd = CrownWorldData.get(ctx.getSource().getServer());
                    ctx.getSource().sendSuccess(() -> Component.literal("Hypercrowns forged: " + wd.crownsForged()
                            + "/" + HypercrownConfig.MAX_CROWNS.get()), false);
                    return wd.crownsForged();
                }))
                .then(Commands.literal("reset").executes(ctx -> {
                    CrownWorldData.get(ctx.getSource().getServer()).reset();
                    ctx.getSource().sendSuccess(() -> Component.literal("Hypercrown forge counter reset."), true);
                    return 1;
                })));
    }
}
