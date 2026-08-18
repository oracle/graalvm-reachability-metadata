# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import unittest
from typing import Any
from unittest.mock import MagicMock, patch

from utility_scripts.library_update_alias_split import _apply_alias_split


class LibraryUpdateAliasSplitTests(unittest.TestCase):
    def test_apply_alias_split_generates_successor_stats_after_index_update(self) -> None:
        group = "org.example"
        artifact = "demo"
        failed_version = "2.0"
        successor_coordinates = f"{group}:{artifact}:{failed_version}"
        entries: list[dict[str, Any]] = [
            {
                "metadata-version": "1.0",
                "tested-versions": ["1.0", failed_version],
                "latest": True,
            },
        ]
        original_entry: dict[str, Any] = {
            "metadata-version": "1.0",
            "tested-versions": ["1.0", failed_version],
            "latest": True,
        }
        gradle_environment = {"JAVA_HOME": "/graalvm"}
        operations: list[str] = []

        def record_index_write(*args: Any, **kwargs: Any) -> None:
            del args, kwargs
            operations.append("index")

        def record_stats_generation(*args: Any, **kwargs: Any) -> MagicMock:
            del args, kwargs
            operations.append("stats")
            return MagicMock(returncode=0)

        with (
            patch("utility_scripts.library_update_alias_split._copy_tree_from_commit"),
            patch(
                "utility_scripts.library_update_alias_split.load_index_entries",
                return_value=entries,
            ),
            patch(
                "utility_scripts.library_update_alias_split._write_index_entries",
                side_effect=record_index_write,
            ),
            patch(
                "utility_scripts.library_update_alias_split.gradle_command_environment",
                return_value=gradle_environment,
            ),
            patch(
                "utility_scripts.library_update_alias_split.subprocess.run",
                side_effect=record_stats_generation,
            ) as run_command,
        ):
            _apply_alias_split(
                repo_path="/repo",
                base_ref="base-ref",
                group=group,
                artifact=artifact,
                requested_coordinates=f"{group}:{artifact}:1.0",
                target_metadata_version="1.0",
                original_entry=original_entry,
                tested_versions=["1.0", failed_version],
                failed_index=1,
                sweep={"commands": []},
            )

        self.assertEqual(["index", "stats"], operations)
        run_command.assert_called_once_with(
            [
                "./gradlew",
                "generateLibraryStats",
                f"-Pcoordinates={successor_coordinates}",
            ],
            cwd="/repo",
            env=gradle_environment,
            check=True,
        )


if __name__ == "__main__":
    unittest.main()
