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

  # librsvg and libwebp render the item artwork (scripts/render-items.py); gitleaks and uv (for mobsfscan) are the
  # local security scans.
  packages = [ pkgs.gradle pkgs.librsvg pkgs.libwebp pkgs.python3 pkgs.gitleaks pkgs.uv ];

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
    # Zero tolerance: any finding fails. Same checks as the CI lint job.
    lint.exec = "./gradlew ktlintCheck detekt :app:lintDebug";
    format.exec = "./gradlew ktlintFormat";
    # Secrets in the full git history, then Android-specific source scan. CodeQL runs in CI only.
    # mobsfscan reads the release merged manifest (what ships) plus the source; EIO_BACKEND=posix keeps its
    # semgrep engine off io_uring, which fails in some environments. Exceptions live in .mobsf.
    security-scan.exec = ''
      set -e
      gitleaks git --redact --verbose .
      ./gradlew -q :app:processReleaseMainManifest
      EIO_BACKEND=posix uvx mobsfscan==1.0.1 --config .mobsf --exit-warning \
        app/src/main app/build/intermediates/merged_manifest/release/processReleaseMainManifest
    '';
    install-apk.exec = ''
      apk="''${1:-app/build/outputs/apk/release/app-release.apk}"
      adb install -r "$apk"
    '';
  };

  enterShell = ''
    echo "Puzzler dev shell — JDK: $(java -version 2>&1 | head -1)"
    echo "ANDROID_HOME=$ANDROID_HOME"
    echo "Scripts: build-debug build-release unit-test ui-test lint format security-scan gen-keystore verify-apk run-emulator install-apk render-items"
  '';
}
