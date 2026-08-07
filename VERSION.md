# Version

## 0.1.0-SNAPSHOT

Current development version. This is not a stable release.

Implemented:

- Native Paper Brigadier `/gameloot` command tree
- Plugin name and version reporting
- Registration of chests, barrels, and chest minecarts as loot points
- SQLite-backed authoritative loot-point metadata
- UUID-only PDC links between physical targets and database records
- Automatic migration of legacy UUID and loot-table PDC registrations
- Automatic database-file and `poiloot:` namespace migration
- Duplicate-registration detection
- Loot-point inspection
- Loot-point deregistration
- `gameloot.admin` permission checks and permission-aware suggestions
- Loaded vanilla and datapack loot-table key completion
- Private 27-slot loot generation for registered containers
- Physical-container access prevention for registered loot points
- Persistent per-player claims keyed by player and loot-point UUID
- In-memory claim cache loaded during startup
- Atomic, duplicate-safe claim insertion
- Stable active loot sessions that do not reroll on reopen
- Protected private inventories that reject player-item insertion
- Versioned database schema and clean database-executor shutdown
- Registration and deregistration failure compensation
- Repository integration tests

Not yet implemented:

- Session recovery after a server restart
- Persistent generated inventory contents for unclaimed sessions
