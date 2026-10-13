# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import os
import re
import unittest

import yaml

from tests.test_code_coverage_rhei_template import _render_numeric_placeholders


class CodeCoveragePromptLayerTests(unittest.TestCase):
    def test_prompt_layers_keep_their_scope(self) -> None:
        """Rhei pastes the index into every agent turn and a task body into every
        agent state of its task, so the index holds only the workflow-wide rules,
        the loop tasks carry no body, and the cover states keep no rule another
        rule already covers.

        §AR-code-coverage-prompts.2
        """
        forge_root: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        roots: tuple[str, ...] = (
            os.path.join(forge_root, ".agents", "rhei", "templates", "code-coverage-improvement"),
            os.path.join(forge_root, "examples", "code-coverage-improvement-example"),
        )
        loop_task: re.Pattern = re.compile(
            r"^### Task code-coverage-(?:api|deep|naive)-coverage: .*\n"
            r"\*\*State:\*\* .*\n\*\*Prior:\*\* .*\n(?P<body>(?:(?!### Task |\{%).*\n)*)",
            re.M,
        )
        dropped_rules: tuple[str, ...] = (
            "Do not weaken metadata-generation tests",
            "Do not measure or claim coverage yourself",
            "Do not write any target state",
        )

        for root in roots:
            with self.subTest(root=root):
                with open(os.path.join(root, "index.rhei.md"), encoding="utf-8") as index_file:
                    index: str = index_file.read()
                self.assertEqual(re.findall(r"^## (.+)$", index, re.M), ["Rules"])

                with open(
                    os.path.join(root, "tasks", "code-coverage-improvement.md"), encoding="utf-8"
                ) as tasks_file:
                    bodies: list[str] = [m["body"] for m in loop_task.finditer(tasks_file.read())]
                self.assertGreaterEqual(len(bodies), 2)
                for body in bodies:
                    self.assertEqual(body.strip(), "")

                with open(os.path.join(root, "states.yaml"), encoding="utf-8") as states_file:
                    states: dict = yaml.safe_load(
                        _render_numeric_placeholders(states_file.read())
                    )["states"]
                for name, state in states.items():
                    instructions: str = " ".join(state.get("instructions", "").split())
                    self.assertNotIn("Artifact paths in this task", instructions, name)
                    if name in ("api-cover", "deep-cover"):
                        for rule in dropped_rules:
                            self.assertNotIn(rule, instructions, name)


if __name__ == "__main__":
    unittest.main()
