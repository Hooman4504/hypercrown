package com.hooman.hypercrown.event;

import com.hooman.hypercrown.HypercrownMod;
import com.hooman.hypercrown.ability.CrownHelper;
import com.hooman.hypercrown.config.HypercrownConfig;
import com.hooman.hypercrown.data.CrownPlayerData;
import com.hooman.hypercrown.data.CrownWorldData;
import com.hooman.hypercrown.dev.DeveloperToolService;
import com.hooman.hypercrown.item.CrownOwner;
import com.hooman.hypercrown.network.ModNetwork;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
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
import org.joml.Vector3f;

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
        java.util.UUID crownId = java.util.UUID.randomUUID();
        crown.set(ModRegistries.OWNER.get(), new CrownOwner(sp.getUUID(), sp.getGameProfile().getName()));
        crown.set(ModRegistries.CROWN_ID.get(), crownId);
        sp.setItemInHand(event.getHand(), crown);
        world.recordForge(sp.getUUID(), crownId);

        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
        if (bolt != null) { bolt.moveTo(Vec3.atBottomCenterOf(pos)); bolt.setVisualOnly(true); sl.addFreshEntity(bolt); }
        Vec3 heart = Vec3.atCenterOf(pos).add(0, 0.5, 0);
        sl.sendParticles(ParticleTypes.FLASH, heart.x, heart.y, heart.z, 1, 0, 0, 0, 0);
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, heart.x, heart.y, heart.z, 220, 1, 1.5, 1, 0.42);
        sl.sendParticles(ParticleTypes.REVERSE_PORTAL, heart.x, heart.y, heart.z, 180, 0.9, 1.1, 0.9, 0.12);
        sl.sendParticles(ParticleTypes.END_ROD, heart.x, heart.y, heart.z, 120, 0.75, 1.4, 0.75, 0.1);
        DustParticleOptions gold = new DustParticleOptions(new Vector3f(1.0f, 0.8f, 0.22f), 1.4f);
        DustParticleOptions violet = new DustParticleOptions(new Vector3f(0.64f, 0.25f, 1.0f), 1.2f);
        sl.sendParticles(gold, heart.x, heart.y, heart.z, 150, 0.8, 0.9, 0.8, 0.04);
        sl.sendParticles(violet, heart.x, heart.y, heart.z, 100, 0.7, 1.1, 0.7, 0.03);
        for (int i = 0; i < 48; i++) {
            double angle = i * (Math.PI * 2 / 48);
            double radius = 1.2 + 0.18 * Math.sin(angle * 5);
            double x = heart.x + Math.cos(angle) * radius;
            double z = heart.z + Math.sin(angle) * radius;
            sl.sendParticles(i % 2 == 0 ? gold : violet, x, pos.getY() + 0.15, z, 1, 0, 0.025, 0, 0);
        }
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
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        CrownPlayerData d = CrownHelper.data(sp);
        event.getDrops().removeIf(ie -> {
            if (!ie.getItem().is(ModRegistries.HYPERCROWN.get())) return false;
            if (HypercrownConfig.KEEP_ON_DEATH.get()) {
                d.pendingReturn.add(ie.getItem().copy());
                return true;
            }
            if (HypercrownConfig.ALLOW_TRANSFER.get() && sp.getKillCredit() instanceof ServerPlayer killer) {
                ie.getItem().set(ModRegistries.OWNER.get(),
                        new CrownOwner(killer.getUUID(), killer.getGameProfile().getName()));
                CrownWorldData.get(sp.getServer()).transferOwnership(killer.getUUID());
            }
            return false;
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
        if (event.getEntity() instanceof ServerPlayer sp) {
            ModNetwork.syncTo(sp);
            DeveloperToolService.syncClient(sp);
        }
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
                }))
                .then(Commands.literal("dev").executes(ctx -> {
                    DeveloperToolService.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }).then(Commands.literal("reload").executes(ctx -> {
                    DeveloperToolService.handle(ctx.getSource().getPlayerOrException(), "reload", "");
                    return 1;
                }))));
        event.getDispatcher().register(Commands.literal("hc").requires(s -> s.hasPermission(2))
                .then(Commands.literal("dev").executes(ctx -> {
                    DeveloperToolService.open(ctx.getSource().getPlayerOrException());
                    return 1;
                }).then(Commands.literal("reload").executes(ctx -> {
                    DeveloperToolService.handle(ctx.getSource().getPlayerOrException(), "reload", "");
                    return 1;
                }))));
    }
}
