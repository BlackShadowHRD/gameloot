# Changelog

All notable changes to POILoot are documented in this file.

## 0.1.0-SNAPSHOT

### Added

- Native Paper Brigadier `/poiloot` command tree registered through the plugin
  lifecycle API.
- Permission-aware `/poiloot` help output.
- `/poiloot version` for plugin name and version reporting.
- `/poiloot register <loot-table>` for registering targeted chests, barrels,
  and chest minecarts.
- `/poiloot inspect` for displaying registration status, ID, container type,
  loot table, and location.
- `/poiloot deregister` for removing POILoot metadata from a targeted
  container.
- `poiloot.admin` permission, granted to server operators by default.
- Brigadier completion for built-in loot-table keys.
- Persistent loot-point UUID and loot-table metadata using
  `PersistentDataContainer`.
- Duplicate-registration detection and player-facing Adventure messages.
- Private loot generation using Paper loot tables and `LootContext`.
- Private 27-slot inventories for registered block containers and chest
  minecarts.
- Interaction handling that prevents access to a registered physical
  container.

### Changed

- Admin-only Brigadier branches are hidden from senders without
  `poiloot.admin`.
- Invalid-target guidance now uses the concise message: `Look at a supported
  container within 6 blocks.`

### Fixed

- Loot-table validation now queries the server's loaded loot tables, allowing
  valid datapack-defined keys as well as built-in keys.

### Not yet implemented

- Per-player claim tracking.
- One-time loot distribution.
