import json
import os
import tempfile
import unittest
from unittest.mock import patch

from ai_workflows.add_new_library_support import (
    ExistingTestsPreparationError,
    prepare_missing_version_from_existing_tests,
)


class AddNewLibrarySupportPreparationTests(unittest.TestCase):
    def test_missing_version_is_added_to_existing_tests_without_scaffold(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            self._write_index(repo)
            os.makedirs(os.path.join(repo, "tests", "src", "org.example", "demo", "1.0.0"), exist_ok=True)

            def add_tested_version(library: str, last_supported_version: str) -> None:
                self.assertEqual(library, "org.example:demo:1.1.0")
                self.assertEqual(last_supported_version, "1.0.0")
                self._write_index(repo, tested_versions=["1.0.0", "1.1.0"])

            with patch(
                    "ai_workflows.add_new_library_support.run_add_tested_version",
                    side_effect=add_tested_version,
            ) as add_tested:
                prepared = prepare_missing_version_from_existing_tests(repo, "org.example", "demo", "1.1.0")

            self.assertTrue(prepared)
            add_tested.assert_called_once()

    def test_missing_version_fails_when_no_existing_tests_can_seed_it(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            self._write_index(repo)

            with self.assertRaises(ExistingTestsPreparationError):
                prepare_missing_version_from_existing_tests(repo, "org.example", "demo", "1.1.0")

    def test_new_artifact_returns_false_so_new_library_flow_can_scaffold(self) -> None:
        with tempfile.TemporaryDirectory() as repo:
            prepared = prepare_missing_version_from_existing_tests(repo, "org.example", "demo", "1.1.0")

        self.assertFalse(prepared)

    def _write_index(self, repo: str, tested_versions: list[str] | None = None) -> None:
        index_dir = os.path.join(repo, "metadata", "org.example", "demo")
        os.makedirs(index_dir, exist_ok=True)
        with open(os.path.join(index_dir, "index.json"), "w", encoding="utf-8") as index_file:
            json.dump([
                {
                    "latest": True,
                    "metadata-version": "1.0.0",
                    "tested-versions": tested_versions or ["1.0.0"],
                }
            ], index_file)


if __name__ == "__main__":
    unittest.main()
