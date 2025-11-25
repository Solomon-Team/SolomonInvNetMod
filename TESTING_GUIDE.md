# Minecraft Mod Testing Guide - BookKeeper Magic Auth

**Last Updated:** 2025-11-22

---

## ✅ What Has Been Implemented

### Minecraft Mod Features

1. **Configuration System**
   - File: `BookKeeperConfig.java`
   - Auto-creates config at: `.minecraft/config/inventory_network.json`
   - Default settings:
     ```json
     {
       "apiBaseUrl": "http://localhost:8000",
       "autoMagicLink": true,
       "magicLinkCooldownSeconds": 60
     }
     ```

2. **API Client**
   - File: `ApiClient.java`
   - Methods:
     - `requestMagicLink(UUID, String)` → Returns magic URL
     - `joinStructure(UUID, String)` → Joins structure with code
   - Uses OkHttp for HTTP requests
   - Handles errors gracefully

3. **Player Join Event**
   - Automatically triggers when player joins server/world
   - Requests magic link from backend
   - Displays clickable URL in chat
   - Respects cooldown (60 seconds by default)
   - Shows welcome message for new users

4. **Commands**
   - `/join <code>` - Join a structure using invite code
   - `/leave` - Leave current structure (placeholder, shows message to use website)

---

## 🔧 Setup Instructions

### 1. Start the Backend Server

```bash
cd C:\BookKeeper\BackendBK

# Activate virtual environment
.venv\Scripts\activate

# Run the server
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

**Verify Backend is Running:**
- Open browser to: http://localhost:8000/docs
- You should see the FastAPI documentation page
- Check that the seed data loaded (look for console output showing demo users created)

### 2. Build the Minecraft Mod

```bash
cd C:\Users\mifan\Documents\GitHub\MinecraftMods\InventoryNetwork\InventoryNetwork

# Build the mod (this will download dependencies)
.\gradlew.bat build
```

**Expected Output:**
- Build should succeed
- Jar file created at: `build/libs/inventory-network-<version>.jar`
- OkHttp and Gson dependencies should be included

**If Build Fails:**
- Check that Java is installed: `java -version`
- Delete `.gradle` folder and try again
- Check internet connection (downloads dependencies)

### 3. Install the Mod

1. Copy the jar from `build/libs/` to your Minecraft `.minecraft/mods` folder
2. Make sure you have Fabric Loader installed
3. Make sure you have Fabric API installed

### 4. Configure the Mod (Optional)

The mod will auto-create a config file on first run. If you need to change the API URL:

**File:** `.minecraft/config/inventory_network.json`

```json
{
  "apiBaseUrl": "http://localhost:8000",
  "autoMagicLink": true,
  "magicLinkCooldownSeconds": 60
}
```

**Settings:**
- `apiBaseUrl`: Backend server URL (change if running on different host)
- `autoMagicLink`: Enable/disable automatic magic link on join
- `magicLinkCooldownSeconds`: Minimum seconds between magic link requests

---

## 🧪 Test Cases

### Test 1: Auto Magic Link on Join (New User)

**Steps:**
1. Start Minecraft with the mod installed
2. Join any server or single-player world
3. Watch the chat

**Expected Result:**
```
[BookKeeper] Click here to login: [OPEN WEBSITE]
[BookKeeper] Welcome! This is your first time. Click the link above to set up your account.
```

**What Happens:**
- Mod detects player join event
- Sends `{ mcUuid, mcName }` to `POST /api/mc/magic-link`
- Backend creates new user (if first time)
- Backend generates magic token (expires in 5 minutes)
- Backend returns magic URL
- Mod displays clickable message in chat

**Check Backend Console:**
```
INFO:     127.0.0.1:xxxxx - "POST /api/mc/magic-link HTTP/1.1" 200 OK
[OK] Created user: <YourMinecraftName> (structure: None)
```

**Check Minecraft Logs:** `.minecraft/logs/latest.log`
```
[Inventory Network] Requesting magic login link for player: <YourName> (<UUID>)
[Inventory Network] Magic link sent to player: http://localhost:5173/#/magic-login/<token>
```

---

### Test 2: Click Magic Link

**Steps:**
1. Click the `[OPEN WEBSITE]` link in chat
2. Your default browser should open

**Expected Result:**
- Browser opens to: `http://localhost:5173/#/magic-login/<token>`
- **NOTE:** Frontend is not yet implemented, so you'll get a 404 or blank page
- This is expected! The URL is correct, just the Vue app isn't built yet

**To Verify Token is Valid (Manual Test):**
Use curl or Postman to exchange the token:

```bash
curl -X POST http://localhost:8000/api/auth/magic-login \
  -H "Content-Type: application/json" \
  -d "{\"token\": \"<paste-token-from-url>\"}"
```

**Expected Response:**
```json
{
  "access_token": "eyJhbGc...",
  "token_type": "bearer",
  "user": {
    "userId": 4,
    "mcUuid": "your-uuid",
    "mcName": "YourMinecraftName",
    "loginName": null,
    "hasPassword": false,
    "structureId": null,
    "roles": []
  }
}
```

**Check Database:**
```sql
-- Connect to PostgreSQL
psql -U postgres -d bookkeeper_v2

-- Check user was created
SELECT id, mc_uuid, mc_name, login_name, has_password, structure_id FROM users;

-- Check magic token was created and used
SELECT token, expires_at, used_at FROM magic_login_tokens WHERE token = '<your-token>';
```

---

### Test 3: Magic Link Cooldown

**Steps:**
1. Join a world (magic link appears)
2. Leave the world immediately
3. Rejoin the world within 60 seconds

**Expected Result:**
- **NO** magic link message appears the second time
- Must wait 60 seconds before next request

**Check Logs:**
- No API request is made (cooldown prevents it)

---

### Test 4: Join Structure with `/join` Command

**Prerequisites:**
- Backend must be running
- You need a valid join code (get from demo data)

**Get a Join Code:**
```bash
# Check database for join codes
psql -U postgres -d bookkeeper_v2
SELECT code, structure_id, is_active FROM structure_join_codes;
```

**Example Output:**
```
    code     | structure_id | is_active
-------------+--------------+-----------
 GPR-6ORQEY  | GPR          | t
```

**Steps:**
1. In Minecraft, open chat (T key)
2. Type: `/join GPR-6ORQEY` (use your actual code)
3. Press Enter

**Expected Result:**
```
[BookKeeper] Joining structure with code: GPR-6ORQEY...
[BookKeeper] Successfully joined Golden Prosperity
```

**Check Backend Console:**
```
INFO:     127.0.0.1:xxxxx - "POST /api/mc/join-structure HTTP/1.1" 200 OK
```

**Check Database:**
```sql
SELECT mc_name, structure_id FROM users WHERE mc_uuid = 'your-uuid';
```

**Expected:**
```
   mc_name    | structure_id
--------------+--------------
 YourName     | GPR
```

---

### Test 5: Join Structure - Error Cases

**Test 5a: Invalid Code**

```
/join INVALID-CODE
```

**Expected:**
```
[BookKeeper] Error: Invalid or inactive join code
```

---

**Test 5b: Already in Structure**

```
/join GPR-6ORQEY    (first time - works)
/join WHB-XXXXXX    (second time - should fail)
```

**Expected:**
```
[BookKeeper] Error: You are already in structure 'GPR'. Please leave first.
```

---

**Test 5c: Expired Code**

Create an expired code in database:
```sql
INSERT INTO structure_join_codes (code, structure_id, created_by_user_id, expires_at, is_active, used_count)
VALUES ('EXPIRED-CODE', 'GPR', 1, NOW() - INTERVAL '1 day', true, 0);
```

```
/join EXPIRED-CODE
```

**Expected:**
```
[BookKeeper] Error: Join code has expired
```

---

### Test 6: Auto Magic Link on Existing User

**Steps:**
1. Join a world (creates user + shows magic link)
2. Wait 60+ seconds
3. Leave and rejoin the world

**Expected Result:**
```
[BookKeeper] Click here to login: [OPEN WEBSITE]
```

**Note:** No "Welcome! This is your first time" message (because user already exists)

**Check Backend Console:**
```
INFO:     127.0.0.1:xxxxx - "POST /api/mc/magic-link HTTP/1.1" 200 OK
```

**Response includes:** `"isNewUser": false`

---

### Test 7: Config Modification

**Steps:**
1. Exit Minecraft
2. Edit `.minecraft/config/inventory_network.json`
3. Change `"autoMagicLink": false`
4. Save file
5. Start Minecraft and join a world

**Expected Result:**
- **NO** magic link message appears
- Config was respected

**Revert:**
1. Exit Minecraft
2. Change back to `"autoMagicLink": true`
3. Restart and rejoin

**Expected:**
- Magic link appears again

---

### Test 8: Backend Offline

**Steps:**
1. Stop the backend server (Ctrl+C)
2. In Minecraft, join a world

**Expected Result:**
- Nothing happens (async call fails silently)
- Check Minecraft logs for error:

```
[Inventory Network] Failed to request magic link
```

**No crash, no blocking of game startup**

---

### Test 9: Database Check - Audit Logs

**Query:**
```sql
SELECT event_type, mc_uuid, created_at, event_metadata
FROM auth_audit_log
ORDER BY created_at DESC
LIMIT 10;
```

**Expected Events:**
- `magic_link_request` - When player joined
- `structure_joined` - When player used `/join`

**Example:**
```
    event_type     |             mc_uuid              |         created_at         | event_metadata
-------------------+----------------------------------+----------------------------+----------------
 structure_joined  | 550e8400-e29b-41d4-a716-...      | 2024-01-15 10:30:00+00     | {"structure_id": "GPR", "code_id": 1}
 magic_link_request| 550e8400-e29b-41d4-a716-...      | 2024-01-15 10:25:00+00     | {"is_new_user": true}
```

---

## 🐛 Troubleshooting

### Problem: No magic link appears

**Check:**
1. Backend is running: http://localhost:8000/docs
2. Config file has correct URL: `.minecraft/config/inventory_network.json`
3. Cooldown hasn't blocked it (wait 60 seconds)
4. Check Minecraft logs: `.minecraft/logs/latest.log`

**Look for:**
```
[Inventory Network] BookKeeper API client initialized with URL: http://localhost:8000
[Inventory Network] Requesting magic login link for player: ...
```

**If you see:**
```
[Inventory Network] Failed to request magic link
```

Then check backend is accessible.

---

### Problem: `/join` command says "API client not initialized"

**Cause:** Config file has invalid URL or backend is unreachable

**Fix:**
1. Check `.minecraft/config/inventory_network.json`
2. Verify `apiBaseUrl` is correct
3. Restart Minecraft

---

### Problem: Build fails with "Could not resolve dependency"

**Cause:** Gradle can't download OkHttp or Gson

**Fix:**
1. Check internet connection
2. Delete `.gradle` folder in project root
3. Run `.\gradlew.bat build --refresh-dependencies`

---

### Problem: Mod crashes on startup

**Check:**
1. Fabric Loader is installed
2. Fabric API is installed
3. Minecraft version matches mod version
4. Check crash log: `.minecraft/crash-reports/`

---

### Problem: Clicking magic link does nothing

**Cause:** Browser security blocks `localhost` URLs

**Fix:**
1. Copy the URL manually from chat
2. Paste into browser
3. Or configure browser to allow `localhost` links

---

## 📊 Success Criteria

The Minecraft mod is working correctly if:

- [x] Config file is created automatically
- [x] Player join triggers magic link request
- [x] Magic link appears in chat with clickable URL
- [x] URL has correct format: `http://localhost:5173/#/magic-login/<token>`
- [x] New users see "Welcome" message
- [x] Cooldown prevents spam requests
- [x] `/join <code>` command works with valid code
- [x] Error messages display for invalid codes
- [x] Backend logs show API requests
- [x] Database shows new users created
- [x] Audit log captures events

---

## 📝 Demo User UUIDs

If you want to test with the demo users (e.g., to set your Minecraft UUID to match a demo user):

**Demo Users Created by Seed:**
1. DemoOwner
   - UUID: `550e8400-e29b-41d4-a716-446655440000`
   - Structure: GPR
   - Roles: OWNER

2. DemoAdmin
   - UUID: `550e8400-e29b-41d4-a716-446655440001`
   - Structure: GPR
   - Roles: ADMIN

3. DemoMember
   - UUID: `550e8400-e29b-41d4-a716-446655440002`
   - Structure: GPR
   - Roles: MEMBER

4. NewPlayer
   - UUID: `550e8400-e29b-41d4-a716-446655440003`
   - Structure: NULL
   - Roles: []

**To Use Demo UUID:**
You'd need to modify your Minecraft client to spoof UUID (not recommended for production, only testing).

---

## 🔄 Next Steps After Testing

Once the Minecraft mod is tested and working:

1. **Build Vue Frontend** - Implement the magic login page and other UI
2. **Test Full Flow** - Complete flow from Minecraft → Website → Password Setup
3. **Test Structure Management** - Create codes, kick members, etc.
4. **Deploy** - Deploy backend and frontend to production servers
5. **Update Mod Config** - Change `apiBaseUrl` to production URL

---

## 📞 Need Help?

**Check Logs:**
- Minecraft: `.minecraft/logs/latest.log`
- Backend: Console where uvicorn is running
- Database: `psql -U postgres -d bookkeeper_v2`

**Verify Endpoints:**
- http://localhost:8000/docs - API documentation
- http://localhost:8000/api/mc/magic-link - POST endpoint
- http://localhost:8000/api/mc/join-structure - POST endpoint

**Common Commands:**
```bash
# Check backend is running
curl http://localhost:8000/docs

# Check database
psql -U postgres -d bookkeeper_v2 -c "SELECT COUNT(*) FROM users;"

# View audit log
psql -U postgres -d bookkeeper_v2 -c "SELECT * FROM auth_audit_log ORDER BY created_at DESC LIMIT 5;"
```

---

**END OF TESTING GUIDE**
