/*
 * Blackout Marker
 * Copyright (C) 2026 Blackout Marker contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package dev.blackoutmarker.compat;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.blackoutmarker.BlackoutConfigScreen;

/**
 * Optional Mod Menu integration. This class is only loaded when Mod Menu is
 * installed; Blackout Marker does not depend on it.
 */
public final class BlackoutModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return BlackoutConfigScreen::new;
    }
}
