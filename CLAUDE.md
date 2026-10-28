# LunaNPC

LunaNPC is a Fabric-only Minecraft NPC framework by LunaCore (lunacore.dev): AI, factions,
combat, spawning, jobs, dialog and custom models, usable in game through menus and by other mods
through a sealed API. It is a real mod on client and server; it never targets Paper, Spigot,
Bukkit, Forge, NeoForge or Quilt. It is free and source-available under the LunaCore Product
License (`LICENSE.txt`), not open source. English only.

This is the only instruction file; read the code for everything else. Every contributor and agent
also follows the LunaCore contributor guidelines, https://lunacore.dev/guidelines/contributing.md
(branches, commit format, AI disclosure). Maintainers may keep personal setup notes in a gitignored
`CLAUDE.local.md`.

## Principles (they decide design questions)

- **Absolute control.** Fewer features, each done right and fully configurable. If a feature
  cannot be done right yet, it is not shipped.
- **API and in-game parity.** Everything a menu can do, the API can do. API-only is fine;
  in-game-only is never allowed.
- **Systems are building blocks.** Build composable pieces that combine freely; when in doubt
  between restricting a system to its intended use and leaving it open, leave it open.
- **The creator owns balance.** Never clamp extreme values to make them "fair".
- **Illogical setups always warn.** Any combination that cannot behave as configured gets a trigger
  in `NpcWarnings`, shown in the editor, sent to ops in red chat and returned by the API. Add one
  for every new feature without being asked.
- **One source of truth.** A rule or check needed in two places (screen and API, client and server)
  lives once and both call it.
- **Registries, not lists.** Anything that enumerates game content (biomes, items, mobs,
  structures...) reads the live registry, so other mods' content works without extra code.

## Stack and build

Minecraft 26.3 (unobfuscated: Mojang names, plain `implementation`, no remapJar), Fabric Loader and
Fabric API, Java 25, Loom with split source sets (`src/main` common/server, `src/client`
client-only). Mod id `luna_npc`, package `net.lunacore.luna_npc`. Input is SDL3 since 26.3: compare
key and mouse codes against `InputConstants`, never literals.

`./gradlew build`, `runClient`, `runServer`, `runDatagen`. `./gradlew release` is the public build:
`checkApi`, `build` and `verifyJar`, which fails on dev-only code, a missing bundled file, a version
mismatch or any AI tool name inside the jar. Ship only `build/libs/LunaNPC.jar`; it carries the
license, a readme and the skin credits (`docs/credit_model.txt`, required by those skins' own
licenses).

Release notes live only on lunacore.dev (https://lunacore.dev/changelog?product=LunaNPC), written by
the maintainer when a version ships; the repo keeps no changelog files.

## The API (`net.lunacore.luna_npc.api`)

The whole public surface is that one package; everything else is internal and free to change.
`api/package-info.java` describes it and must stay current. Keep these true on every change:

1. A type meant for consumers lives in `api`; no API signature names an internal type.
2. The API hands out only interfaces or immutable values. Records copy their collections in the
   compact constructor; registry getters return copies. `NpcInstance` is the face of a live NPC,
   `NpcDefinition` of a template; `NpcEntity` and `NpcEntry` never leak.
3. Anything new in game gets its API path in the same change. A synced settings field runs the
   whole chain: record + Codec + StreamCodec + defaults → `NpcDefinition` → `NpcEntry` →
   `NpcEntity` → `NpcSummary` → payloads → `CreateNpcScreen`.
4. The API version is the mod version (`gradle.properties`). After any API change run
   `./gradlew exportApi` and write every empty text it lists in `docs/wiki/api/<version>.json`
   (gitignored, generated for api.lunacore.dev; keep the folder when changing machines);
   `./gradlew checkApi` must pass before a push.
5. github.com/raeydzone/lunanpc-examples must still compile importing only `api`; if it needs more,
   the API has a hole.

## UI rules

- Screens extend `LunaScreen`. The world stays visible: no solid screen backgrounds; content and
  every list sit on opaque panels (`PANEL_SURFACE`), and a list embedded in a screen gets a 1px
  `PANEL_BORDER` outline. Draw panels in `extractPanels(...)`, never in `extractRenderState`, or they
  cover the widgets.
- Gold (`SECTION_COLOR`) is the only accent: section titles, selection outlines, favourites. Red is
  reserved for warnings. The one exception is the advanced-combat rule cards, tinted by trigger
  category. No decorative dividers; icons reuse font glyphs (★ ♪ ✔ ⚠).
- Reuse the existing widgets (`SimpleSelectionList`, `ReorderableList`, `PagedLogList`,
  `ItemSlotButton`, `RangeSlider`, `LabeledSlider`, the pickers, `ConfirmScreen`) before making a new
  one; custom-draw new UI instead of raw vanilla widgets, which overflow and misplace scrollbars.
- Detail screens: live preview on the left, settings on the right in two flowing columns
  (`COL_ROW_HEIGHT` 22, labels via `LunaScreen.columnLabel`, which wraps instead of overflowing).
  Tabs are 20px buttons, the active one disabled. Primary and Cancel sit as two 100×20 buttons
  centred under the content. Every filled slot and every disabled control has a tooltip; tight
  numbers are rounded, the exact value lives in the tooltip or slider.
- All visible text uses `en_us.json` keys (`gui.luna_npc.<screen>.<element>`). Chat to players goes
  only through `LunaNpcChat` (purple `[LunaNPC]` prefix, orange highlights); command feedback keeps
  vanilla styling.

## Testing

- The test mod `src/testmod` loads only with `runServer` and never ships: `/lunatest
  <setup>|status|clear` builds ready-to-test variants of a feature through the public API alone. For
  every new feature, add a setup covering its variants; an API gap it hits is a parity bug.
- `pathfinding_debug` / `combat_debug` in `run/config/luna_npc.json` turn on the debug logs.
- After a build, both the dev server and the client must run the same jar, or players are kicked
  with a `custom_payload` decoder error.

## Code

- Readable over short: names say what something is for (`calculateProjectileTrajectory`, not
  `calcProjTraj`); folders, files and classes make sense on their own.
- Code goes where it belongs: a separate file when it deserves one; only genuinely tiny helpers may
  share a file.
- Comments are one line, and only for what nobody can learn from the code itself (a Minecraft bug
  being worked around, a non-obvious reason). A longer comment only when truly needed and it cannot
  be said in one line; otherwise none.
- Commits follow the LunaCore format: one change per line tagged `[Added]`, `[Fixed]` (with the issue
  reference), `[Changed]`, `[Removed]` or `[Other]`, and `Assisted By AI Model: <model name>` as the
  last line when an AI helped. That is the only AI credit: never add `Co-Authored-By` or
  "Generated with" lines.
- Reports from players live on lunacore.dev (`https://lunacore.dev/llms.txt` documents the API).
  Public replies are short and human, never internal details; closing a report is a maintainer's call.
