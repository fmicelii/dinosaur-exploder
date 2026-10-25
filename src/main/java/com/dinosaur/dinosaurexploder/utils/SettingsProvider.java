/*
 * SPDX-FileCopyrightText: 2026 jvondermarck
 * SPDX-License-Identifier: MIT
 */

package com.dinosaur.dinosaurexploder.utils;

import com.dinosaur.dinosaurexploder.model.ControlAction;
import com.dinosaur.dinosaurexploder.model.KeyBindings;
import com.dinosaur.dinosaurexploder.model.Settings;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.scene.input.KeyCode;

public class SettingsProvider {

  public static final String SETTINGS_FILE = "settings.properties";
  public static final String SETTING_VOLUME = "soundVolume";
  public static final String SETTINGS_MUTED = "soundMuted";
  public static final String SETTING_VOLUME_SFX = "soundVolumeSfx";
  public static final String SETTINGS_MUTED_SFX = "soundMutedSfx";
  public static final String SETTINGS_LANGUAGE = "selectedLanguage";
  private static final Logger logger = Logger.getLogger(SettingsProvider.class.getName());

  private SettingsProvider() {}

  public static Settings loadSettings() {
    Properties properties = readSettingsFile();
    if (!properties.isEmpty()) {
      try {
        return createSettingsFromProperties(properties);
      } catch (Exception e) {
        logger.log(Level.INFO, "Malformed settings, restoring defaults: {0}", e.getMessage());
        deleteSettingsFile();
      }
    }
    return saveDefaultSettings();
  }

  public static void saveSettings(Settings settings) {
    Properties properties = readSettingsFile();
    properties.putAll(createPropertiesFromSettings(settings));

    try {
      writeSettingsFile(properties);
    } catch (IOException ex) {
      logger.log(Level.INFO, "Error saving settings {0}", ex.getMessage());
    }
  }

  public static KeyBindings loadKeyBindings() {
    Properties properties = readSettingsFile();
    Map<ControlAction, KeyCode> keys = new EnumMap<>(ControlAction.class);
    try {
      for (ControlAction action : ControlAction.values()) {
        keys.put(
            action,
            KeyCode.valueOf(
                properties.getProperty(keyProperty(action), action.defaultKey().name())));
      }
      return new KeyBindings(keys);
    } catch (IllegalArgumentException ex) {
      logger.log(Level.WARNING, "Invalid key bindings, using defaults", ex);
      return KeyBindings.defaults();
    }
  }

  public static void saveKeyBindings(KeyBindings bindings) throws IOException {
    Properties properties = createPropertiesFromSettings(generateDefaultSettings());
    properties.putAll(readSettingsFileStrict());
    for (ControlAction action : ControlAction.values()) {
      properties.setProperty(keyProperty(action), bindings.keyFor(action).name());
    }
    writeSettingsFile(properties);
  }

  private static String keyProperty(ControlAction action) {
    return "controls." + action.translationKey();
  }

  public static void updateMuteState(boolean muted) {
    Settings settings = loadSettings();
    settings.setMuted(muted);
    saveSettings(settings);
  }

  private static Properties readSettingsFile() {
    try {
      return readSettingsFileStrict();
    } catch (IOException | IllegalArgumentException ex) {
      logger.log(Level.INFO, "Error reading settings {0}", ex.getMessage());
      return new Properties();
    }
  }

  private static Properties readSettingsFileStrict() throws IOException {
    Properties properties = new Properties();
    if (!Files.exists(Path.of(SETTINGS_FILE))) {
      return properties;
    }
    try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
      properties.load(in);
    }
    return properties;
  }

  private static void writeSettingsFile(Properties properties) throws IOException {
    Path target = Path.of(SETTINGS_FILE).toAbsolutePath();
    Path temporary = Files.createTempFile(target.getParent(), "settings-", ".tmp");
    try {
      try (FileWriter writer = new FileWriter(temporary.toFile())) {
        properties.store(writer, "store properties");
      }
      Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  private static void deleteSettingsFile() {
    File file = new File(SETTINGS_FILE);
    if (file.exists() && !file.delete()) {
      logger.log(Level.INFO, "Could not delete invalid settings file");
    }
  }

  private static Settings saveDefaultSettings() {
    Settings defaults = generateDefaultSettings();
    saveSettings(defaults);
    return defaults;
  }

  private static Settings createSettingsFromProperties(Properties props) {
    Settings settings = new Settings();
    settings.setVolume(Double.parseDouble(props.getProperty(SETTING_VOLUME)));
    settings.setMuted(Boolean.parseBoolean(props.getProperty(SETTINGS_MUTED)));
    settings.setSfxVolume(Double.parseDouble(props.getProperty(SETTING_VOLUME_SFX)));
    settings.setSfxMuted(Boolean.parseBoolean(props.getProperty(SETTINGS_MUTED_SFX)));
    settings.setLanguage(props.getProperty(SETTINGS_LANGUAGE));

    return settings;
  }

  private static Properties createPropertiesFromSettings(Settings settings) {
    Properties properties = new Properties();
    properties.put(SETTING_VOLUME, String.valueOf(settings.getVolume()));
    properties.put(SETTINGS_MUTED, String.valueOf(settings.isMuted()));
    properties.put(SETTING_VOLUME_SFX, String.valueOf(settings.getSfxVolume()));
    properties.put(SETTINGS_MUTED_SFX, String.valueOf(settings.isSfxMuted()));
    properties.put(SETTINGS_LANGUAGE, settings.getLanguage());

    return properties;
  }

  private static Settings generateDefaultSettings() {
    Settings defaultSettings = new Settings();
    defaultSettings.setVolume(1.0);
    defaultSettings.setMuted(false);
    defaultSettings.setSfxVolume(1.0);
    defaultSettings.setSfxMuted(false);
    defaultSettings.setLanguage("English");

    return defaultSettings;
  }
}
