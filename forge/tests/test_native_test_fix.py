# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Unit tests for the analysis-agent fix the native-test gate routes to.

Covers the reproduce-and-verify prompt the agent receives and how the agent is
run; the gate's routing to it is in ``test_native_test_verification``.
"""

from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import Mock, patch

# Tests run from the forge/ directory in CI; make the package imports work
# whether the test is invoked via pytest or `python -m unittest`.
_FORGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_FORGE_ROOT))

from utility_scripts import native_test_verification as ntv  # noqa: E402


class NativeTestFixPromptTests(unittest.TestCase):

    def test_preserves_the_original_reproduce_and_verify_prompt(self) -> None:
        prompt = ntv._build_native_test_fix_prompt(
            coordinates="g:a:1.0",
            reproduction_command="./gradlew reproduce",
            graalvm_home="/graalvm",
            failure_log_path=None,
        )

        self.assertEqual(
            prompt,
            "\n".join([
                "Reproduce the failure first and read the FULL stack trace, including every `Caused by:`",
                "line, to find the real cause before changing anything.",
                "- If the cause is missing or inactive-condition for metadata, fix that condition.",
                "- If the cause is native-image-unsupported behavior (dynamic class loading, runtime bytecode",
                "  or class definition, runtime lambda definition, URL/plugin/OSGi class-loader assumptions,",
                "  or a class reachable only through a custom class loader), Remove the",
                "  generated test that exercises it, or rewrite it to a native-compatible public-API path that",
                "  still validates metadata.",
                "Re-run the reproduce command after each change and keep running until the test passes.",
                "Do not use any skill for this.",
                "",
                "Use this exact GraalVM for every command; do not switch to another that appears on PATH:",
                "- GRAALVM_HOME=/graalvm",
                "- JAVA_HOME=/graalvm",
                "",
                "Reproduce with:",
                "./gradlew reproduce",
            ]),
        )

    def test_runs_the_central_analysis_agent_without_overrides(self) -> None:
        environment = {"FORGE_ANALYSIS_AGENT": "pi"}
        agent_result = Mock(return_code=0, log_path="/tmp/analysis.log", timed_out=False, failure_message=None)

        with patch(
                "utility_scripts.native_test_verification.analysis_agent_run",
                return_value=agent_result,
        ) as analysis_run:
            result = ntv.run_native_test_fix(
                repo_path="/repo",
                coordinates="g:a:1.0",
                reproduction_command="./gradlew reproduce",
                env=environment,
            )

        self.assertEqual(result, (0, "/tmp/analysis.log", False, None))
        call_kwargs = analysis_run.call_args.kwargs
        self.assertEqual(call_kwargs["environment"], environment)
        self.assertNotIn("model", call_kwargs)
        self.assertNotIn("selection", call_kwargs)


if __name__ == "__main__":
    unittest.main()
