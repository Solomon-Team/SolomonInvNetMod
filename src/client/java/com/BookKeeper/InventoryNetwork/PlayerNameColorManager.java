package com.BookKeeper.InventoryNetwork;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/**
 * Player name color management - deprecated (database removed).
 * Whitelist/blacklist feature removed in favor of server-side management.
 */
public class PlayerNameColorManager {

	public PlayerNameColorManager() {
		// Database removed - feature deprecated
	}

	public void tick(Minecraft client) {
		// No-op: whitelist/blacklist feature removed
	}
}
