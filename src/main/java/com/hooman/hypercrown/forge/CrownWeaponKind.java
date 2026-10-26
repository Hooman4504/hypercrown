package com.hooman.hypercrown.forge;

import com.hooman.hypercrown.gem.CatastropheGem;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One weapon blueprint for each catastrophe gem. */
public enum CrownWeaponKind {
    CELESTIAL_EDGE("celestial_edge", CatastropheGem.CELESTIAL_SAPPHIRE, Items.NETHERITE_SWORD, Items.NETHER_STAR, 19.0f, -2.1f, 30),
    APOCALYPSE_BLADE("apocalypse_blade", CatastropheGem.APOCALYPTIC_RUBY, Items.NETHERITE_AXE, Items.TNT, 25.0f, -2.8f, 50),
    VOID_REAPER("void_reaper", CatastropheGem.VOID_EMERALD, Items.NETHERITE_SWORD, Items.ENDER_EYE, 21.0f, -2.0f, 30),
    THUNDER_FANG("thunder_fang", CatastropheGem.THUNDER_DIAMOND, Items.NETHERITE_SWORD, Items.LIGHTNING_ROD, 20.0f, -2.1f, 30),
    FROSTMAW("frostmaw", CatastropheGem.FROST_TOPAZ, Items.NETHERITE_SWORD, Items.BLUE_ICE, 19.0f, -2.0f, 24),
    SOLAR_BRAND("solar_brand", CatastropheGem.SOLAR_OPAL, Items.NETHERITE_SWORD, Items.BLAZE_ROD, 20.0f, -2.1f, 30),
    SHADOW_SCYTHE("shadow_scythe", CatastropheGem.SHADOW_AMETHYST, Items.NETHERITE_AXE, Items.ECHO_SHARD, 23.0f, -2.7f, 40),
    TECTONIC_BREAKER("tectonic_breaker", CatastropheGem.TECTONIC_JADE, Items.NETHERITE_PICKAXE, Items.OBSIDIAN, 24.0f, -2.7f, 40),
    GALE_DANCER("gale_dancer", CatastropheGem.WIND_SAPPHIRE, Items.NETHERITE_SWORD, Items.PHANTOM_MEMBRANE, 18.0f, -1.6f, 24),
    CHRONO_BLADE("chrono_blade", CatastropheGem.TIME_QUARTZ, Items.NETHERITE_SWORD, Items.CLOCK, 23.0f, -2.0f, 40);

    private final String id;
    private final CatastropheGem gem;
    private final Item baseWeapon;
    private final Item catalyst;
    private final float bonusAttackDamage;
    private final float attackSpeed;
    private final int procCooldownTicks;

    CrownWeaponKind(String id, CatastropheGem gem, Item baseWeapon, Item catalyst,
                    float bonusAttackDamage, float attackSpeed, int procCooldownTicks) {
        this.id = id;
        this.gem = gem;
        this.baseWeapon = baseWeapon;
        this.catalyst = catalyst;
        this.bonusAttackDamage = bonusAttackDamage;
        this.attackSpeed = attackSpeed;
        this.procCooldownTicks = procCooldownTicks;
    }

    public String id() { return id; }
    public CatastropheGem gem() { return gem; }
    public Item baseWeapon() { return baseWeapon; }
    public Item catalyst() { return catalyst; }
    public float bonusAttackDamage() { return bonusAttackDamage; }
    public float attackSpeed() { return attackSpeed; }
    public int procCooldownTicks() { return procCooldownTicks; }

    public ItemStack gemStack() {
        return new ItemStack(ModRegistries.CATASTROPHE_GEMS.get(gem).get());
    }

    public ItemStack outputStack() {
        return new ItemStack(ModRegistries.CROWN_FORGED_WEAPONS.get(this).get());
    }
}
