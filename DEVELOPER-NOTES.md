# GameLoot Developer Notes

This document is a technical handover for developers continuing work on
GameLoot. It describes the architecture and behaviour of the
`0.1.0-SNAPSHOT` development version.

## Project overview

GameLoot is a Paper 26.2 plugin that turns supported Minecraft containers into
loot points. Each player can receive a private, one-time loot roll from each
registered point. The physical container is never used to hold or present the
generated loot.

Current target types are chests, trapped chests, barrels, every oxidation and
waxed copper chest variant, every shulker box colour, chest minecarts, and
shelves. `LootPointTargetType` is the authoritative list and exposes each
target's persisted type, loot mode, and display name. `LootPointTargetResolver`
is the only Bukkit block/entity resolver; do not add target checks elsewhere.

The project uses:

- Java 25;
- Paper API 26.2;
- Gradle Kotlin DSL;
- Paper's Brigadier command API;
- Adventure Components for player-facing messages;
- SQLite for loot-point registrations and claims;
- Persistent Data Containers for the UUID link between a world object and its
  database row.

## Build and local development

Build and run all tests with:

```bash
./gradlew clean build
```

Start a local Paper 26.2 development server with:

```bash
./gradlew runServer
```

Build artifacts are written beneath `build/libs`. Generated files beneath
`build` must not be edited.

The production plugin does not bundle an SQLite JDBC driver. Paper 26.2
provides the driver at runtime. The pinned Xerial dependency in
`build.gradle.kts` is `testRuntimeOnly` so repository tests can run outside a
Paper server.

## Source layout

```text
io.github.blackshadowhrd.gameloot
├── GameLootPlugin.java
├── command
│   └── GameLootCommand.java
├── database
│   ├── DatabaseException.java
│   └── DatabaseManager.java
├── inventory
│   └── PrivateLootInventoryHolder.java
├── listener
│   ├── LootPointInteractionListener.java
│   ├── LootPointProtectionListener.java
│   └── PrivateLootInventoryListener.java
├── model
│   ├── LootPoint.java
│   └── LootPointType.java
├── repository
│   ├── ClaimRepository.java
│   ├── LootPointRepository.java
│   └── model
│       ├── ClaimRecord.java
│       ├── LootPointDeletion.java
│       └── LootPointRecord.java
├── service
│   ├── ClaimService.java
│   ├── ClaimAdministrationService.java
│   ├── LootGenerationService.java
│   ├── LootPointLookupService.java
│   ├── LootPointTargetResolver.java
│   ├── LootPointPersistenceService.java
│   ├── LootPointProtectionPolicy.java
│   ├── LootPointProtectionService.java
│   ├── LootPointRegistrar.java
│   ├── LootSessionService.java
│   ├── MutableLootPointTarget.java
│   ├── PrivateInventoryService.java
│   ├── ShelfRewardService.java
│   └── ShelfRewardTemplate.java
└── target
    ├── BlockLootPointTarget.java
    ├── EntityLootPointTarget.java
    ├── LootPointInspection.java
    ├── LootPointResolution.java
    └── LootPointTarget.java
```

Repository integration tests are in
`src/test/java/io/github/blackshadowhrd/gameloot/repository`.

## Architecture and responsibilities

### Plugin lifecycle

`GameLootPlugin` is the composition root. It creates the database manager,
repositories, services, command tree, and listeners using constructor
injection. It deliberately contains no gameplay or SQL logic.

Startup order is important:

1. Create and initialise `DatabaseManager`.
2. Apply schema migrations.
3. Construct repositories.
4. Load all loot points and claims into their service caches.
5. Construct gameplay services.
6. Register listeners.
7. Register the Brigadier command tree through
   `LifecycleEvents.COMMANDS`.

If database initialisation, migration, or initial cache loading fails, the
plugin logs a severe error and disables itself before listeners become active.

During shutdown, `DatabaseManager` stops accepting new work, drains its
single-thread executor for up to ten seconds, and logs if work cannot be
completed within that timeout.

### Domain and target models

`LootPoint` is immutable domain data containing the loot-point UUID,
loot-table key, and `LootPointType`. It has no persistence or mutable Bukkit
state.

`LootPointTarget` associates a resolved `LootPoint` with its physical Bukkit
block or entity and exposes its location. `BlockLootPointTarget` and
`EntityLootPointTarget` are the public immutable implementations.

`MutableLootPointTarget` is an internal service-boundary abstraction used when
registration code must alter PDC data. It isolates block-state and entity PDC
differences from the registrar.

### Database layer

`DatabaseManager` owns:

- the JDBC URL for `plugins/GameLoot/gameloot.db`;
- SQLite driver loading;
- connection configuration;
- schema creation and migration;
- a dedicated single-thread persistence executor;
- controlled blocking startup access;
- shutdown and queue draining.

Every operation receives a fresh connection. Every connection enables:

```sql
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;
```

`LootPointRepository` owns SQL for registrations. It supports loading,
lookup, insert, transactional deletion, and compensation restoration.

`ClaimRepository` owns SQL for claims. Claim insertion uses
`INSERT OR IGNORE`, making the composite claim key atomic and duplicate-safe.

SQL and JDBC types must remain inside the database and repository packages.
Commands, listeners, inventory code, and loot generation must not execute SQL.

### Persistence services

`LootPointPersistenceService` fronts `LootPointRepository` and maintains the
in-memory loot-point cache. Normal inspection and interaction resolve UUIDs
from this cache, avoiding database reads in gameplay handlers. It also
coordinates idempotent legacy-record insertion.

`ClaimService` fronts `ClaimRepository` and holds claim states keyed by player
UUID and loot-point UUID. All persisted claims are loaded at startup. Pending
and failed writes also remain fail-closed in this cache, preventing a database
error from allowing another roll during the same server process.

`ClaimAdministrationService` coordinates administrative resets. It delegates
SQL-backed cache changes to `ClaimService`, then returns to the main thread to
invalidate affected `LootSessionService` sessions before completing the reset.

### Registration and lookup

`LootPointLookupService` is the only service responsible for:

- finding the block or entity a player is targeting within the configured
  distance;
- deciding whether it is supported;
- reading and validating PDC UUID markers;
- resolving cached authoritative database metadata;
- checking that database target metadata matches the physical world object;
- migrating legacy UUID plus loot-table PDC registrations;
- creating inspection results.

PDC parsing must not be duplicated in commands or listeners.

`LootPointRegistrar` owns changes to registration state. It coordinates the
database operation and the main-thread PDC operation, including compensating
actions when one side fails.

### Loot generation and inventories

`LootGenerationService` resolves namespaced keys through
`Registry.LOOT_TABLES` and generates items with Paper's loot API and a
`LootContext`.

Shelves bypass loot generation and private sessions. `ShelfRewardTemplate`
captures and restores exact `ItemStack` bytes. `ShelfRewardService` plans an
all-or-nothing storage result and coordinates durable claims and delivery.

`PrivateInventoryService` creates and opens Bukkit inventories. It does not
generate loot, track claims, or manage sessions.

`LootSessionService` owns in-memory player/loot-point sessions. A session is
generated once and preserves its remaining inventory when reopened before the
first item is taken. Sessions are intentionally not persisted.

`PrivateLootInventoryHolder` provides a robust identity for GameLoot inventory
views. Never identify private inventories only by their title.

### Listeners

`LootPointInteractionListener` handles right-click interaction with supported
blocks and chest minecarts. It cancels access to marked physical containers,
resolves the loot point through `LootPointLookupService`, checks the claim
cache, reopens an active session or generates a new one, and opens the private
inventory.

`PrivateLootInventoryListener` protects private inventories from item
insertion and handles loot removal. It rejects placing, dragging,
shift-clicking, collection, and hotbar-style manipulation that could introduce
player-owned items into the top inventory.

`LootPointProtectionService` is the central boundary for deciding whether a
supported physical block, entity, or inventory is registered and protected.
It delegates supported-type and PDC-marker handling to the existing lookup and
resolver services. Inventory-holder traversal, including double chests and
storage minecarts, is kept here rather than repeated by listeners.

`LootPointProtectionListener` protects registered blocks from breaking, the
affected-block lists of entity and block explosions, piston movement, fire,
entity-driven block changes, copper block-form changes, and hopper or
hopper-minecart inventory transfers. It
also protects registered chest minecarts from damage, destruction, collisions,
portal travel, and rail/physics movement. The interaction listener uses the
same protection service for direct access and double-chest access.

Physical protection does not inspect game mode, operator status, or command
permissions, so Survival, Adventure, and Creative players receive identical
protection enforcement. The interaction listener deliberately passes Spectator
interactions through to vanilla and does not open or claim GameLoot rewards.
Creative pick-block remains untouched because it does not mutate the world
target.

High-frequency protection checks read only the live PDC marker after the
authoritative resolver accepts the target type. They do not submit database
work. A supported object with a GameLoot marker is protected fail-closed even
when the marker or database record is inconsistent; normal interaction and
inspection resolution provide the detailed diagnostic.

The first valid loot transfer is held until the asynchronous claim insert
succeeds. Once persistence completes, the transfer is performed on the main
thread. If the view was closed while waiting, the item is delivered to the
player inventory and overflow is dropped rather than deleted. A failed claim
write blocks access and leaves the generated session from becoming safely
rerollable.

### Commands

`GameLootCommand` builds this Brigadier tree:

```text
/gameloot
├── version
├── register
│   ├── <loot-table>
│   └── shelf
├── deregister
├── inspect
├── claims
└── reset
    ├── container
    └── player <player>
        └── container
```

The tree uses `Commands.literal(...)`, `Commands.argument(...)`,
`ArgumentTypes.namespacedKey()`, and `CommandSourceStack`.

`version` and root help are available to all senders. Management branches
require `gameloot.admin`; the Brigadier `requires` predicates also hide those
branches from unauthorised suggestions. Player-only operations reject console
and other non-player senders with Adventure messages.

The `player` reset argument uses `ArgumentTypes.playerProfiles()` and requires
exactly one resolved profile. Paper may resolve known offline profiles; all
claim operations use the resulting UUID, never the profile name.

The command class delegates registration, lookup, and loot-table resolution to
services. It schedules user-facing completion messages back onto the main
thread after asynchronous operations.

## Persistent data model

### SQLite schema version 3

```sql
CREATE TABLE schema_version (
    version INTEGER NOT NULL
);

CREATE TABLE loot_points (
    id TEXT PRIMARY KEY,
    world_uuid TEXT NOT NULL,
    target_type TEXT NOT NULL,
    x INTEGER NOT NULL,
    y INTEGER NOT NULL,
    z INTEGER NOT NULL,
    entity_uuid TEXT,
    loot_table TEXT,
    created_at INTEGER NOT NULL,
    created_by TEXT
);

CREATE TABLE loot_claims (
    player_uuid TEXT NOT NULL,
    loot_point_id TEXT NOT NULL,
    claimed_at INTEGER NOT NULL,
    PRIMARY KEY (player_uuid, loot_point_id),
    FOREIGN KEY (loot_point_id)
        REFERENCES loot_points(id)
        ON DELETE CASCADE
);

CREATE TABLE shelf_loot_items (
    loot_point_id TEXT NOT NULL,
    slot INTEGER NOT NULL CHECK (slot BETWEEN 0 AND 2),
    serialized_item BLOB NOT NULL,
    PRIMARY KEY (loot_point_id, slot),
    FOREIGN KEY (loot_point_id)
        REFERENCES loot_points(id)
        ON DELETE CASCADE
);
```

UUIDs are canonical strings. Timestamps are epoch milliseconds. The primary
keys cover current lookups, so no additional indexes are presently required.
`loot_table` is null only for `SHELF`. Schema version 3 transactionally rebuilds
the table while preserving all existing points and claims.

Future schema changes must increment `CURRENT_SCHEMA_VERSION` in
`DatabaseManager` and apply migrations sequentially inside its migration
transaction. Existing databases must not be deleted or recreated to upgrade
them.

### Persistent Data Container

New registrations store only:

```text
gameloot:loot_point_id -> canonical UUID string
```

The UUID is the identity link; location alone is not identity. The database is
authoritative for the loot-table key and target metadata.

Older development targets may also contain:

```text
poiloot:loot_point_id -> canonical UUID string
poiloot:loot_table -> namespaced loot-table key
```

When an old target is inspected or interacted with and its UUID is absent from
SQLite, `LootPointLookupService` inserts the legacy record. It removes the old
loot-table PDC value only after successful insertion. Conflicts between a
physical target and an existing database record are logged and refused.

The rename migration also:

- moves `plugins/POILoot/poiloot.db` to `plugins/GameLoot/gameloot.db` if the
  destination does not exist;
- migrates stored `poiloot:` loot-table keys to `gameloot:` in schema version
  2;
- recognizes old `poiloot:*` PDC keys and replaces them with the UUID-only
  `gameloot:loot_point_id` marker after successful resolution.

## Operational flows

### Registration

1. Locate and validate the target on the main thread.
2. Reject any existing GameLoot UUID marker.
3. Generate a UUID and capture physical metadata.
4. Insert the `loot_points` row on the database executor.
5. Return to the main thread and write the UUID marker.
6. If PDC persistence fails, delete the inserted row as compensation.

### Deregistration

1. Resolve the marker and authoritative record.
2. In one transaction, snapshot the record and claims and delete the loot
   point; SQLite cascades the claim deletion.
3. Return to the main thread and remove the PDC marker.
4. Remove cached claims only after full success.
5. If PDC removal fails, restore the registration and snapshotted claims in a
   transaction.

SQLite and world PDC cannot share one atomic transaction. These compensating
operations are therefore part of the consistency contract and must be
preserved when registration code changes.

### Inspection and interaction

1. Read the UUID marker on the main thread.
2. Resolve authoritative metadata from the loot-point cache.
3. If missing, attempt the legacy migration using the old PDC loot-table key.
4. Return to the main thread before altering PDC, opening inventories, or
   messaging players.
5. Report or log a diagnostic if the marker has no usable database record.

### Claim lifecycle

1. Opening a loot point creates or reopens an in-memory session; it does not
   claim the point.
2. The first valid item request reserves the cache key and submits an atomic
   claim insert.
3. The item is transferred only after the insert succeeds.
4. Further transfers in that session are immediate.
5. Closing after an item was taken discards remaining session contents.
6. Later access is rejected using the claim cache.
7. Restarted servers reload persisted claims before listeners activate.

Different player UUIDs have independent claims for the same loot-point UUID.

### Shelf lifecycle

1. Resolve an explicit shelf and copy its three snapshot slots on the main
   thread.
2. Reject an empty template and serialize non-empty stacks with
   `ItemStack.serializeAsBytes()`.
3. Insert the point and reward rows in one transaction, then write the PDC
   marker through the normal compensated registration flow.
4. Cancel registered-shelf interaction through the central protection service
   and plan a complete resulting player
   storage array without changing live inventory state.
5. Persist the UUID claim atomically, re-plan on the main thread, and replace
   storage contents once. If capacity changed, delete the claim and transfer
   nothing.
6. Deregistration cascades rewards and claims, removes the marker, and leaves
   the visible shelf contents unchanged.

### Administrative claim reset

1. The command resolves a player UUID and/or registered targeted loot point.
2. `ClaimRepository` performs the prepared `DELETE` on the database executor.
3. Only after database success does `ClaimService` remove matching non-pending
   cache entries.
4. Pending claim entries remain fail-closed and follow executor ordering, so a
   concurrent first-item claim cannot be opened for a duplicate transfer.
5. `ClaimAdministrationService` schedules affected session invalidation on the
   main thread.
6. The session is removed and cleared, and any viewer is closed before the
   command reports completion.

There is no schema migration for claim administration. Delete counts come from
SQLite, while interactive claim inspection uses the startup-maintained cache
and counts only durably persisted claim states.

## Threading rules

Treat these rules as architectural constraints:

- Blocks, block states, entities, players, worlds, locations, and inventories
  are accessed only on Paper's main thread.
- Database work after startup runs on `DatabaseManager`'s single-thread
  executor.
- Inventory event handlers use caches and never perform routine database
  reads.
- Asynchronous continuations that need Bukkit objects schedule back through
  the Paper scheduler.
- Initial schema migration and cache loading are blocking, but occur during
  startup before gameplay listeners are registered.
- Do not introduce blocking database calls into commands, listeners, or
  gameplay services.

## Tests

`RepositoryIntegrationTest` currently covers:

- schema creation;
- migration idempotence;
- loot-point insert, lookup, and deletion;
- duplicate loot-point IDs;
- claim insertion and duplicate prevention;
- cascading claim deletion;
- restoration of a deleted loot point and its claims;
- migration from the former database file path;
- migration of stored `poiloot:` loot-table keys to `gameloot:`.
- claim counts;
- deletion of one claim;
- deletion of all claims for a player;
- deletion of all claims for a loot point;
- claim-cache consistency after each reset scope.
- explicit supported and unsupported target mappings;
- schema version 3 migration with existing claim preservation;
- shelf reward insertion, loading, slot preservation, reset independence, and
  cascading deletion;
- empty shelf rejection and per-player shelf claim persistence.

The tests use temporary SQLite databases and the test-only Xerial driver.
There are currently no automated Paper event, command, PDC, or inventory tests;
those paths require manual server verification or a future suitable Paper test
harness.

## Known limitations and next steps

- Active unclaimed inventory sessions exist only in memory. A restart discards
  them and permits a fresh generation for an unclaimed point.
- Generated inventory contents are not stored in SQLite.
- GameLoot does not automatically validate or remove database rows whose
  physical target was removed by an external administration tool. A future
  validation/cleanup command should report these orphaned registrations.
- Explicit plugin or administrator mutations that do not raise cancellable
  Bukkit gameplay events can bypass protection. WorldEdit integration is not
  included.
- There are no player statistics, achievements, discoveries, or objective
  progress repositories yet.
- The database uses one short-lived connection per operation rather than a
  connection pool. This is suitable for current write volume and keeps
  ownership simple.

Likely future repositories should follow the existing repository/service
split. Add SQL to a repository, expose cached or gameplay-oriented behaviour
through a service, construct both in `GameLootPlugin`, and keep listeners and
commands unaware of JDBC.

## Manual regression checklist

- Start with no plugin data directory and verify `gameloot.db` and schema
  version 2 are created.
- Register and inspect a chest, barrel, copper container, and chest minecart.
- Restart and verify registrations remain valid.
- Verify new targets contain the UUID PDC key but no loot-table PDC key.
- Interact with a legacy UUID plus loot-table PDC target and verify automatic
  migration.
- Confirm two players receive independent loot from the same point.
- Take the first item, restart, and confirm the claim is still refused.
- Close an unclaimed inventory and confirm reopening shows the same active
  contents before restart.
- Fill a player inventory and verify items are not silently discarded.
- Deregister a point and verify its database claims are cascade-deleted.
- Compare `/gameloot claims` as two independently claimed players.
- Reset one player's targeted-container claim and verify the other remains.
- Reset a player's claims globally, including while that player is offline.
- Reset all claims for a container and verify its registration remains intact.
- Reset a claim while its private inventory is open and verify the view closes
  without transferring or duplicating remaining items.
- Check `/gameloot`, `version`, `register`, `deregister`, `inspect`, `claims`,
  and `reset`, including console rejection and permission-aware suggestions.
- Stop the server while persistence work is pending and inspect shutdown logs.
- In Creative mode, try to break a registered standard block container and a
  registered shelf; both must remain intact.
- In Creative mode, attack, collide with, and attempt to move a registered
  chest minecart; it must remain intact and stationary.
- In Creative mode, try every shelf item insertion, removal, and hotbar-swap
  interaction; the visible shelf contents must remain unchanged.
- In Creative mode, attempt to insert and remove items from every registered
  physical inventory using both direct access and automation.
- Use Creative pick-block on registered blocks and verify the copied item is
  obtained without changing the world target.
- Repeat destructive attempts as an operator with `gameloot.admin` and verify
  that neither status grants a protection bypass.
- In Spectator mode, verify vanilla non-mutating interaction remains available
  and no GameLoot private inventory or shelf reward is opened or claimed.

## Development conventions

- Keep `GameLootPlugin` small and use constructor injection.
- Use Adventure Components for player-visible messages.
- Preserve UUID-based identity and existing PDC key names.
- Keep Paper-specific objects at service, listener, inventory, and target
  boundaries rather than adding them to domain records unnecessarily.
- Use prepared statements for every SQL value.
- Log malformed stored data with the loot-point ID and target location when
  available; do not log routine unregistered-container checks.
- Never silently discard items when inventory capacity is insufficient.
- Run `./gradlew build` before completing a change.
- Do not edit generated files under `build`.
- Do not commit or push unless explicitly requested.
