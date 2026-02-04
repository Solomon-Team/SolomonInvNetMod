# ChestSync Quick Reference - Minecraft Mod

**Related**: See full development summary at `C:\BookKeeper\BackendBK\CHESTSYNC_DEVELOPMENT.md`

## What Was Implemented Today (2025-11-28)

ChestSync enables real-time chest inventory synchronization across all players in a structure via WebSocket.

### Files Modified

1. **ApiClient.java** (line 178-213)
   - Added `sendChestData()` method
   - Posts chest data to `/api/mc/events/jwt` with JWT auth

2. **ChestSyncManager.java** (NEW FILE - 282 lines)
   - Manages aggregated chest data from all players
   - Singleton pattern: `ChestSyncManager.getInstance()`
   - Key methods:
     - `handleFullState(JsonObject)` - Process complete sync on connection
     - `handleChestUpdate(JsonObject)` - Process incremental update
     - `getChestAt(x, y, z)` - Get specific chest
     - `getAllChests()` - Get all known chests
     - `findChestsWithItem(itemId)` - Search for item

3. **WebSocketManager.java** (line 120-218)
   - Added handlers for `chest_full_state` and `chest_update` messages
   - Added `getJwtToken()` method for authenticated API calls
   - Clears ChestSync data on disconnect

4. **ChestTracker.java** (modified heavily)
   - Now requires `ApiClient` in constructor
   - Builds JSON format for chest data
   - Sends to backend asynchronously in `sendChestDataToBackend()`
   - Still saves to local H2 (backward compatibility)

5. **InventoryNetworkModClient.java** (line 81)
   - Updated initialization: `new ChestTracker(databaseManager, apiClient)`

## Data Flow

```
Player Opens Chest
    ↓
ChestTracker.readChestContents()
    ↓
ChestTracker.saveChestData()
    ├── Save to local H2 (DatabaseManager)
    └── sendChestDataToBackend()
            ↓
        ApiClient.sendChestData() [HTTP POST with JWT]
            ↓
        Backend receives, stores, broadcasts
            ↓
        WebSocketManager receives broadcast
            ↓
        ChestSyncManager.handleChestUpdate()
            ↓
        Local cache updated
            ↓
        UI queries ChestSyncManager ← PENDING IMPLEMENTATION
```

## Usage Examples

### Query Chest Data

```java
ChestSyncManager manager = ChestSyncManager.getInstance();

// Get all chests
Collection<ChestSnapshot> allChests = manager.getAllChests();
for (ChestSnapshot chest : allChests) {
    System.out.println("Chest at " + chest.x + ", " + chest.y + ", " + chest.z);
    System.out.println("Opened by: " + chest.openedByUsername);
}

// Search for chests with diamonds
List<ChestSnapshot> diamondChests = manager.findChestsWithItem("minecraft:diamond");

// Get specific chest
ChestSnapshot chest = manager.getChestAt(100, 64, 200);
if (chest != null) {
    int diamondCount = chest.getItemCount("minecraft:diamond");
    System.out.println("Diamonds in this chest: " + diamondCount);
}
```

### Listen for Updates

```java
ChestSyncManager manager = ChestSyncManager.getInstance();

manager.addUpdateListener(update -> {
    if (update.type == ChestUpdate.Type.FULL_STATE) {
        // Full state received (on connection)
        int totalChests = manager.getChestCount();
        System.out.println("Synchronized " + totalChests + " chests");
    } else {
        // Incremental update
        ChestSnapshot chest = update.chest;
        System.out.println("Chest updated at " + chest.x + ", " + chest.y + ", " + chest.z);
        System.out.println("Last opened by: " + chest.openedByUsername);
    }
});
```

## Next Steps (UI Integration)

### 1. Update InventoryPanelOverlay

Current code (EXAMPLE - NOT ACTUAL):
```java
// OLD: Query local database
List<ChestData> chests = databaseManager.getAllChests();
```

Change to:
```java
// NEW: Query ChestSyncManager for aggregated data
Collection<ChestSnapshot> chests = ChestSyncManager.getInstance().getAllChests();
```

### 2. Update ChestHighlighter

Current code (EXAMPLE):
```java
// OLD: Highlight chests from local database
boolean hasItem = databaseManager.chestHasItem(x, y, z, itemId);
```

Change to:
```java
// NEW: Highlight chests from ChestSyncManager
ChestSnapshot chest = ChestSyncManager.getInstance().getChestAt(x, y, z);
boolean hasItem = chest != null && chest.containsItem(itemId);
```

### 3. Show Ownership in UI

Add to UI rendering:
```java
ChestSnapshot chest = ChestSyncManager.getInstance().getChestAt(x, y, z);
if (chest != null && chest.openedByUsername != null) {
    // Render "Last opened by: PlayerName" text
    String label = "§7Last opened by: §f" + chest.openedByUsername;
    // ... rendering code ...
}
```

## Testing Checklist

- [ ] Build mod successfully (`gradlew.bat build`)
- [ ] Start backend server (`uvicorn app.main:app --reload`)
- [ ] Run Minecraft client (`gradlew.bat runClient`)
- [ ] Connect to server with mod installed
- [ ] Open a chest
- [ ] Verify backend logs show chest data received
- [ ] Verify WebSocket message received in client logs
- [ ] Check ChestSyncManager has data: `manager.getChestCount() > 0`
- [ ] Test with two clients opening same chest
- [ ] Verify both clients receive updates

## Debugging Tips

### Backend Logs
```bash
# Check if chest data received
grep "chest_update" backend_logs.txt

# Check WebSocket connections
grep "WebSocket authenticated" backend_logs.txt
```

### Minecraft Logs
```bash
# Check if chest data sent
grep "ChestSync-Upload" .minecraft/logs/latest.log

# Check WebSocket messages received
grep "InventoryNetwork-WebSocket" .minecraft/logs/latest.log

# Check ChestSyncManager activity
grep "InventoryNetwork-ChestSync" .minecraft/logs/latest.log
```

### Common Issues

**Issue**: Chest data not sent to backend
- Check: WebSocket connected? `WebSocketManager.getInstance().isConnected()`
- Check: JWT token exists? `WebSocketManager.getInstance().getJwtToken() != null`
- Check: ApiClient initialized? Should be passed to ChestTracker constructor

**Issue**: WebSocket not receiving updates
- Check: Backend logs show broadcast? Look for "broadcast_chest_update"
- Check: Player in correct structure? Updates are structure-scoped
- Check: WebSocket connection alive? Look for ping/pong messages

**Issue**: ChestSyncManager empty
- Check: `handleChestFullState()` called? Should happen on connection
- Check: JSON parsing errors? Look for exceptions in logs
- Check: Backend has chest data? Query `/api/mc/chests` endpoint

## File Locations

```
Mod Source:
  C:\Users\mifan\Documents\GitHub\MinecraftMods\InventoryNetwork\InventoryNetwork\src\

Key Files:
  client\java\com\BookKeeper\InventoryNetwork\ChestSyncManager.java
  client\java\com\BookKeeper\InventoryNetwork\ChestTracker.java
  client\java\com\BookKeeper\InventoryNetwork\WebSocketManager.java
  client\java\com\BookKeeper\InventoryNetwork\InventoryNetworkModClient.java
  main\java\com\BookKeeper\InventoryNetwork\ApiClient.java

Build:
  gradlew.bat build
  Output: build\libs\InventoryNetwork-*.jar
```

## API Reference

### ChestSyncManager API

```java
// Singleton access
ChestSyncManager manager = ChestSyncManager.getInstance();

// Query methods
Collection<ChestSnapshot> getAllChests()
ChestSnapshot getChestAt(int x, int y, int z)
List<ChestSnapshot> findChestsWithItem(String itemId)
int getChestCount()

// Listeners
void addUpdateListener(Consumer<ChestUpdate> listener)

// Message handlers (called by WebSocketManager)
void handleFullState(JsonObject message)
void handleChestUpdate(JsonObject message)

// Cleanup
void clear()  // Called on disconnect
```

### ChestSnapshot Fields

```java
public class ChestSnapshot {
    public final int x, y, z;                    // Coordinates
    public final JsonObject items;                // {"0": {"id": "...", "count": 5}}
    public final JsonObject signs;                // Sign data (optional)
    public final String openedByUuid;             // Player UUID
    public final String openedByUsername;         // Player name
    public final String lastSeenAt;               // ISO timestamp

    // Helper methods
    public boolean containsItem(String itemId)
    public int getItemCount(String itemId)
}
```

---

**Last Updated**: 2025-11-28
**Full Documentation**: `C:\BookKeeper\BackendBK\CHESTSYNC_DEVELOPMENT.md`

