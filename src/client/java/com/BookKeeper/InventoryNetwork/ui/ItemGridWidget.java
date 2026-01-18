package com.BookKeeper.InventoryNetwork.ui;

import com.BookKeeper.InventoryNetwork.ChestSyncManager;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grid widget that displays items from the database.
 * Based on JEI's IngredientGrid pattern.
 */
public class ItemGridWidget {
	private final int x;
	private final int y;
	private final int columns;
	private final int rows;
	private final List<ItemSlot> slots;

	private List<ItemStack> allItems;
	private List<ItemStack> filteredItems;
	private int currentPage;
	private String filterText;

	public ItemGridWidget(int x, int y, int columns, int rows) {
		this.x = x;
		this.y = y;
		this.columns = columns;
		this.rows = rows;
		this.slots = new ArrayList<>();
		this.allItems = new ArrayList<>();
		this.filteredItems = new ArrayList<>();
		this.currentPage = 0;
		this.filterText = "";

		createSlots();
	}

	/**
	 * Creates the grid of slots based on rows and columns.
	 */
	private void createSlots() {
		slots.clear();
		for (int row = 0; row < rows; row++) {
			for (int col = 0; col < columns; col++) {
				int slotX = x + col * ItemSlot.SLOT_SIZE;
				int slotY = y + row * ItemSlot.SLOT_SIZE;
				slots.add(new ItemSlot(slotX, slotY));
			}
		}
	}

	/**
	 * Loads items from ChestSyncManager (server as source of truth).
	 * Aggregates all items across all synced chests.
	 */
	public void loadItems(ChestSyncManager chestSync) {
		allItems.clear();

		// Aggregate items from all chests
		Map<String, Integer> itemCounts = new HashMap<>();

		for (ChestSyncManager.ChestSnapshot chest : chestSync.getAllChests()) {
			if (chest.items == null) continue;

			// Iterate through all slots in the chest
			for (String slotKey : chest.items.keySet()) {
				JsonObject itemObj = chest.items.getAsJsonObject(slotKey);
				if (itemObj.has("id")) {
					String itemId = itemObj.get("id").getAsString();
					int count = itemObj.has("count") ? itemObj.get("count").getAsInt() : 1;

					// Aggregate counts
					itemCounts.put(itemId, itemCounts.getOrDefault(itemId, 0) + count);
				}
			}
		}

		// Convert aggregated data to ItemStack list
		for (Map.Entry<String, Integer> entry : itemCounts.entrySet()) {
			Item item = getItemFromId(entry.getKey());
			if (item != null && item != Items.AIR) {
				ItemStack stack = new ItemStack(item);
				stack.setCount(entry.getValue());
				allItems.add(stack);
			}
		}

		applyFilter();
	}

	/**
	 * Converts item ID string to Item object.
	 */
	private Item getItemFromId(String itemId) {
		try {
			ResourceLocation id = ResourceLocation.parse(itemId);
			// BuiltInRegistries.ITEM.get() returns Optional<Reference<Item>>
			return BuiltInRegistries.ITEM.get(id)
				.map(ref -> ref.value())
				.orElse(null);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Applies the current filter and updates visible items.
	 */
	public void applyFilter() {
		filteredItems.clear();

		if (filterText.isEmpty()) {
			filteredItems.addAll(allItems);
		} else {
			String lowerFilter = filterText.toLowerCase();
			for (ItemStack stack : allItems) {
				String itemName = stack.getHoverName().getString().toLowerCase();
				if (itemName.contains(lowerFilter)) {
					filteredItems.add(stack);
				}
			}
		}

		currentPage = 0; // Reset to first page
		updateSlots();
	}

	/**
	 * Sets the filter text and reapplies the filter.
	 */
	public void setFilter(String text) {
		this.filterText = text;
		applyFilter();
	}

	/**
	 * Updates slot contents based on current page.
	 */
	private void updateSlots() {
		int itemsPerPage = slots.size();
		int startIndex = currentPage * itemsPerPage;

		for (int i = 0; i < slots.size(); i++) {
			int itemIndex = startIndex + i;
			if (itemIndex < filteredItems.size()) {
				slots.get(i).setItem(filteredItems.get(itemIndex));
			} else {
				slots.get(i).setItem(ItemStack.EMPTY);
			}
		}
	}

	/**
	 * Goes to the next page if available.
	 */
	public void nextPage() {
		if (hasNextPage()) {
			currentPage++;
			updateSlots();
		}
	}

	/**
	 * Goes to the previous page if available.
	 */
	public void previousPage() {
		if (hasPreviousPage()) {
			currentPage--;
			updateSlots();
		}
	}

	/**
	 * Checks if there is a next page.
	 */
	public boolean hasNextPage() {
		return (currentPage + 1) * slots.size() < filteredItems.size();
	}

	/**
	 * Checks if there is a previous page.
	 */
	public boolean hasPreviousPage() {
		return currentPage > 0;
	}

	/**
	 * Gets the current page number (0-indexed).
	 */
	public int getCurrentPage() {
		return currentPage;
	}

	/**
	 * Gets the total number of pages.
	 */
	public int getTotalPages() {
		if (filteredItems.isEmpty()) return 1;
		return (int) Math.ceil((double) filteredItems.size() / slots.size());
	}

	/**
	 * Renders the grid.
	 */
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		for (ItemSlot slot : slots) {
			slot.render(guiGraphics, mouseX, mouseY);
		}
	}

	/**
	 * Handles mouse click on the grid.
	 * @return The clicked ItemStack, or null if no item was clicked
	 */
	public ItemStack handleClick(double mouseX, double mouseY) {
		for (ItemSlot slot : slots) {
			if (slot.isMouseOver(mouseX, mouseY)) {
				return slot.getItemStack();
			}
		}
		return null;
	}

	/**
	 * Gets the item currently under the mouse.
	 */
	public ItemStack getItemUnderMouse(double mouseX, double mouseY) {
		for (ItemSlot slot : slots) {
			if (slot.isMouseOver(mouseX, mouseY)) {
				return slot.getItemStack();
			}
		}
		return null;
	}

	/**
	 * Gets the width of the grid in pixels.
	 */
	public int getWidth() {
		return columns * ItemSlot.SLOT_SIZE;
	}

	/**
	 * Gets the height of the grid in pixels.
	 */
	public int getHeight() {
		return rows * ItemSlot.SLOT_SIZE;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}

	public int getColumns() {
		return columns;
	}

	public int getRows() {
		return rows;
	}
}
