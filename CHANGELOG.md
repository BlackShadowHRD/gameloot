# Changelog

All notable changes to GameLoot are documented in this file.

## 26.2-0.2.0

### Added

- Native Paper Brigadier `/gameloot` command tree registered through the plugin
  lifecycle API.
- Permission-aware `/gameloot` help output.
- `/gameloot version` for plugin name and version reporting.
- `/gameloot register <loot-table>` for registering targeted chests, barrels,
  and chest minecarts.
- `/gameloot inspect` for displaying registration status, ID, container type,
  loot table, and location.
- `/gameloot deregister` for removing GameLoot metadata from a targeted
  container.
- `gameloot.admin` permission, granted to server operators by default.
- Brigadier completion for loaded vanilla and datapack loot-table keys.
- Persistent loot-point UUID markers using `PersistentDataContainer`.
- Duplicate-registration detection and player-facing Adventure messages.
- Private loot generation using Paper loot tables and `LootContext`.
- Private 27-slot inventories for registered block containers and chest
  minecarts.
- Interaction handling that prevents access to a registered physical
  container.
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
  duplicate handling, claims, cascading deletion, compensation restoration,
  database-file migration, and loot-table namespace migration.
- Administrative claim inspection and UUID-based reset commands.
- Paper player-profile resolution for online and known offline player resets.
- Main-thread invalidation of active private sessions after successful resets.
- Repository and service tests for claim counts, reset scopes, and cache
  consistency.
- One authoritative target definition for supported containers and shelves.
- Fixed shelf registration, persistence, interaction protection, and
  all-or-nothing per-player rewards.
- Schema version 3 with nullable shelf loot tables and normalized
  `shelf_loot_items` storage.
- Central protection services for registered block containers, shelves, and
  chest minecarts.
- Protection against block breaking, explosions, piston movement, fire,
  entity-driven block changes, copper weathering, and container automation.
- Chest-minecart protection against damage, destruction, collisions, portal
  travel, inventory automation, and vanilla movement.
- Game-mode policy coverage for Survival, Adventure, Creative, and Spectator.
- Protection decision tests for piston movement, inventory transfers, and
  loot interaction by game mode.
- Read-only `/gameloot validate` command with batched world checks, database
  integrity reporting, PDC and target validation, loot-table resolution, and
  shelf-template validation.
- Machine-readable validation results distinguishing valid, warning, invalid,
  and unverified registrations.

### Changed

- Renamed the plugin, Java package hierarchy, main class, default command,
  permission, database file, and datapack namespace from POILoot to GameLoot.
- Removed the deprecated `/poiloot` root alias; `/gameloot` is now the only
  command root.
- Removed the unused legacy `de-register` subcommand; use `deregister`.
- Added compatibility migration for the former database location, stored
  `poiloot:` loot-table keys, and old PDC namespace.
- Admin-only Brigadier branches are hidden from senders without
  `gameloot.admin`.
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
- Physical target protection now applies equally to Survival, Adventure, and
  Creative players, including operators and players with `gameloot.admin`.
- Spectators retain vanilla non-mutating interaction and cannot open or claim
  GameLoot rewards.
- Creative pick-block remains available because it does not mutate the
  registered world target.

### Fixed

- Loot-table validation now queries the server's loaded loot tables, allowing
  valid datapack-defined keys as well as built-in keys.
- Deregistration compensates for PDC-removal failures by restoring the deleted
  loot-point record and its claims.
- Inspection reports when a PDC UUID has no matching database record.
- Access through the unmarked half of a double chest can no longer mutate a
  registered adjoining chest inventory.

### Not yet implemented

- Session recovery after a server restart.
- Persistent generated inventory contents for unclaimed sessions.
