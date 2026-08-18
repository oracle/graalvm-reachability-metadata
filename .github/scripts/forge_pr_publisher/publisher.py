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
PROJECT_NUMBER = 30
MAX_BODY_CHARS = 60_000
MAX_TEST_DIFF_CHARS = 12_000
SEVERE_METADATA_DROP_RATIO = 0.25
PROTECTED_PREFIXES = (".github/", "forge/")
ISSUE_ROUTE_LABELS = {
    "library-new-request": "library-new-request",
    "library-update-request": "library-update-request",
    "fixes-javac-fail": "fails-javac-compile",
    "fixes-java-run-fail": "fails-java-run",
    "fixes-native-image-run-fail": "fails-native-image-run",
    "not-for-native-image": "library-new-request",
}
ROUTE_LABELS = {
    "library-new-request": ["GenAI", "library-new-request"],
    "library-update-request": ["GenAI", "library-update-request"],
    "fixes-javac-fail": ["GenAI", "fixes-javac-fail"],
    "fixes-java-run-fail": ["GenAI", "fixes-java-run-fail"],
    "fixes-native-image-run-fail": ["fixes-native-image-run-fail"],
    "not-for-native-image": ["GenAI", "library-new-request", "not-for-native-image"],
}


@dataclass(frozen=True)
class ValidatedPublication:
    descriptor: dict[str, Any]
    descriptor_path: str
    head_sha: str
    changed_paths: list[str]


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
        authorized_pushers: str,
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
    authorized = {value.strip() for value in authorized_pushers.split(",") if value.strip()}
    if actor not in authorized:
        raise ValueError(f"Triggering actor {actor!r} is not in FORGE_AUTHORIZED_PUSHERS")
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

    _validate_changed_paths(descriptor, changed_paths)
    _validate_route_fields(descriptor, changed_paths, head_sha)
    _validate_metrics_reference(head_sha, descriptor)
    _validate_issue(descriptor, repository)
    return ValidatedPublication(descriptor, descriptor_path, head_sha, changed_paths)


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


def _validate_route_fields(
        descriptor: dict[str, Any],
        changed_paths: list[str],
        head_sha: str,
) -> None:
    task_type = descriptor["task_type"]
    template_type = descriptor["template_type"]
    if task_type != "library-update-request" and template_type != task_type:
        raise ValueError(f"Task {task_type!r} cannot select template {template_type!r}")
    previous_library = descriptor.get("previous_library")
    if template_type in {
        "fixes-javac-fail",
        "fixes-java-run-fail",
        "fixes-native-image-run-fail",
    } and previous_library is None:
        raise ValueError(f"Template {template_type!r} requires previous_library")
    if task_type == "not-for-native-image":
        reason = descriptor["render"].get("reason")
        if not isinstance(reason, str) or not reason.strip():
            raise ValueError("Not-for-native-image publication requires a reason")
    elif descriptor.get("metrics") is None:
        raise ValueError(f"Publication task {task_type!r} requires committed execution metrics")

    modifiers = descriptor["modifiers"]
    non_final_chunk = modifiers["chunked_dynamic_access"] and not modifiers["chunk_final"]
    if non_final_chunk != (descriptor["status"] == "chunk_ready"):
        raise ValueError("Only a non-final chunk may use chunk_ready status")
    if modifiers["chunked_dynamic_access"] and task_type not in {
        "library-new-request",
        "library-update-request",
    }:
        raise ValueError(f"Task {task_type!r} cannot be a chunked dynamic-access publication")

    follow_up_types = [follow_up["type"] for follow_up in descriptor["follow_ups"]]
    if len(follow_up_types) != len(set(follow_up_types)):
        raise ValueError("Publication follow-up types must be unique")
    for follow_up in descriptor["follow_ups"]:
        if follow_up["type"] == "deferred_dynamic_access_coverage":
            if template_type not in {"fixes-javac-fail", "fixes-java-run-fail"}:
                raise ValueError("Deferred coverage follow-up requires a Java-fix template")
        elif task_type != "library-update-request":
            raise ValueError("Tested-version split follow-up requires a library-update task")

    verification = descriptor["local_ci_verification"]
    if verification["status"] != "success":
        raise ValueError("Publication requires successful local CI verification")
    if verification["base_commit"] != descriptor["base_commit"]:
        raise ValueError("Local CI base commit does not match descriptor base commit")
    final_commit = verification.get("final_commit")
    if final_commit and subprocess.run(
            ["git", "merge-base", "--is-ancestor", final_commit, head_sha],
            check=False,
    ).returncode != 0:
        raise ValueError("Local CI final commit is not an ancestor of the publication SHA")
    if any(command.get("returncode") != 0 for command in verification["commands"]):
        raise ValueError("Local CI evidence includes a failed command")
    repo_fix_paths = set(verification["repo_fix_paths"])
    if not repo_fix_paths.issubset(changed_paths):
        raise ValueError("Local CI repository-fix paths are not present in the publication diff")
    if verification["human_intervention_required"] != bool(repo_fix_paths):
        raise ValueError("Local CI human-intervention flag does not match repository-fix paths")


def _validate_issue(descriptor: dict[str, Any], repository: str) -> None:
    issue_number = int(descriptor["issue_number"])
    issue = gh_json("api", f"repos/{repository}/issues/{issue_number}")
    if not isinstance(issue, dict) or issue.get("pull_request"):
        raise ValueError(f"Descriptor issue #{issue_number} is not a repository issue")
    if issue.get("state") != "open":
        raise ValueError(f"Descriptor issue #{issue_number} is not open")
    coordinates = descriptor["library"]["coordinates"]
    issue_text = f"{issue.get('title') or ''}\n{issue.get('body') or ''}"
    if coordinates not in issue_text:
        raise ValueError(
            f"Descriptor coordinates {coordinates!r} do not match issue #{issue_number}"
        )
    labels = {
        str(label.get("name"))
        for label in issue.get("labels") or []
        if isinstance(label, dict) and label.get("name")
    }
    expected_label = ISSUE_ROUTE_LABELS[descriptor["task_type"]]
    if expected_label not in labels:
        raise ValueError(
            f"Descriptor issue #{issue_number} does not carry expected label {expected_label!r}"
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


def _validate_changed_paths(descriptor: dict[str, Any], paths: list[str]) -> None:
    library = descriptor["library"]
    group = library["group"]
    artifact = library["artifact"]
    allowed_prefixes = (
        f"metadata/{group}/{artifact}/",
        f"tests/src/{group}/{artifact}/",
        f"stats/{group}/{artifact}/",
    )
    repo_fix_paths = set(descriptor["local_ci_verification"].get("repo_fix_paths") or [])
    for path in paths:
        if path.startswith(PROTECTED_PREFIXES):
            raise ValueError(f"Protected path changed by publication branch: {path}")
        if path.startswith(allowed_prefixes) or path in repo_fix_paths:
            continue
        raise ValueError(f"Out-of-scope publication path: {path}")
    if descriptor["task_type"] == "not-for-native-image":
        allowed = {
            f"metadata/{group}/{artifact}/index.json",
            f"stats/{group}/{artifact}/{library['version']}/forge-publication.json",
        }
        unexpected = [path for path in paths if path not in allowed and path not in repo_fix_paths]
        if unexpected:
            raise ValueError(f"Not-for-native-image publication changed unexpected paths: {unexpected}")


def _validate_metrics_reference(head_sha: str, descriptor: dict[str, Any]) -> None:
    metrics_reference = descriptor.get("metrics")
    if metrics_reference is None:
        if descriptor["task_type"] != "not-for-native-image":
            raise ValueError("Only not-for-native-image publication may omit execution metrics")
        return
    metrics_file = read_json_at_commit(head_sha, metrics_reference["path"])
    timestamp = metrics_reference["timestamp"]
    coordinates = descriptor["library"]["coordinates"]
    matches = [
        entry
        for entry in metrics_file.values()
        if isinstance(entry, dict)
        and entry.get("timestamp") == timestamp
        and entry.get("library") == coordinates
    ]
    if len(matches) != 1:
        raise ValueError("Execution metrics reference does not identify exactly one committed run")
    entry = matches[0]
    if entry.get("metrics") != metrics_reference["summary"]:
        raise ValueError("Descriptor metrics summary does not match committed execution metrics")
    if (descriptor.get("strategy_name") or None) != (entry.get("strategy_name") or None):
        raise ValueError("Descriptor strategy does not match committed execution metrics")
    for key in ("agent", "model"):
        if (metrics_reference.get(key) or None) != (entry.get(key) or None):
            raise ValueError(f"Descriptor {key} does not match committed execution metrics")
    if descriptor["status"] != entry.get("status"):
        raise ValueError("Descriptor status does not match committed execution metrics")

def render_publication(
        descriptor: dict[str, Any],
        follow_up_numbers: dict[str, int] | None = None,
        validated: ValidatedPublication | None = None,
) -> tuple[str, str]:
    """Render trusted title and body text from validated descriptor data."""
    follow_up_numbers = follow_up_numbers or {}
    library = descriptor["library"]
    coordinates = library["coordinates"]
    template = descriptor["template_type"]
    model = _model_display_name(descriptor)
    if template == "library-new-request":
        title = f"[GenAI] Add support for {coordinates} using {model}"
        summary = f"This PR introduces tests and metadata for {coordinates}, enabling support for this library."
    elif template == "library-update-request":
        title = f"[GenAI] Improve coverage for {coordinates} using {model}"
        summary = f"This PR improves dynamic-access coverage for {coordinates} by generating additional tests."
    elif template == "fixes-javac-fail":
        title = f"[GenAI] Test fix for {coordinates} using {model}"
        summary = (
            f"This PR provides test fixes and new metadata for {coordinates}, addressing "
            "Java compilation failures caused by changes in the updated library version."
        )
    elif template == "fixes-java-run-fail":
        title = f"[GenAI] Test fix for {coordinates} using {model}"
        summary = (
            f"This PR provides test fixes and new metadata for {coordinates}, addressing "
            "Java runtime failures caused by changes in the updated library version."
        )
    elif template == "fixes-native-image-run-fail":
        title = f"[Automation] Generated metadata for {coordinates}"
        summary = (
            f"This PR provides metadata needed for {coordinates}, addressing Native Image "
            "runtime failures caused by changes in the updated library version."
        )
    else:
        title = f"[GenAI] Mark {library['group']}:{library['artifact']} as not for Native Image"
        reason = descriptor["render"]["reason"]
        summary = (
            f"This PR records `{library['group']}:{library['artifact']}` as "
            "`not-for-native-image`, so automation and downstream tools know it is "
            "intentionally not a reachability-metadata target.\n\n"
            f"Reason:\n- {reason}"
        )
        replacement = descriptor["render"].get("replacement")
        if replacement:
            summary += f"\n\nReplacement guidance:\n- {replacement}"

    issue_reference = f"Fixes: #{descriptor['issue_number']}"
    modifiers = descriptor["modifiers"]
    if modifiers["chunked_dynamic_access"] and not modifiers["chunk_final"]:
        issue_reference = f"Refs: #{descriptor['issue_number']}"
        title += " (chunked dynamic-access)"

    body = f"## What does this PR do?\n\n{issue_reference}\n\n{summary}\n"
    if template in {"library-new-request", "library-update-request"}:
        body += _render_generation_summary(descriptor, include_coverage=True)
        body += _render_chunk_summary(descriptor)
        body += _render_route_evidence(descriptor)
    elif template in {"fixes-javac-fail", "fixes-java-run-fail"}:
        coverage_deferred = any(
            follow_up["type"] == "deferred_dynamic_access_coverage"
            for follow_up in descriptor["follow_ups"]
        )
        body += _render_generation_summary(descriptor, include_coverage=not coverage_deferred)
        if not coverage_deferred:
            body += _render_route_evidence(descriptor)
        body += _render_test_comparison(validated)
    elif template == "fixes-native-image-run-fail":
        body += _render_native_image_summary(descriptor)
        body += _render_test_comparison(validated)

    body += _render_follow_ups(descriptor, follow_up_numbers)
    body += _render_intervention(descriptor)
    body += _render_local_ci(descriptor["local_ci_verification"])
    forge = descriptor["forge"]
    body += (
        "\n### Forge\n\n"
        f"- Forge monitored branch: `{forge['monitored_branch']}`\n"
        f"- Forge branch: `{forge['branch']}`\n"
        f"- Forge commit hash: `{forge['commit']}`\n"
        f"- Publication SHA: `{validated.head_sha if validated else 'validated at publish time'}`\n"
        f"\nForge-Publication-ID: {descriptor['publication_id']}\n"
    )
    return title, _bound_body(body)


def _model_display_name(descriptor: dict[str, Any]) -> str:
    metrics_reference = descriptor.get("metrics") or {}
    value = metrics_reference.get("model") or descriptor.get("strategy_name") or "Forge"
    return str(value).rsplit("/", 1)[-1]


def _render_generation_summary(
        descriptor: dict[str, Any],
        *,
        include_coverage: bool,
) -> str:
    reference = descriptor.get("metrics") or {}
    metrics = reference.get("summary") or {}
    lines = ["\nSummary:"]
    if descriptor.get("strategy_name"):
        lines.append(f"- Strategy: {descriptor['strategy_name']}")
    if reference.get("agent"):
        lines.append(f"- Agent: {reference['agent']}")
    if reference.get("model"):
        lines.append(f"- Model: {_model_display_name(descriptor)}")
    fields = [
        ("input_tokens_used", "Input tokens"),
        ("cached_input_tokens_used", "Cached input tokens"),
        ("output_tokens_used", "Output tokens"),
        ("iterations", "Iterations"),
    ]
    if include_coverage:
        fields.extend([
            ("metadata_entries", "Metadata entries"),
            ("test_only_metadata_entries", "Test-only metadata entries"),
            ("code_coverage_percent", "Library coverage percentage"),
            ("generated_loc", "Generated lines of code"),
            ("tested_library_loc", "Tested library lines of code"),
            ("previous_library_metadata_entries", "Previous library version metadata entries"),
            (
                "previous_library_test_only_metadata_entries",
                "Previous library version test-only metadata entries",
            ),
            (
                "previous_library_coverage_percent",
                "Previous library version coverage percentage",
            ),
        ])
    for key, label in fields:
        if key in metrics:
            lines.append(f"- {label}: {metrics[key]}")
    return "\n" + "\n".join(lines) + "\n"


def _render_chunk_summary(descriptor: dict[str, Any]) -> str:
    if not descriptor["modifiers"]["chunked_dynamic_access"]:
        return ""
    report = descriptor["render"].get("dynamic_access") or {}
    completed = len(report.get("completedClasses") or [])
    skipped = len(report.get("skippedClasses") or [])
    exhausted = len(report.get("exhaustedClasses") or [])
    failed = len(report.get("failedClasses") or [])
    return (
        "\n### Chunked Dynamic Access\n\n"
        f"- Chunk class threshold: {report.get('classThreshold') or 'unknown'}\n"
        f"- Current chunk class count: {report.get('currentChunkClassCount') or 'unknown'}\n"
        f"- Processed classes: completed={completed}, skipped={skipped}, "
        f"exhausted={exhausted}, failed={failed}\n"
    )


def _render_route_evidence(descriptor: dict[str, Any]) -> str:
    render = descriptor["render"]
    lines: list[str] = []
    before_entries = render.get("baseline_metadata_entries")
    after_entries = render.get("current_metadata_entries")
    if before_entries is not None or after_entries is not None:
        lines.extend([
            "\n### Metadata Comparison\n",
            f"- Metadata entries before: {before_entries if before_entries is not None else 'unknown'}",
            f"- Metadata entries after: {after_entries if after_entries is not None else 'unknown'}",
        ])
        before_test = render.get("baseline_test_only_entries")
        after_test = render.get("current_test_only_entries")
        if before_test is not None or after_test is not None:
            lines.extend([
                f"- Test-only metadata entries before: {before_test or 0}",
                f"- Test-only metadata entries after: {after_test or 0}",
            ])

    update_target = render.get("library_update_target")
    if isinstance(update_target, dict):
        lines.extend([
            "\n### Library Update Target\n",
            f"- Requested coordinate: `{update_target.get('requested_coordinate') or descriptor['library']['coordinates']}`",
            f"- Match type: `{update_target.get('match_type') or 'unknown'}`",
            f"- Matched metadata version: `{update_target.get('matched_metadata_version') or 'none'}`",
            f"- Matched test version: `{update_target.get('matched_test_version') or 'none'}`",
            f"- Resolved metadata version: `{update_target.get('resolved_metadata_version') or 'unknown'}`",
            f"- Resolved test version: `{update_target.get('resolved_test_version') or 'unknown'}`",
        ])

    stats = {"before": render.get("baseline_stats"), "after": render.get("library_stats")}
    if stats["before"] is not None or stats["after"] is not None:
        lines.append("\n### Library Statistics\n")
        lines.append("```json")
        lines.append(json.dumps(stats, indent=2, sort_keys=True, ensure_ascii=False)[:8000])
        lines.append("```")

    evidence = render.get("dynamic_access_evidence")
    if isinstance(evidence, dict):
        call_sites = evidence.get("covered_call_sites") or []
        metadata_rules = evidence.get("metadata_rules") or []
        if call_sites or metadata_rules:
            lines.append("\n### Dynamic-Access Evidence\n")
            for call_site in call_sites[:20]:
                lines.append(f"- Covered call site: {call_site}")
            for rule in metadata_rules[:20]:
                lines.append(f"- Metadata rule: {rule}")
    return "\n".join(lines) + ("\n" if lines else "")


def _render_native_image_summary(descriptor: dict[str, Any]) -> str:
    render = descriptor["render"]
    previous = descriptor["previous_library"]["coordinates"]
    current = descriptor["library"]["coordinates"]
    previous_entries = int(render.get("baseline_metadata_entries") or 0)
    current_entries = int(render.get("current_metadata_entries") or 0)
    previous_test_entries = int(render.get("baseline_test_only_entries") or 0)
    current_test_entries = int(render.get("current_test_only_entries") or 0)
    previous_coverage = (render.get("baseline_stats") or {}).get("coverage_percent", 0)
    current_coverage = (render.get("library_stats") or {}).get("coverage_percent", 0)
    body = (
        "\nSummary:\n"
        f"- Metadata entries (previous `{previous}`): {previous_entries}\n"
        f"- Metadata entries (new `{current}`): {current_entries}\n"
        f"- Test-only metadata entries (previous): {previous_test_entries}\n"
        f"- Test-only metadata entries (new): {current_test_entries}\n"
        f"- Library coverage (previous): {float(previous_coverage):.2f}%\n"
        f"- Library coverage (new): {float(current_coverage):.2f}%\n"
    )
    if _has_severe_metadata_drop(descriptor):
        retained = current_entries / previous_entries * 100 if previous_entries else 0
        body += (
            "\n### Human Intervention: Severe Metadata Drop\n\n"
            "Forge detected a severe drop in reachability metadata entries.\n\n"
            f"- Previous metadata entries (`{previous}`): {previous_entries}\n"
            f"- New metadata entries (`{current}`): {current_entries}\n"
            f"- Retained metadata entries: {retained:.2f}%\n"
        )
    return body


def _has_severe_metadata_drop(descriptor: dict[str, Any]) -> bool:
    if descriptor["template_type"] != "fixes-native-image-run-fail":
        return False
    render = descriptor["render"]
    previous_entries = int(render.get("baseline_metadata_entries") or 0)
    current_entries = int(render.get("current_metadata_entries") or 0)
    return previous_entries > 0 and current_entries < previous_entries * SEVERE_METADATA_DROP_RATIO


def _render_test_comparison(validated: ValidatedPublication | None) -> str:
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


def _bound_body(body: str) -> str:
    if len(body) <= MAX_BODY_CHARS:
        return body
    head_chars = MAX_BODY_CHARS - 2200
    return body[:head_chars].rstrip() + "\n\nGenerated detail was truncated.\n\n" + body[-2000:]

def _render_follow_ups(descriptor: dict[str, Any], numbers: dict[str, int]) -> str:
    sections: list[str] = []
    for follow_up in descriptor["follow_ups"]:
        follow_type = follow_up["type"]
        issue_number = numbers.get(follow_type)
        issue_lines = ""
        if issue_number is not None:
            issue_lines = f"\nRefs: #{issue_number}\nForge-Unblocks-Issue: #{issue_number}\n"
        if follow_type == "deferred_dynamic_access_coverage":
            sections.append(
                "\n### Deferred Dynamic-Access Exploration\n\n"
                f"Exploration was deferred for `{follow_up['coordinate']}` because "
                f"{follow_up['uncovered_class_count']} uncovered classes exceeded the "
                f"threshold of {follow_up['class_threshold']}.\n{issue_lines}"
            )
        else:
            sections.append(
                "\n### Tested-Version Alias Split\n\n"
                f"A successor update is required for `{follow_up['coordinate']}` starting at "
                f"tested version `{follow_up['tested_version']}`.\n{issue_lines}"
            )
    return "".join(sections)


def _render_intervention(descriptor: dict[str, Any]) -> str:
    intervention = descriptor.get("post_generation_intervention")
    if not intervention:
        return ""
    return (
        "\n### Post-Generation Intervention\n\n"
        f"- Stage: `{intervention['stage']}`\n"
        f"- Intervention file: `{intervention['intervention_file']}`\n\n"
        f"{intervention['analysis_markdown'].strip()}\n"
    )


def _render_local_ci(verification: dict[str, Any]) -> str:
    lines = [
        "\n### Local CI Verification\n",
        f"- Status: `{verification['status']}`",
        f"- Base commit: `{verification['base_commit']}`",
        f"- Final verified commit: `{verification.get('final_commit') or 'not recorded'}`",
        f"- Fixup attempts: {len(verification['fixups'])}",
    ]
    commands = verification["commands"]
    if commands:
        lines.append("- Commands:")
        for command in commands:
            rendered_command = " ".join(str(value) for value in command["command"])
            lines.append(
                f"  - `{command['gate']}`: `{rendered_command}` "
                f"(exit {command['returncode']})"
            )
    if verification["repo_fix_paths"]:
        lines.append("- Repository-level fix paths requiring human review:")
        for repo_path in verification["repo_fix_paths"][:20]:
            lines.append(f"  - `{repo_path}`")
    return "\n".join(lines) + "\n"

def ensure_follow_ups(descriptor: dict[str, Any], repository: str) -> dict[str, int]:
    """Create or recover typed follow-up issues before PR rendering."""
    numbers: dict[str, int] = {}
    for follow_up in descriptor["follow_ups"]:
        follow_type = follow_up["type"]
        marker = f"Forge-Follow-Up: {descriptor['publication_id']}:{follow_type}"
        query = f'repo:{repository} in:body "{marker}"'
        result = gh_json("api", "search/issues", "--method", "GET", "-f", f"q={query}")
        matches = [
            item for item in (result.get("items") or [])
            if isinstance(item, dict)
            and not item.get("pull_request")
            and marker in str(item.get("body") or "").splitlines()
        ]
        if len(matches) > 1:
            raise RuntimeError(f"Ambiguous follow-up issue for {marker}")
        if matches:
            if matches[0].get("state") != "open":
                raise RuntimeError(f"Matching follow-up issue for {marker} is not open")
            issue_number = int(matches[0]["number"])
        else:
            title, body = _follow_up_issue_text(descriptor, follow_up, marker)
            created = gh_json(
                "api", f"repos/{repository}/issues", "--method", "POST",
                "-f", f"title={title}", "-f", f"body={body}",
                "-f", "labels[]=library-update-request",
            )
            issue_number = int(created["number"])
        _ensure_project_status(repository, issue_number, "In Progress")
        numbers[follow_type] = issue_number
    return numbers


def _follow_up_issue_text(
        descriptor: dict[str, Any],
        follow_up: dict[str, Any],
        marker: str,
) -> tuple[str, str]:
    coordinate = follow_up["coordinate"]
    if follow_up["type"] == "deferred_dynamic_access_coverage":
        title = f"Improve coverage for {coordinate}"
        body = (
            f"This issue was opened by Forge while resolving #{descriptor['issue_number']} because "
            f"dynamic-access generation found {follow_up['uncovered_class_count']} classes to cover for "
            f"`{coordinate}`, above the configured threshold of {follow_up['class_threshold']}.\n\n{marker}\n"
        )
    else:
        title = f"Update existing library: {coordinate}"
        body = (
            f"This issue tracks the tested-version successor split for `{coordinate}` while resolving "
            f"#{descriptor['issue_number']}. {follow_up.get('reason') or ''}\n\n{marker}\n"
        )
    return title, body


def _ensure_project_status(repository: str, issue_number: int, status: str) -> None:
    owner, repo_name = repository.split("/", 1)
    project_query = """
    query($owner: String!, $number: Int!) {
      organization(login: $owner) {
        projectV2(number: $number) {
          id
          fields(first: 50) {
            nodes { ... on ProjectV2SingleSelectField { id name options { id name } } }
          }
        }
      }
    }
    """
    data = gh_json(
        "api", "graphql", "-f", f"query={project_query}",
        "-F", f"owner={owner}", "-F", f"number={PROJECT_NUMBER}",
    )
    project = data["data"]["organization"]["projectV2"]
    status_field = next(
        field for field in project["fields"]["nodes"]
        if field and field.get("name") == "Status"
    )
    option_id = next(
        option["id"] for option in status_field["options"] if option["name"] == status
    )

    item_query = """
    query($owner: String!, $repo: String!, $number: Int!) {
      repository(owner: $owner, name: $repo) {
        issue(number: $number) {
          id
          projectItems(first: 50) { nodes { id project { id } } }
        }
      }
    }
    """
    item_data = gh_json(
        "api", "graphql", "-f", f"query={item_query}",
        "-F", f"owner={owner}", "-F", f"repo={repo_name}", "-F", f"number={issue_number}",
    )
    issue = item_data["data"]["repository"]["issue"]
    matching_items = [
        node
        for node in issue["projectItems"]["nodes"]
        if node["project"]["id"] == project["id"]
    ]
    if len(matching_items) > 1:
        raise RuntimeError(f"Issue #{issue_number} has duplicate project items")
    if matching_items:
        item_id = matching_items[0]["id"]
    else:
        add_mutation = """
        mutation($project: ID!, $content: ID!) {
          addProjectV2ItemById(input: {projectId: $project, contentId: $content}) {
            item { id }
          }
        }
        """
        added = gh_json(
            "api", "graphql", "-f", f"query={add_mutation}",
            "-f", f"project={project['id']}", "-f", f"content={issue['id']}",
        )
        item_id = added["data"]["addProjectV2ItemById"]["item"]["id"]

    update_mutation = """
    mutation($project: ID!, $item: ID!, $field: ID!, $option: String!) {
      updateProjectV2ItemFieldValue(input: {
        projectId: $project, itemId: $item, fieldId: $field,
        value: {singleSelectOptionId: $option}
      }) { projectV2Item { id } }
    }
    """
    gh_json(
        "api", "graphql", "-f", f"query={update_mutation}",
        "-f", f"project={project['id']}", "-f", f"item={item_id}",
        "-f", f"field={status_field['id']}", "-f", f"option={option_id}",
    )

def publish(
        validated: ValidatedPublication,
        mode: str,
        reviewers: str,
) -> tuple[str, str, str | None]:
    descriptor = validated.descriptor
    if mode not in {"shadow", "live"}:
        raise ValueError("FORGE_PR_PUBLISH_MODE must be 'shadow' or 'live'")
    follow_up_numbers = ensure_follow_ups(descriptor, REPOSITORY) if mode == "live" else {}
    title, body = render_publication(descriptor, follow_up_numbers, validated)
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
    parser.add_argument("--authorized-pushers", required=True)
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
            authorized_pushers=args.authorized_pushers,
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
