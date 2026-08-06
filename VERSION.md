# Version

## 0.1.0-SNAPSHOT

Current development version. This is not a stable release.

Implemented:

- Native Paper Brigadier `/poiloot` command tree
- Plugin name and version reporting
- Registration of chests, barrels, and chest minecarts as loot points
- Persistent UUID and loot-table metadata
- Duplicate-registration detection
- Loot-point inspection
- Loot-point deregistration
- `poiloot.admin` permission checks and permission-aware suggestions
- Registered loot-table key completion
- Private 27-slot loot generation for registered containers
- Physical-container access prevention for registered loot points
- In-memory per-player claims keyed by player and loot-point UUID
- Stable active loot sessions that do not reroll on reopen
- Protected private inventories that reject player-item insertion

Not yet implemented:

- Persistent claim tracking
- Session recovery after a server restart
