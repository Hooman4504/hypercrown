package com.hooman.hypercrown.forge;

import com.hooman.hypercrown.gem.CatastropheGem;
import com.hooman.hypercrown.registry.ModRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One weapon blueprint for each catastrophe gem. */
public enum CrownWeaponKind {
    CELESTIAL_EDGE("celestial_edge", "Celestial Edge", WeaponType.BLADE, "netherite", 3,
            CatastropheGem.CELESTIAL_SAPPHIRE, Items.NETHERITE_SWORD, Items.NETHER_STAR, 19.0f, -2.1f, 30),
    APOCALYPSE_BLADE("apocalypse_blade", "Apocalypse Blade", WeaponType.BLADE, "netherite", 5,
            CatastropheGem.APOCALYPTIC_RUBY, Items.NETHERITE_AXE, Items.TNT, 25.0f, -2.8f, 50),
    VOID_REAPER("void_reaper", "Void Reaper", WeaponType.VOID_EDGE, "netherite", 4,
            CatastropheGem.VOID_EMERALD, Items.NETHERITE_SWORD, Items.ENDER_EYE, 21.0f, -2.0f, 30),
    THUNDER_FANG("thunder_fang", "Thunder Fang", WeaponType.ARCANE, "netherite", 3,
            CatastropheGem.THUNDER_DIAMOND, Items.NETHERITE_SWORD, Items.LIGHTNING_ROD, 20.0f, -2.1f, 30),
    FROSTMAW("frostmaw", "Frostmaw", WeaponType.BLADE, "netherite", 3,
            CatastropheGem.FROST_TOPAZ, Items.NETHERITE_SWORD, Items.BLUE_ICE, 19.0f, -2.0f, 24),
    SOLAR_BRAND("solar_brand", "Solar Brand", WeaponType.ARCANE, "netherite", 4,
            CatastropheGem.SOLAR_OPAL, Items.NETHERITE_SWORD, Items.BLAZE_ROD, 20.0f, -2.1f, 30),
    SHADOW_SCYTHE("shadow_scythe", "Shadow Scythe", WeaponType.VOID_EDGE, "netherite", 4,
            CatastropheGem.SHADOW_AMETHYST, Items.NETHERITE_AXE, Items.ECHO_SHARD, 23.0f, -2.7f, 40),
    TECTONIC_BREAKER("tectonic_breaker", "Tectonic Breaker", WeaponType.BLADE, "netherite", 4,
            CatastropheGem.TECTONIC_JADE, Items.NETHERITE_PICKAXE, Items.OBSIDIAN, 24.0f, -2.7f, 40),
    GALE_DANCER("gale_dancer", "Gale Dancer", WeaponType.BLADE, "netherite", 2,
            CatastropheGem.WIND_SAPPHIRE, Items.NETHERITE_SWORD, Items.PHANTOM_MEMBRANE, 18.0f, -1.6f, 24),
    CHRONO_BLADE("chrono_blade", "Chrono Blade", WeaponType.CHRONIC, "netherite", 5,
            CatastropheGem.TIME_QUARTZ, Items.NETHERITE_SWORD, Items.CLOCK, 23.0f, -2.0f, 40);

    public enum WeaponType {
        BLADE,
        ARCANE,
        CHRONIC,
        VOID_EDGE
    }

    private final String id;
    private final String kindName;
    private final WeaponType weaponType;
    private final String baseMaterial;
    private final int forgingComplexity;
    private final CatastropheGem gem;
    private final Item baseWeapon;
    private final Item catalyst;
    private final float bonusAttackDamage;
    private final float attackSpeed;
    private final int procCooldownTicks;

    CrownWeaponKind(String id, String kindName, WeaponType weaponType, String baseMaterial, int forgingComplexity,
                    CatastropheGem gem, Item baseWeapon, Item catalyst, float bonusAttackDamage, float attackSpeed,
                    int procCooldownTicks) {
        this.id = id;
        this.kindName = kindName;
        this.weaponType = weaponType;
        this.baseMaterial = baseMaterial;
        this.forgingComplexity = forgingComplexity;
        this.gem = gem;
        this.baseWeapon = baseWeapon;
        this.catalyst = catalyst;
        this.bonusAttackDamage = bonusAttackDamage;
        this.attackSpeed = attackSpeed;
        this.procCooldownTicks = procCooldownTicks;
    }

    public String id() { return id; }
    public String kindName() { return kindName; }
    public WeaponType weaponType() { return weaponType; }
    public String baseMaterial() { return baseMaterial; }
    public int forgingComplexity() { return forgingComplexity; }
    public CatastropheGem gem() { return gem; }
    public String gemId() { return "hypercrown:" + gem.id(); }
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
