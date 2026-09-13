"""Named locations for the map-authoring workspace."""

from __future__ import annotations

from pathlib import Path


MAP_WORKSPACE_RELATIVE = Path("tools") / "maps"


def map_workspace_root(repository_root: Path) -> Path:
    """Return the repository-local map workspace without hard-coded path strings."""
    return (repository_root / MAP_WORKSPACE_RELATIVE).resolve()
