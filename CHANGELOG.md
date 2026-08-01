# Changelog

All notable changes to Bronzeman PVP TCG. Versions link to their downloadable jar on the
[releases page](../../releases).

## [1.1.0] — 2026-08-02

### Changed
- **Hard mode** and **Defence level** moved out of the RuneLite settings panel into a new
  **Game mode** section on the plugin panel's Overview tab. They persist exactly as before; the
  config entries are simply hidden from the settings list.
- The developer-mode **Debug mode** checkbox moved into the same section.

### Removed
- The **Multipliers** section (foil chance, kill credit, level-up and XP tuning) and its
  non-default trading warning. Three of those four knobs only affected the PvM and skilling credit
  paths this fork disables, so they were dead controls.

### Card pool — 1,509 → 1,191 cards
- Alchemist gear, skilling necklaces, and unenchanted necklaces, rings and bracelets.
- Crier gear, Maoma's and Saika's helms, Elven signet, Fremennik gear (kilt and diary sea boots
  kept), and one-off quest/minigame jewellery.
- The Gauntlet's in-minigame items (crystal and corrupted sceptres, crystal dagger, Gauntlet cape).
  Blade of saeldor and Bow of faerdhinen are kept — they are used outside the minigame.
- Bows below yew and crossbows below adamant, using the wiki's Ranged requirements. Dorgeshuun
  crossbow kept despite being under the line. Composite bows and Rain bow also removed.
- Culinaromancer's chest kitchen weapons, barrel novelty items, and speedrun trophies.
- Novelty Treasure Trails cosmetics: wigs, masks, joke outfits, novelty weapons and held junk.
  Boaters, cavaliers, Robin Hood hats, ranger and gilded gear are kept.
- Elegant sets, headbands, flags, goblin mail, Hallowed Sepulchre gear, HAM gear, herb tars,
  region scarfs, machetes, cutlasses, mud pie, blackjacks, Oddskull and Paddle.
- Teleport jewellery: neckwear (both glory amulets kept) and rings (Explorer's ring kept as a
  diary reward).

## [1.0.0] — 2026-08-01

First release of the fork.

### Added
- **Bronzeman equip lock**, always on: an item cannot be equipped until you own its card, and
  locked items render greyed out in the inventory, bank and shops. Tutorial Island and live LMS
  matches are exempt so the game cannot soft-lock you.
- **PvP-only economy**: credits come solely from PvP kills, one kill paying for one booster pack.
  PvM kills, raid completions, XP and level-ups award nothing. Fresh profiles start with one pack.
- **Hard mode**: a kill pays 250 credits per 100k of Grand Exchange loot value instead, so a pack
  costs 1M in loot. Partial loot counts pro rata.
- **Defence level** (1 / 20 / 30 / 40 / 50 / 60+): cards for gear you could not equip at that level
  are removed from the roll pool, so a pure never rolls armour it cannot wear. Every card carries a
  `defenceRequirement` parsed from the OSRS Wiki.
- `::btcg-pvp <loot value>` debug command to verify the award rate without a live kill.
- Bronze skull card artwork for the plugin and sidebar icons.

### Changed
- Card pool curated from 6,376 to ~1,500 equipable weapons, armour and jewellery: monsters,
  resources, consumables, event rewards, skilling gear and tools all removed.
- Achievement diary rewards collapsed into one generic card per family using the tier-4 art; the
  eight halos merged into a single Halo card; slayer helmet recolours merged into one card.
- Renamed from OSRS TCG, and fully namespaced (package, config group, storage paths, party message
  types, chat commands) so it runs alongside the Plugin Hub version of OSRS TCG without clashing.
- Welcome tab removed; the panel opens on Overview and renders while logged out.
