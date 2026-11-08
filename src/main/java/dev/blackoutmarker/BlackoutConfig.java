/*
 * Blackout Marker
 * Copyright (C) 2026 Blackout Marker contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package dev.blackoutmarker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent client configuration.
 *
 * <p>All identifiers are stored as strings so a config written by an older
 * or newer registry can still be read. Unknown entries are simply ignored
 * by {@link TargetManager}.</p>
 */
public final class BlackoutConfig {
    public enum EntityVisibility {
        HITBOX,
        CENTER
    }

    public enum BlockVisibility {
        PART,
        CENTER
    }

    /**
     * Per-target override. Missing/INHERIT values fall back to the global
     * {@link #squareHalfSize} and visibility mode.
     */
    public enum StyleVisibility {
        INHERIT,
        ANY,
        CENTER
    }

    public enum StyleOcclusion {
        INHERIT,
        HIDE,
        SHOW
    }

    public enum StyleKind {
        TAG,
        ENTITY_TYPE,
        BLOCK_TYPE,
        MANUAL_ENTITY,
        MANUAL_BLOCK
    }

    public static final class Style {
        /** Square edge length in pixels; null means the global size. */
        public Integer squareSize;
        public StyleVisibility visibility = StyleVisibility.INHERIT;
        public StyleOcclusion occlusion = StyleOcclusion.INHERIT;

        public Style() {
        }

        public Style(Integer squareSize, StyleVisibility visibility, StyleOcclusion occlusion) {
            this.squareSize = squareSize;
            this.visibility = visibility;
            this.occlusion = occlusion;
        }

        public boolean isEmpty() {
            return squareSize == null
                    && (visibility == null || visibility == StyleVisibility.INHERIT)
                    && (occlusion == null || occlusion == StyleOcclusion.INHERIT);
        }
    }

    public boolean enabled = true;

    /** Half edge of the square in screen pixels. 160 => 320x320. */
    public int squareHalfSize = 160;

    /**
     * HITBOX = any part of the entity's collision box is visible.
     * CENTER = the entity's center point is visible and unobstructed.
     */
    public EntityVisibility entityVisibility = EntityVisibility.HITBOX;

    /**
     * PART = any part of the block's VoxelShape is visible.
     * CENTER = the block center point is visible and unobstructed.
     */
    public BlockVisibility blockVisibility = BlockVisibility.PART;

    /**
     * Global default: when true, a fully occluded target is hidden. Individual
     * styles can override this.
     */
    public Boolean hideWhenOccluded = Boolean.TRUE;

    /** Legacy single direction marker; migrated into {@link #directionMarkers}. */
    public boolean directionMarkerEnabled = false;
    public float directionMarkerYaw = 0.0f;

    /** Multiple fixed world-direction markers, each with its own style. */
    public List<DirectionMarker> directionMarkers = new ArrayList<>();

    /** Markers attached to the vanilla celestial bodies. */
    public boolean sunMarkerEnabled = false;
    public boolean moonMarkerEnabled = false;
    public Style sunStyle = new Style();
    public Style moonStyle = new Style();

    /** Radius, in blocks, used when enumerating selected block types. */
    public int blockScanRadius = 48;

    /** Safety limits for the GPU visibility pass. */
    public int maxTargets = 96;
    public int maxBlockTargets = 192;

    /** Entity scoreboard tags, without a leading '#'. */
    public Set<String> tags = new LinkedHashSet<>(Set.of("blackout"));

    /** Entity type IDs, e.g. minecraft:cow. */
    public Set<String> entityTypes = new LinkedHashSet<>();

    /** Block type IDs, e.g. minecraft:diamond_block. */
    public Set<String> blockTypes = new LinkedHashSet<>();

    public List<ManualEntity> manualEntities = new ArrayList<>();
    public List<ManualBlock> manualBlocks = new ArrayList<>();

    public Map<String, Style> tagStyles = new LinkedHashMap<>();
    public Map<String, Style> entityTypeStyles = new LinkedHashMap<>();
    public Map<String, Style> blockTypeStyles = new LinkedHashMap<>();
    public Map<String, Style> manualEntityStyles = new LinkedHashMap<>();
    public Map<String, Style> manualBlockStyles = new LinkedHashMap<>();

    public static final class DirectionMarker {
        public String id = UUID.randomUUID().toString();
        public float yaw = 0.0f;
        public Style style = new Style();

        public DirectionMarker() {
        }

        public DirectionMarker(float yaw) {
            this.yaw = yaw;
        }
    }

    public static final class ManualEntity {
        public String dimension = "minecraft:overworld";
        public String uuid = "";
        public String label = "Entity";

        public ManualEntity() {
        }

        public ManualEntity(String dimension, String uuid, String label) {
            this.dimension = dimension;
            this.uuid = uuid;
            this.label = label;
        }
    }

    public static final class ManualBlock {
        public String dimension = "minecraft:overworld";
        public int x;
        public int y;
        public int z;
        public String label = "Block";

        public ManualBlock() {
        }

        public ManualBlock(String dimension, int x, int y, int z, String label) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
            this.label = label;
        }
    }

    public void sanitize() {
        squareHalfSize = clamp(squareHalfSize, 8, 2048);
        blockScanRadius = clamp(blockScanRadius, 8, 96);
        maxTargets = clamp(maxTargets, 1, 256);
        maxBlockTargets = clamp(maxBlockTargets, 1, 512);

        if (entityVisibility == null) entityVisibility = EntityVisibility.HITBOX;
        if (blockVisibility == null) blockVisibility = BlockVisibility.PART;
        if (hideWhenOccluded == null) hideWhenOccluded = Boolean.TRUE;
        directionMarkerYaw = ((directionMarkerYaw % 360.0f) + 360.0f) % 360.0f;

        if (tags == null) tags = new LinkedHashSet<>();
        if (entityTypes == null) entityTypes = new LinkedHashSet<>();
        if (blockTypes == null) blockTypes = new LinkedHashSet<>();
        if (manualEntities == null) manualEntities = new ArrayList<>();
        if (manualBlocks == null) manualBlocks = new ArrayList<>();
        if (directionMarkers == null) directionMarkers = new ArrayList<>();
        if (sunStyle == null) sunStyle = new Style();
        if (moonStyle == null) moonStyle = new Style();
        if (tagStyles == null) tagStyles = new LinkedHashMap<>();
        if (entityTypeStyles == null) entityTypeStyles = new LinkedHashMap<>();
        if (blockTypeStyles == null) blockTypeStyles = new LinkedHashMap<>();
        if (manualEntityStyles == null) manualEntityStyles = new LinkedHashMap<>();
        if (manualBlockStyles == null) manualBlockStyles = new LinkedHashMap<>();

        // One-time migration from the old single-direction-marker fields.
        if (directionMarkers.isEmpty() && directionMarkerEnabled) {
            directionMarkers.add(new DirectionMarker(directionMarkerYaw));
        }
        directionMarkerEnabled = false;

        if (directionMarkers.size() > 32) {
            directionMarkers = new ArrayList<>(directionMarkers.subList(0, 32));
        }
        directionMarkers.removeIf(marker -> marker == null);
        for (DirectionMarker marker : directionMarkers) {
            if (marker.id == null || marker.id.isBlank()) {
                marker.id = UUID.randomUUID().toString();
            }
            marker.yaw = ((marker.yaw % 360.0f) + 360.0f) % 360.0f;
            if (marker.style == null) {
                marker.style = new Style();
            }
            sanitizeStyle(marker.style);
        }
        sanitizeStyle(sunStyle);
        sanitizeStyle(moonStyle);

        tags.removeIf(s -> s == null || s.isBlank());
        entityTypes.removeIf(s -> s == null || s.isBlank());
        blockTypes.removeIf(s -> s == null || s.isBlank());
        manualEntities.removeIf(e -> e == null || e.uuid == null || e.uuid.isBlank()
                || e.dimension == null || e.dimension.isBlank());
        manualBlocks.removeIf(b -> b == null || b.dimension == null || b.dimension.isBlank());

        sanitizeStyles(tagStyles);
        sanitizeStyles(entityTypeStyles);
        sanitizeStyles(blockTypeStyles);
        sanitizeStyles(manualEntityStyles);
        sanitizeStyles(manualBlockStyles);
    }

    public Map<String, Style> styles(StyleKind kind) {
        return switch (kind) {
            case TAG -> tagStyles;
            case ENTITY_TYPE -> entityTypeStyles;
            case BLOCK_TYPE -> blockTypeStyles;
            case MANUAL_ENTITY -> manualEntityStyles;
            case MANUAL_BLOCK -> manualBlockStyles;
        };
    }

    private static void sanitizeStyles(Map<String, Style> styles) {
        styles.entrySet().removeIf(entry -> {
            String key = entry.getKey();
            Style style = entry.getValue();
            if (key == null || key.isBlank() || style == null) {
                return true;
            }
            sanitizeStyle(style);
            return style.isEmpty();
        });
    }

    private static void sanitizeStyle(Style style) {
        if (style.squareSize != null) {
            if (style.squareSize <= 0) {
                style.squareSize = null;
            } else {
                style.squareSize = clamp(style.squareSize, 16, 4096);
            }
        }
        if (style.visibility == null) {
            style.visibility = StyleVisibility.INHERIT;
        }
        if (style.occlusion == null) {
            style.occlusion = StyleOcclusion.INHERIT;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
