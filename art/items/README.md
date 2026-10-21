# Item artwork

Source SVGs for the six board items, unmodified from [Noto Emoji](https://github.com/googlefonts/noto-emoji) (`2D/svg/`) by Google:

| File | Emoji | Piece color |
|---|---|---|
| `fries.svg` | 🍟 `emoji_u1f35f` | red (0) |
| `burger.svg` | 🍔 `emoji_u1f354` | orange (1) |
| `pizza.svg` | 🍕 `emoji_u1f355` | yellow (2) |
| `plant.svg` | 🪴 `emoji_u1fab4` | green (3) |
| `soda.svg` | 🥫 `emoji_u1f96b` (adapted into a soda can) | blue (4) |
| `chocolate.svg` | 🍫 `emoji_u1f36b` | purple (5) |

Licensing: the Noto Emoji README states that its image resources are under the Apache License 2.0, while the repository's `LICENSE` file (copied here as `LICENSE`) is the SIL Open Font License 1.1. Both allow modified copies to be bundled in the app.

`scripts/render-items.py` (devenv: `render-items`) recolors these to the game's piece colors, turns the canned food into a soda can, and renders:
- `app/src/main/res/drawable-nodpi/item_*.webp`: the board items
- `app/src/main/res/mipmap-*/ic_launcher_foreground.webp` and `ic_launcher_monochrome.webp`: the app icon

Edit the recolor tables in the script, not the SVGs.
