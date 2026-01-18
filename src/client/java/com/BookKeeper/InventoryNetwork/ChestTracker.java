package com.BookKeeper.InventoryNetwork;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * Handles chest detection, content reading, and sending to backend server.
 * Server is the single source of truth - no local database storage.
 */
public class ChestTracker {
	private static final Logger LOGGER = LoggerFactory.getLogger("InventoryNetwork-ChestTracker");

	private final ApiClient apiClient;

	// Chest tracking state
	private BlockPos lastClickedChestPos = null;
	private BlockPos lastOpenedChestPos = null;
	private String lastOpenedChestDimension = null;

	// Delayed reading
	private ChestMenu pendingChestMenu = null;
	private int ticksToWait = 0;

	// Current open chest (for saving on close)
	private ChestMenu currentOpenChest = null;
	private BlockPos currentOpenChestPos = null;

	// Debug logging flag
	private static boolean debugLogsEnabled = false;

	public ChestTracker(ApiClient apiClient) {
		this.apiClient = apiClient;
	}

	public static void setDebugLogsEnabled(boolean enabled) {
		debugLogsEnabled = enabled;
	}

	public static boolean isDebugLogsEnabled() {
		return debugLogsEnabled;
	}

	public void onChestClicked(BlockPos pos) {
		this.lastClickedChestPos = pos;
	}

	public void onScreenOpen(Minecraft client, Screen screen) {
		if (screen instanceof AbstractContainerScreen<?> containerScreen) {
			if (containerScreen.getMenu() instanceof ChestMenu chestMenu) {
				pendingChestMenu = chestMenu;
				ticksToWait = 3; // Wait 3 ticks for inventory sync
			}
		}
	}

	public void tick(Minecraft client) {
		if (ticksToWait > 0) {
			ticksToWait--;
			if (ticksToWait == 0 && pendingChestMenu != null) {
				readChestContents(client, pendingChestMenu);
				pendingChestMenu = null;
			}
		}
	}

	private void readChestContents(Minecraft client, ChestMenu chestMenu) {
		if (client.player == null || client.level == null) {
			return;
		}

		BlockPos originalPos = lastClickedChestPos;
		if (originalPos == null) {
			return;
		}

		// Normalize position for double chests
		BlockPos normalizedPos = normalizeChestPosition(originalPos, client);
		String dimension = client.level.dimension().location().toString();

		// Determine chest type
		int chestSlots = chestMenu.getContainer().getContainerSize();
		boolean isDoubleChest = chestSlots == 54;
		String chestType = isDoubleChest ? "Double Chest" : "Single Chest";

		// Store for command access and for close event
		lastOpenedChestPos = normalizedPos;
		lastOpenedChestDimension = dimension;
		currentOpenChest = chestMenu;
		currentOpenChestPos = normalizedPos;

		// Display detection message (if debug logs enabled)
		if (debugLogsEnabled) {
			client.player.displayClientMessage(
				Component.literal("§e[Inventory Network] " + chestType + " detected at: " +
					normalizedPos.toShortString() + " in " + dimension),
				false
			);
		}

		// Save chest data (on open)
		saveChestData(client, chestMenu, normalizedPos, dimension, false);

		lastClickedChestPos = null;
	}

	/**
	 * Sends chest contents to backend server.
	 * Server is the single source of truth - no local database storage.
	 * Called both on chest open and close.
	 */
	private void saveChestData(Minecraft client, ChestMenu chestMenu, BlockPos normalizedPos, String dimension, boolean isClosing) {
		if (client.player == null) {
			return;
		}

		int chestSlots = chestMenu.getContainer().getContainerSize();

		// Read and build chest contents
		StringBuilder contentsDisplay = new StringBuilder();
		if (!isClosing) {
			contentsDisplay.append("§e[Inventory Network] Contents: ");
		}
		StringBuilder contentsDB = new StringBuilder();
		JsonObject containerJson = new JsonObject();
		boolean hasItems = false;

		for (int i = 0; i < chestSlots; i++) {
			ItemStack stack = chestMenu.getContainer().getItem(i);
			if (!stack.isEmpty()) {
				String itemString = stack.getCount() + "x " + stack.getHoverName().getString();

				if (hasItems) {
					if (!isClosing) {
						contentsDisplay.append(", ");
					}
					contentsDB.append(";");
				}

				if (!isClosing) {
					contentsDisplay.append(itemString);
				}
				contentsDB.append(i).append("|")
					.append(stack.getItem().toString()).append("|")
					.append(stack.getCount()).append("|")
					.append(stack.getHoverName().getString());

				// Build JSON for backend
				JsonObject itemObj = new JsonObject();
				itemObj.addProperty("id", stack.getItem().toString());
				itemObj.addProperty("count", stack.getCount());
				itemObj.addProperty("name", stack.getHoverName().getString());
				containerJson.add(String.valueOf(i), itemObj);

				hasItems = true;
			}
		}

		if (!hasItems) {
			if (!isClosing) {
				contentsDisplay.append("Empty");
			}
			contentsDB.append("EMPTY");
		}

		// Display contents only on open (if debug logs enabled)
		if (!isClosing && contentsDisplay.length() > 0 && debugLogsEnabled) {
			client.player.displayClientMessage(Component.literal(contentsDisplay.toString()), false);
		}

		// Send to backend for ChestSync (server is source of truth)
		sendChestDataToBackend(client, normalizedPos, containerJson);

		// Display save message (if debug logs enabled)
		if (debugLogsEnabled) {
			if (isClosing) {
				client.player.displayClientMessage(
					Component.literal("§a[Inventory Network] Chest data sent to server on close!"),
					true
				);
			} else {
				int totalChests = ChestSyncManager.getInstance().getChestCount();
				client.player.displayClientMessage(
					Component.literal("§a[Inventory Network] Sent to server! Total chests synced: " + totalChests),
					false
				);
			}
		}
	}

	/**
	 * Send chest data to backend for real-time synchronization.
	 * Runs asynchronously to avoid blocking the main thread.
	 */
	private void sendChestDataToBackend(Minecraft client, BlockPos pos, JsonObject containerJson) {
		// Get JWT token from WebSocketManager
		String jwtToken = WebSocketManager.getInstance().getJwtToken();
		if (jwtToken == null) {
			LOGGER.debug("Cannot send chest data: not connected to WebSocket");
			return;
		}

		if (client.player == null) {
			return;
		}

		UUID playerUuid = client.player.getUUID();
		String playerName = client.player.getName().getString();

		// Send asynchronously to avoid blocking game thread
		new Thread(() -> {
			try {
				boolean success = apiClient.sendChestData(
					jwtToken,
					playerUuid,
					playerName,
					pos.getX(),
					pos.getY(),
					pos.getZ(),
					containerJson,
					null  // Signs data (not implemented yet)
				);

				if (success) {
					LOGGER.debug("Sent chest data to backend at ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());
				} else {
					LOGGER.warn("Failed to send chest data to backend at ({}, {}, {})", pos.getX(), pos.getY(), pos.getZ());
				}
			} catch (Exception e) {
				LOGGER.error("Error sending chest data to backend", e);
			}
		}, "ChestSync-Upload").start();
	}

	/**
	 * Called when a chest screen is closed.
	 * Saves the current chest contents to database with updated data.
	 */
	public void onChestClose(Minecraft client) {
		if (currentOpenChest != null && currentOpenChestPos != null && client.level != null) {
			String dimension = client.level.dimension().location().toString();
			saveChestData(client, currentOpenChest, currentOpenChestPos, dimension, true);

			// Clear current chest tracking
			currentOpenChest = null;
			currentOpenChestPos = null;
		}
	}

	private BlockPos normalizeChestPosition(BlockPos pos, Minecraft client) {
		if (client.level == null) {
			return pos;
		}

		BlockState blockState = client.level.getBlockState(pos);
		if (!(blockState.getBlock() instanceof ChestBlock)) {
			return pos;
		}

		ChestType chestType = blockState.getValue(ChestBlock.TYPE);
		if (chestType == ChestType.SINGLE) {
			return pos;
		}

		// For double chests, find the "primary" position
		BlockPos neighborPos = pos.relative(ChestBlock.getConnectedDirection(blockState));

		// Return position with lower coordinates
		if (pos.getX() < neighborPos.getX()) return pos;
		if (pos.getX() > neighborPos.getX()) return neighborPos;
		if (pos.getY() < neighborPos.getY()) return pos;
		if (pos.getY() > neighborPos.getY()) return neighborPos;
		if (pos.getZ() < neighborPos.getZ()) return pos;
		return neighborPos;
	}

	// Getters for command access
	public BlockPos getLastOpenedChestPos() {
		return lastOpenedChestPos;
	}

	public String getLastOpenedChestDimension() {
		return lastOpenedChestDimension;
	}

	public void resetLastOpened() {
		lastOpenedChestPos = null;
		lastOpenedChestDimension = null;
	}
}
