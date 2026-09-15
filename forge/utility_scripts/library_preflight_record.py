# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Preflight record shapes: input evidence, response parsing, and validation.

Turns the preflight agent's JSON response into the persisted record, degrading
to a no-action advisory record on any malformation
(§FS-unsupportable-version-diagnosis). The preflight run itself lives in
`library_preparation_preflight.py`.
"""

import json
import re
from typing import Any

from utility_scripts.metadata_index import coordinate_parts as metadata_coordinate_parts

LIBRARY_PREFLIGHT_MAX_DETERMINISTIC_SETUP = 8
# Requested actions that would take an agent outside the harness — shelling out,
# fetching from the network, mutating the machine. Only the free-text advisory
# fields are scanned; deterministic setup is validated by shape. Subject-matter
# vocabulary is deliberately absent: it names what a library is about, not what
# the guidance asks for (§AR-forge-orchestration.1.2).
LIBRARY_PREFLIGHT_UNSAFE_TERMS = (
    "sudo",
    "curl ",
    "wget ",
    "git clone",
    "rm -rf",
    "docker run",
)
# Only issues carrying failure evidence are asked the unsupportable-version
# question (§FS-unsupportable-version-diagnosis).
LIBRARY_PREFLIGHT_SKIP_VERDICT_LABELS = (
    "fails-javac-compile",
    "fails-java-run",
    "fails-native-image-run",
)
LIBRARY_PREFLIGHT_MAX_SKIPPED_VERSIONS = 12
LIBRARY_PREFLIGHT_DEPENDENCY_SCOPES = (
    "testImplementation",
    "testRuntimeOnly",
    "implementation",
    "runtimeOnly",
)
_DEPENDENCY_COORDINATE_RE = re.compile(r"^[\w.\-]+:[\w.\-]+:[\w.\-]+$")
_DOCKER_IMAGE_RE = re.compile(r"^[\w][\w./\-]*(:[\w][\w.\-]*)?$")
_DOCKER_SLUG_RE = re.compile(r"^[a-z0-9][a-z0-9_\-]*$")


def _truncate_preflight_text(value: str | None, max_chars: int) -> str:
    """Return bounded text for the preflight evidence bundle."""
    if not value:
        return ""
    stripped = value.strip()
    if len(stripped) <= max_chars:
        return stripped
    return stripped[:max_chars] + "\n...[truncated]"


def _target_library_for_preflight(claimed_issue: Any) -> str:
    """Return the library coordinate the workflow will edit or verify."""
    if claimed_issue.label in {"library-new-request", "library-update-request"}:
        return claimed_issue.issue_coordinates
    if claimed_issue.current_coordinates and claimed_issue.new_version:
        group, artifact, _version = metadata_coordinate_parts(claimed_issue.current_coordinates)
        return f"{group}:{artifact}:{claimed_issue.new_version}"
    return claimed_issue.issue_coordinates


def _preflight_evidence(name: str, summary: str) -> dict[str, str]:
    """Build one schema-compatible preflight evidence summary."""
    return {"name": name, "summary": _truncate_preflight_text(summary, 1000) or "not available"}



def _library_preflight_input_evidence(input_bundle: dict[str, Any]) -> list[dict[str, str]]:
    """Summarize the preflight bundle for durable metrics."""
    issue = input_bundle.get("issue") if isinstance(input_bundle.get("issue"), dict) else {}
    issue_body = str(issue.get("body") or "")
    issue_body_error = str(issue.get("body_error") or "")
    existing_tests = input_bundle.get("existing_tests") or {}
    return [
        _preflight_evidence(
            "issue",
            (
                f"#{issue.get('number')} {issue.get('title')}; "
                f"label={issue.get('label')}; body_chars={len(issue_body)}"
                f"{'; body_error=' + issue_body_error if issue_body_error else ''}"
            ),
        ),
        _preflight_evidence(
            "existing_tests",
            f"{sum(len(files) for files in existing_tests.values())} existing test file path(s) included",
        ),
    ]


def _base_library_preflight_record(
        claimed_issue: Any,
        input_bundle: dict[str, Any],
) -> dict[str, Any]:
    """Return fields common to completed and degraded preflight records."""
    record: dict[str, Any] = {
        "status": "degraded",
        "action": "no_action",
        "issue_number": int(claimed_issue.issue["number"]),
        "issue_label": claimed_issue.label,
        "library": str(input_bundle.get("library") or _target_library_for_preflight(claimed_issue)),
        "summary": "",
        "deterministic_setup": [],
        "agent_guidance": "",
        "risks": [],
        "applied_setup": [],
        "input_evidence": _library_preflight_input_evidence(input_bundle),
    }
    if claimed_issue.current_coordinates:
        record["current_library"] = claimed_issue.current_coordinates
    if claimed_issue.new_version:
        record["new_version"] = claimed_issue.new_version
    return record


def _degraded_library_preflight_record(
        claimed_issue: Any,
        input_bundle: dict[str, Any],
        failure_reason: str,
        model_name: str | None = None,
) -> dict[str, Any]:
    """Record an unavailable or unusable preflight as no-action advisory output."""
    record = _base_library_preflight_record(claimed_issue, input_bundle)
    record["summary"] = "Library preparation preflight did not produce usable advisory setup."
    record["failure_reason"] = failure_reason
    if model_name:
        record["model"] = model_name
    return record


def _extract_preflight_json_response(response_text: str) -> dict[str, Any]:
    """Extract a JSON object from an LLM response."""
    try:
        loaded = json.loads(response_text)
        if isinstance(loaded, dict):
            return loaded
    except json.JSONDecodeError:
        pass

    start = response_text.find("{")
    end = response_text.rfind("}")
    if start < 0 or end <= start:
        raise ValueError("response did not contain a JSON object")
    loaded = json.loads(response_text[start:end + 1])
    if not isinstance(loaded, dict):
        raise ValueError("response JSON was not an object")
    return loaded


def _preflight_string_list(value: Any, limit: int = 8) -> list[str]:
    """Normalize a JSON value into a bounded list of non-empty strings."""
    if isinstance(value, str):
        values = [value]
    elif isinstance(value, list):
        values = value
    else:
        values = []
    normalized = []
    for item in values:
        if not isinstance(item, str) or not item.strip():
            continue
        normalized.append(_truncate_preflight_text(item, 600))
        if len(normalized) >= limit:
            break
    return normalized


def _unsafe_terms_in(*values: Any) -> list[str]:
    """Return the unsafe action terms the advisory text requests, for the error message."""
    combined = "\n".join(str(value).lower() for value in values if value is not None)
    return [term.strip() for term in LIBRARY_PREFLIGHT_UNSAFE_TERMS if term in combined]


def _parse_deterministic_setup_entry(entry: Any) -> dict[str, str] | None:
    """Validate one deterministic-setup entry by shape, or drop it (return None)."""
    if not isinstance(entry, dict):
        return None
    kind = str(entry.get("kind") or "").strip()
    reason = _truncate_preflight_text(str(entry.get("reason") or ""), 300)
    if kind == "dependency":
        coordinate = str(entry.get("coordinate") or "").strip()
        if not _DEPENDENCY_COORDINATE_RE.match(coordinate):
            return None
        scope = str(entry.get("scope") or "testImplementation").strip()
        if scope not in LIBRARY_PREFLIGHT_DEPENDENCY_SCOPES:
            scope = "testImplementation"
        return {"kind": kind, "coordinate": coordinate, "scope": scope, "reason": reason}
    if kind == "docker_image":
        image = str(entry.get("image") or "").strip()
        slug = str(entry.get("slug") or "").strip().lower()
        if not _DOCKER_IMAGE_RE.match(image) or not _DOCKER_SLUG_RE.match(slug):
            return None
        return {"kind": kind, "image": image, "slug": slug, "reason": reason}
    return None


def _parse_deterministic_setup(value: Any) -> list[dict[str, str]]:
    """Normalize the response's deterministic setup into validated, bounded entries."""
    if not isinstance(value, list):
        return []
    parsed: list[dict[str, str]] = []
    for entry in value:
        validated = _parse_deterministic_setup_entry(entry)
        if validated is None:
            continue
        parsed.append(validated)
        if len(parsed) >= LIBRARY_PREFLIGHT_MAX_DETERMINISTIC_SETUP:
            break
    return parsed


def _parse_skipped_versions(value: Any, target_version: str) -> list[dict[str, str]]:
    """Validate a skip verdict's version records; the burden of proof is on skipping.

    Any malformation raises, degrading the record so the run falls through to
    normal generation (§FS-unsupportable-version-diagnosis).
    """
    if not isinstance(value, list) or not value:
        raise ValueError("skip_unsupported response did not include skipped_versions")
    if len(value) > LIBRARY_PREFLIGHT_MAX_SKIPPED_VERSIONS:
        raise ValueError("skip_unsupported response listed too many skipped versions")
    parsed: list[dict[str, str]] = []
    seen_versions: set[str] = set()
    for record in value:
        if not isinstance(record, dict):
            raise ValueError("skipped_versions entries must be objects")
        version = record.get("version")
        reason = record.get("reason")
        if not isinstance(version, str) or not version.strip():
            raise ValueError("skipped_versions entries require a version")
        if not isinstance(reason, str) or not reason.strip():
            raise ValueError("skipped_versions entries require a reason")
        if version.strip() in seen_versions:
            continue
        seen_versions.add(version.strip())
        parsed.append({
            "version": version.strip(),
            "reason": _truncate_preflight_text(reason, 500),
        })
    if target_version not in seen_versions:
        raise ValueError("skipped_versions must include the reported version")
    return parsed


def _completed_library_preflight_record(
        claimed_issue: Any,
        input_bundle: dict[str, Any],
        response_payload: dict[str, Any],
        model_name: str,
        result: Any | None,
) -> dict[str, Any]:
    """Normalize a valid preflight response into the persisted metrics shape."""
    action = str(response_payload.get("action") or "no_action").strip()
    if action not in {"no_action", "advisory_preparation", "skip_unsupported"}:
        raise ValueError(f"unsupported preflight action: {action}")
    summary = _truncate_preflight_text(str(response_payload.get("summary") or ""), 1000)
    deterministic_setup = _parse_deterministic_setup(response_payload.get("deterministic_setup"))
    agent_guidance = _truncate_preflight_text(str(response_payload.get("agent_guidance") or ""), 2000)
    risks = _preflight_string_list(response_payload.get("risks"))
    if action == "advisory_preparation" and not (deterministic_setup or agent_guidance):
        raise ValueError("advisory_preparation response did not include deterministic setup or guidance")
    skipped_versions: list[dict[str, str]] = []
    if action == "skip_unsupported":
        # The verdict is only asked for, and only accepted from, issues that
        # carry failure evidence (§FS-unsupportable-version-diagnosis).
        if claimed_issue.label not in LIBRARY_PREFLIGHT_SKIP_VERDICT_LABELS:
            raise ValueError(f"skip_unsupported verdict is not allowed for label {claimed_issue.label}")
        target_version = str(input_bundle.get("library") or "").split(":")[-1]
        skipped_versions = _parse_skipped_versions(response_payload.get("skipped_versions"), target_version)
    # Deterministic entries are validated structurally above; only the free-text
    # fields can carry prose, so only those are scanned — and only for
    # requested actions, never subject matter (§AR-forge-orchestration.1.2). Skip
    # reasons are published verbatim in index.json and the pull request.
    skip_reasons = [entry["reason"] for entry in skipped_versions]
    unsafe_terms = _unsafe_terms_in(summary, agent_guidance, risks, *skip_reasons)
    if unsafe_terms:
        raise ValueError(
            f"preflight response requested unsafe preparation behavior: {', '.join(unsafe_terms)}"
        )

    record = _base_library_preflight_record(claimed_issue, input_bundle)
    record["status"] = "completed"
    record["action"] = action
    record["summary"] = summary
    record["deterministic_setup"] = deterministic_setup
    record["agent_guidance"] = agent_guidance
    record["risks"] = risks
    if skipped_versions:
        record["skipped_versions"] = skipped_versions
    record["model"] = model_name
    record["input_tokens_used"] = int(getattr(result, "input_tokens", 0) or 0)
    record["output_tokens_used"] = int(getattr(result, "output_tokens", 0) or 0)
    return record

