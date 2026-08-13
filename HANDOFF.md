# GameLoot Project Handoff

Last reviewed: 2026-08-13

## Resume checklist

1. Open `/Users/bheidema/projects/poiloot`.
2. Read `AGENTS.md`, then this document.
3. Run `git status --short` before editing. The worktree was clean when this
   handoff was written.
4. Run `./gradlew clean build` to verify Java 25, Paper 26.2, SQLite tests, and
   the current local dependency cache.
5. For server testing, use `./gradlew runServer`. Stop the server before
   replacing a plugin JAR.
6. Do not commit or push unless explicitly requested.

The current Git HEAD when this document was created was:

```text
0656bd0 feat: add loot point listing and reset-all claims
```

The checkout at that commit also contains `/gameloot list csv` and its tests.

## Project summary

GameLoot is a Paper 26.2 plugin written for Java 25. It provides persistent,
per-player, one-time loot at map-builder-defined points of interest.

Current version: `26.2-0.2.0`

Main class:

```text
io.github.blackshadowhrd.gameloot.GameLootPlugin
```

Primary command and permission:

```text
/gameloot
gameloot.admin (default: op)
```

Commands use Paper Brigadier and are registered through
`LifecycleEvents.COMMANDS`. There is no legacy Bukkit command executor and no
`/poiloot` alias.

## Current command tree

```text
/gameloot
├── version
├── register
│   ├── shelf
│   └── <loot-table>
├── deregister
├── inspect
├── claims
├── validate
├── list
│   ├── <page>
│   └── csv
└── reset
    ├── all
    │   └── confirm
    ├── container
    └── player <player>
        └── container
```

`version` and root help are public. Every management branch requires
`gameloot.admin`, and Brigadier hides unauthorized branches.

## Supported loot-point targets

`LootPointTargetType` is the one authoritative target catalogue. Do not add
material lists or parallel `instanceof` chains elsewhere.

Supported targets are:

- chest;
- trapped chest;
- barrel;
- all copper-chest oxidation and waxed variants;
- uncoloured and coloured shulker boxes;
- chest minecart;
- all shelf wood variants.

Standard containers use a namespaced loot table. Shelves capture their three
visible slots as a fixed reward template and do not use a fake loot-table key.

## Persistence model

Database location:

```text
plugins/GameLoot/gameloot.db
```

Current schema version: `3`

Tables:

- `schema_version`
- `loot_points`
- `loot_claims`
- `shelf_loot_items`

SQLite is authoritative. A registered block/entity PDC stores only:

```text
gameloot:loot_point_id = canonical UUID string
```

`loot_points` stores the world UUID, persisted target category, coordinates,
optional chest-minecart entity UUID, optional loot-table key, creation time,
and registering player UUID. `loot_claims` uses player UUID plus loot-point UUID
as its primary key and cascades when a loot point is deleted. Shelf item bytes
are stored by slot in `shelf_loot_items` and cascade with the loot point.

Legacy `plugins/POILoot/poiloot.db`, `poiloot:` loot-table namespaces, and old
PDC metadata have compatibility migration paths. Do not change stored formats
without an explicit migration.

## Architecture map

`GameLootPlugin` is the composition root. It creates repositories, caches,
services, listeners, and the command tree with constructor injection.

Important boundaries:

- `DatabaseManager`: connection setup, schema migration, single database
  executor, shutdown.
- `LootPointRepository`, `ClaimRepository`, `ValidationRepository`: all SQL.
- `LootPointPersistenceService`: in-memory loot-point and shelf-template cache.
- `ClaimService`: persistent/pending claim cache and atomic claim/reset logic.
- `ClaimAdministrationService`: main-thread session invalidation after resets.
- `LootPointTargetType` and `LootPointTargetResolver`: authoritative supported
  target mapping.
- `LootPointLookupService`: target acquisition, PDC parsing, database/cache
  resolution, and legacy PDC migration.
- `LootPointRegistrar`: compensated registration and deregistration writes.
- `LootGenerationService`: Paper loot-table resolution and generation.
- `LootSessionService`: active in-memory player/loot-point inventories.
- `PrivateInventoryService`: private inventory creation/opening.
- `ShelfRewardTemplate` and `ShelfRewardService`: fixed shelf serialization,
  capacity planning, durable claim, and all-or-nothing delivery.
- `LootPointProtectionService` and listener: fast PDC-backed physical target
  protection without SQLite reads.
- `ValidationService`: read-only, batched consistency checks.
- `LootTableCatalog`: cached vanilla plus loaded `gameloot:*` suggestions.
- `LootPointListingService`: one ordered list query followed by safe main-thread
  world/entity enrichment.
- `LootPointCsvExportService`: reuses listing data and writes UTF-8 CSV off the
  main thread.
- `ConfirmationService`: sender-scoped, expiring destructive-command tokens.
- `GameLootCommand`: Brigadier parsing, permission gates, and Adventure output;
  it must not contain SQL or gameplay business logic.

For fuller class-by-class details, see `DEVELOPER-NOTES.md`.

## Gameplay and claims

Standard loot containers open a private 27-slot inventory. Loot is generated
once per active player/loot-point session. Reopening an unclaimed session shows
the same remaining contents.

Opening does not create a claim. The first successful item transfer persists
the claim before delivering the item. After an item has been taken, closing the
private inventory discards remaining session contents. Different players have
independent claims.

Shelf claims directly transfer the complete fixed template. Capacity is
checked all-or-nothing; nothing is claimed or transferred if it cannot all fit.
The physical shelf never changes.

Claims persist across restart. Active generated sessions do not.

Reset-all uses a 30-second, sender-specific confirmation. The claim gate blocks
new claim reservations during the database delete; the cache is cleared only
after success, then affected sessions are invalidated on the main thread.

## Protection

Registered targets are protected map infrastructure. Protection covers player
breaking, explosions, pistons, fire, block-state/entity changes, hopper
automation, shelf mutation, and chest-minecart damage, destruction, collision,
portal movement, and vanilla movement.

Protection applies in Survival, Adventure, and Creative, including operators
and players with `gameloot.admin`. Spectators do not open or claim GameLoot
rewards. Harmless Creative pick-block remains available.

The supported removal path is `/gameloot deregister`. External tools such as
WorldEdit may bypass Bukkit events. GameLoot deliberately retains database rows
and claims if a physical target disappears.

## Validation, listing, and export

`/gameloot validate` is read-only. It begins from cached authoritative database
records, performs database integrity checks asynchronously, and inspects at
most 50 live records per server tick. It never loads chunks. Results are
`VALID`, `WARNING`, `INVALID`, or `UNVERIFIED`.

`/gameloot list [page]` uses one repository query ordered by:

```text
world_uuid, x, z, y, id
```

It displays ten rows per page and suggests clickable cross-world teleport
commands. Unloaded chest minecarts use persisted coordinates marked as possibly
stale. Unavailable worlds show their UUID without a misleading teleport link.

`/gameloot list csv` exports every registration in the same order to:

```text
plugins/GameLoot/exports/gameloot-lootpoints-<UTC timestamp>.csv
```

CSV is UTF-8 with RFC-style quoting and collision suffixes. Columns are:

```text
id,target_type,world,world_uuid,x,y,z,entity_uuid,loot_mode,loot_table,teleport_command
```

## Loot-table autocomplete

`Registry.LOOT_TABLES` supplies default/vanilla suggestions only. Custom
`gameloot:*` keys are discovered from enabled directory or ZIP datapacks under
the level's `datapacks` directory, restricted to:

```text
data/gameloot/loot_table/**/*.json
```

Candidates must resolve through `Server#getLootTable`. The immutable cache is
built at startup and refreshed after `ServerResourcesReloadedEvent`, including
`/minecraft:reload`. Tab completion never scans files directly.

## Threading rules

- Bukkit worlds, blocks, entities, players, inventories, PDC, and registry
  access stay on the main server thread.
- Post-startup SQLite work uses `DatabaseManager`'s single executor.
- CSV and datapack filesystem work run asynchronously using immutable data.
- Async continuations return through the Paper scheduler before touching
  Bukkit objects or messaging senders.
- Protection and inventory event handlers use PDC/caches, never routine SQLite
  reads.
- Startup schema migration and initial cache loading are blocking by design and
  finish before listeners become active.

## Build and testing

```bash
./gradlew clean build
```

The build produces regular and shaded JARs under `build/libs`. SQLite JDBC is
available from Paper at plugin runtime; Xerial is a test-runtime dependency so
repository tests can run outside Paper.

Automated tests cover repositories and schema migrations, claims and resets,
shelf templates, target definitions, protection decisions, validation
classification, datapack discovery/catalog behavior, pagination,
confirmations, deterministic listing order, and CSV generation/escaping.

There is no automated Paper event/world/inventory test harness. Protection,
interaction, Brigadier output, live datapack reload, clickable components, and
teleports require manual server verification.

## Known limitations

- Active unclaimed sessions and generated contents are lost on restart.
- Validation does not repair or delete orphaned registrations.
- Validation does not scan worlds for PDC markers missing from SQLite.
- `BLOCK_CONTAINER` persistence does not retain the precise chest/barrel/
  copper/shulker subtype, so validation cannot detect replacement by another
  supported standard subtype.
- External plugin/admin mutations that bypass cancellable events can bypass
  protection.
- Chest-minecart persisted coordinates can become stale; loaded entities use
  their current position in chat listing, while CSV deliberately remains
  deterministic from persisted coordinates.
- There are no statistics, achievements, discoveries, objectives, or beacon
  progress repositories yet.
- Database operations use short-lived connections rather than a pool.

## Good next-step candidates

Keep future work separately scoped. Reasonable candidates include:

- persistent generated sessions or restart-safe unclaimed inventory contents;
- read-only detailed validation output or pagination;
- explicit repair/cleanup tooling built on validation reports, with strong
  confirmation and backup semantics;
- more precise persisted block target subtypes through a schema migration;
- a Paper-aware automated integration-test harness;
- player statistics, achievements, discoveries, and objective progress.

Do not infer that any of these are already approved or implemented.

## Documentation map

- `README.md`: user and map-builder behavior.
- `VERSION.md`: current implemented/not-implemented scope.
- `CHANGELOG.md`: milestone history.
- `DEVELOPER-NOTES.md`: detailed architecture and operational flows.
- `AGENTS.md`: repository-specific development rules.
- `HANDOFF.md`: compact resumption context and current state.

When a milestone changes architecture or behavior, update the relevant files
above and run the full build before handing work back.
