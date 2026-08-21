#!/usr/bin/env python3
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Validate and publish Forge branches without executing feature-branch code."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from jsonschema import Draft202012Validator, FormatChecker

REPOSITORY = "oracle/graalvm-reachability-metadata"
BASE_BRANCH = "master"
MAX_BODY_CHARS = 60_000
MAX_TEST_DIFF_CHARS = 12_000
SEVERE_METADATA_DROP_RATIO = 0.25
DYNAMIC_ACCESS_METADATA_ENTRY_NOTE_RATIO = 1.75
ROUTE_LABELS = {
    "library-new-request": ["GenAI", "library-new-request"],
    "library-update-request": ["GenAI", "library-update-request"],
    "fixes-javac-fail": ["GenAI", "fixes-javac-fail"],
    "fixes-java-run-fail": ["GenAI", "fixes-java-run-fail"],
    "fixes-native-image-run-fail": ["fixes-native-image-run-fail"],
    "not-for-native-image": ["GenAI", "library-new-request", "not-for-native-image"],
    "code-coverage-improvement": ["GenAI", "code-coverage-improvement", "rhei"],
}


@dataclass(frozen=True)
class ValidatedPublication:
    descriptor: dict[str, Any]
    descriptor_path: str
    head_sha: str


def run(command: list[str], *, input_text: str | None = None) -> str:
    """Run a trusted local command and return stdout."""
    result = subprocess.run(
        command,
        input=input_text,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if result.returncode != 0:
        detail = result.stderr.strip() or result.stdout.strip()
        raise RuntimeError(f"Command failed ({' '.join(command)}): {detail}")
    return result.stdout


def git(*args: str) -> str:
    return run(["git", *args])


def gh_json(*args: str) -> Any:
    output = run(["gh", *args])
    return json.loads(output) if output.strip() else None


def load_schema() -> dict[str, Any]:
    path = Path(__file__).with_name("schema.json")
    with path.open("r", encoding="utf-8") as schema_file:
        return json.load(schema_file)


def read_json_at_commit(commit: str, path: str) -> dict[str, Any]:
    payload = json.loads(git("show", f"{commit}:{path}"))
    if not isinstance(payload, dict):
        raise TypeError(f"Expected JSON object at {commit}:{path}")
    return payload


def validate_publication(
        *,
        head_sha: str,
        branch: str,
        actor: str,
        repository: str,
) -> ValidatedPublication:
    """Validate a feature tree as inert data (§GIT-actions-publication)."""
    if repository != REPOSITORY:
        raise ValueError(f"Unexpected head repository: {repository}")
    resolved_head = git("rev-parse", f"{head_sha}^{{commit}}").strip()
    if resolved_head != head_sha:
        raise ValueError("Requested head SHA did not resolve exactly")

    remote_branch_ref = f"refs/remotes/origin/{branch}"
    try:
        remote_branch_head = git("rev-parse", f"{remote_branch_ref}^{{commit}}").strip()
    except RuntimeError as exc:
        raise ValueError(f"Remote publication branch does not exist: {branch}") from exc
    if remote_branch_head != head_sha:
        raise ValueError(
            f"Remote branch {branch!r} no longer points at the triggering SHA {head_sha}"
        )

    descriptor_paths = [
        path
        for path in git("ls-tree", "-r", "--name-only", head_sha).splitlines()
        if path.endswith("/forge-publication.json")
    ]
    tip_candidates = [path for path in descriptor_paths if _path_changed(head_sha, path)]
    if len(tip_candidates) != 1:
        raise ValueError("Exactly one tip-committed forge-publication.json is required")
    descriptor_path = tip_candidates[0]
    descriptor = read_json_at_commit(head_sha, descriptor_path)
    Draft202012Validator(load_schema(), format_checker=FormatChecker()).validate(descriptor)

    base_commit = str(descriptor["base_commit"])
    if subprocess.run(
            ["git", "merge-base", "--is-ancestor", base_commit, head_sha],
            check=False,
    ).returncode != 0:
        raise ValueError("Descriptor base commit is not an ancestor of the head SHA")
    trusted_base_ref = f"refs/remotes/origin/{BASE_BRANCH}"
    if subprocess.run(
            ["git", "merge-base", "--is-ancestor", base_commit, trusted_base_ref],
            check=False,
    ).returncode != 0:
        raise ValueError("Descriptor base commit is not on the trusted upstream base branch")

    changed_paths = sorted(
        path
        for path in git(
            "diff", "--name-only", "--diff-filter=ACMRTD", base_commit, head_sha,
        ).splitlines()
        if path
    )
    changed_descriptors = [
        path for path in changed_paths if path.endswith("/forge-publication.json")
    ]
    if changed_descriptors != [descriptor_path]:
        raise ValueError("Exactly one forge-publication.json may change in the publication diff")

    producer = str(descriptor["producer"])
    if actor != producer:
        raise ValueError(f"Triggering actor {actor!r} does not match descriptor producer {producer!r}")
    if branch != descriptor["branch"] or not branch.startswith(f"ai/{producer}/"):
        raise ValueError("Event branch does not match the descriptor producer branch")
    if not branch.endswith(f"-{descriptor['publication_id']}"):
        raise ValueError("Event branch does not carry the descriptor publication ID")

    library = descriptor["library"]
    expected_coordinates = f"{library['group']}:{library['artifact']}:{library['version']}"
    if library["coordinates"] != expected_coordinates:
        raise ValueError("Descriptor library coordinate fields do not agree")
    expected_descriptor = (
        f"stats/{library['group']}/{library['artifact']}/{library['version']}/forge-publication.json"
    )
    if descriptor_path != expected_descriptor:
        raise ValueError("Descriptor path does not match its library coordinate")

    expected_publication_id = _build_publication_id(descriptor)
    if descriptor["publication_id"] != expected_publication_id:
        raise ValueError("Descriptor publication ID does not match its durable run inputs")

    _validate_render_inputs(descriptor)
    return ValidatedPublication(descriptor, descriptor_path, head_sha)


def _build_publication_id(descriptor: dict[str, Any]) -> str:
    identity = json.dumps(
        {
            "issue_number": descriptor["issue_number"],
            "timestamp": descriptor["timestamp"],
            "coordinates": descriptor["library"]["coordinates"],
            "task_type": descriptor["task_type"],
        },
        sort_keys=True,
        separators=(",", ":"),
    )
    digest = hashlib.sha256(identity.encode("utf-8")).hexdigest()[:12]
    compact_timestamp = re.sub(r"[^0-9]", "", descriptor["timestamp"])[:20]
    return f"forge-{descriptor['issue_number']}-{compact_timestamp}-{digest}"


def _validate_render_inputs(descriptor: dict[str, Any]) -> None:
    """Require only the descriptor fields the templates dereference unconditionally.

    Local verification owns whether the work is correct; this guards the publisher
    against a `KeyError` mid-render, not against a bad run (§GIT-actions-publication).
    """
    template_type = descriptor["template_type"]
    if template_type in {
        "fixes-javac-fail",
        "fixes-java-run-fail",
        "fixes-native-image-run-fail",
    } and descriptor.get("previous_library") is None:
        raise ValueError(f"Template {template_type!r} requires previous_library")
    if descriptor["task_type"] == "not-for-native-image":
        reason = descriptor["render"].get("reason")
        if not isinstance(reason, str) or not reason.strip():
            raise ValueError("Not-for-native-image publication requires a reason")
    if template_type == "code-coverage-improvement":
        _validate_code_coverage_render(descriptor["render"])


def _validate_code_coverage_render(render: dict[str, Any]) -> None:
    """Require the finalized coverage evidence the coverage template reads."""
    metrics = render.get("code_coverage")
    if not isinstance(metrics, dict):
        raise ValueError("Code coverage publication requires render.code_coverage")
    for key in (
        "coverageSuitePath",
        "needsHumanIntervention",
        "runCoverage",
        "apiJacoco",
        "deepJacoco",
    ):
        if key not in metrics:
            raise ValueError(f"Code coverage evidence is missing {key!r}")
    _validate_run_coverage(metrics["runCoverage"])
    for key in ("apiJacoco", "deepJacoco"):
        evidence = metrics[key]
        if not isinstance(evidence, dict) or not {"baseline", "final"} <= evidence.keys():
            raise ValueError(f"Code coverage evidence {key!r} needs baseline and final")
        for phase in ("baseline", "final"):
            snapshot = evidence[phase]
            if not isinstance(snapshot, dict) or not {"total", "covered"} <= snapshot.keys():
                raise ValueError(f"Code coverage {key!r} {phase} needs total and covered")


def _validate_run_coverage(run: Any) -> None:
    """The whole-run block the body renders every figure from.

    The renderer divides nothing itself, so a descriptor missing a checkpoint or
    a universe count cannot be rendered against a fallback denominator — it is
    rejected here instead (§forge/WF-code-coverage-improvement.4.1).
    """
    if not isinstance(run, dict):
        raise ValueError("Code coverage runCoverage must be an object")
    for key in ("universe", "apiUniverse", "deepUniverse"):
        if not isinstance(run.get(key), int):
            raise ValueError(f"Code coverage runCoverage needs an integer {key!r}")
    checkpoints = run.get("checkpoints")
    if not isinstance(checkpoints, list) or len(checkpoints) != len(CHECKPOINT_LABELS):
        raise ValueError(
            f"Code coverage runCoverage needs {len(CHECKPOINT_LABELS)} checkpoints"
        )
    for point in checkpoints:
        if not isinstance(point, dict) or point.get("name") not in CHECKPOINT_LABELS:
            raise ValueError("Code coverage checkpoint names an unknown instant")
        missing = {
            "apiCovered", "deepCovered", "covered", "uncovered", "coveragePercent",
        } - point.keys()
        if missing:
            raise ValueError(
                f"Code coverage checkpoint {point['name']!r} is missing {sorted(missing)}"
            )
    phases = run.get("phases")
    if not isinstance(phases, list) or len(phases) != len(PHASE_LABELS):
        raise ValueError(f"Code coverage runCoverage needs {len(PHASE_LABELS)} phases")
    for phase in phases:
        if not isinstance(phase, dict) or phase.get("name") not in PHASE_LABELS:
            raise ValueError("Code coverage phase gain names an unknown phase")
        if not {"covered", "coveragePercentagePoints"} <= phase.keys():
            raise ValueError(
                f"Code coverage phase {phase['name']!r} needs covered and "
                "coveragePercentagePoints"
            )


def _path_changed(head_sha: str, path: str) -> bool:
    result = subprocess.run(
        ["git", "diff-tree", "--no-commit-id", "--name-only", "-r", head_sha, "--", path],
        stdout=subprocess.PIPE,
        text=True,
        check=True,
    )
    if result.stdout.strip():
        return True
    return subprocess.run(
        ["git", "cat-file", "-e", f"{head_sha}^:{path}"],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    ).returncode != 0


def render_publication(
        descriptor: dict[str, Any],
        validated: ValidatedPublication | None = None,
) -> tuple[str, str]:
    """Render the pre-Actions PR body shape from validated descriptor data (§forge/GIT-pr-body).

    Publication moved into Actions but the rendered body did not change with it: every
    template below reproduces the layout its local builder produced, so reviewers read the
    same sections in the same order as before the handoff.
    """
    template = descriptor["template_type"]
    builder = _TEMPLATE_BUILDERS.get(template)
    if builder is None:
        raise ValueError(f"Unsupported template type: {template}")
    title, body = builder(descriptor, validated)
    body += f"\nForge-Publication-ID: {descriptor['publication_id']}\n"
    return title, _bound_body(body)


def _issue_reference(descriptor: dict[str, Any]) -> str:
    """Chunked runs keep the issue open until the final chunk (§forge/GIT-chunked-linking)."""
    modifiers = descriptor["modifiers"]
    if modifiers["chunked_dynamic_access"] and not modifiers["chunk_final"]:
        return f"Refs: #{descriptor['issue_number']}"
    return f"Fixes: #{descriptor['issue_number']}"


def _chunked_title_suffix(descriptor: dict[str, Any]) -> str:
    modifiers = descriptor["modifiers"]
    if modifiers["chunked_dynamic_access"] and not modifiers["chunk_final"]:
        return " (chunked dynamic-access)"
    return ""


def _render_library_update_request(
        descriptor: dict[str, Any],
        validated: ValidatedPublication | None,
) -> tuple[str, str]:
    coordinates = descriptor["library"]["coordinates"]
    render = descriptor["render"]
    metrics = _summary_metrics(descriptor)
    title = f"[GenAI] Improve coverage for {coordinates} using {_model_display_name(descriptor)}"
    title += _chunked_title_suffix(descriptor)

    metadata_comparison_lines = ""
    before_entries = render.get("baseline_metadata_entries")
    after_entries = render.get("current_metadata_entries")
    if before_entries is not None and after_entries is not None:
        metadata_comparison_lines += (
            f"- Metadata entries (before): {before_entries}\n"
            f"- Metadata entries (after): {after_entries}\n"
        )
        before_test = render.get("baseline_test_only_entries")
        after_test = render.get("current_test_only_entries")
        if before_test or after_test:
            metadata_comparison_lines += (
                f"- Test-only metadata entries (before): {before_test or 0}\n"
                f"- Test-only metadata entries (after): {after_test or 0}\n"
            )

    update_target = render.get("library_update_target")
    update_target_lines = ""
    if isinstance(update_target, dict):
        update_target_lines = (
            f"- Requested coordinate: `{update_target.get('requested_coordinate') or coordinates}`\n"
            f"- Match type: `{update_target.get('match_type') or 'unknown'}`\n"
            f"- Matched metadata version: `{update_target.get('matched_metadata_version') or 'none'}`\n"
            f"- Matched test version: `{update_target.get('matched_test_version') or 'none'}`\n"
            f"- Resolved metadata version: `{update_target.get('resolved_metadata_version') or 'unknown'}`\n"
            f"- Resolved test version: `{update_target.get('resolved_test_version') or 'unknown'}`\n"
        )

    verification = descriptor["local_ci_verification"]
    validation_status = str(verification.get("status") or "unknown")
    validation_coordinates = [coordinates]
    if isinstance(update_target, dict):
        parts = coordinates.split(":")
        resolved = update_target.get("resolved_metadata_version")
        if len(parts) == 3 and isinstance(resolved, str) and resolved and resolved != parts[2]:
            validation_coordinates.append(f"{parts[0]}:{parts[1]}:{resolved}")
    validation_commands = ", ".join(
        f"`./gradlew test -Pcoordinates={coordinate}`" for coordinate in validation_coordinates
    )

    body = f"""
## What does this PR do?

{_issue_reference(descriptor)}

This PR improves dynamic-access coverage for {coordinates} by generating additional tests.

Summary:
{_format_chunked_dynamic_access_summary(descriptor)}\
- Validation commands: {validation_commands}
- Validation result: `{validation_status}`
{update_target_lines}\
- Strategy: {descriptor.get('strategy_name', '')}
- Agent: {_agent_name(descriptor)}
- Model: {_model_display_name(descriptor)}
- Input tokens: {metrics.get('input_tokens_used', 0)}
- Cached input tokens: {metrics.get('cached_input_tokens_used', 0)}
- Output tokens: {metrics.get('output_tokens_used', 0)}
{metadata_comparison_lines}\
- Iterations: {metrics.get('iterations', 0)}
- Library coverage percentage: {metrics.get('code_coverage_percent', 0)}
- Generated lines of code: {metrics.get('generated_loc', 0)}
- Tested library lines of code: {metrics.get('tested_library_loc', 0)}
"""
    body += "\n" + _format_forge_revision_section(descriptor) + "\n"
    baseline_stats = render.get("baseline_stats")
    library_stats = render.get("library_stats")
    if baseline_stats or library_stats:
        body += "\n" + _format_stats_before_after(baseline_stats, library_stats, coordinates)
    body += _format_post_generation_intervention(descriptor)
    body += _format_alias_split_section(render.get("alias_split"))
    body += _format_local_ci_verification_section(verification)
    return title, body


def _render_library_new_request(
        descriptor: dict[str, Any],
        validated: ValidatedPublication | None,
) -> tuple[str, str]:
    coordinates = descriptor["library"]["coordinates"]
    render = descriptor["render"]
    metrics = _summary_metrics(descriptor)
    title = f"[GenAI] Add support for {coordinates} using {_model_display_name(descriptor)}"
    title += _chunked_title_suffix(descriptor)

    entries_found = int(metrics.get("metadata_entries", 0) or 0)
    test_only_entries = int(metrics.get("test_only_metadata_entries", 0) or 0)
    test_only_metadata_entries_line = ""
    if test_only_entries > 0:
        test_only_metadata_entries_line = f"- Test-only metadata entries: {test_only_entries}\n"

    body = f"""
## What does this PR do?

{_issue_reference(descriptor)}

This PR introduces tests and metadata for {coordinates}, enabling support for this library.

Summary:
{_format_chunked_dynamic_access_summary(descriptor)}\
- Strategy: {descriptor.get('strategy_name', '')}
- Agent: {_agent_name(descriptor)}
- Model: {_model_display_name(descriptor)}
- Input tokens: {metrics.get('input_tokens_used', 0)}
- Cached input tokens: {metrics.get('cached_input_tokens_used', 0)}
- Output tokens: {metrics.get('output_tokens_used', 0)}
- Metadata entries: {entries_found}
{test_only_metadata_entries_line}\
- Iterations: {metrics.get('iterations', 0)}
- Library coverage percentage: {metrics.get('code_coverage_percent', 0)}
- Generated lines of code: {metrics.get('generated_loc', 0)}
- Tested library lines of code: {metrics.get('tested_library_loc', 0)}
"""
    library_stats = render.get("library_stats")
    body += _format_dynamic_access_metadata_entry_note(
        entries_found, library_stats, render.get("dynamic_access_evidence"),
    )
    body += "\n" + _format_forge_revision_section(descriptor) + "\n"
    if library_stats:
        body += "\n" + _format_stats_section(library_stats) + "\n"
    body += _format_post_generation_intervention(descriptor)
    body += _format_local_ci_verification_section(descriptor["local_ci_verification"])
    return title, body


def _render_java_fix(
        descriptor: dict[str, Any],
        validated: ValidatedPublication | None,
        *,
        failure_description: str,
) -> tuple[str, str]:
    coordinates = descriptor["library"]["coordinates"]
    previous = descriptor.get("previous_library") or {}
    previous_coordinates = previous.get("coordinates")
    metrics = _summary_metrics(descriptor)
    title = f"[GenAI] Test fix for {coordinates} using {_model_display_name(descriptor)}"

    coverage_deferred = any(
        follow_up["type"] == "deferred_dynamic_access_coverage"
        for follow_up in descriptor["follow_ups"]
    )
    metadata_entry_lines, coverage_lines = _format_generation_statistics_blocks(
        metrics, coverage_deferred,
    )

    deferred_section = _format_deferred_dynamic_access_section(descriptor)
    stats_diff_section = ""
    if not coverage_deferred and previous_coordinates:
        stats_diff = _format_stats_diff(validated, previous_coordinates, coordinates)
        if stats_diff:
            stats_diff_section = f"{stats_diff}\n"

    body = f"""## What does this PR do?

{_issue_reference(descriptor)}

This PR provides test fixes and new metadata for {coordinates}, addressing {failure_description} caused by changes in the updated library version.

Summary:
- Strategy: {descriptor.get('strategy_name', '')}
- Agent: {_agent_name(descriptor)}
- Model: {_model_display_name(descriptor)}
- Input tokens: {int(metrics.get('input_tokens_used', 0) or 0)}
- Cached input tokens: {int(metrics.get('cached_input_tokens_used', 0) or 0)}
- Output tokens: {int(metrics.get('output_tokens_used', 0) or 0)}
{metadata_entry_lines}\
- Iterations: {int(metrics.get('iterations', 0) or 0)}
{coverage_lines}\

{deferred_section}{_format_forge_revision_section(descriptor)}
{stats_diff_section}{_format_bounded_test_diff_section(validated)}
"""
    body += _format_post_generation_intervention(descriptor)
    body += _format_local_ci_verification_section(descriptor["local_ci_verification"])
    return title, body


def _render_javac_fix(
        descriptor: dict[str, Any],
        validated: ValidatedPublication | None,
) -> tuple[str, str]:
    return _render_java_fix(
        descriptor, validated, failure_description="compile java failures",
    )


def _render_java_run_fix(
        descriptor: dict[str, Any],
        validated: ValidatedPublication | None,
) -> tuple[str, str]:
    return _render_java_fix(
        descriptor, validated, failure_description="runtime java test failures",
    )


def _render_native_image_run_fix(
        descriptor: dict[str, Any],
        validated: ValidatedPublication | None,
) -> tuple[str, str]:
    coordinates = descriptor["library"]["coordinates"]
    previous_coordinates = (descriptor.get("previous_library") or {}).get("coordinates")
    render = descriptor["render"]
    title = f"[Automation] Generated metadata for {coordinates}"

    previous_entries = int(render.get("baseline_metadata_entries") or 0)
    current_entries = int(render.get("current_metadata_entries") or 0)
    previous_test_entries = int(render.get("baseline_test_only_entries") or 0)
    current_test_entries = int(render.get("current_test_only_entries") or 0)
    previous_test_entries_line = ""
    if previous_test_entries:
        previous_test_entries_line = (
            f"- Test-only metadata entries (previous `{previous_coordinates}`): {previous_test_entries}\n"
        )
    current_test_entries_line = ""
    if current_test_entries:
        current_test_entries_line = (
            f"- Test-only metadata entries (new `{coordinates}`): {current_test_entries}\n"
        )
    previous_coverage = float((render.get("baseline_stats") or {}).get("coverage_percent", 0) or 0)
    current_coverage = float((render.get("library_stats") or {}).get("coverage_percent", 0) or 0)

    metrics_section = (
        "\n\nSummary:\n"
        f"- Metadata entries (previous `{previous_coordinates}`): {previous_entries}\n"
        f"{previous_test_entries_line}"
        f"- Metadata entries (new `{coordinates}`): {current_entries}\n"
        f"{current_test_entries_line}"
        f"- Library coverage (previous): {previous_coverage:.2f}%\n"
        f"- Library coverage (new): {current_coverage:.2f}%"
    )
    body = (
        "## What does this PR do?\n\n"
        f"Fixes: {REPOSITORY}#{descriptor['issue_number']}\n\n"
        f"This PR provides new metadata needed for the {coordinates}, "
        "addressing Native Image run failures caused by changes in the updated library version."
        f"{metrics_section}"
        f"{_format_forge_metrics_summary_section(descriptor)}"
        f"\n\n{_format_forge_revision_section(descriptor)}"
        f"{_format_stats_diff(validated, previous_coordinates, coordinates)}"
        f"{_format_bounded_test_diff_section(validated)}"
    )
    if _has_severe_metadata_drop(descriptor):
        retained_percent = current_entries / previous_entries * 100 if previous_entries else 0
        body += (
            "\n\n### Human Intervention: Severe Metadata Drop\n\n"
            "Forge detected a severe drop in reachability metadata entries for this "
            "Native Image run fix. This PR needs human review unless the branch includes "
            "concrete proof that the new library version no longer needs the removed "
            "registrations.\n\n"
            f"- Previous metadata entries (`{previous_coordinates}`): {previous_entries}\n"
            f"- New metadata entries (`{coordinates}`): {current_entries}\n"
            f"- Retained metadata entries: {retained_percent:.2f}%"
        )
    body += _format_local_ci_verification_section(descriptor["local_ci_verification"])
    return title, body


def _render_not_for_native_image(
        descriptor: dict[str, Any],
        validated: ValidatedPublication | None,
) -> tuple[str, str]:
    library = descriptor["library"]
    group = library["group"]
    artifact = library["artifact"]
    render = descriptor["render"]
    title = f"[GenAI] Mark {group}:{artifact} as not for Native Image"
    body = f"""
## What does this PR do?

Fixes: #{descriptor['issue_number']}

This PR records `{group}:{artifact}` as `not-for-native-image`, so automation and downstream tools know this artifact is intentionally not a GraalVM Native Image reachability metadata target.

Reason:
- {render.get('reason')}
"""
    replacement = render.get("replacement")
    if replacement:
        body += f"\nReplacement guidance:\n- {replacement}\n"
    body += "\n" + _format_forge_revision_section(descriptor)
    body += _format_local_ci_verification_section(descriptor["local_ci_verification"])
    return title, body


def _signed(value: int | float) -> str:
    return f"{'+' if value > 0 else ''}{value}"


#: Checkpoint name -> the row label a reader sees.
CHECKPOINT_LABELS: dict[str, str] = {
    "runStart": "Run start",
    "afterApiPhase": "After Simple Jacoco guidance phase",
    "final": "After PGO guidance phase (final)",
}

#: Phase name -> the row label a reader sees.
PHASE_LABELS: dict[str, str] = {
    "api": "Simple Jacoco guidance phase",
    "deep": "PGO guidance phase",
}


def _coverage_universe_lines(run: dict[str, Any]) -> list[str]:
    """The run as one timeline on one denominator.

    Finalization froze the universe and counted every checkpoint over it, so this
    renderer computes no denominator of its own: it reports the counting that
    already happened where the JaCoCo reports are. Each phase's gain is the
    distance from the checkpoint the previous phase ended on, and the phase gains
    sum to the run's gain. Reporting each phase against its own roster instead
    put two different instants of the run on two different scales
    (§forge/WF-code-coverage-improvement.4.1).
    """
    universe = int(run["universe"])
    api_universe = int(run["apiUniverse"])
    deep_universe = int(run["deepUniverse"])
    checkpoints: list[dict[str, Any]] = run["checkpoints"]
    lines = [
        f"Every figure divides by the same denominator: the {universe} library "
        f"methods JaCoCo can rule on, made up of {api_universe} public API "
        f"methods and {deep_universe} internal methods, which are disjoint "
        "by construction. Each checkpoint is counted over that one frozen set of "
        "method ids from a single JaCoCo report, so every phase starts where the "
        "previous phase ended.",
        "",
        "| Checkpoint | Covered | Share |",
        "|---|--:|--:|",
    ]
    lines += [
        f"| {CHECKPOINT_LABELS[point['name']]} | {point['covered']}/{universe} "
        f"| {point['coveragePercent']}% |"
        for point in checkpoints
    ]
    lines.append("")
    lines += [
        f"- {PHASE_LABELS[phase['name']]}: {_signed(phase['covered'])} methods, "
        f"{_signed(phase['coveragePercentagePoints'])}pp"
        for phase in run["phases"]
    ]
    first = checkpoints[0]
    last = checkpoints[-1]
    lines += [
        f"- Run total: {_signed(int(last['covered']) - int(first['covered']))} methods, "
        f"{_signed(round(last['coveragePercent'] - first['coveragePercent'], 2))}pp",
        f"- Remaining uncovered: {last['uncovered']} of {universe}",
        "",
        "Where the coverage and the remaining headroom sit:",
        "",
        "| Method universe | Run start | Final | Still uncovered |",
        "|---|--:|--:|--:|",
        f"| Public API | {first['apiCovered']}/{api_universe} | "
        f"{last['apiCovered']}/{api_universe} | "
        f"{api_universe - int(last['apiCovered'])} |",
        f"| Internal | {first['deepCovered']}/{deep_universe} | "
        f"{last['deepCovered']}/{deep_universe} | "
        f"{deep_universe - int(last['deepCovered'])} |",
    ]
    return lines


def _token_cell(value: Any) -> str:
    """`n/a` keeps an unmeasured phase from reading as a free one."""
    return f"{value:,}" if isinstance(value, int) else "n/a"


def _coverage_token_lines(rows: list[dict[str, Any]]) -> list[str]:
    if not rows:
        return []
    lines = [
        "## Token usage",
        "",
        "| Phase | Input | Input (cached) | Output |",
        "|---|--:|--:|--:|",
    ]
    totals = {"input": 0, "cached": 0, "output": 0}
    for row in rows:
        for key in totals:
            value = row.get(key)
            if isinstance(value, int):
                totals[key] += value
        lines.append(
            f"| {row['phase']} | {_token_cell(row.get('input'))} | "
            f"{_token_cell(row.get('cached'))} | {_token_cell(row.get('output'))} |"
        )
    lines.append(
        f"| **Total** | **{totals['input']:,}** | "
        f"**{totals['cached']:,}** | **{totals['output']:,}** |"
    )
    lines += ["", "Input is uncached input tokens; Input (cached) is cache reads."]
    return lines


def _render_code_coverage_improvement(
        descriptor: dict[str, Any],
        validated: ValidatedPublication | None,
) -> tuple[str, str]:
    """Render the JaCoCo evidence the coverage workflow finalized.

    The descriptor carries the schema-validated `final-metrics.json` and the
    per-phase token accounting; this renderer only reports them, exactly as the
    local helper did before publication moved into Actions (§forge/WF-code-coverage-improvement.4).
    """
    coordinates = descriptor["library"]["coordinates"]
    render = descriptor["render"]
    metrics = render["code_coverage"]
    model = render.get("worker_model") or _model_display_name(descriptor)
    title = f"[GenAI] Improve code coverage for {coordinates} using {model}"

    # Always closing: the route has no chunked mode, so every published run is
    # the run that finishes its issue (§forge/GIT-issue-linking).
    lines = [
        "## Code coverage improvement",
        "",
        f"Fixes: #{descriptor['issue_number']}",
        "",
        f"- Coordinate: `{coordinates}`",
        f"- Coverage suite path: `{metrics['coverageSuitePath']}`",
        f"- Model: {model}",
        f"- Needs human intervention: {'yes' if metrics['needsHumanIntervention'] else 'no'}",
        "",
        "## JaCoCo coverage",
        "",
    ]
    lines += _coverage_universe_lines(metrics["runCoverage"])
    lines += [""]
    token_lines = _coverage_token_lines(render.get("token_usage") or [])
    if token_lines:
        lines += token_lines + [""]

    # No Forge revision block: this workflow is driven by a Rhei template rather
    # than a Forge strategy revision, and the publication ID trailer plus the
    # model in the head branch already identify the run that produced the body.
    body = "\n".join(lines)
    body += _format_post_generation_intervention(descriptor)
    body += _format_local_ci_verification_section(descriptor["local_ci_verification"])
    return title, body


_TEMPLATE_BUILDERS = {
    "library-update-request": _render_library_update_request,
    "library-new-request": _render_library_new_request,
    "fixes-javac-fail": _render_javac_fix,
    "fixes-java-run-fail": _render_java_run_fix,
    "fixes-native-image-run-fail": _render_native_image_run_fix,
    "not-for-native-image": _render_not_for_native_image,
    "code-coverage-improvement": _render_code_coverage_improvement,
}


def _summary_metrics(descriptor: dict[str, Any]) -> dict[str, Any]:
    reference = descriptor.get("metrics") or {}
    return reference.get("summary") or {}


def _model_display_name(descriptor: dict[str, Any]) -> str:
    metrics_reference = descriptor.get("metrics") or {}
    value = metrics_reference.get("model") or descriptor.get("strategy_name") or "Forge"
    return str(value).rsplit("/", 1)[-1]


def _agent_name(descriptor: dict[str, Any]) -> str:
    metrics_reference = descriptor.get("metrics") or {}
    return str(metrics_reference.get("agent") or "")


def _format_forge_revision_section(descriptor: dict[str, Any]) -> str:
    forge = descriptor["forge"]
    return (
        "### Forge\n\n"
        f"- Forge monitored branch: `{forge['monitored_branch']}`\n"
        f"- Forge branch: `{forge['branch']}`\n"
        f"- Forge commit hash: `{forge['commit']}`\n"
    )


def _format_chunked_dynamic_access_summary(descriptor: dict[str, Any]) -> str:
    if not descriptor["modifiers"]["chunked_dynamic_access"]:
        return ""
    report = descriptor["render"].get("dynamic_access")
    if not isinstance(report, dict) or not report:
        return "- Chunked dynamic-access: yes\n"
    return (
        "- Chunked dynamic-access: yes\n"
        f"- Chunk class threshold: {report.get('classThreshold') or 'unknown'}\n"
        f"- Current chunk class count: {report.get('currentChunkClassCount') or 'unknown'}\n"
        "- Processed dynamic-access classes: "
        f"completed={len(report.get('completedClasses') or [])}, "
        f"skipped={len(report.get('skippedClasses') or [])}, "
        f"exhausted={len(report.get('exhaustedClasses') or [])}, "
        f"failed={len(report.get('failedClasses') or [])}\n"
    )


def _format_post_generation_intervention(descriptor: dict[str, Any]) -> str:
    intervention = descriptor.get("post_generation_intervention")
    if not intervention:
        return ""
    return (
        "\n### Post-Generation Intervention\n\n"
        f"- Stage: `{intervention.get('stage', 'unknown')}`\n\n"
        f"- Intervention file: `{intervention.get('intervention_file', 'unknown')}`\n\n"
        f"{str(intervention.get('analysis_markdown', '')).strip()}\n"
    )


def _format_deferred_dynamic_access_section(descriptor: dict[str, Any]) -> str:
    for follow_up in descriptor["follow_ups"]:
        if follow_up["type"] != "deferred_dynamic_access_coverage":
            continue
        issue_number = follow_up["issue_number"]
        issue_url = f"https://github.com/{REPOSITORY}/issues/{issue_number}"
        return (
            "### Deferred Dynamic-Access Exploration\n\n"
            "Exploration was skipped after the repair succeeded because the "
            f"dynamic-access report contained **{follow_up['uncovered_class_count']} uncovered "
            f"classes**, above the configured threshold of **{follow_up['class_threshold']}**.\n\n"
            "Coverage work will continue in the newly opened "
            f"[library-update-request #{issue_number}]({issue_url}).\n\n"
            f"Refs: #{issue_number}\n"
            f"{_format_follow_up_trailer(issue_number)}\n\n"
        )
    return ""


def _format_generation_statistics_blocks(
        metrics: dict[str, Any],
        coverage_deferred: bool,
) -> tuple[str, str]:
    """Deferred runs only repaired the build, so entry and coverage counts stay out."""
    if coverage_deferred:
        return "", ""

    metadata_entry_lines = f"- Metadata entries: {int(metrics.get('metadata_entries', 0) or 0)}\n"
    test_only_entries = int(metrics.get("test_only_metadata_entries", 0) or 0)
    if test_only_entries > 0:
        metadata_entry_lines += f"- Test-only metadata entries: {test_only_entries}\n"

    coverage_lines = f"- Library coverage percentage: {metrics.get('code_coverage_percent', 0)}\n"
    coverage_lines += (
        "- Previous library version metadata entries: "
        f"{int(metrics.get('previous_library_metadata_entries', 0) or 0)}\n"
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


def _format_follow_up_trailer(issue_number: int) -> str:
    """Machine-readable trailer that merge follow-up parses back out of the body."""
    return f"Forge-Unblocks-Issue: #{issue_number}"


def _format_alias_split_section(split: dict[str, Any] | None) -> str:
    if not isinstance(split, dict):
        return ""
    issue_number = split.get("follow_up_issue_number")
    issue_lines = ""
    if isinstance(issue_number, int):
        issue_lines = (
            f"Refs: #{issue_number}\n"
            f"{_format_follow_up_trailer(issue_number)}\n"
        )
    return (
        "\n### Tested-Version Alias Split\n\n"
        f"- First failing JVM alias: `{split.get('failed_version')}`\n"
        f"- Generated prefix retained on `{split.get('current_metadata_version')}`: "
        f"{_format_version_list(split.get('passing_versions'))}\n"
        f"- Baseline successor entry: `{split.get('successor_metadata_version')}` with "
        f"{_format_version_list(split.get('successor_versions'))}\n"
        f"- Baseline metadata copied from: `{split.get('original_metadata_version')}`\n"
        f"- Baseline tests copied from: `{split.get('original_test_version')}`\n"
        f"{issue_lines}"
    )


def _format_version_list(value: Any) -> str:
    if not isinstance(value, list) or not value:
        return "none"
    return ", ".join(f"`{item}`" for item in value)


def _format_local_ci_verification_section(verification: dict[str, Any] | None) -> str:
    if not isinstance(verification, dict):
        return ""
    commands = verification.get("commands")
    command_count = len(commands) if isinstance(commands, list) else 0
    fixups = verification.get("fixups")
    fixup_count = len(fixups) if isinstance(fixups, list) else 0
    repo_fix_paths = verification.get("repo_fix_paths")
    repo_fix_list = repo_fix_paths if isinstance(repo_fix_paths, list) else []
    body = (
        "\n### Local CI Verification\n\n"
        f"- Status: `{verification.get('status', 'unknown')}`\n"
        f"- Commands run: {command_count}\n"
        f"- Fixup attempts: {fixup_count}\n"
    )
    if verification.get("human_intervention_required"):
        body += "- Human intervention: required because repository-level files changed during verification.\n"
    if repo_fix_list:
        body += "- Repository-level fix paths:\n"
        body += "".join(f"  - `{path}`\n" for path in repo_fix_list[:20])
    return body


def _format_dynamic_access_entry(stats: dict[str, Any]) -> str:
    return "{covered}/{total} covered calls ({ratio:.2f}%)".format(
        covered=stats["coveredCalls"],
        total=stats["totalCalls"],
        ratio=stats["coverageRatio"] * 100,
    )


def _is_dynamic_access_stats_entry(stats: Any) -> bool:
    return isinstance(stats, dict) and all(
        key in stats for key in ("coveredCalls", "totalCalls", "coverageRatio")
    )


def _format_coverage_entry(entry: Any) -> str:
    if entry == "N/A":
        return "N/A"
    return "{covered}/{total} ({ratio:.2f}%)".format(
        covered=entry["covered"],
        total=entry["total"],
        ratio=entry["ratio"] * 100,
    )


def _format_dynamic_access_section(dynamic_access_stats: Any) -> str:
    if not _is_dynamic_access_stats_entry(dynamic_access_stats):
        return ""
    lines = [
        "Dynamic access coverage:",
        f"- Overall: {_format_dynamic_access_entry(dynamic_access_stats)}",
    ]
    breakdown = dynamic_access_stats.get("breakdown", {})
    for category in sorted(breakdown):
        if not _is_dynamic_access_stats_entry(breakdown[category]):
            continue
        display_name = category[0].upper() + category[1:]
        lines.append(f"- {display_name}: {_format_dynamic_access_entry(breakdown[category])}")
    return "\n".join(lines)


def _format_library_coverage_section(library_coverage: dict[str, Any]) -> str:
    lines = ["Library coverage:"]
    for metric in ("instruction", "line", "method"):
        entry = library_coverage.get(metric)
        if entry != "N/A" and not isinstance(entry, dict):
            continue
        display_name = metric[0].upper() + metric[1:]
        lines.append(f"- {display_name}: {_format_coverage_entry(entry)}")
    return "\n".join(lines)


def _format_stats_section(version_stats: dict[str, Any]) -> str:
    sections = []
    dynamic_access = version_stats.get("dynamicAccess")
    if dynamic_access:
        dynamic_access_section = _format_dynamic_access_section(dynamic_access)
        if dynamic_access_section:
            sections.append(dynamic_access_section)
    library_coverage = version_stats.get("libraryCoverage")
    if library_coverage:
        sections.append(_format_library_coverage_section(library_coverage))
    if not sections:
        return ""
    return (
        "Stats from `stats/<groupId>/<artifactId>/<metadata-version>/stats.json`:\n\n"
        + "\n\n".join(sections)
    )


def _format_comparison_pair(
        old_label: str,
        new_label: str,
        old_entry: Any,
        new_entry: Any,
        formatter,
) -> list[str]:
    return [
        f"- {old_label}: {formatter(old_entry) if old_entry else 'N/A'}",
        f"- {new_label}: {formatter(new_entry) if new_entry else 'N/A'}",
    ]


def _format_stats_comparison(
        before_stats: dict[str, Any] | None,
        after_stats: dict[str, Any] | None,
        before_label: str,
        after_label: str,
        heading: str,
) -> str:
    lines = ["", heading, ""]

    before_da = before_stats.get("dynamicAccess") if before_stats else None
    after_da = after_stats.get("dynamicAccess") if after_stats else None
    if before_da or after_da:
        lines.append("#### Dynamic access coverage")
        lines.append("")
        lines.extend(_format_comparison_pair(
            before_label, after_label, before_da, after_da, _format_dynamic_access_entry,
        ))

        all_categories: set[str] = set()
        if before_da:
            all_categories.update(before_da.get("breakdown", {}).keys())
        if after_da:
            all_categories.update(after_da.get("breakdown", {}).keys())
        for category in sorted(all_categories):
            display_name = category[0].upper() + category[1:]
            before_category = before_da.get("breakdown", {}).get(category) if before_da else None
            after_category = after_da.get("breakdown", {}).get(category) if after_da else None
            lines.append("")
            lines.append(f"**{display_name}:**")
            lines.extend(_format_comparison_pair(
                before_label, after_label, before_category, after_category, _format_dynamic_access_entry,
            ))
        lines.append("")

    before_coverage = before_stats.get("libraryCoverage") if before_stats else None
    after_coverage = after_stats.get("libraryCoverage") if after_stats else None
    if before_coverage or after_coverage:
        lines.append("#### Library coverage")
        lines.append("")
        for metric in ("instruction", "line", "method"):
            display_name = metric[0].upper() + metric[1:]
            before_entry = before_coverage.get(metric) if before_coverage else None
            after_entry = after_coverage.get(metric) if after_coverage else None
            before_entry = before_entry if isinstance(before_entry, dict) or before_entry == "N/A" else None
            after_entry = after_entry if isinstance(after_entry, dict) or after_entry == "N/A" else None
            if before_entry or after_entry:
                lines.append(f"**{display_name}:**")
                lines.extend(_format_comparison_pair(
                    before_label, after_label, before_entry, after_entry, _format_coverage_entry,
                ))
                lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def _format_stats_before_after(
        before_stats: dict[str, Any] | None,
        after_stats: dict[str, Any] | None,
        coordinates: str,
) -> str:
    if before_stats is None and after_stats is None:
        return ""
    if before_stats == after_stats:
        return (
            "\n### Stats comparison (before vs after)\n\n"
            f"No change in stats for `{coordinates}`.\n"
        )
    return _format_stats_comparison(
        before_stats,
        after_stats,
        f"Before ({coordinates})",
        f"After ({coordinates})",
        f"### Stats comparison for `{coordinates}`",
    )


def _format_stats_diff(
        validated: ValidatedPublication | None,
        old_coordinates: str | None,
        new_coordinates: str,
) -> str:
    """Compare two versions' shipped stats, read from the publication tree as data."""
    if validated is None or not old_coordinates:
        return ""
    old_stats = _load_stats_at_head(validated, old_coordinates)
    new_stats = _load_stats_at_head(validated, new_coordinates)
    if old_stats is None and new_stats is None:
        return ""
    heading = "### Stats from `stats/<groupId>/<artifactId>/<metadata-version>/stats.json`"
    if old_stats == new_stats:
        return (
            f"\n{heading}\n\n"
            f"Same entry for both `{old_coordinates}` and `{new_coordinates}`.\n"
        )
    return _format_stats_comparison(
        old_stats, new_stats, old_coordinates, new_coordinates, heading,
    )


def _load_stats_at_head(
        validated: ValidatedPublication,
        coordinates: str,
) -> dict[str, Any] | None:
    group, artifact, version = coordinates.split(":")
    path = f"stats/{group}/{artifact}/{version}/stats.json"
    try:
        return read_json_at_commit(validated.head_sha, path)
    except (RuntimeError, TypeError, json.JSONDecodeError):
        return None


def _format_dynamic_access_metadata_entry_note(
        metadata_entries: int,
        library_stats: dict[str, Any] | None,
        evidence: dict[str, Any] | None,
) -> str:
    covered_calls = _extract_covered_dynamic_access_calls(library_stats)
    if covered_calls is None:
        return ""
    if metadata_entries > 0 and covered_calls < metadata_entries * DYNAMIC_ACCESS_METADATA_ENTRY_NOTE_RATIO:
        return ""
    if metadata_entries <= 0 and covered_calls <= 0:
        return ""

    lines = [
        "",
        "### Metadata/dynamic-access evidence",
        "",
        f"- Covered dynamic-access calls: {covered_calls}",
        f"- Metadata entries: {metadata_entries}",
        "- These counts are different dimensions: covered dynamic-access calls count observed call sites, "
        "while metadata entries count generated reachability-config items. Depending on the access type, "
        "a single metadata rule can cover multiple observed call sites, or no shipped rule may be required "
        "when the covered access does not target fixed library-owned metadata.",
    ]
    call_sites = (evidence or {}).get("covered_call_sites") or []
    metadata_rules = (evidence or {}).get("metadata_rules") or []
    if call_sites:
        lines.append("- Covered call sites:")
        lines.extend(f"  - {call_site}" for call_site in call_sites)
    if metadata_rules:
        lines.append("- Generated metadata rules:")
        lines.extend(f"  - {metadata_rule}" for metadata_rule in metadata_rules)
    lines.append(
        "- Use the call-site and metadata-rule lists together to review whether the observed dynamic-access "
        "paths are explained by the generated reachability metadata."
    )
    return "\n".join(lines) + "\n"


def _extract_covered_dynamic_access_calls(library_stats: dict[str, Any] | None) -> int | None:
    if not isinstance(library_stats, dict):
        return None
    dynamic_access = library_stats.get("dynamicAccess")
    if not isinstance(dynamic_access, dict):
        return None
    covered_calls = dynamic_access.get("coveredCalls")
    if not isinstance(covered_calls, int):
        return None
    return covered_calls


def _format_forge_metrics_summary_section(descriptor: dict[str, Any]) -> str:
    metrics = _summary_metrics(descriptor)
    strategy_name = descriptor.get("strategy_name", "")
    if not strategy_name and not metrics:
        return ""
    return (
        "\n"
        f"- Strategy: {strategy_name}\n"
        f"- Agent: {_agent_name(descriptor)}\n"
        f"- Model: {_model_display_name(descriptor)}\n"
        f"- Input tokens: {int(metrics.get('input_tokens_used', 0) or 0)}\n"
        f"- Cached input tokens: {int(metrics.get('cached_input_tokens_used', 0) or 0)}\n"
        f"- Output tokens: {int(metrics.get('output_tokens_used', 0) or 0)}\n"
        f"- Iterations: {int(metrics.get('iterations', 0) or 0)}"
    )


def _format_bounded_test_diff_section(validated: ValidatedPublication | None) -> str:
    if validated is None:
        return ""
    descriptor = validated.descriptor
    library = descriptor["library"]
    pathspec = f"tests/src/{library['group']}/{library['artifact']}"
    base_commit = descriptor["base_commit"]
    stat = git("diff", "--stat", base_commit, validated.head_sha, "--", pathspec).strip()
    diff = git(
        "diff", "--no-ext-diff", "--unified=3",
        base_commit, validated.head_sha, "--", pathspec,
    ).strip()
    if not stat and not diff:
        return ""
    if len(diff) > MAX_TEST_DIFF_CHARS:
        diff = diff[:MAX_TEST_DIFF_CHARS].rstrip() + "\n[diff truncated]"
    return (
        "\n### Test-Source Comparison\n\n"
        f"```text\n{stat or 'No test-source stat available.'}\n```\n\n"
        f"```diff\n{diff or 'No inline test-source diff available.'}\n```\n"
    )


def _has_severe_metadata_drop(descriptor: dict[str, Any]) -> bool:
    if descriptor["template_type"] != "fixes-native-image-run-fail":
        return False
    render = descriptor["render"]
    previous_entries = int(render.get("baseline_metadata_entries") or 0)
    current_entries = int(render.get("current_metadata_entries") or 0)
    return previous_entries > 0 and current_entries < previous_entries * SEVERE_METADATA_DROP_RATIO


def _bound_body(body: str) -> str:
    if len(body) <= MAX_BODY_CHARS:
        return body
    head_chars = MAX_BODY_CHARS - 2200
    return body[:head_chars].rstrip() + "\n\nGenerated detail was truncated.\n\n" + body[-2000:]


def publish(
        validated: ValidatedPublication,
        mode: str,
        reviewers: str,
) -> tuple[str, str, str | None]:
    descriptor = validated.descriptor
    if mode not in {"shadow", "live"}:
        raise ValueError("FORGE_PR_PUBLISH_MODE must be 'shadow' or 'live'")
    title, body = render_publication(descriptor, validated)
    if mode == "shadow":
        return title, body, None

    head = f"{REPOSITORY.split('/', 1)[0]}:{descriptor['branch']}"
    existing = gh_json(
        "api", f"repos/{REPOSITORY}/pulls", "--method", "GET",
        "-f", "state=all", "-f", f"head={head}", "-f", "per_page=100",
    )
    trailer = f"Forge-Publication-ID: {descriptor['publication_id']}"
    matching = [
        pull_request
        for pull_request in existing
        if trailer in str(pull_request.get("body") or "").splitlines()
    ]
    if len(matching) > 1:
        raise RuntimeError("Ambiguous pull requests for publication identity")
    if matching:
        pull_request = matching[0]
        if pull_request.get("merged_at"):
            return title, body, str(pull_request["html_url"])
        if pull_request.get("state") != "open":
            raise RuntimeError("Matching publication PR is closed without merge")
        _ensure_pull_request_metadata(pull_request, descriptor, reviewers)
        return title, body, str(pull_request["html_url"])
    if existing:
        raise RuntimeError("Head branch already has a PR with a different publication identity")

    pull_request = gh_json(
        "api", f"repos/{REPOSITORY}/pulls", "--method", "POST",
        "-f", f"title={title}", "-f", f"body={body}",
        "-f", f"head={descriptor['branch']}", "-f", f"base={BASE_BRANCH}",
    )
    _ensure_pull_request_metadata(pull_request, descriptor, reviewers)
    return title, body, str(pull_request["html_url"])


def _ensure_pull_request_metadata(
        pull_request: dict[str, Any],
        descriptor: dict[str, Any],
        reviewers: str,
) -> None:
    labels = list(ROUTE_LABELS[descriptor["task_type"]])
    modifiers = descriptor["modifiers"]
    if modifiers["chunked_dynamic_access"]:
        labels.append("chunked-dynamic-access")
    if modifiers["human_intervention"] or _has_severe_metadata_drop(descriptor):
        labels.append("human-intervention")
    run(
        [
            "gh", "api", f"repos/{REPOSITORY}/issues/{pull_request['number']}/labels",
            "--method", "POST", "--input", "-",
        ],
        input_text=json.dumps({"labels": sorted(set(labels))}),
    )

    reviewer_names = list(dict.fromkeys(
        value.strip() for value in reviewers.split(",") if value.strip()
    ))
    invalid_reviewers = [
        reviewer
        for reviewer in reviewer_names
        if re.fullmatch(r"[A-Za-z0-9](?:[A-Za-z0-9-]{0,38})", reviewer) is None
    ]
    if invalid_reviewers:
        raise ValueError(f"Invalid FORGE_PR_REVIEWERS entries: {invalid_reviewers}")
    if reviewer_names:
        run(
            [
                "gh", "api",
                f"repos/{REPOSITORY}/pulls/{pull_request['number']}/requested_reviewers",
                "--method", "POST", "--input", "-",
            ],
            input_text=json.dumps({"reviewers": reviewer_names}),
        )

def write_evidence(title: str, body: str, validated: ValidatedPublication, pr_url: str | None) -> None:
    evidence = f"# {title}\n\n{body}\n"
    Path("publication.md").write_text(evidence, encoding="utf-8")
    summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_path:
        with open(summary_path, "a", encoding="utf-8") as summary:
            summary.write(f"## Forge publication `{validated.descriptor['publication_id']}`\n\n")
            summary.write(f"- SHA: `{validated.head_sha}`\n")
            summary.write(f"- Descriptor: `{validated.descriptor_path}`\n")
            if pr_url:
                summary.write(f"- Pull request: {pr_url}\n")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=("validate", "publish"))
    parser.add_argument("--sha", required=True)
    parser.add_argument("--branch", required=True)
    parser.add_argument("--actor", required=True)
    parser.add_argument("--repository", required=True)
    parser.add_argument("--mode", default="shadow")
    parser.add_argument("--reviewers", default="")
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        validated = validate_publication(
            head_sha=args.sha,
            branch=args.branch,
            actor=args.actor,
            repository=args.repository,
        )
        if args.command == "publish":
            title, body, pr_url = publish(validated, args.mode, args.reviewers)
        else:
            title, body = render_publication(validated.descriptor, validated=validated)
            pr_url = None
        write_evidence(title, body, validated, pr_url)
        return 0
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        summary_path = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary_path:
            with open(summary_path, "a", encoding="utf-8") as summary:
                summary.write(f"## Forge publication validation failed\n\n- SHA: `{args.sha}`\n- Error: `{exc}`\n")
        return 1


if __name__ == "__main__":
    sys.exit(main())
