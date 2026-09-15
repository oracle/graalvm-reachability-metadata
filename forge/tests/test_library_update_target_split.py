# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Cloning, index-splitting, scaffold, and reset tests for library-update targets."""

import json
import os
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.drivers.library_update_preparation import (
    prepare_library_update_target,
    reset_failed_library_update_worktree,
)
from utility_scripts.metadata_index import (
    MATCH_NEW_VERSION,
    resolve_library_update_target,
)


def _write_index(
        repo_path: str,
        entries: list[dict],
        group: str = "org.example",
        artifact: str = "demo",
) -> str:
    index_dir = os.path.join(repo_path, "metadata", group, artifact)
    os.makedirs(index_dir, exist_ok=True)
    index_path = os.path.join(index_dir, "index.json")
    with open(index_path, "w", encoding="utf-8") as file:
        json.dump(entries, file)
    return index_path


def _write_file(path: str, content: str = "") -> None:
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as file:
        file.write(content)


class LibraryUpdateTargetSplitTests(unittest.TestCase):
    def test_new_version_clones_closest_same_major_minor_support(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            _write_index(repo, [
                {
                    "metadata-version": "1.1.0",
                    "tested-versions": ["1.1.0"],
                },
                {
                    "metadata-version": "1.2.3",
                    "source-code-url": "https://example.test/demo-1.2.3-sources.jar",
                    "tested-versions": ["1.2.3"],
                    "allowed-packages": ["org.example"],
                },
                {
                    "latest": True,
                    "metadata-version": "2.0.0",
                    "tested-versions": ["2.0.0"],
                },
            ])
            _write_file(
                os.path.join(repo, "metadata", "org.example", "demo", "1.1.0", "reachability-metadata.json"),
                '{"reflection":[{"type":"org.example.Legacy"}]}\n',
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "1.1.0", "build.gradle"),
                "implementation 'org.example:demo:1.1.0'\n",
            )
            _write_file(
                os.path.join(repo, "metadata", "org.example", "demo", "1.2.3", "reachability-metadata.json"),
                '{"reflection":[{"type":"org.example.Demo123"}]}\n',
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "1.2.3", "build.gradle"),
                "implementation 'org.example:demo:1.2.3'\n",
            )
            _write_file(
                os.path.join(repo, "metadata", "org.example", "demo", "2.0.0", "reachability-metadata.json"),
                '{"reflection":[{"type":"org.example.Future"}]}\n',
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "2.0.0", "build.gradle"),
                "implementation 'org.example:demo:2.0.0'\n",
            )

            target = prepare_library_update_target(repo, "org.example", "demo", "1.2.4")

            self.assertEqual(target.match_type, MATCH_NEW_VERSION)
            with open(
                    os.path.join(repo, "tests", "src", "org.example", "demo", "1.2.4", "build.gradle"),
                    "r",
                    encoding="utf-8",
            ) as file:
                self.assertIn("org.example:demo:1.2.4", file.read())
            with open(os.path.join(repo, "metadata", "org.example", "demo", "index.json"), encoding="utf-8") as file:
                entries = json.load(file)
            new_entry = [entry for entry in entries if entry.get("metadata-version") == "1.2.4"][0]
            self.assertEqual(new_entry["tested-versions"], ["1.2.4"])
            self.assertEqual(new_entry["source-code-url"], "https://example.test/demo-1.2.4-sources.jar")
            self.assertEqual(new_entry["allowed-packages"], ["org.example"])
            self.assertNotIn("latest", new_entry)
            self.assertTrue([entry for entry in entries if entry.get("metadata-version") == "2.0.0"][0]["latest"])

    def test_cloned_support_rewrites_versions_only_in_safe_contexts(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            _write_index(repo, [
                {
                    "latest": True,
                    "metadata-version": "1.0",
                    "source-code-url": "https://example.test/demo-1.0-sources.jar",
                    "tested-versions": ["1.0"],
                }
            ])
            _write_file(
                os.path.join(repo, "metadata", "org.example", "demo", "1.0", "reachability-metadata.json"),
                '{"version":"1.0","next":"1.0.1","text":"Java 1.0"}\n',
            )
            _write_file(
                os.path.join(repo, "metadata", "org.example", "demo", "1.0", "README.md"),
                "Java 1.0 is unrelated documentation.\n",
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "1.0", "build.gradle"),
                "implementation 'org.example:demo:1.0'\nimplementation 'org.other:dep:1.0'\n",
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "1.0", "gradle.properties"),
                "library.coordinates = org.example:demo:1.0\n"
                "library.version = 1.0\n"
                "metadata.dir = org.example/demo/1.0/\n"
            )
            _write_file(
                os.path.join(
                    repo,
                    "tests",
                    "src",
                    "org.example",
                    "demo",
                    "1.0",
                    "src",
                    "test",
                    "java",
                    "DemoTest.java",
                ),
                'class DemoTest { String text = "1.0.1"; }\n',
            )

            prepare_library_update_target(repo, "org.example", "demo", "1.1")

            with open(
                    os.path.join(repo, "metadata", "org.example", "demo", "1.1", "reachability-metadata.json"),
                    encoding="utf-8",
            ) as file:
                metadata = file.read()
            self.assertIn('"version":"1.1"', metadata)
            self.assertIn('"next":"1.0.1"', metadata)
            with open(
                    os.path.join(repo, "metadata", "org.example", "demo", "1.1", "README.md"),
                    encoding="utf-8",
            ) as file:
                self.assertEqual(file.read(), "Java 1.0 is unrelated documentation.\n")
            with open(
                    os.path.join(repo, "tests", "src", "org.example", "demo", "1.1", "build.gradle"),
                    encoding="utf-8",
            ) as file:
                build_file = file.read()
            self.assertIn("implementation 'org.example:demo:1.1'", build_file)
            self.assertIn("implementation 'org.other:dep:1.0'", build_file)
            with open(
                    os.path.join(repo, "tests", "src", "org.example", "demo", "1.1", "gradle.properties"),
                    encoding="utf-8",
            ) as file:
                gradle_properties = file.read()
            self.assertIn("library.coordinates = org.example:demo:1.1", gradle_properties)
            self.assertIn("library.version = 1.1", gradle_properties)
            self.assertIn("metadata.dir = org.example/demo/1.1/", gradle_properties)
            with open(
                    os.path.join(
                        repo,
                        "tests",
                        "src",
                        "org.example",
                        "demo",
                        "1.1",
                        "src",
                        "test",
                        "java",
                        "DemoTest.java",
                    ),
                    encoding="utf-8",
            ) as file:
                self.assertIn('"1.0.1"', file.read())

    def test_library_update_splits_shared_tested_version_target(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            _write_index(repo, [
                {
                    "latest": True,
                    "metadata-version": "1.0.0",
                    "source-code-url": "https://example.test/demo-$version$-sources.jar",
                    "tested-versions": ["0.9.9", "1.0.0", "1.0.1", "1.0.2"],
                    "allowed-packages": ["org.example"],
                }
            ])
            _write_file(
                os.path.join(repo, "metadata", "org.example", "demo", "1.0.0", "reachability-metadata.json"),
                '{"reflection":[{"type":"org.example.Demo"}]}\n',
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "1.0.0", "build.gradle"),
                "implementation 'org.example:demo:1.0.0'\n",
            )

            target = prepare_library_update_target(
                repo,
                "org.example",
                "demo",
                "1.0.1",
            )

            self.assertEqual(target.match_type, MATCH_NEW_VERSION)
            self.assertEqual(target.resolved_metadata_version, "1.0.1")
            self.assertEqual(target.resolved_test_version, "1.0.1")
            with open(os.path.join(repo, "metadata", "org.example", "demo", "index.json"), encoding="utf-8") as file:
                entries = json.load(file)
            baseline_entry = [entry for entry in entries if entry.get("metadata-version") == "1.0.0"][0]
            new_entry = [entry for entry in entries if entry.get("metadata-version") == "1.0.1"][0]
            self.assertEqual(baseline_entry["tested-versions"], ["0.9.9", "1.0.0"])
            self.assertNotIn("latest", baseline_entry)
            self.assertTrue(new_entry["latest"])
            self.assertEqual(new_entry["tested-versions"], ["1.0.1", "1.0.2"])
            self.assertEqual(new_entry["source-code-url"], "https://example.test/demo-$version$-sources.jar")
            with open(
                    os.path.join(repo, "tests", "src", "org.example", "demo", "1.0.1", "build.gradle"),
                    "r",
                    encoding="utf-8",
            ) as file:
                self.assertIn("org.example:demo:1.0.1", file.read())

    def test_library_update_splits_shared_test_version_target(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            _write_index(repo, [
                {
                    "latest": True,
                    "metadata-version": "3.22.0",
                    "test-version": "3.19.0",
                    "source-code-url": "https://example.test/demo-$version$-sources.jar",
                    "tested-versions": ["3.22.0", "3.23.0"],
                    "allowed-packages": ["org.example"],
                }
            ])
            _write_file(
                os.path.join(repo, "metadata", "org.example", "demo", "3.22.0", "reachability-metadata.json"),
                '{"reflection":[{"type":"org.example.Demo"}]}\n',
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "3.19.0", "build.gradle"),
                'String libraryVersion = "3.19.0"\nimplementation "org.example:demo:$libraryVersion"\n',
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "3.19.0", "gradle.properties"),
                "library.coordinates = org.example:demo:3.19.0\n"
                "library.version = 3.19.0\n"
                "metadata.dir = org.example/demo/3.22.0/\n",
            )

            target = prepare_library_update_target(repo, "org.example", "demo", "3.22.0")

            self.assertEqual(target.match_type, MATCH_NEW_VERSION)
            self.assertEqual(target.resolved_metadata_version, "3.22.0")
            self.assertEqual(target.resolved_test_version, "3.22.0")
            self.assertTrue(os.path.isdir(os.path.join(repo, "tests", "src", "org.example", "demo", "3.22.0")))
            with open(
                    os.path.join(repo, "tests", "src", "org.example", "demo", "3.22.0", "gradle.properties"),
                    encoding="utf-8",
            ) as file:
                gradle_properties = file.read()
            self.assertIn("library.coordinates = org.example:demo:3.22.0", gradle_properties)
            self.assertIn("library.version = 3.22.0", gradle_properties)
            self.assertIn("metadata.dir = org.example/demo/3.22.0/", gradle_properties)
            with open(os.path.join(repo, "metadata", "org.example", "demo", "index.json"), encoding="utf-8") as file:
                entries = json.load(file)
            matching_entries = [entry for entry in entries if entry.get("metadata-version") == "3.22.0"]
            self.assertEqual(len(matching_entries), 1)
            self.assertTrue(matching_entries[0]["latest"])
            self.assertNotIn("test-version", matching_entries[0])
            self.assertEqual(matching_entries[0]["tested-versions"], ["3.22.0", "3.23.0"])

    def test_library_update_splits_default_for_target(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            _write_index(repo, [
                {
                    "latest": True,
                    "metadata-version": "1.0.0",
                    "default-for": "1\\.0\\..*",
                    "source-code-url": "https://example.test/demo-$version$-sources.jar",
                    "tested-versions": ["1.0.0"],
                    "allowed-packages": ["org.example"],
                }
            ])
            _write_file(
                os.path.join(repo, "metadata", "org.example", "demo", "1.0.0", "reachability-metadata.json"),
                '{"reflection":[{"type":"org.example.Demo"}]}\n',
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "1.0.0", "build.gradle"),
                "implementation 'org.example:demo:1.0.0'\n",
            )

            target = prepare_library_update_target(repo, "org.example", "demo", "1.0.2")

            self.assertEqual(target.match_type, MATCH_NEW_VERSION)
            self.assertEqual(target.resolved_metadata_version, "1.0.2")
            self.assertEqual(target.resolved_test_version, "1.0.2")
            with open(os.path.join(repo, "metadata", "org.example", "demo", "index.json"), encoding="utf-8") as file:
                entries = json.load(file)
            baseline_entry = [entry for entry in entries if entry.get("metadata-version") == "1.0.0"][0]
            new_entry = [entry for entry in entries if entry.get("metadata-version") == "1.0.2"][0]
            self.assertEqual(baseline_entry["tested-versions"], ["1.0.0"])
            self.assertNotIn("latest", baseline_entry)
            self.assertNotIn("default-for", new_entry)
            self.assertTrue(new_entry["latest"])
            self.assertEqual(new_entry["tested-versions"], ["1.0.2"])

    def test_library_update_split_keeps_older_qualifier_versions_on_baseline(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            _write_index(repo, [
                {
                    "latest": True,
                    "metadata-version": "1.0.0",
                    "source-code-url": "https://example.test/demo-$version$-sources.jar",
                    "tested-versions": ["1.0.1-RC1", "1.0.1", "1.0.2"],
                }
            ])
            _write_file(
                os.path.join(repo, "metadata", "org.example", "demo", "1.0.0", "reachability-metadata.json"),
                '{"reflection":[{"type":"org.example.Demo"}]}\n',
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "1.0.0", "build.gradle"),
                "implementation 'org.example:demo:1.0.0'\n",
            )

            prepare_library_update_target(repo, "org.example", "demo", "1.0.1")

            with open(os.path.join(repo, "metadata", "org.example", "demo", "index.json"), encoding="utf-8") as file:
                entries = json.load(file)
            baseline_entry = [entry for entry in entries if entry.get("metadata-version") == "1.0.0"][0]
            new_entry = [entry for entry in entries if entry.get("metadata-version") == "1.0.1"][0]
            self.assertEqual(baseline_entry["tested-versions"], ["1.0.1-RC1"])
            self.assertEqual(new_entry["tested-versions"], ["1.0.1", "1.0.2"])

    def test_library_update_split_moves_equivalent_numeric_versions_to_new_entry(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            _write_index(repo, [
                {
                    "latest": True,
                    "metadata-version": "1.0.0",
                    "source-code-url": "https://example.test/demo-$version$-sources.jar",
                    "tested-versions": ["1.0.1-RC1", "1.0.1.0", "1.0.2"],
                }
            ])
            _write_file(
                os.path.join(repo, "metadata", "org.example", "demo", "1.0.0", "reachability-metadata.json"),
                '{"reflection":[{"type":"org.example.Demo"}]}\n',
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "1.0.0", "build.gradle"),
                "implementation 'org.example:demo:1.0.0'\n",
            )

            prepare_library_update_target(repo, "org.example", "demo", "1.0.1")

            with open(os.path.join(repo, "metadata", "org.example", "demo", "index.json"), encoding="utf-8") as file:
                entries = json.load(file)
            baseline_entry = [entry for entry in entries if entry.get("metadata-version") == "1.0.0"][0]
            new_entry = [entry for entry in entries if entry.get("metadata-version") == "1.0.1"][0]
            self.assertEqual(baseline_entry["tested-versions"], ["1.0.1-RC1"])
            self.assertEqual(new_entry["tested-versions"], ["1.0.1", "1.0.1.0", "1.0.2"])

    def test_new_version_split_moves_later_tested_versions_to_new_entry(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            _write_index(repo, [
                {
                    "latest": True,
                    "metadata-version": "12.3.0",
                    "source-code-url": "https://example.test/demo-$version$-sources.jar",
                    "tested-versions": ["12.3.0", "12.4.0", "12.6.0", "12.6.1"],
                    "allowed-packages": ["org.example"],
                }
            ])
            _write_file(
                os.path.join(repo, "metadata", "org.example", "demo", "12.3.0", "reachability-metadata.json"),
                '{"reflection":[{"type":"org.example.Demo"}]}\n',
            )
            _write_file(
                os.path.join(repo, "tests", "src", "org.example", "demo", "12.3.0", "build.gradle"),
                "implementation 'org.example:demo:12.3.0'\n",
            )

            prepare_library_update_target(repo, "org.example", "demo", "12.5.0")

            with open(os.path.join(repo, "metadata", "org.example", "demo", "index.json"), encoding="utf-8") as file:
                entries = json.load(file)
            baseline_entry = [entry for entry in entries if entry.get("metadata-version") == "12.3.0"][0]
            new_entry = [entry for entry in entries if entry.get("metadata-version") == "12.5.0"][0]
            self.assertEqual(baseline_entry["tested-versions"], ["12.3.0", "12.4.0"])
            self.assertEqual(new_entry["tested-versions"], ["12.5.0", "12.6.0", "12.6.1"])

    def test_scaffold_failure_reports_target_coordinate(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            _write_index(repo, [])

            failed_result = subprocess.CompletedProcess(["./gradlew"], 17)
            with patch(
                    "ai_workflows.drivers.library_update_preparation.run_logged_command",
                    return_value=failed_result,
            ):
                with self.assertRaisesRegex(
                        RuntimeError,
                        "Failed to scaffold library-update target org.example:demo:9.9.9",
                ):
                    prepare_library_update_target(repo, "org.example", "demo", "9.9.9")

    def test_failure_reset_restores_generated_target_files(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            _write_index(repo, [
                {
                    "metadata-version": "1.0.0",
                    "tested-versions": ["1.0.0"],
                }
            ])
            target = resolve_library_update_target(repo, "org.example", "demo", "1.0.0")
            generated_test = os.path.join(target.test_dir, "src", "test", "java", "GeneratedTest.java")
            generated_metadata = os.path.join(target.metadata_dir, "reachability-metadata.json")
            generated_stats = os.path.join(repo, "stats", "org.example", "demo", "1.0.0", "stats.json")
            _write_file(generated_test, "class GeneratedTest {}\n")
            _write_file(generated_metadata, '{"reflection":[{"type":"org.example.Generated"}]}\n')
            _write_file(generated_stats, '{"versions":[{"version":"1.0.0"}]}\n')

            def fake_run(command, **kwargs):  # type: ignore[no-untyped-def]
                if command[:3] == ["git", "reset", "--hard"]:
                    os.remove(generated_test)
                    os.remove(generated_metadata)
                    os.remove(generated_stats)
                return subprocess.CompletedProcess(command, 0)

            with patch("utility_scripts.worktree_reset.subprocess.run", side_effect=fake_run), \
                    patch(
                        "utility_scripts.worktree_reset.subprocess.check_output",
                        return_value="checkpoint\n",
                    ):
                ending_commit = reset_failed_library_update_worktree(repo, "checkpoint", target)

            self.assertEqual(ending_commit, "checkpoint")
            self.assertTrue(os.path.isfile(generated_test))
            self.assertTrue(os.path.isfile(generated_metadata))
            self.assertTrue(os.path.isfile(generated_stats))


if __name__ == "__main__":
    unittest.main()
