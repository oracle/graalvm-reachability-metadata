# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import unittest

import yaml


class CodeCoverageRheiTemplateTests(unittest.TestCase):

    def test_reenterable_fix_states_have_visit_scoped_outputs(self) -> None:
        forge_root: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        states_paths: tuple[str, ...] = (
            os.path.join(
                forge_root,
                ".agents",
                "rhei",
                "templates",
                "code-coverage-improvement",
                "states.yaml",
            ),
            os.path.join(
                forge_root,
                "examples",
                "code-coverage-improvement-example",
                "states.yaml",
            ),
        )

        for states_path in states_paths:
            with open(states_path, encoding="utf-8") as states_file:
                source: str = states_file.read()
            source = source.replace("{{measure_visits}}", "16")
            source = source.replace("{{coverage_iterations}}", "5")
            source = source.replace("{{fix_passes}}", "2")
            machine: dict = yaml.safe_load(source)

            for state_name in ("api-fix", "deep-fix", "finalize-fix"):
                with self.subTest(path=states_path, state=state_name):
                    state: dict = machine["states"][state_name]
                    self.assertGreater(state["visits"], 1)
                    self.assertTrue(state["outputs"])
                    for output in state["outputs"]:
                        self.assertIn("{visit_count}", output["path"])


if __name__ == "__main__":
    unittest.main()
