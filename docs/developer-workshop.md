# Hypercrown Developer Workshop

The Developer Workshop is an operator-only, in-game authoring panel for the Hypercrown NeoForge 1.21.1 mod.

## Open the workshop

- Run `/hypercrown dev` or `/hc dev` in a world where you have permission level 2 or higher.
- Press **F8** by default. The key can be rebound under Controls → Hypercrown.
- `/hypercrown dev reload` (also `/hc dev reload`) reloads the saved world workspace.

The server checks permission on every request, including requests from the keybind. In single player, cheats/operator permission must be enabled. Multiplayer edits are sent to and saved by the server; the client never writes server files directly.

## Tabs

### Abilities

The list includes every `Ability` registered by this version of Hypercrown. Core entries can edit their enabled switch and cooldown; these update the existing `serverconfig/hypercrown-server.toml` values through NeoForge and apply immediately when their config restart type is `NONE`. The existing code-enforced minimum cooldowns for Hyperstep, Hyperbreak, Crown Guard, and Tactical Awareness still apply.

Core ability names, descriptions, damage, target rules, triggers, and visuals are stored as authoring notes. Their actual implementation remains the registered Java ability. Custom entries support `damage`, `effect`, `launch`, `heal`, and `visual` test actions, targets `self`, `nearest_enemy`, and `all_enemies`, registered mob effects, a bounded range and duration, and a chain to another custom entry. Test runs on the server. It does not create a hotkey or make the custom ability available to the crown's normal ability selector.

The `manaCost`, arbitrary trigger text, and arbitrary condition text are stored for designers. Hypercrown currently has no mana system or expression/trigger engine. A custom test ability can be tested immediately without restarting the server.

**New**, **Copy**, and **Delete** manage drafts. Delete asks for a second confirmation click. **Test** runs a registered ability through its normal server checks or runs a custom test action. **Save** writes the current workspace. Unsaved workspace edits auto-save after 30 seconds and when the screen closes. **Ctrl+S** saves immediately. Undo/redo keeps up to 20 saved workspace states for the current server session.

### Particles

Create and duplicate reusable particle presets. The pages cover registered particle ids, player/target/world position, offsets, delay, duration, interval, count, velocity, acceleration, random scatter, radius, height, color, size, alpha, animation metadata, pattern, condition, and custom path points.

Preview supports vanilla or mod **simple particle types** and `minecraft:dust` (which supports RGB and size). Parameterized particle types such as block/item particles need options data and are not previewed by this editor yet. Alpha and color animation are saved in the format but vanilla particle packets do not apply them. The supported preview conditions are `always`, `player_sprinting`, `player_sneaking`, `target_exists`, and `target_distance:<blocks>`.

Previews execute on the server, with at most eight active preview jobs, 24 particles per server tick per job, and 120 particles total per preview. Duration is capped at 200 ticks. Supported shapes are sphere, cylinder, cone, line, ring, and path. A path is semicolon-separated triples such as `0,0,0;1,1,0;0,2,0`.

### Sounds

Sound ids resolve through Minecraft's sound event registry, including loaded mod and resource-pack sound events. Fields include volume, pitch, intended distance, trigger label, attachment, world position, volume/pitch variance, a comma-separated sound pool, and an optional sequence.

Sequence steps use `sound_id@delay_ticks@volume@pitch`, separated by semicolons, for example:

```text
minecraft:block.note_block.pling@0@0.8@1.2;minecraft:block.note_block.bell@8@0.7@0.9
```

At most 15 steps per preview and 128 queued sounds per server are accepted. The stored distance is descriptive; actual attenuation follows the sound event and Minecraft's sound engine. Custom audio files must be added by a resource pack with a `sounds.json` definition, then referenced by that registered sound event id.

### Config

This tab reads the live `HypercrownConfig.SPEC`, so the displayed rows are generated from the mod's actual config definitions rather than a separate hard-coded list. It shows category, value, default, type, range, comment, and restart type. Use the category selector and Search field to filter settings. Booleans use a toggle; numeric and text settings use a validated input.

Before a config edit, import, or reset, the workshop saves a timestamped JSON snapshot of all current server config values in the world workspace. **Restore latest backup** restores the most recent snapshot. Each setting is validated by its NeoForge `ValueSpec` before anything is changed; accepted values are written through `ModConfigSpec.save()`. Config export copies a JSON object keyed by paths such as `tuning.powerScale`; import applies recognized paths and ignores unknown paths. Reset All requires a second click.

Restart state is taken from NeoForge. `NONE` applies immediately. A setting marked `WORLD` or `GAME` requires its indicated restart.

### Debug and Logs

The debug toggle adds a compact HUD panel with dimension, position, crown state, and the last server snapshot's active preview/job counts. The log tab shows recent developer tool actions and errors recorded for this world session. It is not a viewer for the full Minecraft or mod logger output.

## Workspace files and compatibility

Each world stores its workspace at:

```text
<world>/hypercrown/developer-workspace.json
```

The previous saved workspace is copied to `developer-workspace.json.bak` before each save. The JSON has a required `schemaVersion` field; the current version is `1`. Unknown JSON fields are ignored when loaded, and future schema versions are rejected with an in-game error instead of being overwritten.

Use **Export workspace** to copy the whole workspace JSON to the clipboard. **Import workspace** validates the clipboard JSON and merges in any registered core abilities missing from that export. The same JSON can be shared between worlds running compatible Hypercrown versions. The Config tab's JSON export/import is separate and contains values rather than ability/effect drafts.

Example content: [developer-workspace.json](examples/developer-workspace.json).

## Format overview

- `abilities[]`: `id`, `name`, `description`, `core`, `active`, `enabled`, `cooldownSeconds`, `manaCost`, `damage`, `range`, `durationTicks`, `trigger`, `condition`, `target`, `action`, `effect`, `particle`, `sound`, and `chain`.
- `particles[]`: `id`, `name`, `particle`, `position`, `x/y/z`, `delayTicks`, `durationTicks`, `intervalTicks`, `count`, `vx/vy/vz`, `speed`, `accelerationX/Y/Z`, `randomness`, `radius`, `height`, `size`, `red/green/blue/alpha`, `colorAnimation`, `shape`, `condition`, and `path`.
- `sounds[]`: `id`, `name`, `sound`, `volume`, `pitch`, `distance`, `trigger`, `attachment`, `x/y/z`, `volumeVariance`, `pitchVariance`, `soundPool`, and `sequence`.
- `configBackups[]`: timestamped JSON value maps produced before live config changes.

IDs are unique lowercase strings containing letters, digits, `.`, `_`, or `-`, up to 48 characters. Each list is capped at 64 entries and imported workspaces at 30,000 characters. Numeric values are bounded during normalization and config values are checked against their registered type/range.
