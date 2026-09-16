# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The reporter-requested metadata phase shared by workflow strategies.

Generate-then-fill coverage of reporter-requested metadata on the analysis
role (§forge/FS-forge-agent-runtime-selection), mixed into the strategy
base class so every workflow can attempt the reporter's request.
"""

import os
import subprocess

from ai_workflows.agents.agent_runtime import analysis_agent_run
from utility_scripts.issue_requested_metadata import (
    has_issue_requested_metadata_context as has_reporter_metadata_context,
)
from utility_scripts.metadata_index import resolve_metadata_version, resolve_test_version
from utility_scripts.run_location import (
    PHASE_EXPLORE as RUN_PHASE_EXPLORE,
    STEP_GENERATE_TESTS,
    RunLocation,
    record_step_failure,
    run_step,
)
from utility_scripts.stage_logger import log_detail
from utility_scripts.strategy_loader import load_prompt_template
from utility_scripts.task_logs import display_log_path

ISSUE_REQUESTED_METADATA_PROMPT_KEY = "issue-requested-metadata"
ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY = "issue-requested-metadata-fill"
#: The phase runs on the analysis role with its own prompts, so a strategy that
#: never declared them still gets the reporter's request attempted.
#: §forge/FS-forge-agent-runtime-selection
DEFAULT_ISSUE_REQUESTED_METADATA_PROMPTS = {
    ISSUE_REQUESTED_METADATA_PROMPT_KEY:
        "prompt_templates/issue_requested_metadata/issue-requested-metadata.md",
    ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY:
        "prompt_templates/issue_requested_metadata/issue-requested-metadata-fill.md",
}
ISSUE_REQUESTED_METADATA_TASK_TYPE = "issue-requested-metadata"
ISSUE_REQUESTED_METADATA_TIMEOUT_SECONDS = 1800
ISSUE_REQUESTED_METADATA_TEST_ITERATIONS = 3
ISSUE_REQUESTED_METADATA_MAX_OUTPUT_CHARS = 12000


def _trim_output_tail(text: str, limit: int) -> str:
    """Return the tail of command output constrained to `limit` characters."""
    if len(text) <= limit:
        return text
    return "[... truncated ...]\n" + text[-limit:]


class IssueRequestedMetadataPhase:
    """Mixin owning the reporter-requested metadata phase."""

    def has_issue_requested_metadata_context(self) -> bool:
        """Return whether this run carries reporter-requested metadata to cover."""
        return has_reporter_metadata_context(self.context.get("issue_requested_metadata_context"))

    def run_issue_requested_metadata_phase(self) -> tuple[bool, int]:
        """Cover reporter-requested metadata, independent of dynamic access.

        Generate-then-fill: the analysis agent writes tests, the engine traces
        them with `generateMetadata`, and the analysis agent hand-writes only the
        requested entries tracing missed (§root/FS-test-contract.2.7). The phase
        succeeds only when the whole suite passes, so "reached `nativeTest`" is
        never mistaken for a satisfied request.
        """
        if not self.has_issue_requested_metadata_context():
            return True, 0

        checkpoint = self._current_head_commit()
        iterations = 0
        with run_step(RUN_PHASE_EXPLORE, STEP_GENERATE_TESTS, operand="reporter-requested metadata"):
            self._print_issue_requested_metadata_message("agent: writing tests for the reporter-requested metadata")
            write_prompt = self._render_issue_requested_metadata_prompt(ISSUE_REQUESTED_METADATA_PROMPT_KEY)
            iterations += 1
            if not self._run_issue_requested_metadata_turn(write_prompt):
                return self._fail_issue_requested_metadata_phase(
                    checkpoint, iterations, "analysis_agent_turn_failed",
                )
            # The engine owns every Gradle command; the agent only edits.
            # §forge/AR-forge-strategy-agent-boundary
            last_generate_output: str = ""
            last_generate_failed_task: str | None = None
            generation_succeeded: bool = False
            for generate_iteration in range(ISSUE_REQUESTED_METADATA_TEST_ITERATIONS):
                self._print_issue_requested_metadata_message(
                    "generate {current}/{maximum}: running ./gradlew generateMetadata "
                    "-Pcoordinates={library}".format(
                        current=generate_iteration + 1,
                        maximum=ISSUE_REQUESTED_METADATA_TEST_ITERATIONS,
                        library=self.library,
                    )
                )
                last_generate_output = self._run_command_with_env(
                    f"./gradlew generateMetadata -Pcoordinates={self.library}"
                )
                last_generate_failed_task = self._get_first_failed_task(last_generate_output)
                generation_succeeded = (
                    last_generate_failed_task is None
                    and "BUILD SUCCESSFUL" in last_generate_output
                )
                self._print_issue_requested_metadata_message(
                    "generateMetadata: complete (succeeded: {succeeded}, failed task: {task})".format(
                        succeeded=generation_succeeded,
                        task=last_generate_failed_task or "none",
                    )
                )
                if generation_succeeded:
                    break
                if generate_iteration + 1 == ISSUE_REQUESTED_METADATA_TEST_ITERATIONS:
                    break

                self._print_issue_requested_metadata_message(
                    "agent: sending generation failure back for test repair"
                )
                iterations += 1
                if not self._run_issue_requested_metadata_turn(
                        "{prompt}\n\nWhen `./gradlew generateMetadata "
                        "-Pcoordinates={library}` is run this is the error:\n"
                        "{error_output}".format(
                            prompt=write_prompt,
                            library=self.library,
                            error_output=_trim_output_tail(
                                last_generate_output,
                                ISSUE_REQUESTED_METADATA_MAX_OUTPUT_CHARS,
                            ),
                        )
                ):
                    return self._fail_issue_requested_metadata_phase(
                        checkpoint, iterations, "analysis_agent_turn_failed",
                    )

            if not generation_succeeded:
                return self._fail_issue_requested_metadata_phase(
                    checkpoint,
                    iterations,
                    "generate_metadata_never_succeeded",
                    failed_task=last_generate_failed_task or "unknown",
                )

            self._print_issue_requested_metadata_message(
                "agent: filling requested entries tracing did not produce"
            )
            fill_prompt = self._render_issue_requested_metadata_prompt(
                ISSUE_REQUESTED_METADATA_FILL_PROMPT_KEY,
                issue_requested_metadata_dir=self._issue_requested_metadata_dir(),
                generate_metadata_output=_trim_output_tail(
                    last_generate_output,
                    ISSUE_REQUESTED_METADATA_MAX_OUTPUT_CHARS,
                ),
            )
            iterations += 1
            if not self._run_issue_requested_metadata_turn(fill_prompt):
                return self._fail_issue_requested_metadata_phase(
                    checkpoint, iterations, "analysis_agent_turn_failed",
                )

        last_test_output: str = ""
        last_failed_task: str | None = None
        for test_iteration in range(ISSUE_REQUESTED_METADATA_TEST_ITERATIONS):
            self._print_issue_requested_metadata_message(
                "test {current}/{maximum}: running ./gradlew test -Pcoordinates={library}".format(
                    current=test_iteration + 1,
                    maximum=ISSUE_REQUESTED_METADATA_TEST_ITERATIONS,
                    library=self.library,
                )
            )
            last_test_output = self._run_command_with_env(f"./gradlew test -Pcoordinates={self.library}")
            last_failed_task = self._get_first_failed_task(last_test_output)
            self._print_issue_requested_metadata_message(
                "test: complete (failed task: {failed_task})".format(
                    failed_task=last_failed_task or "none"
                )
            )
            # The request is satisfied only when the suite it is exercised by
            # passes end to end, `nativeTest` included.
            # §forge/FS-local-ci-equivalent-verification
            if last_failed_task is None and "BUILD SUCCESSFUL" in last_test_output:
                self._commit_issue_requested_metadata(
                    f"Issue-requested metadata coverage for {self.library}"
                )
                return True, iterations
            if test_iteration + 1 == ISSUE_REQUESTED_METADATA_TEST_ITERATIONS:
                break

            # Generation already succeeded for the current tests. Retry only the
            # permitted metadata fill so no test change can bypass regeneration.
            self._print_issue_requested_metadata_message(
                "agent: sending test failure back for metadata repair"
            )
            iterations += 1
            if not self._run_issue_requested_metadata_turn(
                    "{prompt}\n\nWhen `./gradlew test -Pcoordinates={library}` "
                    "is run this is the error:\n{error_output}".format(
                        prompt=fill_prompt,
                        library=self.library,
                        error_output=_trim_output_tail(
                            last_test_output,
                            ISSUE_REQUESTED_METADATA_MAX_OUTPUT_CHARS,
                        ),
                    )
            ):
                return self._fail_issue_requested_metadata_phase(
                    checkpoint, iterations, "analysis_agent_turn_failed",
                )

        return self._fail_issue_requested_metadata_phase(
            checkpoint,
            iterations,
            "test_failures_prevented_a_passing_suite",
            failed_task=last_failed_task or "unknown",
        )

    def _run_issue_requested_metadata_turn(self, prompt: str) -> bool:
        """Run one reporter-requested metadata turn on the analysis role.

        The phase reads reporter evidence and repairs from it, so it sits on the
        analysis role rather than on the strategy's own agent field
        (§forge/FS-forge-agent-runtime-selection). The prompt is self-contained:
        strategy persistent instructions forbid the hand-written entry this phase
        is allowed to make, so they are deliberately not passed.
        """
        result = analysis_agent_run(
            working_dir=self.reachability_repo_path,
            context=prompt,
            task_type=ISSUE_REQUESTED_METADATA_TASK_TYPE,
            library=self.library,
            timeout=ISSUE_REQUESTED_METADATA_TIMEOUT_SECONDS,
        )
        if result.return_code != 0:
            self._print_issue_requested_metadata_message(
                "agent: failed ({message}); see {log}".format(
                    message=result.failure_message or "non-zero exit",
                    log=display_log_path(result.log_path),
                )
            )
            return False
        self._print_issue_requested_metadata_message("agent: complete")
        return True

    def _render_issue_requested_metadata_prompt(self, key: str, **extra_context) -> str:
        """Render a phase prompt, falling back to the phase's own template."""
        template_path = self.prompts.get(key) or DEFAULT_ISSUE_REQUESTED_METADATA_PROMPTS[key]
        prompt_context = dict(self.context)
        prompt_context["library"] = self.library
        prompt_context.update(extra_context)
        return load_prompt_template(template_path, **prompt_context)

    def _issue_requested_metadata_dir(self) -> str:
        """Return the repository-relative metadata directory the phase may edit."""
        metadata_version = str(
            self.context.get("metadata_version")
            or resolve_metadata_version(self.reachability_repo_path, self.group, self.artifact, self.version)
        )
        return os.path.join("metadata", self.group, self.artifact, metadata_version)

    def _commit_issue_requested_metadata(self, message: str) -> None:
        """Commit the tests and the metadata the phase produced."""
        test_version = str(
            self.context.get("test_version")
            or resolve_test_version(self.reachability_repo_path, self.group, self.artifact, self.version)
        )
        paths = [
            os.path.join("tests", "src", self.group, self.artifact, test_version),
            self._issue_requested_metadata_dir(),
        ]
        subprocess.run(
            ["git", "add", "-A", "--", *paths],
            cwd=self.reachability_repo_path,
            capture_output=True, check=False,
        )
        # `git diff --cached --quiet -- <paths>` exits non-zero iff they are staged.
        if subprocess.run(
                ["git", "diff", "--cached", "--quiet", "--", *paths],
                cwd=self.reachability_repo_path,
                capture_output=True, check=False,
        ).returncode != 0:
            subprocess.run(
                ["git", "commit", "-m", message, "--", *paths],
                cwd=self.reachability_repo_path,
                capture_output=True, check=False,
            )

    def _fail_issue_requested_metadata_phase(
            self,
            checkpoint: str,
            iterations: int,
            issue: str,
            **details,
    ) -> tuple[bool, int]:
        """Revert the phase to its checkpoint and locate the failure."""
        self._print_issue_requested_metadata_message(
            f"result: {issue}, reverting to checkpoint"
        )
        for key, value in details.items():
            log_detail("issue-requested-metadata", f"{key}={value}", indent_level=1)
        subprocess.run(
            ["git", "reset", "--hard", checkpoint],
            cwd=self.reachability_repo_path,
            capture_output=True, check=False,
        )
        # The first recorded location wins, so an earlier gate keeps its step.
        # §forge/FS-forge-run-location-reporting.3
        record_step_failure(
            location=RunLocation(RUN_PHASE_EXPLORE, STEP_GENERATE_TESTS, "reporter-requested metadata"),
        )
        return False, iterations

    def _current_head_commit(self) -> str:
        """Return the current `HEAD` SHA of the reachability worktree."""
        return subprocess.check_output(
            ["git", "rev-parse", "HEAD"],
            cwd=self.reachability_repo_path,
            text=True,
        ).strip()

    @staticmethod
    def _print_issue_requested_metadata_message(message: str) -> None:
        log_detail("issue-requested-metadata", message)
