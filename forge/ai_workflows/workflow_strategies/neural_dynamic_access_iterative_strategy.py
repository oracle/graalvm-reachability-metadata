# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess

from ai_workflows.workflow_strategies.dynamic_access_iterative_strategy import DynamicAccessIterativeStrategy
from ai_workflows.workflow_strategies.workflow_strategy import (
    RUN_STATUS_CHUNK_READY,
    RUN_STATUS_FAILURE,
    RUN_STATUS_SUCCESS,
    WorkflowStrategy,
)
from utility_scripts.dynamic_access_report import load_dynamic_access_coverage_report
from utility_scripts.library_finalization import run_library_finalization
from utility_scripts.metadata_index import resolve_metadata_version
from utility_scripts.neural_dynamic_access import (
    canonicalize_neural_call_reports,
    dynamic_access_stats_from_coverage_report,
    write_neural_dynamic_access_coverage_report,
)
from utility_scripts.stage_logger import log_stage


@WorkflowStrategy.register("neural_dynamic_access_iterative")
class NeuralDynamicAccessIterativeStrategy(DynamicAccessIterativeStrategy):
    """Dynamic-access workflow using a frozen source-derived call-site seed."""

    REQUIRED_PROMPTS = ["neural-dynamic-access-preprocess", "dynamic-access-iteration"]

    def __init__(self, strategy_obj: dict, **context):
        super().__init__(strategy_obj, **context)
        self.extra_metrics: dict[str, int | float | str] = {}
        self.neural_report_root = os.path.join(
            self.reachability_repo_path,
            "tests",
            "src",
            self.group,
            self.artifact,
            self.test_version,
            "build",
            "reports",
            "neural-dynamic-access",
        )
        self.neural_raw_output_dir = os.path.join(self.neural_report_root, "agent-output")
        self.neural_seed_dir = os.path.join(self.neural_report_root, "seed")
        self.neural_seed_checksum_path = os.path.join(self.neural_report_root, "seed.sha256")
        self.jacoco_report_path = os.path.join(
            self.reachability_repo_path,
            "tests",
            "src",
            self.group,
            self.artifact,
            self.test_version,
            "build",
            "reports",
            "jacoco",
            "test",
            "jacocoTestReport.xml",
        )
        self._neural_seed_checksum: str | None = None

    @staticmethod
    def _print_neural_message(message: str) -> None:
        log_stage("neural-dynamic-access", message)

    @classmethod
    def _print_neural_detail(cls, message: str, indent_level: int = 1) -> None:
        log_stage("neural-dynamic-access", message, indent_level=indent_level)

    def run(self, agent, checkpoint_commit_hash):
        try:
            self._run_preprocess(agent)
        except ValueError as exc:
            self._print_neural_message(f"preprocess failed: {exc}")
            return RUN_STATUS_FAILURE, 1, 0

        initial_report = self._generate_dynamic_access_report()
        if self._should_fallback_to_basic_flow(initial_report):
            self._print_neural_message(
                "neural report produced no usable dynamic-access guidance: "
                f"cause={self._dynamic_access_fallback_cause()} coordinate={self.library}"
            )
            return RUN_STATUS_FAILURE, 1, 0
        self._maybe_activate_large_library_series(initial_report)

        global_iterations = 1
        phase_ok, extra_iterations = self._run_dynamic_access_phase(agent, initial_report)
        global_iterations += extra_iterations
        if self._last_phase_status == RUN_STATUS_CHUNK_READY:
            return RUN_STATUS_CHUNK_READY, global_iterations, 1
        if not phase_ok:
            subprocess.run(["git", "reset", "--hard", checkpoint_commit_hash], check=False)
            return RUN_STATUS_FAILURE, global_iterations, 0

        return RUN_STATUS_SUCCESS, global_iterations, 1

    def _finalize_successful_iteration(self) -> tuple[str, str | None]:
        """Finalize and commit with stats patched from the frozen neural seed."""
        log_stage("generate-metadata", f"Running generateMetadata for {self.library}")
        self._run_command(f"./gradlew generateMetadata -Pcoordinates={self.library} --agentAllowedPackages=fromJar")
        test_retry_status = self._run_test_with_retry(self.library)
        if test_retry_status == RUN_STATUS_FAILURE:
            return test_retry_status, None
        if not run_library_finalization(
            repo_path=self.reachability_repo_path,
            library=self.library,
            group=self.group,
            artifact=self.artifact,
            library_version=self.version,
            model_name=self.model_name,
        ):
            return RUN_STATUS_FAILURE, None
        if not self._patch_library_stats_with_neural_dynamic_access():
            return RUN_STATUS_FAILURE, None
        log_stage("commit-iteration", f"Running commit iteration for {self.library}")
        if not self._commit_library_iteration():
            return RUN_STATUS_FAILURE, None
        checkpoint_commit_hash = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
        return test_retry_status, checkpoint_commit_hash

    def _run_preprocess(self, agent) -> None:
        self._print_neural_message("preprocess: generating source-derived raw call files")
        os.makedirs(self.neural_raw_output_dir, exist_ok=True)
        prompt = self._render_prompt(
            "neural-dynamic-access-preprocess",
            neural_dynamic_access_output_dir=os.path.relpath(
                self.neural_raw_output_dir,
                self.reachability_repo_path,
            ),
        )
        token_snapshot_before = self._agent_token_snapshot(agent)
        agent.send_prompt(prompt)
        self.extra_metrics.update(
            self._neural_report_token_metrics(token_snapshot_before, self._agent_token_snapshot(agent))
        )
        self._print_neural_detail("agent: complete")

        seed = canonicalize_neural_call_reports(self.neural_raw_output_dir, self.neural_seed_dir)
        self.extra_metrics["neural_report_call_sites"] = seed.total_calls
        self._neural_seed_checksum = seed.checksum
        os.makedirs(os.path.dirname(self.neural_seed_checksum_path), exist_ok=True)
        with open(self.neural_seed_checksum_path, "w", encoding="utf-8") as checksum_file:
            checksum_file.write(f"{seed.checksum}  {os.path.relpath(seed.path, self.reachability_repo_path)}\n")
        self._print_neural_detail(
            "seed frozen: {calls} calls checksum={checksum} path={path}".format(
                calls=seed.total_calls,
                checksum=seed.checksum,
                path=os.path.relpath(seed.path, self.reachability_repo_path),
            )
        )

    @staticmethod
    def _agent_token_snapshot(agent) -> dict[str, int | None]:
        cached_input_tokens = getattr(agent, "cached_input_tokens_used", None)
        if cached_input_tokens is not None:
            cached_input_tokens = int(cached_input_tokens or 0)
        return {
            "input_tokens": int(getattr(agent, "total_tokens_sent", 0) or 0),
            "cached_input_tokens": cached_input_tokens,
            "output_tokens": int(getattr(agent, "total_tokens_received", 0) or 0),
        }

    def _neural_report_token_metrics(
            self,
            before: dict[str, int | None],
            after: dict[str, int | None],
    ) -> dict[str, int | float]:
        input_tokens = max(int(after["input_tokens"] or 0) - int(before["input_tokens"] or 0), 0)
        output_tokens = max(int(after["output_tokens"] or 0) - int(before["output_tokens"] or 0), 0)
        cached_input_tokens = None
        if after["cached_input_tokens"] is not None or before["cached_input_tokens"] is not None:
            cached_input_tokens = max(
                int(after["cached_input_tokens"] or 0) - int(before["cached_input_tokens"] or 0),
                0,
            )

        metrics: dict[str, int | float] = {
            "neural_report_input_tokens_used": input_tokens,
            "neural_report_output_tokens_used": output_tokens,
        }
        if cached_input_tokens is not None:
            metrics["neural_report_cached_input_tokens_used"] = cached_input_tokens
        metrics["neural_report_cost_usd"] = self._token_cost(input_tokens, cached_input_tokens, output_tokens)
        return metrics

    def _token_cost(self, input_tokens: int, cached_input_tokens: int | None, output_tokens: int) -> float:
        from utility_scripts.metrics_writer import (
            CACHED_INPUT_TOKEN_RATE_PER_1M_BY_MODEL,
            DEFAULT_CACHED_INPUT_RATE_PER_1M,
            DEFAULT_INPUT_RATE_PER_1M,
            DEFAULT_OUTPUT_RATE_PER_1M,
            INPUT_TOKEN_RATE_PER_1M_BY_MODEL,
            OUTPUT_TOKEN_RATE_PER_1M_BY_MODEL,
            calc_input_cost,
            calc_output_cost,
        )

        input_rate = INPUT_TOKEN_RATE_PER_1M_BY_MODEL.get(self.model_name, DEFAULT_INPUT_RATE_PER_1M)
        cached_input_rate = CACHED_INPUT_TOKEN_RATE_PER_1M_BY_MODEL.get(
            self.model_name,
            DEFAULT_CACHED_INPUT_RATE_PER_1M,
        )
        output_rate = OUTPUT_TOKEN_RATE_PER_1M_BY_MODEL.get(self.model_name, DEFAULT_OUTPUT_RATE_PER_1M)
        billable_input_tokens = input_tokens
        cached_input_cost = 0.0
        if cached_input_tokens is not None:
            billable_input_tokens = max(input_tokens - cached_input_tokens, 0)
            cached_input_cost = calc_input_cost(cached_input_tokens, cached_input_rate)
        input_cost = calc_input_cost(billable_input_tokens, input_rate)
        output_cost = calc_output_cost(output_tokens, output_rate)
        return round(input_cost + cached_input_cost + output_cost, 4)

    def _generate_dynamic_access_report(self, indent_level: int = 0):
        self._print_dynamic_access_detail(
            "report: correlating frozen neural seed for {library}".format(library=self.library),
            indent_level=indent_level,
        )
        result = self._run_gradle_command_with_output([
            "./gradlew",
            "jacocoTestReport",
            f"-Pcoordinates={self.library}",
        ])
        if result.returncode != 0:
            self._last_dynamic_access_report_issue = "jacoco_task_failed"
            self._print_dynamic_access_detail(
                "Coverage report refresh failed:",
                indent_level=indent_level,
            )
            self._print_dynamic_access_detail(
                "cause=jacoco_task_failed task=jacocoTestReport exit_code={exit_code}".format(
                    exit_code=result.returncode,
                ),
                indent_level=indent_level,
            )
            self._print_failure_analysis(
                "jacocoTestReport",
                issue=self._summarize_gradle_issue(result.stdout),
                indent_level=indent_level,
                exit_code=result.returncode,
            )
            print(result.stdout)
            return None

        try:
            write_neural_dynamic_access_coverage_report(
                coordinate=self.library,
                seed_dir=self.neural_seed_dir,
                jacoco_report=self.jacoco_report_path,
                output_path=self.dynamic_access_report_path,
            )
            report = load_dynamic_access_coverage_report(
                self.dynamic_access_report_path,
                source_context_files=self.context.get("source_context_files") or [],
            )
        except (FileNotFoundError, ValueError) as exc:
            self._last_dynamic_access_report_issue = "neural_report_correlation_failed"
            self._print_failure_analysis(
                "neural_dynamic_access_correlation",
                issue=str(exc),
                indent_level=indent_level,
                seed_dir=self.neural_seed_dir,
                jacoco_report=self.jacoco_report_path,
            )
            return None

        self._print_dynamic_access_detail(
            "report: {library} -> {covered}/{total} covered using frozen neural seed".format(
                library=self.library,
                covered=report.covered_calls,
                total=report.total_calls,
            ),
            indent_level=indent_level,
        )
        if not report.has_dynamic_access or report.total_calls == 0:
            self._last_dynamic_access_report_issue = "no_dynamic_access"
        else:
            self._last_dynamic_access_report_issue = "ok"
        return report

    def _run_native_test_verification_gate(self, class_name: str) -> bool:
        """Skip the inherited native-test verification gate for neural DA experiments."""
        self._print_dynamic_access_detail(
            f"native-test gate: skipped for neural dynamic-access workflow class={class_name}",
            indent_level=2,
        )
        return True

    def _run_gradle_command(self, command: list[str]) -> bool:
        result = super()._run_gradle_command(command)
        if result and len(command) > 1 and command[1] == "generateLibraryStats":
            return self._patch_library_stats_with_neural_dynamic_access()
        return result

    def _patch_library_stats_with_neural_dynamic_access(self) -> bool:
        self._print_neural_message("stats: replacing dynamic-access stats with frozen neural correlation")
        try:
            report_payload = write_neural_dynamic_access_coverage_report(
                coordinate=self.library,
                seed_dir=self.neural_seed_dir,
                jacoco_report=self.jacoco_report_path,
                output_path=self.dynamic_access_report_path,
            )
        except (FileNotFoundError, ValueError) as exc:
            self._print_neural_message(f"stats patch failed: {exc}")
            return False

        metadata_version = resolve_metadata_version(
            self.reachability_repo_path,
            self.group,
            self.artifact,
            self.version,
        )
        stats_path = os.path.join(
            self.reachability_repo_path,
            "stats",
            self.group,
            self.artifact,
            metadata_version,
            "stats.json",
        )
        if not os.path.isfile(stats_path):
            self._print_neural_message(f"stats patch failed: missing {os.path.relpath(stats_path, self.reachability_repo_path)}")
            return False

        with open(stats_path, "r", encoding="utf-8") as stats_file:
            stats_payload = json.load(stats_file)
        for version_entry in stats_payload.get("versions", []):
            if version_entry.get("version") == self.version:
                version_entry["dynamicAccess"] = dynamic_access_stats_from_coverage_report(report_payload)
                break
        else:
            self._print_neural_message(f"stats patch failed: version {self.version} not found in stats")
            return False

        with open(stats_path, "w", encoding="utf-8") as stats_file:
            json.dump(stats_payload, stats_file, indent=2, ensure_ascii=False)
            stats_file.write("\n")
        self._print_neural_detail(
            "stats patched: {path}".format(path=os.path.relpath(stats_path, self.reachability_repo_path))
        )
        return True
