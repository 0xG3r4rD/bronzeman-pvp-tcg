# Bronzeman PVP TCG

A personal fork of [OSRS TCG](https://github.com/Azderi/osrs-tcg) that turns the card game into a
bronzeman-style PvP challenge:

- **Equipment-only card pool** — the catalog is curated down to ~1,800 equipable weapons, armour and
  jewellery (no monsters, resources, consumables, event or skilling items).
- **PvP-only economy** — credits are earned exclusively through PvP kills; one kill pays for exactly
  one booster pack. PvM kills, raids and skilling award nothing. Fresh profiles start with one pack.
- **Bronzeman equip lock** — always on: an item cannot be equipped until you own its TCG card
  (Tutorial Island and LMS are exempt so the game can't soft-lock you).

Card trading through the party plugin is unchanged from upstream.

## Sideloading

This fork is not on the Plugin Hub. Build the jar and drop it into RuneLite's sideload folder, then
start RuneLite with `--developer-mode`:

```
gradlew jar
copy build\libs\bronzeman-pvp-tcg-0.18.0.jar %USERPROFILE%\.runelite\sideloaded-plugins\
```

## Credits

This fork stands on two plugins — all credit for the underlying systems goes to their authors:

- **[OSRS TCG](https://github.com/Azderi/osrs-tcg)** by [Azderi](https://github.com/Azderi) and
  contributors — the entire card game: collection, packs, rarities, trading, persistence and UI.
  Support Azderi's projects on [Patreon](https://www.patreon.com/Azderi).
- **[Bronzeman TCG](https://github.com/Felmeme/bronzeman-tcg)** by
  [Felmeme](https://github.com/Felmeme) — the bronzeman restriction concept and the
  equip-blocking design this fork's card-gated equipment lock is ported from.

Acknowledgments carried over from upstream:

- [Monster Monitor](https://runelite.net/plugin-hub/show/monster-monitor) NPC kill credit tracking
- [Customizable XP Drops](https://runelite.net/plugin-hub/show/customizable-xp-drops) XP drop value tracking

Card data and images come from the [OSRS Wiki](https://oldschool.runescape.wiki/).

## Disclaimer

This plugin is a fan-made minigame for fun only. Cards have no real-world or in-game monetary value
and are not intended to be bought, sold, or traded for real money, bonds, gold, items, or any other
goods or services.

Do not pay for cards or collections, and do not accept payment from others for them. If someone
offers to sell you cards or asks you to pay for theirs, decline and report them if appropriate.

Trading cards with other players is done at your own risk.
