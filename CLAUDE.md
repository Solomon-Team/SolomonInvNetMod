# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Inventory Network** is a client-side Minecraft Fabric mod (Minecraft 1.21.10) that creates a crowdsourced, shared storage network across multiplayer servers. Players with the mod can:

- Automatically share chest contents to a third-party server when opening chests
- Search and locate items across all known storage locations
- Preview chest contents before opening
- Collaborate on base organization through synchronized inventory data

This is a **client-only mod** - all functionality runs on the client side and communicates with an external server you control, without requiring server-side installation.

## Development Commands

### Building the Mod
```bash
./gradlew build
```
The compiled mod JAR will be in `build/libs/`

### Running in Development
```bash
./gradlew runClient
```
This launches a Minecraft client with the mod loaded for testing.

### Cleaning Build Artifacts
```bash
./gradlew clean
```

### Generating Source JARs
```bash
./gradlew sourcesJar
```

## Architecture

### Entry Points

The mod uses Fabric's split environment approach:

- **Main Entry Point**: `com.BookKeeper.InventoryNetwork.InventoryNetworkMod` (src/main/java/)
  - Runs on both client and server environments
  - Currently minimal - most logic should go in the client entry point since this is client-only

- **Client Entry Point**: `com.BookKeeper.InventoryNetwork.InventoryNetworkModClient` (src/client/java/)
  - **This is where most mod functionality belongs**
  - Handles client-specific logic: chest opening events, rendering, UI, network communication

### Source Organization

- `src/main/java/` - Common code (runs in all environments)
- `src/client/java/` - Client-only code (use this for the majority of implementation)
- `src/main/resources/` - Mod metadata and assets

The `build.gradle` uses `splitEnvironmentSourceSets()` to maintain proper separation.

### Key Technical Details

- **Minecraft Version**: 1.21.10
- **Java Version**: 21 (required)
- **Fabric Loader**: 0.17.3+
- **Fabric API**: 0.136.0+1.21.10
- **Mappings**: Official Mojang mappings

### Implementation Strategy

Since this mod syncs chest data with an external server, the architecture should include:

1. **Event Listeners** (in client code): Hook into Minecraft's chest/container opening events
2. **Data Collection**: Extract chest position, dimension, and item contents when opened
3. **Network Client**: HTTP/WebSocket client to communicate with your external server
4. **Data Synchronization**: Upload discovered chest data and download known chest locations
5. **UI/Rendering**: Overlay or GUI to display search results, highlights, and chest previews
6. **Caching**: Local cache of known chest locations to reduce server queries

### Mod Identification

Currently uses placeholder values in `fabric.mod.json` and `gradle.properties`:
- MOD_ID: `"modid"` (should be changed to `"inventory-network"` or similar)
- Package: `com.BookKeeper.InventoryNetwork`
- Maven group: `com.example` (should be updated to match package)

## Important Constraints

- This is a **client-side only mod** - do not add server-side dependencies or assume server installation
- Only access data that the vanilla client legitimately receives (e.g., after opening a chest)
- All external communication must happen client-to-server, never interfere with Minecraft's networking
- Use Fabric API for event hooks and client utilities

## Branch Strategy

- Main development branch: `1.21`
- Current working branch: `IN-01`
- PRs should target `1.21`
