# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import unittest

import yaml

from tests.test_code_coverage_rhei_template import _render_numeric_placeholders
from utility_scripts.code_coverage_session_count import COUNT_STEM
from utility_scripts.code_coverage_stop import PHASE_REPORTS


class CodeCoverageTrajectoryMetricTests(unittest.TestCase):
    def test_each_loop_declares_its_pass_yield(self) -> None:
        """Every coverage loop declares one Rhei metric on the numbered report its
        measure program writes, valued at the covered count the stop rule reads,
        so a trajectory row's delta is that pass's yield; deep dispatch adds one
        count per cover session.

        §AR-code-coverage-trajectory.1 §AR-code-coverage-trajectory.4
        """
        forge_root: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        roots: dict[str, tuple[str, ...]] = {
            os.path.join(
                forge_root, ".agent-grounds", "rhei", "templates", "code-coverage-improvement"
            ): ("api", "deep", "naive"),
            os.path.join(forge_root, "examples", "code-coverage-improvement-example"): (
                "api",
                "deep",
            ),
        }

        for root, phases in roots.items():
            with self.subTest(root=root):
                with open(os.path.join(root, "states.yaml"), encoding="utf-8") as source:
                    machine: dict = yaml.safe_load(_render_numeric_placeholders(source.read()))
                metrics: dict = machine["metrics"]
                self.assertEqual(
                    sorted([*(f"{phase}-coverage" for phase in phases), "deep-session-coverage"]),
                    sorted(metrics),
                )
                for phase in phases:
                    metric: dict = metrics[f"{phase}-coverage"]
                    stem, field = PHASE_REPORTS[phase]
                    measure: str = machine["states"][f"{phase}-measure"]["program"]
                    self.assertEqual([f"{phase}-measure"], metric["measured_by"])
                    self.assertEqual([f"{phase}-cover"], metric["drivers"])
                    self.assertEqual(f"/summary/{field}", metric["pointer"])
                    self.assertEqual("increase", metric["goal"])
                    directory, name = os.path.split(metric["artifact"])
                    self.assertEqual(f"{stem}-{{iteration}}.json", name)
                    self.assertIn(f'Path("{directory}")', measure)
                    self.assertIn(f'f"{stem}-{{iteration}}.json"', measure)

                session: dict = metrics["deep-session-coverage"]
                self.assertEqual(["deep-dispatch"], session["measured_by"])
                self.assertEqual(["deep-cover"], session["drivers"])
                self.assertEqual("/summary/covered", session["pointer"])
                self.assertEqual(
                    f"runtime/code-coverage/discovery/{COUNT_STEM}-{{iteration}}.json",
                    session["artifact"],
                )
                self.assertIn("count_session(", machine["states"]["deep-dispatch"]["program"])


if __name__ == "__main__":
    unittest.main()
