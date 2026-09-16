# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Fetches open issues from the graalvm-reachability-metadata project board,
claims them via optimistic locking (set-assignee + wait + verify),
runs the matching pipeline, and updates the project item status.

This module is the CLI entry point of the Forge control-plane dispatcher
(§AR-forge-control-plane): argument parsing and queue wiring live here; the
stages live in the `dispatcher` package (§AR-forge-dispatcher-decomposition),
implementing the orchestration contract in §AR-forge-orchestration to resolve
supported GitHub issues into runs (§FS-forge-issue-resolution-goal).

Usage:
  python forge-metadata.py --label <label> [--limit N] [--offset N|--random-offset]
      [--strategy-name <name>] [--reachability-metadata-path <path>]
  python forge-metadata.py --fixture-testing --issue-number <number>
      --strategy-name <name> [--reachability-metadata-path <path>]
  python forge-metadata.py --review-pr <label> [--limit N]
      [--reachability-metadata-path <path>] [--period <seconds|Nm|Nh|Nd>]
"""

import argparse
import os
import signal
import sys

import ai_workflows.core  # noqa: F401 - triggers strategy registration
from ai_workflows.drivers.add_new_library_support import (
    DEFAULT_STRATEGY_NAME as DEFAULT_NEW_LIBRARY_STRATEGY_NAME,
)
from ai_workflows.drivers.fix_ni_run import (
    DEFAULT_STRATEGY_NAME as DEFAULT_NI_RUN_STRATEGY_NAME,
)
from ai_workflows.drivers.improve_library_coverage import (
    DEFAULT_STRATEGY_NAME as DEFAULT_LIBRARY_UPDATE_STRATEGY_NAME,
)
from ai_workflows.drivers.java_fail_workflow import (
    DEFAULT_JAVAC_STRATEGY,
    DEFAULT_JAVA_RUN_STRATEGY,
)
from git_scripts.github_cli import (
    GitHubRateLimitExceeded,
)
from utility_scripts.repo_path_resolver import (
    resolve_metrics_repo_root,
    resolve_reachability_repo_root,
)
from utility_scripts.run_location import (
    PHASE_CLAIM,
    STEP_CHECK_HOST_REQUIREMENTS,
    STEP_CHECK_STRATEGY_AND_MODEL,
    enter_phase,
    log_step_progress,
    pipeline_step,
    run_step,
)
from utility_scripts.stage_logger import (
    debug_logging_enabled,
    enable_verbose_logging,
    log_debug,
    log_failure_banner,
    log_stage,
    log_success_banner,
)
from utility_scripts.shutdown_signal import (
    is_shutdown_requested,
)
from utility_scripts.strategy_loader import (
    require_strategy_by_name,
)
from utility_scripts.host_graalvm_checks import (
    DEFAULT_GRAALVM_VERSION_CHECK,
    GRAALVM_VERSION_CHECK_ENV_VAR,
    GRAALVM_VERSION_CHECK_MODES,
    resolve_graalvm_version_check,
)
from utility_scripts.host_requirements import (
    QueueRequirements,
    ensure_host_requirements,
    resolve_queue_requirements,
)

try:
    import fcntl
except ImportError:
    fcntl = None





























from dispatcher.claim_setup import (
    build_fixture_claimed_issue,
    claim_issue_for_processing,
)
from dispatcher.config import (
    DEFAULT_MAX_ISSUES,
    DEFAULT_PARALLELISM,
    DEFAULT_TAKE_BLOCKED_ISSUES,
    FORGE_DIR,
    GITHUB_RATE_LIMIT_EXIT_CODE,
    GITHUB_SEARCH_MAX_RESULTS,
    GRADLE_BOOTSTRAP_EXIT_CODE,
    LABEL_JAVAC_FAIL,
    LABEL_JAVA_RUN_FAIL,
    LABEL_LIBRARY_NEW,
    LABEL_LIBRARY_UPDATE,
    LABEL_NI_RUN_FAIL,
    MAX_PARALLELISM,
    PRIORITY_CHOICES,
    PROJECT_NUMBER,
)
from dispatcher.env_config import (
    validate_issue_processing_environment,
    validate_non_negative_integer,
    validate_parallelism,
    validate_review_period,
)
from dispatcher.fixture_support import (
    configure_fixture_testing,
    fixture_issue_run_log,
    get_fixture_run_timestamp,
    is_fixture_testing_enabled,
    require_fixture_github_state,
)
from dispatcher.github_api import resolve_authenticated_user
from dispatcher.interrupts import (
    _handle_sigint,
    clear_user_interrupt_requested,
    describe_active_shutdown_signal_path,
    is_gradle_bootstrap_interrupt,
    is_shutdown_request_interrupt,
    mark_shutdown_requested,
    preserve_user_interrupt_reason,
)
from dispatcher.issue_cache import clear_issue_caches
from dispatcher.issue_processing import (
    process_fixture_issues_for_label,
    process_issues_with_label,
    process_work_queues,
    resolve_random_issue_scan_offset,
)
from dispatcher.issue_queue import (
    get_issue_by_number,
    resolve_user_requested_only,
)
from dispatcher.lifecycle import process_claimed_issue_lifecycle
from dispatcher.queue_config import get_work_queue_configs_from_environment
from dispatcher.review_loop import run_pull_request_review_loop

from dispatcher.queue_config import require_host_requirements

def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Resolve graalvm-reachability-metadata issues automatically."
    )

    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument(
        "--label",
        choices=[LABEL_LIBRARY_NEW, LABEL_LIBRARY_UPDATE, LABEL_JAVAC_FAIL, LABEL_JAVA_RUN_FAIL, LABEL_NI_RUN_FAIL],
        help="GitHub label to filter issues and select the pipeline.",
    )
    mode.add_argument(
        "--issue-number", type=int,
        help="Process a single issue by number. The pipeline label is detected from the issue.",
    )
    mode.add_argument(
        "--review-pr",
        metavar="LABEL",
        help="Review open pull requests with the given GitHub label using the analysis agent.",
    )
    mode.add_argument(
        "--run-work-queues",
        action="store_true",
        help="Process all configured issue and review queues in one Python process.",
    )
    mode.add_argument(
        "--clear-issue-caches",
        action="store_true",
        help="Delete local issue claim/search caches used by work-queue scanning and exit.",
    )

    parser.add_argument(
        "--limit", type=int, default=DEFAULT_MAX_ISSUES,
        help=f"Maximum number of items to process per run (default: {DEFAULT_MAX_ISSUES}).",
    )
    parser.add_argument(
        "--reachability-metadata-path",
        help=(
            "Path to the graalvm-reachability-metadata repository. "
            "If omitted, the parent checkout of this Forge directory is used."
        ),
    )
    parser.add_argument(
        "--strategy-name",
        help="Workflow strategy name to pass to the pipeline. If omitted, the pipeline default is used.",
    )
    parser.add_argument(
        "--fixture-testing",
        action="store_true",
        help=(
            "Use local GitHub issue fixtures instead of live GitHub. Combine with "
            "--issue-number, --label/--limit, or --run-work-queues for the E2E run."
        ),
    )
    parser.add_argument(
        "--graalvm-version-check",
        choices=GRAALVM_VERSION_CHECK_MODES,
        default=None,
        help=(
            "How the startup host-requirement gate treats a GraalVM version mismatch: `strict` stops "
            "the run, `warn` reports it, `off` skips the version match. Native Image and the "
            "reachability-metadata schema stay mandatory in every mode. Defaults to "
            f"{GRAALVM_VERSION_CHECK_ENV_VAR}, then {DEFAULT_GRAALVM_VERSION_CHECK}."
        ),
    )
    parser.add_argument(
        "--period",
        type=validate_review_period,
        help=(
            "Repeat `--review-pr` runs after the given period. Accepts seconds or "
            "s/m/h/d suffixes, for example `300`, `5m`, or `1h`."
        ),
    )
    parser.add_argument(
        "--keep-tests-without-dynamic-access",
        action="store_true",
        help=(
            "Forwarded to the new-library pipeline. Keeps generated tests for dynamic-access "
            "strategies even when no dynamic-access coverage is added."
        ),
    )
    parser.add_argument(
        "--offset", type=validate_non_negative_integer, default=0,
        help="Number of issues to skip from the start of the list (default: 0).",
    )
    parser.add_argument(
        "--priority",
        choices=PRIORITY_CHOICES,
        help=(
            "Process only one issue tier: high, priority, or normal. "
            "Without this option, all tiers are drained in order."
        ),
    )
    parser.add_argument(
        "--random-offset",
        dest="random_offset",
        action="store_true",
        default=None,
        help=(
            "Start issue scanning at a random open issue offset for the selected label "
            "or run-work-queues work queue. "
            f"The random range is capped at GitHub search's first {GITHUB_SEARCH_MAX_RESULTS} results."
        ),
    )
    parser.add_argument(
        "--no-random-offset",
        dest="random_offset",
        action="store_false",
        help="Disable random issue scan offsets for run-work-queues or selected-label runs.",
    )
    parser.add_argument(
        "--user-requested-only",
        dest="user_requested_only",
        action="store_true",
        default=None,
        help=(
            "Fetch only user-requested issue queue items by excluding configured "
            "automation and maintainer issue authors."
        ),
    )
    parser.add_argument(
        "--parallelism",
        type=validate_parallelism,
        default=DEFAULT_PARALLELISM,
        help=f"Number of workflows to run in parallel (1-{MAX_PARALLELISM}).",
    )
    parser.add_argument(
        "--take-blocked-issues",
        dest="take_blocked_issues",
        action="store_true",
        default=DEFAULT_TAKE_BLOCKED_ISSUES,
        help="Claim issues even when GitHub shows open blocking issues. Defaults to disabled.",
    )
    parser.add_argument(
        "-v", "--verbose",
        action="store_true",
        help="Show narration hidden by the compact default output.",
    )

    args = parser.parse_args(argv)
    if args.period is not None and args.review_pr is None:
        parser.error("--period can only be used with --review-pr")
    if args.fixture_testing:
        if args.review_pr is not None:
            parser.error("--fixture-testing cannot be combined with --review-pr")
        if args.offset != 0:
            parser.error("--fixture-testing cannot be combined with --offset")
        if args.random_offset is not None:
            parser.error("--fixture-testing cannot be combined with --random-offset/--no-random-offset")
    if args.random_offset is not None and args.label is None and not args.run_work_queues:
        parser.error("--random-offset/--no-random-offset can only be used with --label or --run-work-queues")
    if args.priority is not None and args.label is None and not args.run_work_queues:
        parser.error("--priority can only be used with --label or --run-work-queues")
    if args.random_offset is True and args.offset != 0:
        parser.error("--random-offset cannot be combined with --offset")
    return args


def log_fixture_testing_selection(issue_number: int, label: str, strategy_name: str | None) -> None:
    """Log fixture-backed E2E routing before workflow execution starts."""
    fixture_state = require_fixture_github_state()
    fixture_path = fixture_state.get_issue_fixture_path(issue_number)
    strategy_override = f", strategy_override={strategy_name}" if strategy_name else ""

    print()
    log_stage(
        "fixture-testing",
        (
            "Selected fixture-backed issue run: "
            f"mode=fixture-testing, issue=#{issue_number}, fixture={fixture_path}, "
            f"queue_label={label}{strategy_override}"
        ),
    )


def process_single_issue(
        issue_number: int,
        base_reachability_metadata_path: str,
        canonical_metrics_repo_path: str,
        strategy_name: str | None,
        keep_tests_without_dynamic_access: bool,
        authenticated_user: str,
        take_blocked_issues: bool = DEFAULT_TAKE_BLOCKED_ISSUES,
) -> bool:
    """Fetch and process a single issue by number, claiming only in live GitHub mode."""
    validate_issue_processing_environment()

    issue, label = get_issue_by_number(issue_number)
    log_debug("issue-scan", f"Issue #{issue_number} matched pipeline label: {label}")

    if is_fixture_testing_enabled():
        with fixture_issue_run_log(issue_number):
            log_fixture_testing_selection(issue_number, label, strategy_name)
            claimed_issue = build_fixture_claimed_issue(
                issue,
                label,
                base_reachability_metadata_path,
                canonical_metrics_repo_path,
            )
            if not claimed_issue:
                log_failure_banner(f"Could not prepare fixture issue #{issue_number}.", file=sys.stderr)
                sys.exit(1)
            return process_claimed_issue_lifecycle(
                claimed_issue,
                strategy_name,
                keep_tests_without_dynamic_access,
                canonical_metrics_repo_path,
            )

    claim_kwargs: dict[str, bool] = {}
    if take_blocked_issues != DEFAULT_TAKE_BLOCKED_ISSUES:
        claim_kwargs["take_blocked_issues"] = take_blocked_issues
    claimed_issue = claim_issue_for_processing(
        issue,
        label,
        base_reachability_metadata_path,
        canonical_metrics_repo_path,
        authenticated_user,
        **claim_kwargs,
    )
    if not claimed_issue:
        log_failure_banner(f"Could not claim issue #{issue_number}.", file=sys.stderr)
        sys.exit(1)

    return process_claimed_issue_lifecycle(
        claimed_issue,
        strategy_name,
        keep_tests_without_dynamic_access,
        canonical_metrics_repo_path,
    )


def main() -> None:
    clear_user_interrupt_requested()
    previous_sigint_handler = signal.getsignal(signal.SIGINT)
    signal.signal(signal.SIGINT, _handle_sigint)
    args = parse_args()
    if args.verbose:
        enable_verbose_logging()
    normal_exit = False

    try:
        if args.fixture_testing:
            # Fix one shared run timestamp; each issue's evidence is written under
            # `fixture-e2e-logs/issue-<number>/<timestamp>/` by `fixture_issue_run_log`.
            get_fixture_run_timestamp()
            fixture_state = configure_fixture_testing()
            if args.issue_number is not None:
                fixture_selection = f"selected issue #{args.issue_number}"
            elif args.run_work_queues:
                fixture_selection = "work-queue mode"
            else:
                fixture_selection = f"label '{args.label}'"
            log_stage(
                "fixture-loading",
                (
                    f"Loaded {len(fixture_state.issue_numbers)} fixture issue(s); "
                    f"{fixture_selection}."
                ),
            )
        if args.clear_issue_caches:
            clear_issue_caches()
            return
        # The gate must check the repository this run actually operates on, so it is resolved first.
        reachability_metadata_path = resolve_reachability_repo_root(args.reachability_metadata_path)
        enter_phase(PHASE_CLAIM)
        require_host_requirements(args, reachability_metadata_path)
        if args.strategy_name:
            with run_step(PHASE_CLAIM, STEP_CHECK_STRATEGY_AND_MODEL, operand=args.strategy_name):
                log_step_progress(
                    PHASE_CLAIM,
                    STEP_CHECK_STRATEGY_AND_MODEL,
                    f"Checking strategy {args.strategy_name}",
                )
                require_strategy_by_name(args.strategy_name)
                log_step_progress(
                    PHASE_CLAIM,
                    STEP_CHECK_STRATEGY_AND_MODEL,
                    f"Strategy {args.strategy_name} accepted",
                )
        metrics_repo_path = resolve_metrics_repo_root(reachability_metadata_path, None)

        if not PROJECT_NUMBER:
            print("ERROR: GITHUB_PROJECT_NUMBER env var is not set.", file=sys.stderr)
            sys.exit(1)
        if args.run_work_queues:
            process_work_queues(
                reachability_metadata_path,
                metrics_repo_path,
                None,
                args.strategy_name,
                args.keep_tests_without_dynamic_access,
                args.parallelism,
                random_offset_override=args.random_offset,
                user_requested_only_override=args.user_requested_only,
                priority_override=args.priority,
                take_blocked_issues=args.take_blocked_issues,
            )
        elif args.review_pr is not None:
            authenticated_user = resolve_authenticated_user()
            run_pull_request_review_loop(
                args.review_pr,
                args.limit,
                reachability_metadata_path,
                authenticated_user,
                args.period,
            )
        elif args.issue_number is not None:
            authenticated_user = resolve_authenticated_user()
            process_single_issue(
                args.issue_number,
                reachability_metadata_path,
                metrics_repo_path,
                args.strategy_name,
                args.keep_tests_without_dynamic_access,
                authenticated_user,
                args.take_blocked_issues,
            )
        elif is_fixture_testing_enabled():
            process_fixture_issues_for_label(
                args.label,
                args.limit,
                reachability_metadata_path,
                metrics_repo_path,
                args.strategy_name,
                args.keep_tests_without_dynamic_access,
                user_requested_only=resolve_user_requested_only(args.user_requested_only),
                priority=args.priority,
            )
        else:
            authenticated_user = resolve_authenticated_user()
            user_requested_only = resolve_user_requested_only(args.user_requested_only)
            issue_scan_offset = args.offset
            if args.random_offset is True:
                issue_scan_offset = resolve_random_issue_scan_offset(
                    args.label,
                    priority=args.priority,
                    user_requested_only=user_requested_only,
                )
                log_debug(
                    "issue-scan",
                    f"Selected random start offset {issue_scan_offset} for label '{args.label}'",
                )
            process_issues_with_label(
                args.label,
                args.limit,
                issue_scan_offset,
                reachability_metadata_path,
                metrics_repo_path,
                args.strategy_name,
                args.keep_tests_without_dynamic_access,
                authenticated_user,
                args.parallelism,
                take_blocked_issues=args.take_blocked_issues,
                user_requested_only=user_requested_only,
                priority=args.priority,
            )
        normal_exit = True
    except KeyboardInterrupt:
        if is_shutdown_requested():
            mark_shutdown_requested()
        else:
            preserve_user_interrupt_reason()
        if is_shutdown_request_interrupt():
            print(
                f"\nRun stopped because the Forge stop marker exists at {describe_active_shutdown_signal_path()}.",
                file=sys.stderr,
            )
            sys.exit(0)
        if is_gradle_bootstrap_interrupt():
            log_failure_banner(
                "Shared Gradle bootstrap failed after retry. Stop current run and retry later.",
                file=sys.stderr,
            )
            sys.exit(GRADLE_BOOTSTRAP_EXIT_CODE)
        print("\nERROR: Run interrupted by Ctrl+C.", file=sys.stderr)
        sys.exit(130)
    except GitHubRateLimitExceeded as exc:
        log_failure_banner(f"{exc}. Stop current run and retry after reset.", file=sys.stderr)
        sys.exit(GITHUB_RATE_LIMIT_EXIT_CODE)
    finally:
        if normal_exit:
            log_success_banner("Run complete.")
        signal.signal(signal.SIGINT, previous_sigint_handler)


if __name__ == "__main__":
    main()
