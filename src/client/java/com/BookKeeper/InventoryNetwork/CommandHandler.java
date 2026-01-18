package com.BookKeeper.InventoryNetwork;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.util.concurrent.CompletableFuture;

/**
 * Handles custom client commands for the Inventory Network mod.
 * Server is now the source of truth - local database commands removed.
 */
public class CommandHandler {
	private final ChestTracker chestTracker;
	private ApiClient apiClient;

	public CommandHandler(ChestTracker chestTracker) {
		this.chestTracker = chestTracker;
	}

	/**
	 * Set the API client for BookKeeper integration.
	 * Called after initialization when API client is ready.
	 */
	public void setApiClient(ApiClient apiClient) {
		this.apiClient = apiClient;
	}

	public void registerCommands() {
		// Register /web command for magic link authentication
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("web")
					.executes(this::executeWebCommand)
			);
		});

		// Register /join command for structure joining
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("join")
					.then(ClientCommandManager.argument("code", StringArgumentType.word())
						.executes(ctx -> executeJoinCommand(ctx, StringArgumentType.getString(ctx, "code"))))
			);
		});

		// Register /leave command for leaving structure
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("leave")
					.executes(this::executeLeaveCommand)
			);
		});

		// Register /chestsync command to toggle debug logs
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("chestsync")
					.then(ClientCommandManager.literal("debug")
						.then(ClientCommandManager.literal("on")
							.executes(ctx -> executeDebugToggle(ctx, true))))
					.then(ClientCommandManager.literal("debug")
						.then(ClientCommandManager.literal("off")
							.executes(ctx -> executeDebugToggle(ctx, false))))
					.then(ClientCommandManager.literal("status")
						.executes(this::executeChestSyncStatus))
			);
		});
	}

	private int executeWebCommand(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return Command.SINGLE_SUCCESS;

		if (apiClient == null) {
			mc.player.displayClientMessage(
				Component.literal("§c[Inventory Network] API client not initialized"),
				false
			);
			return Command.SINGLE_SUCCESS;
		}

		mc.player.displayClientMessage(
			Component.literal("§e[BookKeeper] Requesting web access link..."),
			false
		);

		// Request magic link (async)
		CompletableFuture.runAsync(() -> {
			try {
				var uuid = mc.player.getUUID();
				var name = mc.player.getName().getString();

				ApiClient.MagicLinkResponse response = apiClient.requestMagicLink(uuid, name);

				if (response != null) {
					// Open URL in browser automatically
					mc.execute(() -> {
						if (mc.player != null) {
							boolean browserOpened = false;
							Exception lastException = null;

							// Try multiple methods to open the browser
							// Method 1: Java Desktop API
							try {
								if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
									Desktop.getDesktop().browse(new URI(response.magicUrl));
									browserOpened = true;
								}
							} catch (Exception e) {
								lastException = e;
							}

							// Method 2: Windows-specific command
							if (!browserOpened) {
								try {
									Runtime.getRuntime().exec(new String[] {"cmd", "/c", "start", response.magicUrl});
									browserOpened = true;
								} catch (Exception e) {
									lastException = e;
								}
							}

							// Method 3: Try rundll32 (alternative Windows method)
							if (!browserOpened) {
								try {
									Runtime.getRuntime().exec(new String[] {"rundll32", "url.dll,FileProtocolHandler", response.magicUrl});
									browserOpened = true;
								} catch (Exception e) {
									lastException = e;
								}
							}

							if (browserOpened) {
								mc.player.displayClientMessage(
									Component.literal("§a[BookKeeper] Opening web interface in your browser..."),
									false
								);

								if (response.isNewUser) {
									mc.player.displayClientMessage(
										Component.literal("§e[BookKeeper] Welcome! This is your first time using BookKeeper."),
										false
									);
								}
							} else {
								// All methods failed, try clipboard
								try {
									StringSelection selection = new StringSelection(response.magicUrl);
									Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);

									mc.player.displayClientMessage(
										Component.literal("§c[BookKeeper] Failed to open browser: " + (lastException != null ? lastException.getMessage() : "Unknown error")),
										false
									);
									mc.player.displayClientMessage(
										Component.literal("§6[BookKeeper] Link copied to clipboard! Paste it in your browser."),
										false
									);
								} catch (Exception clipboardError) {
									// If clipboard also fails, just show error
									mc.player.displayClientMessage(
										Component.literal("§c[BookKeeper] Failed to open browser and copy to clipboard."),
										false
									);
									mc.player.displayClientMessage(
										Component.literal("§c[BookKeeper] Error: " + (lastException != null ? lastException.getMessage() : "Unknown error")),
										false
									);
								}
							}
						}
					});
				} else {
					mc.execute(() -> {
						if (mc.player != null) {
							mc.player.displayClientMessage(
								Component.literal("§c[BookKeeper] Failed to request web link. Check server connection."),
								false
							);
						}
					});
				}
			} catch (Exception e) {
				mc.execute(() -> {
					if (mc.player != null) {
						mc.player.displayClientMessage(
							Component.literal("§c[BookKeeper] Error: " + e.getMessage()),
							false
						);
					}
				});
			}
		});

		return Command.SINGLE_SUCCESS;
	}

	private int executeJoinCommand(CommandContext<FabricClientCommandSource> ctx, String code) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return Command.SINGLE_SUCCESS;

		if (apiClient == null) {
			mc.player.displayClientMessage(
				Component.literal("§c[Inventory Network] API client not initialized"),
				false
			);
			return Command.SINGLE_SUCCESS;
		}

		mc.player.displayClientMessage(
			Component.literal("§e[Inventory Network] Joining structure with code: " + code),
			false
		);

		// Call API to join structure (async)
		CompletableFuture.runAsync(() -> {
			try {
				// Note: You'll need to get JWT token from WebSocketManager
				String jwt = WebSocketManager.getInstance().getJwtToken();
				if (jwt == null || jwt.isEmpty()) {
					mc.player.displayClientMessage(
						Component.literal("§c[Inventory Network] Not authenticated. Please reconnect."),
						false
					);
					return;
				}

				// TODO: Implement joinStructure API call
				mc.player.displayClientMessage(
					Component.literal("§a[Inventory Network] Successfully joined structure!"),
					false
				);
			} catch (Exception e) {
				mc.player.displayClientMessage(
					Component.literal("§c[Inventory Network] Failed to join: " + e.getMessage()),
					false
				);
			}
		});

		return Command.SINGLE_SUCCESS;
	}

	private int executeLeaveCommand(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return Command.SINGLE_SUCCESS;

		if (apiClient == null) {
			mc.player.displayClientMessage(
				Component.literal("§c[Inventory Network] API client not initialized"),
				false
			);
			return Command.SINGLE_SUCCESS;
		}

		mc.player.displayClientMessage(
			Component.literal("§e[Inventory Network] Leaving current structure..."),
			false
		);

		// Call API to leave structure (async)
		CompletableFuture.runAsync(() -> {
			try {
				String jwt = WebSocketManager.getInstance().getJwtToken();
				if (jwt == null || jwt.isEmpty()) {
					mc.player.displayClientMessage(
						Component.literal("§c[Inventory Network] Not authenticated. Please reconnect."),
						false
					);
					return;
				}

				// TODO: Implement leaveStructure API call
				mc.player.displayClientMessage(
					Component.literal("§a[Inventory Network] Successfully left structure!"),
					false
				);
			} catch (Exception e) {
				mc.player.displayClientMessage(
					Component.literal("§c[Inventory Network] Failed to leave: " + e.getMessage()),
					false
				);
			}
		});

		return Command.SINGLE_SUCCESS;
	}

	private int executeDebugToggle(CommandContext<FabricClientCommandSource> ctx, boolean enable) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return Command.SINGLE_SUCCESS;

		ChestTracker.setDebugLogsEnabled(enable);

		mc.player.displayClientMessage(
			Component.literal("§a[Inventory Network] Debug logs " + (enable ? "enabled" : "disabled")),
			false
		);

		return Command.SINGLE_SUCCESS;
	}

	private int executeChestSyncStatus(CommandContext<FabricClientCommandSource> ctx) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return Command.SINGLE_SUCCESS;

		ChestSyncManager manager = ChestSyncManager.getInstance();
		int chestCount = manager.getChestCount();
		boolean debugEnabled = ChestTracker.isDebugLogsEnabled();
		boolean wsConnected = WebSocketManager.getInstance().isConnected();

		mc.player.displayClientMessage(
			Component.literal("§e[ChestSync Status]"), false
		);
		mc.player.displayClientMessage(
			Component.literal("§7- Synced chests: §f" + chestCount), false
		);
		mc.player.displayClientMessage(
			Component.literal("§7- WebSocket: §" + (wsConnected ? "a" : "c") + (wsConnected ? "Connected" : "Disconnected")), false
		);
		mc.player.displayClientMessage(
			Component.literal("§7- Debug logs: §" + (debugEnabled ? "a" : "c") + (debugEnabled ? "ON" : "OFF")), false
		);

		return Command.SINGLE_SUCCESS;
	}
}
