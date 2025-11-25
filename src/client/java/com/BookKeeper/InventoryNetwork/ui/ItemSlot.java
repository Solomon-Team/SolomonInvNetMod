package com.BookKeeper.InventoryNetwork.ui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * Represents a single slot in the item grid.
 * Based on JEI's IngredientListSlot pattern.
 */
public class ItemSlot {
	public static final int SLOT_SIZE = 18; // 16px item + 1px padding on each side
	public static final int ITEM_SIZE = 16;

	private final int x;
	private final int y;
	private ItemStack itemStack;
	private boolean isBlocked; // If overlapped by other GUI elements

	public ItemSlot(int x, int y) {
		this.x = x;
		this.y = y;
		this.itemStack = ItemStack.EMPTY;
		this.isBlocked = false;
	}

	/**
	 * Sets the item to display in this slot.
	 */
	public void setItem(ItemStack item) {
		this.itemStack = item != null ? item : ItemStack.EMPTY;
	}

	/**
	 * Gets the current item stack.
	 */
	public ItemStack getItemStack() {
		return itemStack;
	}

	/**
	 * Checks if this slot is empty.
	 */
	public boolean isEmpty() {
		return itemStack.isEmpty();
	}

	/**
	 * Checks if mouse is over this slot.
	 */
	public boolean isMouseOver(double mouseX, double mouseY) {
		if (isEmpty() || isBlocked) {
			return false;
		}
		return mouseX >= x && mouseX < x + SLOT_SIZE &&
		       mouseY >= y && mouseY < y + SLOT_SIZE;
	}

	/**
	 * Renders the item in this slot.
	 * Matches JEI's IngredientGrid.java rendering exactly.
	 */
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (isEmpty() || isBlocked) {
			return;
		}

		// Draw slot background like JEI does
		// Slightly darker background for each slot to create depth
		guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x66000000);

		// Calculate centered position within slot (1px padding)
		int itemX = x + 1;
		int itemY = y + 1;

		// Render the item icon
		guiGraphics.renderItem(itemStack, itemX, itemY);
		guiGraphics.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, itemStack, itemX, itemY);

		// Render highlight if mouse is over - EXACT JEI style from IngredientGrid.java:172-180
		if (isMouseOver(mouseX, mouseY)) {
			renderHighlight(guiGraphics);
		}
	}

	/**
	 * Renders highlight EXACTLY like JEI's IngredientGrid.drawHighlight()
	 * Source: IngredientGrid.java:172-180
	 */
	private void renderHighlight(GuiGraphics guiGraphics) {
		guiGraphics.fillGradient(
			x,
			y,
			x + SLOT_SIZE,
			y + SLOT_SIZE,
			0x80FFFFFF,  // EXACT JEI color
			0x80FFFFFF   // EXACT JEI color
		);
	}

	/**
	 * Sets whether this slot is blocked by other GUI elements.
	 */
	public void setBlocked(boolean blocked) {
		this.isBlocked = blocked;
	}

	public boolean isBlocked() {
		return isBlocked;
	}

	public int getX() {
		return x;
	}

	public int getY() {
		return y;
	}
}
