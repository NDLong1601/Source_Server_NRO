from __future__ import annotations

import importlib.util
import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image


REPO_ROOT = Path(__file__).resolve().parents[2]
COMPILER_PATH = REPO_ROOT / "map-tools" / "tools" / "compile_map_plan.py"
PLAN_PATH = REPO_ROOT / "map-tools" / "examples" / "stage1-demo" / "teamobi-map-plan.json"

spec = importlib.util.spec_from_file_location("teamobi_compile_map_plan", COMPILER_PATH)
compiler = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = compiler
spec.loader.exec_module(compiler)


class CompileMapPlanTests(unittest.TestCase):
    def setUp(self) -> None:
        self.plan = json.loads(PLAN_PATH.read_text(encoding="utf-8"))
        self.map_id = self.plan["map"]["mapId"]

    def test_compile_canonical_bundle_without_runtime_write(self) -> None:
        runtime_tile = REPO_ROOT / "data" / "map" / "tile_map_data" / str(self.map_id)
        runtime_bg = REPO_ROOT / "data" / "map" / "item_bg_map_data" / str(self.map_id)
        before = (runtime_tile.exists(), runtime_bg.exists())

        with tempfile.TemporaryDirectory(prefix="teamobi-map-test-") as temp:
            output = Path(temp) / "bundle"
            result = compiler.compile_plan(PLAN_PATH, output, REPO_ROOT)
            self.assertEqual(result.report["summary"]["errors"], 0)

            required = {
                "compiled-tile-map.json",
                "compiled-bg-map.json",
                "compiled-map-template.json",
                "bg_item_registry.json",
                "validation-report.json",
                "manifest.json",
                "map-template.sql",
                "bg-item-template.sql",
                "preview_clean.png",
                "preview_debug.png",
                "teamobi-map-plan.json",
            }
            self.assertTrue(required.issubset({path.name for path in output.iterdir()}))

            width = self.plan["map"]["widthTiles"]
            height = self.plan["map"]["heightTiles"]
            tile_binary = output / "runtime" / "tile_map_data" / str(self.map_id)
            tile_data = tile_binary.read_bytes()
            self.assertEqual(tile_data[:2], bytes((width, height)))
            self.assertEqual(len(tile_data), 2 + width * height)

            bg_binary = output / "runtime" / "item_bg_map_data" / str(self.map_id)
            bg_data = bg_binary.read_bytes()
            self.assertEqual(struct.unpack(">h", bg_data[:2])[0], len(self.plan["bgItems"]))
            self.assertEqual(len(bg_data), 2 + 6 * len(self.plan["bgItems"]))

            with Image.open(output / "preview_clean.png") as preview:
                self.assertEqual(preview.size, (width * 24, height * 24))
            with Image.open(output / "preview_debug.png") as preview:
                self.assertEqual(preview.size, (width * 24, height * 24))

            manifest = json.loads((output / "manifest.json").read_text(encoding="utf-8"))
            self.assertTrue(manifest["publishReady"])
            self.assertIn(f"runtime/tile_map_data/{self.map_id}", manifest["artifacts"])
            self.assertIn(f"runtime/item_bg_map_data/{self.map_id}", manifest["artifacts"])
            self.assertNotIn("manifest.json", manifest["artifacts"])

            compiled_tile = json.loads((output / "compiled-tile-map.json").read_text(encoding="utf-8"))
            used_tile_ids = {tile for row in compiled_tile["matrix"] for tile in row if tile}
            resolved = set(compiled_tile["previewAssets"]["resolvedTileIds"])
            missing = set(compiled_tile["previewAssets"]["missingTileIds"])
            self.assertEqual(used_tile_ids, resolved | missing)
            self.assertTrue(resolved)
            self.assertTrue(any(key.startswith("tilePreview.") for key in manifest["references"]))

            sql = (output / "map-template.sql").read_text(encoding="utf-8")
            self.assertIn("INSERT INTO `map_template`", sql)
            self.assertIn("ON DUPLICATE KEY UPDATE", sql)

        self.assertEqual(before, (runtime_tile.exists(), runtime_bg.exists()))

    def test_compilation_is_deterministic(self) -> None:
        with tempfile.TemporaryDirectory(prefix="teamobi-map-determinism-") as temp:
            root = Path(temp)
            first = root / "first"
            second = root / "second"
            compiler.compile_plan(PLAN_PATH, first, REPO_ROOT)
            compiler.compile_plan(PLAN_PATH, second, REPO_ROOT)
            first_manifest = json.loads((first / "manifest.json").read_text(encoding="utf-8"))
            second_manifest = json.loads((second / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(first_manifest, second_manifest)
            for relative, metadata in first_manifest["artifacts"].items():
                self.assertEqual((first / relative).read_bytes(), (second / relative).read_bytes())
                self.assertEqual(metadata, second_manifest["artifacts"][relative])

    def test_invalid_runtime_dimension_is_rejected(self) -> None:
        invalid = json.loads(json.dumps(self.plan))
        invalid["map"]["widthTiles"] = 128
        with tempfile.TemporaryDirectory(prefix="teamobi-map-invalid-") as temp:
            temp_root = Path(temp)
            plan_path = temp_root / "invalid.json"
            output = temp_root / "output"
            plan_path.write_text(json.dumps(invalid, ensure_ascii=False), encoding="utf-8")
            with self.assertRaises(compiler.CompilationFailed) as caught:
                compiler.compile_plan(plan_path, output, REPO_ROOT)
            self.assertGreater(caught.exception.report["summary"]["errors"], 0)
            self.assertFalse(output.exists())

    def test_entity_without_ground_is_rejected(self) -> None:
        invalid = json.loads(json.dumps(self.plan))
        invalid["gameplay"]["playerSpawn"] = {"x": 72, "y": 240}
        with tempfile.TemporaryDirectory(prefix="teamobi-map-ground-") as temp:
            temp_root = Path(temp)
            plan_path = temp_root / "invalid-ground.json"
            plan_path.write_text(json.dumps(invalid, ensure_ascii=False), encoding="utf-8")
            with self.assertRaises(compiler.CompilationFailed) as caught:
                compiler.compile_plan(plan_path, temp_root / "output", REPO_ROOT)
            codes = {issue["code"] for issue in caught.exception.report["issues"]}
            self.assertIn("NO_GROUND_AT_ENTITY", codes)

    def test_flying_mob_without_ground_is_allowed(self) -> None:
        valid = json.loads(json.dumps(self.plan))
        valid["gameplay"]["mobs"] = [
            {"mobTemp": 7, "mobLevel": 1, "mobHp": 100, "mobX": 72, "mobY": 240}
        ]
        with tempfile.TemporaryDirectory(prefix="teamobi-map-flying-mob-") as temp:
            temp_root = Path(temp)
            plan_path = temp_root / "flying-mob.json"
            plan_path.write_text(json.dumps(valid, ensure_ascii=False), encoding="utf-8")
            result = compiler.compile_plan(plan_path, temp_root / "output", REPO_ROOT)
            self.assertEqual(result.report["summary"]["errors"], 0)

    def test_force_keeps_previous_bundle_for_rollback(self) -> None:
        with tempfile.TemporaryDirectory(prefix="teamobi-map-force-") as temp:
            temp_root = Path(temp)
            output = temp_root / "bundle"
            compiler.compile_plan(PLAN_PATH, output, REPO_ROOT)

            changed = json.loads(json.dumps(self.plan))
            changed["map"]["name"] = "Bản đồ đã sửa"
            changed_path = temp_root / "changed.json"
            changed_path.write_text(json.dumps(changed, ensure_ascii=False), encoding="utf-8")
            compiler.compile_plan(changed_path, output, REPO_ROOT, force=True)

            previous = Path(str(output) + ".previous")
            self.assertTrue(previous.is_dir())
            previous_plan = json.loads((previous / "teamobi-map-plan.json").read_text(encoding="utf-8"))
            current_plan = json.loads((output / "teamobi-map-plan.json").read_text(encoding="utf-8"))
            self.assertEqual(previous_plan["map"]["name"], self.plan["map"]["name"])
            self.assertEqual(current_plan["map"]["name"], "Bản đồ đã sửa")

    def test_runtime_output_path_is_blocked(self) -> None:
        forbidden = REPO_ROOT / "data" / "map" / "stage1-should-never-exist"
        self.assertFalse(forbidden.exists())
        with self.assertRaises(ValueError):
            compiler.compile_plan(PLAN_PATH, forbidden, REPO_ROOT)
        self.assertFalse(forbidden.exists())

    def test_asset_fine_offset_materializes_runtime_template_and_exact_pixel(self) -> None:
        precise = json.loads(json.dumps(self.plan))
        precise["bgItems"] = [
            {"templateId": 0, "tileX": 5, "tileY": 7, "offsetX": 7, "offsetY": 11}
        ]
        with tempfile.TemporaryDirectory(prefix="teamobi-map-fine-offset-") as temp:
            root = Path(temp)
            plan_path = root / "precise.json"
            output = root / "bundle"
            plan_path.write_text(json.dumps(precise, ensure_ascii=False), encoding="utf-8")

            compiler.compile_plan(plan_path, output, REPO_ROOT)

            compiled = json.loads((output / "compiled-bg-map.json").read_text(encoding="utf-8"))
            item = compiled["items"][0]
            generated = compiled["customTemplates"][0]
            self.assertEqual(item["sourceTemplateId"], 0)
            self.assertNotEqual(item["templateId"], 0)
            self.assertEqual(item["pixelX"], 5 * 24 + item["dx"])
            self.assertEqual(item["pixelY"], 7 * 24 + item["dy"])
            self.assertEqual(item["editorOffsetX"], 7)
            self.assertEqual(item["editorOffsetY"], 11)
            self.assertTrue(generated["generatedOffsetTemplate"])
            self.assertEqual(generated["assets"], {})

            bg_data = (output / "runtime" / "item_bg_map_data" / str(self.map_id)).read_bytes()
            runtime_template_id, runtime_x, runtime_y = struct.unpack(">hhh", bg_data[2:8])
            self.assertEqual(runtime_template_id, item["templateId"])
            self.assertEqual((runtime_x, runtime_y), (5, 7))
            sql = (output / "bg-item-template.sql").read_text(encoding="utf-8")
            self.assertIn(f"({runtime_template_id},", sql)


if __name__ == "__main__":
    unittest.main()
