# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Compatibility entry point for the javac-fix workflow.

Usage:
  python3 ai_workflows/fix_javac_fail.py \
    --coordinates group:artifact:oldVersion \
    --new-version newVersion \
    [--strategy-name "javac_iterative_with_coverage_sources_pi_gpt-5.5"] \
    [--reachability-metadata-path /path/to/graalvm-reachability-metadata] \
    [--metrics-repo-path /path/to/metrics-storage] \
    [--docs-path /path/to/docs] \
    [-v]
"""

import sys

from ai_workflows.java_fail_workflow import JAVAC_CONFIG, run_java_fail_workflow

# Re-export symbols used by forge_metadata.py and other callers
from ai_workflows.java_fail_workflow import (  # noqa: F401
    list_all_files,
    init_agent,
)


def main(argv=None):
    """Execute the end-to-end javac-fix workflow for a version bump."""
    return run_java_fail_workflow(JAVAC_CONFIG, argv)


if __name__ == "__main__":
    sys.exit(main())
