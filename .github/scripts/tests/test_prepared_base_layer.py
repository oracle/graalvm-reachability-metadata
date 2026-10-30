# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Exercise complete base transfers and reject incompatible consumers. §FS-tests.3.6"""

from __future__ import annotations

import importlib.util
import json
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

SCRIPT: Path = Path(__file__).resolve().parents[1] / "prepared_base_layer.py"
SPEC = importlib.util.spec_from_file_location("prepared_base_layer", SCRIPT)
base_layer = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(base_layer)


class PreparedBaseLayerTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root: Path = Path(self.temporary.name)
        self.identity: dict[str, str] = {
            "revision": "revision-one",
            "nativeImageVersion": "native-image fixture",
            "jdkReleaseSha256": "build-one",
            "os": "Linux",
            "arch": "X64",
            "nativeImageMode": "current-defaults",
        }
        self.base: Path = self.root / "producer"
        self.base.mkdir()
        (self.base / "base-layer.nil").write_bytes(b"layer contents")
        (self.base / "manifest.json").write_text(json.dumps(self.identity), encoding="utf-8")
        library: Path = self.base / "libbase.so"
        library.write_bytes(b"native library")
        library.chmod(0o755)
        (self.base / "libalias.so").symlink_to("libbase.so")
        self.archive: Path = self.root / "shared-base.tar"
        self.destination: Path = self.root / "consumer"
        identity_patch = patch.object(base_layer, "current_identity", return_value=self.identity)
        identity_patch.start()
        self.addCleanup(identity_patch.stop)

    def test_round_trip_preserves_manifest_libraries_permissions_and_links(self) -> None:
        base_layer.pack(self.base, self.archive)
        restored: Path = base_layer.restore(self.archive, self.destination)
        for name in ("base-layer.nil", "manifest.json", "libbase.so"):
            self.assertEqual((restored / name).read_bytes(), (self.base / name).read_bytes())
        self.assertEqual((restored / "libbase.so").stat().st_mode & 0o777, 0o755)
        self.assertTrue((restored / "libalias.so").is_symlink())
        self.assertEqual(os.readlink(restored / "libalias.so"), "libbase.so")

    def test_every_identity_mismatch_is_rejected_before_extraction(self) -> None:
        base_layer.pack(self.base, self.archive)
        for key in self.identity:
            with self.subTest(key=key), patch.object(
                base_layer, "current_identity", return_value={**self.identity, key: "different"}
            ):
                with self.assertRaisesRegex(ValueError, key):
                    base_layer.restore(self.archive, self.destination)
                self.assertFalse(self.destination.exists())

    def test_missing_layer_or_manifest_cannot_be_published(self) -> None:
        for name in ("base-layer.nil", "manifest.json"):
            with self.subTest(name=name):
                file: Path = self.base / name
                contents: bytes = file.read_bytes()
                file.unlink()
                with self.assertRaisesRegex(ValueError, name):
                    base_layer.pack(self.base, self.archive)
                file.write_bytes(contents)
                self.assertFalse(self.archive.exists())

    def test_manifest_must_match_preparing_toolchain_and_mode(self) -> None:
        for key in ("nativeImageVersion", "nativeImageMode"):
            with self.subTest(key=key):
                (self.base / "manifest.json").write_text(
                    json.dumps({**self.identity, key: "different"}), encoding="utf-8"
                )
                with self.assertRaisesRegex(ValueError, key):
                    base_layer.pack(self.base, self.archive)

    def test_cli_exports_restored_directory_for_gradle(self) -> None:
        tool: Path = self.root / "native-image"
        tool.write_text("#!/bin/sh\necho 'native-image fixture'\n", encoding="utf-8")
        tool.chmod(0o755)
        (self.root / "release").write_text("JAVA_VERSION=25\n", encoding="utf-8")
        environment: dict[str, str] = {
            **os.environ, "PATH": str(self.root) + os.pathsep + os.environ["PATH"],
            "JAVA_HOME": str(self.root), "RUNNER_OS": "Linux", "RUNNER_ARCH": "X64",
            "GVM_TCK_NATIVE_IMAGE_MODE": "current-defaults", "GITHUB_ENV": str(self.root / "env"),
        }
        subprocess.run([sys.executable, str(SCRIPT), "pack", "--base-layer-dir", str(self.base),
                        "--archive", str(self.archive)], env=environment, check=True, capture_output=True)
        subprocess.run([sys.executable, str(SCRIPT), "restore", "--archive", str(self.archive),
                        "--destination", str(self.destination)], env=environment, check=True, capture_output=True)
        self.assertEqual((self.root / "env").read_text(encoding="utf-8"),
                         f"GVM_TCK_BASE_LAYER_DIR={self.destination / 'base-layer'}\n")


if __name__ == "__main__":
    unittest.main()
