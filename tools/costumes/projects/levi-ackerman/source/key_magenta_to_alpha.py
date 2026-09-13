#!/usr/bin/env python3
"""Convert the approved flat #ff00ff sheet background to anti-aliased PNG alpha.

This is deliberately project-local: it never changes the source image in place.  It is
for the exact magenta generated for this costume, not a general background-removal tool.
"""

from __future__ import annotations

import argparse
from pathlib import Path

import numpy as np
from PIL import Image


def matte_from_magenta(rgb: np.ndarray) -> np.ndarray:
    """Classify the deliberately saturated pink background without touching the costume."""
    red, green, blue = rgb[:, :, 0], rgb[:, :, 1], rgb[:, :, 2]
    original_key = (
        (red >= 145.0)
        & (blue >= 145.0)
        & (green <= 115.0)
        & (np.abs(red - blue) <= 105.0)
    )
    # Generated chroma sheets antialias black and white outlines against the
    # magenta matte. Those fringe pixels can fall below the original absolute
    # red/blue threshold and survive as a purple halo after downscaling. Detect
    # them by magenta dominance as well; the Levi palette has no material whose
    # red and blue channels both exceed green by this margin.
    minimum_magenta_channel = np.minimum(red, blue)
    antialiased_key = (
        (minimum_magenta_channel >= 70.0)
        & ((minimum_magenta_channel - green) >= 28.0)
        & (np.abs(red - blue) <= 120.0)
    )
    is_magenta = original_key | antialiased_key
    return (~is_magenta).astype(np.float32)


def convert(input_path: Path, output_path: Path) -> None:
    source = Image.open(input_path).convert("RGBA")
    rgba = np.asarray(source, dtype=np.float32).copy()
    coverage = matte_from_magenta(rgba[:, :, :3])

    # Neutralize any last low-saturation chroma spill on opaque outline pixels.
    # This is color cleanup only; it does not enlarge or erode the silhouette.
    red, green, blue = rgba[:, :, 0], rgba[:, :, 1], rgba[:, :, 2]
    minimum_magenta_channel = np.minimum(red, blue)
    spill = (coverage > 0.0) & ((minimum_magenta_channel - green) >= 12.0)
    rgba[:, :, 1][spill] = minimum_magenta_channel[spill]

    rgba[:, :, 3] = np.round(255.0 * coverage)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(rgba.astype(np.uint8), "RGBA").save(output_path)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    convert(args.input, args.output)


if __name__ == "__main__":
    main()
