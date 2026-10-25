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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import javafx.scene.input.KeyCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SettingsProviderTest {

  private Path settingsBackup;

  @BeforeEach
  void backupSettingsFile() throws Exception {
    Path settingsFile = Path.of(SettingsProvider.SETTINGS_FILE);
    if (Files.exists(settingsFile)) {
      settingsBackup = Files.createTempFile("settings", ".properties");
      Files.copy(settingsFile, settingsBackup, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  @AfterEach
  void restoreSettingsFile() throws Exception {
    Path settingsFile = Path.of(SettingsProvider.SETTINGS_FILE);
    if (settingsBackup != null) {
      Files.copy(settingsBackup, settingsFile, StandardCopyOption.REPLACE_EXISTING);
      Files.deleteIfExists(settingsBackup);
    } else {
      Files.deleteIfExists(settingsFile);
    }
  }

  @Test
  public void testLoadSettings() {
    Settings settings = SettingsProvider.loadSettings();
    Assertions.assertNotNull(
        settings, "Settings should always be available. At least the default settings");
  }

  @Test
  public void loadSettings_returnsDefaultsWhenVolumeIsMalformed() throws Exception {
    try (FileWriter writer = new FileWriter(SettingsProvider.SETTINGS_FILE)) {
      writer.write("soundVolume=not-a-number\n");
      writer.write("soundMuted=false\n");
      writer.write("soundVolumeSfx=1.0\n");
      writer.write("soundMutedSfx=false\n");
      writer.write("selectedLanguage=English\n");
    }

    Settings settings = SettingsProvider.loadSettings();

    Assertions.assertNotNull(settings);
    Assertions.assertEquals(1.0, settings.getVolume());
    Assertions.assertFalse(settings.isMuted());
    Assertions.assertEquals(1.0, settings.getSfxVolume());
    Assertions.assertFalse(settings.isSfxMuted());
    Assertions.assertEquals("English", settings.getLanguage());

    File regenerated = new File(SettingsProvider.SETTINGS_FILE);
    Assertions.assertTrue(regenerated.exists());
    Properties properties = new Properties();
    try (FileInputStream in = new FileInputStream(regenerated)) {
      properties.load(in);
    }
    Assertions.assertEquals("1.0", properties.getProperty(SettingsProvider.SETTING_VOLUME));
  }

  @Test
  void updateMuteStatePreservesLatestLanguage() {
    Settings initialSettings = new Settings();
    initialSettings.setVolume(1.0);
    initialSettings.setMuted(false);
    initialSettings.setSfxVolume(1.0);
    initialSettings.setSfxMuted(false);
    initialSettings.setLanguage("Japanese");
    SettingsProvider.saveSettings(initialSettings);

    Settings olderSettings = SettingsProvider.loadSettings();

    Settings latestSettings = SettingsProvider.loadSettings();
    latestSettings.setLanguage("English");
    SettingsProvider.saveSettings(latestSettings);

    SettingsProvider.updateMuteState(true);

    Settings savedSettings = SettingsProvider.loadSettings();
    Assertions.assertEquals("English", savedSettings.getLanguage());
    Assertions.assertTrue(savedSettings.isMuted());
    Assertions.assertEquals(1.0, savedSettings.getVolume());
    Assertions.assertEquals(1.0, savedSettings.getSfxVolume());
    Assertions.assertFalse(savedSettings.isSfxMuted());
    Assertions.assertEquals("Japanese", olderSettings.getLanguage());
  }

  @Test
  void customKeysSurviveSavingOlderAudioSettings() throws Exception {
    Settings olderSettings = SettingsProvider.loadSettings();
    SettingsProvider.saveKeyBindings(
        KeyBindings.defaults().withKey(ControlAction.SHOOT, KeyCode.J));
    olderSettings.setVolume(0.4);
    SettingsProvider.saveSettings(olderSettings);

    Assertions.assertEquals(
        KeyCode.J, SettingsProvider.loadKeyBindings().keyFor(ControlAction.SHOOT));
    Assertions.assertEquals(0.4, SettingsProvider.loadSettings().getVolume());
  }

  @Test
  void savingKeysPreservesLatestAudioAndLanguage() throws Exception {
    Settings settings = SettingsProvider.loadSettings();
    settings.setVolume(0.3);
    settings.setSfxVolume(0.6);
    settings.setMuted(true);
    settings.setSfxMuted(true);
    settings.setLanguage("Spanish");
    SettingsProvider.saveSettings(settings);

    KeyBindings custom =
        new KeyBindings(
            java.util.Map.of(
                ControlAction.SHOOT, KeyCode.J,
                ControlAction.BOMB, KeyCode.K,
                ControlAction.SHIELD, KeyCode.L));
    SettingsProvider.saveKeyBindings(custom);

    for (ControlAction action : ControlAction.values()) {
      Assertions.assertEquals(
          custom.keyFor(action), SettingsProvider.loadKeyBindings().keyFor(action));
    }
    Settings saved = SettingsProvider.loadSettings();
    Assertions.assertEquals(0.3, saved.getVolume());
    Assertions.assertEquals(0.6, saved.getSfxVolume());
    Assertions.assertTrue(saved.isMuted());
    Assertions.assertTrue(saved.isSfxMuted());
    Assertions.assertEquals("Spanish", saved.getLanguage());
  }

  @Test
  void legacySettingsWithoutControlsUseDefaults() throws Exception {
    Files.writeString(
        Path.of(SettingsProvider.SETTINGS_FILE),
        "soundVolume=0.5\nsoundMuted=false\nsoundVolumeSfx=1.0\n"
            + "soundMutedSfx=false\nselectedLanguage=Spanish\n");
    assertDefaultKeys();
    Assertions.assertEquals(0.5, SettingsProvider.loadSettings().getVolume());
    Assertions.assertEquals("Spanish", SettingsProvider.loadSettings().getLanguage());
  }

  @Test
  void invalidKeysUseDefaultsWithoutErasingOtherSettings() throws Exception {
    for (String invalid : new String[] {"NOT_A_KEY", "W", "B"}) {
      Files.writeString(
          Path.of(SettingsProvider.SETTINGS_FILE),
          "soundVolume=0.5\nsoundMuted=false\nsoundVolumeSfx=1.0\n"
              + "soundMutedSfx=false\nselectedLanguage=Spanish\ncontrols.shoot="
              + invalid
              + "\n");
      assertDefaultKeys();
      Assertions.assertEquals("Spanish", SettingsProvider.loadSettings().getLanguage());
      Assertions.assertEquals(0.5, SettingsProvider.loadSettings().getVolume());
    }
  }

  @Test
  void resetPersistsDefaultKeys() throws Exception {
    SettingsProvider.saveKeyBindings(
        KeyBindings.defaults().withKey(ControlAction.SHOOT, KeyCode.J));
    SettingsProvider.saveKeyBindings(KeyBindings.defaults());
    assertDefaultKeys();
  }

  @Test
  void firstSaveAlsoCreatesValidGeneralSettings() throws Exception {
    Files.deleteIfExists(Path.of(SettingsProvider.SETTINGS_FILE));
    SettingsProvider.saveKeyBindings(
        KeyBindings.defaults().withKey(ControlAction.SHOOT, KeyCode.J));
    Assertions.assertEquals(1.0, SettingsProvider.loadSettings().getVolume());
    Assertions.assertEquals(
        KeyCode.J, SettingsProvider.loadKeyBindings().keyFor(ControlAction.SHOOT));
  }

  private void assertDefaultKeys() {
    KeyBindings loaded = SettingsProvider.loadKeyBindings();
    for (ControlAction action : ControlAction.values()) {
      Assertions.assertEquals(action.defaultKey(), loaded.keyFor(action));
    }
  }
}
