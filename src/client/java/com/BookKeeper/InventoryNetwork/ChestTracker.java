package com.BookKeeper.InventoryNetwork;

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

/**
 * Handles chest detection, content reading, and database storage
 */
public class ChestTracker {
	private final DatabaseManager databaseManager;

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

	public ChestTracker(DatabaseManager databaseManager) {
		this.databaseManager = databaseManager;
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
		if (databaseManager.isDebugLogsEnabled()) {
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
	 * Saves chest contents to database.
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
		if (!isClosing && contentsDisplay.length() > 0 && databaseManager.isDebugLogsEnabled()) {
			client.player.displayClientMessage(Component.literal(contentsDisplay.toString()), false);
		}

		// Save to database (this will overwrite old data with new data)
		databaseManager.saveChestData(
			normalizedPos.getX(),
			normalizedPos.getY(),
			normalizedPos.getZ(),
			dimension,
			contentsDB.toString()
		);

		// Display save message (if debug logs enabled)
		if (databaseManager.isDebugLogsEnabled()) {
			if (isClosing) {
				client.player.displayClientMessage(
					Component.literal("§a[Inventory Network] Chest data updated on close!"),
					true
				);
			} else {
				int totalChests = databaseManager.getTotalChestCount();
				client.player.displayClientMessage(
					Component.literal("§a[Inventory Network] Saved to database! Total chests tracked: " + totalChests),
					false
				);
			}
		}
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
