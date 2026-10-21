#!/usr/bin/env python3
"""
Renders the board items and the app icon layers from the Noto Emoji sources in art/items/ (docs/SPEC.md §6, §8).

Each item is recolored so its dominant color is its piece color, the canned food becomes a soda can, and the
results are written as lossless WebP. Needs rsvg-convert and cwebp (both in the devenv shell: run `render-items`).
"""
import math
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ART = ROOT / "art" / "items"
RES = ROOT / "app" / "src" / "main" / "res"

# Color-index order: red, orange, yellow, green, blue, purple (PuzzlerColors.pieces).
ITEMS = ["fries", "burger", "pizza", "plant", "soda", "chocolate"]

# Per item: original hex -> new hex. Unlisted colors are kept.
RECOLOR = {
    "fries": {},  # red carton already
    "burger": {},  # orange bun already
    "pizza": {  # pale cheese -> the yellow piece color; crust and pepperoni stay
        "#FFCC8A": "#F5D442",
    },
    "plant": {  # orange pot -> greens a step away from the leaves
        "#FF8A65": "#6FD08C",
        "#ED603A": "#3FA864",
        "#D44A28": "#2E8A50",
    },
    "soda": {  # yellow tin -> blue can; food label -> light-blue panel, white rim, stripe and logo dot
        "#FEB502": "#3E8BFF",
        "#719921": "#FFFFFF",
        "#E5DDA0": "#9CC4FF",
        "#749B22": "#FFFFFF",
        "#FF5119": "#FFFFFF",
    },
    "chocolate": {  # red wrapper -> purple
        "#DC0D28": "#B065F0",
        "#B0091B": "#7E3FC2",
    },
}

# A pull tab on the soda can's lid (the lid is centered near x=65, y=18 in the 128-unit canvas).
SODA_TAB = """
<path style="fill:#9E9E9E;" d="M58,13.5c0-2.6,3.1-4.2,7-4.2s7,1.6,7,4.2c0,2.6-3.1,4.6-7,4.6S58,16.1,58,13.5z
  M61.2,13.6c0,1.2,1.7,2.1,3.8,2.1s3.8-0.9,3.8-2.1c0-1.2-1.7-2-3.8-2S61.2,12.4,61.2,13.6z" fill-rule="evenodd"/>
<path style="fill:#757575;" d="M63.2,17.6h3.6v3.2h-3.6z"/>
"""

# Icon geometry on the 108-unit adaptive-icon canvas (docs/SPEC.md §6).
CX = CY = 54.0
ORB = 26.0
RING = ORB * 0.6
ITEM = ORB * 0.42
SPARKLE = ORB * 0.23
ORB_COLOR = "#140A26"
GOLD = "#FFD34D"
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def recolored(name: str) -> str:
    svg = (ART / f"{name}.svg").read_text()
    for old, new in RECOLOR[name].items():
        svg = re.sub(re.escape(old), new, svg, flags=re.IGNORECASE)
    if name == "soda":
        svg = svg.replace("</svg>", SODA_TAB + "</svg>")
    return svg


def inner(svg: str) -> tuple[str, str]:
    """The viewBox and body of an SVG, for nesting it inside another."""
    view_box = re.search(r'viewBox="([^"]+)"', svg).group(1)
    body = re.sub(r"^.*?<svg[^>]*>", "", svg, flags=re.DOTALL)
    body = re.sub(r"</svg>\s*$", "", body)
    return view_box, body


def run(*cmd: str) -> None:
    subprocess.run(cmd, check=True)


def to_webp(svg: str, size: int, out: Path, tmp: Path) -> None:
    src = tmp / (out.stem + ".svg")
    png = tmp / (out.stem + ".png")
    src.write_text(svg)
    run("rsvg-convert", "-w", str(size), "-h", str(size), str(src), "-o", str(png))
    out.parent.mkdir(parents=True, exist_ok=True)
    run("cwebp", "-quiet", "-lossless", "-z", "9", str(png), "-o", str(out))


def sparkle_path() -> str:
    points = []
    for i in range(8):
        r = SPARKLE if i % 2 == 0 else SPARKLE * 0.3
        a = -math.pi / 2 + i * math.pi / 4
        points.append(f"{CX + math.cos(a) * r:.2f},{CY + math.sin(a) * r:.2f}")
    return "M" + " L".join(points) + " Z"


def ring_items(svgs: dict[str, str], black: bool = False) -> str:
    """The six mini items on the color-bomb ring, first at the top, clockwise."""
    out = []
    for i, name in enumerate(ITEMS):
        a = -math.pi / 2 + 2 * math.pi * i / len(ITEMS)
        x = CX + math.cos(a) * RING - ITEM / 2
        y = CY + math.sin(a) * RING - ITEM / 2
        view_box, body = inner(svgs[name])
        style = ' filter="url(#black)"' if black else ""
        out.append(f'<svg x="{x:.2f}" y="{y:.2f}" width="{ITEM:.2f}" height="{ITEM:.2f}" viewBox="{view_box}"{style}>{body}</svg>')
    return "\n".join(out)


def icon_svgs(svgs: dict[str, str]) -> tuple[str, str]:
    head = '<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
    foreground = (
        head
        + f'<circle cx="{CX}" cy="{CY}" r="{ORB}" fill="{ORB_COLOR}"/>'
        + ring_items(svgs)
        + f'<path d="{sparkle_path()}" fill="{GOLD}"/></svg>'
    )
    # Themed icon: the orb silhouette with the items and sparkle cut out (a luminance mask: white keeps, black cuts).
    monochrome = (
        head
        + '<defs><filter id="black"><feColorMatrix type="matrix" values="0 0 0 0 0  0 0 0 0 0  0 0 0 0 0  0 0 0 1 0"/></filter>'
        + '<mask id="cut">'
        + f'<circle cx="{CX}" cy="{CY}" r="{ORB}" fill="#fff"/>'
        + ring_items(svgs, black=True)
        + f'<path d="{sparkle_path()}" fill="#000"/></mask></defs>'
        + '<rect width="108" height="108" fill="#fff" mask="url(#cut)"/></svg>'
    )
    return foreground, monochrome


def main() -> None:
    svgs = {name: recolored(name) for name in ITEMS}
    with tempfile.TemporaryDirectory() as tmp_name:
        tmp = Path(tmp_name)
        for name in ITEMS:
            to_webp(svgs[name], 192, RES / "drawable-nodpi" / f"item_{name}.webp", tmp)
        foreground, monochrome = icon_svgs(svgs)
        for density, scale in DENSITIES.items():
            size = round(108 * scale)
            to_webp(foreground, size, RES / f"mipmap-{density}" / "ic_launcher_foreground.webp", tmp)
            to_webp(monochrome, size, RES / f"mipmap-{density}" / "ic_launcher_monochrome.webp", tmp)
    print(f"Rendered {len(ITEMS)} items and the icon layers for {len(DENSITIES)} densities.", file=sys.stderr)


if __name__ == "__main__":
    main()
