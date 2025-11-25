package com.BookKeeper.InventoryNetwork;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;

public class DatabaseManager {
	private static final Logger LOGGER = LoggerFactory.getLogger("InventoryNetwork-DB");
	private static DatabaseManager instance;

	private Connection connection;
	private final String dbPath;

	// Debug logs toggle (default: false = disabled)
	private boolean debugLogsEnabled = false;

	private DatabaseManager(String dbPath) {
		this.dbPath = dbPath;
		initializeDatabase();
	}

	public static DatabaseManager getInstance(String dbPath) {
		if (instance == null) {
			instance = new DatabaseManager(dbPath);
		}
		return instance;
	}

	private void initializeDatabase() {
		try {
			// Create database directory if it doesn't exist
			File dbFile = new File(dbPath).getParentFile();
			if (dbFile != null && !dbFile.exists()) {
				dbFile.mkdirs();
			}

			// Connect to H2 database (creates it if it doesn't exist)
			String jdbcUrl = "jdbc:h2:" + dbPath + ";AUTO_SERVER=TRUE";
			connection = DriverManager.getConnection(jdbcUrl, "sa", "");

			LOGGER.info("Connected to H2 database at: {}", dbPath);

			// Create table if it doesn't exist
			createTablesIfNotExist();

		} catch (SQLException e) {
			LOGGER.error("Failed to initialize database", e);
		}
	}

	private void createTablesIfNotExist() throws SQLException {
		String createChestTableSQL = """
			CREATE TABLE IF NOT EXISTS chest_inventory (
				x INT NOT NULL,
				y INT NOT NULL,
				z INT NOT NULL,
				dimension VARCHAR(255) NOT NULL,
				contents TEXT,
				last_updated TIMESTAMP,
				PRIMARY KEY (x, y, z, dimension)
			)
		""";

		String createWhitelistTableSQL = """
			CREATE TABLE IF NOT EXISTS player_whitelist (
				player_name VARCHAR(255) NOT NULL,
				player_uuid VARCHAR(36),
				added_at TIMESTAMP,
				PRIMARY KEY (player_name)
			)
		""";

		String createBlacklistTableSQL = """
			CREATE TABLE IF NOT EXISTS player_blacklist (
				player_name VARCHAR(255) NOT NULL,
				player_uuid VARCHAR(36),
				added_at TIMESTAMP,
				PRIMARY KEY (player_name)
			)
		""";

		try (Statement stmt = connection.createStatement()) {
			stmt.execute(createChestTableSQL);
			stmt.execute(createWhitelistTableSQL);
			stmt.execute(createBlacklistTableSQL);
			// Ensure existing databases get the player_uuid column if they lack it
			try {
				stmt.execute("ALTER TABLE player_whitelist ADD COLUMN IF NOT EXISTS player_uuid VARCHAR(36);");
			} catch (SQLException ignored) {
			}
			try {
				stmt.execute("ALTER TABLE player_blacklist ADD COLUMN IF NOT EXISTS player_uuid VARCHAR(36);");
			} catch (SQLException ignored) {
			}
			LOGGER.info("Database schema initialized");
		}
	}

	/**
	 * Saves or updates chest data in the database.
	 * If a chest at the same coordinates already exists, it will be updated.
	 *
	 * @param x X coordinate
	 * @param y Y coordinate
	 * @param z Z coordinate
	 * @param dimension Dimension identifier (e.g., "minecraft:overworld")
	 * @param contents JSON or text representation of chest contents
	 */
	public void saveChestData(int x, int y, int z, String dimension, String contents) {
		String mergeSQL = """
			MERGE INTO chest_inventory (x, y, z, dimension, contents, last_updated)
			KEY (x, y, z, dimension)
			VALUES (?, ?, ?, ?, ?, ?)
		""";

		try (PreparedStatement pstmt = connection.prepareStatement(mergeSQL)) {
			pstmt.setInt(1, x);
			pstmt.setInt(2, y);
			pstmt.setInt(3, z);
			pstmt.setString(4, dimension);
			pstmt.setString(5, contents);
			pstmt.setTimestamp(6, Timestamp.valueOf(LocalDateTime.now()));

			int rowsAffected = pstmt.executeUpdate();
			LOGGER.info("Saved chest data at [{}, {}, {}] in {} - Rows affected: {}",
				x, y, z, dimension, rowsAffected);

		} catch (SQLException e) {
			LOGGER.error("Failed to save chest data at [{}, {}, {}]", x, y, z, e);
		}
	}

	/**
	 * Retrieves chest data from the database.
	 *
	 * @param x X coordinate
	 * @param y Y coordinate
	 * @param z Z coordinate
	 * @param dimension Dimension identifier
	 * @return Contents string or null if not found
	 */
	public String getChestData(int x, int y, int z, String dimension) {
		String selectSQL = """
			SELECT contents FROM chest_inventory
			WHERE x = ? AND y = ? AND z = ? AND dimension = ?
		""";

		try (PreparedStatement pstmt = connection.prepareStatement(selectSQL)) {
			pstmt.setInt(1, x);
			pstmt.setInt(2, y);
			pstmt.setInt(3, z);
			pstmt.setString(4, dimension);

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					return rs.getString("contents");
				}
			}

		} catch (SQLException e) {
			LOGGER.error("Failed to retrieve chest data at [{}, {}, {}]", x, y, z, e);
		}

		return null;
	}

	/**
	 * Returns the total number of chests stored in the database.
	 */
	public int getTotalChestCount() {
		String countSQL = "SELECT COUNT(*) as total FROM chest_inventory";

		try (Statement stmt = connection.createStatement();
		     ResultSet rs = stmt.executeQuery(countSQL)) {
			if (rs.next()) {
				return rs.getInt("total");
			}
		} catch (SQLException e) {
			LOGGER.error("Failed to count chests", e);
		}

		return 0;
	}

	/**
	 * Finds all chests within a certain radius that contain a specific item.
	 *
	 * @param centerX Center X coordinate
	 * @param centerY Center Y coordinate
	 * @param centerZ Center Z coordinate
	 * @param radius Search radius
	 * @param dimension Dimension identifier
	 * @param itemName Item display name to search for
	 * @return List of chest positions [x, y, z] that contain the item
	 */
	public java.util.List<int[]> findChestsWithItem(int centerX, int centerY, int centerZ,
	                                                  int radius, String dimension, String itemName) {
		java.util.List<int[]> results = new java.util.ArrayList<>();

		String searchSQL = """
			SELECT x, y, z, contents FROM chest_inventory
			WHERE dimension = ?
			AND x BETWEEN ? AND ?
			AND y BETWEEN ? AND ?
			AND z BETWEEN ? AND ?
		""";

		try (PreparedStatement pstmt = connection.prepareStatement(searchSQL)) {
			pstmt.setString(1, dimension);
			pstmt.setInt(2, centerX - radius);
			pstmt.setInt(3, centerX + radius);
			pstmt.setInt(4, centerY - radius);
			pstmt.setInt(5, centerY + radius);
			pstmt.setInt(6, centerZ - radius);
			pstmt.setInt(7, centerZ + radius);

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String contents = rs.getString("contents");

					// Check if item name appears in contents
					if (contents != null && !contents.equals("EMPTY") &&
						contents.toLowerCase().contains(itemName.toLowerCase())) {
						int[] pos = new int[3];
						pos[0] = rs.getInt("x");
						pos[1] = rs.getInt("y");
						pos[2] = rs.getInt("z");
						results.add(pos);
					}
				}
			}

		} catch (SQLException e) {
			LOGGER.error("Failed to search for chests with item: {}", itemName, e);
		}

		return results;
	}

	/**
	 * Clears all data from the database.
	 */
	public void clearDatabase() {
		String deleteSQL = "DELETE FROM chest_inventory";

		try (Statement stmt = connection.createStatement()) {
			int rowsDeleted = stmt.executeUpdate(deleteSQL);
			LOGGER.info("Database cleared. Deleted {} rows", rowsDeleted);
		} catch (SQLException e) {
			LOGGER.error("Failed to clear database", e);
		}
	}

	/**
	 * Adds a player to the whitelist (or updates timestamp if exists).
	 */
	public boolean addToWhitelist(String playerName) {
		String sql = """
			MERGE INTO player_whitelist (player_name, added_at)
			KEY (player_name)
			VALUES (?, ?)
		""";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, playerName.toLowerCase()); // store lowercase to avoid case mismatches
			pstmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
			int rows = pstmt.executeUpdate();
			LOGGER.info("Added/updated whitelist player: {} (rows={})", playerName.toLowerCase(), rows);
			return true;
		} catch (SQLException e) {
			LOGGER.error("Failed to add to whitelist: {}", playerName, e);
			return false;
		}
	}

	/**
	 * Adds a player to the whitelist specifying UUID when available.
	 */
	public boolean addToWhitelist(String playerName, String playerUuid) {
		String sql = """
			MERGE INTO player_whitelist (player_name, player_uuid, added_at)
			KEY (player_name)
			VALUES (?, ?, ?)
		""";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, playerName.toLowerCase());
			pstmt.setString(2, playerUuid != null ? playerUuid : null);
			pstmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
			int rows = pstmt.executeUpdate();
			LOGGER.info("Added/updated whitelist player: {} uuid={} (rows={})", playerName.toLowerCase(), playerUuid, rows);
			return true;
		} catch (SQLException e) {
			LOGGER.error("Failed to add to whitelist: {}", playerName, e);
			return false;
		}
	}

	/**
	 * Removes a player from the whitelist.
	 */
	public boolean removeFromWhitelist(String playerName) {
		String sql = "DELETE FROM player_whitelist WHERE player_name = ?";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, playerName.toLowerCase());
			int rows = pstmt.executeUpdate();
			LOGGER.info("Removed whitelist player: {} (rows={})", playerName.toLowerCase(), rows);
			return rows > 0;
		} catch (SQLException e) {
			LOGGER.error("Failed to remove from whitelist: {}", playerName, e);
			return false;
		}
	}

	/**
	 * Adds a player to the blacklist (or updates timestamp if exists).
	 */
	public boolean addToBlacklist(String playerName) {
		String sql = """
			MERGE INTO player_blacklist (player_name, added_at)
			KEY (player_name)
			VALUES (?, ?)
		""";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, playerName.toLowerCase()); // store lowercase
			pstmt.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
			int rows = pstmt.executeUpdate();
			LOGGER.info("Added/updated blacklist player: {} (rows={})", playerName.toLowerCase(), rows);
			return true;
		} catch (SQLException e) {
			LOGGER.error("Failed to add to blacklist: {}", playerName, e);
			return false;
		}
	}

	/**
	 * Adds a player to the blacklist specifying UUID when available.
	 */
	public boolean addToBlacklist(String playerName, String playerUuid) {
		String sql = """
			MERGE INTO player_blacklist (player_name, player_uuid, added_at)
			KEY (player_name)
			VALUES (?, ?, ?)
		""";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, playerName.toLowerCase());
			pstmt.setString(2, playerUuid != null ? playerUuid : null);
			pstmt.setTimestamp(3, Timestamp.valueOf(LocalDateTime.now()));
			int rows = pstmt.executeUpdate();
			LOGGER.info("Added/updated blacklist player: {} uuid={} (rows={})", playerName.toLowerCase(), playerUuid, rows);
			return true;
		} catch (SQLException e) {
			LOGGER.error("Failed to add to blacklist: {}", playerName, e);
			return false;
		}
	}

	/**
	 * Removes a player from the blacklist.
	 */
	public boolean removeFromBlacklist(String playerName) {
		String sql = "DELETE FROM player_blacklist WHERE player_name = ?";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, playerName.toLowerCase());
			int rows = pstmt.executeUpdate();
			LOGGER.info("Removed blacklist player: {} (rows={})", playerName.toLowerCase(), rows);
			return rows > 0;
		} catch (SQLException e) {
			LOGGER.error("Failed to remove from blacklist: {}", playerName, e);
			return false;
		}
	}

	/**
	 * Checks if a player is whitelisted by name.
	 */
	public boolean isWhitelisted(String playerName) {
		String sql = "SELECT COUNT(*) as cnt FROM player_whitelist WHERE player_name = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, playerName.toLowerCase());
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) return rs.getInt("cnt") > 0;
			}
		} catch (SQLException e) {
			LOGGER.error("Failed to check whitelist for {}", playerName, e);
		}
		return false;
	}

	/**
	 * Checks if a player is whitelisted by UUID.
	 */
	public boolean isWhitelistedByUuid(String playerUuid) {
		if (playerUuid == null) return false;
		String sql = "SELECT COUNT(*) as cnt FROM player_whitelist WHERE player_uuid = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, playerUuid);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) return rs.getInt("cnt") > 0;
			}
		} catch (SQLException e) {
			LOGGER.error("Failed to check whitelist for uuid {}", playerUuid, e);
		}
		return false;
	}

	/**
	 * Checks if a player is blacklisted by name.
	 */
	public boolean isBlacklisted(String playerName) {
		String sql = "SELECT COUNT(*) as cnt FROM player_blacklist WHERE player_name = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, playerName.toLowerCase());
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) return rs.getInt("cnt") > 0;
			}
		} catch (SQLException e) {
			LOGGER.error("Failed to check blacklist for {}", playerName, e);
		}
		return false;
	}

	/**
	 * Checks if a player is blacklisted by UUID.
	 */
	public boolean isBlacklistedByUuid(String playerUuid) {
		if (playerUuid == null) return false;
		String sql = "SELECT COUNT(*) as cnt FROM player_blacklist WHERE player_uuid = ?";
		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			pstmt.setString(1, playerUuid);
			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) return rs.getInt("cnt") > 0;
			}
		} catch (SQLException e) {
			LOGGER.error("Failed to check blacklist for uuid {}", playerUuid, e);
		}
		return false;
	}

	/**
	 * Returns all whitelisted player names.
	 */
	public java.util.List<String> getAllWhitelisted() {
		java.util.List<String> results = new java.util.ArrayList<>();
		String sql = "SELECT player_name FROM player_whitelist ORDER BY player_name";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			while (rs.next()) {
				results.add(rs.getString("player_name"));
			}
		} catch (SQLException e) {
			LOGGER.error("Failed to retrieve whitelist", e);
		}
		return results;
	}

	/**
	 * Returns all blacklisted player names.
	 */
	public java.util.List<String> getAllBlacklisted() {
		java.util.List<String> results = new java.util.ArrayList<>();
		String sql = "SELECT player_name FROM player_blacklist ORDER BY player_name";
		try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
			while (rs.next()) {
				results.add(rs.getString("player_name"));
			}
		} catch (SQLException e) {
			LOGGER.error("Failed to retrieve blacklist", e);
		}
		return results;
	}

	/**
	 * Helper class to store item information.
	 */
	public static class ItemData {
		public final String itemId;
		public final String displayName;
		public int totalCount;

		public ItemData(String itemId, String displayName, int count) {
			this.itemId = itemId;
			this.displayName = displayName;
			this.totalCount = count;
		}
	}

	/**
	 * Gets all unique items from all chests across all dimensions.
	 *
	 * @return List of ItemData with item IDs, display names, and total counts
	 */
	public java.util.List<ItemData> getAllUniqueItems() {
		return getAllUniqueItemsInDimension(null);
	}

	/**
	 * Gets all unique items from chests in a specific dimension.
	 *
	 * @param dimension Dimension identifier, or null for all dimensions
	 * @return List of ItemData with item IDs, display names, and total counts
	 */
	public java.util.List<ItemData> getAllUniqueItemsInDimension(String dimension) {
		java.util.Map<String, ItemData> itemMap = new java.util.HashMap<>();

		String sql = dimension == null
			? "SELECT contents FROM chest_inventory WHERE contents IS NOT NULL AND contents != 'EMPTY'"
			: "SELECT contents FROM chest_inventory WHERE dimension = ? AND contents IS NOT NULL AND contents != 'EMPTY'";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			if (dimension != null) {
				pstmt.setString(1, dimension);
			}

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String contents = rs.getString("contents");
					parseContentsIntoMap(contents, itemMap);
				}
			}

		} catch (SQLException e) {
			LOGGER.error("Failed to retrieve unique items", e);
		}

		return new java.util.ArrayList<>(itemMap.values());
	}

	/**
	 * Parses chest contents string and adds items to the map.
	 * Contents format: "slot|itemId|count|displayName;slot|itemId|count|displayName"
	 */
	private void parseContentsIntoMap(String contents, java.util.Map<String, ItemData> itemMap) {
		if (contents == null || contents.equals("EMPTY")) {
			return;
		}

		String[] items = contents.split(";");
		for (String item : items) {
			String[] parts = item.split("\\|");
			if (parts.length >= 4) {
				// parts[0] = slot, parts[1] = itemId, parts[2] = count, parts[3] = displayName
				String itemId = parts[1];
				int count = Integer.parseInt(parts[2]);
				String displayName = parts[3];

				if (itemMap.containsKey(itemId)) {
					itemMap.get(itemId).totalCount += count;
				} else {
					itemMap.put(itemId, new ItemData(itemId, displayName, count));
				}
			}
		}
	}

	/**
	 * Gets the total count of a specific item across all chests in a dimension.
	 *
	 * @param itemName Item display name to search for
	 * @param dimension Dimension identifier, or null for all dimensions
	 * @return Total count of the item across all chests
	 */
	public int getItemCount(String itemName, String dimension) {
		int totalCount = 0;

		String sql = dimension == null
			? "SELECT contents FROM chest_inventory WHERE contents LIKE ?"
			: "SELECT contents FROM chest_inventory WHERE dimension = ? AND contents LIKE ?";

		try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
			String searchPattern = "%" + itemName + "%";

			if (dimension == null) {
				pstmt.setString(1, searchPattern);
			} else {
				pstmt.setString(1, dimension);
				pstmt.setString(2, searchPattern);
			}

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					String contents = rs.getString("contents");
					totalCount += countItemInContents(contents, itemName);
				}
			}

		} catch (SQLException e) {
			LOGGER.error("Failed to count items: {}", itemName, e);
		}

		return totalCount;
	}

	/**
	 * Counts occurrences of an item in a contents string.
	 */
	private int countItemInContents(String contents, String itemName) {
		if (contents == null || contents.equals("EMPTY")) {
			return 0;
		}

		int total = 0;
		String[] items = contents.split(";");
		for (String item : items) {
			String[] parts = item.split("\\|");
			if (parts.length >= 4) {
				String displayName = parts[3];
				if (displayName.toLowerCase().contains(itemName.toLowerCase())) {
					total += Integer.parseInt(parts[2]);
				}
			}
		}
		return total;
	}

	/**
	 * Closes the database connection.
	 */
	public void close() {
		if (connection != null) {
			try {
				connection.close();
				LOGGER.info("Database connection closed");
			} catch (SQLException e) {
				LOGGER.error("Failed to close database connection", e);
			}
		}
	}

	// Debug logs toggle methods
	public boolean isDebugLogsEnabled() {
		return debugLogsEnabled;
	}

	public void setDebugLogsEnabled(boolean enabled) {
		this.debugLogsEnabled = enabled;
		LOGGER.info("Debug logs " + (enabled ? "enabled" : "disabled"));
	}
}
