# GameLoot

GameLoot is a Java plugin for Paper Minecraft 26.2. It is being built to provide
per-player, one-time loot at registered points of interest.

The current development version can register block containers and chest
minecarts as loot points, then generate a private inventory from the configured
loot table when a player interacts with one. Loot-point metadata and per-player
claims are stored in SQLite. Active inventory sessions remain in memory and
reset when the server restarts.

## Requirements

- Paper 26.2
- Java 25

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/gameloot` | None | Shows the commands available to the sender. |
| `/gameloot version` | None | Shows the plugin name and version. |
| `/gameloot register <loot-table>` | `gameloot.admin` | Registers the targeted container with a namespaced loot-table key. |
| `/gameloot deregister` | `gameloot.admin` | Removes GameLoot registration from the targeted container. |
| `/gameloot inspect` | `gameloot.admin` | Shows the target's registration status, ID, type, loot table, and location. |

Registration commands require a player to look at a supported container within
six blocks. The `gameloot.admin` permission is granted to server operators by
default. Admin command branches are hidden from Brigadier suggestions for
senders without this permission.

The loot-table argument accepts loaded vanilla and datapack loot tables. For
example:

```text
/gameloot register gameloot:mining_camp/common
```

Brigadier completion suggests built-in loot-table keys. Datapack-defined keys
are validated by the server when entered but currently need to be typed
manually.

## Loot-point data

GameLoot stores authoritative registration metadata in:

```text
plugins/GameLoot/gameloot.db
```

The database records the loot-point UUID, world and physical target, loot-table
key, creation time, and registering player. The target's
`PersistentDataContainer` retains only:

- `gameloot:loot_point_id` — a unique UUID

This UUID links the chest, barrel, copper container, or chest minecart to its
database record. Deregistration removes the database record, its associated
claims, and the GameLoot-owned PDC marker.

Development containers registered by older POILoot builds may contain
`poiloot:loot_point_id` and `poiloot:loot_table` PDC values. GameLoot migrates
these registrations and markers when they are inspected or interacted with,
retaining their UUID and removing obsolete metadata only after successful
persistence. On first startup after the rename, an existing
`plugins/POILoot/poiloot.db` is moved to `plugins/GameLoot/gameloot.db` when a
new database does not already exist. Stored `poiloot:` loot-table keys migrate
to the `gameloot:` namespace through schema version 2.

## Private loot

Interacting with a registered loot point cancels access to its physical
inventory and opens a private 27-slot inventory instead. Loot is generated from
the stored loot table once per active player and loot-point session. Closing an
inventory before taking an item preserves the same remaining contents for the
next interaction.

A claim is recorded when the player successfully takes the first item. Closing
the inventory after that point discards its remaining contents, and further
access is refused for that player. Other players have independent claims and
sessions. Claims persist across server restarts. The first transfer waits for
the claim write to succeed so a database error cannot make the same loot
claimable again; failures are logged and access is blocked safely.

Unclaimed active sessions are deliberately not persisted. Restarting the
server discards those temporary generated inventories, so reopening after a
restart generates a new session.

## Building

The project uses the included Gradle wrapper:

```bash
./gradlew clean build
```

The plugin JARs are produced under `build/libs`. Do not rebuild or replace the
plugin JAR while a server is actively loading it; stop the server before
building and restarting.

For local development, a Paper 26.2 server can be started with:

```bash
./gradlew runServer
```

## Project status

GameLoot is currently an early development snapshot. See [VERSION.md](VERSION.md)
for the current version and implemented scope, and [CHANGELOG.md](CHANGELOG.md)
for notable changes.

## License

GameLoot is available under the [MIT License](LICENSE).
