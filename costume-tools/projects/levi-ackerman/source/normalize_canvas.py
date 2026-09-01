#!/usr/bin/env python3
"""Proportionally normalize one accepted RGBA source canvas for the NRO draft."""

from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("input", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--height", type=int, required=True)
    args = parser.parse_args()
    if args.height < 1:
        raise ValueError("--height must be positive")
    image = Image.open(args.input).convert("RGBA")
    scale = args.height / image.height
    width = max(1, round(image.width * scale))
    normalized = image.resize((width, args.height), Image.Resampling.LANCZOS)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    normalized.save(args.output)
    print({"input": str(args.input), "output": str(args.output), "size": normalized.size})


if __name__ == "__main__":
    main()
