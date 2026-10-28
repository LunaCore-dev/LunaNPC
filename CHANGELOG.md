# LunaNPC Changelog

Every entry starts with one tag, in this order: [Added], [Fixed], [Changed], [Removed], [Other].

Online: https://lunacore.dev/changelog?product=LunaNPC · API reference: https://api.lunacore.dev/LunaNPC ·
Report a bug: https://lunacore.dev/LunaNPC/reports

## 0.2.0

- [Added] **Patrol job**: the NPC walks a saved route in a loop, back and forth or in random order, picked from weighted route variants. After a fight it carries on from the nearest point; works on placed NPCs only.
- [Added] **Route Creator** stick: click the ground to place up to 50 points and sneak-click to finish and name the route. Its menu shows, renames, deletes and edits routes (point order, wait time, facing and speed), and one route can serve many NPCs.
- [Added] **Bodyguard job**: guards the closest of its chosen players, alliances or NPCs, or whoever hires it with a right-click for an item price and a hire time. It stays close and fights whoever hurts its protectee, optionally also what the protectee attacks.
- [Added] Bodyguard rules for retreating from far-off fights, teleporting to catch up (also across dimensions), protecting a player through death and respawn, and staying, going home or despawning when it gives up.
- [Added] Bodyguard friendly fire switch: when off, the ones it guards can't hurt it by accident.
- [Added] Bodyguards can be hired through two new dialog actions (Give bodyguard, Order bodyguards) and `/lunanpc bodyguard assign|dismiss|order`. The hirer can tell it to follow, stay, go home or leave.
- [Added] **Chunk Loader** trait: keeps the chunks around the NPC loaded, so a patrol or bodyguard keeps going with no player nearby, also after a restart.
- [Added] **Download Skin** in the Model picker: download a player's skin once by name or URL, and every player sees it, in singleplayer too. It is kept with the world, so later skin changes or a deleted account never affect it.
- [Added] Skin Settings for downloaded skins: rename, switch between classic and slim arms, or delete. Arms come from the player's profile by default, and old 64×32 skins are converted.
- [Added] **Edit access** per NPC, set on the General tab as "Editable by": API only, in-game only, or both. API-only NPCs show a lock in the NPC lists; NPCs from older worlds stay editable by both.
- [Added] Fixed accuracy slider: with dynamic accuracy off, it sets how accurate every shot is (0 to 100%) at any distance.
- [Added] **Target In Sight** value for advanced combat conditions (1 when the NPC sees its target, 0 behind a wall), so attacks can stop firing through walls.
- [Added] Spears are carried in the charge pose while running at the target and play their vanilla sounds. With "gear affects stats" on, a hit gains vanilla's charge bonus.
- [Added] Illagers, villagers, witches, vexes, allays and foxes can hold items, and illagers, villagers and player-shaped NPCs can wear heads, skulls and blocks. The Inventory tab enables exactly the slots a model can show.
- [Added] The Manage Permissions log shows each player's head (also for offline players) and records skin downloads, edits and deletes.
- [Added] Ops get a red chat warning when a saved NPC has a setup that can't work as configured.
- [Added] New setup warnings for patrol, bodyguard, Chunk Loader and spawn-area rules, for example a patrol without a route or a bodyguard with no attack.
- [Added] API: `RouteRegistry`, `NpcRoute`, `NpcPatrolSubType`, `NpcBodyguardSettings` and patrol and bodyguard controls on `NpcInstance`. New events: `onPatrolPointReached`, `onBodyguardLocked` and `onBodyguardReleased`.
- [Added] API: `LunaNpcApi.skins(server)` with `SkinRegistry` to download, rename, change and delete skins.
- [Added] API: `NpcEditAccess` with `editAccess()` / `setEditAccess` on `NpcDefinition`. A change the NPC's access forbids throws `IllegalStateException`.
- [Added] API: `AllianceRegistry.setMatchAllPlayers` and `setFriendlyFire`, which only the editor could set before.
- [Fixed] An NPC stuck chasing a target it couldn't reach ignored players hitting it; it now turns on its attacker right away.
- [Fixed] Melee attacks landed through walls.
- [Fixed] A melee NPC below its target stayed stuck at the bottom, and one chasing across a pit walked back down into it instead of going around.
- [Fixed] Wide NPCs (big models, golems) needed gaps one block wider than their body; they now fit through any gap their body fits through.
- [Fixed] An NPC knocked off its route kept pushing at the ledge it missed instead of finding a new way.
- [Fixed] An NPC that gave up on a target never chased it again while it stayed in view; it now retries once its give-up time has passed.
- [Fixed] Left-clicking an item slot in any menu cleared the slot instead of opening it.
- [Fixed] NPCs routing around a wall kept turning back to their target and edged slowly along the wall.
- [Fixed] NPCs hopped constantly while brushing past walls, which slowed them to a crawl.
- [Fixed] Fast NPCs circled around corners and obstacles while chasing instead of rounding them.
- [Fixed] Alliance changes made through the API didn't reach connected players right away.
- [Changed] LunaNPC now runs on Minecraft 26.3 (was 26.2).
- [Changed] Attack speeds faster than 0.5s really hit that fast, and a changed attack speed applies on the very next attack.
- [Changed] Crossbows load once, stay raised and reload right after each shot, like a pillager, and bows stay drawn for the whole fight.
- [Changed] Switching between close and long range needs a clear margin and holds for a moment, so the NPC no longer flips weapons at the boundary.
- [Changed] Armed illagers use their vanilla weapon poses (crossbow charge and hold, bow aim, attack swing).
- [Changed] The Main Hand and Ranged Weapon slots explain what they do: the main hand is the melee weapon, the ranged weapon is held at long range and sets the shot style.
- [Changed] Items that don't fit in a full inventory (trades, quest and dialog rewards) drop at the player's feet instead of toward the NPC.
- [Changed] API: `NpcRegistry.create` and `getOrCreate` now take an `NpcEditAccess`. Pass `NpcEditAccess.SHARED` to keep the old behavior.
- [Changed] API: `NpcJobSettings`, `NpcTraitsSettings` and `NpcProjectileSettings` have new components, so code calling their constructors must be updated. The `with…` methods are unaffected.
- [Other] Mod and API version 0.2.0.
