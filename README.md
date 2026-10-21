# Puzzler

A match-3 puzzle game for Android, written in Kotlin with Jetpack Compose. Ten levels, each harder than the last. Score **100 points** to pass a level, and score more to earn 2 or 3 stars. See [docs/SPEC.md](docs/SPEC.md) for the full game rules.

You match six items: fries, burgers, pizza, potted plants, soda cans and chocolate bars. A **How to play** page explains every rule with live demos. Levels 1 and 6 have guided in-level tutorials the first time you play them.

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

The build uses [devenv](https://devenv.sh), which provides a JDK, the Android SDK (platform 35, build-tools 35.0.0) and Gradle. It also provides the art and security tools (librsvg, libwebp, gitleaks, uv). You don't need Android Studio. The emulator is opt-in (see UI tests below) because its system image is a ~1.5 GB download.

One-time setup (Arch with a multi-user Nix install):

```sh
sudo systemctl enable --now nix-daemon.socket
nix --extra-experimental-features 'nix-command flakes' profile add nixpkgs#devenv
```

Then, from the repo:

```sh
devenv shell          # or `direnv allow` to use .envrc
unit-test             # engine, level, tutorial and How to play tests
build-debug           # app/build/outputs/apk/debug/app-debug.apk
lint                  # ktlint, detekt and Android Lint (see Code quality below)
format                # auto-fix code style
security-scan         # gitleaks and mobsfscan
render-items          # regenerate the item and icon images (see Artwork below)
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

`run-emulator` turns the system animations off, which makes the tests steadier. With animations off the app also skips its launch intro, so the tests start straight on the level map. On a phone, turn animations off yourself (Developer options) before `ui-test`. Shut the emulator down with `adb emu kill`.

## Continuous integration

GitHub Actions runs these workflows (in `.github/workflows/`):

| Workflow | When | What |
|---|---|---|
| `CI` | every push, and PRs to `main` | unit tests and the debug APK; Lint; then the UI tests on an API 35 emulator. Test reports and the APK are uploaded as artifacts |
| `Release` | after `CI` passes on `main`, or by hand for MAJOR | see Versioning and releases |
| `PR title` | PRs | checks that the title is a Conventional Commit |
| `Security` | pushes and PRs to `main`, and weekly | see Code quality and security |

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

## Code quality and security

Every check is zero-tolerance: a finding fails it. Exceptions are rare and live in config files, each with its reason: `config/detekt.yml`, `app/lint.xml`, `.mobsf`.

| Check | What it looks for | Local | CI |
|---|---|---|---|
| ktlint | Kotlin style (official style guide, `.editorconfig`) | `lint`; `format` auto-fixes | `CI` → Lint |
| detekt | Code smells, complexity, likely bugs | `lint` | `CI` → Lint |
| Android Lint | Android, manifest, resource and security issues | `lint` | `CI` → Lint |
| gitleaks | Secrets anywhere in the git history | `security-scan` | `Security` → gitleaks |
| mobsfscan | Android-specific security issues in the code and the release manifest | `security-scan` | `Security` → mobsfscan |
| CodeQL | Security and quality issues in the Kotlin code | — | `Security` → CodeQL |
| Dependabot | Vulnerable or outdated Gradle dependencies and Actions | — | weekly PRs |

The lint job is part of CI, so a lint failure also blocks releases. The Security workflow runs on pushes and PRs to `main`, and weekly. CodeQL and mobsfscan findings appear under **Security → Code scanning** on GitHub.

One-time GitHub setup (Settings → Code security):
1. Turn on **Dependabot alerts** and **Dependabot security updates**.
2. Leave CodeQL's *default setup* off: the `Security` workflow is the advanced setup.

## Artwork and credits

- **Items:** based on [Noto Emoji](https://github.com/googlefonts/noto-emoji) by Google, recolored to the six piece colors (the canned-food emoji was adapted into a soda can). The source SVGs are in `art/items/`. `render-items` (`scripts/render-items.py`) recolors them and writes the item images (`drawable-nodpi/item_*.webp`) and the app icon layers (`mipmap-*dpi/`). To change a color, edit the tables in the script and re-run it; don't edit the generated images. Licensing: Noto Emoji's README says its images are Apache 2.0, while its repository's license file is the SIL Open Font License 1.1. Both allow this use. See `art/items/README.md`.
- **Font:** [Fredoka](https://github.com/google/fonts/tree/main/ofl/fredoka) (SIL Open Font License 1.1), for the title and tutorial headings.
- License texts ship with the app in `app/src/main/assets/licenses/`.
