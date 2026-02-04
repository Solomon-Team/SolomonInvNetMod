package com.BookKeeper.InventoryNetwork.ui;

import com.BookKeeper.InventoryNetwork.ApiClient;
import com.BookKeeper.InventoryNetwork.ChestHighlighter;
import com.BookKeeper.InventoryNetwork.ChestSyncManager;
import com.BookKeeper.InventoryNetwork.WebSocketManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Main overlay controller for the inventory panel UI.
 * Manages the grid, search box, dimension toggle, and coordinates rendering.
 * Now uses ChestSyncManager as the single source of truth (server data).
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
	private static final int REFRESH_BUTTON_WIDTH = 50;
	private static final int REFRESH_BUTTON_HEIGHT = 14;

	private final ChestSyncManager chestSyncManager;
	private final ChestHighlighter chestHighlighter;
	private final ApiClient apiClient;

	private ItemGridWidget itemGrid;
	private SearchBoxWidget searchBox;

	private int panelX;
	private int panelY;
	private int panelWidth;
	private int panelHeight;

	// Refresh button state
	private int refreshButtonX;
	private int refreshButtonY;
	private boolean isRefreshing = false;
	private long lastRefreshTime = 0;
	private static final long REFRESH_COOLDOWN_MS = 2000; // 2 seconds cooldown

	private boolean initialized = false;

	public InventoryPanelOverlay(ChestSyncManager chestSyncManager, ChestHighlighter chestHighlighter, ApiClient apiClient) {
		this.chestSyncManager = chestSyncManager;
		this.chestHighlighter = chestHighlighter;
		this.apiClient = apiClient;

		// Register listener for automatic UI refresh when server data changes
		chestSyncManager.addUpdateListener(update -> {
			if (initialized) {
				loadItems(); // Reload items when server data updates
			}
		});
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

		// Position refresh button (top-right corner, next to search box)
		refreshButtonX = panelX + panelWidth - BORDER_PADDING - REFRESH_BUTTON_WIDTH;
		refreshButtonY = panelY + BORDER_PADDING + 3; // Align with search box

		// Load initial items
		loadItems();

		initialized = true;
	}

	/**
	 * Loads items from ChestSyncManager (server as source of truth).
	 * Aggregates data from all synced chests across all players.
	 */
	private void loadItems() {
		if (itemGrid == null) return;

		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		// Load from ChestSyncManager (server data)
		itemGrid.loadItems(chestSyncManager);
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

		// Render refresh button
		renderRefreshButton(guiGraphics, mouseX, mouseY);

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
	 * Renders the refresh button for manually syncing from server.
	 */
	private void renderRefreshButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
		Font font = Minecraft.getInstance().font;

		// Check if mouse is hovering
		boolean isHovered = mouseX >= refreshButtonX && mouseX < refreshButtonX + REFRESH_BUTTON_WIDTH &&
		                    mouseY >= refreshButtonY && mouseY < refreshButtonY + REFRESH_BUTTON_HEIGHT;

		// Button background color
		int bgColor;
		if (isRefreshing) {
			bgColor = 0xFF555555; // Gray when refreshing
		} else if (isHovered) {
			bgColor = 0xFF4A90E2; // Blue when hovered
		} else {
			bgColor = 0xFF3A3A3A; // Dark gray normally
		}

		// Draw button background
		guiGraphics.fill(refreshButtonX, refreshButtonY,
		                 refreshButtonX + REFRESH_BUTTON_WIDTH,
		                 refreshButtonY + REFRESH_BUTTON_HEIGHT,
		                 bgColor);

		// Draw button border
		int borderColor = isHovered ? 0xFFFFFFFF : 0xFF808080;
		guiGraphics.fill(refreshButtonX, refreshButtonY,
		                 refreshButtonX + REFRESH_BUTTON_WIDTH, refreshButtonY + 1, borderColor); // Top
		guiGraphics.fill(refreshButtonX, refreshButtonY + REFRESH_BUTTON_HEIGHT - 1,
		                 refreshButtonX + REFRESH_BUTTON_WIDTH, refreshButtonY + REFRESH_BUTTON_HEIGHT, borderColor); // Bottom
		guiGraphics.fill(refreshButtonX, refreshButtonY,
		                 refreshButtonX + 1, refreshButtonY + REFRESH_BUTTON_HEIGHT, borderColor); // Left
		guiGraphics.fill(refreshButtonX + REFRESH_BUTTON_WIDTH - 1, refreshButtonY,
		                 refreshButtonX + REFRESH_BUTTON_WIDTH, refreshButtonY + REFRESH_BUTTON_HEIGHT, borderColor); // Right

		// Button text
		String buttonText = isRefreshing ? "..." : "Sync";
		int textX = refreshButtonX + (REFRESH_BUTTON_WIDTH - font.width(buttonText)) / 2;
		int textY = refreshButtonY + (REFRESH_BUTTON_HEIGHT - font.lineHeight) / 2;
		int textColor = isRefreshing ? 0xFF999999 : 0xFFFFFFFF;
		guiGraphics.drawString(font, buttonText, textX, textY, textColor);
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

		// Check refresh button click
		if (handleRefreshButtonClick(mouseX, mouseY)) {
			return true;
		}

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
	 * Handles refresh button click - fetches latest data from server.
	 */
	private boolean handleRefreshButtonClick(double mouseX, double mouseY) {
		// Check if click is within button bounds
		if (mouseX >= refreshButtonX && mouseX < refreshButtonX + REFRESH_BUTTON_WIDTH &&
		    mouseY >= refreshButtonY && mouseY < refreshButtonY + REFRESH_BUTTON_HEIGHT) {

			// Check cooldown
			long currentTime = System.currentTimeMillis();
			if (currentTime - lastRefreshTime < REFRESH_COOLDOWN_MS) {
				Minecraft.getInstance().player.displayClientMessage(
					Component.literal("§c[Inventory Network] Please wait before refreshing again"),
					true
				);
				return true;
			}

			// Check if already refreshing
			if (isRefreshing) {
				return true;
			}

			// Get JWT token from WebSocket manager
			String jwtToken = WebSocketManager.getInstance().getJwtToken();
			if (jwtToken == null || jwtToken.isEmpty()) {
				Minecraft.getInstance().player.displayClientMessage(
					Component.literal("§c[Inventory Network] Not authenticated. Please reconnect."),
					true
				);
				return true;
			}

			// Start refresh
			isRefreshing = true;
			lastRefreshTime = currentTime;

			Minecraft.getInstance().player.displayClientMessage(
				Component.literal("§6[Inventory Network] Syncing from server..."),
				true
			);

			// Refresh from server (async)
			chestSyncManager.refreshFromServer(apiClient, jwtToken).thenAccept(success -> {
				isRefreshing = false;
				if (success) {
					Minecraft.getInstance().player.displayClientMessage(
						Component.literal("§a[Inventory Network] Sync complete! (" +
							chestSyncManager.getChestCount() + " chests)"),
						true
					);
				} else {
					Minecraft.getInstance().player.displayClientMessage(
						Component.literal("§c[Inventory Network] Sync failed. Check connection."),
						true
					);
				}
			});

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
