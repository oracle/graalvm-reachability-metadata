# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import subprocess
import tempfile
import unittest

from git_scripts import pr_publication
from utility_scripts.continuation_marker import CONTINUATION_MARKER_FILENAME
from utility_scripts.metrics_writer import PENDING_METRICS_FILENAME


def _git(repo_path: str, *args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=repo_path,
        check=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
    )
    return result.stdout


class PrPublicationTests(unittest.TestCase):
    def test_preservation_cleanup_keeps_pending_metrics_as_local_input(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            _git(repo_path, "init")
            forge_path = os.path.join(repo_path, "forge")
            logs_path = os.path.join(forge_path, "human-intervention-logs")
            os.makedirs(logs_path)

            pending_path = os.path.join(forge_path, PENDING_METRICS_FILENAME)
            marker_path = os.path.join(forge_path, CONTINUATION_MARKER_FILENAME)
            log_path = os.path.join(logs_path, "run.log")
            with open(pending_path, "w", encoding="utf-8") as pending_file:
                pending_file.write('{"status":"success"}\n')
            with open(marker_path, "w", encoding="utf-8") as marker_file:
                marker_file.write("{}\n")
            with open(log_path, "w", encoding="utf-8") as log_file:
                log_file.write("log\n")

            _git(
                repo_path,
                "add",
                "-f",
                f"forge/{PENDING_METRICS_FILENAME}",
                f"forge/{CONTINUATION_MARKER_FILENAME}",
                "forge/human-intervention-logs/run.log",
            )

            pr_publication._remove_preservation_only_files(repo_path)

            self.assertTrue(os.path.isfile(pending_path))
            with open(pending_path, "r", encoding="utf-8") as pending_file:
                self.assertEqual(pending_file.read(), '{"status":"success"}\n')
            self.assertFalse(os.path.exists(marker_path))
            self.assertFalse(os.path.exists(logs_path))
            tracked_paths = _git(repo_path, "ls-files").splitlines()
            self.assertNotIn(f"forge/{PENDING_METRICS_FILENAME}", tracked_paths)
            self.assertNotIn(f"forge/{CONTINUATION_MARKER_FILENAME}", tracked_paths)
            self.assertNotIn("forge/human-intervention-logs/run.log", tracked_paths)


if __name__ == "__main__":
    unittest.main()
