# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import re
import unittest

import yaml


_TEMPLATE_NUMBERS: dict[str, int] = {
    "measure_visits": 16,
    "coverage_iterations": 5,
    "fix_passes": 2,
}


def _render_numeric_placeholders(source: str) -> str:
    """Resolve the numeric template placeholders, including arithmetic on them.

    Visit caps are expressed relative to the budget they gate, so a placeholder
    can be an expression rather than a bare name. Anything that is not numeric
    is left untouched for the YAML parser to read as a plain string.
    """

    def render(match: re.Match) -> str:
        try:
            return str(eval(match.group(1).strip(), {"__builtins__": {}}, _TEMPLATE_NUMBERS))
        except (NameError, SyntaxError, TypeError):
            return match.group(0)

    return re.sub(r"\{\{([^{}]+)\}\}", render, source)


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
        machine: dict = yaml.safe_load(_render_numeric_placeholders(source))

        self.assertNotIn("requirement-test-prepared", machine["states"])
        prepare_program: str = machine["states"]["prepare-test-project"]["program"]
        self.assertIn('["gh", "issue", "view"', prepare_program)
        self.assertIn('"tests", "src"', prepare_program)
        self.assertTrue(any(
            transition["from"] == "prepare-test-project"
            and transition["to"] == "execute"
            and transition.get("exit_code") == 0
            for transition in machine["transitions"]
        ))

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

    def test_measurement_repairs_reuse_the_logical_cover_pass(self) -> None:
        """Both loops must keep retries out of the pass-yield history."""
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

        self.assertEqual(source.count("begin_measurement("), 2)
        self.assertEqual(source.count("complete_measurement("), 4)
        self.assertNotIn(
            'iteration = len(list(validation.glob("api-cover-report-*.json")))',
            source,
        )
        self.assertNotIn(
            'iteration = len(list(discovery.glob("discovery-report-*.json")))',
            source,
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
            machine: dict = yaml.safe_load(_render_numeric_placeholders(source))

            for state_name in ("api-fix", "deep-fix", "finalize-fix"):
                with self.subTest(path=states_path, state=state_name):
                    state: dict = machine["states"][state_name]
                    self.assertGreater(state["visits"], 1)
                    self.assertTrue(state["outputs"])
                    for output in state["outputs"]:
                        self.assertIn("{visit_count}", output["path"])


if __name__ == "__main__":
    unittest.main()
