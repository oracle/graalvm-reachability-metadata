# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Markdown formatting of library stats for PR bodies.

Formats dynamic-access and library-coverage stats entries, version diffs, and
before/after comparisons. `common_git.py` re-exports these names for its
existing importers.
"""

from utility_scripts.library_stats import load_library_stats_entry


def load_library_stats(repo_path, coordinates):
    """Load the full stats entry for the given coordinates from exploded stats files."""
    group, artifact, version = coordinates.split(":")
    return load_library_stats_entry(repo_path, group, artifact, version)


def format_dynamic_access_entry(stats):
    """Format a single dynamic-access stats entry."""
    return "{covered}/{total} covered calls ({ratio:.2f}%)".format(
        covered=stats["coveredCalls"],
        total=stats["totalCalls"],
        ratio=stats["coverageRatio"] * 100,
    )


def is_dynamic_access_stats_entry(stats):
    """Return True when stats has the expected dynamic-access entry shape."""
    return isinstance(stats, dict) and all(
        key in stats for key in ("coveredCalls", "totalCalls", "coverageRatio")
    )


def format_coverage_entry(entry):
    """Format a single library coverage entry."""
    if entry == "N/A":
        return "N/A"
    return "{covered}/{total} ({ratio:.2f}%)".format(
        covered=entry["covered"],
        total=entry["total"],
        ratio=entry["ratio"] * 100,
    )


def format_dynamic_access_section(dynamic_access_stats):
    """Format the dynamic-access stats into a markdown section."""
    if not is_dynamic_access_stats_entry(dynamic_access_stats):
        return ""
    lines = [
        "Dynamic access coverage:",
        f"- Overall: {format_dynamic_access_entry(dynamic_access_stats)}",
    ]
    for category in sorted(dynamic_access_stats.get("breakdown", {})):
        if not is_dynamic_access_stats_entry(dynamic_access_stats["breakdown"][category]):
            continue
        display_name = category[0].upper() + category[1:]
        lines.append(f"- {display_name}: {format_dynamic_access_entry(dynamic_access_stats['breakdown'][category])}")
    return "\n".join(lines)


def format_library_coverage_section(library_coverage):
    """Format the library coverage stats into a markdown section."""
    lines = ["Library coverage:"]
    for metric in ("instruction", "line", "method"):
        entry = library_coverage.get(metric)
        if entry != "N/A" and not isinstance(entry, dict):
            continue
        display_name = metric[0].upper() + metric[1:]
        lines.append(f"- {display_name}: {format_coverage_entry(entry)}")
    return "\n".join(lines)


def format_stats_section(version_stats):
    """Format a single version's stats (dynamic access + library coverage) into markdown."""
    sections = []
    dynamic_access = version_stats.get("dynamicAccess")
    if dynamic_access:
        dynamic_access_section = format_dynamic_access_section(dynamic_access)
        if dynamic_access_section:
            sections.append(dynamic_access_section)
    library_coverage = version_stats.get("libraryCoverage")
    if library_coverage:
        sections.append(format_library_coverage_section(library_coverage))
    if not sections:
        return ""
    return "Stats from `stats/<groupId>/<artifactId>/<metadata-version>/stats.json`:\n\n" + "\n\n".join(sections)


def _format_comparison_pair(old_coordinates, new_coordinates, old_entry, new_entry, formatter):
    """Return two bullet lines comparing an entry between old and new coordinates."""
    return [
        f"- {old_coordinates}: {formatter(old_entry) if old_entry else 'N/A'}",
        f"- {new_coordinates}: {formatter(new_entry) if new_entry else 'N/A'}",
    ]


def format_stats_diff(repo_path, old_coordinates, new_coordinates):
    """Format a comparison of stats between two library versions."""
    old_version_stats = load_library_stats(repo_path, old_coordinates)
    new_version_stats = load_library_stats(repo_path, new_coordinates)

    if old_version_stats is None and new_version_stats is None:
        return ""
    if old_version_stats == new_version_stats:
        return (
            "\n### Stats from `stats/<groupId>/<artifactId>/<metadata-version>/stats.json`\n\n"
            f"Same entry for both `{old_coordinates}` and `{new_coordinates}`.\n"
        )

    lines = ["", "### Stats from `stats/<groupId>/<artifactId>/<metadata-version>/stats.json`", ""]

    old_da = old_version_stats.get("dynamicAccess") if old_version_stats else None
    new_da = new_version_stats.get("dynamicAccess") if new_version_stats else None
    if old_da or new_da:
        lines.append("#### Dynamic access coverage")
        lines.append("")
        lines.extend(_format_comparison_pair(
            old_coordinates, new_coordinates, old_da, new_da, format_dynamic_access_entry
        ))

        all_categories = set()
        if old_da:
            all_categories.update(old_da.get("breakdown", {}).keys())
        if new_da:
            all_categories.update(new_da.get("breakdown", {}).keys())

        for category in sorted(all_categories):
            display_name = category[0].upper() + category[1:]
            old_cat = old_da.get("breakdown", {}).get(category) if old_da else None
            new_cat = new_da.get("breakdown", {}).get(category) if new_da else None
            lines.append("")
            lines.append(f"**{display_name}:**")
            lines.extend(_format_comparison_pair(
                old_coordinates, new_coordinates, old_cat, new_cat, format_dynamic_access_entry
            ))
        lines.append("")

    old_cov = old_version_stats.get("libraryCoverage") if old_version_stats else None
    new_cov = new_version_stats.get("libraryCoverage") if new_version_stats else None
    if old_cov or new_cov:
        lines.append("#### Library coverage")
        lines.append("")
        for metric in ("instruction", "line", "method"):
            display_name = metric[0].upper() + metric[1:]
            old_entry = old_cov.get(metric) if old_cov else None
            new_entry = new_cov.get(metric) if new_cov else None
            old_entry = old_entry if isinstance(old_entry, dict) or old_entry == "N/A" else None
            new_entry = new_entry if isinstance(new_entry, dict) or new_entry == "N/A" else None
            if old_entry or new_entry:
                lines.append(f"**{display_name}:**")
                lines.extend(_format_comparison_pair(
                    old_coordinates, new_coordinates, old_entry, new_entry, format_coverage_entry
                ))
                lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def format_stats_before_after(before_stats: dict | None, after_stats: dict | None, coordinates: str) -> str:
    """Format a before/after comparison of stats for the same library version."""
    if before_stats is None and after_stats is None:
        return ""
    if before_stats == after_stats:
        return (
            "\n### Stats comparison (before vs after)\n\n"
            f"No change in stats for `{coordinates}`.\n"
        )

    before_label = f"Before ({coordinates})"
    after_label = f"After ({coordinates})"
    lines = ["", f"### Stats comparison for `{coordinates}`", ""]

    before_da = before_stats.get("dynamicAccess") if before_stats else None
    after_da = after_stats.get("dynamicAccess") if after_stats else None
    if before_da or after_da:
        lines.append("#### Dynamic access coverage")
        lines.append("")
        lines.extend(_format_comparison_pair(
            before_label, after_label, before_da, after_da, format_dynamic_access_entry
        ))

        all_categories = set()
        if before_da:
            all_categories.update(before_da.get("breakdown", {}).keys())
        if after_da:
            all_categories.update(after_da.get("breakdown", {}).keys())

        for category in sorted(all_categories):
            display_name = category[0].upper() + category[1:]
            before_cat = before_da.get("breakdown", {}).get(category) if before_da else None
            after_cat = after_da.get("breakdown", {}).get(category) if after_da else None
            lines.append("")
            lines.append(f"**{display_name}:**")
            lines.extend(_format_comparison_pair(
                before_label, after_label, before_cat, after_cat, format_dynamic_access_entry
            ))
        lines.append("")

    before_cov = before_stats.get("libraryCoverage") if before_stats else None
    after_cov = after_stats.get("libraryCoverage") if after_stats else None
    if before_cov or after_cov:
        lines.append("#### Library coverage")
        lines.append("")
        for metric in ("instruction", "line", "method"):
            display_name = metric[0].upper() + metric[1:]
            before_entry = before_cov.get(metric) if before_cov else None
            after_entry = after_cov.get(metric) if after_cov else None
            before_entry = before_entry if isinstance(before_entry, dict) or before_entry == "N/A" else None
            after_entry = after_entry if isinstance(after_entry, dict) or after_entry == "N/A" else None
            if before_entry or after_entry:
                lines.append(f"**{display_name}:**")
                lines.extend(_format_comparison_pair(
                    before_label, after_label, before_entry, after_entry, format_coverage_entry
                ))
                lines.append("")

    return "\n".join(lines).rstrip() + "\n"
