# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Compatibility entry point for the java-run fix workflow.

Usage:
  python3 ai_workflows/fix_java_run_fail.py \
    --coordinates group:artifact:oldVersion \
    --new-version newVersion \
    [--strategy-name "java_run_iterative_with_coverage_sources_pi_gpt-5.5"] \
    [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
    [--metrics-repo-path /path/to/metrics-storage] \
    [--docs-path /path/to/docs] \
    [-v]
"""

import sys

from ai_workflows.java_fail_workflow import JAVA_RUN_CONFIG, run_java_fail_workflow


def main(argv=None):
    """Execute the end-to-end java-run fix workflow for a version bump."""
    return run_java_fail_workflow(JAVA_RUN_CONFIG, argv)


if __name__ == "__main__":
    sys.exit(main())
