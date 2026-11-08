/*
 * Blackout Marker
 * Copyright (C) 2026 Blackout Marker contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package dev.blackoutmarker;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * Small dependency-free configuration screen, written in the vanilla visual
 * style. It intentionally uses a list plus a text field rather than adding
 * Cloth Config / YACL as hard dependencies.
 */
public final class BlackoutConfigScreen extends Screen {
    private enum Tab {
        GENERAL("常规"),
        TAGS("标签"),
        ENTITY_TYPES("实体类型"),
        BLOCK_TYPES("方块类型"),
        MANUAL("手动目标"),
        DIRECTION("方向"),
        SPECIAL("特殊");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private final Screen parent;
    private Tab tab = Tab.GENERAL;
    private EntryList list;
    private EditBox input;
    private EditBox sizeInput;
    private Button addButton;
    private Button removeButton;
    private Button applySizeButton;
    private Button styleButton;
    private Button enabledButton;
    private Button entityModeButton;
    private Button blockModeButton;
    private Button scanRadiusButton;
    private Button occlusionButton;
    private Button addDirectionButton;
    private Button sunButton;
    private Button moonButton;
    private Button sunStyleButton;
    private Button moonStyleButton;

    public BlackoutConfigScreen(Screen parent) {
        super(Component.literal("Blackout Marker"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();

        int margin = 10;
        int tabWidth = Math.max(40, (width - margin * 2) / Tab.values().length);
        int x = margin;
        for (Tab value : Tab.values()) {
            Tab target = value;
            Button button = Button.builder(
                            Component.literal(value.label),
                            b -> {
                                tab = target;
                                rebuild();
                            })
                    .bounds(x, 24, tabWidth - 2, 18)
                    .build();
            button.active = tab != value;
            addRenderableWidget(button);
            x += tabWidth;
        }

        boolean editable = tab == Tab.TAGS || tab == Tab.ENTITY_TYPES || tab == Tab.BLOCK_TYPES;
        boolean general = tab == Tab.GENERAL;
        boolean direction = tab == Tab.DIRECTION;
        boolean special = tab == Tab.SPECIAL;
        int listTop = direction ? 76 : 48;
        int listHeight = Math.max(30, height - (editable ? 116 : (general ? 128 : (direction ? 128 : 96))));
        list = new EntryList(minecraft, width, listHeight, listTop, 20);
        list.visible = !general && !special;
        addRenderableWidget(list);

        int generalRow = 52;
        int generalWidth = Math.max(70, (width - margin * 2 - 8) / 3);
        enabledButton = Button.builder(Component.literal(enabledLabel()), b -> {
                    BlackoutConfig config = BlackoutMarkerMod.config();
                    config.enabled = !config.enabled;
                    BlackoutMarkerMod.saveConfig();
                    updateGeneralButtons();
                })
                .bounds(margin, generalRow, generalWidth, 18)
                .build();
        addRenderableWidget(enabledButton);

        entityModeButton = Button.builder(Component.literal(entityModeButtonLabel()), b -> {
                    BlackoutConfig config = BlackoutMarkerMod.config();
                    config.entityVisibility = nextEntityMode(config.entityVisibility);
                    BlackoutMarkerMod.saveConfig();
                    updateGeneralButtons();
                })
                .bounds(margin + generalWidth + 4, generalRow, generalWidth, 18)
                .build();
        addRenderableWidget(entityModeButton);

        blockModeButton = Button.builder(Component.literal(blockModeButtonLabel()), b -> {
                    BlackoutConfig config = BlackoutMarkerMod.config();
                    config.blockVisibility = nextBlockMode(config.blockVisibility);
                    BlackoutMarkerMod.saveConfig();
                    updateGeneralButtons();
                })
                .bounds(margin + (generalWidth + 4) * 2, generalRow, generalWidth, 18)
                .build();
        addRenderableWidget(blockModeButton);

        occlusionButton = Button.builder(Component.literal(occlusionLabel()), b -> {
                    BlackoutConfig config = BlackoutMarkerMod.config();
                    config.hideWhenOccluded = !config.hideWhenOccluded;
                    BlackoutMarkerMod.saveConfig();
                    updateGeneralButtons();
                })
                .bounds(margin + 134, generalRow + 24, 130, 18)
                .build();
        addRenderableWidget(occlusionButton);

        scanRadiusButton = Button.builder(Component.literal(scanRadiusLabel()), b -> {
                    BlackoutConfig config = BlackoutMarkerMod.config();
                    config.blockScanRadius = nextRadius(config.blockScanRadius);
                    BlackoutMarkerMod.saveConfig();
                    BlackoutMarkerMod.targetManager().markDirty();
                    updateGeneralButtons();
                })
                .bounds(margin, generalRow + 24, 130, 18)
                .build();
        addRenderableWidget(scanRadiusButton);

        addDirectionButton = Button.builder(Component.literal(addDirectionLabel()), b -> addDirectionMarker())
                .bounds(margin, generalRow, width - margin * 2, 18)
                .build();
        addRenderableWidget(addDirectionButton);

        int halfButton = Math.max(60, (width - margin * 2 - 4) / 2);
        sunButton = Button.builder(Component.literal(sunLabel()), b -> {
                    BlackoutConfig config = BlackoutMarkerMod.config();
                    config.sunMarkerEnabled = !config.sunMarkerEnabled;
                    BlackoutMarkerMod.saveConfig();
                    updateSpecialButtons();
                })
                .bounds(margin, generalRow, halfButton, 18)
                .build();
        addRenderableWidget(sunButton);

        moonButton = Button.builder(Component.literal(moonLabel()), b -> {
                    BlackoutConfig config = BlackoutMarkerMod.config();
                    config.moonMarkerEnabled = !config.moonMarkerEnabled;
                    BlackoutMarkerMod.saveConfig();
                    updateSpecialButtons();
                })
                .bounds(margin + halfButton + 4, generalRow, halfButton, 18)
                .build();
        addRenderableWidget(moonButton);

        sunStyleButton = Button.builder(Component.literal(sunStyleLabel()), b ->
                        openSpecialStyle("太阳黑块样式", BlackoutMarkerMod.config().sunStyle))
                .bounds(margin, generalRow + 24, halfButton, 18)
                .build();
        addRenderableWidget(sunStyleButton);

        moonStyleButton = Button.builder(Component.literal(moonStyleLabel()), b ->
                        openSpecialStyle("月亮黑块样式", BlackoutMarkerMod.config().moonStyle))
                .bounds(margin + halfButton + 4, generalRow + 24, halfButton, 18)
                .build();
        addRenderableWidget(moonStyleButton);

        int inputY = height - 50;
        int fieldWidth = Math.min(190, width - 110);
        input = new EditBox(font, margin, inputY, fieldWidth, 18, Component.literal("添加"));
        input.setMaxLength(120);
        input.setHint(Component.literal(hintForTab()));
        addRenderableWidget(input);

        addButton = Button.builder(Component.literal("添加"), b -> addCurrent())
                .bounds(margin + fieldWidth + 4, inputY, 64, 18)
                .build();
        addRenderableWidget(addButton);

        sizeInput = new EditBox(font, margin, inputY, 80, 18, Component.literal("尺寸"));
        sizeInput.setMaxLength(4);
        sizeInput.setValue(String.valueOf(BlackoutMarkerMod.config().squareHalfSize * 2));
        sizeInput.setHint(Component.literal("像素"));
        addRenderableWidget(sizeInput);

        applySizeButton = Button.builder(Component.literal("应用尺寸"), b -> applySize())
                .bounds(margin + 84, inputY, 70, 18)
                .build();
        addRenderableWidget(applySizeButton);

        int bottom = height - 26;
        removeButton = Button.builder(Component.literal("移除选中"), b -> removeSelected())
                .bounds(margin, bottom, 80, 18)
                .build();
        addRenderableWidget(removeButton);

        styleButton = Button.builder(Component.literal("样式"), b -> openStyleEditor())
                .bounds(margin + 84, bottom, 64, 18)
                .build();
        addRenderableWidget(styleButton);

        addRenderableWidget(Button.builder(Component.literal("完成"), b -> onClose())
                .bounds(width - margin - 64, bottom, 64, 18)
                .build());

        input.visible = editable;
        addButton.visible = editable;
        sizeInput.visible = general;
        applySizeButton.visible = general;
        enabledButton.visible = general;
        entityModeButton.visible = general;
        blockModeButton.visible = general;
        scanRadiusButton.visible = general;
        occlusionButton.visible = general;
        addDirectionButton.visible = direction;
        sunButton.visible = special;
        moonButton.visible = special;
        sunStyleButton.visible = special;
        moonStyleButton.visible = special;
        removeButton.visible = !general && !special;
        styleButton.visible = !general && !special;

        updateSpecialButtons();
        rebuildList();
    }

    private void updateSpecialButtons() {
        if (addDirectionButton != null) addDirectionButton.setMessage(Component.literal(addDirectionLabel()));
        if (sunButton != null) sunButton.setMessage(Component.literal(sunLabel()));
        if (moonButton != null) moonButton.setMessage(Component.literal(moonLabel()));
        if (sunStyleButton != null) sunStyleButton.setMessage(Component.literal(sunStyleLabel()));
        if (moonStyleButton != null) moonStyleButton.setMessage(Component.literal(moonStyleLabel()));
    }

    private static String addDirectionLabel() {
        if (Minecraft.getInstance().player == null) {
            return "添加当前朝向";
        }
        float yaw = ((Minecraft.getInstance().player.getYRot() % 360.0f) + 360.0f) % 360.0f;
        return "添加当前朝向 (" + Math.round(yaw) + "°)";
    }

    private static String sunLabel() {
        return "太阳黑块: " + (BlackoutMarkerMod.config().sunMarkerEnabled ? "开" : "关");
    }

    private static String moonLabel() {
        return "月亮黑块: " + (BlackoutMarkerMod.config().moonMarkerEnabled ? "开" : "关");
    }

    private static String sunStyleLabel() {
        return "太阳样式 " + specialStyleSummary(BlackoutMarkerMod.config().sunStyle);
    }

    private static String moonStyleLabel() {
        return "月亮样式 " + specialStyleSummary(BlackoutMarkerMod.config().moonStyle);
    }

    private static String specialStyleSummary(BlackoutConfig.Style style) {
        return style != null && style.squareSize != null
                ? "[" + style.squareSize + " px]"
                : "[全局]";
    }

    private void addDirectionMarker() {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        BlackoutConfig config = BlackoutMarkerMod.config();
        if (config.directionMarkers.size() >= 32) {
            return;
        }
        float yaw = ((minecraft.player.getYRot() % 360.0f) + 360.0f) % 360.0f;
        config.directionMarkers.add(new BlackoutConfig.DirectionMarker(yaw));
        BlackoutMarkerMod.saveConfig();
        rebuildList();
    }

    private void openSpecialStyle(String title, BlackoutConfig.Style style) {
        if (style != null) {
            minecraft.setScreenAndShow(new BlackoutSpecialStyleScreen(this, title, style));
        }
    }

    private void updateGeneralButtons() {
        if (enabledButton != null) {
            enabledButton.setMessage(Component.literal(enabledLabel()));
        }
        if (entityModeButton != null) {
            entityModeButton.setMessage(Component.literal(entityModeButtonLabel()));
        }
        if (blockModeButton != null) {
            blockModeButton.setMessage(Component.literal(blockModeButtonLabel()));
        }
        if (scanRadiusButton != null) {
            scanRadiusButton.setMessage(Component.literal(scanRadiusLabel()));
        }
        if (occlusionButton != null) {
            occlusionButton.setMessage(Component.literal(occlusionLabel()));
        }
    }

    private static String enabledLabel() {
        return "启用: " + (BlackoutMarkerMod.config().enabled ? "开" : "关");
    }

    private static String entityModeButtonLabel() {
        return "实体: " + (BlackoutMarkerMod.config().entityVisibility == BlackoutConfig.EntityVisibility.CENTER
                ? "中心" : "碰撞箱");
    }

    private static String blockModeButtonLabel() {
        return "方块: " + (BlackoutMarkerMod.config().blockVisibility == BlackoutConfig.BlockVisibility.CENTER
                ? "中心" : "部分");
    }

    private static String scanRadiusLabel() {
        return "扫描半径: " + BlackoutMarkerMod.config().blockScanRadius;
    }

    private static String occlusionLabel() {
        return "遮挡: " + (BlackoutMarkerMod.config().hideWhenOccluded ? "隐藏" : "显示");
    }

    private String hintForTab() {
        return switch (tab) {
            case TAGS -> "计分板 tag，例如 blackout";
            case ENTITY_TYPES -> "实体类型，例如 minecraft:cow";
            case BLOCK_TYPES -> "方块类型，例如 minecraft:diamond_block";
            default -> "";
        };
    }

    private void rebuild() {
        init();
    }

    private void rebuildList() {
        if (list == null) {
            return;
        }
        list.clearEntriesPublic();
        BlackoutConfig config = BlackoutMarkerMod.config();

        switch (tab) {
            case GENERAL -> {
                // General uses native buttons so the mode controls work with
                // mouse clicks without relying on selection-list click events.
            }
            case DIRECTION -> {
                int i = 0;
                for (BlackoutConfig.DirectionMarker marker : config.directionMarkers) {
                    String label = "方向 " + (i + 1) + ": " + Math.round(marker.yaw) + "°"
                            + specialStyleSuffix(marker.style);
                    list.addConfigEntry(new ValueEntry(label, tab, marker.id));
                    i++;
                }
                if (i == 0) {
                    list.addConfigEntry(new ValueEntry("暂无方向标记；点击上方按钮添加。", null, ""));
                }
            }
            case SPECIAL -> {
                // Special markers use native buttons; there is no list.
            }
            case TAGS -> addValues(config.tags, BlackoutConfig.StyleKind.TAG);
            case ENTITY_TYPES -> addValues(config.entityTypes, BlackoutConfig.StyleKind.ENTITY_TYPE);
            case BLOCK_TYPES -> addValues(config.blockTypes, BlackoutConfig.StyleKind.BLOCK_TYPE);
            case MANUAL -> {
                int i = 0;
                for (BlackoutConfig.ManualEntity entity : config.manualEntities) {
                    String raw = "实体  " + entity.label + "  [" + entity.uuid.substring(0, Math.min(8, entity.uuid.length())) + "]";
                    list.addConfigEntry(new ValueEntry(raw + styleSuffix(BlackoutConfig.StyleKind.MANUAL_ENTITY, entity.uuid),
                            tab, entity.uuid));
                    i++;
                }
                for (BlackoutConfig.ManualBlock block : config.manualBlocks) {
                    String key = TargetManager.blockStyleKey(block.dimension, block.x, block.y, block.z);
                    String raw = "方块  " + block.label + "  @ " + block.x + "," + block.y + "," + block.z;
                    list.addConfigEntry(new ValueEntry(raw + styleSuffix(BlackoutConfig.StyleKind.MANUAL_BLOCK, key),
                            tab, "B:" + block.dimension + "|" + block.x + "|" + block.y + "|" + block.z));
                    i++;
                }
                if (i == 0) {
                    list.addConfigEntry(new ValueEntry("暂无手动目标。按 V 对着目标添加。", null, ""));
                }
            }
        }
    }

    private void addValues(Set<String> values, BlackoutConfig.StyleKind kind) {
        for (String value : values) {
            list.addConfigEntry(new ValueEntry(value + styleSuffix(kind, value), tab, value));
        }
        if (values.isEmpty()) {
            list.addConfigEntry(new ValueEntry("暂无条目", null, ""));
        }
    }

    private void addCurrent() {
        String raw = input.getValue().trim();
        if (raw.isEmpty()) {
            return;
        }

        BlackoutConfig config = BlackoutMarkerMod.config();
        boolean added = false;
        switch (tab) {
            case TAGS -> {
                if (raw.startsWith("#")) raw = raw.substring(1);
                if (raw.matches("[A-Za-z0-9_.\\-]+")) {
                    added = config.tags.add(raw);
                }
            }
            case ENTITY_TYPES -> {
                Identifier id = Identifier.tryParse(raw);
                if (id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                    added = config.entityTypes.add(id.toString());
                }
            }
            case BLOCK_TYPES -> {
                Identifier id = Identifier.tryParse(raw);
                if (id != null && BuiltInRegistries.BLOCK.containsKey(id)) {
                    added = config.blockTypes.add(id.toString());
                }
            }
            default -> {
            }
        }

        if (added) {
            input.setValue("");
            BlackoutMarkerMod.saveConfig();
            BlackoutMarkerMod.targetManager().markDirty();
            rebuildList();
        }
    }

    private void applySize() {
        if (sizeInput == null) {
            return;
        }
        BlackoutConfig config = BlackoutMarkerMod.config();
        try {
            int pixels = Integer.parseInt(sizeInput.getValue().trim());
            config.squareHalfSize = Math.max(8, Math.min(2048, pixels / 2));
            BlackoutMarkerMod.saveConfig();
            sizeInput.setValue(String.valueOf(config.squareHalfSize * 2));
            rebuildList();
        } catch (NumberFormatException e) {
            sizeInput.setValue(String.valueOf(config.squareHalfSize * 2));
        }
    }

    private static String specialStyleSuffix(BlackoutConfig.Style style) {
        return style != null && style.squareSize != null
                ? "  [" + style.squareSize + " px]"
                : "";
    }

    private String styleSuffix(BlackoutConfig.StyleKind kind, String key) {
        BlackoutConfig.Style style = BlackoutMarkerMod.config().styles(kind).get(key);
        if (style == null) {
            return "";
        }
        StringBuilder suffix = new StringBuilder("  [");
        boolean first = true;
        if (style.squareSize != null) {
            suffix.append(style.squareSize).append(" px");
            first = false;
        }
        if (style.visibility != null && style.visibility != BlackoutConfig.StyleVisibility.INHERIT) {
            if (!first) suffix.append(" / ");
            suffix.append(style.visibility == BlackoutConfig.StyleVisibility.CENTER ? "中心" : "任意部分");
            first = false;
        }
        if (style.occlusion != null && style.occlusion != BlackoutConfig.StyleOcclusion.INHERIT) {
            if (!first) suffix.append(" / ");
            suffix.append(style.occlusion == BlackoutConfig.StyleOcclusion.SHOW ? "遮挡显示" : "遮挡隐藏");
        }
        return suffix.append(']').toString();
    }

    private void openStyleEditor() {
        BaseEntry selected = list == null ? null : list.getSelected();
        if (!(selected instanceof ValueEntry entry) || entry.value.isEmpty() || entry.tab == null) {
            return;
        }

        if (entry.tab == Tab.DIRECTION) {
            for (BlackoutConfig.DirectionMarker marker : BlackoutMarkerMod.config().directionMarkers) {
                if (marker.id.equals(entry.value) && marker.style != null) {
                    minecraft.setScreenAndShow(new BlackoutSpecialStyleScreen(this, "方向标记样式", marker.style));
                    return;
                }
            }
            return;
        }

        BlackoutConfig.StyleKind kind;
        String key;
        switch (entry.tab) {
            case TAGS -> {
                kind = BlackoutConfig.StyleKind.TAG;
                key = entry.value;
            }
            case ENTITY_TYPES -> {
                kind = BlackoutConfig.StyleKind.ENTITY_TYPE;
                key = entry.value;
            }
            case BLOCK_TYPES -> {
                kind = BlackoutConfig.StyleKind.BLOCK_TYPE;
                key = entry.value;
            }
            case MANUAL -> {
                if (entry.value.startsWith("B:")) {
                    String[] parts = entry.value.split("\\|");
                    if (parts.length != 4) {
                        return;
                    }
                    try {
                        kind = BlackoutConfig.StyleKind.MANUAL_BLOCK;
                        key = TargetManager.blockStyleKey(parts[0].substring(2),
                                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                    } catch (NumberFormatException e) {
                        return;
                    }
                } else {
                    kind = BlackoutConfig.StyleKind.MANUAL_ENTITY;
                    key = entry.value;
                }
            }
            default -> {
                return;
            }
        }

        minecraft.setScreenAndShow(new BlackoutStyleScreen(this, kind, key, entry.label));
    }

    void rebuildAfterStyleChange() {
        rebuild();
    }

    private void removeSelected() {
        BaseEntry entry = list == null ? null : list.getSelected();
        if (!(entry instanceof ValueEntry value) || value.value.isEmpty() || value.tab == null) {
            return;
        }

        BlackoutConfig config = BlackoutMarkerMod.config();
        switch (value.tab) {
            case TAGS -> {
                config.tags.remove(value.value);
                config.tagStyles.remove(value.value);
            }
            case ENTITY_TYPES -> {
                config.entityTypes.remove(value.value);
                config.entityTypeStyles.remove(value.value);
            }
            case BLOCK_TYPES -> {
                config.blockTypes.remove(value.value);
                config.blockTypeStyles.remove(value.value);
            }
            case MANUAL -> {
                if (value.value.startsWith("B:")) {
                    String[] parts = value.value.split("\\|");
                    if (parts.length == 4) {
                        try {
                            String dimension = parts[0].substring(2);
                            int x = Integer.parseInt(parts[1]);
                            int y = Integer.parseInt(parts[2]);
                            int z = Integer.parseInt(parts[3]);
                            config.manualBlocks.removeIf(b ->
                                    b.x == x && b.y == y && b.z == z && b.dimension.equals(dimension));
                            config.manualBlockStyles.remove(TargetManager.blockStyleKey(dimension, x, y, z));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                } else {
                    config.manualEntities.removeIf(e -> e.uuid.equals(value.value));
                    config.manualEntityStyles.remove(value.value);
                }
            }
            case GENERAL -> {
            }
            case DIRECTION -> {
                config.directionMarkers.removeIf(marker -> marker.id.equals(value.value));
            }
            case SPECIAL -> {
            }
        }
        BlackoutMarkerMod.saveConfig();
        BlackoutMarkerMod.targetManager().markDirty();
        rebuildList();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xC8101010);
        graphics.nextStratum();
        graphics.centeredText(font, title, width / 2, 8, 0xFFFFFFFF);

        boolean hasInput = tab == Tab.TAGS || tab == Tab.ENTITY_TYPES || tab == Tab.BLOCK_TYPES;
        boolean general = tab == Tab.GENERAL;
        String status;
        if (tab == Tab.TAGS && !BlackoutMarkerMod.targetManager().isTagSourceAvailable()) {
            status = "§c远程服务器不同步 tag；仅单机/LAN 主机可用";
        } else if (tab == Tab.MANUAL) {
            status = "V 添加 / Shift+V 移除准星目标";
        } else if (tab == Tab.DIRECTION) {
            status = "点击上方按钮把当前朝向加入多个方向标记";
        } else if (tab == Tab.SPECIAL) {
            status = "太阳/月亮跟随当前天体位置；可分别设置样式";
        } else if (general) {
            status = "按钮切换模式/遮挡；尺寸在下方输入";
        } else {
            status = "输入后添加；选中条目可移除";
        }

        int statusY = height - (hasInput ? 62 : (general ? 72 : 42));
        graphics.text(font, status, 10, statusY, 0xFFA0A0A0);

        if (general) {
            graphics.text(font, "黑块尺寸 (像素)", 10, height - 62, 0xFFA0A0A0);
        }

        // Widgets (including the list) render after this background/title.
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        BlackoutMarkerMod.saveConfig();
        if (minecraft != null) {
            minecraft.setScreenAndShow(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static BlackoutConfig.EntityVisibility nextEntityMode(BlackoutConfig.EntityVisibility mode) {
        return mode == BlackoutConfig.EntityVisibility.HITBOX
                ? BlackoutConfig.EntityVisibility.CENTER
                : BlackoutConfig.EntityVisibility.HITBOX;
    }

    private static BlackoutConfig.BlockVisibility nextBlockMode(BlackoutConfig.BlockVisibility mode) {
        return mode == BlackoutConfig.BlockVisibility.PART
                ? BlackoutConfig.BlockVisibility.CENTER
                : BlackoutConfig.BlockVisibility.PART;
    }

    private static int nextRadius(int current) {
        int[] values = {16, 24, 32, 48, 64, 80};
        for (int value : values) {
            if (value > current) return value;
        }
        return values[0];
    }

    private abstract static class BaseEntry extends ObjectSelectionList.Entry<BaseEntry> {
        abstract String label();

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int index, int top, boolean hovered, float partialTick) {
            int color = hovered ? 0xFFFFC040 : 0xFFFFFFFF;
            graphics.text(Minecraft.getInstance().font, label(), getContentX(), getContentY() + 6, color);
        }

        @Override
        public Component getNarration() {
            return Component.literal(label());
        }
    }

    private final class ValueEntry extends BaseEntry {
        private final String label;
        private final Tab tab;
        private final String value;

        private ValueEntry(String label, Tab tab, String value) {
            this.label = label;
            this.tab = tab;
            this.value = value;
        }

        @Override
        String label() {
            return label;
        }
    }

    private final class EntryList extends ObjectSelectionList<BaseEntry> {
        private EntryList(Minecraft minecraft, int width, int height, int y, int itemHeight) {
            super(minecraft, width, height, y, itemHeight);
            this.centerListVertically = false;
        }

        @Override
        public int getRowWidth() {
            return Math.min(300, BlackoutConfigScreen.this.width - 20);
        }

        @Override
        protected int scrollBarX() {
            return getRowRight() + 6;
        }

        private void addConfigEntry(BaseEntry entry) {
            super.addEntry(entry);
        }

        public void clearEntriesPublic() {
            super.clearEntries();
        }
    }
}
