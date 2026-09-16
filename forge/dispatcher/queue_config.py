# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Work and review queue configuration from the environment
(§AR-forge-dispatcher-decomposition, §FS-forge-run-requirements)."""


import os
import sys
from utility_scripts.run_location import PHASE_CLAIM
from utility_scripts.run_location import STEP_CHECK_STRATEGY_AND_MODEL
from utility_scripts.run_location import log_step_progress
from utility_scripts.run_location import pipeline_step
from utility_scripts.strategy_loader import require_strategy_by_name
from dispatcher.config import (
    DEFAULT_WORK_QUEUE_STRATEGY_NAME,
    LABEL_JAVAC_FAIL,
    LABEL_JAVA_RUN_FAIL,
    LABEL_LIBRARY_NEW,
    LABEL_LIBRARY_UPDATE,
    LABEL_NI_RUN_FAIL,
    LABEL_PR_CODE_COVERAGE,
    LABEL_PR_JAVAC_FIX,
    LABEL_PR_JAVA_RUN_FIX,
    LABEL_PR_LIBRARY_UPDATE,
    LABEL_PR_NI_RUN_FIX,
    PIPELINE_LABELS,
)
from dispatcher.env_config import (
    get_env_non_negative_int,
    get_env_zero_one_bool,
)
from dispatcher.records import WorkQueueConfig
from dispatcher.review_loop import ReviewQueueConfig

import argparse
from ai_workflows.drivers.add_new_library_support import DEFAULT_STRATEGY_NAME as DEFAULT_NEW_LIBRARY_STRATEGY_NAME
from ai_workflows.drivers.fix_ni_run import DEFAULT_STRATEGY_NAME as DEFAULT_NI_RUN_STRATEGY_NAME
from ai_workflows.drivers.improve_library_coverage import DEFAULT_STRATEGY_NAME as DEFAULT_LIBRARY_UPDATE_STRATEGY_NAME
from ai_workflows.drivers.java_fail_workflow import DEFAULT_JAVAC_STRATEGY
from ai_workflows.drivers.java_fail_workflow import DEFAULT_JAVA_RUN_STRATEGY
from utility_scripts.host_graalvm_checks import resolve_graalvm_version_check
from utility_scripts.host_requirements import QueueRequirements
from utility_scripts.host_requirements import ensure_host_requirements
from utility_scripts.host_requirements import resolve_queue_requirements
from utility_scripts.run_location import STEP_CHECK_HOST_REQUIREMENTS
from utility_scripts.stage_logger import debug_logging_enabled
from dispatcher.config import FORGE_DIR

def get_work_queue_configs_from_environment(
        work_strategy_name_override: str | None = None,
        random_offset_override: bool | None = None,
) -> list[WorkQueueConfig]:
    """Return issue work queue configuration from the FORGE_* environment."""
    work_label = os.environ.get("FORGE_WORK_LABEL", LABEL_LIBRARY_NEW)
    if work_label not in PIPELINE_LABELS:
        print(f"ERROR: FORGE_WORK_LABEL must be one of {sorted(PIPELINE_LABELS)}.", file=sys.stderr)
        sys.exit(1)

    return [
        WorkQueueConfig(
            label=LABEL_JAVAC_FAIL,
            limit=get_env_non_negative_int("FORGE_JAVAC_WORK_LIMIT", 1),
            strategy_name=os.environ.get("FORGE_JAVAC_STRATEGY_NAME") or None,
        ),
        WorkQueueConfig(
            label=LABEL_JAVA_RUN_FAIL,
            limit=get_env_non_negative_int("FORGE_JAVA_RUN_WORK_LIMIT", 1),
            strategy_name=os.environ.get("FORGE_JAVA_RUN_STRATEGY_NAME") or None,
        ),
        WorkQueueConfig(
            label=LABEL_NI_RUN_FAIL,
            limit=get_env_non_negative_int("FORGE_NI_RUN_WORK_LIMIT", 1),
            strategy_name=os.environ.get("FORGE_NI_RUN_STRATEGY_NAME") or None,
        ),
        WorkQueueConfig(
            label=LABEL_LIBRARY_UPDATE,
            limit=get_env_non_negative_int("FORGE_LIBRARY_UPDATE_WORK_LIMIT", 1),
            strategy_name=os.environ.get("FORGE_LIBRARY_UPDATE_STRATEGY_NAME") or None,
        ),
        WorkQueueConfig(
            label=work_label,
            limit=get_env_non_negative_int("FORGE_WORK_LIMIT", 1),
            strategy_name=(
                work_strategy_name_override
                or os.environ.get("FORGE_STRATEGY_NAME")
                or DEFAULT_WORK_QUEUE_STRATEGY_NAME
            ),
            random_offset=(
                random_offset_override
                if random_offset_override is not None
                else get_env_zero_one_bool("FORGE_RANDOM_WORK_OFFSET", False)
            ),
        ),
    ]


def get_review_queue_configs_from_environment() -> list[ReviewQueueConfig]:
    """Return pull request review queue configurations from the FORGE_* environment."""
    review_label = os.environ.get("FORGE_REVIEW_LABEL")
    review_limit = get_env_non_negative_int("FORGE_REVIEW_LIMIT", 1)
    if review_label:
        return [
            ReviewQueueConfig(
                label=review_label,
                limit=review_limit,
            )
        ]

    return [
        ReviewQueueConfig(
            label=LABEL_LIBRARY_NEW,
            limit=get_env_non_negative_int("FORGE_LIBRARY_REVIEW_LIMIT", review_limit),
        ),
        ReviewQueueConfig(
            label=LABEL_PR_JAVAC_FIX,
            limit=get_env_non_negative_int("FORGE_JAVAC_REVIEW_LIMIT", review_limit),
        ),
        ReviewQueueConfig(
            label=LABEL_PR_JAVA_RUN_FIX,
            limit=get_env_non_negative_int("FORGE_JAVA_RUN_REVIEW_LIMIT", review_limit),
        ),
        ReviewQueueConfig(
            label=LABEL_PR_NI_RUN_FIX,
            limit=get_env_non_negative_int("FORGE_NI_RUN_REVIEW_LIMIT", review_limit),
        ),
        ReviewQueueConfig(
            label=LABEL_PR_LIBRARY_UPDATE,
            limit=get_env_non_negative_int("FORGE_LIBRARY_UPDATE_REVIEW_LIMIT", review_limit),
        ),
        ReviewQueueConfig(
            label=LABEL_PR_CODE_COVERAGE,
            limit=get_env_non_negative_int("FORGE_BENCHMARK_REVIEW_LIMIT", review_limit),
        ),
    ]


@pipeline_step(PHASE_CLAIM, STEP_CHECK_STRATEGY_AND_MODEL)
def validate_work_queue_strategies(queue_configs: list[WorkQueueConfig]) -> None:
    """Validate strategy names configured for enabled issue queues."""
    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_STRATEGY_AND_MODEL,
        "Checking configured strategies",
    )
    seen_strategy_names: set[str] = set()
    for queue_config in queue_configs:
        strategy_name = queue_config.strategy_name
        if queue_config.limit <= 0 or not strategy_name or strategy_name in seen_strategy_names:
            continue
        require_strategy_by_name(strategy_name)
        seen_strategy_names.add(strategy_name)
    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_STRATEGY_AND_MODEL,
        f"Configured strategies accepted: {len(seen_strategy_names)}",
    )


def resolve_host_requirement_queues(args: argparse.Namespace) -> QueueRequirements:
    """Select the capabilities the invoked Forge mode needs, not the ones its queue limits allow.

    §FS-forge-host-requirements
    """
    github_work = not args.fixture_testing
    if args.review_pr is not None:
        return QueueRequirements(issue_work=False, review_work=True, github_work=github_work)
    if args.run_work_queues:
        enabled_queues = resolve_queue_requirements(os.environ)
        return QueueRequirements(
            issue_work=enabled_queues.issue_work,
            review_work=enabled_queues.review_work,
            github_work=github_work,
        )
    return QueueRequirements(issue_work=True, review_work=False, github_work=github_work)


def resolve_host_requirement_strategy_names(args: argparse.Namespace) -> list[str]:
    """Return every test strategy reachable from the enabled issue queues.

    §FS-forge-host-requirements
    """
    if not resolve_host_requirement_queues(args).issue_work:
        return []
    if not args.run_work_queues:
        return [args.strategy_name] if args.strategy_name else []

    defaults_by_label = {
        LABEL_LIBRARY_NEW: DEFAULT_NEW_LIBRARY_STRATEGY_NAME,
        LABEL_LIBRARY_UPDATE: DEFAULT_LIBRARY_UPDATE_STRATEGY_NAME,
        LABEL_JAVAC_FAIL: DEFAULT_JAVAC_STRATEGY,
        LABEL_JAVA_RUN_FAIL: DEFAULT_JAVA_RUN_STRATEGY,
        LABEL_NI_RUN_FAIL: DEFAULT_NI_RUN_STRATEGY_NAME,
    }
    strategy_names: list[str] = []
    for queue_config in get_work_queue_configs_from_environment(args.strategy_name):
        if queue_config.limit <= 0:
            continue
        strategy_name = queue_config.strategy_name or defaults_by_label[queue_config.label]
        if strategy_name not in strategy_names:
            strategy_names.append(strategy_name)
    return strategy_names


@pipeline_step(PHASE_CLAIM, STEP_CHECK_HOST_REQUIREMENTS)
def require_host_requirements(args: argparse.Namespace, reachability_metadata_path: str) -> None:
    """Stop before any work when this host cannot run the invoked Forge mode.

    Forge paths are checked against `FORGE_DIR` and the repository paths against the
    checkout this run selected, which `--reachability-metadata-path` can move away from
    the checkout that contains Forge (§FS-forge-host-requirements).
    """
    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_HOST_REQUIREMENTS,
        "Checking host requirements",
    )
    ensure_host_requirements(
        FORGE_DIR,
        requirements=resolve_host_requirement_queues(args),
        graalvm_version_check=resolve_graalvm_version_check(args.graalvm_version_check),
        repo_dir=reachability_metadata_path,
        test_strategy_names=resolve_host_requirement_strategy_names(args),
        verbose=args.verbose or debug_logging_enabled(),
    )
    log_step_progress(
        PHASE_CLAIM,
        STEP_CHECK_HOST_REQUIREMENTS,
        "Host requirements passed",
    )
