# POILoot development instructions

## Project

POILoot is a Java plugin for Paper Minecraft 26.2.

The plugin will provide per-player, one-time loot at registered POIs,
including chests, barrels, chest minecarts and shelves.

## Technical requirements

- Use Java 25.
- Use Paper API 26.2.
- Use Gradle with Kotlin DSL.
- Use Adventure Components for player-facing messages.
- Use UUIDs for player identity.
- Use PersistentDataContainer for identifying loot points.
- Avoid NMS and reflection unless explicitly approved.
- Keep the JavaPlugin entry class small.
- Separate commands, listeners, storage and loot logic.
- Do not perform blocking database work on the server thread.
- Do not silently discard items when an inventory is full.

## Development workflow

Before completing a code change:

1. Run `./gradlew build`.
2. Report the files changed.
3. Report any tests performed.
4. Do not modify generated files under `build`.
5. Do not commit or push unless explicitly asked.