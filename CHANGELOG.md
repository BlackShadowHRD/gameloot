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
- In-memory per-player claim tracking keyed by player and loot-point UUID.
- Stable loot sessions that preserve remaining items until the first item is
  taken and the inventory is closed.
- Custom inventory holders and guarded click/drag handling for private loot
  inventories.
- SQLite schema management with automatic versioned migrations.
- Persistent loot-point registration records linked to world targets by their
  PDC UUID markers.
- Persistent per-player claims with an in-memory cache for synchronous gameplay
  checks.
- Repository integration tests covering schema creation, registration CRUD,
  duplicate handling, claims, cascading deletion, and compensation restoration.

### Changed

- Admin-only Brigadier branches are hidden from senders without
  `poiloot.admin`.
- Invalid-target guidance now uses the concise message: `Look at a supported
  container within 6 blocks.`
- Loot-point metadata is now authoritative in SQLite; targets retain only their
  loot-point UUID in PDC.
- Registration, deregistration, inspection, and interaction now use repository-
  backed persistence services.
- Claim writes run on a dedicated database executor, while Bukkit world and
  inventory operations remain on the server thread.
- The first loot transfer waits for its claim to be durably persisted.
- Existing UUID and loot-table PDC registrations migrate automatically when
  inspected or interacted with.

### Fixed

- Loot-table validation now queries the server's loaded loot tables, allowing
  valid datapack-defined keys as well as built-in keys.
- Deregistration compensates for PDC-removal failures by restoring the deleted
  loot-point record and its claims.
- Inspection reports when a PDC UUID has no matching database record.

### Not yet implemented

- Session recovery after a server restart.
- Persistent generated inventory contents for unclaimed sessions.
