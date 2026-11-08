/*
 * Blackout Marker
 * Copyright (C) 2026 Blackout Marker contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package dev.blackoutmarker;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Editor for a single target's optional size/visibility override. Leaving the
 * size field empty, or selecting "inherit", falls back to the global settings.
 */
public final class BlackoutStyleScreen extends Screen {
    private final BlackoutConfigScreen parent;
    private final BlackoutConfig.StyleKind kind;
    private final String key;
    private final String targetLabel;

    private EditBox sizeInput;
    private Button modeButton;
    private int modeIndex;
    private int occlusionIndex;

    public BlackoutStyleScreen(BlackoutConfigScreen parent,
                               BlackoutConfig.StyleKind kind,
                               String key,
                               String targetLabel) {
        super(Component.literal("样式"));
        this.parent = parent;
        this.kind = kind;
        this.key = key;
        this.targetLabel = targetLabel;
        BlackoutConfig.Style existing = BlackoutMarkerMod.config().styles(kind).get(key);
        if (existing != null) {
            this.modeIndex = switch (existing.visibility) {
                case ANY -> 1;
                case CENTER -> 2;
                default -> 0;
            };
            this.occlusionIndex = switch (existing.occlusion) {
                case HIDE -> 1;
                case SHOW -> 2;
                default -> 0;
            };
        }
    }

    @Override
    protected void init() {
        clearWidgets();

        BlackoutConfig config = BlackoutMarkerMod.config();
        BlackoutConfig.Style existing = config.styles(kind).get(key);

        sizeInput = new EditBox(font, width / 2 - 110, 74, 220, 20, Component.literal("尺寸"));
        sizeInput.setMaxLength(4);
        sizeInput.setValue(existing != null && existing.squareSize != null
                ? String.valueOf(existing.squareSize) : "");
        sizeInput.setHint(Component.literal("留空 = 全局 " + (config.squareHalfSize * 2) + " px"));
        addRenderableWidget(sizeInput);

        modeButton = Button.builder(Component.literal(modeLabel()), b -> {
                    modeIndex = (modeIndex + 1) % 3;
                    b.setMessage(Component.literal(modeLabel()));
                })
                .bounds(width / 2 - 110, 102, 220, 20)
                .build();
        addRenderableWidget(modeButton);

        addRenderableWidget(Button.builder(Component.literal(occlusionLabel()), b -> {
                    occlusionIndex = (occlusionIndex + 1) % 3;
                    b.setMessage(Component.literal(occlusionLabel()));
                })
                .bounds(width / 2 - 110, 128, 220, 20)
                .build());

        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(width / 2 - 110, 156, 104, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(width / 2 + 6, 156, 104, 20)
                .build());
    }

    private String occlusionLabel() {
        return switch (occlusionIndex) {
            case 1 -> "遮挡: 被挡住时隐藏";
            case 2 -> "遮挡: 被挡住时仍显示";
            default -> "遮挡: 继承全局 (" + (BlackoutMarkerMod.config().hideWhenOccluded ? "隐藏" : "显示") + ")";
        };
    }

    private String modeLabel() {
        return switch (modeIndex) {
            case 1 -> isBlock() ? "模式: 看到任意部分" : "模式: 看到碰撞箱任意部分";
            case 2 -> "模式: 看到中心";
            default -> "模式: 继承全局 (" + (isBlock()
                    ? BlackoutMarkerMod.config().blockVisibility == BlackoutConfig.BlockVisibility.CENTER
                            ? "看到中心" : "看到部分"
                    : BlackoutMarkerMod.config().entityVisibility == BlackoutConfig.EntityVisibility.CENTER
                            ? "看到中心" : "看到碰撞箱") + ")";
        };
    }

    private boolean isBlock() {
        return kind == BlackoutConfig.StyleKind.BLOCK_TYPE || kind == BlackoutConfig.StyleKind.MANUAL_BLOCK;
    }

    private void save() {
        BlackoutConfig config = BlackoutMarkerMod.config();
        Integer size = null;
        String raw = sizeInput.getValue().trim();
        if (!raw.isEmpty()) {
            try {
                int pixels = Integer.parseInt(raw);
                if (pixels > 0) {
                    size = Math.max(16, Math.min(4096, pixels));
                }
            } catch (NumberFormatException ignored) {
                size = null;
            }
        }

        BlackoutConfig.StyleVisibility visibility = switch (modeIndex) {
            case 1 -> BlackoutConfig.StyleVisibility.ANY;
            case 2 -> BlackoutConfig.StyleVisibility.CENTER;
            default -> BlackoutConfig.StyleVisibility.INHERIT;
        };
        BlackoutConfig.StyleOcclusion occlusion = switch (occlusionIndex) {
            case 1 -> BlackoutConfig.StyleOcclusion.HIDE;
            case 2 -> BlackoutConfig.StyleOcclusion.SHOW;
            default -> BlackoutConfig.StyleOcclusion.INHERIT;
        };

        BlackoutConfig.Style style = new BlackoutConfig.Style(size, visibility, occlusion);
        Map<String, BlackoutConfig.Style> styles = config.styles(kind);
        if (style.isEmpty()) {
            styles.remove(key);
        } else {
            styles.put(key, style);
        }

        BlackoutMarkerMod.saveConfig();
        BlackoutMarkerMod.targetManager().markDirty();
        parent.rebuildAfterStyleChange();
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD8101010);
        graphics.nextStratum();
        graphics.centeredText(font, Component.literal("Blackout Marker — 目标样式"), width / 2, 18, 0xFFFFFFFF);
        graphics.centeredText(font, targetLabel, width / 2, 38, 0xFFA0A0A0);
        graphics.text(font, "黑块尺寸 (像素，留空继承全局)", width / 2 - 110, 62, 0xFFA0A0A0);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreenAndShow(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
