# Changelog

All notable changes to Bronzeman PVP TCG. Versions link to their downloadable jar on the
[releases page](../../releases).

## [1.4.1] — 2026-08-02

### Added
- Locked items can no longer be **bought from the Grand Exchange**: selecting one in the GE search
  results is blocked until you own its card. Best effort — the search results are drawn in the
  chatbox, so a purely keyboard-driven flow can still reach the offer screen.
- **Lightbearer** restored. It carries no equipment bonuses so the zero-stat sweep removed it, but
  its special-attack regeneration makes it worth collecting.

## [1.4.0] — 2026-08-02

### Added
- **Blessed spirit shield** and a card for **every enchanted bolt** — all ten gem types in both
  regular and dragon form (20 cards), built from wiki data.
- Aliases so the in-game **Rune crossbow** matches its card (catalogued as "Runite crossbow"), and
  so the poisoned **Dragon dagger** and **Abyssal dagger** grades map to their base card.

### Removed
- Items the wiki shows with **no equipment bonuses at all** (180 cards) — naval and tuxedo outfits,
  cavaliers, tricorns, novelty slippers, banners, utility bracelets and rings, and similar. **Boaters
  are kept** by request. Cards whose wiki page carries no bonus block at all (merged family cards
  and diary rewards) are kept, since missing data is not proof of zero stats.
- The remaining unenchanted gem **amulets**, leaving only enchanted neckwear.

## [1.3.0] — 2026-08-02

### Fixed
- Item **variants no longer bypass the equip lock**. Previously anything whose in-game name did not
  match a card exactly — `Accursed sceptre (a)` / `(au)`, `Ring of suffering (i)` / `(ri)`, locked
  `(l)` untradeables such as void and fire/infernal capes, and ornament-kitted gear like
  `Amulet of fury (or)` — counted as untracked and could be worn without the card. Item names are
  now resolved by peeling one variant marker at a time (brackets, degradation counters, and
  `Imbued`/`Superior`/`Corrupted` prefixes) until a card matches.

### Added
- Cards can carry an `aliases` list, so one card can cover several in-game items.

### Changed
- Bundled onto single cards (1,191 → 1,167): the six god **vestment** mitres, robe tops, robe legs
  and cloaks each collapse to one card; the three **Mage Arena** capes to one, and the three
  **imbued Mage Arena** capes to another.

## [1.2.0] — 2026-08-02

### Added
- A **Patreon** support button at the bottom of the Overview tab.

## [1.1.2] — 2026-08-02

### Changed
- The **Hard mode** and **Defence level** explanations are now hover tooltips rather than text
  printed in the panel, keeping the Overview tab compact. The Defence tooltip still shows the live
  pool count, and the Hard mode tooltip spells out both payout rates.

## [1.1.1] — 2026-08-02

### Added
- A short explainer under the **Defence level** dropdown describing what the setting does, with a
  live count of how many cards the current level leaves in the pool.

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
