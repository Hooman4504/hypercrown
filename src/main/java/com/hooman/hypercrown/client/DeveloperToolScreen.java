package com.hooman.hypercrown.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hooman.hypercrown.dev.DeveloperWorkspace;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Dark, tabbed in-game authoring interface for the server workspace. */
public final class DeveloperToolScreen extends Screen {
    private enum Tab { ABILITIES, PARTICLES, EFFECTS, SOUNDS, CONFIG, DEBUG, LOGS }
    private record Field(EditBox box, String label, String help, Consumer<String> commit) {}
    private record ViewState(Tab tab, String selectedAbility, String selectedParticle, String selectedEffect, String selectedLayer, String selectedSound, String selectedConfig,
                            String configCategory, String configSearch, int abilityOffset, int particleOffset, int soundOffset, int visualOffset, int visualLayerOffset, int effectScroll,
                            int configOffset, int logOffset, int abilityPage, int particlePage, int effectPage, int soundPage) {}
    private static ViewState lastViewState = new ViewState(Tab.ABILITIES, "", "", "", "", "", "", "all", "", 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

    private DeveloperWorkspace.Snapshot snapshot;
    private DeveloperWorkspace workspace;
    private Tab tab = Tab.ABILITIES;
    private String selectedAbility = "";
    private String selectedParticle = "";
    private String selectedEffect = "";
    private String selectedLayer = "";
    private String selectedSound = "";
    private String selectedConfig = "";
    private String configCategory = "all";
    private final List<String> categories = List.of("all", "general", "abilities", "cooldowns", "tuning", "gems", "gemWorld");
    private String configSearch = "";
    private int abilityOffset, particleOffset, soundOffset, visualOffset, visualLayerOffset, effectScroll, configOffset, logOffset;
    private int abilityPage, particlePage, effectPage, soundPage;
    private boolean localDirty;
    private boolean pendingSave;
    private String pendingHistoryAction = "";
    private boolean pendingConfig;
    private boolean pendingDelete;
    private boolean pendingResetAll;
    private int ticksSinceEdit;
    private int panelX, panelY, panelWidth, panelHeight, sidebarWidth, formX, formWidth;
    private final List<Field> fields = new ArrayList<>();
    private EditBox configSearchBox;
    private EditBox configValueBox;
    private final List<GraphNode> graphNodes = new ArrayList<>();
    private record GraphNode(int x, int y, int width, int height, String layerId, String name, String source, String output, boolean selected) {}

    public DeveloperToolScreen(DeveloperWorkspace.Snapshot snapshot) {
        super(Component.literal("Hypercrown Developer Workshop"));
        this.snapshot = snapshot;
        this.workspace = parseWorkspace(snapshot == null ? null : snapshot.workspaceJson);
        ViewState saved = lastViewState;
        tab = saved.tab;
        selectedAbility = existingId(workspace.abilities.stream().map(a -> a.id).toList(), saved.selectedAbility);
        selectedParticle = existingId(workspace.particles.stream().map(p -> p.id).toList(), saved.selectedParticle);
        selectedEffect = existingId(workspace.visualEffects.stream().map(p -> p.id).toList(), saved.selectedEffect);
        selectedLayer = saved.selectedLayer;
        selectedSound = existingId(workspace.sounds.stream().map(s -> s.id).toList(), saved.selectedSound);
        selectedConfig = saved.selectedConfig;
        configCategory = categories.contains(saved.configCategory) ? saved.configCategory : "all";
        configSearch = saved.configSearch;
        abilityOffset = saved.abilityOffset;
        particleOffset = saved.particleOffset;
        soundOffset = saved.soundOffset;
        visualOffset = saved.visualOffset;
        visualLayerOffset = saved.visualLayerOffset;
        effectScroll = saved.effectScroll;
        configOffset = saved.configOffset;
        logOffset = saved.logOffset;
        abilityPage = saved.abilityPage;
        particlePage = saved.particlePage;
        effectPage = saved.effectPage;
        soundPage = saved.soundPage;
    }

    private static String existingId(List<String> ids, String selected) {
        return ids.contains(selected) ? selected : ids.isEmpty() ? "" : ids.get(0);
    }

    private static DeveloperWorkspace parseWorkspace(String json) {
        try { return DeveloperWorkspace.parse(json); }
        catch (RuntimeException exception) { return DeveloperWorkspace.defaults(); }
    }

    @Override
    protected void init() {
        clearWidgets();
        fields.clear();
        paragraphSink.clear();
        graphNodes.clear();
        panelWidth = Math.min(920, Math.max(300, width - 16));
        panelHeight = Math.min(430, Math.max(260, height - 16));
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        effectScroll = Math.max(0, Math.min(effectScroll, effectScrollMax()));
        sidebarWidth = panelWidth < 600 ? 112 : 166;
        formX = panelX + sidebarWidth + 14;
        formWidth = panelX + panelWidth - formX - 12;

        int tabY = panelY + 28;
        String[] names = {"Abilities", "Particles", "Effects", "Sounds", "Config", "Debug", "Logs"};
        Tab[] tabs = Tab.values();
        int tabWidth = (panelWidth - 16) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            final Tab selected = tabs[i];
            addRenderableWidget(Button.builder(Component.literal(names[i]), button -> switchTab(selected))
                    .bounds(panelX + 8 + i * tabWidth, tabY, tabWidth - 3, 20).build());
        }

        switch (tab) {
            case ABILITIES -> initAbilities();
            case PARTICLES -> initParticles();
            case EFFECTS -> initEffects();
            case SOUNDS -> initSounds();
            case CONFIG -> initConfig();
            case DEBUG -> initDebug();
            case LOGS -> initLogs();
        }
    }

    private void switchTab(Tab selected) {
        commitFields();
        tab = selected;
        init();
    }

    private void initAbilities() {
        addSelectableList("ability", workspace.abilities.stream().map(a -> (a.core ? "◆ " : "+ ") + a.name).toList(),
                workspace.abilities.stream().map(a -> a.id).toList(), abilityOffset,
                id -> { commitFields(); selectedAbility = id; abilityPage = 0; init(); });
        DeveloperWorkspace.AbilityDraft a = currentAbility();
        if (a != null) {
            if (abilityPage == 0) {
                int y = panelY + 72;
                int gap = 8;
                int half = (formWidth - gap) / 2;
                addField("Display name", a.name, formX, y, half, s -> a.name = s, "Shown in this developer workspace. Existing abilities still use their registered translation key.");
                addField("ID", a.id, formX + half + gap, y, half, s -> setAbilityId(a, s), a.core ? "Registered IDs cannot be renamed." : "Stable lowercase id used by exports and ability chains.", a.core);
                y += 42;
                addField("Description", a.description, formX, y, formWidth, s -> a.description = s, "Authoring notes; core gameplay descriptions remain code and translation driven.");
                y += 42;
                int quarter = (formWidth - gap * 3) / 4;
                addField("Enabled", Boolean.toString(a.enabled), formX, y, quarter, s -> a.enabled = Boolean.parseBoolean(s), "For core abilities this updates the live Hypercrown server setting.");
                addField("Cooldown (s)", number(a.cooldownSeconds), formX + quarter + gap, y, quarter, s -> a.cooldownSeconds = decimal(s, a.cooldownSeconds), "For core abilities this updates the server setting immediately. Four core abilities retain their code-enforced minimum cooldown.");
                addField("Mana cost", number(a.manaCost), formX + (quarter + gap) * 2, y, quarter, s -> a.manaCost = decimal(s, a.manaCost), "Stored design metadata. Hypercrown has no mana resource system.");
                addField("Damage", number(a.damage), formX + (quarter + gap) * 3, y, quarter, s -> a.damage = decimal(s, a.damage), a.core ? "Metadata only for registered abilities. Their effects are implemented in code." : "Damage or healing amount for this custom test ability.");
                y += 42;
                addField("Target range", number(a.range), formX, y, half, s -> a.range = decimal(s, a.range), "Custom test target search range in blocks. Range for core abilities remains code controlled.");
                addField("Duration (ticks)", Integer.toString(a.durationTicks), formX + half + gap, y, half, s -> a.durationTicks = integer(s, a.durationTicks), "Custom effect duration; 20 ticks equal one second.");
            } else if (abilityPage == 1) {
                int y = panelY + 72;
                int gap = 8, half = (formWidth - gap) / 2;
                addField("Action", a.action, formX, y, half, s -> a.action = s.toLowerCase(Locale.ROOT), "Custom actions: damage, effect, launch, heal, or visual. Core behavior is unchanged.");
                addField("Target", a.target, formX + half + gap, y, half, s -> a.target = s.toLowerCase(Locale.ROOT), "Custom targets: self, nearest_enemy, or all_enemies.");
                y += 42;
                addField("Trigger", a.trigger, formX, y, half, s -> a.trigger = s, "Core triggers are fixed by the registered ability. Custom abilities are currently test-triggered from this screen.");
                addField("Condition", a.condition, formX + half + gap, y, half, s -> a.condition = s, "Authoring metadata. The custom test executor does not evaluate arbitrary condition text.");
                y += 42;
                addField("Effect ID", a.effect, formX, y, half, s -> a.effect = s, "Registry id for a Minecraft mob effect, used by custom action 'effect'.");
                addField("Chain to custom ability", a.chain, formX + half + gap, y, half, s -> a.chain = s, "Optional custom ability id. Test chains are limited to four steps.");
                addParagraph("Custom actions run on the server with registered Minecraft effects. Mana cost and arbitrary trigger/condition expressions are saved as design metadata.", panelY + panelHeight - 86, 0xFFB7C2D8);
            } else {
                int y = panelY + 72;
                int gap = 8, half = (formWidth - gap) / 2;
                addField("Particle id", a.particle, formX, y, Math.max(60, half - 86), s -> a.particle = s, "Simple Minecraft or mod particle id, or minecraft:dust.");
                addRenderableWidget(Button.builder(Component.literal("Next"), button -> { commitFields(); a.particle = nextRegisteredId(BuiltInRegistries.PARTICLE_TYPE.keySet().stream().map(Object::toString).sorted().toList(), a.particle); localDirty = true; init(); })
                        .bounds(formX + half - 82, y + 11, 82, 20).build());
                addField("Sound id", a.sound, formX + half + gap, y, Math.max(60, half - 86), s -> a.sound = s, "Sound event id from Minecraft, Hypercrown, or another loaded mod/resource pack.");
                addRenderableWidget(Button.builder(Component.literal("Next"), button -> { commitFields(); a.sound = nextRegisteredId(BuiltInRegistries.SOUND_EVENT.keySet().stream().map(Object::toString).sorted().toList(), a.sound); localDirty = true; init(); })
                        .bounds(formX + half + gap + half - 82, y + 11, 82, 20).build());
                addParagraph("A custom ability test emits the selected visual feedback at the player. Core audiovisual behavior stays in its registered implementation.", y + 58, 0xFFB7C2D8);
            }
            addPageButtons(abilityPage, 3, page -> { commitFields(); abilityPage = page; init(); });
        }
        addWorkspaceButtons("Test", () -> testAbility());
    }

    private void initParticles() {
        addSelectableList("particle", workspace.particles.stream().map(p -> p.name).toList(),
                workspace.particles.stream().map(p -> p.id).toList(), particleOffset,
                id -> { commitFields(); selectedParticle = id; particlePage = 0; init(); });
        DeveloperWorkspace.ParticlePreset p = currentParticle();
        if (p != null) {
            int y = panelY + 72, gap = 8, half = (formWidth - gap) / 2;
            if (particlePage == 0) {
                addField("Preset name", p.name, formX, y, half, s -> p.name = s, "Human-readable name for this reusable preset.");
                addField("Preset ID", p.id, formX + half + gap, y, half, s -> setParticleId(p, s), "Stable lowercase id used by workspace exports.");
                y += 42;
                addField("Particle type id", p.particle, formX, y, Math.max(60, formWidth - 88), s -> p.particle = s, "Built-in or mod particle registry id. Preview supports simple particle types and minecraft:dust.");
                addRenderableWidget(Button.builder(Component.literal("Next type"), button -> {
                    commitFields(); p.particle = nextRegisteredId(BuiltInRegistries.PARTICLE_TYPE.keySet().stream().map(Object::toString).sorted().toList(), p.particle);
                    localDirty = true; init();
                }).bounds(formX + formWidth - 82, y + 11, 82, 20).build());
                y += 42;
                int third = (formWidth - gap * 2) / 3;
                addField("Position", p.position, formX, y, third, s -> p.position = s.toLowerCase(Locale.ROOT), "player, target, or world.");
                addField("Offset X", number(p.x), formX + third + gap, y, third, s -> p.x = decimal(s, p.x), "Horizontal offset or world coordinate.");
                addField("Offset Y", number(p.y), formX + (third + gap) * 2, y, third, s -> p.y = decimal(s, p.y), "Vertical offset or world coordinate.");
                y += 42;
                addField("Offset Z", number(p.z), formX, y, third, s -> p.z = decimal(s, p.z), "Depth offset or world coordinate.");
                addField("Delay ticks", Integer.toString(p.delayTicks), formX + third + gap, y, third, s -> p.delayTicks = integer(s, p.delayTicks), "Preview start delay. 20 ticks equal one second.");
                addField("Duration ticks", Integer.toString(p.durationTicks), formX + (third + gap) * 2, y, third, s -> p.durationTicks = integer(s, p.durationTicks), "Preview repeats within this duration, up to the 120-particle cap.");
                y += 42;
                addField("Interval ticks", Integer.toString(p.intervalTicks), formX, y, third, s -> p.intervalTicks = integer(s, p.intervalTicks), "Ticks between preview bursts.");
                addField("Count per burst", Integer.toString(p.count), formX + third + gap, y, third, s -> p.count = integer(s, p.count), "Particles per burst, capped at 24 per server tick and 120 total per preview.");
                addField("Condition", p.condition, formX + (third + gap) * 2, y, third, s -> p.condition = s, "Preview conditions: always, player_sprinting, player_sneaking, target_exists, target_distance:<blocks>.");
            } else if (particlePage == 1) {
                int third = (formWidth - gap * 2) / 3;
                addField("Velocity X", number(p.vx), formX, y, third, s -> p.vx = decimal(s, p.vx), "Initial X motion.");
                addField("Velocity Y", number(p.vy), formX + third + gap, y, third, s -> p.vy = decimal(s, p.vy), "Initial Y motion.");
                addField("Velocity Z", number(p.vz), formX + (third + gap) * 2, y, third, s -> p.vz = decimal(s, p.vz), "Initial Z motion.");
                y += 42;
                addField("Speed", number(p.speed), formX, y, third, s -> p.speed = decimal(s, p.speed), "Minecraft particle speed scale.");
                addField("Acceleration X", number(p.accelerationX), formX + third + gap, y, third, s -> p.accelerationX = decimal(s, p.accelerationX), "Velocity change over the preview duration.");
                addField("Acceleration Y", number(p.accelerationY), formX + (third + gap) * 2, y, third, s -> p.accelerationY = decimal(s, p.accelerationY), "Velocity change over the preview duration.");
                y += 42;
                addField("Acceleration Z", number(p.accelerationZ), formX, y, third, s -> p.accelerationZ = decimal(s, p.accelerationZ), "Velocity change over the preview duration.");
                addField("Randomization", number(p.randomness), formX + third + gap, y, third, s -> p.randomness = decimal(s, p.randomness), "Adds bounded scatter to position and velocity.");
                addParagraph("Motion is evaluated on the server during the preview. Stored acceleration and randomization are bounded before use.", y + 48, 0xFFB7C2D8);
            } else {
                int third = (formWidth - gap * 2) / 3;
                addField("Shape", p.shape, formX, y, third, s -> p.shape = s.toLowerCase(Locale.ROOT), "sphere, cylinder, cone, line, ring, or path.");
                addField("Radius", number(p.radius), formX + third + gap, y, third, s -> p.radius = decimal(s, p.radius), "Pattern radius in blocks.");
                addField("Height", number(p.height), formX + (third + gap) * 2, y, third, s -> p.height = decimal(s, p.height), "Cylinder or cone height in blocks.");
                y += 42;
                addField("Dust size", number(p.size), formX, y, third, s -> p.size = decimal(s, p.size), "Used by minecraft:dust.");
                addField("Red 0–1", number(p.red), formX + third + gap, y, third, s -> p.red = decimal(s, p.red), "Dust red channel.");
                addField("Green 0–1", number(p.green), formX + (third + gap) * 2, y, third, s -> p.green = decimal(s, p.green), "Dust green channel.");
                y += 42;
                addField("Blue 0–1", number(p.blue), formX, y, third, s -> p.blue = decimal(s, p.blue), "Dust blue channel.");
                addField("Alpha 0–1", number(p.alpha), formX + third + gap, y, third, s -> p.alpha = decimal(s, p.alpha), "Stored in the preset; vanilla particles do not expose per-particle alpha.");
                addField("Color animation", p.colorAnimation, formX + (third + gap) * 2, y, third, s -> p.colorAnimation = s.toLowerCase(Locale.ROOT), "none, fade, pulse, rainbow. Stored for format compatibility; vanilla dust preview uses a static color.");
                y += 42;
                addField("Custom path points", p.path, formX, y, formWidth, s -> p.path = s, "Semicolon-separated x,y,z points, for example 0,0,0;1,1,0;0,2,0.");
            }
            addPageButtons(particlePage, 3, page -> { commitFields(); particlePage = page; init(); });
        }
        addWorkspaceButtons("View preview", this::previewParticleInWorld);
    }

    private void initEffectsLegacy() {
        addSelectableList("visual", workspace.visualEffects.stream().map(effect -> effect.name).toList(),
                workspace.visualEffects.stream().map(effect -> effect.id).toList(), visualOffset,
                id -> { commitFields(); selectedEffect = id; effectPage = 0; init(); });
        DeveloperWorkspace.VisualEffect effect = currentVisualEffect();
        if (effect != null) {
            int y = panelY + 72;
            int gap = 8;
            int half = (formWidth - gap) / 2;
            if (effectPage == 0) {
                addField("Effect", effect.name, formX, y, half, value -> effect.name = value,
                        "This profile edits the existing crown or gem effect in the mod.", true);
                addField("Particle type id", effect.particle, formX + half + gap, y, half,
                        value -> effect.particle = value, "Use keep to preserve the existing mix, a registered simple particle, or minecraft:dust.");
                y += 42;
                int third = (formWidth - gap * 2) / 3;
                addField("Enabled", Boolean.toString(effect.enabled), formX, y, third,
                        value -> effect.enabled = Boolean.parseBoolean(value), "Disable only the particles for this effect.");
                addField("Density", number(effect.density), formX + third + gap, y, third,
                        value -> effect.density = decimal(value, effect.density), "Scales the number of particles from 0 to 4.");
                addField("Spread", number(effect.spread), formX + (third + gap) * 2, y, third,
                        value -> effect.spread = decimal(value, effect.spread), "Scales particle spread and effect radius from 0 to 4.");
                y += 42;
                addParagraph("These controls edit the existing crown aura, crown ability bursts, beam and all ten gem activations. Saving updates the running world and syncs the crown visuals to connected players.", y, 0xFFB7C2D8);
            } else {
                int third = (formWidth - gap * 2) / 3;
                addField("Particle size", number(effect.size), formX, y, third,
                        value -> effect.size = decimal(value, effect.size), "Dust particle size, from 0.1 to 4.");
                addField("Red 0–1", number(effect.red), formX + third + gap, y, third,
                        value -> effect.red = decimal(value, effect.red), "Used when particle type is minecraft:dust.");
                addField("Green 0–1", number(effect.green), formX + (third + gap) * 2, y, third,
                        value -> effect.green = decimal(value, effect.green), "Used when particle type is minecraft:dust.");
                y += 42;
                addField("Blue 0–1", number(effect.blue), formX, y, third,
                        value -> effect.blue = decimal(value, effect.blue), "Used when particle type is minecraft:dust.");
                addParagraph("Color and size edits apply to dust particles. Selecting another particle type uses that particle's built-in appearance.", y + 48, 0xFFB7C2D8);
            }
            addPageButtons(effectPage, 2, page -> { commitFields(); effectPage = page; init(); });
        }
        addEffectButtons();
    }

    private void initEffects() {
        addSelectableList("visual", workspace.visualEffects.stream().map(effect -> effect.name).toList(),
                workspace.visualEffects.stream().map(effect -> effect.id).toList(), visualOffset,
                id -> {
                    commitFields(); selectedEffect = id; effectPage = 0;
                    DeveloperWorkspace.VisualEffect selected = currentVisualEffect();
                    selectedLayer = selected == null || selected.layers.isEmpty() ? "" : selected.layers.get(0).id;
                    visualLayerOffset = 0; init();
                });
        DeveloperWorkspace.VisualEffect effect = currentVisualEffect();
        if (effect != null) {
            currentVisualLayer();
            int gap = 4;
            int navWidth = 22;
            int controlWidth = Math.max(24, (formWidth - 2 * gap * 2 - navWidth * 2 - 8) / 3);
            addRenderableWidget(Button.builder(Component.literal("Add layer"), button -> addVisualLayer(effect, false))
                    .bounds(formX, panelY + 70, controlWidth, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Duplicate"), button -> addVisualLayer(effect, true))
                    .bounds(formX + controlWidth + gap, panelY + 70, controlWidth, 20).build());
            Button remove = Button.builder(Component.literal("Remove"), button -> removeVisualLayer(effect))
                    .bounds(formX + (controlWidth + gap) * 2, panelY + 70, controlWidth, 20).build();
            remove.active = effect.layers.size() > 1;
            addRenderableWidget(remove);
            addRenderableWidget(Button.builder(Component.literal("‹"), button -> {
                visualLayerOffset = Math.max(0, visualLayerOffset - graphVisibleRows() * graphColumns()); init();
            }).bounds(formX + formWidth - navWidth * 2 - gap, panelY + 70, navWidth, 20).build());
            addRenderableWidget(Button.builder(Component.literal("›"), button -> {
                visualLayerOffset = Math.min(Math.max(0, effect.layers.size() - graphVisibleRows() * graphColumns()),
                        visualLayerOffset + graphVisibleRows() * graphColumns()); init();
            }).bounds(formX + formWidth - navWidth, panelY + 70, navWidth, 20).build());
            addParagraph("PARTICLE FLOW  /  SOURCE → OUTPUT", formX, panelY + 96 - effectScroll, 0xFFFFD47B);
            buildParticleGraph(effect);

            DeveloperWorkspace.VisualLayer layer = currentVisualLayer();
            if (layer != null) {
                int y = panelY + 211 - effectScroll;
                int fieldGap = 8, half = (formWidth - fieldGap) / 2;
                addField("Layer name", layer.name, formX, y, half, value -> layer.name = value,
                        "Label shown on the graph node.");
                addField("Source particle id", layer.sourceParticle, formX + half + fieldGap, y, half,
                        value -> layer.sourceParticle = value, "Only particles emitted by this source registry type enter this node.");
                y += 42;
                addField("Output particle id", layer.particle, formX, y, Math.max(60, formWidth - 88),
                        value -> layer.particle = value, "Use keep to preserve the source, minecraft:dust, or a registered simple particle.");
                addRenderableWidget(Button.builder(Component.literal("Next"), button -> {
                    commitFields(); layer.particle = nextVisualParticle(layer.particle); localDirty = true; init();
                }).bounds(formX + formWidth - 82, y + 11, 82, 20).build());
                y += 42;
                int third = (formWidth - fieldGap * 2) / 3;
                addField("Enabled", Boolean.toString(layer.enabled), formX, y, third,
                        value -> layer.enabled = Boolean.parseBoolean(value), "Disable this source stream without affecting sibling nodes.");
                addField("Density", number(layer.density), formX + third + fieldGap, y, third,
                        value -> layer.density = decimal(value, layer.density), "0 removes the stream; values above 1 add copies, up to 4.");
                addField("Spread", number(layer.spread), formX + (third + fieldGap) * 2, y, third,
                        value -> layer.spread = decimal(value, layer.spread), "Scales velocity and effect geometry for this source stream.");
                y += 42;
                int quarter = (formWidth - fieldGap * 3) / 4;
                addField("Dust size", number(layer.size), formX, y, quarter,
                        value -> layer.size = decimal(value, layer.size), "Used when this layer outputs minecraft:dust.");
                addField("Red", number(layer.red), formX + quarter + fieldGap, y, quarter,
                        value -> layer.red = decimal(value, layer.red), "Dust red channel from 0 to 1.");
                addField("Green", number(layer.green), formX + (quarter + fieldGap) * 2, y, quarter,
                        value -> layer.green = decimal(value, layer.green), "Dust green channel from 0 to 1.");
                addField("Blue", number(layer.blue), formX + (quarter + fieldGap) * 3, y, quarter,
                        value -> layer.blue = decimal(value, layer.blue), "Dust blue channel from 0 to 1.");
            } else {
                addParagraph("This effect has no particle nodes. Add a layer to start the graph.", formX, panelY + 230 - effectScroll, 0xFFB7C2D8);
            }
            addParagraph("Each connection edits only its matching source. Unmatched particle types pass through unchanged.", formX, panelY + panelHeight - 52, 0xFFB7C2D8);
        }
        setEffectFieldVisibility();
        addEffectButtons();
    }

    private int graphColumns() { return formWidth >= 420 ? 2 : 1; }
    private int graphVisibleRows() { return panelHeight < 360 ? 2 : 3; }
    private int effectScrollMax() { return Math.max(0, 434 - panelHeight); }
    private int effectContentTop() { return panelY + 96; }
    private int effectContentBottom() { return panelY + panelHeight - 64; }

    private void setEffectFieldVisibility() {
        int top = effectContentTop();
        int bottom = effectContentBottom();
        for (Field field : fields) {
            EditBox box = field.box();
            box.visible = box.getY() >= top && box.getY() + box.getHeight() <= bottom;
        }
    }

    private void buildParticleGraph(DeveloperWorkspace.VisualEffect effect) {
        if (effect.layers.isEmpty()) return;
        int columns = graphColumns();
        int visible = graphVisibleRows() * columns;
        visualLayerOffset = Math.max(0, Math.min(visualLayerOffset, Math.max(0, effect.layers.size() - visible)));
        int cardWidth = (formWidth - (columns - 1) * 8) / columns;
        for (int i = visualLayerOffset; i < Math.min(effect.layers.size(), visualLayerOffset + visible); i++) {
            DeveloperWorkspace.VisualLayer layer = effect.layers.get(i);
            int local = i - visualLayerOffset;
            int nodeY = panelY + 108 + (local / columns) * 31 - effectScroll;
            if (nodeY >= effectContentTop() && nodeY + 26 <= effectContentBottom()) {
                graphNodes.add(new GraphNode(formX + (local % columns) * (cardWidth + 8),
                        nodeY, cardWidth, 26, layer.id, layer.name,
                        layer.sourceParticle, layer.particle, layer.id.equals(selectedLayer)));
            }
        }
    }

    private void addVisualLayer(DeveloperWorkspace.VisualEffect effect, boolean duplicate) {
        commitFields();
        if (effect.layers.size() >= 32) { showMessage("A visual effect can contain at most 32 particle layers.", false); return; }
        DeveloperWorkspace.VisualLayer layer;
        if (duplicate && currentVisualLayer() != null) {
            DeveloperWorkspace.VisualLayer original = currentVisualLayer();
            layer = DeveloperWorkspace.GSON.fromJson(DeveloperWorkspace.GSON.toJson(original), DeveloperWorkspace.VisualLayer.class);
            layer.id = uniqueId(original.id + "_copy", effect.layers.stream().map(value -> value.id).toList());
            layer.name += " Copy";
            layer.sourceParticle = nextUnusedSource(effect);
        } else {
            layer = new DeveloperWorkspace.VisualLayer();
            layer.id = uniqueId("particle_layer", effect.layers.stream().map(value -> value.id).toList());
            layer.name = "New Particle Layer";
            layer.sourceParticle = nextUnusedSource(effect);
            layer.particle = "keep";
        }
        if (layer.sourceParticle == null) { showMessage("Every registered source particle already has a node for this effect.", false); return; }
        effect.layers.add(layer);
        selectedLayer = layer.id;
        visualLayerOffset = Math.max(0, effect.layers.size() - graphVisibleRows() * graphColumns());
        localDirty = true; init();
    }

    private void removeVisualLayer(DeveloperWorkspace.VisualEffect effect) {
        if (effect.layers.size() <= 1) return;
        commitFields();
        int index = effect.layers.indexOf(currentVisualLayer());
        effect.layers.removeIf(layer -> layer.id.equals(selectedLayer));
        selectedLayer = effect.layers.get(Math.max(0, Math.min(index, effect.layers.size() - 1))).id;
        localDirty = true; init();
    }

    private void addEffectButtons() {
        int y = panelY + panelHeight - 28;
        int gap = 4;
        int width = (panelWidth - 16 - gap * 3) / 4;
        String[] labels = {"Preview", "Undo", "Redo", "Save"};
        Runnable[] actions = {this::previewVisualEffect, () -> applyHistory("undo"), () -> applyHistory("redo"), this::saveWorkspace};
        for (int i = 0; i < labels.length; i++) {
            int index = i;
            addRenderableWidget(Button.builder(Component.literal(labels[i]), button -> actions[index].run())
                    .bounds(panelX + 8 + i * (width + gap), y, width, 20).build());
        }
    }

    private void previewVisualEffectLegacy() {
        commitFields();
        DeveloperWorkspace.VisualEffect effect = currentVisualEffect();
        if (effect == null) return;
        if (!effect.enabled || effect.density <= 0) {
            showMessage("This visual profile is disabled, so it has no particle preview.", false);
            return;
        }
        DeveloperWorkspace.ParticlePreset preview = new DeveloperWorkspace.ParticlePreset();
        preview.id = effect.id + "_preview";
        preview.name = effect.name + " Preview";
        preview.particle = effect.particle.equalsIgnoreCase("keep") ? "minecraft:dust" : effect.particle;
        preview.shape = "sphere";
        preview.position = "player";
        preview.radius = 1.8 * effect.spread;
        preview.count = Math.max(1, (int) Math.round(24 * effect.density));
        preview.size = effect.size;
        preview.red = effect.red; preview.green = effect.green; preview.blue = effect.blue;
        preview.durationTicks = 1;
        DeveloperToolClient.request("preview_particle", DeveloperWorkspace.GSON.toJson(preview));
        Minecraft.getInstance().setScreen(null);
    }

    private void previewVisualEffect() {
        commitFields();
        DeveloperWorkspace.VisualLayer layer = currentVisualLayer();
        DeveloperWorkspace.VisualEffect effect = currentVisualEffect();
        if (layer == null || effect == null) return;
        if (!layer.enabled || layer.density <= 0) {
            showMessage("This particle layer is disabled, so it has no preview.", false);
            return;
        }
        DeveloperWorkspace.ParticlePreset preview = new DeveloperWorkspace.ParticlePreset();
        preview.id = effect.id + "_" + layer.id + "_preview";
        preview.name = layer.name + " Preview";
        String particleId = layer.particle.equalsIgnoreCase("keep") ? layer.sourceParticle : layer.particle;
        ResourceLocation parsed = ResourceLocation.tryParse(particleId);
        var particleType = parsed == null ? null : BuiltInRegistries.PARTICLE_TYPE.getOptional(parsed).orElse(null);
        preview.particle = particleType instanceof net.minecraft.core.particles.SimpleParticleType
                || parsed != null && parsed.equals(ResourceLocation.withDefaultNamespace("dust")) ? particleId : "minecraft:dust";
        preview.shape = "sphere";
        preview.position = "player";
        preview.radius = 1.8 * layer.spread;
        preview.count = Math.max(1, Math.min(120, (int) Math.round(24 * layer.density)));
        preview.size = layer.size;
        preview.red = layer.red; preview.green = layer.green; preview.blue = layer.blue;
        preview.durationTicks = 1;
        DeveloperToolClient.request("preview_particle", DeveloperWorkspace.GSON.toJson(preview));
        Minecraft.getInstance().setScreen(null);
    }

    private void initSounds() {
        addSelectableList("sound", workspace.sounds.stream().map(s -> s.name).toList(),
                workspace.sounds.stream().map(s -> s.id).toList(), soundOffset,
                id -> { commitFields(); selectedSound = id; soundPage = 0; init(); });
        DeveloperWorkspace.SoundPreset s = currentSound();
        if (s != null) {
            int y = panelY + 72, gap = 8, half = (formWidth - gap) / 2;
            if (soundPage == 0) {
                addField("Preset name", s.name, formX, y, half, value -> s.name = value, "Human-readable sound preset name.");
                addField("Preset ID", s.id, formX + half + gap, y, half, value -> setSoundId(s, value), "Stable lowercase id used by workspace exports.");
                y += 42;
                addField("Sound event id", s.sound, formX, y, Math.max(60, formWidth - 88), value -> s.sound = value, "Minecraft, Hypercrown, or resource pack sound event id.");
                addRenderableWidget(Button.builder(Component.literal("Next sound"), button -> {
                    commitFields(); s.sound = nextRegisteredId(BuiltInRegistries.SOUND_EVENT.keySet().stream().map(Object::toString).sorted().toList(), s.sound);
                    localDirty = true; init();
                }).bounds(formX + formWidth - 82, y + 11, 82, 20).build());
                y += 42;
                int third = (formWidth - gap * 2) / 3;
                addField("Volume", number(s.volume), formX, y, third, value -> s.volume = decimal(value, s.volume), "Volume from 0 to 4.");
                addField("Pitch", number(s.pitch), formX + third + gap, y, third, value -> s.pitch = decimal(value, s.pitch), "Pitch from 0.01 to 2.");
                addField("Distance", number(s.distance), formX + (third + gap) * 2, y, third, value -> s.distance = decimal(value, s.distance), "Saved as intended attenuation distance. Actual rolloff follows the sound event and vanilla sound engine.");
                y += 42;
                addField("Trigger", s.trigger, formX, y, third, value -> s.trigger = value, "cast, hit, tick, end, or a custom trigger name.");
                addField("Attachment", s.attachment, formX + third + gap, y, third, value -> s.attachment = value.toLowerCase(Locale.ROOT), "player, target, or world.");
                addField("World X", number(s.x), formX + (third + gap) * 2, y, third, value -> s.x = decimal(value, s.x), "Fixed X when attachment is world.");
                y += 42;
                addField("World Y", number(s.y), formX, y, third, value -> s.y = decimal(value, s.y), "Fixed Y when attachment is world.");
                addField("World Z", number(s.z), formX + third + gap, y, third, value -> s.z = decimal(value, s.z), "Fixed Z when attachment is world.");
            } else {
                int third = (formWidth - gap * 2) / 3;
                addField("Volume variance", number(s.volumeVariance), formX, y, third, value -> s.volumeVariance = decimal(value, s.volumeVariance), "Random volume variation as a fraction of base volume.");
                addField("Pitch variance", number(s.pitchVariance), formX + third + gap, y, third, value -> s.pitchVariance = decimal(value, s.pitchVariance), "Random pitch variation as a fraction of base pitch.");
                addField("Sound pool (comma separated)", s.soundPool, formX, y + 42, formWidth, value -> s.soundPool = value, "Preview chooses one valid event from this pool, or the main sound id when empty.");
                addField("Sequence: id@delay@volume@pitch;...", s.sequence, formX, y + 84, formWidth, value -> s.sequence = value, "Example: minecraft:block.note_block.pling@0@0.8@1.2;minecraft:block.note_block.bell@8@0.7@0.9. Up to 15 events are queued.");
            }
            addPageButtons(soundPage, 2, page -> { commitFields(); soundPage = page; init(); });
        }
        addWorkspaceButtons("Preview", this::previewSound);
    }

    private void initConfig() {
        int y = panelY + 54;
        addRenderableWidget(Button.builder(Component.literal("Category: " + configCategory), button -> {
            int i = categories.indexOf(configCategory);
            configCategory = categories.get((i + 1) % categories.size());
            configOffset = 0; init();
        }).bounds(panelX + 8, y, sidebarWidth - 12, 20).build());
        configSearchBox = new EditBox(font, formX, y, Math.max(60, formWidth - 84), 20, Component.literal("Search config"));
        configSearchBox.setMaxLength(80);
        configSearchBox.setValue(configSearch);
        configSearchBox.setHint(Component.literal("Search settings…"));
        configSearchBox.setResponder(value -> { configSearch = value; configOffset = 0; });
        addRenderableWidget(configSearchBox);
        addRenderableWidget(Button.builder(Component.literal("Search"), button -> init()).bounds(panelX + panelWidth - 80, y, 72, 20).build());

        List<DeveloperWorkspace.ConfigEntry> filtered = filteredConfig();
        if (filtered.stream().noneMatch(entry -> entry.path.equals(selectedConfig))) selectedConfig = filtered.isEmpty() ? "" : filtered.get(0).path;
        List<String> labels = filtered.stream().map(entry -> entry.path.substring(entry.path.indexOf('.') + 1)).toList();
        List<String> ids = filtered.stream().map(entry -> entry.path).toList();
        addSelectableList("config", labels, ids, configOffset, id -> { selectedConfig = id; pendingConfig = false; init(); }, panelY + 82);

        DeveloperWorkspace.ConfigEntry entry = snapshot == null ? null : snapshot.config.stream().filter(value -> value.path.equals(selectedConfig)).findFirst().orElse(null);
        if (entry != null) {
            int x = formX;
            int detailY = panelY + 88;
            addParagraph(entry.path, detailY, 0xFFFFD47B);
            detailY += 24;
            addParagraph("Current: " + entry.value + "    Default: " + entry.defaultValue, detailY, 0xFFE5E1EF);
            detailY += 20;
            addParagraph("Type: " + entry.type + (entry.range.isBlank() ? "" : "    Range: " + entry.range), detailY, 0xFFBAC4D9);
            detailY += 20;
            addParagraph("Restart: " + entry.restart + (entry.restart.equals("NONE") ? " (applies immediately)" : " (restart required)"), detailY, entry.restart.equals("NONE") ? 0xFF80D6A2 : 0xFFFFC36A);
            detailY += 26;
            String comment = entry.comment.isBlank() ? "No description is registered for this setting." : entry.comment;
            for (String line : wrap(comment, Math.max(20, formWidth / 6))) {
                addParagraph(line, detailY, 0xFFB7C2D8);
                detailY += 12;
            }
            detailY += 14;
            if (entry.type.equals("Boolean")) {
                addRenderableWidget(Button.builder(Component.literal("Toggle from " + entry.value), button -> {
                    JsonObject request = new JsonObject(); request.addProperty("path", entry.path); request.addProperty("value", !Boolean.parseBoolean(entry.value));
                    pendingConfig = true; DeveloperToolClient.request("config_set", request.toString());
                }).bounds(x, detailY, Math.min(210, formWidth), 20).build());
            } else {
                configValueBox = new EditBox(font, x, detailY, Math.min(250, formWidth), 20, Component.literal("Value"));
                configValueBox.setMaxLength(128);
                configValueBox.setValue(entry.value);
                addRenderableWidget(configValueBox);
                addRenderableWidget(Button.builder(Component.literal("Apply"), button -> {
                    try {
                        JsonObject request = new JsonObject(); request.addProperty("path", entry.path); request.add("value", parseConfigValue(entry.type, configValueBox.getValue()));
                        pendingConfig = true; DeveloperToolClient.request("config_set", request.toString());
                    } catch (RuntimeException exception) { showMessage("Enter a valid " + entry.type + " value.", false); }
                }).bounds(x + Math.min(258, formWidth - 72), detailY, 72, 20).build());
            }
            detailY += 30;
            int actionY = Math.min(panelY + panelHeight - 88, detailY);
            addRenderableWidget(Button.builder(Component.literal("Reset option"), button -> {
                JsonObject request = new JsonObject(); request.addProperty("path", entry.path);
                pendingConfig = true; DeveloperToolClient.request("config_reset", request.toString());
            }).bounds(x, actionY, 116, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Reset category"), button -> {
                JsonObject request = new JsonObject(); request.addProperty("category", topCategory(entry.path));
                pendingConfig = true; DeveloperToolClient.request("config_reset", request.toString());
            }).bounds(x + 122, actionY, 126, 20).build());
            addRenderableWidget(Button.builder(Component.literal(pendingResetAll ? "Confirm reset all" : "Reset all"), button -> {
                if (!pendingResetAll) { pendingResetAll = true; ticksSinceEdit = 0; init(); return; }
                pendingResetAll = false;
                pendingConfig = true;
                DeveloperToolClient.request("config_reset", "{}");
            }).bounds(x + 254, actionY, 126, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Export"), button -> exportConfig()).bounds(x + 386, actionY, 76, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Import"), button -> importConfig()).bounds(x + 468, actionY, Math.max(70, formWidth - 468), 20).build());
            detailY = actionY + 26;
            if (workspace.configBackups.isEmpty()) {
                addParagraph("No config backups saved yet. A snapshot is created before each config change.", detailY, 0xFF8E99AE);
            } else {
                addRenderableWidget(Button.builder(Component.literal("Restore latest backup"), button -> {
                    pendingConfig = true; DeveloperToolClient.request("config_restore", "0");
                }).bounds(x, detailY, 190, 20).build());
            }
        } else {
            addParagraph("No setting matches this filter.", formX, panelY + 90, 0xFFBAC4D9);
        }
    }

    private void initDebug() {
        int y = panelY + 76;
        boolean enabled = snapshot != null && snapshot.debugEnabled;
        addRenderableWidget(Button.builder(Component.literal("Debug overlay: " + (enabled ? "ON" : "OFF")), button -> {
            DeveloperToolClient.request("toggle_debug", "");
        }).bounds(formX, y, 210, 20).build());
        y += 36;
        addParagraph("Server: " + serverLabel(), formX, y, 0xFFE5E1EF); y += 18;
        addParagraph("Registered abilities: " + workspace.abilities.stream().filter(a -> a.core).count(), formX, y, 0xFFBAC4D9); y += 18;
        addParagraph("Custom test abilities: " + workspace.abilities.stream().filter(a -> !a.core).count(), formX, y, 0xFFBAC4D9); y += 18;
        addParagraph("Particle previews running: " + (snapshot == null ? 0 : snapshot.activeParticlePreviews), formX, y, 0xFFBAC4D9); y += 18;
        addParagraph("Queued sounds: " + (snapshot == null ? 0 : snapshot.pendingSounds), formX, y, 0xFFBAC4D9); y += 24;
        addParagraph("/hypercrown dev and /hc dev open this operator-only panel. F8 is the in-game keybind.", formX, y, 0xFFB7C2D8); y += 16;
        addParagraph("Reload rereads this world's workspace and applies core enable/cooldown settings without a server restart.", formX, y, 0xFFB7C2D8); y += 16;
        addParagraph("Custom abilities currently run only from Test. Mana, arbitrary conditions, and custom hotkeys require code integration.", formX, y, 0xFFFFC36A);
        addRenderableWidget(Button.builder(Component.literal("Reload workspace"), button -> {
            pendingSave = false; DeveloperToolClient.request("reload", "");
        }).bounds(formX, panelY + panelHeight - 60, 160, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Export workspace"), button -> exportWorkspace()).bounds(formX + 168, panelY + panelHeight - 60, 160, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Import workspace"), button -> importWorkspace()).bounds(formX + 336, panelY + panelHeight - 60, 160, 20).build());
    }

    private void initLogs() {
        addParagraph("Recent developer tool and configuration events", formX, panelY + 70, 0xFFBAC4D9);
        if (snapshot == null || snapshot.logs.isEmpty()) {
            addParagraph("No events recorded in this session.", formX, panelY + 94, 0xFF8E99AE);
            return;
        }
        int y = panelY + 96;
        List<String> logs = snapshot.logs;
        int maxRows = Math.max(1, (panelHeight - 138) / 14);
        int end = Math.max(0, logs.size() - logOffset);
        int start = Math.max(0, end - maxRows);
        for (int i = start; i < end; i++) {
            String line = logs.get(i);
            addParagraph(line, formX, y, line.contains("ERROR") ? 0xFFFF8585 : 0xFFE1DDED);
            y += 14;
        }
        addRenderableWidget(Button.builder(Component.literal("Older"), button -> { logOffset = Math.min(snapshot.logs.size(), logOffset + 5); init(); })
                .bounds(formX, panelY + panelHeight - 60, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Newer"), button -> { logOffset = Math.max(0, logOffset - 5); init(); })
                .bounds(formX + 82, panelY + panelHeight - 60, 76, 20).build());
    }

    private void addSelectableList(String kind, List<String> labels, List<String> ids, int offset, Consumer<String> select) {
        addSelectableList(kind, labels, ids, offset, select, panelY + 70);
    }

    private void addSelectableList(String kind, List<String> labels, List<String> ids, int offset, Consumer<String> select, int top) {
        int visible = visibleListRows(top);
        int from = Math.max(0, Math.min(offset, Math.max(0, ids.size() - visible)));
        int to = Math.min(ids.size(), from + visible);
        for (int i = from; i < to; i++) {
            String id = ids.get(i);
            int index = i;
            int y = top + (i - from) * 22;
            addRenderableWidget(Button.builder(Component.literal(clip(labels.get(i), sidebarWidth - 28)), button -> select.accept(id))
                    .bounds(panelX + 8, y, sidebarWidth - 16, 20).build());
        }
        int navY = panelY + panelHeight - 60;
        addRenderableWidget(Button.builder(Component.literal("‹"), button -> {
            switch (kind) {
                case "ability" -> abilityOffset = Math.max(0, abilityOffset - visible);
                case "particle" -> particleOffset = Math.max(0, particleOffset - visible);
                case "sound" -> soundOffset = Math.max(0, soundOffset - visible);
                case "visual" -> visualOffset = Math.max(0, visualOffset - visible);
                case "config" -> configOffset = Math.max(0, configOffset - visible);
            }
            init();
        }).bounds(panelX + 8, navY, (sidebarWidth - 20) / 2, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), button -> {
            int max = Math.max(0, ids.size() - visible);
            switch (kind) {
                case "ability" -> abilityOffset = Math.min(max, abilityOffset + visible);
                case "particle" -> particleOffset = Math.min(max, particleOffset + visible);
                case "sound" -> soundOffset = Math.min(max, soundOffset + visible);
                case "visual" -> visualOffset = Math.min(max, visualOffset + visible);
                case "config" -> configOffset = Math.min(max, configOffset + visible);
            }
            init();
        }).bounds(panelX + 12 + (sidebarWidth - 20) / 2, navY, (sidebarWidth - 20) / 2, 20).build());
        addParagraph(ids.isEmpty() ? "Empty" : (from + 1) + "–" + to + " / " + ids.size(), panelX + 10, navY - 14, 0xFF8E99AE);
    }

    private int visibleListRows(int top) { return Math.max(3, (panelY + panelHeight - 68 - top) / 22); }

    private void addWorkspaceButtons(String testLabel, Runnable testAction) {
        int y = panelY + panelHeight - 28;
        int gap = 3, count = 7;
        int w = (panelWidth - 16 - gap * (count - 1)) / count;
        List<Runnable> actions = List.of(this::newEntry, this::duplicateEntry, this::deleteEntry,
                testAction, () -> applyHistory("undo"), () -> applyHistory("redo"), this::saveWorkspace);
        String[] labels = {"New", "Copy", pendingDelete ? "Confirm delete" : "Delete", testLabel, "Undo", "Redo", "Save"};
        for (int i = 0; i < count; i++) {
            int x = panelX + 8 + i * (w + gap);
            int index = i;
            addRenderableWidget(Button.builder(Component.literal(labels[i]), button -> actions.get(index).run())
                    .bounds(x, y, w, 20).build());
        }
    }

    private void addPageButtons(int page, int pageCount, Consumer<Integer> change) {
        int y = panelY + panelHeight - 59;
        int x = panelX + sidebarWidth + 14;
        addRenderableWidget(Button.builder(Component.literal("‹"), button -> change.accept(Math.floorMod(page - 1, pageCount)))
                .bounds(x, y, 28, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Page " + (page + 1) + "/" + pageCount), button -> change.accept((page + 1) % pageCount))
                .bounds(x + 32, y, 104, 20).build());
        addRenderableWidget(Button.builder(Component.literal("›"), button -> change.accept((page + 1) % pageCount))
                .bounds(x + 140, y, 28, 20).build());
    }

    private void addField(String label, String value, int x, int y, int w, Consumer<String> commit, String help) {
        addField(label, value, x, y, w, commit, help, false);
    }

    private void addField(String label, String value, int x, int y, int w, Consumer<String> commit, String help, boolean locked) {
        if (w < 54) return;
        EditBox box = new EditBox(font, x, y + 11, w, 20, Component.literal(label));
        box.setMaxLength(512);
        box.setValue(value == null ? "" : value);
        box.setEditable(!locked);
        box.setResponder(s -> { localDirty = true; ticksSinceEdit = 0; });
        addRenderableWidget(box);
        fields.add(new Field(box, label, help, commit));
    }

    private void addParagraph(String text, int x, int y, int color) {
        paragraphSink.add(new Paragraph(text, x, y, color));
    }

    private final List<Paragraph> paragraphSink = new ArrayList<>();
    private record Paragraph(String text, int x, int y, int color) {}

    private void commitFields() {
        for (Field field : fields) {
            try { field.commit().accept(field.box().getValue()); }
            catch (RuntimeException ignored) { }
        }
        normalizeWorkspace();
    }

    private void normalizeWorkspace() {
        for (DeveloperWorkspace.AbilityDraft a : workspace.abilities) a.normalize();
        for (DeveloperWorkspace.ParticlePreset p : workspace.particles) p.normalize();
        for (DeveloperWorkspace.VisualEffect effect : workspace.visualEffects) effect.normalize();
        for (DeveloperWorkspace.SoundPreset s : workspace.sounds) s.normalize();
    }

    private void newEntry() {
        commitFields();
        if (tab == Tab.ABILITIES) {
            DeveloperWorkspace.AbilityDraft draft = new DeveloperWorkspace.AbilityDraft();
            draft.id = uniqueId("custom_ability", workspace.abilities.stream().map(a -> a.id).toList());
            draft.name = DeveloperWorkspace.title(draft.id);
            workspace.abilities.add(draft); selectedAbility = draft.id; abilityPage = 0;
        } else if (tab == Tab.PARTICLES) {
            DeveloperWorkspace.ParticlePreset draft = new DeveloperWorkspace.ParticlePreset();
            draft.id = uniqueId("particle", workspace.particles.stream().map(p -> p.id).toList());
            draft.name = DeveloperWorkspace.title(draft.id);
            workspace.particles.add(draft); selectedParticle = draft.id; particlePage = 0;
        } else if (tab == Tab.SOUNDS) {
            DeveloperWorkspace.SoundPreset draft = new DeveloperWorkspace.SoundPreset();
            draft.id = uniqueId("sound", workspace.sounds.stream().map(s -> s.id).toList());
            draft.name = DeveloperWorkspace.title(draft.id);
            workspace.sounds.add(draft); selectedSound = draft.id; soundPage = 0;
        }
        localDirty = true; init();
    }

    private void duplicateEntry() {
        commitFields();
        if (tab == Tab.ABILITIES) {
            DeveloperWorkspace.AbilityDraft original = currentAbility(); if (original == null) return;
            DeveloperWorkspace.AbilityDraft copy = DeveloperWorkspace.GSON.fromJson(DeveloperWorkspace.GSON.toJson(original), DeveloperWorkspace.AbilityDraft.class);
            copy.core = false; copy.id = uniqueId(original.id + "_copy", workspace.abilities.stream().map(a -> a.id).toList()); copy.name += " Copy";
            workspace.abilities.add(copy); selectedAbility = copy.id;
        } else if (tab == Tab.PARTICLES) {
            DeveloperWorkspace.ParticlePreset original = currentParticle(); if (original == null) return;
            DeveloperWorkspace.ParticlePreset copy = DeveloperWorkspace.GSON.fromJson(DeveloperWorkspace.GSON.toJson(original), DeveloperWorkspace.ParticlePreset.class);
            copy.id = uniqueId(original.id + "_copy", workspace.particles.stream().map(p -> p.id).toList()); copy.name += " Copy";
            workspace.particles.add(copy); selectedParticle = copy.id;
        } else if (tab == Tab.SOUNDS) {
            DeveloperWorkspace.SoundPreset original = currentSound(); if (original == null) return;
            DeveloperWorkspace.SoundPreset copy = DeveloperWorkspace.GSON.fromJson(DeveloperWorkspace.GSON.toJson(original), DeveloperWorkspace.SoundPreset.class);
            copy.id = uniqueId(original.id + "_copy", workspace.sounds.stream().map(s -> s.id).toList()); copy.name += " Copy";
            workspace.sounds.add(copy); selectedSound = copy.id;
        }
        localDirty = true; init();
    }

    private void deleteEntry() {
        if (!pendingDelete) { commitFields(); pendingDelete = true; ticksSinceEdit = 0; init(); return; }
        commitFields();
        if (tab == Tab.ABILITIES) {
            DeveloperWorkspace.AbilityDraft a = currentAbility(); if (a == null || a.core) { pendingDelete = false; init(); return; }
            workspace.abilities.remove(a); selectedAbility = workspace.abilities.stream().findFirst().map(v -> v.id).orElse("");
        } else if (tab == Tab.PARTICLES) {
            DeveloperWorkspace.ParticlePreset p = currentParticle(); if (p == null) return;
            workspace.particles.remove(p); selectedParticle = workspace.particles.stream().findFirst().map(v -> v.id).orElse("");
        } else if (tab == Tab.SOUNDS) {
            DeveloperWorkspace.SoundPreset s = currentSound(); if (s == null) return;
            workspace.sounds.remove(s); selectedSound = workspace.sounds.stream().findFirst().map(v -> v.id).orElse("");
        }
        pendingDelete = false; localDirty = true; init();
    }

    private static String uniqueId(String base, List<String> used) {
        String normalized = base.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
        if (normalized.length() > 40) normalized = normalized.substring(0, 40);
        String candidate = normalized; int index = 2;
        while (used.contains(candidate)) candidate = normalized + "_" + index++;
        return candidate;
    }

    private void testAbility() {
        commitFields();
        DeveloperWorkspace.AbilityDraft a = currentAbility();
        if (a != null) DeveloperToolClient.request("test_ability", a.core ? a.id : DeveloperWorkspace.GSON.toJson(a));
    }

    private void previewParticle() {
        commitFields();
        DeveloperWorkspace.ParticlePreset p = currentParticle();
        if (p != null) DeveloperToolClient.request("preview_particle", DeveloperWorkspace.GSON.toJson(p));
    }

    private void previewParticleInWorld() {
        previewParticle();
        Minecraft.getInstance().setScreen(null);
    }

    private void previewSound() {
        commitFields();
        DeveloperWorkspace.SoundPreset s = currentSound();
        if (s != null) DeveloperToolClient.request("preview_sound", DeveloperWorkspace.GSON.toJson(s));
    }

    private void saveWorkspace() {
        if (pendingSave) return;
        commitFields();
        pendingSave = true; localDirty = false;
        DeveloperToolClient.request("save", DeveloperWorkspace.GSON.toJson(workspace));
    }

    private void applyHistory(String action) {
        if (pendingSave) return;
        if (!localDirty) {
            DeveloperToolClient.request(action, "");
            return;
        }
        commitFields();
        pendingHistoryAction = action;
        saveWorkspace();
    }

    private void exportWorkspace() {
        commitFields();
        Minecraft.getInstance().keyboardHandler.setClipboard(DeveloperWorkspace.GSON.toJson(workspace));
        showMessage("Workspace JSON copied to clipboard.", true);
    }

    private void importWorkspace() {
        try {
            workspace = DeveloperWorkspace.parse(Minecraft.getInstance().keyboardHandler.getClipboard());
            selectedAbility = workspace.abilities.stream().findFirst().map(a -> a.id).orElse("");
            selectedParticle = workspace.particles.stream().findFirst().map(p -> p.id).orElse("");
            selectedEffect = workspace.visualEffects.stream().findFirst().map(effect -> effect.id).orElse("");
            selectedSound = workspace.sounds.stream().findFirst().map(s -> s.id).orElse("");
            localDirty = true; init(); saveWorkspace();
        } catch (RuntimeException exception) { showMessage("Clipboard does not contain a supported workspace JSON file.", false); }
    }

    private void exportConfig() {
        JsonObject json = new JsonObject();
        if (snapshot != null) for (DeveloperWorkspace.ConfigEntry entry : snapshot.config) {
            try { json.add(entry.path, JsonParser.parseString(entry.value)); } catch (RuntimeException ignored) { }
        }
        Minecraft.getInstance().keyboardHandler.setClipboard(json.toString());
        showMessage("Current server config values copied as JSON.", true);
    }

    private void importConfig() {
        try {
            JsonObject json = JsonParser.parseString(Minecraft.getInstance().keyboardHandler.getClipboard()).getAsJsonObject();
            pendingConfig = true; DeveloperToolClient.request("config_import", json.toString());
        } catch (RuntimeException exception) { showMessage("Clipboard must contain a JSON object of config paths and values.", false); }
    }

    void updateSnapshot(DeveloperWorkspace.Snapshot updated) {
        this.snapshot = updated;
        if (pendingSave) {
            pendingSave = false;
            if (updated.success) {
                workspace = parseWorkspace(updated.workspaceJson);
                localDirty = false;
            } else {
                localDirty = true;
                pendingHistoryAction = "";
            }
            init();
            if (updated.success && !pendingHistoryAction.isEmpty()) {
                String action = pendingHistoryAction;
                pendingHistoryAction = "";
                DeveloperToolClient.request(action, "");
            }
        } else if (pendingConfig) {
            pendingConfig = false;
            if (updated.success) {
                DeveloperWorkspace serverWorkspace = parseWorkspace(updated.workspaceJson);
                workspace.configBackups = serverWorkspace.configBackups;
                init();
            }
        } else if (!localDirty && updated.workspaceJson != null && !updated.workspaceJson.equals("{}")) {
            workspace = parseWorkspace(updated.workspaceJson);
            init();
        }
    }

    private void showMessage(String message, boolean good) {
        if (Minecraft.getInstance().player != null) Minecraft.getInstance().player.displayClientMessage(Component.literal(message), false);
    }

    private List<DeveloperWorkspace.ConfigEntry> filteredConfig() {
        if (snapshot == null || snapshot.config == null) return List.of();
        return snapshot.config.stream().filter(entry -> configCategory.equals("all") || topCategory(entry.path).equals(configCategory))
                .filter(entry -> configSearch.isBlank() || entry.path.toLowerCase(Locale.ROOT).contains(configSearch.toLowerCase(Locale.ROOT))
                        || entry.comment.toLowerCase(Locale.ROOT).contains(configSearch.toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static String topCategory(String path) { int dot = path.indexOf('.'); return dot < 0 ? path : path.substring(0, dot); }

    private static com.google.gson.JsonElement parseConfigValue(String type, String value) {
        return switch (type) {
            case "Boolean", "boolean" -> {
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException();
                yield new com.google.gson.JsonPrimitive(Boolean.parseBoolean(value));
            }
            case "Integer", "Long", "Double", "Float", "int", "long", "double", "float" -> {
                double number = Double.parseDouble(value); if (!Double.isFinite(number)) throw new IllegalArgumentException();
                yield JsonParser.parseString(value);
            }
            default -> new com.google.gson.JsonPrimitive(value);
        };
    }
    private DeveloperWorkspace.AbilityDraft currentAbility() { return workspace.abilities.stream().filter(a -> a.id.equals(selectedAbility)).findFirst().orElse(null); }
    private DeveloperWorkspace.ParticlePreset currentParticle() { return workspace.particles.stream().filter(p -> p.id.equals(selectedParticle)).findFirst().orElse(null); }
    private DeveloperWorkspace.VisualEffect currentVisualEffect() { return workspace.visualEffects.stream().filter(effect -> effect.id.equals(selectedEffect)).findFirst().orElse(null); }
    private DeveloperWorkspace.VisualLayer currentVisualLayer() {
        DeveloperWorkspace.VisualEffect effect = currentVisualEffect();
        if (effect == null) return null;
        if (effect.layers.stream().noneMatch(layer -> layer.id.equals(selectedLayer))) selectedLayer = effect.layers.isEmpty() ? "" : effect.layers.get(0).id;
        return effect.layers.stream().filter(layer -> layer.id.equals(selectedLayer)).findFirst().orElse(null);
    }
    private DeveloperWorkspace.SoundPreset currentSound() { return workspace.sounds.stream().filter(s -> s.id.equals(selectedSound)).findFirst().orElse(null); }

    private String nextVisualParticle(String current) {
        List<String> particleIds = new ArrayList<>();
        particleIds.add("keep");
        particleIds.addAll(BuiltInRegistries.PARTICLE_TYPE.keySet().stream()
                .filter(id -> id.equals(ResourceLocation.withDefaultNamespace("dust"))
                        || BuiltInRegistries.PARTICLE_TYPE.getOptional(id).orElse(null) instanceof net.minecraft.core.particles.SimpleParticleType)
                .map(Object::toString).sorted().toList());
        return nextRegisteredId(particleIds, current);
    }

    private String nextUnusedSource(DeveloperWorkspace.VisualEffect effect) {
        java.util.Set<ResourceLocation> used = new java.util.HashSet<>();
        for (DeveloperWorkspace.VisualLayer layer : effect.layers) {
            ResourceLocation id = ResourceLocation.tryParse(layer.sourceParticle);
            if (id != null) used.add(id);
        }
        List<String> candidates = new ArrayList<>(List.of("minecraft:dust", "minecraft:end_rod", "minecraft:electric_spark",
                "minecraft:enchant", "minecraft:flash", "minecraft:cloud", "minecraft:reverse_portal"));
        candidates.addAll(BuiltInRegistries.PARTICLE_TYPE.keySet().stream().map(Object::toString).sorted().toList());
        return candidates.stream().distinct().filter(candidate -> {
            ResourceLocation id = ResourceLocation.tryParse(candidate);
            return id != null && !used.contains(id);
        }).findFirst().orElse(null);
    }

    private void addParagraph(String text, int y, int color) { addParagraph(text, formX, y, color); }

    private void setAbilityId(DeveloperWorkspace.AbilityDraft ability, String id) {
        String oldId = ability.id;
        ability.id = id.toLowerCase(Locale.ROOT);
        if (selectedAbility.equals(oldId)) selectedAbility = ability.id;
    }

    private void setParticleId(DeveloperWorkspace.ParticlePreset preset, String id) {
        String oldId = preset.id;
        preset.id = id.toLowerCase(Locale.ROOT);
        if (selectedParticle.equals(oldId)) selectedParticle = preset.id;
    }

    private void setSoundId(DeveloperWorkspace.SoundPreset preset, String id) {
        String oldId = preset.id;
        preset.id = id.toLowerCase(Locale.ROOT);
        if (selectedSound.equals(oldId)) selectedSound = preset.id;
    }

    private static String nextRegisteredId(List<String> ids, String current) {
        if (ids.isEmpty()) return current;
        return ids.get((ids.indexOf(current) + 1) % ids.size());
    }

    private void drawParagraphs(GuiGraphics graphics) {
        for (Paragraph paragraph : paragraphSink) {
            if (tab == Tab.EFFECTS
                    && !(paragraph.y >= effectContentTop() && paragraph.y < effectContentBottom())
                    && paragraph.y < panelY + panelHeight - 48) continue;
            graphics.drawString(font, clip(paragraph.text, Math.max(40, panelX + panelWidth - paragraph.x - 12)), paragraph.x, paragraph.y, paragraph.color, false);
        }
    }

    private void drawParticleGraph(GuiGraphics graphics, int mouseX, int mouseY) {
        for (GraphNode node : graphNodes) {
            int border = node.selected ? 0xFFFFC45F : 0xFF46516A;
            int color = 0xFF5F91C4;
            DeveloperWorkspace.VisualEffect effect = currentVisualEffect();
            if (effect != null) {
                DeveloperWorkspace.VisualLayer layer = effect.layers.stream().filter(value -> value.id.equals(node.layerId)).findFirst().orElse(null);
                if (layer != null) color = 0xFF000000 | ((int) (layer.red * 190 + 40) << 16)
                        | ((int) (layer.green * 155 + 50) << 8) | (int) (layer.blue * 150 + 70);
            }
            graphics.fill(node.x(), node.y(), node.x() + node.width(), node.y() + node.height(), 0xFF101A2B);
            graphics.fill(node.x(), node.y(), node.x() + node.width(), node.y() + 1, border);
            graphics.fill(node.x(), node.y() + node.height() - 1, node.x() + node.width(), node.y() + node.height(), border);
            graphics.fill(node.x(), node.y(), node.x() + 1, node.y() + node.height(), border);
            graphics.fill(node.x() + node.width() - 1, node.y(), node.x() + node.width(), node.y() + node.height(), border);
            int nodeWidth = Math.max(24, (node.width() - 34) / 2);
            int boxY = node.y() + 4;
            int boxHeight = node.height() - 8;
            int sourceX = node.x() + 5;
            int outputX = node.x() + node.width() - nodeWidth - 5;
            graphics.fill(sourceX, boxY, sourceX + nodeWidth, boxY + boxHeight, 0xFF28445E);
            graphics.fill(outputX, boxY, outputX + nodeWidth, boxY + boxHeight, color);
            graphics.fill(sourceX + nodeWidth, node.y() + node.height() / 2 - 1,
                    outputX, node.y() + node.height() / 2 + 1, 0xFFFFD47B);
            graphics.drawString(font, "›", sourceX + nodeWidth + Math.max(0, (outputX - sourceX - 20) / 2), node.y() + 8, 0xFFFFD47B, false);
            graphics.drawCenteredString(font, clip(shortParticle(node.source()), nodeWidth - 6), sourceX + nodeWidth / 2, node.y() + 9, 0xFFEAF2FF);
            graphics.drawCenteredString(font, clip(shortParticle(node.output()), nodeWidth - 6), outputX + nodeWidth / 2, node.y() + 9, 0xFFFFFFFF);
            if (mouseX >= node.x() && mouseX < node.x() + node.width() && mouseY >= node.y() && mouseY < node.y() + node.height())
                graphics.renderTooltip(font, Component.literal(node.name() + "  " + node.source() + " -> " + node.output()), mouseX, mouseY);
        }
    }

    private static String shortParticle(String id) {
        if (id == null) return "?";
        String value = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
        return value.equalsIgnoreCase("keep") ? "keep" : value;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF080B14);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xFF0D1323);
        graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 2, 0xFFFFB84F);
        graphics.fill(panelX + 8, panelY + 51, panelX + panelWidth - 8, panelY + 52, 0xFF333B51);
        graphics.drawString(font, "HYPERCROWN  /  DEVELOPER WORKSHOP", panelX + 10, panelY + 9, 0xFFFFD47B, false);
        if (snapshot != null) {
            int color = snapshot.success ? 0xFF80D6A2 : 0xFFFF8585;
            graphics.drawString(font, clip(snapshot.message, Math.max(32, panelWidth - 258)), panelX + 238, panelY + 9, color, false);
        }
        drawParagraphs(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        if (tab == Tab.EFFECTS) {
            drawParticleGraph(graphics, mouseX, mouseY);
            if (effectScrollMax() > 0) {
                int top = effectContentTop(), bottom = effectContentBottom();
                int railX = panelX + panelWidth - 7;
                int trackHeight = Math.max(1, bottom - top);
                int thumbHeight = Math.max(18, trackHeight * trackHeight / (trackHeight + effectScrollMax()));
                int thumbY = top + (trackHeight - thumbHeight) * effectScroll / effectScrollMax();
                graphics.fill(railX, top, railX + 2, bottom, 0xFF333B51);
                graphics.fill(railX - 1, thumbY, railX + 3, thumbY + thumbHeight, 0xFFFFB84F);
            }
        }
        for (Field field : fields) {
            if (field.box().visible) graphics.drawString(font, field.label(), field.box().getX(), field.box().getY() - 10, 0xFFBFC9DE, false);
        }
        for (Field field : fields) {
            if (field.box().visible && field.box().isMouseOver(mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.literal(field.help()), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == Tab.EFFECTS && scrollY != 0
                && mouseX >= formX && mouseX < panelX + panelWidth - 8
                && mouseY >= effectContentTop() && mouseY < effectContentBottom()) {
            int updated = Math.max(0, Math.min(effectScrollMax(), effectScroll + (scrollY < 0 ? 32 : -32)));
            if (updated != effectScroll) {
                commitFields();
                effectScroll = updated;
                init();
            }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (tab == Tab.EFFECTS && button == 0) {
            for (GraphNode node : graphNodes) {
                if (mouseX >= node.x() && mouseX < node.x() + node.width()
                        && mouseY >= node.y() && mouseY < node.y() + node.height()) {
                    commitFields();
                    selectedLayer = node.layerId();
                    init();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void renderBlurredBackground(float partialTick) {
    }

    @Override public void tick() {
        super.tick();
        ticksSinceEdit++;
        if (pendingDelete && ticksSinceEdit > 80) { pendingDelete = false; init(); }
        if (pendingResetAll && ticksSinceEdit > 80) { pendingResetAll = false; init(); }
        if (localDirty && !pendingSave && ticksSinceEdit > 600) saveWorkspace();
    }

    @Override
    public void onClose() {
        lastViewState = new ViewState(tab, selectedAbility, selectedParticle, selectedEffect, selectedLayer, selectedSound, selectedConfig, configCategory,
                configSearch, abilityOffset, particleOffset, soundOffset, visualOffset, visualLayerOffset, effectScroll, configOffset, logOffset, abilityPage, particlePage, effectPage, soundPage);
        if (localDirty && !pendingSave) saveWorkspace();
        super.onClose();
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 && tab == Tab.CONFIG && configValueBox != null && configValueBox.isFocused()) {
            configValueBox.setFocused(false); return true;
        }
        if ((modifiers & 2) != 0 && keyCode == 83) {
            saveWorkspace(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private String serverLabel() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level == null ? "not connected" : mc.level.dimension().location().toString();
    }

    private static String number(double value) { return String.format(Locale.ROOT, "%.3f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }
    private static double decimal(String text, double fallback) { try { return Double.parseDouble(text.strip()); } catch (RuntimeException ignored) { return fallback; } }
    private static int integer(String text, int fallback) { try { return Integer.parseInt(text.strip()); } catch (RuntimeException ignored) { return fallback; } }
    private String clip(String text, int pixels) {
        String value = text == null ? "" : text;
        if (font == null) return value;
        int limit = Math.max(20, pixels);
        return font.width(value) <= limit ? value : font.plainSubstrByWidth(value, limit - 8) + "…";
    }
    private static List<String> wrap(String text, int maxChars) {
        List<String> result = new ArrayList<>(); StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            if (!line.isEmpty() && line.length() + word.length() + 1 > maxChars) { result.add(line.toString()); line.setLength(0); }
            if (!line.isEmpty()) line.append(' '); line.append(word);
        }
        if (!line.isEmpty()) result.add(line.toString()); return result;
    }
}
