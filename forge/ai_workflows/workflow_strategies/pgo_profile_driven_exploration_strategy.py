# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import re
import subprocess

from ai_workflows.workflow_strategies.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from ai_workflows.workflow_strategies.workflow_strategy import (
    DEFAULT_RUNTIME_ENVIRONMENT_SUMMARY,
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
)
from utility_scripts.dynamic_access_report import (
    DynamicAccessCallSite,
    DynamicAccessClass,
    DynamicAccessClassDelta,
    DynamicAccessCoverageReport,
    format_call_sites,
)
from utility_scripts.pgo_near_call_report import format_pgo_near_call_guidance
from utility_scripts.runtime_context import collect_runtime_environment_summary


DEFAULT_PGO_SAMPLING_PERIOD_MICROS = 100
MINIMUM_PGO_SAMPLING_PERIOD_MICROS = 100
PGO_GUIDANCE_UNAVAILABLE_PREFIX = "- PGO near-call guidance unavailable:"


class PgoNearCallGuidanceUnavailableError(RuntimeError):
    """Raised when the PGO strategy cannot produce actionable near-call guidance."""


@DynamicAccessIterativeStrategy.register("pgo_profile_driven_exploration")
class PgoProfileDrivenExplorationStrategy(DynamicAccessIterativeStrategy):
    """Dynamic-access workflow that augments prompts with PGO near-call path guidance."""

    PROMPT_KEY = "pgo-profile-driven-exploration"
    REACHABILITY_ANALYSIS_PROMPT_KEY = "pgo-reachability-analysis"
    REQUIRED_PROMPTS = [
        "pgo-profile-driven-exploration",
        "pgo-reachability-analysis",
    ]
    REQUIRED_PARAMS = [
        "max-iterations",
        "max-class-test-iterations",
    ]

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, **context)
        self.pgo_sampling_period_micros = self._parameter_int(
            "pgo-sampling-period-micros",
            DEFAULT_PGO_SAMPLING_PERIOD_MICROS,
        )
        if self.pgo_sampling_period_micros < MINIMUM_PGO_SAMPLING_PERIOD_MICROS:
            raise ValueError(
                "Strategy parameter 'pgo-sampling-period-micros' must be at least "
                f"{MINIMUM_PGO_SAMPLING_PERIOD_MICROS}"
            )
        self.pgo_near_call_report_dir = os.path.join(
            self.reachability_repo_path,
            "tests",
            "src",
            self.group,
            self.artifact,
            self.test_version,
            "build",
            "reports",
            "pgo-near-call",
        )
        self._pgo_guidance_by_call: dict[str, str] = {}
        self.dynamic_access_unreachable: list[dict] = []
        self._runtime_environment_summary = self._resolve_runtime_environment_summary()

    def _resolve_runtime_environment_summary(self) -> str:
        runtime_environment_summary = str(self.context.get("runtime_environment_summary") or "")
        if not runtime_environment_summary or runtime_environment_summary == DEFAULT_RUNTIME_ENVIRONMENT_SUMMARY:
            runtime_environment_summary = collect_runtime_environment_summary()
            self.context["runtime_environment_summary"] = runtime_environment_summary
        return runtime_environment_summary

    def run(self, agent, checkpoint_commit_hash):
        initial_report = self._generate_dynamic_access_report()
        if initial_report is None:
            self._print_dynamic_access_message(
                "PGO profile-driven exploration cannot continue without dynamic-access guidance: "
                f"cause={self._dynamic_access_fallback_cause()} "
                f"previous_report={self._current_dynamic_access_status()} "
                f"coordinate={self.library}"
            )
            self._print_failure_analysis(
                "pgo_profile_driven_exploration",
                issue=self._dynamic_access_fallback_cause(),
                indent_level=1,
            )
            return RUN_STATUS_FAILURE, 0, 0

        if not initial_report.has_dynamic_access or initial_report.total_calls == 0:
            self._print_dynamic_access_message(
                "PGO profile-driven exploration cannot continue because the dynamic-access report "
                "contains no call-site guidance: "
                f"cause={self._dynamic_access_fallback_cause()} "
                f"coordinate={self.library}"
            )
            self._print_failure_analysis(
                "pgo_profile_driven_exploration",
                issue=self._dynamic_access_fallback_cause(),
                indent_level=1,
                has_dynamic_access=str(initial_report.has_dynamic_access).lower(),
                total_calls=initial_report.total_calls,
                covered_calls=initial_report.covered_calls,
            )
            return RUN_STATUS_FAILURE, 0, 0

        self._maybe_activate_large_library_series(initial_report)

        global_iterations = 0
        phase_ok, extra_iterations = self._run_dynamic_access_phase(agent, initial_report)
        global_iterations += extra_iterations
        if self._last_phase_status == RUN_STATUS_CHUNK_READY:
            return RUN_STATUS_CHUNK_READY, global_iterations, 1
        if not phase_ok:
            subprocess.run(["git", "reset", "--hard", checkpoint_commit_hash], check=False)
            return RUN_STATUS_FAILURE, global_iterations, 0

        return RUN_STATUS_SUCCESS, global_iterations, 1

    def _dynamic_access_prompt_extras(self, active_class) -> dict:
        guidance_key = self._active_call_guidance_key(active_class)
        guidance = self._pgo_guidance_by_call.get(guidance_key)
        if guidance is None:
            guidance = self._refresh_pgo_near_call_guidance(active_class)
            self._pgo_guidance_by_call[guidance_key] = guidance
        return {
            "pgo_near_call_guidance": guidance,
        }

    def _prepare_dynamic_access_attempt(self, agent, active_class, current_report, class_attempts: int) -> bool:
        guidance = self._refresh_pgo_near_call_guidance(active_class)
        if not guidance.startswith(PGO_GUIDANCE_UNAVAILABLE_PREFIX):
            self._pgo_guidance_by_call[self._active_call_guidance_key(active_class)] = guidance
            return True

        issue = guidance.removeprefix(PGO_GUIDANCE_UNAVAILABLE_PREFIX).strip()
        self._print_failure_analysis(
            "formatPgoNearCallGuidance",
            issue=issue,
            indent_level=2,
        )
        unreachable_entries = self._record_unreachable_call_sites(agent, active_class, issue)

        if self._all_uncovered_call_sites_recorded_unreachable(active_class, unreachable_entries):
            self._pgo_guidance_by_call.pop(self._active_call_guidance_key(active_class), None)
            return False

        return False

    def _run_dynamic_access_phase(self, agent, current_report=None) -> tuple[bool, int]:
        if current_report is None:
            current_report = self._generate_dynamic_access_report()
        self._dynamic_access_phase_prompt_iterations = 0
        if self._should_fallback_to_basic_flow(current_report):
            return True, 0
        self._maybe_activate_large_library_series(current_report)

        exhausted_call_sites: set[str] = set()
        previous_report = None
        prompt_iterations = 0
        successful_calls = 0
        terminal_skipped_calls = 0
        processed_calls_this_part = 0
        initial_part_covered_calls = current_report.covered_calls
        initial_call_positions = self._initial_uncovered_call_positions(current_report)
        initial_call_count = len(initial_call_positions)
        initial_test_change_signature = self._library_test_change_signature()

        while True:
            active_selection = self._next_uncovered_call_site(current_report, exhausted_call_sites)
            if active_selection is None:
                if (
                        successful_calls == 0
                        and self.keep_tests_without_dynamic_access
                        and prompt_iterations > 0
                        and self._library_test_change_signature() != initial_test_change_signature
                ):
                    self._print_dynamic_access_message("Coverage not improved. Keeping generated tests by request.")
                    return True, prompt_iterations
                return (
                    successful_calls > 0
                    or terminal_skipped_calls > 0
                ), prompt_iterations

            report_class, target_call_site = active_selection
            active_class = self._single_call_class(report_class, target_call_site)
            target_key = target_call_site.key
            call_checkpoint = subprocess.check_output(
                ["git", "rev-parse", "HEAD"], text=True,
            ).strip()
            call_progress = initial_call_positions.get(target_key)
            progress_text = "unknown"
            if call_progress is not None:
                progress_text = "{current}/{total}".format(
                    current=call_progress,
                    total=initial_call_count,
                )
            self._print_dynamic_access_message(
                "Dynamic-access call {progress}: {class_name}".format(
                    progress=progress_text,
                    class_name=active_class.class_name,
                )
            )
            self._print_dynamic_access_detail(format_call_sites(active_class.uncovered_call_sites))

            call_attempts = 0
            call_attempts_without_progress = 0
            call_failed = False
            call_resolved = False
            gate_failed = False
            call_skipped = False
            while self._should_continue_class_attempts(call_attempts, call_attempts_without_progress):
                self._print_dynamic_access_detail(
                    self._format_class_attempt_status(call_attempts, call_attempts_without_progress)
                )
                if not self._prepare_dynamic_access_attempt(agent, active_class, current_report, call_attempts):
                    call_skipped = True
                    break

                delta = self._compute_single_call_delta(previous_report, current_report, active_class, target_key)
                dynamic_prompt = self._render_dynamic_access_prompt(active_class, delta, call_attempts)
                self._print_dynamic_access_detail("agent: running dynamic-access prompt", indent_level=2)
                agent.send_prompt(dynamic_prompt)
                self._print_dynamic_access_detail("agent: complete", indent_level=2)
                prompt_iterations += 1
                self._dynamic_access_phase_prompt_iterations = prompt_iterations
                call_attempts += 1

                refreshed_report = None
                last_test_output = ""
                last_failed_task = None
                for test_iteration in range(self.max_class_test_iterations):
                    self._print_dynamic_access_detail(
                        "test {current}/{maximum}: running ./gradlew test -Pcoordinates={library}".format(
                            current=test_iteration + 1,
                            maximum=self.max_class_test_iterations,
                            library=self.library,
                        ),
                        indent_level=2,
                    )
                    test_output = agent.run_test_command(f"./gradlew test -Pcoordinates={self.library}")
                    failed_task = self._get_first_failed_task(test_output)
                    last_test_output = test_output
                    last_failed_task = failed_task
                    self._print_dynamic_access_detail(
                        "test: complete (failed task: {failed_task})".format(
                            failed_task=failed_task or "none",
                        ),
                        indent_level=2,
                    )
                    if failed_task not in {"nativeTest", None}:
                        self._print_dynamic_access_detail(
                            "agent: test failed before nativeTest; sending failure output back to agent",
                            indent_level=2,
                        )
                        agent.send_prompt(
                            "When `./gradlew test -Pcoordinates={library}` is ran this is the error:\n"
                            "{error_output}".format(
                                library=self.library,
                                error_output=test_output,
                            )
                        )
                        self._print_dynamic_access_detail("agent: complete", indent_level=2)
                        prompt_iterations += 1
                        self._dynamic_access_phase_prompt_iterations = prompt_iterations
                        continue

                    previous_report = current_report
                    refreshed_report = self._generate_dynamic_access_report(indent_level=2)
                    if refreshed_report is not None:
                        break

                    self._print_dynamic_access_detail(
                        "agent: dynamic-access report unavailable after native execution; "
                        "sending failure output back to agent",
                        indent_level=2,
                    )
                    agent.send_prompt(
                        "The generated tests reached native execution, but Forge could not refresh the "
                        "dynamic-access coverage report for `{library}`.\n\n"
                        "Fix the generated tests so `./gradlew test -Pcoordinates={library}` and "
                        "`./gradlew generateDynamicAccessCoverageReport -Pcoordinates={library}` can complete "
                        "far enough to produce a loadable dynamic-access report. Do not skip native-image "
                        "execution and do not remove meaningful assertions.\n\n"
                        "`./gradlew test -Pcoordinates={library}` output:\n"
                        "{test_output}\n\n"
                        "`./gradlew generateDynamicAccessCoverageReport -Pcoordinates={library}` output:\n"
                        "{report_output}".format(
                            library=self.library,
                            test_output=self._trim_for_agent_prompt(test_output),
                            report_output=self._trim_for_agent_prompt(self._last_dynamic_access_report_output),
                        )
                    )
                    self._print_dynamic_access_detail("agent: complete", indent_level=2)
                    prompt_iterations += 1
                    self._dynamic_access_phase_prompt_iterations = prompt_iterations

                if refreshed_report is None and last_failed_task not in {"nativeTest", None}:
                    self._print_dynamic_access_detail("result: failed, reverting to checkpoint", indent_level=2)
                    self._print_failure_analysis(
                        "gradle_test_failed",
                        issue=self._summarize_gradle_issue(last_test_output),
                        indent_level=2,
                        failed_task=last_failed_task or "unknown",
                        class_name=active_class.class_name,
                        tracked_api=target_call_site.tracked_api,
                        frame=target_call_site.frame,
                    )
                    subprocess.run(["git", "reset", "--hard", call_checkpoint], check=False)
                    call_failed = True
                    break

                if refreshed_report is None:
                    self._print_dynamic_access_detail(
                        "result: dynamic-access report unavailable after test run",
                        indent_level=2,
                    )
                    self._print_failure_analysis(
                        "dynamic_access_report_refresh_failed",
                        issue=self._last_dynamic_access_report_issue,
                        indent_level=2,
                        class_name=active_class.class_name,
                        tracked_api=target_call_site.tracked_api,
                        frame=target_call_site.frame,
                    )
                    return False, prompt_iterations

                previous_covered_calls = current_report.covered_calls
                current_report = refreshed_report
                if self._is_call_site_covered(current_report, active_class.class_name, target_key):
                    self._print_dynamic_access_detail("result: call covered", indent_level=2)
                    self._commit_test_sources(
                        "Dynamic-access coverage for {class_name} {tracked_api}".format(
                            class_name=active_class.class_name,
                            tracked_api=target_call_site.tracked_api,
                        )
                    )
                    successful_calls += 1
                    exhausted_call_sites.add(target_key)
                    if not self._run_native_test_verification_gate(active_class.class_name):
                        gate_failed = True
                        break
                    current_report = self._refresh_report_after_gate(active_class.class_name) or current_report
                    call_resolved = True
                    break

                if current_report.covered_calls > previous_covered_calls:
                    self._print_dynamic_access_detail(
                        "result: overall dynamic-access coverage improved, target call still uncovered",
                        indent_level=2,
                    )
                    self._commit_test_sources(
                        "Partial dynamic-access coverage while targeting {class_name} {tracked_api}".format(
                            class_name=active_class.class_name,
                            tracked_api=target_call_site.tracked_api,
                        )
                    )
                    call_checkpoint = subprocess.check_output(
                        ["git", "rev-parse", "HEAD"], text=True,
                    ).strip()
                    if not self._run_native_test_verification_gate(active_class.class_name):
                        gate_failed = True
                        break
                    current_report = self._refresh_report_after_gate(active_class.class_name) or current_report
                    if self._is_call_site_covered(current_report, active_class.class_name, target_key):
                        successful_calls += 1
                        exhausted_call_sites.add(target_key)
                        call_resolved = True
                        break
                else:
                    self._print_dynamic_access_detail(
                        "result: no new coverage for target call",
                        indent_level=2,
                    )
                call_attempts_without_progress += 1

            if hasattr(agent, "clear_context"):
                agent.clear_context()
            self._pgo_guidance_by_call.pop(self._active_call_guidance_key(active_class), None)
            if gate_failed:
                self._print_failure_analysis(
                    "native_test_verification_gate_failed",
                    issue="nativeTest_did_not_pass_within_verification_budget",
                    indent_level=1,
                    class_name=active_class.class_name,
                    tracked_api=target_call_site.tracked_api,
                    frame=target_call_site.frame,
                )
                return False, prompt_iterations
            if call_resolved:
                processed_calls_this_part += 1
                if self._should_stop_for_large_library_chunk(
                        current_report,
                        self._classes_for_exhausted_calls(current_report, exhausted_call_sites),
                        processed_calls_this_part,
                        initial_part_covered_calls,
                ):
                    self._last_phase_status = RUN_STATUS_CHUNK_READY
                    self._save_large_library_progress(current_report)
                    return True, prompt_iterations
                continue
            if call_failed:
                self._record_unreachable_call_sites(
                    agent,
                    active_class,
                    "The generated test changes could not reach nativeTest for this target call.",
                )
                exhausted_call_sites.add(target_key)
                terminal_skipped_calls += 1
            elif call_skipped:
                self._print_dynamic_access_detail(
                    "final: skipped because this dynamic-access call is unreachable in this setup",
                    indent_level=1,
                )
                exhausted_call_sites.add(target_key)
                terminal_skipped_calls += 1
            else:
                self._print_dynamic_access_detail(
                    "final: exhausted after {attempts} attempts".format(
                        attempts=self._format_class_exhaustion_attempts(
                            call_attempts,
                            call_attempts_without_progress,
                        ),
                    ),
                    indent_level=1,
                )
                subprocess.run(["git", "reset", "--hard", call_checkpoint], check=False)
                self._record_unreachable_call_sites(
                    agent,
                    active_class,
                    "PGO-guided exploration did not cover this call within the no-progress budget.",
                )
                exhausted_call_sites.add(target_key)
                terminal_skipped_calls += 1

            self._print_call_completion_progress(
                active_class,
                min(len(exhausted_call_sites), initial_call_count),
                initial_call_count,
                current_report,
            )
            processed_calls_this_part += 1

    def _refresh_pgo_near_call_guidance(self, active_class) -> str:
        result = self._run_gradle_command_with_output([
            "./gradlew",
            "generatePgoDynamicAccessNearCallReport",
            f"-Pcoordinates={self.library}",
            f"-PpgoSamplingPeriodMicros={self.pgo_sampling_period_micros}",
        ])
        if result.returncode != 0:
            self._print_dynamic_access_detail(
                "PGO near-call report refresh failed: exit_code={exit_code}".format(
                    exit_code=result.returncode,
                ),
                indent_level=2,
            )
            self._print_failure_analysis(
                "generatePgoDynamicAccessNearCallReport",
                issue=self._summarize_gradle_issue(result.stdout),
                indent_level=2,
                exit_code=result.returncode,
            )
            print(result.stdout)
            return (
                f"{PGO_GUIDANCE_UNAVAILABLE_PREFIX} "
                f"{self._format_pgo_gradle_failure(result)}"
            )

        guidance = format_pgo_near_call_guidance(
            self.pgo_near_call_report_dir,
            active_class.uncovered_call_sites,
        )
        if guidance.startswith(PGO_GUIDANCE_UNAVAILABLE_PREFIX):
            self._print_dynamic_access_detail("PGO near-call guidance unavailable", indent_level=2)
        else:
            self._print_dynamic_access_detail("PGO near-call guidance refreshed", indent_level=2)
        return guidance

    def _run_reachability_analysis(self, agent, active_class, pgo_failure_reason: str) -> dict:
        self._print_dynamic_access_detail("agent: running PGO reachability analysis", indent_level=2)
        response = agent.send_prompt(self._render_reachability_analysis_prompt(active_class, pgo_failure_reason))
        self._print_dynamic_access_detail("agent: PGO reachability analysis complete", indent_level=2)
        try:
            return self._parse_json_response(response)
        except (AttributeError, TypeError, ValueError) as error:
            self._print_failure_analysis(
                "pgo_reachability_analysis",
                issue="invalid_json_response",
                indent_level=2,
                error=error,
            )
            raise PgoNearCallGuidanceUnavailableError("reachability_analysis_invalid") from error

    def _render_reachability_analysis_prompt(self, active_class, pgo_failure_reason: str) -> str:
        return self._render_prompt(
            self.REACHABILITY_ANALYSIS_PROMPT_KEY,
            runtime_environment_summary=self._runtime_environment_summary,
            test_runtime_classpath=self._format_test_runtime_classpath(self._collect_test_runtime_classpath()),
            build_gradle_contents=self._read_build_gradle_contents(),
            active_class_name=active_class.class_name,
            active_class_source_file=active_class.resolved_source_file or active_class.source_file or "N/A",
            uncovered_dynamic_access_calls=format_call_sites(active_class.uncovered_call_sites),
            pgo_failure_reason=pgo_failure_reason,
            source_context_overview=self.context.get("source_context_overview") or "- Source context not available.",
        )

    @classmethod
    def _next_uncovered_call_site(
            cls,
            current_report: DynamicAccessCoverageReport,
            exhausted_call_sites: set[str],
    ) -> tuple[DynamicAccessClass, DynamicAccessCallSite] | None:
        for class_coverage in current_report.classes:
            for call_site in class_coverage.uncovered_call_sites:
                if call_site.key not in exhausted_call_sites:
                    return class_coverage, call_site
        return None

    @staticmethod
    def _single_call_class(
            class_coverage: DynamicAccessClass,
            call_site: DynamicAccessCallSite,
    ) -> DynamicAccessClass:
        return DynamicAccessClass(
            class_name=class_coverage.class_name,
            source_file=class_coverage.source_file,
            resolved_source_file=class_coverage.resolved_source_file,
            total_calls=1,
            covered_calls=1 if call_site.covered else 0,
            call_sites=[call_site],
        )

    @classmethod
    def _compute_single_call_delta(
            cls,
            previous_report: DynamicAccessCoverageReport | None,
            current_report: DynamicAccessCoverageReport,
            active_class: DynamicAccessClass,
            target_key: str,
    ) -> DynamicAccessClassDelta:
        previous_call = None
        if previous_report is not None:
            previous_call = cls._find_call_site(previous_report, active_class.class_name, target_key)
        current_call = cls._find_call_site(current_report, active_class.class_name, target_key)
        if current_call is None:
            current_call = active_class.call_sites[0]
        newly_covered = []
        if current_call.covered and (previous_call is None or not previous_call.covered):
            newly_covered = [current_call]
        still_uncovered = [] if current_call.covered else [current_call]
        return DynamicAccessClassDelta(newly_covered=newly_covered, still_uncovered=still_uncovered)

    @classmethod
    def _is_call_site_covered(
            cls,
            current_report: DynamicAccessCoverageReport,
            class_name: str,
            target_key: str,
    ) -> bool:
        call_site = cls._find_call_site(current_report, class_name, target_key)
        return call_site is not None and call_site.covered

    @staticmethod
    def _find_call_site(
            current_report: DynamicAccessCoverageReport,
            class_name: str,
            target_key: str,
    ) -> DynamicAccessCallSite | None:
        current_class = current_report.get_class(class_name)
        if current_class is None:
            return None
        for call_site in current_class.call_sites:
            if call_site.key == target_key:
                return call_site
        return None

    @staticmethod
    def _initial_uncovered_call_positions(current_report: DynamicAccessCoverageReport) -> dict[str, int]:
        positions = {}
        position = 1
        for class_coverage in current_report.classes:
            for call_site in class_coverage.uncovered_call_sites:
                positions.setdefault(call_site.key, position)
                position += 1
        return positions

    @staticmethod
    def _classes_for_exhausted_calls(
            current_report: DynamicAccessCoverageReport,
            exhausted_call_sites: set[str],
    ) -> set[str]:
        exhausted_classes = set()
        for class_coverage in current_report.classes:
            uncovered_keys = {call_site.key for call_site in class_coverage.uncovered_call_sites}
            if uncovered_keys and uncovered_keys.issubset(exhausted_call_sites):
                exhausted_classes.add(class_coverage.class_name)
        return exhausted_classes

    @staticmethod
    def _active_call_guidance_key(active_class: DynamicAccessClass) -> str:
        if not active_class.call_sites:
            return active_class.class_name
        return "\u0000".join((active_class.class_name, active_class.call_sites[0].key))

    @classmethod
    def _print_call_completion_progress(
            cls,
            active_class: DynamicAccessClass,
            completed_call_count: int,
            total_call_count: int,
            current_report: DynamicAccessCoverageReport,
    ) -> None:
        remaining_calls = max(current_report.total_calls - current_report.covered_calls, 0)
        target = format_call_sites(active_class.call_sites)
        cls._print_dynamic_access_message(cls.PROGRESS_DIVIDER)
        cls._print_dynamic_access_message(
            "Progress after target {target}: calls {completed}/{total} processed; "
            "overall coverage {covered}/{call_total} covered ({remaining} remaining)".format(
                target=target,
                completed=completed_call_count,
                total=total_call_count,
                covered=current_report.covered_calls,
                call_total=current_report.total_calls,
                remaining=remaining_calls,
            )
        )
        cls._print_dynamic_access_message(cls.PROGRESS_DIVIDER)

    def _record_unreachable_call_sites(
            self,
            agent,
            active_class: DynamicAccessClass,
            pgo_failure_reason: str,
    ) -> list[dict]:
        analysis = None
        try:
            analysis = self._run_reachability_analysis(agent, active_class, pgo_failure_reason)
            unreachable_entries = self._unreachable_entries_from_analysis(active_class, pgo_failure_reason, analysis)
        except PgoNearCallGuidanceUnavailableError as error:
            self._print_failure_analysis(
                "pgo_reachability_analysis",
                issue=str(error),
                indent_level=2,
            )
            unreachable_entries = []

        if not unreachable_entries:
            unreachable_entries = self._fallback_unreachable_entries(active_class, pgo_failure_reason, analysis)

        new_entries = []
        known_keys = {
            (
                entry.get("metadataType"),
                entry.get("trackedApi"),
                entry.get("frame"),
            )
            for entry in self.dynamic_access_unreachable
        }
        for entry in unreachable_entries:
            key = (
                entry.get("metadataType"),
                entry.get("trackedApi"),
                entry.get("frame"),
            )
            if key in known_keys:
                continue
            new_entries.append(entry)
            known_keys.add(key)

        if new_entries:
            self.dynamic_access_unreachable.extend(new_entries)
            for entry in new_entries:
                self._print_failure_analysis(
                    "dynamic_access_reachability",
                    issue="unreachable_in_current_setup",
                    indent_level=2,
                    class_name=entry["className"],
                    tracked_api=entry["trackedApi"],
                    frame=entry["frame"],
                    reason=entry["reason"],
                )
        return unreachable_entries

    def _fallback_unreachable_entries(
            self,
            active_class: DynamicAccessClass,
            pgo_failure_reason: str,
            analysis: dict | None,
    ) -> list[dict]:
        reason = ""
        confidence = "low"
        if isinstance(analysis, dict):
            confidence = str(analysis.get("confidence") or confidence)
        if not reason:
            reason = (
                "PGO-guided exploration could not reach this call from existing tests in the current setup. "
                "The reachability analysis did not return an unreachable classification for the target, "
                "so Forge recorded this conservative fallback for follow-up."
            )
        if pgo_failure_reason:
            reason = "{reason} PGO diagnostic context: {context}".format(
                reason=reason,
                context=pgo_failure_reason,
            )
        if not reason:
            reason = "Forge could not produce a conclusive reachability analysis for this call site."
        return [
            self._unreachable_entry(
                active_class,
                call_site,
                {
                    "reason": reason,
                    "confidence": confidence,
                },
                pgo_failure_reason,
            )
            for call_site in active_class.uncovered_call_sites
        ]

    @classmethod
    def _two_sentence_reason(cls, text: str) -> str:
        normalized = " ".join(str(text or "").split())
        if not normalized:
            normalized = "Forge could not reach this dynamic-access call in the current setup."
        sentences = [
            cls._ensure_sentence_punctuation(part.strip())
            for part in re.split(r"(?<=[.!?])\s+", normalized)
            if part.strip()
        ]
        if not sentences:
            sentences = ["Forge could not reach this dynamic-access call in the current setup."]
        if len(sentences) == 1:
            sentences.append(
                "Forge recorded it as unreachable for this run so PGO exploration can continue to the next call."
            )
        return " ".join(sentences[:2])

    @staticmethod
    def _ensure_sentence_punctuation(sentence: str) -> str:
        if not sentence:
            return sentence
        if sentence[-1] in ".!?":
            return sentence
        return sentence + "."

    def _collect_test_runtime_classpath(self) -> list[str]:
        result = self._run_gradle_command_with_output([
            "./gradlew",
            "listTestRuntimeClasspath",
            f"-Pcoordinates={self.library}",
            "--quiet",
        ])
        if result.returncode != 0:
            return [
                "unavailable: listTestRuntimeClasspath failed ({issue})".format(
                    issue=self._summarize_gradle_issue(result.stdout),
                )
            ]
        return [
            line.strip()
            for line in result.stdout.splitlines()
            if line.strip()
        ]

    @staticmethod
    def _format_test_runtime_classpath(classpath_entries: list[str]) -> str:
        if not classpath_entries:
            return "- Empty or unavailable."
        lines = ["- {entry}".format(entry=entry) for entry in classpath_entries[:200]]
        if len(classpath_entries) > 200:
            lines.append("- ... {count} more entries".format(count=len(classpath_entries) - 200))
        return "\n".join(lines)

    def _read_build_gradle_contents(self) -> str:
        build_gradle_path = os.path.join(
            self.reachability_repo_path,
            "tests",
            "src",
            self.group,
            self.artifact,
            self.test_version,
            "build.gradle",
        )
        try:
            with open(build_gradle_path, encoding="utf-8") as build_gradle_file:
                return build_gradle_file.read()
        except FileNotFoundError:
            return "// build.gradle not found"

    def _format_pgo_gradle_failure(self, result) -> str:
        issue = self._summarize_gradle_issue(result.stdout)
        excerpt = self._extract_pgo_failure_excerpt(result.stdout)
        if excerpt:
            return (
                "generatePgoDynamicAccessNearCallReport failed "
                f"(exit_code={result.returncode}, issue={issue}).\n"
                "Relevant Gradle/native-image output:\n"
                f"{excerpt}"
            )
        return (
            "generatePgoDynamicAccessNearCallReport failed "
            f"(exit_code={result.returncode}, issue={issue})."
        )

    @staticmethod
    def _extract_pgo_failure_excerpt(output: str, max_lines: int = 120, max_chars: int = 12000) -> str:
        """Extract diagnostic lines that are useful to the reachability analyst."""
        patterns = (
            "Execution failed for task",
            "> Task :nativeTestCompile FAILED",
            "> Task :generatePgoDynamicAccessNearCallReport FAILED",
            "Error:",
            "Caused by:",
            "mismatch with existing registration",
            "Cluster members:",
            "Process 'command",
            "Fatal error:",
            "Could not find agent library native-image-agent",
            "FAILURE: Build failed with an exception.",
            "* What went wrong:",
            "BUILD FAILED",
        )
        selected: list[str] = []
        include_following_lines = 0
        for raw_line in output.splitlines():
            line = raw_line.strip()
            if not line:
                continue
            if any(pattern in line for pattern in patterns):
                selected.append(line)
                include_following_lines = 3
            elif include_following_lines > 0:
                selected.append(line)
                include_following_lines -= 1

        if not selected:
            selected = [
                line.strip()
                for line in output.splitlines()
                if line.strip()
            ][-40:]

        deduplicated: list[str] = []
        for line in selected:
            if deduplicated and deduplicated[-1] == line:
                continue
            deduplicated.append(line)

        excerpt = "\n".join(deduplicated[:max_lines])
        if len(excerpt) > max_chars:
            return excerpt[:max_chars] + "\n... truncated ..."
        return excerpt

    @staticmethod
    def _parse_json_response(response: str) -> dict:
        stripped = response.strip()
        if stripped.startswith("{") and stripped.endswith("}"):
            return json.loads(stripped)
        match = re.search(r"```(?:json)?\s*(\{.*?\})\s*```", response, re.DOTALL)
        if match:
            return json.loads(match.group(1))
        match = re.search(r"(\{.*\})", response, re.DOTALL)
        if match:
            return json.loads(match.group(1))
        raise ValueError("No JSON object found in reachability analysis response")

    def _unreachable_entries_from_analysis(self, active_class, pgo_failure_reason: str, analysis: dict) -> list[dict]:
        analyzed_call_sites = analysis.get("callSites")
        if not isinstance(analyzed_call_sites, list):
            raise PgoNearCallGuidanceUnavailableError("reachability_analysis_missing_callSites")

        current_call_sites_by_key = {
            self._call_site_identity(call_site): call_site
            for call_site in active_class.uncovered_call_sites
        }
        entries = []
        for analyzed in analyzed_call_sites:
            if not isinstance(analyzed, dict) or analyzed.get("reachable") is not False:
                continue
            key = self._analysis_call_site_identity(analyzed)
            call_site = current_call_sites_by_key.get(key)
            if call_site is None:
                continue
            entries.append(self._unreachable_entry(active_class, call_site, analyzed, pgo_failure_reason))
        return entries

    def _unreachable_entry(
            self,
            active_class,
            call_site: DynamicAccessCallSite,
            analyzed: dict,
            pgo_failure_reason: str,
    ) -> dict:
        return {
            "className": active_class.class_name,
            "sourceFile": active_class.resolved_source_file or active_class.source_file,
            "metadataType": call_site.metadata_type,
            "trackedApi": call_site.tracked_api,
            "frame": call_site.frame,
            "line": call_site.line,
            "reason": self._two_sentence_reason(
                str(analyzed.get("reason") or "Marked unreachable in the current setup.")
            ),
            "confidence": str(analyzed.get("confidence") or "unspecified"),
            "pgoFailureReason": pgo_failure_reason,
            "runtimeEnvironment": self._runtime_environment_summary,
        }

    @classmethod
    def _all_uncovered_call_sites_recorded_unreachable(cls, active_class, unreachable_entries: list[dict]) -> bool:
        unreachable_keys = {
            (
                entry.get("metadataType"),
                entry.get("trackedApi"),
                entry.get("frame"),
            )
            for entry in unreachable_entries
        }
        return all(
            cls._call_site_identity(call_site) in unreachable_keys
            for call_site in active_class.uncovered_call_sites
        )

    @staticmethod
    def _call_site_identity(call_site: DynamicAccessCallSite) -> tuple[str, str, str]:
        return (call_site.metadata_type, call_site.tracked_api, call_site.frame)

    @staticmethod
    def _analysis_call_site_identity(analyzed: dict) -> tuple[str | None, str | None, str | None]:
        return (
            analyzed.get("metadataType"),
            analyzed.get("trackedApi"),
            analyzed.get("frame"),
        )

    def _should_continue_class_attempts(self, class_attempts: int, class_attempts_without_progress: int) -> bool:
        return class_attempts_without_progress < self.max_class_iterations

    def _format_class_attempt_status(self, class_attempts: int, class_attempts_without_progress: int) -> str:
        return "attempt {attempt} (no-progress budget used: {stalled}/{budget})".format(
            attempt=class_attempts + 1,
            stalled=class_attempts_without_progress,
            budget=self.max_class_iterations,
        )

    def _format_class_exhaustion_attempts(self, class_attempts: int, class_attempts_without_progress: int) -> str:
        return "{attempts} ({stalled} consecutive without progress)".format(
            attempts=class_attempts,
            stalled=class_attempts_without_progress,
        )
