# POILoot

POILoot is a Java plugin for Paper Minecraft 26.2. It is being built to provide
per-player, one-time loot at registered points of interest.

The current development version can register block containers and chest
minecarts as loot points, then generate a private inventory from the configured
loot table when a player interacts with one. Per-player claims are not yet
implemented, so reopening a loot point currently generates fresh loot.

## Requirements

- Paper 26.2
- Java 25

## Commands

| Command | Permission | Description |
| --- | --- | --- |
| `/poiloot` | None | Shows the commands available to the sender. |
| `/poiloot version` | None | Shows the plugin name and version. |
| `/poiloot register <loot-table>` | `poiloot.admin` | Registers the targeted container with a namespaced loot-table key. |
| `/poiloot deregister` | `poiloot.admin` | Removes POILoot registration from the targeted container. |
| `/poiloot inspect` | `poiloot.admin` | Shows the target's registration status, ID, type, loot table, and location. |

Registration commands require a player to look at a supported container within
six blocks. The `poiloot.admin` permission is granted to server operators by
default. Admin command branches are hidden from Brigadier suggestions for
senders without this permission.

The loot-table argument accepts loaded vanilla and datapack loot tables. For
example:

```text
/poiloot register poiloot:mining_camp/common
```

Brigadier completion suggests built-in loot-table keys. Datapack-defined keys
are validated by the server when entered but currently need to be typed
manually.

## Loot-point data

POILoot stores its registration metadata in the container's
`PersistentDataContainer`:

- `poiloot:loot_point_id` — a unique UUID
- `poiloot:loot_table` — the namespaced loot-table key

This metadata is stored with the chest, barrel, or chest minecart and persists
with the world. Deregistration removes only these POILoot-owned values.

## Private loot

Interacting with a registered loot point cancels access to its physical
inventory and opens a private 27-slot inventory instead. Loot is generated from
the stored loot table for every interaction. Claim persistence and session
recovery are intentionally not part of the current milestone.

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

POILoot is currently an early development snapshot. See [VERSION.md](VERSION.md)
for the current version and implemented scope, and [CHANGELOG.md](CHANGELOG.md)
for notable changes.

## License

POILoot is available under the [MIT License](LICENSE).
