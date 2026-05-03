# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Unified entry point for Java fail fix workflows.

Usage:
  python3 ai_workflows/fix_java_fails.py --javac \
    --coordinates group:artifact:oldVersion \
    --new-version newVersion \
    [--strategy-name NAME] [--reachability-metadata-path PATH] \
    [--metrics-repo-path PATH] [--docs-path PATH] [-v]

  python3 ai_workflows/fix_java_fails.py --java-run \
    --coordinates group:artifact:oldVersion \
    --new-version newVersion \
    [--strategy-name NAME] [--reachability-metadata-path PATH] \
    [--metrics-repo-path PATH] [--docs-path PATH] [-v]
"""

import argparse
import sys

DEFAULT_JAVAC_STRATEGY = "javac_iterative_with_coverage_sources_pi_gpt-5.5"
DEFAULT_JAVA_RUN_STRATEGY = "java_run_iterative_with_coverage_sources_pi_gpt-5.5"


def build_parser():
    """Build the unified Java-fail workflow CLI parser."""
    parser = argparse.ArgumentParser(
        prog="fix_java_fails.py",
        description="Automate Java compile or runtime test fixes for a library version bump.",
        formatter_class=argparse.RawTextHelpFormatter,
    )
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument(
        "--javac",
        action="store_true",
        help=f"fix Java compilation failures (default strategy: {DEFAULT_JAVAC_STRATEGY})",
    )
    mode.add_argument(
        "--java-run",
        action="store_true",
        help=f"fix Java runtime test failures (default strategy: {DEFAULT_JAVA_RUN_STRATEGY})",
    )

    parser.add_argument(
        "--coordinates",
        required=True,
        help="Maven coordinates Group:Artifact:Version for the current library version",
    )
    parser.add_argument(
        "--new-version",
        required=True,
        help="Target library version which needs a fix",
    )
    parser.add_argument(
        "--reachability-metadata-path",
        help=(
            "Path to the graalvm-reachability-metadata repository. "
            "If omitted, the parent checkout of this Forge directory is used."
        ),
    )
    parser.add_argument(
        "--metrics-repo-path",
        help=(
            "Path where workflow metrics are written. "
            "If omitted, the forge directory in the selected worktree is used."
        ),
    )
    parser.add_argument(
        "--strategy-name",
        metavar="NAME",
        help="Strategy name from strategies/predefined_strategies.json",
    )
    parser.add_argument(
        "--docs-path",
        default=None,
        help="Optional path with additional read-only docs/sources for agent context",
    )
    parser.add_argument("-v", "--verbose", action="store_true", help="enable verbose mode for the configured agent")
    return parser


def _workflow_argv(flags):
    """Build mode-specific workflow arguments from parsed unified flags."""
    workflow_argv = [
        "--coordinates",
        flags.coordinates,
        "--new-version",
        flags.new_version,
    ]
    if flags.reachability_metadata_path:
        workflow_argv.extend(["--reachability-metadata-path", flags.reachability_metadata_path])
    if flags.metrics_repo_path:
        workflow_argv.extend(["--metrics-repo-path", flags.metrics_repo_path])
    if flags.strategy_name:
        workflow_argv.extend(["--strategy-name", flags.strategy_name])
    if flags.docs_path:
        workflow_argv.extend(["--docs-path", flags.docs_path])
    if flags.verbose:
        workflow_argv.append("--verbose")
    return workflow_argv


def main(argv=None):
    """Select and run the appropriate java-fail workflow based on the mode flag."""
    flags = build_parser().parse_args(argv if argv is not None else sys.argv[1:])
    from ai_workflows.java_fail_workflow import JAVAC_CONFIG, JAVA_RUN_CONFIG, run_java_fail_workflow

    config = JAVAC_CONFIG if flags.javac else JAVA_RUN_CONFIG
    return run_java_fail_workflow(config, _workflow_argv(flags))


if __name__ == "__main__":
    sys.exit(main())
