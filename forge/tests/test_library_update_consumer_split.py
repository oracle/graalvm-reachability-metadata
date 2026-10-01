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

from utility_scripts.library_update_consumer_split import (
    CONSUMER_SPLIT_METRICS_KEY,
    maybe_split_test_version_consumers,
)
from utility_scripts.metrics_writer import read_pending_metrics, write_pending_metrics

MODULE = "utility_scripts.library_update_consumer_split"
GROUP = "org.liquibase"
ARTIFACT = "liquibase-core"


def _sweep_result(versions: list[str], failed_version: str | None) -> dict[str, Any]:
    failed_index = None if failed_version is None else versions.index(failed_version)
    return {
        "commands": [{"version": version} for version in versions],
        "failed_version": failed_version,
        "failed_index": failed_index,
    }


class TestVersionConsumerSplitTests(unittest.TestCase):
    """Regression for PR #10136. §FS-library-update-tested-version-split.1"""

    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.repo_path = os.path.join(self.temp_dir.name, "repo")
        self.metrics_path = os.path.join(self.temp_dir.name, "metrics")
        os.makedirs(os.path.join(self.repo_path, "metadata", GROUP, ARTIFACT))
        os.makedirs(self.metrics_path)
        write_pending_metrics(self.metrics_path, {})
        self._write_index([
            {"metadata-version": "4.17.0", "tested-versions": ["4.17.0"]},
            {"metadata-version": "4.20.0", "test-version": "4.17.0", "tested-versions": ["4.20.0"]},
            {
                "metadata-version": "4.23.0",
                "test-version": "4.17.0",
                "tested-versions": ["4.23.0"],
                "latest": True,
            },
        ])
        self.baseline_restored = False
        self.sweeps: list[tuple[str, list[str], bool]] = []

    def tearDown(self) -> None:
        self.temp_dir.cleanup()

    def _write_index(self, entries: list[dict[str, Any]]) -> None:
        with open(self._index_path(), "w", encoding="utf-8") as index_file:
            json.dump(entries, index_file)

    def _index_path(self) -> str:
        return os.path.join(self.repo_path, "metadata", GROUP, ARTIFACT, "index.json")

    def _read_index(self) -> list[dict[str, Any]]:
        with open(self._index_path(), encoding="utf-8") as index_file:
            return json.load(index_file)

    def _restore_baseline(self, *args: Any) -> None:
        del args
        self.baseline_restored = True

    def _run(self, failing_on_baseline: str | None = None) -> tuple[dict[str, Any] | None, MagicMock, MagicMock]:
        def sweep(repo_path: str, coordinates: str, versions: list[str]) -> dict[str, Any]:
            del repo_path
            self.sweeps.append((coordinates, versions, self.baseline_restored))
            metadata_version = coordinates.rsplit(":", 1)[1]
            if not self.baseline_restored and metadata_version == "4.20.0":
                return _sweep_result(versions, versions[0])
            if self.baseline_restored and metadata_version == failing_on_baseline:
                return _sweep_result(versions, versions[0])
            return _sweep_result(versions, None)

        with (
            patch(f"{MODULE}.has_changed_tests", return_value=True),
            patch(f"{MODULE}.run_tested_version_sweep", side_effect=sweep),
            patch(f"{MODULE}.copy_tree_from_commit", side_effect=self._restore_baseline) as copy_tree,
            patch(f"{MODULE}.stage_and_commit_common") as commit,
        ):
            split = maybe_split_test_version_consumers(
                repo_path=self.repo_path,
                coordinates=f"{GROUP}:{ARTIFACT}:4.17.0",
                base_ref="base-ref",
                metrics_repo_path=self.metrics_path,
            )
        return split, copy_tree, commit

    def test_first_failing_consumer_moves_to_the_baseline_suite_and_later_ones_follow(self) -> None:
        split, copy_tree, commit = self._run()

        copy_tree.assert_called_once_with(
            self.repo_path,
            "base-ref",
            os.path.join("tests", "src", GROUP, ARTIFACT, "4.17.0"),
            os.path.join(self.repo_path, "tests", "src", GROUP, ARTIFACT, "4.20.0"),
        )
        self.assertEqual(
            [
                {"metadata-version": "4.17.0", "tested-versions": ["4.17.0"]},
                {"metadata-version": "4.20.0", "tested-versions": ["4.20.0"]},
                {
                    "metadata-version": "4.23.0",
                    "test-version": "4.20.0",
                    "tested-versions": ["4.23.0"],
                    "latest": True,
                },
            ],
            self._read_index(),
        )
        self.assertEqual(
            [
                (f"{GROUP}:{ARTIFACT}:4.20.0", ["4.20.0"], False),
                (f"{GROUP}:{ARTIFACT}:4.20.0", ["4.20.0"], True),
                (f"{GROUP}:{ARTIFACT}:4.23.0", ["4.23.0"], True),
            ],
            self.sweeps,
        )
        assert split is not None
        self.assertEqual(f"{GROUP}:{ARTIFACT}:4.20.0", split["successor_coordinates"])
        self.assertEqual("4.20.0", split["failed_version"])
        self.assertEqual([], split["passing_consumers"])
        self.assertEqual(["4.23.0"], split["repointed_consumers"])
        self.assertEqual(split, read_pending_metrics(self.metrics_path)[CONSUMER_SPLIT_METRICS_KEY])
        # Consumers keep their metadata and stats: only the index and the restored suite change.
        self.assertEqual(
            [
                os.path.join("metadata", GROUP, ARTIFACT, "index.json"),
                os.path.join("tests", "src", GROUP, ARTIFACT, "4.20.0"),
            ],
            commit.call_args.args[0],
        )

    def test_baseline_failure_fails_publication(self) -> None:
        with self.assertRaisesRegex(RuntimeError, "4.23.0"):
            self._run(failing_on_baseline="4.23.0")
        self.assertNotIn(CONSUMER_SPLIT_METRICS_KEY, read_pending_metrics(self.metrics_path))

    def test_passing_consumers_change_nothing(self) -> None:
        self._write_index([
            {"metadata-version": "4.17.0", "tested-versions": ["4.17.0"]},
            {"metadata-version": "4.23.0", "test-version": "4.17.0", "tested-versions": ["4.23.0"]},
        ])
        before = self._read_index()

        split, copy_tree, commit = self._run()

        self.assertIsNone(split)
        copy_tree.assert_not_called()
        commit.assert_not_called()
        self.assertEqual(before, self._read_index())


if __name__ == "__main__":
    unittest.main()
