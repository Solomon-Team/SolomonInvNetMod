package com.BookKeeper.InventoryNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Colors player name tags based on whitelist/blacklist stored in the DatabaseManager.
 * - Blacklisted: red
 * - Whitelisted: blue
 * - Otherwise: restore default name (clear custom name)
 */
public class PlayerNameColorManager {
	private final DatabaseManager databaseManager;

	public PlayerNameColorManager(DatabaseManager databaseManager) {
		this.databaseManager = databaseManager;
	}

	public void tick(Minecraft client) {
		if (client.player == null || client.level == null) return;

		for (Player p : client.level.players()) {
			// Skip local player
			if (p == client.player) continue;

			// Use UUID when possible for reliable matching, fall back to display name
			String uuid = p.getStringUUID();
			String cleanName = stripFormatting(p.getName().getString());

			try {
				boolean black = databaseManager.isBlacklistedByUuid(uuid) || databaseManager.isBlacklisted(cleanName);
				boolean white = databaseManager.isWhitelistedByUuid(uuid) || databaseManager.isWhitelisted(cleanName);

				if (black && !white) {
					p.setCustomName(Component.literal(cleanName).withStyle(ChatFormatting.RED));
					p.setCustomNameVisible(true);
				} else if (white && !black) {
					p.setCustomName(Component.literal(cleanName).withStyle(ChatFormatting.BLUE));
					p.setCustomNameVisible(true);
				} else {
					// If both or neither, do not override server/team coloring; clear custom name
					p.setCustomName(null);
					p.setCustomNameVisible(false);
				}
			} catch (Exception e) {
				InventoryNetworkMod.LOGGER.warn("Failed to update name tag color for {} ({})", cleanName, uuid, e);
			}
		}
	}

	private String stripFormatting(String s) {
		if (s == null) return "";
		return s.replaceAll("§.", "");
	}
}
