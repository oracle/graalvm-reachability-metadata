# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import unittest

import yaml


class CodeCoverageRheiTemplateTests(unittest.TestCase):

    def test_test_project_validation_belongs_to_prepare(self) -> None:
        forge_root: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        states_path: str = os.path.join(
            forge_root,
            ".agents",
            "rhei",
            "templates",
            "code-coverage-improvement",
            "states.yaml",
        )
        with open(states_path, encoding="utf-8") as states_file:
            source: str = states_file.read()
        source = source.replace("{{measure_visits}}", "16")
        source = source.replace("{{coverage_iterations}}", "5")
        source = source.replace("{{fix_passes}}", "2")
        machine: dict = yaml.safe_load(source)

        requirement_program: str = machine["states"]["requirement-test-execute"]["program"]
        prepare_program: str = machine["states"]["prepare-test-project"]["program"]
        self.assertIn("host_requirements.py", requirement_program)
        self.assertNotIn('["gh", "issue", "view"', requirement_program)
        self.assertNotIn('"tests", "src"', requirement_program)
        self.assertIn('"tests", "src"', prepare_program)

        prepare_transition: bool = any(
            transition["from"] == "prepare-test-project"
            and transition["to"] == "execute"
            and transition.get("exit_code") == 0
            for transition in machine["transitions"]
        )
        self.assertTrue(prepare_transition)

        tasks_path: str = os.path.join(
            forge_root,
            ".agents",
            "rhei",
            "templates",
            "code-coverage-improvement",
            "tasks",
            "code-coverage-improvement.md",
        )
        with open(tasks_path, encoding="utf-8") as tasks_file:
            tasks_source: str = tasks_file.read()
        self.assertIn(
            "### Task code-coverage-prepare: Prepare library\n"
            "**State:** prepare-test-project",
            tasks_source,
        )

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
