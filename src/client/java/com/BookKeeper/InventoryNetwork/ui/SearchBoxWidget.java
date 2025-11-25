package com.BookKeeper.InventoryNetwork.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * Search text field for filtering items.
 * Uses Minecraft's default EditBox rendering for consistency with JEI.
 * JEI uses textures for the search background, but the default EditBox looks very similar.
 */
public class SearchBoxWidget extends EditBox {
	private static final int MAX_LENGTH = 128;
	private static final int TEXT_COLOR_NORMAL = 0xFFFFFFFF; // White
	private static final int TEXT_COLOR_NO_RESULTS = 0xFFFF0000; // Red (JEI style)

	private Runnable onTextChanged;
	private boolean hasResults = true;

	public SearchBoxWidget(Font font, int x, int y, int width, int height) {
		super(font, x, y, width, height, Component.literal("Search..."));
		setMaxLength(MAX_LENGTH);
		setHint(Component.literal("Search..."));
		setBordered(true); // Use default Minecraft border

		// Set up text change listener
		setResponder(this::onValueChanged);
	}

	/**
	 * Called when the text value changes.
	 */
	private void onValueChanged(String newText) {
		if (onTextChanged != null) {
			onTextChanged.run();
		}
	}

	/**
	 * Sets the callback for when text changes.
	 */
	public void setOnTextChanged(Runnable callback) {
		this.onTextChanged = callback;
	}

	/**
	 * Sets whether the current search has results.
	 * Changes text color to red if no results.
	 */
	public void setHasResults(boolean hasResults) {
		this.hasResults = hasResults;
		setTextColor(hasResults ? TEXT_COLOR_NORMAL : TEXT_COLOR_NO_RESULTS);
	}

	/**
	 * Gets the current filter text.
	 */
	public String getFilterText() {
		return getValue();
	}

	/**
	 * Clears the search box.
	 */
	public void clear() {
		setValue("");
	}

	/**
	 * Handles key press events.
	 * When focused, consumes all typing keys to prevent 'e' from closing inventory.
	 */
	public boolean handleKeyPress(int keyCode, int scanCode, int modifiers) {
		if (!isFocused()) return false;

		// Handle backspace
		if (keyCode == 259) { // GLFW_KEY_BACKSPACE
			String current = getValue();
			if (!current.isEmpty()) {
				setValue(current.substring(0, current.length() - 1));
			}
			return true;
		}

		// Handle escape to unfocus - don't consume so inventory can close
		if (keyCode == 256) { // GLFW_KEY_ESCAPE
			setFocused(false);
			return false; // Let ESC propagate to close inventory
		}

		// Handle character input directly to prevent 'e' from closing inventory
		// Letter keys (A-Z = 65-90)
		if (keyCode >= 65 && keyCode <= 90) {
			char chr = (char) keyCode;
			// Convert to lowercase if shift is not pressed
			if ((modifiers & 1) == 0) { // NO SHIFT
				chr = Character.toLowerCase(chr);
			}
			setValue(getValue() + chr);
			return true;
		}

		// Number keys (0-9 = 48-57)
		if (keyCode >= 48 && keyCode <= 57) {
			char chr = (char) keyCode;
			setValue(getValue() + chr);
			return true;
		}

		// Space bar
		if (keyCode == 32) {
			setValue(getValue() + ' ');
			return true;
		}

		// Consume other keys when focused to prevent unwanted actions
		return true;
	}

	/**
	 * Handles character input.
	 * This is now mostly handled in handleKeyPress to prevent inventory closing.
	 */
	public boolean handleCharTyped(char codePoint, int modifiers) {
		if (!isFocused()) return false;

		// Accept most printable characters (backup handler)
		if (codePoint >= 32 && codePoint != 127) {
			setValue(getValue() + codePoint);
			return true;
		}

		return false;
	}
}
