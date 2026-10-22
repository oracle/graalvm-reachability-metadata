# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Names that Forge resolves only at run time, after the test suite's own mocks.

The file split in #10088 left callers pointing at names their module no longer
imported; each crashed a live run after hours of agent work (#10099, #10101).
"""

import os
import subprocess
import sys
import unittest

from dispatcher import pr_publication
from utility_scripts import metrics_writer, run_metrics_payloads


class RunTimeNameResolutionTests(unittest.TestCase):
    def test_pr_publication_resolves_sys(self) -> None:
        self.assertIs(pr_publication.sys, sys)

    def test_fresh_interpreter_finds_no_undefined_names(self) -> None:
        # pylint's undefined-name checks catch this class of bug without running Forge;
        # the repository rcfile disables them, so enable exactly those here.
        forge_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        result = subprocess.run(
            [
                sys.executable, "-m", "pylint",
                "--disable=all", "--enable=E0602,E0611,E0401",
                "--score=n", "--recursive=y",
                "ai_workflows", "dispatcher", "git_scripts", "utility_scripts", "forge_metadata.py",
            ],
            cwd=forge_dir,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
