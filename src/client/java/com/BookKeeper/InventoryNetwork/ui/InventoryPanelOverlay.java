package com.BookKeeper.InventoryNetwork.ui;

import com.BookKeeper.InventoryNetwork.ChestHighlighter;
import com.BookKeeper.InventoryNetwork.DatabaseManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

/**
 * Main overlay controller for the inventory panel UI.
 * Manages the grid, search box, dimension toggle, and coordinates rendering.
 */
public class InventoryPanelOverlay {
	// JEI-style layout constants
	private static final int BORDER_MARGIN = 6;        // Outer margin from screen edges
	private static final int BORDER_PADDING = 5;       // Border interior padding
	private static final int INNER_PADDING = 2;        // Internal spacing between components
	private static final int SEARCH_BOX_HEIGHT = 20;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GRID_COLUMNS = 6;
	private static final int GRID_ROWS = 9;
	private static final int NAVIGATION_HEIGHT = 20;   // Navigation bar height (like JEI)

	private final DatabaseManager databaseManager;
	private final ChestHighlighter chestHighlighter;

	private ItemGridWidget itemGrid;
	private SearchBoxWidget searchBox;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	private boolean initialized = false;

	public InventoryPanelOverlay(DatabaseManager databaseManager, ChestHighlighter chestHighlighter) {
		this.databaseManager = databaseManager;
		this.chestHighlighter = chestHighlighter;
	}

	/**
	 * Initializes the overlay for a specific screen.
	 */
	public void init(Screen screen) {
		if (!(screen instanceof AbstractContainerScreen<?> containerScreen)) {
			return;
		}

		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;

		// Calculate panel dimensions (JEI-style with border padding) - NO dimension button
		panelWidth = GRID_COLUMNS * ItemSlot.SLOT_SIZE + BORDER_PADDING * 2;
		panelHeight = BORDER_PADDING +
		              SEARCH_BOX_HEIGHT + INNER_PADDING +
		              GRID_ROWS * ItemSlot.SLOT_SIZE + INNER_PADDING +
		              NAVIGATION_HEIGHT + BORDER_PADDING;

		// Position at left-center of screen (centered vertically, left edge horizontally)
		int screenWidth = screen.width;
		int screenHeight = screen.height;
		panelX = BORDER_MARGIN; // Left edge with margin
		panelY = (screenHeight - panelHeight) / 2; // Vertically centered

		// Initialize search box
		int searchBoxX = panelX + BORDER_PADDING;
		int searchBoxY = panelY + BORDER_PADDING;
		int searchBoxWidth = panelWidth - BORDER_PADDING * 2;
		searchBox = new SearchBoxWidget(font, searchBoxX, searchBoxY, searchBoxWidth, SEARCH_BOX_HEIGHT);
		searchBox.setOnTextChanged(this::onSearchTextChanged);

		// Initialize item grid (directly below search box now)
		int gridX = panelX + BORDER_PADDING;
		int gridY = searchBoxY + SEARCH_BOX_HEIGHT + INNER_PADDING;
		itemGrid = new ItemGridWidget(gridX, gridY, GRID_COLUMNS, GRID_ROWS);

		// Load initial items
		loadItems();

		initialized = true;
	}

	/**
	 * Loads items from the database into the grid.
	 * Always loads from ALL dimensions.
	 */
	private void loadItems() {
		if (itemGrid == null) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		// Always load from all dimensions (dimension = null)
		itemGrid.loadItems(databaseManager, null);
		updateSearchResults();
	}

	/**
	 * Called when search text changes.
	 */
	private void onSearchTextChanged() {
		if (itemGrid == null || searchBox == null) return;

		itemGrid.setFilter(searchBox.getFilterText());
		updateSearchResults();
	}

	/**
	 * Updates search box color based on results.
	 */
	private void updateSearchResults() {
		if (searchBox != null && itemGrid != null) {
			boolean hasResults = itemGrid.getTotalPages() > 0;
			searchBox.setHasResults(hasResults);
		}
	}

	/**
	 * Renders the overlay.
	 */
	public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
		if (!initialized) return;

		// Draw semi-transparent background
		renderBackground(guiGraphics);

		// Render search box
		searchBox.render(guiGraphics, mouseX, mouseY, partialTick);

		// Render item grid
		itemGrid.render(guiGraphics, mouseX, mouseY);

		// Render pagination
		renderPagination(guiGraphics);
	}

	/**
	 * Renders tooltips (should be called in foreground layer).
	 */
	public void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		if (!initialized) return;

		ItemStack hoveredItem = itemGrid.getItemUnderMouse(mouseX, mouseY);
		if (hoveredItem != null && !hoveredItem.isEmpty()) {
			// TODO: Fix tooltip rendering for this Minecraft version
			// For now, tooltips are disabled to get the build working
			// The tooltip API has changed in this version and needs investigation
		}
	}

	/**
	 * Renders the panel background.
	 * Uses colors inspired by JEI's ingredient list background.
	 */
	private void renderBackground(GuiGraphics guiGraphics) {
		// Main panel background - semi-transparent gray like JEI
		// Using slightly lighter color to match JEI's appearance
		guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xC0808080);

		// Dark border around the entire panel
		int borderColor = 0xFF373737;
		guiGraphics.fill(panelX, panelY, panelX + panelWidth, panelY + 1, borderColor); // Top
		guiGraphics.fill(panelX, panelY + panelHeight - 1, panelX + panelWidth, panelY + panelHeight, borderColor); // Bottom
		guiGraphics.fill(panelX, panelY, panelX + 1, panelY + panelHeight, borderColor); // Left
		guiGraphics.fill(panelX + panelWidth - 1, panelY, panelX + panelWidth, panelY + panelHeight, borderColor); // Right

		// Lighter inner border for depth effect (like JEI)
		int innerBorderColor = 0xFFC0C0C0;
		guiGraphics.fill(panelX + 1, panelY + 1, panelX + panelWidth - 1, panelY + 2, innerBorderColor); // Top inner
		guiGraphics.fill(panelX + 1, panelY + 1, panelX + 2, panelY + panelHeight - 1, innerBorderColor); // Left inner
	}

	/**
	 * Renders pagination controls - EXACT copy of JEI's PageNavigation.java:55-73
	 * Uses the same fill color and text rendering as JEI.
	 */
	private void renderPagination(GuiGraphics guiGraphics) {
		if (itemGrid.getTotalPages() <= 1) return;

		Font font = Minecraft.getInstance().font;

		// Calculate navigation bar area
		int navY = panelY + panelHeight - NAVIGATION_HEIGHT - BORDER_PADDING;
		int backButtonX = panelX + BORDER_PADDING;
		int backButtonWidth = NAVIGATION_HEIGHT;
		int nextButtonX = panelX + panelWidth - BORDER_PADDING - NAVIGATION_HEIGHT;
		int nextButtonWidth = NAVIGATION_HEIGHT;

		// EXACT JEI rendering from PageNavigation.java:57-63
		// Draw semi-transparent dark background between buttons
		guiGraphics.fill(
			backButtonX + backButtonWidth,
			navY,
			nextButtonX,
			navY + NAVIGATION_HEIGHT,
			0x30000000  // EXACT JEI color
		);

		// Page number text - centered
		String pageText = String.format("%d/%d", itemGrid.getCurrentPage() + 1, itemGrid.getTotalPages());
		int textX = panelX + (panelWidth - font.width(pageText)) / 2;
		int textY = navY + (NAVIGATION_HEIGHT - font.lineHeight) / 2;
		guiGraphics.drawString(font, pageText, textX, textY, 0xFFFFFFFF);  // EXACT JEI color

		// Draw arrow buttons (simplified - JEI uses GuiIconButton with sprites)
		if (itemGrid.hasPreviousPage()) {
			String prevArrow = "<";
			int prevX = backButtonX + (backButtonWidth - font.width(prevArrow)) / 2;
			guiGraphics.drawString(font, prevArrow, prevX, textY, 0xFFFFFFFF);
		}

		if (itemGrid.hasNextPage()) {
			String nextArrow = ">";
			int nextX = nextButtonX + (nextButtonWidth - font.width(nextArrow)) / 2;
			guiGraphics.drawString(font, nextArrow, nextX, textY, 0xFFFFFFFF);
		}
	}

	/**
	 * Handles mouse click events.
	 */
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (!initialized) return false;

		// Check search box click - just focus it
		if (searchBox.isMouseOver(mouseX, mouseY)) {
			searchBox.setFocused(true);
			return true;
		} else {
			searchBox.setFocused(false);
		}

		// Check pagination clicks
		if (handlePaginationClick(mouseX, mouseY)) {
			return true;
		}

		// Check item grid click
		ItemStack clickedItem = itemGrid.handleClick(mouseX, mouseY);
		if (clickedItem != null && !clickedItem.isEmpty()) {
			handleItemClick(clickedItem);
			return true;
		}

		return false;
	}

	/**
	 * Handles pagination arrow clicks.
	 * Matches the button areas from renderPagination().
	 */
	private boolean handlePaginationClick(double mouseX, double mouseY) {
		if (itemGrid.getTotalPages() <= 1) return false;

		int navY = panelY + panelHeight - NAVIGATION_HEIGHT - BORDER_PADDING;
		int backButtonX = panelX + BORDER_PADDING;
		int backButtonWidth = NAVIGATION_HEIGHT;
		int nextButtonX = panelX + panelWidth - BORDER_PADDING - NAVIGATION_HEIGHT;
		int nextButtonWidth = NAVIGATION_HEIGHT;

		// Check back button click
		if (itemGrid.hasPreviousPage()) {
			if (mouseX >= backButtonX && mouseX < backButtonX + backButtonWidth &&
			    mouseY >= navY && mouseY < navY + NAVIGATION_HEIGHT) {
				itemGrid.previousPage();
				return true;
			}
		}

		// Check next button click
		if (itemGrid.hasNextPage()) {
			if (mouseX >= nextButtonX && mouseX < nextButtonX + nextButtonWidth &&
			    mouseY >= navY && mouseY < navY + NAVIGATION_HEIGHT) {
				itemGrid.nextPage();
				return true;
			}
		}

		return false;
	}

	/**
	 * Handles clicking an item in the grid.
	 */
	private void handleItemClick(ItemStack item) {
		String itemName = item.getHoverName().getString();
		chestHighlighter.highlightItemByName(itemName);
	}

	/**
	 * Handles keyboard input for search box.
	 */
	public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
		if (!initialized) return false;

		// Let the search box handle keyboard input if it's focused
		return searchBox.handleKeyPress(keyCode, scanCode, modifiers);
	}

	/**
	 * Handles character typed events for search box.
	 */
	public boolean charTyped(char codePoint, int modifiers) {
		if (!initialized) return false;

		// Let the search box handle character input if it's focused
		return searchBox.handleCharTyped(codePoint, modifiers);
	}

	/**
	 * Handles mouse scroll events for pagination.
	 */
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (!initialized) return false;

		// Check if mouse is over the panel
		if (mouseX >= panelX && mouseX < panelX + panelWidth &&
		    mouseY >= panelY && mouseY < panelY + panelHeight) {

			if (scrollY > 0 && itemGrid.hasPreviousPage()) {
				itemGrid.previousPage();
				return true;
			} else if (scrollY < 0 && itemGrid.hasNextPage()) {
				itemGrid.nextPage();
				return true;
			}
		}

		return false;
	}

	public boolean isInitialized() {
		return initialized;
	}
}
