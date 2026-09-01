# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import contextlib
import io
import tempfile
import unittest
from unittest.mock import patch

from utility_scripts import run_location
from utility_scripts.continuation_marker import (
    ContinuationMarker,
    continuation_marker_path,
    load_continuation_marker,
)
from utility_scripts.run_location import (
    PHASE_CLAIM,
    PHASE_EXPLORE,
    PHASE_FINALIZATION,
    PHASE_PUBLICATION,
    PHASE_SETUP,
    STEP_CHECK_HOST_REQUIREMENTS,
    STEP_GENERATE_TESTS,
    STEP_NORMAL_SETUP,
    STEP_NATIVE_TRACE_GATE,
    STEP_PUBLISH_BRANCH,
    UNLOCATED_FAILURE_DEFECT,
    UNLOCATED_STEP,
    PHASE_STEPS,
    RunLocation,
    bind_continuation_marker,
    bind_run_context,
    clear_recorded_failure,
    format_run_failure_line,
    log_step_progress,
    marker_failure_location,
    pipeline_step,
    record_step_failure,
    report_run_failure,
    reset_run_location,
    resolve_failure_location,
    run_step,
    step_position,
)
from utility_scripts.stage_logger import log_detail


def _captured(callable_under_test):
    """Run a callable and return its (stdout, stderr) as text."""
    out, err = io.StringIO(), io.StringIO()
    with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
        callable_under_test()
    return out.getvalue(), err.getvalue()


class RunLocationVocabularyTests(unittest.TestCase):
    def setUp(self) -> None:
        reset_run_location()

    def tearDown(self) -> None:
        reset_run_location()

    def test_every_registered_phase_and_step_is_ordered_and_positioned(self) -> None:
        for phase, steps in PHASE_STEPS.items():
            for index, step in enumerate(steps, start=1):
                self.assertEqual(step_position(phase, step), (index, len(steps)))

    def test_unregistered_phase_and_step_are_rejected(self) -> None:
        with self.assertRaises(ValueError):
            step_position("nonsense", STEP_GENERATE_TESTS)
        with self.assertRaises(ValueError):
            step_position(PHASE_PUBLICATION, "nonsense()")

    def test_location_renders_with_and_without_operand(self) -> None:
        self.assertEqual(str(RunLocation(PHASE_EXPLORE, STEP_GENERATE_TESTS)), "explore/generate_tests()")
        self.assertEqual(
            str(RunLocation(PHASE_EXPLORE, STEP_GENERATE_TESTS, "com.acme.Thing")),
            "explore/generate_tests()[com.acme.Thing]",
        )


class ProgressOutputTests(unittest.TestCase):
    def setUp(self) -> None:
        reset_run_location()

    def tearDown(self) -> None:
        reset_run_location()

    def test_step_entry_prints_the_running_step_line(self) -> None:
        def enter() -> None:
            with run_step(PHASE_EXPLORE, STEP_NATIVE_TRACE_GATE, operand="com.acme.Thing"):
                pass

        stdout, _ = _captured(enter)
        position, total = step_position(PHASE_EXPLORE, STEP_NATIVE_TRACE_GATE)
        self.assertIn(
            f"Running step {STEP_NATIVE_TRACE_GATE} ({position}/{total}) of phase {PHASE_EXPLORE} on com.acme.Thing",
            stdout,
        )

    def test_compact_claim_progress_keeps_the_derived_step_count(self) -> None:
        def enter() -> None:
            with run_step(PHASE_CLAIM, STEP_CHECK_HOST_REQUIREMENTS):
                log_step_progress(
                    PHASE_CLAIM,
                    STEP_CHECK_HOST_REQUIREMENTS,
                    "Checking host requirements",
                )

        with patch.dict(
                "os.environ",
                {"FORGE_VERBOSE": "0", "FORGE_DEBUG_LOGGING": "0"},
        ):
            stdout, _ = _captured(enter)

        position, total = step_position(PHASE_CLAIM, STEP_CHECK_HOST_REQUIREMENTS)
        self.assertIn(f"[claim] Checking host requirements ({position}/{total})", stdout)
        self.assertNotIn(f"Running step {STEP_CHECK_HOST_REQUIREMENTS}", stdout)

    def test_verbose_claim_progress_restores_the_registered_step_line(self) -> None:
        def enter() -> None:
            with run_step(PHASE_CLAIM, STEP_CHECK_HOST_REQUIREMENTS):
                log_step_progress(
                    PHASE_CLAIM,
                    STEP_CHECK_HOST_REQUIREMENTS,
                    "Checking host requirements",
                )

        with patch.dict("os.environ", {"FORGE_VERBOSE": "1"}):
            stdout, _ = _captured(enter)

        self.assertIn(f"Running step {STEP_CHECK_HOST_REQUIREMENTS}", stdout)
        self.assertIn("[claim] Checking host requirements", stdout)

    def test_compact_setup_hides_detail_but_keeps_state_and_count(self) -> None:
        def enter() -> None:
            with run_step(PHASE_SETUP, STEP_NORMAL_SETUP, operand="org.example:lib:1.0.0"):
                log_step_progress(
                    PHASE_SETUP,
                    STEP_NORMAL_SETUP,
                    "Scaffolding org.example:lib:1.0.0",
                )
                log_detail("scaffold", "internal setup narration")

        with patch.dict(
                "os.environ",
                {"FORGE_VERBOSE": "0", "FORGE_DEBUG_LOGGING": "0"},
        ):
            stdout, _ = _captured(enter)

        position, total = step_position(PHASE_SETUP, STEP_NORMAL_SETUP)
        self.assertIn(f"[setup] Scaffolding org.example:lib:1.0.0 ({position}/{total})", stdout)
        self.assertNotIn(f"Running step {STEP_NORMAL_SETUP}", stdout)
        self.assertNotIn("internal setup narration", stdout)

    def test_verbose_setup_restores_detail_and_registered_step(self) -> None:
        def enter() -> None:
            with run_step(PHASE_SETUP, STEP_NORMAL_SETUP, operand="org.example:lib:1.0.0"):
                log_step_progress(
                    PHASE_SETUP,
                    STEP_NORMAL_SETUP,
                    "Scaffolding org.example:lib:1.0.0",
                )
                log_detail("scaffold", "internal setup narration")

        with patch.dict("os.environ", {"FORGE_VERBOSE": "1"}):
            stdout, _ = _captured(enter)

        self.assertIn(f"Running step {STEP_NORMAL_SETUP}", stdout)
        self.assertIn("internal setup narration", stdout)

    def test_entering_explore_restores_normal_detail_after_setup(self) -> None:
        def transition() -> None:
            run_location.enter_phase(PHASE_SETUP)
            log_detail("setup-detail", "hidden setup detail")
            run_location.enter_phase(PHASE_EXPLORE)
            log_detail("explore-detail", "visible explore detail")

        with patch.dict(
                "os.environ",
                {"FORGE_VERBOSE": "0", "FORGE_DEBUG_LOGGING": "0"},
        ):
            stdout, _ = _captured(transition)

        self.assertNotIn("hidden setup detail", stdout)
        self.assertIn("visible explore detail", stdout)

    def test_phase_banner_prints_once_per_transition(self) -> None:
        bind_run_context("issue #1412 org.example:demo:1.0.0")

        def enter() -> None:
            with run_step(PHASE_EXPLORE, STEP_GENERATE_TESTS):
                pass
            with run_step(PHASE_EXPLORE, STEP_NATIVE_TRACE_GATE):
                pass
            with run_step(PHASE_PUBLICATION, STEP_PUBLISH_BRANCH):
                pass

        stdout, _ = _captured(enter)
        self.assertEqual(stdout.count("PHASE: EXPLORE"), 1)
        self.assertEqual(stdout.count("PHASE: PUBLICATION"), 1)
        self.assertIn("issue #1412 org.example:demo:1.0.0", stdout)


class FailureLocationTests(unittest.TestCase):
    def setUp(self) -> None:
        reset_run_location()

    def tearDown(self) -> None:
        reset_run_location()

    def test_terminal_failure_prints_one_located_line_before_its_detail(self) -> None:
        def fail() -> None:
            try:
                with run_step(PHASE_EXPLORE, STEP_GENERATE_TESTS, operand="com.acme.Thing"):
                    raise RuntimeError("boom")
            except RuntimeError as exc:
                report_run_failure(resolve_failure_location(exc), "ERROR: boom")

        _, stderr = _captured(fail)
        lines = [line for line in stderr.splitlines() if line]
        self.assertEqual(lines[0], "run failed in explore/generate_tests()[com.acme.Thing]")
        self.assertEqual(lines[1], "Phase failed: explore")
        self.assertEqual(lines[2], "Step failed: generate_tests()")
        self.assertEqual(lines[3], "Class: com.acme.Thing")
        self.assertEqual(lines[4], "Error: boom")

    def test_innermost_step_wins_and_survives_an_intermediate_handler(self) -> None:
        def fail() -> None:
            with run_step(PHASE_SETUP, "run_workflow_engine()"):
                try:
                    with run_step(PHASE_EXPLORE, STEP_NATIVE_TRACE_GATE, operand="com.acme.Thing"):
                        raise RuntimeError("boom")
                except RuntimeError as exc:
                    raise ValueError("wrapped") from exc

        with self.assertRaises(ValueError):
            _captured(fail)
        self.assertEqual(
            str(run_location.failed_run_location()),
            "explore/native_trace_gate()[com.acme.Thing]",
        )

    def test_the_failure_line_is_printed_once_per_run(self) -> None:
        location = RunLocation(PHASE_EXPLORE, STEP_GENERATE_TESTS)

        def report_twice() -> None:
            report_run_failure(location, "ERROR: first")
            report_run_failure(location, "ERROR: second")

        _, stderr = _captured(report_twice)
        self.assertEqual(stderr.count("run failed in explore/generate_tests()"), 1)
        self.assertNotIn("ERROR: second", stderr)

    def test_a_user_interrupt_carries_and_records_no_location(self) -> None:
        def interrupt() -> None:
            with run_step(PHASE_EXPLORE, STEP_GENERATE_TESTS):
                raise KeyboardInterrupt

        with self.assertRaises(KeyboardInterrupt):
            _captured(interrupt)
        self.assertIsNone(run_location.failed_run_location())

    def test_a_failure_outside_every_step_is_reported_as_a_defect(self) -> None:
        def fail() -> None:
            report_run_failure(resolve_failure_location(RuntimeError("boom")), "ERROR: boom")

        _, stderr = _captured(fail)
        self.assertIn(f"run failed in {PHASE_CLAIM}/{UNLOCATED_STEP}", stderr)
        self.assertIn(UNLOCATED_FAILURE_DEFECT, stderr)

    def test_a_status_code_failure_records_the_step_it_gave_up_in(self) -> None:
        def give_up() -> None:
            with run_step(PHASE_FINALIZATION, "finalize_run()", operand="org.example:demo:1.0.0"):
                record_step_failure()

        _captured(give_up)
        self.assertEqual(
            format_run_failure_line(resolve_failure_location()),
            "run failed in finalization/finalize_run()[org.example:demo:1.0.0]",
        )


class MarkerFailureLocationTests(unittest.TestCase):
    def setUp(self) -> None:
        reset_run_location()

    def tearDown(self) -> None:
        reset_run_location()

    def test_the_recorded_location_reaches_the_continuation_marker(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            marker_path = continuation_marker_path(repo_path)
            ContinuationMarker.create(
                strategy_name="strategy",
                issue_number=1412,
                label="library-new-request",
                coordinate="org.example:demo:1.0.0",
                new_version=None,
            ).save(marker_path)
            bind_continuation_marker(marker_path)

            def fail() -> None:
                with contextlib.suppress(RuntimeError):
                    with run_step(PHASE_EXPLORE, STEP_GENERATE_TESTS, operand="com.acme.Thing"):
                        raise RuntimeError("boom")

            _captured(fail)
            reloaded = load_continuation_marker(marker_path)

        self.assertEqual(
            reloaded.failure,
            {"phase": PHASE_EXPLORE, "step": STEP_GENERATE_TESTS, "operand": "com.acme.Thing"},
        )
        self.assertEqual(
            format_run_failure_line(marker_failure_location(reloaded)),
            "run failed in explore/generate_tests()[com.acme.Thing]",
        )

    def test_a_recovered_failure_is_cleared_before_a_later_failure(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            marker_path = continuation_marker_path(repo_path)
            ContinuationMarker.create(
                strategy_name="strategy",
                issue_number=1412,
                label="fails-native-image-run",
                coordinate="org.example:demo:1.0.0",
                new_version="2.0.0",
            ).save(marker_path)
            bind_continuation_marker(marker_path)

            with run_step(PHASE_EXPLORE, STEP_GENERATE_TESTS):
                record_step_failure()
            clear_recorded_failure()
            self.assertIsNone(load_continuation_marker(marker_path).failure)

            with run_step(PHASE_PUBLICATION, STEP_PUBLISH_BRANCH):
                record_step_failure()
            reloaded = load_continuation_marker(marker_path)

        self.assertEqual(
            format_run_failure_line(resolve_failure_location()),
            "run failed in publication/publish_branch()",
        )
        self.assertEqual(
            reloaded.failure,
            {"phase": PHASE_PUBLICATION, "step": STEP_PUBLISH_BRANCH, "operand": None},
        )


class PipelineStepDecoratorTests(unittest.TestCase):
    def setUp(self) -> None:
        reset_run_location()

    def tearDown(self) -> None:
        reset_run_location()

    def test_the_operand_is_resolved_by_parameter_name_for_either_call_style(self) -> None:
        @pipeline_step(
            PHASE_CLAIM,
            STEP_CHECK_HOST_REQUIREMENTS,
            operand=lambda arguments: f"issue #{arguments['issue_number']}",
        )
        def gate(reachability_path: str, issue_number: int) -> None:
            del reachability_path, issue_number
            raise RuntimeError("boom")

        for call in (lambda: gate("/repo", 1412), lambda: gate(issue_number=1412, reachability_path="/repo")):
            reset_run_location()
            with self.assertRaises(RuntimeError):
                _captured(call)
            self.assertEqual(
                format_run_failure_line(resolve_failure_location()),
                "run failed in claim/check_host_requirements()[issue #1412]",
            )


if __name__ == "__main__":
    unittest.main()
