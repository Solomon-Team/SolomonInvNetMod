package com.BookKeeper.InventoryNetwork;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Handles highlighting chests that contain items the player is holding.
 * Now uses ChestSyncManager as the single source of truth (server data).
 */
public class ChestHighlighter {
	private static final int HIGHLIGHT_RADIUS = 50;

	private final ChestSyncManager chestSyncManager;

	// Highlighting state
	private Set<BlockPos> highlightedChests = new HashSet<>();
	private int particleCounter = 0;

	// Particle style: 1 = Edges, 2 = Corners Only, 3 = Full Outline, 4 = Sparse Flame, 5 = Spectral Glow (NEW!)
	private int particleStyle = 5;

	// Animation counter for pulsing effect
	private long animationTick = 0;

	// Auto-clear timer (10 seconds = 200 ticks at 20 ticks/second)
	private static final int HIGHLIGHT_DURATION_TICKS = 200; // 10 seconds
	private int highlightTimer = 0;
	private boolean isHighlightActive = false;

	public ChestHighlighter(ChestSyncManager chestSyncManager) {
		this.chestSyncManager = chestSyncManager;
	}

	public void tick(Minecraft client) {
		// Spawn particles every 5 ticks
		particleCounter++;
		if (particleCounter >= 5) {
			particleCounter = 0;
			spawnParticles(client);
		}

		// Auto-clear highlighting after 10 seconds
		if (isHighlightActive) {
			highlightTimer++;
			if (highlightTimer >= HIGHLIGHT_DURATION_TICKS) {
				clearHighlights();
				if (client.player != null) {
					client.player.displayClientMessage(
						Component.literal("§7[Inventory Network] Highlighting cleared."),
						true
					);
				}
			}
		}
	}

	private void spawnParticles(Minecraft client) {
		if (highlightedChests.isEmpty() || client.level == null) {
			return;
		}

		animationTick++;

		for (BlockPos chestPos : highlightedChests) {
			switch (particleStyle) {
				case 1 -> spawnEdgeOutline(client, chestPos);
				case 2 -> spawnCornerMarkers(client, chestPos);
				case 3 -> spawnFullOutline(client, chestPos);
				case 4 -> spawnSparseFlame(client, chestPos);
				case 5 -> spawnSpectralGlow(client, chestPos); // NEW SPECTRAL STYLE!
				default -> spawnSpectralGlow(client, chestPos);
			}
		}
	}

	private void spawnEdgeOutline(Minecraft client, BlockPos pos) {
		double x = pos.getX();
		double y = pos.getY();
		double z = pos.getZ();

		int particlesPerEdge = 2;

		// Bottom edges
		spawnLineParticles(client, x, y, z, x + 1, y, z, particlesPerEdge);
		spawnLineParticles(client, x, y, z + 1, x + 1, y, z + 1, particlesPerEdge);
		spawnLineParticles(client, x, y, z, x, y, z + 1, particlesPerEdge);
		spawnLineParticles(client, x + 1, y, z, x + 1, y, z + 1, particlesPerEdge);

		// Top edges
		spawnLineParticles(client, x, y + 1, z, x + 1, y + 1, z, particlesPerEdge);
		spawnLineParticles(client, x, y + 1, z + 1, x + 1, y + 1, z + 1, particlesPerEdge);
		spawnLineParticles(client, x, y + 1, z, x, y + 1, z + 1, particlesPerEdge);
		spawnLineParticles(client, x + 1, y + 1, z, x + 1, y + 1, z + 1, particlesPerEdge);

		// Vertical edges
		spawnLineParticles(client, x, y, z, x, y + 1, z, particlesPerEdge);
		spawnLineParticles(client, x + 1, y, z, x + 1, y + 1, z, particlesPerEdge);
		spawnLineParticles(client, x, y, z + 1, x, y + 1, z + 1, particlesPerEdge);
		spawnLineParticles(client, x + 1, y, z + 1, x + 1, y + 1, z + 1, particlesPerEdge);
	}

	private void spawnCornerMarkers(Minecraft client, BlockPos pos) {
		double x = pos.getX();
		double y = pos.getY();
		double z = pos.getZ();

		double[][] corners = {
			{x, y, z}, {x + 1, y, z}, {x, y, z + 1}, {x + 1, y, z + 1},
			{x, y + 1, z}, {x + 1, y + 1, z}, {x, y + 1, z + 1}, {x + 1, y + 1, z + 1}
		};

		for (double[] corner : corners) {
			client.level.addParticle(ParticleTypes.ELECTRIC_SPARK, corner[0], corner[1], corner[2], 0, 0, 0);
			client.level.addParticle(ParticleTypes.WAX_ON, corner[0], corner[1], corner[2], 0, 0, 0);
		}
	}

	private void spawnFullOutline(Minecraft client, BlockPos pos) {
		double x = pos.getX();
		double y = pos.getY();
		double z = pos.getZ();

		int particlesPerEdge = 3;

		// All 12 edges
		// Bottom
		spawnLineParticles(client, x, y, z, x + 1, y, z, particlesPerEdge);
		spawnLineParticles(client, x, y, z + 1, x + 1, y, z + 1, particlesPerEdge);
		spawnLineParticles(client, x, y, z, x, y, z + 1, particlesPerEdge);
		spawnLineParticles(client, x + 1, y, z, x + 1, y, z + 1, particlesPerEdge);
		// Top
		spawnLineParticles(client, x, y + 1, z, x + 1, y + 1, z, particlesPerEdge);
		spawnLineParticles(client, x, y + 1, z + 1, x + 1, y + 1, z + 1, particlesPerEdge);
		spawnLineParticles(client, x, y + 1, z, x, y + 1, z + 1, particlesPerEdge);
		spawnLineParticles(client, x + 1, y + 1, z, x + 1, y + 1, z + 1, particlesPerEdge);
		// Vertical
		spawnLineParticles(client, x, y, z, x, y + 1, z, particlesPerEdge);
		spawnLineParticles(client, x + 1, y, z, x + 1, y + 1, z, particlesPerEdge);
		spawnLineParticles(client, x, y, z + 1, x, y + 1, z + 1, particlesPerEdge);
		spawnLineParticles(client, x + 1, y, z + 1, x + 1, y + 1, z + 1, particlesPerEdge);
	}

	private void spawnSparseFlame(Minecraft client, BlockPos pos) {
		double x = pos.getX() + 0.5;
		double y = pos.getY();
		double z = pos.getZ() + 0.5;

		for (int i = 0; i < 4; i++) {
			double angle = (i * Math.PI / 2) + (Math.random() * 0.3);
			double radius = 0.6;
			client.level.addParticle(
				ParticleTypes.SOUL_FIRE_FLAME,
				x + Math.cos(angle) * radius,
				y + Math.random() * 1.2,
				z + Math.sin(angle) * radius,
				0, 0.02, 0
			);
		}
	}

	private void spawnLineParticles(Minecraft client, double x1, double y1, double z1,
	                                  double x2, double y2, double z2, int count) {
		for (int i = 0; i < count; i++) {
			double t = i / (double) (count - 1);
			double x = x1 + (x2 - x1) * t;
			double y = y1 + (y2 - y1) * t;
			double z = z1 + (z2 - z1) * t;

			client.level.addParticle(ParticleTypes.SCRAPE, x, y, z, 0, 0, 0);
		}
	}

	/**
	 * NEW SPECTRAL GLOW EFFECT - Similar to spectral arrow highlighting!
	 * Uses dense, glowing particles with pulsing animation for maximum visibility.
	 */
	private void spawnSpectralGlow(Minecraft client, BlockPos pos) {
		double x = pos.getX();
		double y = pos.getY();
		double z = pos.getZ();

		// Pulsing effect - varies between 0.7 and 1.0
		double pulseScale = 0.85 + 0.15 * Math.sin(animationTick * 0.1);
		int particlesPerEdge = 5; // MUCH denser than before

		// Use GLOW particles for spectral effect - they're bright and visible through walls!
		// Draw ALL edges with dense particles

		// Bottom edges (more particles = more visible)
		spawnGlowLineParticles(client, x, y, z, x + 1, y, z, particlesPerEdge, pulseScale);
		spawnGlowLineParticles(client, x, y, z + 1, x + 1, y, z + 1, particlesPerEdge, pulseScale);
		spawnGlowLineParticles(client, x, y, z, x, y, z + 1, particlesPerEdge, pulseScale);
		spawnGlowLineParticles(client, x + 1, y, z, x + 1, y, z + 1, particlesPerEdge, pulseScale);

		// Top edges
		spawnGlowLineParticles(client, x, y + 1, z, x + 1, y + 1, z, particlesPerEdge, pulseScale);
		spawnGlowLineParticles(client, x, y + 1, z + 1, x + 1, y + 1, z + 1, particlesPerEdge, pulseScale);
		spawnGlowLineParticles(client, x, y + 1, z, x, y + 1, z + 1, particlesPerEdge, pulseScale);
		spawnGlowLineParticles(client, x + 1, y + 1, z, x + 1, y + 1, z + 1, particlesPerEdge, pulseScale);

		// Vertical edges
		spawnGlowLineParticles(client, x, y, z, x, y + 1, z, particlesPerEdge, pulseScale);
		spawnGlowLineParticles(client, x + 1, y, z, x + 1, y + 1, z, particlesPerEdge, pulseScale);
		spawnGlowLineParticles(client, x, y, z + 1, x, y + 1, z + 1, particlesPerEdge, pulseScale);
		spawnGlowLineParticles(client, x + 1, y, z + 1, x + 1, y + 1, z + 1, particlesPerEdge, pulseScale);

		// Add corner emphasis with ELECTRIC_SPARK for extra pop!
		double[][] corners = {
			{x, y, z}, {x + 1, y, z}, {x, y, z + 1}, {x + 1, y, z + 1},
			{x, y + 1, z}, {x + 1, y + 1, z}, {x, y + 1, z + 1}, {x + 1, y + 1, z + 1}
		};
		for (double[] corner : corners) {
			client.level.addParticle(ParticleTypes.ELECTRIC_SPARK,
				corner[0], corner[1], corner[2], 0, 0.01, 0);
			// Add GLOW for brightness
			client.level.addParticle(ParticleTypes.GLOW,
				corner[0], corner[1], corner[2], 0, 0, 0);
		}

		// Add floating souls around the chest for extra spectral effect (every other tick)
		if (animationTick % 2 == 0) {
			double centerX = x + 0.5;
			double centerY = y + 0.5;
			double centerZ = z + 0.5;
			double angle = (animationTick * 0.05) % (2 * Math.PI);
			double radius = 0.8;

			// Orbiting soul particles
			client.level.addParticle(ParticleTypes.SOUL,
				centerX + Math.cos(angle) * radius,
				centerY,
				centerZ + Math.sin(angle) * radius,
				0, 0.02, 0);
			client.level.addParticle(ParticleTypes.SOUL,
				centerX + Math.cos(angle + Math.PI) * radius,
				centerY,
				centerZ + Math.sin(angle + Math.PI) * radius,
				0, 0.02, 0);
		}
	}

	/**
	 * Spawns GLOW particles along a line - these particles are very bright and visible!
	 */
	private void spawnGlowLineParticles(Minecraft client, double x1, double y1, double z1,
	                                     double x2, double y2, double z2, int count, double scale) {
		for (int i = 0; i < count; i++) {
			double t = i / (double) (count - 1);
			double x = x1 + (x2 - x1) * t;
			double y = y1 + (y2 - y1) * t;
			double z = z1 + (z2 - z1) * t;

			// GLOW particles - bright yellow/white, visible through walls
			client.level.addParticle(ParticleTypes.GLOW, x, y, z, 0, 0, 0);

			// Add some ENCHANTED_HIT for sparkle effect
			if (i % 2 == 0) {
				client.level.addParticle(ParticleTypes.ENCHANTED_HIT, x, y, z, 0, 0.01, 0);
			}
		}
	}

	public void setParticleStyle(int style) {
		this.particleStyle = style;
	}

	/**
	 * Highlights chests containing a specific item by name.
	 * Called when user clicks an item in the UI panel.
	 * This is now the ONLY way to trigger highlighting.
	 * Highlighting will automatically clear after 10 seconds.
	 * Uses ChestSyncManager as source of truth (server data).
	 *
	 * @param itemName The display name of the item to search for
	 */
	public void highlightItemByName(String itemName) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.level == null) {
			return;
		}

		highlightedChests.clear();
		highlightTimer = 0; // Reset timer
		isHighlightActive = false; // Will be set to true if chests are found

		BlockPos playerPos = client.player.blockPosition();

		// Search all synced chests from server
		for (ChestSyncManager.ChestSnapshot chest : chestSyncManager.getAllChests()) {
			// Check proximity (within HIGHLIGHT_RADIUS blocks)
			double distance = Math.sqrt(
				Math.pow(chest.x - playerPos.getX(), 2) +
				Math.pow(chest.y - playerPos.getY(), 2) +
				Math.pow(chest.z - playerPos.getZ(), 2)
			);

			if (distance > HIGHLIGHT_RADIUS) {
				continue; // Too far away
			}

			// Check if chest contains item matching the display name
			if (chestContainsItemByName(chest, itemName)) {
				highlightedChests.add(new BlockPos(chest.x, chest.y, chest.z));
			}
		}

		if (!highlightedChests.isEmpty()) {
			isHighlightActive = true; // Start the timer
			client.player.displayClientMessage(
				Component.literal("§6[Inventory Network] Found " + highlightedChests.size() +
					" chest(s) with " + itemName + " nearby! (10s timer)"),
				true
			);
		} else {
			client.player.displayClientMessage(
				Component.literal("§c[Inventory Network] No chests with " + itemName + " found nearby."),
				true
			);
		}
	}

	/**
	 * Checks if a chest contains an item matching the display name.
	 */
	private boolean chestContainsItemByName(ChestSyncManager.ChestSnapshot chest, String displayName) {
		if (chest.items == null) return false;

		for (String slotKey : chest.items.keySet()) {
			var itemObj = chest.items.getAsJsonObject(slotKey);
			if (itemObj.has("name")) {
				String itemDisplayName = itemObj.get("name").getAsString();
				if (itemDisplayName.equals(displayName)) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Clears all highlighted chests.
	 * Useful for stopping the highlighting effect manually or after timer expires.
	 */
	public void clearHighlights() {
		highlightedChests.clear();
		isHighlightActive = false;
		highlightTimer = 0;
	}
}
