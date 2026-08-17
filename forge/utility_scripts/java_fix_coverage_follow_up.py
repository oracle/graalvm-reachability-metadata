# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Fixed-version coverage follow-up for Java repair workflows."""

from utility_scripts.dynamic_access_report import DynamicAccessCoverageReport
from utility_scripts.library_update_alias_split import format_follow_up_trailer


def uncovered_dynamic_access_class_count(
        report: DynamicAccessCoverageReport | None,
) -> int:
    """Return the number of classes with uncovered dynamic-access calls.

    An unusable report counts as zero so exploration still runs and regenerates it.
    """
    if report is None:
        return 0
    return sum(
        1
        for class_coverage in report.classes
        if class_coverage.uncovered_calls > 0
    )


def format_generation_statistics_blocks(metrics: dict, coverage_deferred: bool) -> tuple[str, str]:
    """Return the metadata-entry and coverage bullet blocks of a Java-fix PR summary.

    Both blocks are empty when exploration was deferred: that run only repaired the
    build, so entry counts and coverage percentages would describe work it never
    attempted and invite reading a deliberate deferral as a regression.
    §WF-java-fail-fix-workflow.3
    """
    if coverage_deferred:
        return "", ""

    metadata_entry_lines = f"- Metadata entries: {int(metrics.get('metadata_entries', 0))}\n"
    test_only_metadata_entries = int(metrics.get("test_only_metadata_entries", 0) or 0)
    if test_only_metadata_entries > 0:
        metadata_entry_lines += f"- Test-only metadata entries: {test_only_metadata_entries}\n"

    coverage_lines = f"- Library coverage percentage: {metrics.get('code_coverage_percent', 0)}\n"
    coverage_lines += (
        "- Previous library version metadata entries: "
        f"{int(metrics.get('previous_library_metadata_entries', 0))}\n"
    )
    previous_test_only_entries = int(metrics.get("previous_library_test_only_metadata_entries", 0) or 0)
    if previous_test_only_entries > 0:
        coverage_lines += (
            f"- Previous library version test-only metadata entries: {previous_test_only_entries}\n"
        )
    coverage_lines += (
        "- Previous library version coverage percentage: "
        f"{metrics.get('previous_library_coverage_percent', 0)}\n"
    )
    return metadata_entry_lines, coverage_lines


def format_coverage_follow_up_pr_section(
        *,
        issue_number: int | None,
        uncovered_class_count: int | None,
        class_threshold: int | None,
        repo: str,
) -> str:
    """Return repair-PR text linking the fixed-version coverage issue."""
    if issue_number is None:
        return ""
    if uncovered_class_count is None or class_threshold is None:
        raise ValueError("Coverage follow-up class count and threshold are required")
    issue_url = f"https://github.com/{repo}/issues/{issue_number}"
    return (
        "### Deferred Dynamic-Access Exploration\n\n"
        "Exploration was skipped after the repair succeeded because the "
        f"dynamic-access report contained **{uncovered_class_count} uncovered "
        f"classes**, above the configured threshold of **{class_threshold}**.\n\n"
        f"Coverage work will continue in the newly opened "
        f"[library-update-request #{issue_number}]({issue_url}).\n\n"
        f"Refs: #{issue_number}\n"
        f"{format_follow_up_trailer(issue_number)}\n\n"
    )
