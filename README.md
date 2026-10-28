<p align="center">
  <img src="assets/LunaNPC_Font_Transparent.png" alt="LunaNPC" width="620">
</p>

<p align="center">
  An NPC framework for Minecraft on Fabric: build NPCs in game, or drive them from your own mod.
</p>

<p align="center">
  <a href="https://lunacore.dev/LunaNPC"><b>Download</b></a> ·
  <a href="https://api.lunacore.dev/LunaNPC">API reference</a> ·
  <a href="https://lunacore.dev/changelog?product=LunaNPC">Changelog</a> ·
  <a href="https://lunacore.dev/LunaNPC/reports">Report a bug</a> ·
  <a href="https://discord.lunacore.dev">Discord</a>
</p>

LunaNPC is a full framework for **NPC AI, factions, combat, spawning, jobs (trading, quests,
farming, patrols, bodyguards) and dialog**, built for two audiences at once:

- **Creators** build NPCs entirely in game through menus, no code needed.
- **Developers** control NPCs, factions, aggression, spawning and behaviour through a sealed Java API.

## Highlights

- **Client and server**: a real Fabric mod on both sides.
- **Fabric only**: no Paper, Spigot, Bukkit, Forge, NeoForge or Quilt. One loader, done properly.
- **Full control**: fewer features, each one fully configurable.
- **API and in-game parity**: whatever a menu can do, the API can do too.
- **No forced balance**: impossible bosses, unfair snipers and scripted raids are allowed. Balance
  is the creator's call.

## For developers

Add LunaNPC as a `compileOnly` dependency and import `net.lunacore.luna_npc.api.*`; nothing else is
public. The [API reference](https://api.lunacore.dev/LunaNPC) documents every type per version, and
[lunanpc-examples](https://github.com/raeydzone/lunanpc-examples) holds five small mods (a dungeon
monster, a biome archer, a zoned guard, two warring factions and a boss) that use it end to end.

## Building

Requires Java 25.

```
./gradlew build      # build/libs/LunaNPC.jar
./gradlew release    # the checked public build
```

## Contributing

Bugs and ideas go to [lunacore.dev/LunaNPC/reports](https://lunacore.dev/LunaNPC/reports). Code
contributions follow the [LunaCore contributor guidelines](https://lunacore.dev/guidelines/contributing.md)
and need the [Contributor License Agreement](https://lunacore.dev/legal/cla.md), which our bot asks you to sign on your
first pull request.

## License

LunaNPC is free and **source-available** under the
[LunaCore Product License 1.0](LICENSE.txt). It is not open source.

| You may, for free | You need our written permission to |
|---|---|
| Use it, including on servers you earn money with | Publish a changed version, under any name |
| Read the code and change it for your own use | Release a "successor", "2.0", fork or port |
| Share official releases unchanged and clearly labelled (modpacks, server packs), for free | Sell it, or charge for a pack that contains it |
| Fork it on GitHub to keep a copy or to contribute | Remove or hide credits and license notices |

Names and logos: see the [Branding Policy](https://lunacore.dev/legal/branding.md). Bundled skins keep their own licenses, listed
in [docs/credit_model.txt](docs/credit_model.txt).

### Requesting a license

Want to publish, sell or build a product on LunaNPC? Open a ticket at
[lunacore.dev/support](https://lunacore.dev/support) and describe what you plan. We decide case by case;
there is no fee schedule and no obligation to grant one.
