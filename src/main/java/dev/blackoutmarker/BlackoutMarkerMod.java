/*
 * Blackout Marker
 * Copyright (C) 2026 Blackout Marker contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package dev.blackoutmarker;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BlackoutMarkerMod implements ClientModInitializer {
    public static final String MOD_ID = "blackout-marker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static ConfigStore store;
    private static TargetManager targetManager;

    private static KeyMapping openMenuKey;
    private static KeyMapping pickKey;

    @Override
    public void onInitializeClient() {
        store = new ConfigStore();
        store.load();
        targetManager = new TargetManager(store);

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(MOD_ID, "controls"));

        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.blackoutmarker.menu",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_B,
                category
        ));

        pickKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.blackoutmarker.pick",
                InputConstants.Type.KEYBOARD,
                InputConstants.KEY_V,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(this::onEndTick);
        LOGGER.info("Blackout Marker initialized");
    }

    public static BlackoutConfig config() {
        return store.get();
    }

    public static TargetManager targetManager() {
        return targetManager;
    }

    public static ConfigStore store() {
        return store;
    }

    public static void saveConfig() {
        if (store != null) {
            store.save();
        }
    }

    private void onEndTick(Minecraft minecraft) {
        while (openMenuKey.consumeClick()) {
            minecraft.setScreenAndShow(new BlackoutConfigScreen(null));
        }

        while (pickKey.consumeClick()) {
            pickTarget(minecraft, isShiftDown());
        }

        if (targetManager != null) {
            targetManager.tick(minecraft);
        }
    }

    private static boolean isShiftDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT)
                || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    private static void pickTarget(Minecraft minecraft, boolean remove) {
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        HitResult hit = minecraft.hitResult;
        if (hit == null) {
            actionBar(minecraft, Component.literal("没有对准实体或方块").withStyle(ChatFormatting.RED));
            return;
        }

        BlackoutConfig config = config();
        String dimension = minecraft.level.dimension().identifier().toString();
        boolean changed = false;
        String what;

        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult entityHit) {
            Entity entity = entityHit.getEntity();
            String uuid = entity.getUUID().toString();
            if (remove) {
                changed = config.manualEntities.removeIf(entry -> uuid.equals(entry.uuid));
                if (changed) {
                    config.manualEntityStyles.remove(uuid);
                }
            } else {
                boolean exists = config.manualEntities.stream().anyMatch(entry -> uuid.equals(entry.uuid));
                if (!exists) {
                    config.manualEntities.add(new BlackoutConfig.ManualEntity(
                            dimension, uuid, entity.getDisplayName().getString()));
                    changed = true;
                }
            }
            what = entity.getDisplayName().getString();
        } else if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            if (remove) {
                changed = config.manualBlocks.removeIf(entry ->
                        entry.x == pos.getX() && entry.y == pos.getY() && entry.z == pos.getZ()
                                && dimension.equals(entry.dimension));
                if (changed) {
                    config.manualBlockStyles.remove(TargetManager.blockStyleKey(
                            dimension, pos.getX(), pos.getY(), pos.getZ()));
                }
            } else {
                boolean exists = config.manualBlocks.stream().anyMatch(entry ->
                        entry.x == pos.getX() && entry.y == pos.getY() && entry.z == pos.getZ()
                                && dimension.equals(entry.dimension));
                if (!exists) {
                    String label = minecraft.level.getBlockState(pos).getBlock().getName().getString();
                    config.manualBlocks.add(new BlackoutConfig.ManualBlock(
                            dimension, pos.getX(), pos.getY(), pos.getZ(), label));
                    changed = true;
                }
            }
            what = pos.getX() + "," + pos.getY() + "," + pos.getZ();
        } else {
            actionBar(minecraft, Component.literal("没有对准实体或方块").withStyle(ChatFormatting.RED));
            return;
        }

        if (changed) {
            saveConfig();
            targetManager.markDirty();
            actionBar(minecraft, Component.literal(
                    (remove ? "已移除目标: " : "已添加目标: ") + what).withStyle(ChatFormatting.GREEN));
        } else {
            actionBar(minecraft, Component.literal(
                    remove ? "该目标不在列表中" : "该目标已在列表中").withStyle(ChatFormatting.YELLOW));
        }
    }

    private static void actionBar(Minecraft minecraft, Component component) {
        if (minecraft.player != null) {
            minecraft.player.sendOverlayMessage(component);
        }
    }
}
