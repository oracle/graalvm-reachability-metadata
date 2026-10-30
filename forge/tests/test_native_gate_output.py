# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Unit tests for what the native-test gate prints in concise mode.

Drives the gate's outer loop through the shared ``subprocess.run`` harness and
checks the one-line progress and failure messages a concise run shows.
"""

from __future__ import annotations

import io
import os
import sys
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

# Tests run from the forge/ directory in CI; make the package imports work
# whether the test is invoked via pytest or `python -m unittest`.
_FORGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_FORGE_ROOT))

from utility_scripts import native_test_verification as ntv  # noqa: E402
from utility_scripts.run_location import (  # noqa: E402
    PHASE_EXPLORE,
    STEP_NATIVE_TRACE_GATE,
    run_step,
)

from tests.native_gate_support import (  # noqa: E402
    GATE_SUBPROCESS_RUN,
    GateHarness,
)


class GateOutputTests(GateHarness):
    """The concise messages of a gate run that routes to the agent fix."""

    def test_concise_output_names_the_native_trace_agent_fix(self) -> None:
        fake, _calls = self._fake_run_factory([1])
        output = io.StringIO()
        with patch.dict(
                os.environ,
                {"FORGE_VERBOSE": "0", "FORGE_DEBUG_LOGGING": "0"},
        ), patch(
                GATE_SUBPROCESS_RUN,
                side_effect=fake,
        ), patch(
                "utility_scripts.native_test_verification.run_native_test_fix",
                return_value=(0, "/tmp/codex.log", False, None),
        ), redirect_stdout(output):
            with run_step(
                    PHASE_EXPLORE,
                    STEP_NATIVE_TRACE_GATE,
                    operand="g:a:1.0",
            ):
                result = ntv.verify_native_test_passes(
                    reachability_repo_path=self.repo,
                    coordinate="g:a:1.0",
                    output_dir=self.output_dir,
                    max_iterations=5,
                )

        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        printed = output.getvalue()
        self.assertIn(
            "[explore] Running native-trace agent fix for g:a:1.0:",
            printed,
        )
        self.assertIn(
            "[explore] Native-trace agent fix completed for g:a:1.0 (3/3)",
            printed,
        )
        self.assertNotIn("[native-test-verify]", printed)
        self.assertNotIn("./gradlew", printed)

    def test_concise_agent_fix_failure_keeps_error_and_log(self) -> None:
        fake, _calls = self._fake_run_factory([1])
        output = io.StringIO()
        with patch.dict(
                os.environ,
                {"FORGE_VERBOSE": "0", "FORGE_DEBUG_LOGGING": "0"},
        ), patch(
                GATE_SUBPROCESS_RUN,
                side_effect=fake,
        ), patch(
                "utility_scripts.native_test_verification.run_native_test_fix",
                return_value=(2, "/tmp/codex.log", False, "Agent repair failed"),
        ), redirect_stdout(output):
            with run_step(
                    PHASE_EXPLORE,
                    STEP_NATIVE_TRACE_GATE,
                    operand="g:a:1.0",
            ):
                result = ntv.verify_native_test_passes(
                    reachability_repo_path=self.repo,
                    coordinate="g:a:1.0",
                    output_dir=self.output_dir,
                    max_iterations=5,
                )

        self.assertEqual(result.status, ntv.STATUS_FAILED)
        self.assertIn(
            "[explore] Native-trace agent fix failed for g:a:1.0: "
            "Agent repair failed (log: ../../codex.log) (3/3)",
            output.getvalue(),
        )


if __name__ == "__main__":
    unittest.main()
