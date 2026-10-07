# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import re
import unittest

import yaml

from utility_scripts.code_coverage_deep_sessions import MAX_SESSIONS_PER_PASS


_TEMPLATE_NUMBERS: dict[str, int] = {
    "measure_visits": 16,
    "coverage_iterations": 5,
    "fix_passes": 2,
    "naive_iterations": 30,
    "naive_measure_visits": 40,
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

    def test_deep_cover_preserves_java_package_visibility(self) -> None:
        """Deep-cover prompts must reach internals through public behavior.

        §AR-code-coverage-improvement.4.2
        """
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
            instructions: str = machine["states"]["deep-cover"]["instructions"]
            normalized_instructions: str = " ".join(instructions.split())

            with self.subTest(path=states_path):
                self.assertIn(
                    "Never bypass Java visibility by declaring a test in a package "
                    "that exists in the tested library.",
                    normalized_instructions,
                )
                self.assertIn("Use a distinct test-only package.", normalized_instructions)
                self.assertIn(
                    "A `public` member on a package-private class is not public "
                    "user-callable API.",
                    normalized_instructions,
                )
                self.assertIn(
                    "If no public user-callable API reaches an internal target, "
                    "leave that target uncovered rather than call it directly.",
                    normalized_instructions,
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

        self.assertEqual(source.count("begin_measurement("), 3)
        self.assertEqual(source.count("complete_measurement("), 6)
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


    def test_finalization_fix_requires_one_agent_free_remeasurement(self) -> None:
        """A finalization repair cannot complete with its pre-repair evidence."""
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
            machine: dict = yaml.safe_load(
                _render_numeric_placeholders(states_file.read())
            )

        transitions: list[dict] = machine["transitions"]
        self.assertEqual(
            {
                transition["to"]
                for transition in transitions
                if transition["from"] == "finalize-fix"
            },
            {"finalize-remeasure"},
        )
        self.assertEqual(
            {
                (transition["exit_code"], transition["to"])
                for transition in transitions
                if transition["from"] == "finalize-remeasure"
            },
            {
                (0, "reviewed-execute"),
                *((code, "human-intervention") for code in range(1, 7)),
            },
        )
        program: str = machine["states"]["finalize-remeasure"]["program"]
        python_source: str = program.split("<<'PY'\n", 1)[1].rsplit("\nPY", 1)[0]
        compile(python_source, "finalize-remeasure", "exec")
        for command in (
                "jacocoCodeCoverageReport",
                "nativeTestPGOSampling",
                "runNativeTestPGO",
                "code_coverage_profile_report.py",
        ):
            self.assertIn(command, program)
        self.assertNotIn("begin_measurement", program)
        self.assertNotIn("deep-cover", program)
        finalization: str = machine["states"]["reviewed-execute"]["program"]
        self.assertIn("runtime/code-coverage/final-measurement", finalization)
        self.assertIn("jacoco.xml", finalization)
        self.assertIn("discovery-report.json", finalization)

    def test_helpers_never_resolve_from_the_checkout_under_test(self) -> None:
        """Every helper resolves from the work path, never from the checkout.

        `repo_checkout` is the tree under test, which a benchmark pins to an
        old commit; only `workPath` tracks the implementation that must measure
        the run. A helper reached through the checkout silently runs pinned
        tooling against current metrics. §FS-code-coverage-benchmarking.1
        """
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
        helper: re.Pattern = re.compile(
            r"\S*(?:\{\{repo_checkout\}\}|\.\./\.\./\.\.)\S*"
            r"(?:utility_scripts|schemas)\S*"
        )

        for states_path in states_paths:
            with open(states_path, encoding="utf-8") as states_file:
                source: str = states_file.read()
            machine: dict = yaml.safe_load(_render_numeric_placeholders(source))

            for name, state in machine["states"].items():
                program: str = state.get("program", "")
                with self.subTest(path=states_path, state=name):
                    self.assertEqual(
                        helper.findall(program),
                        [],
                        f"state '{name}' resolves a helper from the checkout "
                        "under test; resolve it from conversion.json workPath",
                    )

            verify: dict = machine["states"]["finalize-verify"]
            with self.subTest(path=states_path, state="finalize-verify"):
                self.assertIn("schema_validator.py", verify["program"])
                self.assertIn('["workPath"]', verify["program"])
                self.assertIn(
                    "conversion",
                    {declared["name"] for declared in verify["inputs"]},
                )

    def test_programs_routing_into_a_final_state_write_the_terminal_result(self) -> None:
        """A program's exit-zero route into `completed` owns the ticket result.

        Rhei writes no result for a program whose exit code lands on a
        `final: true` state and holds the task there while the file is
        missing, so every such program must write `RHEI_RESULT_PATH` itself or
        delegate to the benchmark runner, which does.
        §AR-code-coverage-improvement.4
        """
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
            states: dict = machine["states"]
            final_states: set[str] = {
                name for name, state in states.items() if state.get("final")
            }
            checked: int = 0

            for transition in machine["transitions"]:
                # `from: "*"` (cancel any task) names no state and has no program.
                program: str = states.get(transition["from"], {}).get("program", "")
                if not program or transition["to"] not in final_states:
                    continue
                checked += 1
                with self.subTest(path=states_path, state=transition["from"]):
                    self.assertTrue(
                        "RHEI_RESULT_PATH" in program
                        or "code_coverage_benchmark.py" in program,
                        f"program state '{transition['from']}' routes into "
                        f"'{transition['to']}' without writing RHEI_RESULT_PATH",
                    )
            self.assertGreater(checked, 0, states_path)

    def test_cover_states_receive_the_prompt_as_a_handoff(self) -> None:
        """The cover prompt reaches the agent inside its message, not by path.

        The state that writes the prompt declares it as a handoff output, the
        cover state inherits it as required from that state, and the
        instructions name every listed path a target. In the deep phase that
        state is the session dispatcher.
        §AR-code-coverage-improvement.5.2, §AR-code-coverage-deep-navigation.4
        """
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
                machine: dict = yaml.safe_load(
                    _render_numeric_placeholders(states_file.read())
                )
            states: dict = machine["states"]
            transitions: list[dict] = machine["transitions"]

            for phase, source in (("api", "api-measure"), ("deep", "deep-dispatch")):
                cover: str = f"{phase}-cover"
                prompt_path: str = f"runtime/code-coverage/prompts/{phase}-cover-prompt.md"
                with self.subTest(path=states_path, phase=phase):
                    self.assertEqual(
                        {t["from"] for t in transitions if t["to"] == cover},
                        {source},
                        f"{cover} must be entered only from {source}",
                    )
                    handoffs: list[dict] = [
                        output
                        for output in states[source]["outputs"]
                        if output.get("kind") == "handoff"
                    ]
                    self.assertEqual(
                        [(h["name"], h["path"]) for h in handoffs],
                        [("prompt", prompt_path)],
                    )
                    self.assertIn(
                        f'"{prompt_path}"',
                        states[source]["program"],
                        f"{source} must write the prompt path on its zero exit",
                    )
                    self.assertEqual(
                        states[cover]["handoff"]["inherit"],
                        [{"from": "transition.previous", "name": "prompt", "required": True}],
                    )
                    instructions: str = " ".join(states[cover]["instructions"].split())
                    self.assertIn(f"`## Handoff from {source}` section below", instructions)
                    self.assertNotIn("guidance only", instructions)
                    self.assertNotIn("Read the prompt at", instructions)

    def test_cover_states_run_only_the_coverage_suite(self) -> None:
        """A cover session runs `codeCoverageTest` alone and leaves Spotless to
        finalization, which formats before checkstyle reads the suite.

        §AR-code-coverage-improvement.2, §AR-code-coverage-improvement.5
        """
        forge_root: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        template_path: str = os.path.join(
            forge_root, ".agents", "rhei", "templates", "code-coverage-improvement", "states.yaml",
        )
        example_path: str = os.path.join(
            forge_root, "examples", "code-coverage-improvement-example", "states.yaml",
        )

        for states_path in (template_path, example_path):
            with open(states_path, encoding="utf-8") as states_file:
                machine: dict = yaml.safe_load(
                    _render_numeric_placeholders(states_file.read())
                )
            for cover in ("api-cover", "deep-cover"):
                with self.subTest(path=states_path, cover=cover):
                    instructions: str = " ".join(machine["states"][cover]["instructions"].split())
                    self.assertIn(
                        "`./gradlew codeCoverageTest -Pcoordinates=<coordinate>`",
                        instructions,
                    )
                    self.assertIn("is the only Gradle task you run", instructions)
                    self.assertIn("delete a test method you wrote", instructions)
                    self.assertNotIn("./gradlew spotlessApply", instructions)

            if states_path == template_path:
                finalization: str = machine["states"]["reviewed-execute"]["program"]
                self.assertLess(
                    finalization.index('"spotlessApply", "spotlessCheck"'),
                    finalization.index('"checkstyle"'),
                    "finalization must format before checkstyle reads the cover suite",
                )

    def test_deep_pass_runs_as_dispatched_group_sessions(self) -> None:
        """Measurement queues the pass, the dispatcher loops over it with the
        cover state, and the visit caps sit at the session ceiling.

        §AR-code-coverage-deep-navigation.4, §AR-code-coverage-deep-navigation.4
        """
        forge_root: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        states_paths: tuple[str, ...] = (
            os.path.join(
                forge_root, ".agents", "rhei", "templates", "code-coverage-improvement", "states.yaml",
            ),
            os.path.join(forge_root, "examples", "code-coverage-improvement-example", "states.yaml"),
        )
        queue_path: str = "runtime/code-coverage/prompts/deep-session-queue.json"

        for states_path in states_paths:
            with open(states_path, encoding="utf-8") as states_file:
                machine: dict = yaml.safe_load(
                    _render_numeric_placeholders(states_file.read())
                )
            states: dict = machine["states"]
            edges: set[tuple[str, str, int | None]] = {
                (t["from"], t["to"], t.get("exit_code"))
                for t in machine["transitions"]
                if "deep-dispatch" in (t["from"], t["to"])
            }
            with self.subTest(path=states_path):
                self.assertEqual(edges, {
                    ("deep-measure", "deep-dispatch", 10),
                    ("deep-dispatch", "deep-cover", 10),
                    ("deep-dispatch", "deep-measure", 0),
                    ("deep-dispatch", "human-intervention", 1),
                    ("deep-cover", "deep-dispatch", None),
                })
                self.assertNotIn(
                    ("deep-cover", "deep-measure"),
                    {(t["from"], t["to"]) for t in machine["transitions"]},
                )
                self.assertEqual(
                    [output["path"] for output in states["deep-measure"]["outputs"]],
                    [queue_path],
                )
                self.assertIn(f'"{queue_path}"', states["deep-measure"]["program"])
                self.assertIn(f'"{queue_path}"', states["deep-dispatch"]["program"])
                # §AR-code-coverage-deep-navigation.4: a stuck session gives way
                # to the next one instead of being retried.
                self.assertEqual(states["deep-cover"]["agent_timeout"], "45m")
                self.assertIn(
                    ("deep-cover", "deep-dispatch", "45m"),
                    {(t["from"], t["to"], t.get("timeout")) for t in machine["transitions"]},
                )
                instructions: str = " ".join(states["deep-cover"]["instructions"].split())
                self.assertIn(
                    "Write every test for this session's targets first, without running "
                    "Gradle in between.",
                    instructions,
                )
                self.assertIn("run the suite again until it passes", instructions)
                passes: int = states["api-cover"]["visits"]
                self.assertEqual(states["deep-cover"]["visits"], passes * MAX_SESSIONS_PER_PASS)
                self.assertEqual(
                    states["deep-dispatch"]["visits"], passes * (MAX_SESSIONS_PER_PASS + 1)
                )

    def test_settings_ask_for_rhei_bounds_that_fit_the_session_ceiling(self) -> None:
        """Rhei bounds transitions per task and agent starts per day; a deep
        pass at the session ceiling must fit both at the default budget.

        §AR-code-coverage-deep-navigation.4
        """
        forge_root: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        template_dir: str = os.path.join(
            forge_root, ".agents", "rhei", "templates", "code-coverage-improvement"
        )
        with open(os.path.join(template_dir, "template.yaml"), encoding="utf-8") as template_file:
            inputs: dict[str, dict] = {
                entry["name"]: entry for entry in yaml.safe_load(template_file)["inputs"]
            }
        with open(os.path.join(template_dir, "settings.json"), encoding="utf-8") as settings_file:
            defaults: dict = json.load(settings_file)["defaults"]
        passes: int = inputs["coverage_iterations"]["default"]
        retries: int = inputs["measure_visits"]["default"]
        # Per pass: into dispatch, out and back per session, back to measurement;
        # every measurement retry adds a fix round trip; a few edges frame the task.
        deep_transitions: int = passes * (2 * MAX_SESSIONS_PER_PASS + 2) + 2 * retries + 10
        # Deep and API cover sessions, one fix per retry in either phase, and the
        # handful of single agent states around them.
        agent_starts: int = passes * (MAX_SESSIONS_PER_PASS + 1) + 2 * retries + 10

        self.assertGreaterEqual(defaults["transition_limit"], deep_transitions)
        self.assertGreaterEqual(defaults["invocations_per_day"], agent_starts)


if __name__ == "__main__":
    unittest.main()
