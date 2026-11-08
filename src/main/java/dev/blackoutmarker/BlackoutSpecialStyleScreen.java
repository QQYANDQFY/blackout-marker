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

/**
 * Small style editor for direction/sun/moon markers. Only the square size is
 * configurable here; these markers are always visible and do not use the
 * entity/block occlusion modes.
 */
public final class BlackoutSpecialStyleScreen extends Screen {
    private final BlackoutConfigScreen parent;
    private final String titleText;
    private final BlackoutConfig.Style style;

    private EditBox sizeInput;

    public BlackoutSpecialStyleScreen(BlackoutConfigScreen parent,
                                      String titleText,
                                      BlackoutConfig.Style style) {
        super(Component.literal("特殊样式"));
        this.parent = parent;
        this.titleText = titleText;
        this.style = style;
    }

    @Override
    protected void init() {
        clearWidgets();
        BlackoutConfig config = BlackoutMarkerMod.config();

        sizeInput = new EditBox(font, width / 2 - 110, 80, 220, 20, Component.literal("尺寸"));
        sizeInput.setMaxLength(4);
        sizeInput.setValue(style.squareSize != null ? String.valueOf(style.squareSize) : "");
        sizeInput.setHint(Component.literal("留空 = 全局 " + (config.squareHalfSize * 2) + " px"));
        addRenderableWidget(sizeInput);

        addRenderableWidget(Button.builder(Component.literal("保存"), b -> save())
                .bounds(width / 2 - 110, 116, 104, 20)
                .build());
        addRenderableWidget(Button.builder(Component.literal("取消"), b -> onClose())
                .bounds(width / 2 + 6, 116, 104, 20)
                .build());
    }

    private void save() {
        String raw = sizeInput.getValue().trim();
        if (raw.isEmpty()) {
            style.squareSize = null;
        } else {
            try {
                style.squareSize = Math.max(16, Math.min(4096, Integer.parseInt(raw)));
            } catch (NumberFormatException e) {
                style.squareSize = null;
            }
        }
        BlackoutMarkerMod.saveConfig();
        parent.rebuildAfterStyleChange();
        minecraft.setScreenAndShow(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xD8101010);
        graphics.nextStratum();
        graphics.centeredText(font, titleText, width / 2, 30, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("黑块尺寸 (像素，留空继承全局)"),
                width / 2, 64, 0xFFA0A0A0);
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
