{ pkgs, lib, config, ... }:

{
  # Android SDK; the module also brings a JDK supported by AGP (the system JDK 27 is not).
  android = {
    enable = true;
    platforms.version = [ "35" ];
    buildTools.version = [ "35.0.0" ];
    systemImageTypes = [ "google_apis" ];
    abis = [ "x86_64" ];
    # The emulator and its ~1.5 GB system image are opt-in: `devenv --profile emulator shell`.
    emulator.enable = lib.mkDefault false;
    systemImages.enable = lib.mkDefault false;
    ndk.enable = false;
    googleAPIs.enable = false;
    android-studio.enable = false;
  };

  profiles.emulator.module = {
    android.emulator.enable = true;
    android.systemImages.enable = true;
  };

  languages.kotlin.enable = true;

  # librsvg and libwebp render the item artwork (scripts/render-items.py).
  packages = [ pkgs.gradle pkgs.librsvg pkgs.libwebp pkgs.python3 ];

  env.PUZZLER_AVD = "puzzler-api35";

  scripts = {
    build-debug.exec = "./gradlew :app:assembleDebug";
    build-release.exec = "./gradlew :app:assembleRelease";
    unit-test.exec = "./gradlew :app:testDebugUnitTest";
    ui-test.exec = "./gradlew :app:connectedDebugAndroidTest";
    gen-keystore.exec = "./scripts/gen-keystore.sh \"$@\"";
    verify-apk.exec = "./scripts/verify-apk.sh \"$@\"";
    run-emulator.exec = "./scripts/run-emulator.sh \"$@\"";
    render-items.exec = "python3 ./scripts/render-items.py \"$@\"";
    install-apk.exec = ''
      apk="''${1:-app/build/outputs/apk/release/app-release.apk}"
      adb install -r "$apk"
    '';
  };

  enterShell = ''
    echo "Puzzler dev shell — JDK: $(java -version 2>&1 | head -1)"
    echo "ANDROID_HOME=$ANDROID_HOME"
    echo "Scripts: build-debug build-release unit-test ui-test gen-keystore verify-apk run-emulator install-apk render-items"
  '';
}
