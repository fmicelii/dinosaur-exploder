/*
 * SPDX-FileCopyrightText: 2026 jvondermarck
 * SPDX-License-Identifier: MIT
 */

package com.dinosaur.dinosaurexploder;

import com.almasb.fxgl.app.GameApplication;
import com.jpro.webapi.JProApplication;
import java.util.concurrent.CompletableFuture;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class DinosaurWebApp extends JProApplication {

  @Override
  public void start(Stage stage) {
    System.setProperty("fxgl.isBrowser", "true");

    GameApplication app =
        new DinosaurApp() {
          @Override
          protected void initGame() {
            runOnFxThread(super::initGame);
          }

          @Override
          protected void initPhysics() {
            runOnFxThread(super::initPhysics);
          }
        };
    var fxglRoot = GameApplication.embeddedLaunch(app);

    stage.setScene(new Scene(new StackPane(fxglRoot)));
  }

  static void runOnFxThread(Runnable action) {
    if (Platform.isFxApplicationThread()) {
      action.run();
    } else {
      // JPro observes the live scene on restart; initialization must finish before FXGL continues.
      CompletableFuture.runAsync(action, Platform::runLater).join();
    }
  }

  public static void main(String[] args) {
    launch(args);
  }
}
