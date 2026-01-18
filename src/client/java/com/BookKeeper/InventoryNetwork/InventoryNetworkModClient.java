package com.BookKeeper.InventoryNetwork;

import com.BookKeeper.InventoryNetwork.ui.InventoryPanelOverlay;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Main client initializer for Inventory Network mod.
 * Orchestrates all modules: ChestTracker, ChestHighlighter, EntityTracker, CommandHandler
 */
public class InventoryNetworkModClient implements ClientModInitializer {
	// Module instances
	// DatabaseManager removed - server is now source of truth
	private ChestTracker chestTracker;
	private ChestHighlighter chestHighlighter;
	private EntityTracker entityTracker;
	private CommandHandler commandHandler;
	private PlayerNameColorManager playerNameColorManager;
	private InventoryPanelOverlay inventoryPanelOverlay;

	// BookKeeper API
	private BookKeeperConfig config;
	private ApiClient apiClient;
	private WebSocketManager webSocketManager;

	// Magic link cooldown tracking
	private long lastMagicLinkRequest = 0;

	// JWT token cached after magic link authentication (memory only, not persisted)
	private String cachedJwtToken = null;

	// Tick counter for periodic tasks
	private int tickCounter = 0;

	// Track if chest was open in previous tick (for detecting close)
	private boolean wasChestOpen = false;

	@Override
	public void onInitializeClient() {
		// Database removed - server is now the single source of truth for chest data
		InventoryNetworkMod.LOGGER.info("Inventory Network client initialized (server-backed mode)");

		// Load BookKeeper configuration from project root
		File configFile = new File("config/inventory_network.json");
		config = BookKeeperConfig.getInstance();
		config.load(configFile);

		// Initialize API client
		apiClient = new ApiClient(config.getApiBaseUrl());
		InventoryNetworkMod.LOGGER.info("BookKeeper API client initialized with URL: {}", config.getApiBaseUrl());

		// Initialize WebSocket manager
		webSocketManager = WebSocketManager.getInstance();
		InventoryNetworkMod.LOGGER.info("WebSocket manager initialized");

		// Initialize modules (UI now uses ChestSyncManager as source of truth)
		chestTracker = new ChestTracker(apiClient);
		chestHighlighter = new ChestHighlighter(ChestSyncManager.getInstance());
		entityTracker = new EntityTracker();
		commandHandler = new CommandHandler(chestTracker);
		playerNameColorManager = new PlayerNameColorManager();
		inventoryPanelOverlay = new InventoryPanelOverlay(ChestSyncManager.getInstance(), chestHighlighter, apiClient);

		// Initialize entity tracker (registers keybind)
		entityTracker.initialize();

		// Register commands
		commandHandler.registerCommands();

		// Set API client for command handler (for /join and /leave commands)
		commandHandler.setApiClient(apiClient);

		// Register player join event for magic link
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			onPlayerJoin(client);
		});

		// Register player disconnect event for WebSocket cleanup
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			onPlayerDisconnect(client);
		});

		// Register block use callback to track which chest the player clicks
		UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
			BlockPos pos = hitResult.getBlockPos();
			BlockEntity blockEntity = level.getBlockEntity(pos);

			// If the player clicked on a chest, notify ChestTracker
			if (blockEntity instanceof ChestBlockEntity) {
				chestTracker.onChestClicked(pos);
			}

			return InteractionResult.PASS;
		});

		// Register screen opening event to detect when containers are opened
		ScreenEvents.AFTER_INIT.register(this::onScreenOpen);

		// Register client tick event
		ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

		// Database removed - no shutdown hook needed
	}

	private void onScreenOpen(Minecraft client, Screen screen, int scaledWidth, int scaledHeight) {
		// Delegate screen opening to ChestTracker
		chestTracker.onScreenOpen(client, screen);

		// Initialize inventory panel overlay for container screens
		if (screen instanceof AbstractContainerScreen<?>) {
			inventoryPanelOverlay.init(screen);

			// Register rendering events - render EVERYTHING in foreground (after screen renders)
			ScreenEvents.afterRender(screen).register((scr, graphics, mouseX, mouseY, delta) -> {
				// Render overlay first
				inventoryPanelOverlay.render(graphics, mouseX, mouseY, delta);
				// Then render tooltips on top
				inventoryPanelOverlay.renderTooltips(graphics, mouseX, mouseY);
			});

			// Register keyboard events for search box
			ScreenKeyboardEvents.allowKeyPress(screen).register((scr, keyEvent) -> {
				if (inventoryPanelOverlay.isInitialized()) {
					boolean handled = inventoryPanelOverlay.keyPressed(keyEvent.key(), keyEvent.scancode(), keyEvent.modifiers());
					return !handled; // Return true to allow, false to cancel
				}
				return true;
			});

			ScreenKeyboardEvents.afterKeyPress(screen).register((scr, keyEvent) -> {
				if (inventoryPanelOverlay.isInitialized()) {
					// Handle character input through key events
					int key = keyEvent.key();
					// Letter keys (A-Z = 65-90, a-z handled with shift check)
					// Number keys (0-9 = 48-57)
					// Space = 32
					if ((key >= 65 && key <= 90) || (key >= 48 && key <= 57) || key == 32) {
						char chr = (char) key;
						// Convert to lowercase if shift is not pressed
						if ((keyEvent.modifiers() & 1) == 0 && key >= 65 && key <= 90) { // NO SHIFT
							chr = Character.toLowerCase(chr);
						}
						inventoryPanelOverlay.charTyped(chr, keyEvent.modifiers());
					}
				}
			});

			// Register mouse click handler (like JEI does)
			ScreenMouseEvents.allowMouseClick(screen).register(this::handleOverlayMouseClick);

			// Register mouse scroll handler
			ScreenMouseEvents.allowMouseScroll(screen).register(this::handleOverlayMouseScroll);
		}
	}

	private boolean handleOverlayMouseClick(Screen screen, MouseButtonEvent event) {
		if (inventoryPanelOverlay.isInitialized()) {
			// Get mouse position from Minecraft's mouse handler
			Minecraft mc = Minecraft.getInstance();
			double mouseX = mc.mouseHandler.xpos() * screen.width / mc.getWindow().getScreenWidth();
			double mouseY = mc.mouseHandler.ypos() * screen.height / mc.getWindow().getScreenHeight();
			int button = event.button();

			// Let overlay handle the click
			boolean handled = inventoryPanelOverlay.mouseClicked(mouseX, mouseY, button);

			// Return true to allow the click, false to cancel it
			return !handled;
		}
		return true;
	}

	private boolean handleOverlayMouseScroll(Screen screen, double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
		if (inventoryPanelOverlay.isInitialized()) {
			boolean handled = inventoryPanelOverlay.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
			return !handled;
		}
		return true;
	}

	private void onClientTick(Minecraft client) {
		tickCounter++;

		// Check if chest was closed (screen changed from chest to non-chest)
		boolean isChestOpen = false;
		Screen currentScreen = client.screen;
		if (currentScreen instanceof AbstractContainerScreen<?> containerScreen) {
			if (containerScreen.getMenu() instanceof net.minecraft.world.inventory.ChestMenu) {
				isChestOpen = true;
			}
		}

		// Detect chest close: was open before, not open now
		if (wasChestOpen && !isChestOpen) {
			chestTracker.onChestClose(client);
		}

		wasChestOpen = isChestOpen;

		// Check WebSocket connection health every 20 ticks (1 second)
		if (client.player != null && tickCounter % 20 == 0) {
			if (!webSocketManager.isConnected() && cachedJwtToken != null) {
				// Attempt reconnect if we have token but not connected
				webSocketManager.attemptReconnect();
			}
		}

		// Tick all modules
		chestTracker.tick(client);
		chestHighlighter.tick(client);
		entityTracker.tick(client);
		// Update player nametag colors
		playerNameColorManager.tick(client);
	}

	/**
	 * Called when player joins a server/world.
	 * Requests a magic login link from the BookKeeper API.
	 */
	private void onPlayerJoin(Minecraft client) {
		// Check if auto magic link is enabled
		if (!config.isAutoMagicLink()) {
			return;
		}

		// Check cooldown to prevent spam
		long now = System.currentTimeMillis();
		long cooldownMs = config.getMagicLinkCooldownSeconds() * 1000L;
		if (now - lastMagicLinkRequest < cooldownMs) {
			return; // Too soon, skip
		}
		lastMagicLinkRequest = now;

		// Run async to avoid blocking the game thread
		CompletableFuture.runAsync(() -> {
			if (client.player == null) return;

			UUID uuid = client.player.getUUID();
			String name = client.player.getName().getString();

			InventoryNetworkMod.LOGGER.info("Requesting magic login link for player: {} ({})", name, uuid);

			ApiClient.MagicLinkResponse response = apiClient.requestMagicLink(uuid, name);

			if (response != null) {
				// Send clickable message to player on main thread
				client.execute(() -> {
					if (client.player != null) {
						sendMagicLinkMessage(client.player, response);
					}
				});

				// Wait a bit for user to click the link (5 seconds)
				// In production, this would be triggered by user action
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					return;
				}

				// Exchange magic token for JWT
				InventoryNetworkMod.LOGGER.info("Exchanging magic token for JWT");
				String jwtToken = apiClient.exchangeMagicToken(response.token);

				if (jwtToken != null) {
					// Connect WebSocket with JWT token
					client.execute(() -> {
						onAuthenticationComplete(jwtToken);
					});
				} else {
					InventoryNetworkMod.LOGGER.error("Failed to exchange magic token for JWT");
				}
			} else {
				InventoryNetworkMod.LOGGER.error("Failed to request magic link");
			}
		});
	}

	/**
	 * Sends a clickable magic link message to the player.
	 */
	private void sendMagicLinkMessage(Player player, ApiClient.MagicLinkResponse response) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) return;

		// For now, send URL as plain text since ClickEvent has mapping issues
		// TODO: Fix click event once proper Fabric mappings are resolved
		client.player.displayClientMessage(
				Component.literal("[BookKeeper] Click to login: ")
						.withStyle(ChatFormatting.GOLD)
						.append(Component.literal(response.magicUrl)
								.withStyle(ChatFormatting.GREEN, ChatFormatting.UNDERLINE)),
				false
		);

		// Additional message for new users
		if (response.isNewUser) {
			client.player.displayClientMessage(
					Component.literal("[BookKeeper] Welcome! This is your first time. Copy the link above to set up your account.")
							.withStyle(ChatFormatting.YELLOW),
					false
			);
		}

		InventoryNetworkMod.LOGGER.info("Magic link sent to player: {}", response.magicUrl);
	}

	/**
	 * Called when player disconnects from server/world.
	 * Closes WebSocket connection and clears cached token.
	 */
	private void onPlayerDisconnect(Minecraft client) {
		InventoryNetworkMod.LOGGER.info("Player disconnected, closing WebSocket");
		webSocketManager.disconnect();
		cachedJwtToken = null;
	}

	/**
	 * Called after magic link authentication is complete with JWT token.
	 * Connects WebSocket with the obtained JWT token.
	 *
	 * @param jwtToken JWT access token from magic link exchange
	 */
	public void onAuthenticationComplete(String jwtToken) {
		InventoryNetworkMod.LOGGER.info("Authentication complete, connecting WebSocket");
		this.cachedJwtToken = jwtToken;

		// Connect WebSocket with token
		String baseUrl = config.getApiBaseUrl();
		webSocketManager.connect(baseUrl, jwtToken);
	}
}
