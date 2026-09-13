from __future__ import annotations

from pathlib import Path

from PIL import Image


PROJECT_DIR = Path(__file__).resolve().parent
SOURCE_DIR = PROJECT_DIR / "source-v2-chibi"
IDLE_FULL = SOURCE_DIR / "idle-full.png"
IDLE_OUTPUT = SOURCE_DIR / "idle.png"


def alpha_bbox(image: Image.Image) -> tuple[int, int, int, int]:
    bbox = image.getchannel("A").point(lambda value: 255 if value >= 12 else 0).getbbox()
    if bbox is None:
        raise ValueError("Idle chibi source is fully transparent")
    return bbox


def main() -> None:
    image = Image.open(IDLE_FULL).convert("RGBA")

    normalized_height = 650
    normalized_width = max(1, round(image.width * normalized_height / image.height))
    image.resize((normalized_width, normalized_height), Image.Resampling.LANCZOS).save(IDLE_OUTPUT)

    # Profile and inventory art are user-supplied role assets and must not be
    # regenerated from idle. The build uses profile-user.png/item-icon-user.png.
    print({"idle": str(IDLE_OUTPUT)})


if __name__ == "__main__":
    main()
