# GameLoot

GameLoot is a Java plugin for Paper Minecraft 26.2. It is being built to provide
per-player, one-time loot at registered points of interest.

The current development version supports explicit loot-table containers and
fixed-reward shelves. Loot-point metadata, shelf templates, and per-player
claims are stored in SQLite. Active loot-table inventory sessions remain in
memory and reset when the server restarts.

## Requirements

- Paper 26.2
- Java 25

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/gameloot` | None | Shows the commands available to the sender. |
| `/gameloot version` | None | Shows the plugin name and version. |
| `/gameloot register <loot-table>` | `gameloot.admin` | Registers the targeted container with a namespaced loot-table key. |
| `/gameloot register shelf` | `gameloot.admin` | Captures the targeted shelf as a fixed reward. |
| `/gameloot deregister` | `gameloot.admin` | Removes GameLoot registration from the targeted container. |
| `/gameloot inspect` | `gameloot.admin` | Shows the target's registration status, ID, type, loot table, and location. |
| `/gameloot claims` | `gameloot.admin` | Shows claim information for the targeted loot point. |
| `/gameloot reset container` | `gameloot.admin` | Resets every claim for the targeted loot point. |
| `/gameloot reset player <player>` | `gameloot.admin` | Resets every claim belonging to a player UUID. |
| `/gameloot reset player <player> container` | `gameloot.admin` | Resets one player's claim for the targeted loot point. |

Registration commands require a player to look at a supported container within
six blocks. The `gameloot.admin` permission is granted to server operators by
default. Admin command branches are hidden from Brigadier suggestions for
senders without this permission.

The loot-table argument accepts loaded vanilla and datapack loot tables. For
example:

```text
/gameloot register gameloot:mining_camp/common
```

Brigadier completion suggests the loot-table keys currently loaded in the
server registry, including available vanilla and datapack-defined tables.

The reset-player commands use Paper's player-profile argument. They resolve a
single online or reliably known offline profile and perform persistence using
its UUID rather than its current name.

## Loot-point data

GameLoot supports only chests, trapped chests, barrels, every copper chest
variant, every shulker box colour, chest minecarts, and shelves. Arbitrary
inventory holders are deliberately unsupported.

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

## Fixed shelf rewards

Place up to three item stacks on a shelf, look at it within six blocks, and run
`/gameloot register shelf`. Exact Paper-serialized copies of the visible stacks
become the authoritative fixed template; empty shelves are rejected.

Registered shelves are immutable through player interaction. Claiming copies
the complete template directly into player storage without changing the
display. The transfer is all-or-nothing: if every item cannot fit, nothing is
transferred or claimed. Deregistration removes the template and claims, leaves
the physical items in place, and restores vanilla shelf interaction.

## Claim administration

`/gameloot claims` reports the targeted loot point's UUID, loot-table key,
persisted claim count, and whether the executing player has claimed it.

Claim resets update SQLite first and then update the in-memory claim cache.
Successful resets also invalidate affected active private inventories on the
server thread. This prevents stale claimed sessions from remaining open while
allowing the next interaction to create a fresh session safely.

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
