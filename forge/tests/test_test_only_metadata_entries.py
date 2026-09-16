# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Test-only metadata entry counting and native-image config policy tests."""

import json
import os
import subprocess
import tempfile
import unittest

from utility_scripts.metrics_writer import count_test_only_metadata_entries
from utility_scripts.native_image_config_policy import (
    find_legacy_test_native_image_config_files_for_coordinate,
    is_legacy_test_native_image_config_path,
)


def _git(repo_path: str, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repo_path,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        check=True,
    )
    return result.stdout.strip()


def _commit_all(repo_path: str, message: str) -> str:
    _git(repo_path, "add", "-A")
    _git(repo_path, "-c", "user.name=Forge Test", "-c", "user.email=forge@example.com", "commit", "-m", message)
    return _git(repo_path, "rev-parse", "HEAD")


class TestOnlyMetadataEntryTests(unittest.TestCase):
    def test_count_test_only_metadata_entries_counts_direct_native_image_reachability_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            test_metadata_dir = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            os.makedirs(test_metadata_dir)
            with open(os.path.join(test_metadata_dir, "reachability-metadata.json"), "w", encoding="utf-8") as file:
                json.dump(
                    {
                        "reflection": [
                            {
                                "condition": {"typeReached": "org.example.Demo"},
                                "type": "org.example.TestFixture",
                                "methods": [{"name": "create"}, {"name": "read"}],
                            }
                        ]
                    },
                    file,
                )

            self.assertEqual(count_test_only_metadata_entries(temp_dir, "org.example", "demo", "1.0.0"), 3)

    def test_count_test_only_metadata_entries_rejects_legacy_native_image_config(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            _git(temp_dir, "init")
            with open(os.path.join(temp_dir, "README.md"), "w", encoding="utf-8") as file:
                file.write("base\n")
            base = _commit_all(temp_dir, "base")
            test_metadata_dir = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            os.makedirs(test_metadata_dir)
            with open(os.path.join(test_metadata_dir, "serialization-config.json"), "w", encoding="utf-8") as file:
                json.dump([], file)
            _commit_all(temp_dir, "generated legacy config")

            with self.assertRaisesRegex(ValueError, "reachability-metadata.json"):
                count_test_only_metadata_entries(temp_dir, "org.example", "demo", "1.0.0", base_commit=base)

    def test_count_test_only_metadata_entries_grandfathers_existing_legacy_native_image_config(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            _git(temp_dir, "init")
            test_metadata_dir = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            os.makedirs(test_metadata_dir)
            with open(os.path.join(test_metadata_dir, "serialization-config.json"), "w", encoding="utf-8") as file:
                json.dump([], file)
            base = _commit_all(temp_dir, "existing legacy config")

            self.assertEqual(count_test_only_metadata_entries(
                temp_dir,
                "org.example",
                "demo",
                "1.0.0",
                base_commit=base,
            ), 0)

    def test_native_image_config_policy_resolves_test_version_from_index(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            metadata_dir = os.path.join(temp_dir, "metadata", "org.example", "demo")
            test_metadata_dir = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            os.makedirs(metadata_dir)
            os.makedirs(test_metadata_dir)
            with open(os.path.join(metadata_dir, "index.json"), "w", encoding="utf-8") as file:
                json.dump(
                    [
                        {
                            "metadata-version": "1.0.0",
                            "test-version": "1.0.0",
                            "tested-versions": ["1.0.1"],
                        }
                    ],
                    file,
                )
            with open(os.path.join(test_metadata_dir, "resource-config.json"), "w", encoding="utf-8") as file:
                json.dump({"resources": {"includes": []}}, file)

            self.assertTrue(is_legacy_test_native_image_config_path(
                "tests/src/org.example/demo/1.0.0/src/test/resources/META-INF/native-image/resource-config.json"
            ))
            self.assertEqual(
                find_legacy_test_native_image_config_files_for_coordinate(temp_dir, "org.example:demo:1.0.1"),
                [
                    "tests/src/org.example/demo/1.0.0/src/test/resources/META-INF/native-image/resource-config.json",
                ],
            )

    def test_native_image_config_policy_covers_extension_suites_outside_test_resources(self) -> None:
        """Extension suites are siblings of `src/`, so anchoring on META-INF let them through."""
        with tempfile.TemporaryDirectory() as temp_dir:
            metadata_dir = os.path.join(temp_dir, "metadata", "org.example", "demo")
            suite_metadata_dir = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "code-coverage-improvement",
                "metadata",
            )
            os.makedirs(metadata_dir)
            os.makedirs(suite_metadata_dir)
            with open(os.path.join(metadata_dir, "index.json"), "w", encoding="utf-8") as file:
                json.dump(
                    [
                        {
                            "metadata-version": "1.0.0",
                            "test-version": "1.0.0",
                            "tested-versions": ["1.0.1"],
                        }
                    ],
                    file,
                )
            with open(os.path.join(suite_metadata_dir, "jni-config.json"), "w", encoding="utf-8") as file:
                json.dump([], file)

            suite_path = (
                "tests/src/org.example/demo/1.0.0/code-coverage-improvement/metadata/jni-config.json"
            )
            self.assertTrue(is_legacy_test_native_image_config_path(suite_path))
            self.assertEqual(
                find_legacy_test_native_image_config_files_for_coordinate(temp_dir, "org.example:demo:1.0.1"),
                [suite_path],
            )
            # `reachability-metadata.json` is the supported format, so it stays.
            self.assertFalse(is_legacy_test_native_image_config_path(
                "tests/src/org.example/demo/1.0.0/src/test/resources/META-INF/native-image/reachability-metadata.json"
            ))

    def test_native_image_config_policy_skips_gitignored_build_output(self) -> None:
        """The Gradle native plugin generates split-config files under `build/`; ignored files cannot be committed."""
        with tempfile.TemporaryDirectory() as temp_dir:
            metadata_dir = os.path.join(temp_dir, "metadata", "org.example", "demo")
            test_project_dir = os.path.join(temp_dir, "tests", "src", "org.example", "demo", "1.0.0")
            build_config_dir = os.path.join(
                test_project_dir, "build", "native", "generated", "generateTestResourcesConfigFile"
            )
            test_metadata_dir = os.path.join(
                test_project_dir, "src", "test", "resources", "META-INF", "native-image"
            )
            os.makedirs(metadata_dir)
            os.makedirs(build_config_dir)
            os.makedirs(test_metadata_dir)
            with open(os.path.join(metadata_dir, "index.json"), "w", encoding="utf-8") as file:
                json.dump(
                    [
                        {
                            "metadata-version": "1.0.0",
                            "test-version": "1.0.0",
                            "tested-versions": ["1.0.1"],
                        }
                    ],
                    file,
                )
            for directory in (build_config_dir, test_metadata_dir):
                with open(os.path.join(directory, "resource-config.json"), "w", encoding="utf-8") as file:
                    json.dump({"resources": {"includes": []}}, file)
            _git(temp_dir, "init")
            with open(os.path.join(temp_dir, ".gitignore"), "w", encoding="utf-8") as file:
                file.write("**/build\n")

            self.assertEqual(
                find_legacy_test_native_image_config_files_for_coordinate(temp_dir, "org.example:demo:1.0.1"),
                [
                    "tests/src/org.example/demo/1.0.0/src/test/resources/META-INF/native-image/resource-config.json",
                ],
            )


if __name__ == "__main__":
    unittest.main()
