# Puzzler

A match-3 puzzle game for Android, written in Kotlin with Jetpack Compose. Ten levels, each harder than the last. Score **100 points** to pass a level, and score more to earn 2 or 3 stars. See [docs/SPEC.md](docs/SPEC.md) for the full game rules.

## Scoring

| Pattern | Points | Creates |
|---|---|---|
| Line of 3 | 5 | – |
| Line of 4 | 10 | Striped piece (clears a row or column) |
| L / T shape | 15 | Bomb (clears 3×3) |
| Line of 5 | 25 | Color bomb (clears one color) |
| Piece cleared by a special | +1 | |
| Jelly cleared / stone broken | +2 / +3 | |
| Cascades | ×1, ×1.5, ×2, … | |

Levels 1–5 limit your moves. Levels 6–10 also have a timer, and running out of either one ends the level. The levels are defined in `app/src/main/assets/levels.json`.

## Development environment

The build uses [devenv](https://devenv.sh), which provides a JDK, the Android SDK (platform 35, build-tools 35.0.0) and Gradle. You don't need Android Studio. The emulator is opt-in (see UI tests below) because its system image is a ~1.5 GB download.

One-time setup (Arch with a multi-user Nix install):

```sh
sudo systemctl enable --now nix-daemon.socket
nix --extra-experimental-features 'nix-command flakes' profile add nixpkgs#devenv
```

Then, from the repo:

```sh
devenv shell          # or `direnv allow` to use .envrc
unit-test             # engine tests
build-debug           # app/build/outputs/apk/debug/app-debug.apk
```

## Signing a release for sideloading

```sh
gen-keystore      # one time: creates ~/.android-keys/puzzler-release.jks and ./keystore.properties
build-release     # signed (APK Signature Scheme v3) and minified with R8
verify-apk        # checks the signature and prints the certificate's SHA-256
install-apk       # adb install -r on a connected phone
```

- The keystore stays **outside the repo**, and `keystore.properties` is gitignored.
- On CI, or to skip the file, set `PUZZLER_KEYSTORE_FILE`, `PUZZLER_KEYSTORE_PASSWORD`, `PUZZLER_KEY_ALIAS` and `PUZZLER_KEY_PASSWORD` instead.
- `assembleRelease` fails if no key is configured, so you never get an unsigned release by mistake.
- **Back up the keystore and its password.** Android only installs an update if it's signed with the same key, so losing the key means uninstalling the app and losing progress.
- To check that an installed copy is genuine, compare the fingerprint `verify-apk` prints with the output of `adb shell pm dump com.sheen.puzzler | grep -A1 signatures` (or with any APK-info app).

## UI tests

On a phone with USB debugging on (it's quicker):

```sh
devenv shell
ui-test           # runs on whatever `adb devices` shows
```

Or on an emulator, using the `emulator` profile, which adds the emulator and the API 35 system image:

```sh
devenv --profile emulator shell
run-emulator      # headless API 35 emulator (add --window to watch it)
ui-test
```

## Versioning and releases

Versions are `MAJOR.MINOR.PATCH`, and each release is a `vX.Y.Z` git tag with a GitHub Release that has the signed APK attached. The app's `versionName` comes from the tag, and `versionCode` is `MAJOR*10000 + MINOR*100 + PATCH`.

- **MINOR and PATCH are automatic.** After CI passes on a push to `main`, the Release workflow reads the [Conventional Commit](https://www.conventionalcommits.org) messages since the last tag. A `feat: …` commit bumps MINOR, and a `fix: …` commit bumps PATCH. Other types (`chore:`, `docs:`, `test:`, `ci:`, `refactor:`, …) release nothing. `feat!:` does not bump MAJOR.
- **MAJOR is manual.** Go to Actions → Release → Run workflow and type `major`.
- **Format is enforced on PR titles.** PRs are squash-merged, so the title becomes the commit on `main`. The PR title check rejects titles that don't follow the format.
- `scripts/next-version.sh` prints the version the next release would get. Local builds take their version from the latest tag, so run `git fetch --tags` first.

One-time GitHub setup:

1. Settings → Secrets and variables → Actions → add these repository secrets:
   - `PUZZLER_KEYSTORE_BASE64`: the output of `base64 -w0 ~/.android-keys/puzzler-release.jks`
   - `PUZZLER_KEYSTORE_PASSWORD` and `PUZZLER_KEY_PASSWORD`: the keystore password. `gen-keystore` makes a PKCS12 store, which uses the same password for both.
   - `PUZZLER_KEY_ALIAS`: `puzzler`
2. Settings → General → Pull Requests: allow squash merging, with the default commit message set to "Pull request title". Turn off merge commits and rebase merging, so `main` gets exactly one commit per PR.
