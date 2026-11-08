/*
 * Blackout Marker
 * Copyright (C) 2026 Blackout Marker contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package dev.blackoutmarker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Collects the current frame's targets and maintains the (small amount of)
 * state that cannot be read from the client every frame:
 *
 * <ul>
 *   <li>scoreboard tags are server-side in vanilla, so they are copied from
 *       the integrated server every 10 client ticks;</li>
 *   <li>block-type targets are found by scanning loaded chunks around the
 *       player every second.</li>
 * </ul>
 */
public final class TargetManager {
    public static final class Target {
        public final String key;
        public final String label;
        public final Vec3 center;
        public final List<AABB> boxes;
        public final double distanceSq;
        public final boolean block;
        public final BlockPos blockPos;
        public final float halfSize;
        public final boolean centerMode;
        public final boolean hideWhenOccluded;
        public boolean visible;

        public Target(String key, String label, Vec3 center, List<AABB> boxes, double distanceSq,
                      boolean block, BlockPos blockPos, float halfSize, boolean centerMode,
                      boolean hideWhenOccluded) {
            this.key = key;
            this.label = label;
            this.center = center;
            this.boxes = boxes;
            this.distanceSq = distanceSq;
            this.block = block;
            this.blockPos = blockPos;
            this.halfSize = halfSize;
            this.centerMode = centerMode;
            this.hideWhenOccluded = hideWhenOccluded;
        }
    }

    private record ResolvedStyle(float halfSize, boolean centerMode, boolean hideWhenOccluded) {
    }

    private final ConfigStore store;
    private volatile Set<UUID> taggedEntities = Collections.emptySet();
    private volatile Map<UUID, String> taggedEntityTags = Collections.emptyMap();
    private volatile boolean tagSourceAvailable;
    private int tagTick;

    private final Set<BlockPos> blockTypeCache = new HashSet<>();
    private int blockScanTick;
    private BlockPos lastScanPos;
    private ResourceKey<Level> lastScanDimension;
    private boolean blockScanDirty = true;

    public TargetManager(ConfigStore store) {
        this.store = store;
    }

    public void markDirty() {
        blockScanDirty = true;
    }

    public boolean isTagSourceAvailable() {
        return tagSourceAvailable;
    }

    public void tick(Minecraft minecraft) {
        ClientLevel level = minecraft.level;
        if (level == null || minecraft.player == null) {
            taggedEntities = Collections.emptySet();
            taggedEntityTags = Collections.emptyMap();
            blockTypeCache.clear();
            return;
        }

        if (++tagTick >= 10) {
            tagTick = 0;
            refreshServerTags(minecraft, level);
        }

        BlackoutConfig config = store.get();
        if (config.blockTypes.isEmpty()) {
            blockTypeCache.clear();
            return;
        }

        BlockPos pos = minecraft.player.blockPosition();
        boolean moved = lastScanPos == null || pos.distSqr(lastScanPos) >= 64.0;
        boolean dimensionChanged = lastScanDimension != level.dimension();
        if (blockScanDirty || moved || dimensionChanged || ++blockScanTick >= 20) {
            blockScanTick = 0;
            blockScanDirty = false;
            lastScanPos = pos.immutable();
            lastScanDimension = level.dimension();
            scanBlockTypes(level, pos);
        }
    }

    private void refreshServerTags(Minecraft minecraft, ClientLevel clientLevel) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            taggedEntities = Collections.emptySet();
            taggedEntityTags = Collections.emptyMap();
            tagSourceAvailable = false;
            return;
        }

        ServerLevel serverLevel = server.getLevel(clientLevel.dimension());
        if (serverLevel == null) {
            taggedEntities = Collections.emptySet();
            taggedEntityTags = Collections.emptyMap();
            tagSourceAvailable = false;
            return;
        }

        List<String> tagOrder = List.copyOf(store.get().tags);
        Set<String> wanted = Set.copyOf(tagOrder);
        var expectedDimension = clientLevel.dimension();
        tagSourceAvailable = true;
        if (wanted.isEmpty()) {
            taggedEntities = Collections.emptySet();
            taggedEntityTags = Collections.emptyMap();
            return;
        }

        // The integrated server owns these entities. Reading them from the
        // render/client thread is racy, so do the query on the server thread.
        try {
            server.execute(() -> {
                try {
                    if (minecraft.level == null || minecraft.level.dimension() != expectedDimension) {
                        return;
                    }
                    Map<UUID, String> found = new HashMap<>();
                    for (Entity entity : serverLevel.getAllEntities()) {
                        for (String tag : tagOrder) {
                            if (entity.entityTags().contains(tag)) {
                                found.put(entity.getUUID(), tag);
                                break;
                            }
                        }
                    }
                    taggedEntityTags = Map.copyOf(found);
                    taggedEntities = taggedEntityTags.keySet();
                } catch (Throwable t) {
                    BlackoutMarkerMod.LOGGER.warn("Failed to read server scoreboard tags", t);
                }
            });
        } catch (Throwable t) {
            taggedEntities = Collections.emptySet();
            taggedEntityTags = Collections.emptyMap();
            tagSourceAvailable = false;
        }
    }

    private void scanBlockTypes(ClientLevel level, BlockPos center) {
        blockTypeCache.clear();
        BlackoutConfig config = store.get();
        if (config.blockTypes.isEmpty()) {
            return;
        }

        int radius = config.blockScanRadius;
        int radiusSq = radius * radius;
        int chunkRadius = Math.max(1, (radius + 15) / 16);
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        int max = config.maxBlockTargets;
        outer:
        for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
            for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                LevelChunk chunk = level.getChunk(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }

                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection section = sections[i];
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }

                    boolean maybe = section.getStates().maybeHas(state ->
                            !state.isAir() && config.blockTypes.contains(blockId(state)));
                    if (!maybe) {
                        continue;
                    }

                    int sectionY = level.getSectionYFromSectionIndex(i);
                    int baseY = sectionY << 4;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                int wx = (chunkX << 4) + x;
                                int wy = baseY + y;
                                int wz = (chunkZ << 4) + z;
                                int ddx = wx - center.getX();
                                int ddy = wy - center.getY();
                                int ddz = wz - center.getZ();
                                if (ddx * ddx + ddy * ddy + ddz * ddz > radiusSq) {
                                    continue;
                                }
                                BlockPos pos = new BlockPos(wx, wy, wz);
                                BlockState state = section.getBlockState(x, y, z);
                                if (state.isAir() || !config.blockTypes.contains(blockId(state))) {
                                    continue;
                                }
                                blockTypeCache.add(pos);
                                if (blockTypeCache.size() >= max) {
                                    break outer;
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Builds the target list for the current frame. Positions are the current
     * interpolated/actual client positions; no server query happens here.
     */
    public List<Target> collect(Minecraft minecraft) {
        BlackoutConfig config = store.get();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (!config.enabled || level == null || player == null) {
            return List.of();
        }

        String dimension = level.dimension().identifier().toString();
        Map<String, Target> targets = new LinkedHashMap<>();
        Set<UUID> manualEntityIds = new HashSet<>();
        for (BlackoutConfig.ManualEntity entry : config.manualEntities) {
            if (dimension.equals(entry.dimension)) {
                try {
                    manualEntityIds.add(UUID.fromString(entry.uuid));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player || entity.isInvisibleTo(player)) {
                continue;
            }

            String type = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
            boolean matched = config.entityTypes.contains(type)
                    || taggedEntities.contains(entity.getUUID())
                    || manualEntityIds.contains(entity.getUUID());
            if (!matched) {
                continue;
            }

            AABB box = entity.getBoundingBox();
            if (box == null || box.getXsize() <= 0.0 || box.getYsize() <= 0.0 || box.getZsize() <= 0.0) {
                continue;
            }

            Vec3 center = box.getCenter();
            double dst = center.distanceToSqr(player.getX(), player.getEyeY(), player.getZ());
            String key = "E:" + entity.getUUID();
            String label = entity.getDisplayName().getString() + " (" + type + ")";
            ResolvedStyle style = resolveEntityStyle(config, entity, type);
            targets.put(key, new Target(key, label, center, List.of(box), dst,
                    false, null, style.halfSize(), style.centerMode(), style.hideWhenOccluded()));
        }

        // Manual block targets and scanned block-type targets.
        for (BlackoutConfig.ManualBlock manual : config.manualBlocks) {
            if (!dimension.equals(manual.dimension)) {
                continue;
            }
            addBlockTarget(level, player, targets, new BlockPos(manual.x, manual.y, manual.z));
        }

        for (BlockPos pos : blockTypeCache) {
            addBlockTarget(level, player, targets, pos);
        }

        List<Target> result = new ArrayList<>(targets.values());
        result.sort(Comparator.comparingDouble(t -> t.distanceSq));
        if (result.size() > config.maxTargets) {
            result = new ArrayList<>(result.subList(0, config.maxTargets));
        }
        return result;
    }

    private void addBlockTarget(ClientLevel level, LocalPlayer player, Map<String, Target> targets, BlockPos pos) {
        BlackoutConfig config = store.get();
        if (!level.isLoaded(pos)) {
            return;
        }

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }

        String dimension = level.dimension().identifier().toString();
        boolean manual = config.manualBlocks.stream().anyMatch(b ->
                b.x == pos.getX() && b.y == pos.getY() && b.z == pos.getZ() && dimension.equals(b.dimension));
        if (!manual && !config.blockTypes.contains(blockId(state))) {
            return;
        }

        VoxelShape shape = state.getShape(level, pos);
        if (shape.isEmpty()) {
            return;
        }

        List<AABB> boxes = shape.toAabbs();
        if (boxes.isEmpty()) {
            return;
        }

        // getShape() is normally already in world coordinates. Be defensive
        // about a local-space shape from a custom block implementation.
        AABB bounds = shape.bounds();
        boolean worldSpace = bounds.minX >= pos.getX() - 0.01 && bounds.maxX <= pos.getX() + 1.01
                && bounds.minY >= pos.getY() - 0.01 && bounds.maxY <= pos.getY() + 1.01
                && bounds.minZ >= pos.getZ() - 0.01 && bounds.maxZ <= pos.getZ() + 1.01;
        if (!worldSpace) {
            List<AABB> moved = new ArrayList<>(boxes.size());
            for (AABB box : boxes) {
                moved.add(box.move(pos));
            }
            boxes = moved;
            bounds = bounds.move(pos);
        }

        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        String key = "B:" + dimension + ":" + pos.asLong();
        String label = state.getBlock().getName().getString() + " @ " + pos.getX() + "," + pos.getY() + "," + pos.getZ();
        double dst = center.distanceToSqr(player.getX(), player.getEyeY(), player.getZ());
        ResolvedStyle style = resolveBlockStyle(config, dimension, pos, blockId(state));
        targets.put(key, new Target(key, label, center, boxes, dst,
                true, pos, style.halfSize(), style.centerMode(), style.hideWhenOccluded()));
    }

    public static String blockStyleKey(String dimension, int x, int y, int z) {
        return dimension + "|" + x + "|" + y + "|" + z;
    }

    private ResolvedStyle resolveEntityStyle(BlackoutConfig config, Entity entity, String type) {
        BlackoutConfig.Style style = config.manualEntityStyles.get(entity.getUUID().toString());
        if (style == null) {
            style = config.entityTypeStyles.get(type);
        }
        if (style == null) {
            String tag = taggedEntityTags.get(entity.getUUID());
            if (tag != null) {
                style = config.tagStyles.get(tag);
            }
        }
        return resolveStyle(style, config, false);
    }

    private ResolvedStyle resolveBlockStyle(BlackoutConfig config, String dimension, BlockPos pos, String type) {
        BlackoutConfig.Style style = config.manualBlockStyles.get(
                blockStyleKey(dimension, pos.getX(), pos.getY(), pos.getZ()));
        if (style == null) {
            style = config.blockTypeStyles.get(type);
        }
        return resolveStyle(style, config, true);
    }

    private static ResolvedStyle resolveStyle(BlackoutConfig.Style style, BlackoutConfig config, boolean block) {
        float half = config.squareHalfSize;
        boolean center = block
                ? config.blockVisibility == BlackoutConfig.BlockVisibility.CENTER
                : config.entityVisibility == BlackoutConfig.EntityVisibility.CENTER;
        boolean hideWhenOccluded = config.hideWhenOccluded;
        if (style != null) {
            if (style.squareSize != null) {
                half = style.squareSize / 2.0f;
            }
            if (style.visibility == BlackoutConfig.StyleVisibility.ANY) {
                center = false;
            } else if (style.visibility == BlackoutConfig.StyleVisibility.CENTER) {
                center = true;
            }
            if (style.occlusion == BlackoutConfig.StyleOcclusion.HIDE) {
                hideWhenOccluded = true;
            } else if (style.occlusion == BlackoutConfig.StyleOcclusion.SHOW) {
                hideWhenOccluded = false;
            }
        }
        half = Math.max(8.0f, Math.min(2048.0f, half));
        return new ResolvedStyle(half, center, hideWhenOccluded);
    }

    private static String blockId(BlockState state) {
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return id.toString();
    }
}
