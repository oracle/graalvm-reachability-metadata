# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import re

from ai_workflows.workflow_strategies.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from utility_scripts.dynamic_access_report import DynamicAccessCallSite, format_call_sites
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
        self._pgo_guidance_by_class: dict[str, str] = {}
        self.dynamic_access_unreachable: list[dict] = []
        self._runtime_environment_summary = str(
            self.context.get("runtime_environment_summary") or collect_runtime_environment_summary()
        )

    def _dynamic_access_prompt_extras(self, active_class) -> dict:
        guidance = self._pgo_guidance_by_class.get(active_class.class_name)
        if guidance is None:
            guidance = self._refresh_pgo_near_call_guidance(active_class)
            self._pgo_guidance_by_class[active_class.class_name] = guidance
        return {
            "pgo_near_call_guidance": guidance,
        }

    def _prepare_dynamic_access_attempt(self, agent, active_class, current_report, class_attempts: int) -> bool:
        guidance = self._refresh_pgo_near_call_guidance(active_class)
        if not guidance.startswith(PGO_GUIDANCE_UNAVAILABLE_PREFIX):
            self._pgo_guidance_by_class[active_class.class_name] = guidance
            return True

        issue = guidance.removeprefix(PGO_GUIDANCE_UNAVAILABLE_PREFIX).strip()
        self._print_failure_analysis(
            "formatPgoNearCallGuidance",
            issue=issue,
            indent_level=2,
        )
        analysis = self._run_reachability_analysis(agent, active_class, issue)
        unreachable_entries = self._unreachable_entries_from_analysis(active_class, issue, analysis)
        if unreachable_entries:
            self.dynamic_access_unreachable.extend(unreachable_entries)
            for entry in unreachable_entries:
                self._print_failure_analysis(
                    "dynamic_access_reachability",
                    issue="unreachable_in_current_setup",
                    indent_level=2,
                    class_name=entry["className"],
                    tracked_api=entry["trackedApi"],
                    frame=entry["frame"],
                    reason=entry["reason"],
                )

        if self._all_uncovered_call_sites_recorded_unreachable(active_class, unreachable_entries):
            self._pgo_guidance_by_class.pop(active_class.class_name, None)
            return False

        raise PgoNearCallGuidanceUnavailableError("near_call_guidance_unavailable")

    def _run_dynamic_access_phase(self, agent, current_report=None) -> tuple[bool, int]:
        try:
            return super()._run_dynamic_access_phase(agent, current_report)
        except PgoNearCallGuidanceUnavailableError as error:
            self._print_failure_analysis(
                "pgo_profile_driven_exploration",
                issue=str(error),
                indent_level=1,
            )
            return False, 0

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
            raise PgoNearCallGuidanceUnavailableError("gradle_diagnostic_task_failed")

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
        except ValueError as error:
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
            "reason": str(analyzed.get("reason") or "Marked unreachable in the current setup."),
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
