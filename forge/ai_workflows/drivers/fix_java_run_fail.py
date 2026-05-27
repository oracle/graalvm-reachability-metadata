# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Canonical entry point for the java-run fix workflow.

Usage:
  python3 ai_workflows/drivers/fix_java_run_fail.py \
    --coordinates group:artifact:oldVersion \
    --new-version newVersion \
    [--strategy-name "java_run_iterative_with_coverage_sources_pi_gpt-5.5"] \
    [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
    [--metrics-repo-path /path/to/metrics-storage] \
    [--docs-path /path/to/docs] \
    [-v]
"""

import os
import sys

if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from ai_workflows.drivers.java_fail_workflow import JAVA_RUN_CONFIG, run_java_fail_workflow


def main(argv=None):
    """Execute the end-to-end java-run fix driver for a version bump.

    The java-run-failure driver for §WF-java-fail-fix-workflow, following the
    single-run driver contract (§WF-forge-workflow-drivers).
    """
    return run_java_fail_workflow(JAVA_RUN_CONFIG, argv)


if __name__ == "__main__":
    sys.exit(main())
