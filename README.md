# Bronzeman PVP TCG

A fork of [OSRS TCG](https://github.com/Azderi/osrs-tcg) that turns the card game into a
bronzeman-style PvP challenge:

- **Equipment-only card pool** — the catalog is curated down to ~1,190 equipable weapons, armour and
  jewellery (no monsters, resources, consumables, event, skilling or novelty items).
- **PvP-only economy** — credits are earned exclusively through PvP kills; one kill pays for exactly
  one booster pack. PvM kills, raids and skilling award nothing. Fresh profiles start with one pack.
- **Hard mode** — optional: a kill pays 250 credits per 100k of loot value instead, so a pack costs
  1M in loot.
- **Defence level** — set 1 / 20 / 30 / 40 / 50 / 60+ and any card for gear you could not equip at
  that level is removed from the roll pool, so a pure never rolls armour it cannot wear. Equip
  requirements come from the [OSRS Wiki](https://oldschool.runescape.wiki/).
- **Bronzeman equip lock** — always on: an item cannot be equipped until you own its TCG card, and
  locked items render greyed out in the inventory, bank and shops. Tutorial Island and LMS are
  exempt so the game can't soft-lock you.

Card trading through the party plugin is unchanged from upstream.

## Installing

Grab the jar from the [latest release](../../releases/latest) (or build it yourself with
`gradlew jar`) and drop it into RuneLite's sideload folder:

```
%USERPROFILE%\.runelite\sideloaded-plugins\
```

On macOS and Linux that path is `~/.runelite/sideloaded-plugins/`. Create the folder if it does not
exist. Sideloaded plugins are only read at startup, so RuneLite must be restarted after copying the
jar — and it must run with `--developer-mode`.

### Standard RuneLite account

Launch the client with the developer-mode flag. Everything after `--` is passed to the client:

```
"%LOCALAPPDATA%\RuneLite\RuneLite.exe" -- --developer-mode
```

### Jagex Launcher account

The Jagex Launcher starts RuneLite itself, so the flag goes in the launcher's own configuration
rather than on a command line. This needs RuneLite launcher 2.6.3 or newer.

1. Open **RuneLite (configure)** from the Start menu (on macOS/Linux, run the launcher with
   `--configure`).
2. Put `--developer-mode` in the **Client arguments** box and save.
3. Start RuneLite from the Jagex Launcher as usual. It now loads sideloaded plugins while still
   logging in with your Jagex account.

That is all most people need. Only if you want to start `RuneLite.exe` **directly**, without going
through the Jagex Launcher at all, do the following as well:

1. Add `--insecure-write-credentials` to the same **Client arguments** box.
2. Launch once through the Jagex Launcher. RuneLite writes your session to
   `.runelite/credentials.properties`.
3. From then on you can run the client directly and it will reuse that session:

```
"%LOCALAPPDATA%\RuneLite\RuneLite.exe" --insecure-write-credentials -- --developer-mode
```

> **Never share `credentials.properties`.** It lets anyone log into your account without your
> password. Delete it when you are done, and use the *End sessions* option on runescape.com if you
> think it has leaked.

See [CHANGELOG.md](CHANGELOG.md) for what changed in each release.

## Credits

This fork stands on two plugins — all credit for the underlying systems goes to their authors:

- **[OSRS TCG](https://github.com/Azderi/osrs-tcg)** by [Azderi](https://github.com/Azderi) and
  contributors — the entire card game: collection, packs, rarities, trading, persistence and UI.
  Support Azderi's projects on [Patreon](https://www.patreon.com/Azderi).
- **[Bronzeman TCG](https://github.com/Felmeme/bronzeman-tcg)** by
  [Felmeme](https://github.com/Felmeme) — the bronzeman restriction concept and the
  equip-blocking design this fork's card-gated equipment lock is ported from.

Card data and images come from the [OSRS Wiki](https://oldschool.runescape.wiki/).

## Disclaimer

This plugin is a fan-made minigame for fun only. Cards have no real-world or in-game monetary value
and are not intended to be bought, sold, or traded for real money, bonds, gold, items, or any other
goods or services.

Do not pay for cards or collections, and do not accept payment from others for them. If someone
offers to sell you cards or asks you to pay for theirs, decline and report them if appropriate.

Trading cards with other players is done at your own risk.
