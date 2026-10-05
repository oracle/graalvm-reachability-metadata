# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Markdown and LCOV rendering for the deep-method report.

Turns the correlated report and prompt records into the compact prompt
markdown and the guidance-only LCOV file (§AR-code-coverage-improvement.3),
rendering each miss classification as its hint
(§AR-code-coverage-deep-navigation.3).
"""

from __future__ import annotations

import os

from utility_scripts.code_coverage_jacoco import JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef, parse_inventory_id
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_records import (
    NearCallRecord,
    simple_owner,
    translated_path,
)
from utility_scripts.code_coverage_profile_routes import Sample, SampledProfile

MAX_RENDERED_DISPATCH_CANDIDATES = 12
MAX_RENDERED_RECEIVERS = 6
MAX_RENDERED_BRANCHES = 8
# A thematic break between consecutive route groups; a blank line always
# precedes it, so Markdown never reads it as a setext heading underline.
GROUP_SEPARATOR = "---"


def _display_method(ref: MethodRef, qualify_owner: bool) -> str:
    owner = simple_owner(ref.owner)
    arguments = "..." if ref.params else ""
    if ref.name == "<init>":
        return f"{owner}({arguments})"
    method = f"{ref.name}({arguments})"
    return f"{owner}.{method}" if qualify_owner else method


def _display_path(static_path: list[int], graph: CallGraph, limit: int = 6) -> str:
    # Generated lambda classes and extracted bodies carry compiler-chosen names;
    # the agent can only act on the method that creates them
    # (§AR-code-coverage-improvement.4.2.1).
    path: list[MethodRef] = translated_path(static_path, graph)
    selected: list[MethodRef | None]
    if len(path) <= limit:
        selected = list(path)
    else:
        selected = [*path[:2], None, *path[-3:]]

    labels: list[str] = []
    previous_owner: str | None = None
    for ref in selected:
        if ref is None:
            labels.append("…")
            previous_owner = None
            continue
        labels.append(_display_method(ref, ref.owner != previous_owner))
        previous_owner = ref.owner
    return " → ".join(labels)


def _line_location(evidence: dict | None) -> str:
    if evidence is None:
        return "unknown invoking line"
    source_path: str = evidence["sourcePath"]
    return f"{os.path.basename(source_path)}:{evidence['line']}"


def _count_text(count: int | None, counted: bool) -> str:
    """A successor's count, or why it has none; nothing without counters."""
    if not counted:
        return ""
    return f" ×{count:,}" if count is not None else " (no counter)"


def _shown_branches(items: list[dict]) -> tuple[list[dict], int]:
    """Every branch that reaches the target or ran, filled up to the cap."""
    if len(items) <= MAX_RENDERED_BRANCHES:
        return items, 0
    kept: set[int] = {
        index for index, item in enumerate(items)
        if item["reachesTarget"] or (item["count"] or 0) > 0
    }
    for index in range(len(items)):
        if len(kept) >= MAX_RENDERED_BRANCHES:
            break
        kept.add(index)
    return [items[index] for index in sorted(kept)], len(items) - len(kept)


def _branch_lines(branches: list[dict], counted: bool) -> list[str]:
    """One numbered line per successor of every branch instruction on the fork
    line, labelled by landing line, so the items add up to JaCoCo's taken/total
    (§AR-code-coverage-deep-navigation.3.2)."""
    # A successor that lands on a later condition of the same line is named
    # after it; with one condition on the line there is nothing to name.
    by_block: dict[int, str] = {
        branch["blockStart"]: f"condition {index}"
        for index, branch in enumerate(branches, start=1)
    } if len(branches) > 1 else {}
    items: list[dict] = [
        {**successor, "number": number}
        for number, successor in enumerate(
            (successor for branch in branches for successor in branch["successors"]),
            start=1,
        )
    ]
    shown, omitted = _shown_branches(items)
    lines: list[str] = []
    for item in shown:
        landing: str = by_block.get(item["bci"]) or (
            f"line {item['line']}" if item["line"] is not None else f"bci {item['bci']}"
        )
        marker: str = " ← target" if item["reachesTarget"] else ""
        lines.append(
            f"    branch {item['number']} → {landing}{_count_text(item['count'], counted)}{marker}"
        )
    if omitted:
        lines.append(f"    … {omitted} more")
    return lines


def _dispatch_lines(classification: dict) -> list[str]:
    site: dict | None = classification.get("site")
    candidate_records: list[dict] = sorted(
        classification["candidates"],
        key=lambda candidate: (
            not candidate["coverageSuite"],
            -(candidate.get("dispatches") or 0),
            candidate["id"],
        ),
    )
    candidates: list[str] = []
    for candidate in candidate_records[:MAX_RENDERED_DISPATCH_CANDIDATES]:
        tags: list[str] = ["coverage suite"] if candidate["coverageSuite"] else []
        dispatches: int | None = candidate.get("dispatches")
        if dispatches is not None:
            tags.append(f"dispatched ×{dispatches:,}" if dispatches else "never dispatched here")
        candidates.append(
            f"`{candidate['id']}`" + (f" [{', '.join(tags)}]" if tags else "")
        )
    omitted: int = len(candidate_records) - len(candidates)
    candidates_text: str = ", ".join(candidates)
    if omitted:
        candidates_text += f", … {omitted} more in JSON"
    if site is None:
        return [
            "  no fork — same line, different implementation answered",
            f"  candidates: {candidates_text}",
        ]
    receivers: list[str] = [
        f"{simple_owner(receiver['type'])} ×{receiver['count']:,}"
        for receiver in site["receivers"][:MAX_RENDERED_RECEIVERS]
    ]
    if len(site["receivers"]) > MAX_RENDERED_RECEIVERS:
        receivers.append(f"… {len(site['receivers']) - MAX_RENDERED_RECEIVERS} more")
    # `None` means a receiver did not resolve, so nothing is known about the
    # target's share; only a proven zero earns the claim.
    never: str = ", never to your target" if site.get("targetDispatches") == 0 else ""
    return [
        f"  no fork — site dispatched {site['dispatches']:,}×{never}",
        f"  observed receivers: {' · '.join(receivers)}",
        f"  candidates: {candidates_text}",
    ]


def classification_lines(classification: dict, counted: bool = False) -> list[str]:
    target: dict | None = classification.get("target")
    if target is None:
        target_line: str = "  target line unavailable"
    else:
        status: str = "RAN" if target["ci"] > 0 else "never ran"
        target_line = f"  target `{_line_location(target)}` {status}"
    kind: str = classification["kind"]
    if kind == "dispatched-elsewhere":
        return [target_line, *_dispatch_lines(classification)]
    if kind == "fork-not-taken":
        fork: dict = classification["fork"]
        total_branches: int = fork["mb"] + fork["cb"]
        reach: int | None = fork.get("reach")
        ran: str = f"reached {reach:,}×" if reach is not None else "ran"
        header: str = (
            f"  fork `{_line_location(fork)}` {ran}, {fork['cb']} of "
            f"{total_branches} branches taken"
        )
        branches: list[dict] | None = fork.get("branches")
        if not branches:
            return [target_line, f"{header} — target is beyond an untaken branch"]
        return [target_line, header, *_branch_lines(branches, counted)]
    nearest: dict | None = classification.get("nearestCovered")
    nearest_text: str = (
        f"nearest covered `{_line_location(nearest)}`"
        if nearest is not None
        else "no covered line was available"
    )
    return [
        target_line,
        f"  no fork above — {nearest_text}; target is reached only by an exception "
        "or external event",
    ]


def _step_label(step: dict) -> str:
    """`Caller.m() → Impl.m()` for one unobserved dispatch step."""
    labels: list[str] = []
    for method_id in (step["caller"], step["callee"]):
        ref: MethodRef | None = parse_inventory_id(method_id)
        labels.append(_display_method(ref, True) if ref is not None else method_id)
    return " → ".join(labels)


def _prompt_line(
        record: NearCallRecord,
        graph: CallGraph,
        notes: dict[str, dict],
        counted: bool,
) -> str:
    """One prompt path with its line diagnosis and synthetic-method notes."""
    note: dict = notes.get(record.target_ref.canonical_id, {})
    # A public target may be called directly, unlike an internal one
    # (§AR-code-coverage-improvement.4.2).
    suffixes: list[str] = ["public API"] if note.get("publicApi") else []
    closures: dict | None = note.get("closures")
    if closures is not None and closures["unexecuted"]:
        suffixes.append(
            f"{closures['total']} closures, {closures['unexecuted']} never executed"
        )
    if note.get("handOff"):
        suffixes.append(f"runs on another thread via `{note['handOff']}` — the test must wait")
    unobserved: list[dict] = note.get("unobservedSteps") or []
    if unobserved:
        more: str = f" (+{len(unobserved) - 1} more)" if len(unobserved) > 1 else ""
        suffixes.append(
            f"route assumes `{_step_label(unobserved[0])}`, a dispatch the run never made{more}"
        )
    path: str = f"`{_display_path(record.static_path, graph)}`"
    path_line: str = f"{path} — {'; '.join(suffixes)}" if suffixes else path
    classification: dict = note.get("missClassification", {
        "kind": "no-fork",
        "target": None,
        "nearestCovered": None,
        "candidates": [],
    })
    return "\n".join([path_line, *classification_lines(classification, counted)])


def write_markdown(
        report: dict,
        prompt_records: list[NearCallRecord],
        graph: CallGraph,
        coordinate: str,
        iteration: int,
        md_path: str,
) -> None:
    summary: dict = report["summary"]
    notes: dict[str, dict] = {target["id"]: target for target in report["bulkTargets"]}
    lines: list[str] = [
        f"# Deep coverage paths (iteration {iteration}) — {coordinate}",
        "",
        # The public-entry obligation (§AR-code-coverage-deep-navigation.2).
        "Reach every target below through its public entry; never call internal "
        "methods directly.",
        "",
        "## Where the tests go",
        "",
        "All of them belong in the dedicated coverage suite, and nowhere else: the "
        "library's regular test sources are metadata-generation tests and stay "
        "untouched.",
        "",
        "Inside that suite, write one test class per subsystem you drive, not one "
        "class per run. The targets below cluster into a few subsystems — each "
        "public entry in the routes drives one of them — and a scenario built for "
        "its own subsystem, with the setup that subsystem needs, reaches far more "
        "of it than one appended to a class built for something else. Before "
        "extending an existing test class, check whether the cluster you are about "
        "to drive already has one; if not, add a class.",
    ]
    counted: bool = bool(summary.get("instrumentedCounters"))

    # Every prompted record is a sampled route (§AR-code-coverage-deep-navigation.2).
    sampled_groups: dict[tuple[str, int], list[NearCallRecord]] = {}
    sampled_group_order: list[tuple[str, int]] = []
    for record in prompt_records:
        assert record.join_kind == "sampled" and record.sample is not None and record.static_path
        group_key: tuple[str, int] = (record.sample.context_id, record.static_path[0])
        if group_key not in sampled_groups:
            sampled_groups[group_key] = []
            sampled_group_order.append(group_key)
        sampled_groups[group_key].append(record)

    lines += ["", "## Observed (sampled guidance only)", ""]
    if not sampled_group_order:
        lines.append("_No sampled context reaches an actionable uncovered path._")
    for position, group_key in enumerate(sampled_group_order):
        if position:
            lines += [GROUP_SEPARATOR, ""]
        records: list[NearCallRecord] = sampled_groups[group_key]
        representative: NearCallRecord = records[0]
        assert representative.sample is not None
        sample: Sample = representative.sample
        join_index: int = representative.sampled_join_path_index or 0
        observed_path: list[int] = [
            static_id
            for static_id, _ in sample.path[join_index:join_index + 2]
        ]
        lines.append("Observed:")
        lines.append(f"`{_display_path(observed_path, graph)}`")
        lines.append("")
        lines.append("Uncovered paths:")
        for record in records:
            lines.append(_prompt_line(record, graph, notes, counted))
        lines.append("")
    # Totals, omitted counts and caveats stay in the JSON report
    # (§AR-code-coverage-deep-navigation.2).
    with open(md_path, "w", encoding="utf-8") as md_file:
        md_file.write("\n".join(lines) + "\n")


def write_lcov(
        profile: SampledProfile,
        graph: CallGraph,
        jacoco_methods: dict[str, JacocoMethodCoverage],
        lcov_path: str,
) -> None:
    """Emit only positive sampled observations as guidance-only LCOV."""
    by_method: dict[str, tuple[JacocoMethodCoverage, int]] = {}
    for static_id, count in profile.sample_counts.items():
        if count <= 0:
            continue
        ref: MethodRef = graph.methods[static_id]
        coverage: JacocoMethodCoverage | None = jacoco_methods.get(ref.canonical_id)
        if coverage is None:
            continue
        previous: tuple[JacocoMethodCoverage, int] | None = by_method.get(ref.canonical_id)
        total: int = count + (previous[1] if previous is not None else 0)
        by_method[ref.canonical_id] = (coverage, total)

    by_source: dict[str, list[tuple[MethodRef, int, int]]] = {}
    for coverage, count in by_method.values():
        ref: MethodRef = coverage.method_ref
        source_path: str = coverage.source_path or f"{ref.owner.replace('.', '/')}.java"
        source_line: int = coverage.source_line or 1
        by_source.setdefault(source_path, []).append((ref, source_line, count))

    lines: list[str] = ["TN:sampled-pgo-guidance"]
    for source_path in sorted(by_source):
        entries: list[tuple[MethodRef, int, int]] = sorted(
            by_source[source_path], key=lambda item: (item[1], item[0].canonical_id)
        )
        lines.append(f"SF:{source_path}")
        for ref, line, _ in entries:
            lines.append(f"FN:{line},{ref.canonical_id}")
        for ref, _, count in entries:
            lines.append(f"FNDA:{count},{ref.canonical_id}")
        lines.append(f"FNF:{len(entries)}")
        lines.append(f"FNH:{len(entries)}")
        lines.append("end_of_record")
    with open(lcov_path, "w", encoding="utf-8") as lcov_file:
        lcov_file.write("\n".join(lines) + "\n")
