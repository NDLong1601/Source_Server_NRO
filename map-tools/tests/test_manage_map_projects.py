from __future__ import annotations

import importlib.util
import json
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

from PIL import Image


REPO_ROOT = Path(__file__).resolve().parents[2]
MANAGER_PATH = REPO_ROOT / "map-tools" / "tools" / "manage_map_projects.py"

spec = importlib.util.spec_from_file_location("teamobi_manage_map_projects", MANAGER_PATH)
manager = importlib.util.module_from_spec(spec)
assert spec.loader is not None
sys.modules[spec.name] = manager
spec.loader.exec_module(manager)


class ManageMapProjectsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory(prefix="teamobi-map-projects-")
        self.repo = Path(self.temporary.name) / "repo"
        (self.repo / "src").mkdir(parents=True)
        (self.repo / "data" / "map" / "tile_map_data").mkdir(parents=True)
        (self.repo / "data" / "map" / "item_bg_map_data").mkdir(parents=True)
        for zoom in range(1, 5):
            (self.repo / "data" / "item_bg_temp" / f"x{zoom}").mkdir(parents=True)
        (self.repo / "data" / "res" / "x2").mkdir(parents=True)
        (self.repo / "map-tools" / "schemas").mkdir(parents=True)
        shutil.copy2(
            REPO_ROOT / "map-tools" / "schemas" / "teamobi-map-plan.schema.json",
            self.repo / "map-tools" / "schemas" / "teamobi-map-plan.schema.json",
        )
        profile_dir = self.repo / ".agents" / "skills" / "generate-teamobi-map" / "references"
        profile_dir.mkdir(parents=True)
        shutil.copy2(
            REPO_ROOT / ".agents" / "skills" / "generate-teamobi-map" / "references" / "tile-profiles.json",
            profile_dir / "tile-profiles.json",
        )
        (self.repo / "sql").mkdir(parents=True)
        shutil.copy2(REPO_ROOT / "sql" / "team2026.sql", self.repo / "sql" / "team2026.sql")
        (self.repo / "data" / "map" / "version.txt").write_text("10\n", encoding="utf-8")

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def create_and_compile(self, map_id: int = 200) -> str:
        created = manager.create_project(self.repo, map_id, "Map test", 12, 8, 2)
        project_id = created["project"]["projectId"]
        manager.compile_project(self.repo, project_id)
        return project_id

    def test_create_save_and_restore_revision(self) -> None:
        created = manager.create_project(self.repo, 200, "Map draft", 12, 8, 2)
        project_id = created["project"]["projectId"]
        plan = created["plan"]
        plan["map"]["name"] = "Map draft revision 2"

        saved = manager.save_project(self.repo, project_id, plan)
        self.assertEqual(saved["metadata"]["revision"], 2)
        self.assertEqual(saved["plan"]["map"]["name"], "Map draft revision 2")
        self.assertEqual(saved["revisions"], ["000001"])

        restored = manager.restore_revision(self.repo, project_id, 1)
        self.assertEqual(restored["metadata"]["revision"], 3)
        self.assertEqual(restored["plan"]["map"]["name"], "Map draft")
        self.assertEqual(restored["revisions"], ["000001", "000002"])

    def test_cli_forces_utf8_when_windows_stdio_starts_as_cp1252(self) -> None:
        environment = dict(os.environ)
        environment["PYTHONIOENCODING"] = "cp1252"
        completed = subprocess.run(
            [
                sys.executable,
                str(MANAGER_PATH),
                "--repo-root", str(self.repo),
                "create", "--map-id", "209", "--name", "Vô Hạn Thành thử nghiệm",
                "--width", "12", "--height", "8", "--tile-set-id", "2",
            ],
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            env=environment,
            check=False,
        )
        self.assertEqual(completed.returncode, 0, completed.stderr.decode("utf-8", errors="replace"))
        result = json.loads(completed.stdout.decode("utf-8"))
        self.assertEqual(result["project"]["displayName"], "Vô Hạn Thành thử nghiệm")

    def test_editor_state_exposes_real_client_tile_previews(self) -> None:
        tile_path = self.repo / "data" / "res" / "x2" / "2$35"
        Image.new("RGBA", (48, 48), (40, 120, 210, 255)).save(tile_path, format="PNG")
        created = manager.create_project(self.repo, 205, "Tile preview", 8, 6, 2)

        state = manager.get_editor_state(self.repo, created["project"]["projectId"])

        self.assertEqual(state["tilePalette"]["visualSource"], "client-resource")
        self.assertIn(35, state["tilePalette"]["available"])
        tile = next(item for item in state["tilePalette"]["tiles"] if item["tileId"] == 35)
        self.assertEqual(tile["sourceZoom"], 2)
        self.assertEqual(Path(tile["previewPath"]), tile_path)

    def test_resize_plan_preserves_anchor_and_reports_destructive_crop(self) -> None:
        plan = manager.new_default_plan(206, "Resize test", 6, 4, 2)
        plan["bgItems"] = [{"templateId": 0, "tileX": 1, "tileY": 1}]
        plan["gameplay"]["npcs"] = [{"npcId": 6, "npcX": 24, "npcY": 48}]
        plan["gameplay"]["waypoints"] = [{
            "name": "Self",
            "minX": 0,
            "minY": 24,
            "maxX": 24,
            "maxY": 48,
            "isEnter": False,
            "isOffline": False,
            "goMap": 206,
            "goX": 24,
            "goY": 48,
        }]

        expanded = manager.resize_plan_for_editor(self.repo, plan, 8, 6, "center")
        self.assertEqual(expanded["report"]["offsetTiles"], {"x": 1, "y": 1})
        self.assertEqual(expanded["plan"]["bgItems"][0]["tileX"], 2)
        self.assertEqual(expanded["plan"]["gameplay"]["npcs"][0]["npcX"], 48)
        self.assertEqual(expanded["plan"]["gameplay"]["waypoints"][0]["goX"], 48)
        self.assertEqual(len(expanded["matrix"]), 6)
        self.assertEqual(len(expanded["matrix"][0]), 8)

        shrunk = manager.resize_plan_for_editor(self.repo, plan, 3, 3, "bottom-right")
        self.assertGreater(shrunk["report"]["croppedNonEmptyTiles"], 0)
        self.assertIn("player", shrunk["report"]["clampedEntities"])
        self.assertEqual(shrunk["plan"]["gameplay"]["playerSpawn"]["x"], 0)

    def test_compile_verify_and_stale_draft_detection(self) -> None:
        project_id = self.create_and_compile()
        verified = manager.verify_project_build(self.repo, project_id)
        self.assertTrue(verified["verified"])

        detail = manager.get_project(self.repo, project_id)
        changed = detail["plan"]
        changed["map"]["name"] = "Changed after compile"
        manager.save_project(self.repo, project_id, changed)
        stale = manager.verify_project_build(self.repo, project_id)
        self.assertFalse(stale["verified"])
        self.assertIn("STALE_BUILD", {issue["code"] for issue in stale["issues"]})

    def test_release_plan_is_blocked_while_server_runs(self) -> None:
        project_id = self.create_and_compile()
        blocked = manager.create_release_plan(self.repo, project_id, server_running=True)
        self.assertFalse(blocked["canPublish"])
        self.assertIn("SERVER_RUNNING", {item["code"] for item in blocked["blockers"]})

        ready = manager.create_release_plan(self.repo, project_id, server_running=False)
        self.assertTrue(ready["canPublish"])
        self.assertEqual(len(ready["operations"]), 4)

    def test_import_runtime_preserves_matrix_background_and_entities(self) -> None:
        map_id = 201
        width, height = 4, 3
        matrix = [
            [0, 0, 0, 0],
            [0, 0, 0, 0],
            [35, 35, 35, 35],
        ]
        (self.repo / "data" / "map" / "tile_map_data" / str(map_id)).write_bytes(
            bytes([width, height, *(tile for row in matrix for tile in row)])
        )
        (self.repo / "data" / "map" / "item_bg_map_data" / str(map_id)).write_bytes(
            struct.pack(">hhhh", 1, 0, 2, 1)
        )
        template = {
            "id": map_id,
            "name": "Imported map",
            "zones": 5,
            "maxPlayer": 12,
            "data": [0, 0, 0, 2, 1],
            "type": 0,
            "planetId": 0,
            "bgType": 0,
            "tileId": 2,
            "bgId": 1,
            "waypoints": [],
            "mobs": ["[1,2,300,48,48]"],
            "npcs": [[6, 24, 48]],
            "isMapDouble": 0,
        }
        imported = manager.import_runtime_project(self.repo, map_id, template)
        plan = imported["plan"]
        self.assertEqual(plan["terrain"]["matrix"], matrix)
        self.assertEqual(plan["bgItems"], [{"templateId": 0, "tileX": 2, "tileY": 1}])
        self.assertEqual(plan["gameplay"]["mobs"][0]["mobTemp"], 1)
        self.assertEqual(plan["gameplay"]["npcs"][0]["npcId"], 6)
        self.assertEqual(plan["gameplay"]["playerSpawn"]["y"], 48)

    def test_publish_runtime_creates_rollback_and_restores_exact_state(self) -> None:
        project_id = self.create_and_compile(202)
        tile_target = self.repo / "data" / "map" / "tile_map_data" / "202"
        bg_target = self.repo / "data" / "map" / "item_bg_map_data" / "202"
        self.assertFalse(tile_target.exists())
        self.assertFalse(bg_target.exists())

        published = manager.publish_runtime(self.repo, project_id, None, server_running=False)
        self.assertTrue(tile_target.is_file())
        self.assertTrue(bg_target.is_file())
        self.assertTrue((self.repo / "map-tools" / "releases" / ".publish.lock").is_file())
        self.assertEqual((self.repo / "data" / "map" / "version.txt").read_text(encoding="utf-8"), "11\n")
        release_dir = Path(published["releaseDirectory"])
        self.assertTrue((release_dir / "rollback-database.sql").is_file())
        self.assertIn("DELETE FROM `map_template`", (release_dir / "rollback-database.sql").read_text(encoding="utf-8"))

        rolled_back = manager.rollback_runtime(
            self.repo,
            project_id,
            published["releaseId"],
            server_running=False,
        )
        self.assertFalse(rolled_back["alreadyRolledBack"])
        self.assertFalse(tile_target.exists())
        self.assertFalse(bg_target.exists())
        self.assertFalse((self.repo / "map-tools" / "releases" / ".publish.lock").exists())
        self.assertEqual((self.repo / "data" / "map" / "version.txt").read_text(encoding="utf-8"), "10\n")

    def test_finalize_release_marks_project_published_and_releases_lock(self) -> None:
        project_id = self.create_and_compile(203)
        published = manager.publish_runtime(self.repo, project_id, None, server_running=False)
        finalized = manager.finalize_release(self.repo, project_id, published["releaseId"])
        detail = manager.get_project(self.repo, project_id)

        self.assertEqual(finalized["release"]["releaseId"], published["releaseId"])
        self.assertEqual(detail["project"]["status"], "published")
        self.assertFalse((self.repo / "map-tools" / "releases" / ".publish.lock").exists())
        releases = manager.list_project_releases(self.repo, project_id)
        self.assertEqual(releases["releases"][0]["releaseId"], published["releaseId"])
        verification = manager.verify_release_runtime(self.repo, project_id, published["releaseId"])
        self.assertTrue(verification["verified"])
        release = manager.get_project_release(self.repo, project_id, published["releaseId"])["release"]
        self.assertEqual(release["releaseVersion"], 2)
        self.assertIn("databaseExpected", release)
        self.assertEqual(set(release["releaseFiles"]), {"manifest", "publishSql", "rollbackDatabaseSql", "databaseBefore"})
        self.assertTrue(all(item.get("sha256Published") for item in release["runtimeSnapshots"]))

    def test_older_published_release_accepts_later_global_map_version(self) -> None:
        first_project_id = self.create_and_compile(203)
        first = manager.publish_runtime(self.repo, first_project_id, None, server_running=False)
        manager.finalize_release(self.repo, first_project_id, first["releaseId"])

        second_project_id = self.create_and_compile(204)
        second = manager.publish_runtime(self.repo, second_project_id, None, server_running=False)
        manager.finalize_release(self.repo, second_project_id, second["releaseId"])

        self.assertEqual((self.repo / "data" / "map" / "version.txt").read_text(encoding="utf-8"), "12\n")
        verification = manager.verify_release_runtime(self.repo, first_project_id, first["releaseId"])
        self.assertTrue(verification["verified"])
        self.assertEqual(verification["mapVersion"]["expectedValue"], 11)
        self.assertEqual(verification["mapVersion"]["currentValue"], 12)
        self.assertTrue(verification["mapVersion"]["matched"])

    def test_published_release_detects_drift_and_rolls_back_only_after_exact_match(self) -> None:
        project_id = self.create_and_compile(207)
        published = manager.publish_runtime(self.repo, project_id, None, server_running=False)
        manager.finalize_release(self.repo, project_id, published["releaseId"])
        tile_target = self.repo / "data" / "map" / "tile_map_data" / "207"
        original_published = tile_target.read_bytes()
        tile_target.write_bytes(b"drift")

        drift = manager.verify_release_runtime(self.repo, project_id, published["releaseId"])
        self.assertFalse(drift["verified"])
        self.assertIn("RUNTIME_FILE_DRIFT", {issue["code"] for issue in drift["issues"]})
        with self.assertRaises(manager.ProjectError):
            manager.rollback_runtime(self.repo, project_id, published["releaseId"], server_running=False)

        tile_target.write_bytes(original_published)
        version_backup = Path(published["releaseDirectory"]) / "backup" / "version.txt"
        original_version_backup = version_backup.read_bytes()
        version_backup.write_bytes(b"corrupt-backup")
        with self.assertRaises(manager.ProjectError):
            manager.rollback_runtime(self.repo, project_id, published["releaseId"], server_running=False)
        self.assertEqual(tile_target.read_bytes(), original_published)
        version_backup.write_bytes(original_version_backup)
        rolled_back = manager.rollback_runtime(self.repo, project_id, published["releaseId"], server_running=False)
        self.assertEqual(rolled_back["previousStatus"], "published")
        self.assertFalse(tile_target.exists())
        self.assertEqual(manager.get_project(self.repo, project_id)["project"]["status"], "draft")
        rollback_verification = manager.verify_release_runtime(self.repo, project_id, published["releaseId"])
        self.assertTrue(rollback_verification["verified"])
        self.assertEqual(rollback_verification["mode"], "rolled-back")

    def test_release_acceptance_is_scoped_validated_and_preserved_as_sidecar(self) -> None:
        created = manager.create_project(self.repo, 208, "Acceptance map", 12, 8, 2)
        project_id = created["project"]["projectId"]
        plan = created["plan"]
        plan["gameplay"]["waypoints"] = [{
            "name": "Cổng tự kiểm tra", "minX": 0, "minY": 120, "maxX": 24, "maxY": 144,
            "isEnter": False, "isOffline": False, "goMap": 208, "goX": 24, "goY": 144,
        }]
        plan["gameplay"]["npcs"] = [{"npcId": 6, "npcX": 48, "npcY": 144}]
        plan["gameplay"]["mobs"] = [{"mobTemp": 1, "mobLevel": 1, "mobHp": 100, "mobX": 72, "mobY": 144}]
        manager.save_project(self.repo, project_id, plan)
        manager.compile_project(self.repo, project_id)
        published = manager.publish_runtime(self.repo, project_id, None, server_running=False)

        with self.assertRaises(manager.ProjectError):
            manager.save_release_acceptance(
                self.repo, project_id, published["releaseId"], {"tester": "Admin", "notes": "", "checks": {}},
            )

        manager.finalize_release(self.repo, project_id, published["releaseId"])
        initial = manager.get_release_acceptance(self.repo, project_id, published["releaseId"])
        self.assertFalse(initial["saved"])
        self.assertEqual(set(initial["requiredChecks"]), set(manager.ACCEPTANCE_CHECK_KEYS))

        payload = {
            "tester": "Admin QA",
            "notes": "Đã đi hết map ở client x4.",
            "checks": {key: True for key in manager.ACCEPTANCE_CHECK_KEYS},
        }
        saved = manager.save_release_acceptance(self.repo, project_id, published["releaseId"], payload)
        self.assertTrue(saved["manualComplete"])
        self.assertTrue(Path(saved["path"]).is_file())
        self.assertEqual(saved["acceptance"]["tester"], "Admin QA")
        self.assertEqual(
            manager.list_project_releases(self.repo, project_id)["releases"][0]["acceptanceStatus"],
            "manual-complete",
        )

        manager.rollback_runtime(self.repo, project_id, published["releaseId"], server_running=False)
        with self.assertRaises(manager.ProjectError):
            manager.save_release_acceptance(self.repo, project_id, published["releaseId"], payload)

    def test_custom_x4_asset_compiles_publishes_and_rolls_back_all_zooms(self) -> None:
        created = manager.create_project(self.repo, 204, "Custom asset map", 12, 8, 2)
        project_id = created["project"]["projectId"]
        source = Path(self.temporary.name) / "custom-x4.png"
        Image.new("RGBA", (40, 20), (20, 180, 90, 255)).save(source, format="PNG")

        uploaded = manager.upload_project_asset(
            self.repo,
            project_id,
            source,
            "Foreground test",
            4,
            2,
            -3,
            requested_template_id=600,
            requested_image_id=500,
        )
        asset = uploaded["uploadedAsset"]
        self.assertEqual(asset["templateId"], 600)
        self.assertEqual(asset["imageId"], 500)
        expected_sizes = {"x1": (10, 5), "x2": (20, 10), "x3": (30, 15), "x4": (40, 20)}
        for zoom, size in expected_sizes.items():
            path = self.repo / "map-tools" / "projects" / project_id / "assets" / zoom / "500.png"
            with Image.open(path) as generated:
                self.assertEqual(generated.size, size)

        detail = manager.get_project(self.repo, project_id)
        plan = detail["plan"]
        plan["bgItems"].append({"templateId": 600, "tileX": 3, "tileY": 3})
        manager.save_project(self.repo, project_id, plan)
        manager.compile_project(self.repo, project_id)
        build = self.repo / "map-tools" / "output" / "projects" / project_id
        compiled_bg = json.loads((build / "compiled-bg-map.json").read_text(encoding="utf-8"))
        self.assertEqual(compiled_bg["customTemplates"][0]["templateId"], 600)
        for zoom in expected_sizes:
            self.assertTrue((build / "runtime" / "item_bg_temp" / zoom / "500.png").is_file())
        self.assertIn("INSERT INTO `bg_item_template`", (build / "bg-item-template.sql").read_text(encoding="utf-8"))
        manifest = json.loads((build / "manifest.json").read_text(encoding="utf-8"))
        for zoom in expected_sizes:
            self.assertIn(f"runtime/item_bg_temp/{zoom}/500.png", manifest["artifacts"])

        preexisting_x2 = self.repo / "data" / "item_bg_temp" / "x2" / "500.png"
        preexisting_x2.write_bytes(b"legacy-x2-asset")

        database_before = {"mapTemplate": None, "bgTemplates": [{"id": 600, "row": None}]}
        published = manager.publish_runtime(self.repo, project_id, database_before, server_running=False)
        for zoom in expected_sizes:
            self.assertTrue((self.repo / "data" / "item_bg_temp" / zoom / "500.png").is_file())
        release = Path(published["releaseDirectory"])
        self.assertIn("bg_item_template", (release / "publish.sql").read_text(encoding="utf-8"))
        rollback_sql = (release / "rollback-database.sql").read_text(encoding="utf-8")
        self.assertIn("DELETE FROM `bg_item_template` WHERE `id` = 600", rollback_sql)

        manager.rollback_runtime(self.repo, project_id, published["releaseId"], server_running=False)
        for zoom in expected_sizes:
            runtime_asset = self.repo / "data" / "item_bg_temp" / zoom / "500.png"
            if zoom == "x2":
                self.assertEqual(runtime_asset.read_bytes(), b"legacy-x2-asset")
            else:
                self.assertFalse(runtime_asset.exists())

    def test_fine_offset_runtime_template_reuses_existing_png_without_asset_copy(self) -> None:
        created = manager.create_project(self.repo, 210, "Fine offset map", 12, 8, 2)
        project_id = created["project"]["projectId"]
        plan = created["plan"]
        plan["bgItems"] = [
            {"templateId": 0, "tileX": 2, "tileY": 2, "offsetX": 5, "offsetY": 9}
        ]
        manager.save_project(self.repo, project_id, plan)
        manager.compile_project(self.repo, project_id, minimum_template_id=700)

        build = self.repo / "map-tools" / "output" / "projects" / project_id
        compiled = json.loads((build / "compiled-bg-map.json").read_text(encoding="utf-8"))
        generated = compiled["customTemplates"][0]
        self.assertTrue(generated["generatedOffsetTemplate"])
        self.assertEqual(generated["assets"], {})
        self.assertGreaterEqual(generated["templateId"], 700)
        generated_registry = manager.load_generated_offset_registry(
            self.repo / "map-tools" / "projects" / project_id
        )
        self.assertEqual(generated_registry["templates"][0]["templateId"], generated["templateId"])

        second = manager.create_project(self.repo, 211, "Fine offset map 2", 12, 8, 2)
        second_plan = second["plan"]
        second_plan["bgItems"] = [
            {"templateId": 0, "tileX": 1, "tileY": 1, "offsetX": 5, "offsetY": 9}
        ]
        manager.save_project(self.repo, second["project"]["projectId"], second_plan)
        manager.compile_project(
            self.repo,
            second["project"]["projectId"],
            minimum_template_id=700,
        )
        second_build = (
            self.repo / "map-tools" / "output" / "projects" / second["project"]["projectId"]
        )
        second_compiled = json.loads(
            (second_build / "compiled-bg-map.json").read_text(encoding="utf-8")
        )
        self.assertNotEqual(
            generated["templateId"], second_compiled["customTemplates"][0]["templateId"]
        )

        release_plan = manager.create_release_plan(self.repo, project_id, server_running=False)
        self.assertTrue(release_plan["canPublish"])
        self.assertEqual(release_plan["assetScaling"]["imageCount"], 0)
        self.assertFalse(any(item["kind"] == "copy-bg-asset" for item in release_plan["operations"]))

        database_before = {
            "mapTemplate": None,
            "bgTemplates": [{"id": generated["templateId"], "row": None}],
        }
        published = manager.publish_runtime(
            self.repo, project_id, database_before, server_running=False
        )
        release = manager.load_json(Path(published["releaseDirectory"]) / "release.json")
        self.assertEqual(release["assetImageIds"], [])
        self.assertEqual(release["customTemplateIds"], [generated["templateId"]])
        manager.rollback_runtime(
            self.repo, project_id, published["releaseId"], server_running=False
        )


if __name__ == "__main__":
    unittest.main()
