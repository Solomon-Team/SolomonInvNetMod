package com.BookKeeper.InventoryNetwork;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Handles entity tracking and highlighting with particle effects
 */
public class EntityTracker {
	private static final double RAYCAST_RANGE = 100.0;
	private static final int PARTICLES_PER_EDGE = 4;

	private KeyMapping trackEntityKey;
	private Entity trackedEntity = null;
	private int entityParticleCounter = 0;
	private int entityInfoCounter = 0;

	public void initialize() {
		// Register entity tracking keybind (V key by default - rebindable in settings)
		trackEntityKey = new KeyMapping(
			"key.inventorynetwork.track_entity",
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_V,
			new KeyMapping.Category(ResourceLocation.fromNamespaceAndPath("minecraft", "misc"))
		);
		KeyBindingHelper.registerKeyBinding(trackEntityKey);
	}

	public void tick(Minecraft client) {
		// Handle entity tracking keybind
		while (trackEntityKey.consumeClick()) {
			handleEntityTracking(client);
		}

		// Update tracked entity validity
		updateTrackedEntity(client);

		// Spawn particles around tracked entity every 2 ticks
		entityParticleCounter++;
		if (entityParticleCounter >= 2) {
			entityParticleCounter = 0;
			spawnTrackedEntityParticles(client);
		}

		// Print entity info every 20 ticks (1 second)
		entityInfoCounter++;
		if (entityInfoCounter >= 20) {
			entityInfoCounter = 0;
			printEntityInfo(client);
		}
	}

	private void handleEntityTracking(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}

		// Perform custom long-range raycast to find entity at crosshair
		Entity targetEntity = getLookedAtEntity(client, RAYCAST_RANGE);

		if (targetEntity != null) {
			// Toggle tracking for this entity
			if (trackedEntity == targetEntity) {
				// Same entity - stop tracking
				stopTrackingEntity(client);
			} else {
				// Different entity - start tracking
				startTrackingEntity(client, targetEntity);
			}
		} else {
			// No entity in crosshair - stop tracking
			stopTrackingEntity(client);
		}
	}

	private Entity getLookedAtEntity(Minecraft client, double range) {
		if (client.player == null || client.level == null) {
			return null;
		}

		Vec3 eyePos = client.player.getEyePosition(1.0F);
		Vec3 lookVec = client.player.getViewVector(1.0F);
		Vec3 endPos = eyePos.add(lookVec.scale(range));

		// Create bounding box for the raycast
		AABB searchBox = client.player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.0);

		Entity closestEntity = null;
		double closestDistance = range;

		// Find all entities in the search area
		for (Entity entity : client.level.getEntities(client.player, searchBox)) {
			if (entity == client.player) continue;

			// Get entity's bounding box and check if raycast intersects
			AABB entityBox = entity.getBoundingBox().inflate(0.3);
			var hitResult = entityBox.clip(eyePos, endPos);

			if (hitResult.isPresent()) {
				double distance = eyePos.distanceTo(hitResult.get());
				if (distance < closestDistance) {
					closestDistance = distance;
					closestEntity = entity;
				}
			}
		}

		return closestEntity;
	}

	private void startTrackingEntity(Minecraft client, Entity entity) {
		// Start tracking new entity
		trackedEntity = entity;

		String entityName = entity.getName().getString();
		client.player.displayClientMessage(
			Component.literal("§a[Inventory Network] Tracking entity: " + entityName),
			true
		);
	}

	private void stopTrackingEntity(Minecraft client) {
		trackedEntity = null;

		if (client.player != null) {
			client.player.displayClientMessage(
				Component.literal("§c[Inventory Network] Entity tracking stopped"),
				true
			);
		}
	}

	private void updateTrackedEntity(Minecraft client) {
		// Check if tracked entity still exists and is valid
		if (trackedEntity != null) {
			if (trackedEntity.isRemoved() || trackedEntity.level() != client.level) {
				// Entity was removed or changed dimension
				stopTrackingEntity(client);
			}
		}
	}

	private void printEntityInfo(Minecraft client) {
		if (trackedEntity == null || client.player == null || trackedEntity.isRemoved()) {
			return;
		}

		// Get entity position
		Vec3 pos = trackedEntity.position();
		String coords = String.format("§e[%.1f, %.1f, %.1f]", pos.x, pos.y, pos.z);

		// Get entity name
		String entityName = trackedEntity.getName().getString();

		// Get entity type
		String entityType = trackedEntity.getType().toString();

		// Build info message
		StringBuilder info = new StringBuilder();
		info.append("§b[Entity] §f").append(entityName);
		info.append(" §7(").append(entityType).append(")");
		info.append(" §aat ").append(coords);

		// Get health if it's a living entity
		if (trackedEntity instanceof LivingEntity livingEntity) {
			float health = livingEntity.getHealth();
			float maxHealth = livingEntity.getMaxHealth();
			info.append(" §c❤ ").append(String.format("%.1f/%.1f", health, maxHealth));

			// Add armor value if available
			int armor = livingEntity.getArmorValue();
			if (armor > 0) {
				info.append(" §7⛨ ").append(armor);
			}
		}

		// Get velocity
		Vec3 velocity = trackedEntity.getDeltaMovement();
		double speed = Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z);
		if (speed > 0.01) {
			info.append(" §6⚡ ").append(String.format("%.2f m/s", speed * 20));
		}

		// Get distance from player
		double distance = client.player.position().distanceTo(trackedEntity.position());
		info.append(" §d↔ ").append(String.format("%.1fm", distance));

		// Display in chat
		client.player.displayClientMessage(Component.literal(info.toString()), false);
	}

	private void spawnTrackedEntityParticles(Minecraft client) {
		if (trackedEntity == null || client.level == null || trackedEntity.isRemoved()) {
			return;
		}

		AABB box = trackedEntity.getBoundingBox();

		// Spawn particles along the edges of the entity's bounding box
		// Bottom edges
		spawnEntityLineParticles(client, box.minX, box.minY, box.minZ, box.maxX, box.minY, box.minZ, PARTICLES_PER_EDGE);
		spawnEntityLineParticles(client, box.minX, box.minY, box.maxZ, box.maxX, box.minY, box.maxZ, PARTICLES_PER_EDGE);
		spawnEntityLineParticles(client, box.minX, box.minY, box.minZ, box.minX, box.minY, box.maxZ, PARTICLES_PER_EDGE);
		spawnEntityLineParticles(client, box.maxX, box.minY, box.minZ, box.maxX, box.minY, box.maxZ, PARTICLES_PER_EDGE);

		// Top edges
		spawnEntityLineParticles(client, box.minX, box.maxY, box.minZ, box.maxX, box.maxY, box.minZ, PARTICLES_PER_EDGE);
		spawnEntityLineParticles(client, box.minX, box.maxY, box.maxZ, box.maxX, box.maxY, box.maxZ, PARTICLES_PER_EDGE);
		spawnEntityLineParticles(client, box.minX, box.maxY, box.minZ, box.minX, box.maxY, box.maxZ, PARTICLES_PER_EDGE);
		spawnEntityLineParticles(client, box.maxX, box.maxY, box.minZ, box.maxX, box.maxY, box.maxZ, PARTICLES_PER_EDGE);

		// Vertical edges
		spawnEntityLineParticles(client, box.minX, box.minY, box.minZ, box.minX, box.maxY, box.minZ, PARTICLES_PER_EDGE);
		spawnEntityLineParticles(client, box.maxX, box.minY, box.minZ, box.maxX, box.maxY, box.minZ, PARTICLES_PER_EDGE);
		spawnEntityLineParticles(client, box.minX, box.minY, box.maxZ, box.minX, box.maxY, box.maxZ, PARTICLES_PER_EDGE);
		spawnEntityLineParticles(client, box.maxX, box.minY, box.maxZ, box.maxX, box.maxY, box.maxZ, PARTICLES_PER_EDGE);
	}

	private void spawnEntityLineParticles(Minecraft client, double x1, double y1, double z1,
	                                        double x2, double y2, double z2, int count) {
		for (int i = 0; i < count; i++) {
			double t = i / (double) (count - 1);
			double x = x1 + (x2 - x1) * t;
			double y = y1 + (y2 - y1) * t;
			double z = z1 + (z2 - z1) * t;

			// Use red flame particles for high visibility
			client.level.addParticle(
				ParticleTypes.FLAME,
				x, y, z,
				0, 0.02, 0
			);
		}
	}
}
