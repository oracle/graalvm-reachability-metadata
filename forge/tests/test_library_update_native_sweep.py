# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest
from typing import Any
from unittest.mock import MagicMock, patch

from utility_scripts.library_update_native_sweep import (
    NATIVE_SWEEP_METRICS_KEY,
    run_library_update_native_sweep,
)
from utility_scripts.metrics_writer import read_pending_metrics, write_pending_metrics
from utility_scripts.native_test_verification import STATUS_FAILED, STATUS_PASSED

MODULE = "utility_scripts.library_update_native_sweep"
GROUP = "org.example"
ARTIFACT = "demo"


class LibraryUpdateNativeSweepTests(unittest.TestCase):
    """Native failures on the shared suite. §FS-library-update-tested-version-split.2"""

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.repo_path = os.path.join(self.temp_dir.name, "repo")
        self.metrics_path = os.path.join(self.temp_dir.name, "metrics")
        os.makedirs(self.metrics_path)
        write_pending_metrics(self.metrics_path, {})
        self.metadata_root = os.path.join(self.repo_path, "metadata", GROUP, ARTIFACT)
        os.makedirs(os.path.join(self.metadata_root, "1.0"))
        with open(os.path.join(self.metadata_root, "1.0", "reachability-metadata.json"), "w", encoding="utf-8") as f:
            f.write('{"reflection": []}\n')
        self.sweeps: list[tuple[str, list[str], str]] = []
        self.repaired: set[str] = set()

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _write_index(self, entries: list[dict[str, Any]]) -> None:
        with open(os.path.join(self.metadata_root, "index.json"), "w", encoding="utf-8") as index_file:
            json.dump(entries, index_file)

    def _read_index(self) -> list[dict[str, Any]]:
        with open(os.path.join(self.metadata_root, "index.json"), encoding="utf-8") as index_file:
            return json.load(index_file)

    def _run(
            self,
            failing_version: str,
            gate_status: str = STATUS_PASSED,
    ) -> tuple[dict[str, Any] | None, MagicMock, MagicMock, MagicMock]:
        def sweep(repo_path: str, coordinates: str, versions: list[str], gradle_task: str) -> dict[str, Any]:
            del repo_path
            self.sweeps.append((coordinates, versions, gradle_task))
            if failing_version in versions and failing_version not in self.repaired:
                return {
                    "commands": [],
                    "failed_version": failing_version,
                    "failed_index": versions.index(failing_version),
                }
            return {"commands": [], "failed_version": None, "failed_index": None}

        def gate(**kwargs: Any) -> MagicMock:
            self.repaired.add(kwargs["env"]["GVM_TCK_LV"])
            return MagicMock(status=gate_status, failure_detail="still failing", failure_log_path="gate.log")

        with (
            patch(f"{MODULE}.has_changed_tests", return_value=True),
            patch(f"{MODULE}.run_tested_version_sweep", side_effect=sweep),
            patch(f"{MODULE}.verify_native_test_passes", side_effect=gate) as gate_mock,
            patch(f"{MODULE}.generate_library_stats") as stats,
            patch(f"{MODULE}.stage_and_commit_common") as commit,
        ):
            record = run_library_update_native_sweep(
                repo_path=self.repo_path,
                coordinates=f"{GROUP}:{ARTIFACT}:1.0",
                base_ref="base-ref",
                metrics_repo_path=self.metrics_path,
            )
        return record, gate_mock, stats, commit

    def test_failure_on_a_consumer_own_version_adds_metadata_to_that_entry(self) -> None:
        entries = [
            {"metadata-version": "1.0", "tested-versions": ["1.0"]},
            {"metadata-version": "2.0", "test-version": "1.0", "tested-versions": ["2.0", "2.1"], "latest": True},
        ]
        self._write_index(entries)

        record, gate, stats, commit = self._run("2.0")

        self.assertEqual(entries, self._read_index())
        gate.assert_called_once()
        self.assertEqual(f"{GROUP}:{ARTIFACT}:2.0", gate.call_args.kwargs["coordinate"])
        self.assertEqual("2.0", gate.call_args.kwargs["env"]["GVM_TCK_LV"])
        stats.assert_called_once_with(self.repo_path, f"{GROUP}:{ARTIFACT}:2.0", "library-update-native-sweep")
        self.assertEqual(
            [
                (f"{GROUP}:{ARTIFACT}:1.0", ["1.0"], "test"),
                (f"{GROUP}:{ARTIFACT}:2.0", ["2.0", "2.1"], "test"),
                (f"{GROUP}:{ARTIFACT}:2.0", ["2.1"], "test"),
            ],
            self.sweeps,
        )
        assert record is not None
        self.assertEqual(
            [{"kind": "metadata", "metadata_version": "2.0", "failed_version": "2.0"}],
            record["repairs"],
        )
        self.assertEqual(record, read_pending_metrics(self.metrics_path)[NATIVE_SWEEP_METRICS_KEY])
        self.assertIn(os.path.join("metadata", GROUP, ARTIFACT, "2.0"), commit.call_args.args[0])
        self.assertIn(os.path.join("stats", GROUP, ARTIFACT, "2.0"), commit.call_args.args[0])

    def test_failure_on_a_later_tested_version_splits_the_entry(self) -> None:
        self._write_index([
            {
                "metadata-version": "1.0",
                "tested-versions": ["1.0", "1.1", "1.2"],
                "allowed-packages": ["org.example"],
                "latest": True,
                "auto-update": True,
            },
        ])

        record, gate, stats, commit = self._run("1.1")

        self.assertEqual(
            [
                {
                    "metadata-version": "1.0",
                    "tested-versions": ["1.0"],
                    "allowed-packages": ["org.example"],
                },
                {
                    "metadata-version": "1.1",
                    "test-version": "1.0",
                    "tested-versions": ["1.1", "1.2"],
                    "allowed-packages": ["org.example"],
                    "latest": True,
                    "auto-update": True,
                },
            ],
            self._read_index(),
        )
        with open(os.path.join(self.metadata_root, "1.1", "reachability-metadata.json"), encoding="utf-8") as f:
            self.assertEqual('{"reflection": []}\n', f.read())
        self.assertEqual(f"{GROUP}:{ARTIFACT}:1.1", gate.call_args.kwargs["coordinate"])
        stats.assert_called_once_with(self.repo_path, f"{GROUP}:{ARTIFACT}:1.1", "library-update-native-sweep")
        self.assertEqual(
            [
                (f"{GROUP}:{ARTIFACT}:1.0", ["1.0", "1.1", "1.2"], "test"),
                (f"{GROUP}:{ARTIFACT}:1.1", ["1.2"], "test"),
            ],
            self.sweeps,
        )
        assert record is not None
        self.assertEqual("entry_split", record["repairs"][0]["kind"])
        self.assertEqual(["1.1", "1.2"], record["repairs"][0]["moved_versions"])
        self.assertIn(os.path.join("metadata", GROUP, ARTIFACT, "1.1"), commit.call_args.args[0])

    def test_unrepairable_version_fails_publication(self) -> None:
        self._write_index([{"metadata-version": "1.0", "tested-versions": ["1.0"]}])

        with self.assertRaisesRegex(RuntimeError, "could not repair"):
            self._run("1.0", gate_status=STATUS_FAILED)


if __name__ == "__main__":
    unittest.main()
