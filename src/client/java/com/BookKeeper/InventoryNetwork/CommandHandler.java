package com.BookKeeper.InventoryNetwork;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Handles custom client commands for the Inventory Network mod
 */
public class CommandHandler {
	private final DatabaseManager databaseManager;
	private final ChestTracker chestTracker;
	private ApiClient apiClient;

	public CommandHandler(DatabaseManager databaseManager, ChestTracker chestTracker) {
		this.databaseManager = databaseManager;
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
		// Register /db command
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("db")
					.executes(this::executeDbCommand)
			);
		});

		// Register /debugDB clear command
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("debugDB")
					.then(ClientCommandManager.literal("clear")
						.executes(this::executeDebugDBClearCommand))
			);
		});

		// Register whitelist commands: /whitelist add/remove/list
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("whitelist")
					.then(ClientCommandManager.literal("add")
						.then(ClientCommandManager.argument("player", StringArgumentType.word())
							.executes(ctx -> executeWhitelistAdd(ctx, StringArgumentType.getString(ctx, "player")))))
						.then(ClientCommandManager.literal("remove")
							.then(ClientCommandManager.argument("player", StringArgumentType.word())
								.executes(ctx -> executeWhitelistRemove(ctx, StringArgumentType.getString(ctx, "player")))))
						.then(ClientCommandManager.literal("list")
							.executes(this::executeWhitelistList))
				);
		});

		// Register blacklist commands: /blacklist add/remove/list
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("blacklist")
					.then(ClientCommandManager.literal("add")
						.then(ClientCommandManager.argument("player", StringArgumentType.word())
							.executes(ctx -> executeBlacklistAdd(ctx, StringArgumentType.getString(ctx, "player")))))
						.then(ClientCommandManager.literal("remove")
							.then(ClientCommandManager.argument("player", StringArgumentType.word())
								.executes(ctx -> executeBlacklistRemove(ctx, StringArgumentType.getString(ctx, "player")))))
						.then(ClientCommandManager.literal("list")
							.executes(this::executeBlacklistList))
				);
		});

        // Register debug command to inspect player namecolor state
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(
                ClientCommandManager.literal("namecolor")
                    .then(ClientCommandManager.literal("check")
                        .then(ClientCommandManager.argument("player", StringArgumentType.word())
                            .executes(ctx -> executeNameColorCheck(ctx, StringArgumentType.getString(ctx, "player")))))
            );
        });

		// Register /debugLogs command to toggle debug messages
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("debugLogs")
					.then(ClientCommandManager.argument("enabled", StringArgumentType.word())
						.executes(ctx -> executeDebugLogsToggle(ctx, StringArgumentType.getString(ctx, "enabled"))))
					.executes(this::executeDebugLogsStatus) // Show current status if no argument
			);
		});

		// Register /join command to join a structure using a code
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("join")
					.then(ClientCommandManager.argument("code", StringArgumentType.string())
						.executes(ctx -> executeJoinStructure(ctx, StringArgumentType.getString(ctx, "code"))))
			);
		});

		// Register /leave command to leave current structure
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("leave")
					.executes(this::executeLeaveStructure)
			);
		});

		// Register /web command to open BookKeeper website with auto-login
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(
				ClientCommandManager.literal("web")
					.executes(this::executeWebLogin)
			);
		});
	}

	private int executeDbCommand(CommandContext<FabricClientCommandSource> context) {
		Minecraft client = context.getSource().getClient();

		if (client.player == null) {
			return 0;
		}

		BlockPos lastOpenedChestPos = chestTracker.getLastOpenedChestPos();
		String lastOpenedChestDimension = chestTracker.getLastOpenedChestDimension();

		if (lastOpenedChestPos == null || lastOpenedChestDimension == null) {
			client.player.displayClientMessage(
				Component.literal("§c[Inventory Network] No chest has been opened yet!"),
				false
			);
			return 0;
		}

		// Retrieve data from database
		String dbContents = databaseManager.getChestData(
			lastOpenedChestPos.getX(),
			lastOpenedChestPos.getY(),
			lastOpenedChestPos.getZ(),
			lastOpenedChestDimension
		);

		if (dbContents == null) {
			client.player.displayClientMessage(
				Component.literal("§c[Inventory Network] No data found in database for chest at: " +
					lastOpenedChestPos.toShortString() + " in " + lastOpenedChestDimension),
				false
			);
			return 0;
		}

		// Display database info
		client.player.displayClientMessage(
			Component.literal("§b[Inventory Network] Database Info:"),
			false
		);
		client.player.displayClientMessage(
			Component.literal("§b  Position: " + lastOpenedChestPos.toShortString()),
			false
		);
		client.player.displayClientMessage(
			Component.literal("§b  Dimension: " + lastOpenedChestDimension),
			false
		);

		// Parse and display contents
		if (dbContents.equals("EMPTY")) {
			client.player.displayClientMessage(
				Component.literal("§b  Contents: Empty"),
				false
			);
		} else {
			client.player.displayClientMessage(
				Component.literal("§b  Contents:"),
				false
			);

			// Parse format: slot|itemId|count|displayName;slot|itemId|count|displayName
			String[] items = dbContents.split(";");
			for (String item : items) {
				String[] parts = item.split("\\|");
				if (parts.length == 4) {
					String slot = parts[0];
					String count = parts[2];
					String displayName = parts[3];
					client.player.displayClientMessage(
						Component.literal("§b    Slot " + slot + ": " + count + "x " + displayName),
						false
					);
				}
			}
		}
		return Command.SINGLE_SUCCESS;
	}

	private int executeDebugDBClearCommand(CommandContext<FabricClientCommandSource> context) {
		Minecraft client = context.getSource().getClient();

		if (client.player == null) {
			return 0;
		}

		int totalBefore = databaseManager.getTotalChestCount();
		databaseManager.clearDatabase();

		client.player.displayClientMessage(
			Component.literal("§c[Inventory Network] Database cleared! Removed " + totalBefore + " chest entries."),
			false
		);

		// Reset last opened chest in ChestTracker
		chestTracker.resetLastOpened();

		return Command.SINGLE_SUCCESS;
	}

	// Whitelist handlers
	private int executeWhitelistAdd(CommandContext<FabricClientCommandSource> context, String playerName) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null) return 0;

		boolean ok = databaseManager.addToWhitelist(playerName);
		if (ok) {
			client.player.displayClientMessage(Component.literal("§9[Inventory Network] Added to whitelist: §9" + playerName), false);
		} else {
			client.player.displayClientMessage(Component.literal("§c[Inventory Network] Failed to add to whitelist: " + playerName), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private int executeWhitelistRemove(CommandContext<FabricClientCommandSource> context, String playerName) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null) return 0;

		boolean removed = databaseManager.removeFromWhitelist(playerName);
		if (removed) {
			client.player.displayClientMessage(Component.literal("§9[Inventory Network] Removed from whitelist: §9" + playerName), false);
		} else {
			client.player.displayClientMessage(Component.literal("§c[Inventory Network] Player not found in whitelist: " + playerName), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private int executeWhitelistList(CommandContext<FabricClientCommandSource> context) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null) return 0;

		java.util.List<String> list = databaseManager.getAllWhitelisted();
		client.player.displayClientMessage(Component.literal("§b[Inventory Network] Whitelist:"), false);
		if (list.isEmpty()) {
			client.player.displayClientMessage(Component.literal("§b  (empty)"), false);
		} else {
			for (String name : list) {
				client.player.displayClientMessage(Component.literal("§9" + name), false);
			}
		}
		return Command.SINGLE_SUCCESS;
	}

	// Blacklist handlers
	private int executeBlacklistAdd(CommandContext<FabricClientCommandSource> context, String playerName) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null) return 0;

		boolean ok = databaseManager.addToBlacklist(playerName);
		if (ok) {
			client.player.displayClientMessage(Component.literal("§c[Inventory Network] Added to blacklist: §c" + playerName), false);
		} else {
			client.player.displayClientMessage(Component.literal("§c[Inventory Network] Failed to add to blacklist: " + playerName), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private int executeBlacklistRemove(CommandContext<FabricClientCommandSource> context, String playerName) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null) return 0;

		boolean removed = databaseManager.removeFromBlacklist(playerName);
		if (removed) {
			client.player.displayClientMessage(Component.literal("§c[Inventory Network] Removed from blacklist: §c" + playerName), false);
		} else {
			client.player.displayClientMessage(Component.literal("§c[Inventory Network] Player not found in blacklist: " + playerName), false);
		}
		return Command.SINGLE_SUCCESS;
	}

	private int executeBlacklistList(CommandContext<FabricClientCommandSource> context) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null) return 0;

		java.util.List<String> list = databaseManager.getAllBlacklisted();
		client.player.displayClientMessage(Component.literal("§b[Inventory Network] Blacklist:"), false);
		if (list.isEmpty()) {
			client.player.displayClientMessage(Component.literal("§b  (empty)"), false);
		} else {
			for (String name : list) {
				client.player.displayClientMessage(Component.literal("§c" + name), false);
			}
		}
		return Command.SINGLE_SUCCESS;
	}

	// Debug: check a player's name/color status
	private int executeNameColorCheck(CommandContext<FabricClientCommandSource> context, String queryName) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null || client.level == null) return 0;

		Player found = null;
		for (Player p : client.level.players()) {
			String display = p.getName().getString();
			String clean = display.replaceAll("§.", "");
			String uuid = p.getUUID().toString();
			if (clean.equalsIgnoreCase(queryName) || uuid.equalsIgnoreCase(queryName)) {
				found = p;
				break;
			}
		}

		if (found == null) {
			client.player.displayClientMessage(Component.literal("§c[Inventory Network] Player not found online: " + queryName), false);
			return 0;
		}

		String display = found.getName().getString();
		String clean = display.replaceAll("§.", "");
		String uuid = found.getUUID().toString();

		boolean black = databaseManager.isBlacklisted(clean) || databaseManager.isBlacklistedByUuid(uuid);
		boolean white = databaseManager.isWhitelisted(clean) || databaseManager.isWhitelistedByUuid(uuid);

		client.player.displayClientMessage(Component.literal("§b[NameColor Debug] Display='" + display + "' Clean='" + clean + "' UUID=" + uuid), false);
		client.player.displayClientMessage(Component.literal("§b[NameColor Debug] Blacklisted=" + black + " Whitelisted=" + white), false);

		return Command.SINGLE_SUCCESS;
	}

	// Debug logs toggle handler
	private int executeDebugLogsToggle(CommandContext<FabricClientCommandSource> context, String enabled) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null) return 0;

		boolean enableLogs = enabled.equalsIgnoreCase("true") || enabled.equalsIgnoreCase("on") || enabled.equalsIgnoreCase("1");
		databaseManager.setDebugLogsEnabled(enableLogs);

		String status = enableLogs ? "§aenabled" : "§cdisabled";
		client.player.displayClientMessage(
			Component.literal("§6[Inventory Network] Debug logs " + status + "§6!"),
			false
		);

		return Command.SINGLE_SUCCESS;
	}

	// Show current debug logs status
	private int executeDebugLogsStatus(CommandContext<FabricClientCommandSource> context) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null) return 0;

		boolean enabled = databaseManager.isDebugLogsEnabled();
		String status = enabled ? "§aenabled" : "§cdisabled";
		client.player.displayClientMessage(
			Component.literal("§6[Inventory Network] Debug logs are currently " + status + "§6."),
			false
		);
		client.player.displayClientMessage(
			Component.literal("§7Use /debugLogs <true|false> to toggle."),
			false
		);

		return Command.SINGLE_SUCCESS;
	}

	/**
	 * Execute /join <code> command to join a structure.
	 */
	private int executeJoinStructure(CommandContext<FabricClientCommandSource> context, String code) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null) {
			return Command.SINGLE_SUCCESS;
		}

		// Check if API client is initialized
		if (apiClient == null) {
			client.player.displayClientMessage(
				Component.literal("[BookKeeper] ")
					.withStyle(ChatFormatting.RED)
					.append(Component.literal("Error: API client not initialized. Check your config.")
						.withStyle(ChatFormatting.WHITE)),
				false
			);
			return Command.SINGLE_SUCCESS;
		}

		Player player = client.player;
		UUID uuid = player.getUUID();

		// Show processing message
		player.displayClientMessage(
			Component.literal("[BookKeeper] ")
				.withStyle(ChatFormatting.GOLD)
				.append(Component.literal("Joining structure with code: " + code + "...")
					.withStyle(ChatFormatting.WHITE)),
			false
		);

		// Run async to avoid blocking game thread
		CompletableFuture.runAsync(() -> {
			ApiClient.JoinStructureResponse response = apiClient.joinStructure(uuid, code);

			// Send result message on main thread
			client.execute(() -> {
				if (client.player != null) {
					if (response.success) {
						client.player.displayClientMessage(
							Component.literal("[BookKeeper] ")
								.withStyle(ChatFormatting.GOLD)
								.append(Component.literal(response.message)
									.withStyle(ChatFormatting.GREEN)),
							false
						);
					} else {
						client.player.displayClientMessage(
							Component.literal("[BookKeeper] Error: ")
								.withStyle(ChatFormatting.RED)
								.append(Component.literal(response.message)
									.withStyle(ChatFormatting.WHITE)),
							false
						);
					}
				}
			});
		});

		return Command.SINGLE_SUCCESS;
	}

	/**
	 * Execute /leave command to leave current structure.
	 */
	private int executeLeaveStructure(CommandContext<FabricClientCommandSource> context) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null) {
			return Command.SINGLE_SUCCESS;
		}

		client.player.displayClientMessage(
			Component.literal("[BookKeeper] ")
				.withStyle(ChatFormatting.GOLD)
				.append(Component.literal("The /leave command is not yet implemented. Please use the website to leave your structure.")
					.withStyle(ChatFormatting.YELLOW)),
			false
		);

		// TODO: Implement leave structure API endpoint in backend
		// For now, users must use the website

		return Command.SINGLE_SUCCESS;
	}

	/**
	 * Execute /web command to open BookKeeper website with auto-login.
	 * Requests a magic link and opens it in the player's default browser.
	 */
	private int executeWebLogin(CommandContext<FabricClientCommandSource> context) {
		Minecraft client = context.getSource().getClient();
		if (client.player == null) {
			return Command.SINGLE_SUCCESS;
		}

		// Check if API client is initialized
		if (apiClient == null) {
			client.player.displayClientMessage(
				Component.literal("[BookKeeper] ")
					.withStyle(ChatFormatting.RED)
					.append(Component.literal("Error: API client not initialized. Check your config.")
						.withStyle(ChatFormatting.WHITE)),
				false
			);
			return Command.SINGLE_SUCCESS;
		}

		Player player = client.player;
		UUID uuid = player.getUUID();
		String playerName = player.getName().getString();

		// Show processing message
		player.displayClientMessage(
			Component.literal("[BookKeeper] ")
				.withStyle(ChatFormatting.GOLD)
				.append(Component.literal("Requesting login link...")
					.withStyle(ChatFormatting.WHITE)),
			false
		);

		// Run async to avoid blocking game thread
		CompletableFuture.runAsync(() -> {
			ApiClient.MagicLinkResponse response = apiClient.requestMagicLink(uuid, playerName);

			// Send result message on main thread
			client.execute(() -> {
				if (client.player != null) {
					if (response != null && response.magicUrl != null) {
						// Try to open the URL in the default browser using Minecraft's Util class
						try {
							net.minecraft.Util.getPlatform().openUri(response.magicUrl);

							client.player.displayClientMessage(
								Component.literal("[BookKeeper] ")
									.withStyle(ChatFormatting.GOLD)
									.append(Component.literal("Opening website in your browser...")
										.withStyle(ChatFormatting.GREEN)),
								false
							);

							if (response.isNewUser) {
								client.player.displayClientMessage(
									Component.literal("[BookKeeper] ")
										.withStyle(ChatFormatting.GOLD)
										.append(Component.literal("Welcome! You can set a password for web access.")
											.withStyle(ChatFormatting.YELLOW)),
									false
								);
							}
						} catch (Exception e) {
							// If opening browser fails, show the URL as plain text
							client.player.displayClientMessage(
								Component.literal("[BookKeeper] ")
									.withStyle(ChatFormatting.RED)
									.append(Component.literal("Could not open browser automatically.")
										.withStyle(ChatFormatting.WHITE)),
								false
							);
							client.player.displayClientMessage(
								Component.literal("[BookKeeper] ")
									.withStyle(ChatFormatting.GOLD)
									.append(Component.literal("Copy this URL to your browser: ")
										.withStyle(ChatFormatting.YELLOW))
									.append(Component.literal(response.magicUrl)
										.withStyle(ChatFormatting.AQUA)),
								false
							);
						}
					} else {
						client.player.displayClientMessage(
							Component.literal("[BookKeeper] ")
								.withStyle(ChatFormatting.RED)
								.append(Component.literal("Failed to get login link. Please try again or contact an administrator.")
									.withStyle(ChatFormatting.WHITE)),
							false
						);
					}
				}
			});
		});

		return Command.SINGLE_SUCCESS;
	}
}
