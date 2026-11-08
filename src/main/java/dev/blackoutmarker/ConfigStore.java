/*
 * Blackout Marker
 * Copyright (C) 2026 Blackout Marker contributors
 * SPDX-License-Identifier: LGPL-3.0-only
 */
package dev.blackoutmarker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private final Path legacyPath;
    private BlackoutConfig config = new BlackoutConfig();

    public ConfigStore() {
        Path configDir = FabricLoader.getInstance().getConfigDir();
        this.path = configDir.resolve("blackout-marker.json");
        this.legacyPath = configDir.resolve("blackoutmarker.json");
    }

    public synchronized BlackoutConfig get() {
        return config;
    }

    public synchronized void load() {
        Path source = Files.isRegularFile(path) ? path
                : (Files.isRegularFile(legacyPath) ? legacyPath : null);
        if (source == null) {
            config = new BlackoutConfig();
            save();
            return;
        }

        try {
            String json = Files.readString(source, StandardCharsets.UTF_8);
            BlackoutConfig loaded = GSON.fromJson(json, BlackoutConfig.class);
            config = loaded == null ? new BlackoutConfig() : loaded;
            config.sanitize();
            if (source.equals(legacyPath)) {
                save();
            }
        } catch (Exception e) {
            BlackoutMarkerMod.LOGGER.warn("Could not read {}; using defaults", source, e);
            try {
                Files.move(source, source.resolveSibling(source.getFileName() + ".broken"),
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            config = new BlackoutConfig();
            save();
        }
    }

    public synchronized void save() {
        config.sanitize();
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling("blackout-marker.json.tmp");
            Files.writeString(tmp, GSON.toJson(config), StandardCharsets.UTF_8);
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            BlackoutMarkerMod.LOGGER.warn("Could not write {}", path, e);
        }
    }
}
