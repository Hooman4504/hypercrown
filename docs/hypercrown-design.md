# Hypercrown balance and server rules

## Runtime target

This implementation targets Minecraft 1.21.1, NeoForge 21.1.264, and Java 21. All ability requests arrive as client packets and are checked and executed on the logical server. The client HUD is informational; it is not authoritative.

## Crown progression

The Hypercrown is forged once per world (the world record is saved in `hypercrown_world`). Craft three Crown Fragments into an Unawakened Crown, defeat the Ender Dragon, Wither, and Elder Guardian within 128 blocks to earn each boss credit, then use the Unawakened Crown on the Dragon Egg in the End while carrying 50 XP levels. The ritual consumes the egg and costs the player all 50 levels, leaves a noncreative player at one health and one food point, and records the current owner.

The Hypercrown retains its original strong ability package. The separate Crown of Tactics is the balanced alternative; its passive powers are not granted by default. Use a crafted gem while wearing the Crown of Tactics; the gem is consumed and the unlock is saved on that crown:

| Gem | Passive | Limit |
| --- | --- | --- |
| Strider | Movement speed | +5% |
| Bulwark | Damage reduction | 10% |
| Edge | Attack damage | +8% |
| Gravity | Knockback resistance | +25% |

Recipes are deliberately costly and the four passives are independent, so the server can pace progression by controlling access to the ingredients. Other legacy passives (flight, immunity, invincibility frames, instant mining, and similar effects) are not socketable and remain unavailable.

## Active abilities

All cooldowns use the overworld game clock and player attachment data, so item swapping, death, logout, dimension changes, and server restarts do not reset them. Cooldowns have code-enforced minimums; config values may lengthen them but not shorten them.

| Ability | Activation and exact effect | Cooldown | Counterplay |
| --- | --- | --- | --- |
| Hyperstep (Crown of Tactics only) | Press C for a short look-directed velocity burst (strength 1.05); no teleport, damage, fall protection, or collision bypass. | 12 seconds | Block the line, force it to be spent early, then pursue; terrain and fall hazards still matter. |
| Momentum | A successful non-explosive hit against another living entity grants 1 stack no more than once every 2 seconds; maximum 3. After 5 seconds without a successful hit, 1 stack decays each second. | Resource, not an ability cooldown | Avoid feeding hits, disengage until it decays, or pressure while the owner has fewer than 3 stacks. |
| Hyperbreak | Press B with 3 stacks to consume all of them and prime the next non-explosive player-attributed hit for 5 seconds. That hit gains at most 25% and at most 3 raw damage, before normal armor and invulnerability handling. One hit consumes it. | 30 seconds | Disengage or block during the 5-second tell; armor still applies and the bonus cannot be stacked across a rapid-hit attack. |
| Crown Guard | Press N to activate a 4-second window with a shared pool of 6 raw damage absorbed across hits. It does not cancel a hit that exceeds the remaining pool. | 35 seconds | Coordinate repeated hits, bait the activation, or use sustained pressure to exhaust its pool. |
| Tactical Awareness | Press M to pulse once for the nearest visible, non-invisible player within 16 blocks. Gives only an approximate eight-way direction, never coordinates or through-wall tracking. | 15 seconds | Break line of sight, use stealth/invisibility, or approach outside the radius. The wearer and nearby players receive audible/visual activation feedback. |

Hypercrown keys V, H, G, and J each select between two powers. Hold Alt and press a key to switch its selection; press the key normally to activate the displayed selection. V toggles between Blink and Phase Dash (an 8-block collision-checked directional teleport, 18-second cooldown); H toggles between Anchor and Time Snare (applies Slowness II for 4 seconds to non-allied living entities within 8 blocks, 30-second cooldown); G toggles between Energy Beam and Chain Lightning (strikes up to four visible targets, 24-block initial range and 8-block chaining range, 12 damage each, 25-second cooldown); J toggles between Weather Control and Crown Nova (10 damage and knockback within 7 blocks, 35-second cooldown). Damage is multiplied by the server power scale and capped at 40 for Chain Lightning and 30 for Nova. All cooldowns are tracked independently and persist like the original active abilities.

The compact crown HUD is anchored to the bottom-left, scaled to 80%, and stacks its ability rows vertically above the hotbar to avoid covering it. The Hypercrown title has a continuously changing rainbow hue and a subtle per-letter wave; its rows show each selected power and cooldown. Tactical Crown uses the same compact placement without the animated title.

The craftable Crown Field Manual is a right-click reference that prints controls and detailed descriptions of the Hypercrown's original passives, all original and alternate active powers, and the Crown of Tactics' abilities and gem effects. Successful active ability use and ability selection each provide an audible cue.

The Crown of Tactics is a separate, balanced item: it provides 2 helmet armor and has Hyperstep, Momentum, Hyperbreak, Crown Guard, Tactical Awareness, and optional gem sockets. It is crafted from five gold ingots. The Hypercrown retains its original powerful state: 10 armor, 5 toughness, flight, its full passive package, Blink, Anchor, Energy Beam, and Weather Control, with the four additional alternate powers above.

## Ownership and recovery

The overworld SavedData record tracks the single current owner and canonical crown UUID; persistent owner and crown-ID components must agree with that record for server abilities to work. The default permits a transfer only after a player kill drops the crown: the killer becomes the recorded owner, while the item remains a normal death drop to be recovered. Environmental deaths leave ownership with the dead player so the item can be recovered. `keepOnDeath` can instead return the item to its owner on respawn; in that mode it will not transfer on death. The item does not despawn or burn and is recovered from below the world rather than destroyed.

The server record and item components survive normal restarts. `/hypercrown status` shows the forge count; the existing operator-only `/hypercrown reset` is an administrative reset and invalidates the current ownership and crown-ID records, so use it only as a deliberate recovery action after securing/removing any old crown. Old worlds migrate the current owner from the saved forger list and claim an ID for the first legacy crown checked by the server.

There is no general NeoForge API for inspecting every third-party container or every region-protection plugin. Bare creative/command crowns are inert by default, and the canonical UUID makes old/reset or mismatched items inert. However, an operator or a third-party duplication exploit that copies the full owner and crown-ID components can make indistinguishable copies; this implementation does not claim cryptographic item uniqueness or reliable deduplication across arbitrary containers. A production server requiring that guarantee needs a server-specific inventory/region integration and an audited item-instance ledger.

## Protection integration and known limits

This repository has no region-protection API dependency. Awareness is limited to direct line of sight, but active abilities are not currently blocked by third-party protected-region rules. Connect the server's protection plugin/mod to the ability and damage handlers before enabling the crown in protected PvP areas. Do not infer region safety from the global `allowTransfer` or `crownEnabled` settings.

The ritual is a final contest at the Dragon Egg and requires each credited boss kill, but this code does not enforce team-neutral scheduling, claim windows, offline-owner rules, or a server-wide challenge announcement before the ritual. Those are server policy decisions and need a server-specific event/claim module.

## Balance test plan

No automated combat simulation or multiplayer balance run is included. Before production, test the same armor/enchantment tier and record time-to-kill, total damage absorbed, cooldown use, and escape success in repeated fights:

1. Melee specialist: Hyperbreak primed versus shield use, strafing, and a baited Guard.
2. Ranged specialist: bow/crossbow/trident pressure, blocked sightlines, projectile attribution, and Hyperstep escape potential.
3. Mobility specialist: repeated pursuits on open ground and uneven terrain; verify no fall protection or collision bypass.
4. Defensive specialist: high-quality armor, shield, Resistance effects, and the maximum Hyperbreak bonus.
5. Coordinated team: simultaneous hits against the six-damage Guard pool and focus fire during cooldowns.
6. Modded equipment: rapid-hit, area-damage, attributed projectiles, unusually large damage values, and external attribute modifiers; confirm explosions do not trigger Hyperbreak or Momentum.
7. Exploit checks: swap helmets, die, relog, change dimensions, restart, duplicate a command-created crown, copy a forged crown's full components, and attempt simultaneous transfers.

Tune only upward cooldowns or downward caps if fights show the crown deciding engagements too often. If it rarely changes outcomes, first shorten downtime cautiously rather than adding permanent damage, health, or immunity.
