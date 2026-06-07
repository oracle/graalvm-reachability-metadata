# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Shared helpers for library-specific preparation preflight records."""

import json
import os
import re
from typing import Any, Callable

from utility_scripts.metadata_index import (
    coordinate_parts as metadata_coordinate_parts,
    resolve_test_dir,
)
from utility_scripts.stage_logger import log_stage
from utility_scripts.strategy_loader import load_strategy_by_name

LIBRARY_PREPARATION_PREFLIGHT_FILENAME = ".library_preparation_preflight.json"
NO_LIBRARY_PREPARATION_PREFLIGHT_CONTEXT = (
    "Library preparation preflight did not request additional setup."
)
DEFAULT_LIBRARY_PREFLIGHT_STRATEGY_ENV = "FORGE_LIBRARY_PREFLIGHT_STRATEGY_NAME"
DEFAULT_LIBRARY_PREFLIGHT_MODEL_NAME = "oca/gpt-5.4"
LIBRARY_PREFLIGHT_MAX_ISSUE_BODY_CHARS = 8000
LIBRARY_PREFLIGHT_MAX_TEST_FILES = 40
LIBRARY_PREFLIGHT_MAX_DETERMINISTIC_SETUP = 8
# Only the free-text advisory guidance is scanned for these; the deterministic
# setup entries are validated structurally and cannot smuggle prose commands.
LIBRARY_PREFLIGHT_UNSAFE_TERMS = (
    "sudo",
    "curl ",
    "wget ",
    "git clone",
    "rm -rf",
    "docker run",
    "credential",
    "secret",
    "token",
)
# Deterministic setup the driver applies itself, idempotently, as source edits
# (§ORCH-forge-orchestration-spec.1.1). Anything else stays advisory guidance.
LIBRARY_PREFLIGHT_DETERMINISTIC_KINDS = ("dependency", "docker_image")
LIBRARY_PREFLIGHT_DEPENDENCY_SCOPES = (
    "testImplementation",
    "testRuntimeOnly",
    "implementation",
    "runtimeOnly",
)
_DEPENDENCY_COORDINATE_RE = re.compile(r"^[\w.\-]+:[\w.\-]+:[\w.\-]+$")
_DOCKER_IMAGE_RE = re.compile(r"^[\w][\w./\-]*(:[\w][\w.\-]*)?$")
_DOCKER_SLUG_RE = re.compile(r"^[a-z0-9][a-z0-9_\-]*$")
ALLOWED_DOCKER_IMAGES_RELDIR = os.path.join(
    "tests", "tck-build-logic", "src", "main", "resources", "allowed-docker-images"
)


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


def _list_all_files(root_dir: str) -> list[str]:
    """Return all regular files under a directory in stable order."""
    result: list[str] = []
    for dirpath, _dirnames, filenames in os.walk(root_dir):
        for file_name in sorted(filenames):
            result.append(os.path.join(dirpath, file_name))
    return result


def _list_preflight_test_files(repo_path: str, coordinate: str) -> list[str]:
    """Return a bounded list of existing test files for a supported coordinate."""
    try:
        group, artifact, version = metadata_coordinate_parts(coordinate)
        if version is None:
            return []
        test_dir = resolve_test_dir(repo_path, group, artifact, version)
    except Exception:
        return []
    if not os.path.isdir(test_dir):
        return []
    files = []
    for file_path in _list_all_files(test_dir):
        if len(files) >= LIBRARY_PREFLIGHT_MAX_TEST_FILES:
            break
        files.append(os.path.relpath(file_path, repo_path))
    return files


def build_library_preflight_input_bundle(
        claimed_issue: Any,
        issue_body_provider: Callable[[int], str],
) -> dict[str, Any]:
    """Collect the small starting context the preflight agent researches from."""
    issue_number = int(claimed_issue.issue["number"])
    target_library = _target_library_for_preflight(claimed_issue)
    issue_body_error = ""
    try:
        issue_body = _truncate_preflight_text(
            issue_body_provider(issue_number),
            LIBRARY_PREFLIGHT_MAX_ISSUE_BODY_CHARS,
        )
    except Exception as exc:
        issue_body = ""
        issue_body_error = f"{type(exc).__name__}: {exc}"
    existing_test_coordinates = [
        coordinate for coordinate in (
            claimed_issue.current_coordinates,
            claimed_issue.issue_coordinates,
            target_library,
        )
        if coordinate
    ]
    existing_tests: dict[str, list[str]] = {}
    for coordinate in dict.fromkeys(existing_test_coordinates):
        files = _list_preflight_test_files(claimed_issue.worktree_path, coordinate)
        if files:
            existing_tests[coordinate] = files

    return {
        "issue": {
            "number": issue_number,
            "title": claimed_issue.issue.get("title") or "",
            "label": claimed_issue.label,
            "body": issue_body,
            "body_error": issue_body_error,
        },
        "library": target_library,
        "current_library": claimed_issue.current_coordinates,
        "new_version": claimed_issue.new_version,
        "existing_tests": existing_tests,
    }


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
        prompt_path: str | None = None,
        raw_response_path: str | None = None,
        session_log_path: str | None = None,
) -> dict[str, Any]:
    """Record an unavailable or unusable preflight as no-action advisory output."""
    record = _base_library_preflight_record(claimed_issue, input_bundle)
    record["summary"] = "Library preparation preflight did not produce usable advisory setup."
    record["failure_reason"] = failure_reason
    if model_name:
        record["model"] = model_name
    if prompt_path:
        record["prompt_path"] = prompt_path
    if raw_response_path:
        record["raw_response_path"] = raw_response_path
    if session_log_path:
        record["session_log_path"] = session_log_path
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


def _preflight_contains_unsafe_text(*values: Any) -> bool:
    """Return True when advisory text asks for unsafe preparation behavior."""
    combined = "\n".join(str(value).lower() for value in values if value is not None)
    return any(term in combined for term in LIBRARY_PREFLIGHT_UNSAFE_TERMS)


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


def _completed_library_preflight_record(
        claimed_issue: Any,
        input_bundle: dict[str, Any],
        response_payload: dict[str, Any],
        model_name: str,
        agent: Any | None,
        prompt_path: str | None,
        raw_response_path: str | None,
        session_log_path: str | None,
) -> dict[str, Any]:
    """Normalize a valid preflight response into the persisted metrics shape."""
    action = str(response_payload.get("action") or "no_action").strip()
    if action not in {"no_action", "advisory_preparation"}:
        raise ValueError(f"unsupported preflight action: {action}")
    summary = _truncate_preflight_text(str(response_payload.get("summary") or ""), 1000)
    deterministic_setup = _parse_deterministic_setup(response_payload.get("deterministic_setup"))
    agent_guidance = _truncate_preflight_text(str(response_payload.get("agent_guidance") or ""), 2000)
    risks = _preflight_string_list(response_payload.get("risks"))
    if action == "advisory_preparation" and not (deterministic_setup or agent_guidance):
        raise ValueError("advisory_preparation response did not include deterministic setup or guidance")
    # Deterministic entries are validated structurally above; only the free-text
    # advisory fields can carry prose, so only those are scanned for unsafe terms.
    if _preflight_contains_unsafe_text(summary, agent_guidance, risks):
        raise ValueError("preflight response requested unsafe preparation behavior")

    record = _base_library_preflight_record(claimed_issue, input_bundle)
    record["status"] = "completed"
    record["action"] = action
    record["summary"] = summary
    record["deterministic_setup"] = deterministic_setup
    record["agent_guidance"] = agent_guidance
    record["risks"] = risks
    record["model"] = model_name
    record["input_tokens_used"] = int(getattr(agent, "total_tokens_sent", 0) or 0)
    record["output_tokens_used"] = int(getattr(agent, "total_tokens_received", 0) or 0)
    if prompt_path:
        record["prompt_path"] = prompt_path
    if raw_response_path:
        record["raw_response_path"] = raw_response_path
    if session_log_path:
        record["session_log_path"] = session_log_path
    return record


def _library_preflight_prompt(input_bundle: dict[str, Any]) -> str:
    """Build the LLM prompt instructing the agent to research preparation needs."""
    return (
        "You are preparing a Forge workflow for a library issue. Decide whether this "
        "library needs additional setup beyond the normal generated test scaffold, "
        "such as optional Maven dependencies, a Docker-backed service and its allowed "
        "image, or library initialization the test must perform before meaningful "
        "coverage is reachable.\n\n"
        "Research the library yourself to make this decision. Investigate the resolved "
        "artifact and its dependencies, how the library is normally used, and its "
        "documentation. The JSON context below is only a starting point, not the limit "
        "of what you may consider. Do not modify the repository or apply any setup "
        "yourself — return only the decision. The driver applies deterministic setup, "
        "and local Gradle and Native Image verification remain authoritative.\n\n"
        "Split any needed setup into two kinds. `deterministic_setup` is a typed list of "
        "one-time, idempotent edits the driver applies for you; supported kinds:\n"
        '  - {"kind": "dependency", "coordinate": "group:artifact:version", '
        '"scope": "testImplementation", "reason": "..."}\n'
        '  - {"kind": "docker_image", "image": "name:tag", "slug": "short-slug", "reason": "..."}\n'
        "`agent_guidance` is concise reasoning the workflow agent must apply inside the generated "
        "test code (for example, exercise a driver against a stood-up service, or set a system "
        "property before a factory lookup). Do not restate deterministic edits as guidance.\n\n"
        "Return a single JSON object with this shape:\n"
        "{\n"
        '  "action": "no_action" | "advisory_preparation",\n'
        '  "summary": "short reason",\n'
        '  "deterministic_setup": [ ... typed entries as above, if any ... ],\n'
        '  "agent_guidance": "concise guidance for the workflow agent, if any",\n'
        '  "risks": ["risk notes, if any"]\n'
        "}\n\n"
        "Choose no_action unless your research finds concrete evidence that extra setup is needed.\n\n"
        "Context (starting point):\n"
        f"{json.dumps(input_bundle, indent=2, ensure_ascii=False)}"
    )


def _relative_or_absolute_path(path: str | None, root: str) -> str | None:
    """Return a stable metrics path, relative when it is under the metrics root."""
    if not path:
        return None
    try:
        absolute_path = os.path.abspath(path)
        absolute_root = os.path.abspath(root)
        if os.path.commonpath([absolute_path, absolute_root]) == absolute_root:
            return os.path.relpath(absolute_path, absolute_root)
    except ValueError:
        pass
    return path


def _write_text_artifact(root: str, file_name: str, content: str) -> str:
    """Write a preflight text artifact and return its metrics-root-relative path."""
    os.makedirs(root, exist_ok=True)
    path = os.path.join(root, file_name)
    with open(path, "w", encoding="utf-8") as artifact_file:
        artifact_file.write(content)
    return os.path.relpath(path, root)


def _fixture_response_text(fixture_response: Any) -> str:
    """Serialize a configured fixture response like an agent response body."""
    if isinstance(fixture_response, str):
        return fixture_response
    return json.dumps(fixture_response, indent=2, ensure_ascii=False)


def _preflight_run_mode(fixture_response: Any | None) -> str:
    """Describe how the preflight decision is being produced, for logging."""
    if fixture_response is False:
        return "live"
    if fixture_response is None:
        return "fixture (no response configured)"
    return "fixture (configured response)"


def _write_and_log_preflight(claimed_issue: Any, record: dict[str, Any]) -> str:
    """Persist the record and log a one-line outcome, covering every decision path."""
    detail = f"status={record.get('status')} action={record.get('action')}"
    setup_count = len(record.get("deterministic_setup") or [])
    if setup_count:
        detail += f" deterministic_setup={setup_count}"
    failure_reason = record.get("failure_reason")
    if failure_reason:
        detail += f" reason={failure_reason}"
    log_stage(
        "library-preflight",
        f"Preflight decision for issue #{record.get('issue_number')}: {detail}",
    )
    return write_library_preparation_preflight(claimed_issue.scratch_metrics_repo_path, record)


def run_library_preparation_preflight(
        claimed_issue: Any,
        strategy_name: str | None,
        issue_body_provider: Callable[[int], str],
        init_agent: Callable[..., Any],
        default_strategy_name: str,
        default_model_name: str = DEFAULT_LIBRARY_PREFLIGHT_MODEL_NAME,
        fixture_response: Any | None = None,
) -> str:
    """Run and persist the library-specific preparation preflight before workflow dispatch."""
    selected_strategy_name = (
        os.environ.get(DEFAULT_LIBRARY_PREFLIGHT_STRATEGY_ENV)
        or strategy_name
        or default_strategy_name
    )
    input_bundle = build_library_preflight_input_bundle(
        claimed_issue,
        issue_body_provider,
    )
    log_stage(
        "library-preflight",
        (
            f"Running preflight for issue #{claimed_issue.issue['number']} "
            f"({_preflight_run_mode(fixture_response)}) "
            f"library={input_bundle.get('library')} strategy={selected_strategy_name}"
        ),
    )
    prompt = _library_preflight_prompt(input_bundle)
    prompt_path = _write_text_artifact(
        claimed_issue.scratch_metrics_repo_path,
        "library-preflight-prompt.txt",
        prompt,
    )
    raw_response_path: str | None = None
    session_log_path: str | None = None
    model_name = default_model_name

    if fixture_response is None:
        record = _degraded_library_preflight_record(
            claimed_issue,
            input_bundle,
            "fixture-testing mode did not configure a library preparation preflight response",
            model_name="fixture-no-response",
            prompt_path=prompt_path,
        )
        return _write_and_log_preflight(claimed_issue, record)

    if fixture_response is not False:
        try:
            response_text = _fixture_response_text(fixture_response)
            raw_response_path = _write_text_artifact(
                claimed_issue.scratch_metrics_repo_path,
                "library-preflight-response.txt",
                response_text,
            )
            response_payload = _extract_preflight_json_response(response_text)
            record = _completed_library_preflight_record(
                claimed_issue,
                input_bundle,
                response_payload,
                "fixture-configured-response",
                None,
                prompt_path,
                raw_response_path,
                None,
            )
        except Exception as exc:
            record = _degraded_library_preflight_record(
                claimed_issue,
                input_bundle,
                f"{type(exc).__name__}: {exc}",
                model_name="fixture-configured-response",
                prompt_path=prompt_path,
                raw_response_path=raw_response_path,
            )
        return _write_and_log_preflight(claimed_issue, record)

    strategy = load_strategy_by_name(selected_strategy_name)
    if strategy is None:
        record = _degraded_library_preflight_record(
            claimed_issue,
            input_bundle,
            f"preflight strategy not found: {selected_strategy_name}",
            prompt_path=prompt_path,
        )
        return _write_and_log_preflight(claimed_issue, record)

    model_name = str(strategy.get("model") or default_model_name)
    agent: Any | None = None
    try:
        agent = init_agent(
            strategy=strategy,
            working_dir=claimed_issue.worktree_path,
            editable_files=[],
            read_only_files=[],
            library=str(input_bundle.get("library") or ""),
            task_type="library-preparation-preflight",
            verbose=False,
            model_name=model_name,
        )
        response_text = agent.send_prompt(prompt)
        raw_response_path = _write_text_artifact(
            claimed_issue.scratch_metrics_repo_path,
            "library-preflight-response.txt",
            response_text,
        )
        session_log_path = _relative_or_absolute_path(
            getattr(agent, "_session_log_path", None),
            claimed_issue.scratch_metrics_repo_path,
        )
        response_payload = _extract_preflight_json_response(response_text)
        record = _completed_library_preflight_record(
            claimed_issue,
            input_bundle,
            response_payload,
            model_name,
            agent,
            prompt_path,
            raw_response_path,
            session_log_path,
        )
    except Exception as exc:
        if session_log_path is None and agent is not None:
            session_log_path = _relative_or_absolute_path(
                getattr(agent, "_session_log_path", None),
                claimed_issue.scratch_metrics_repo_path,
            )
        record = _degraded_library_preflight_record(
            claimed_issue,
            input_bundle,
            f"{type(exc).__name__}: {exc}",
            model_name=model_name,
            prompt_path=prompt_path,
            raw_response_path=raw_response_path,
            session_log_path=session_log_path,
        )
    return _write_and_log_preflight(claimed_issue, record)


def write_library_preparation_preflight(metrics_repo_root: str, preflight: dict[str, Any]) -> str:
    """Persist the dispatcher's library preparation preflight record."""
    os.makedirs(metrics_repo_root, exist_ok=True)
    preflight_path = os.path.join(metrics_repo_root, LIBRARY_PREPARATION_PREFLIGHT_FILENAME)
    with open(preflight_path, "w", encoding="utf-8") as preflight_file:
        json.dump(preflight, preflight_file, indent=2, ensure_ascii=False)
        preflight_file.write("\n")
    return preflight_path


def load_library_preparation_preflight(preflight_path: str | None) -> dict[str, Any] | None:
    """Load a preflight record passed by the dispatcher."""
    if not preflight_path:
        return None
    if not os.path.isfile(preflight_path):
        return None
    with open(preflight_path, "r", encoding="utf-8") as preflight_file:
        loaded = json.load(preflight_file)
    return loaded if isinstance(loaded, dict) else None


def _insert_into_dependencies_block(text: str, new_line: str) -> str | None:
    """Insert a dependency line before the close of the `dependencies { }` block."""
    lines = text.splitlines(keepends=True)
    start = None
    for index, line in enumerate(lines):
        if re.match(r"\s*dependencies\s*\{", line):
            start = index
            break
    if start is None:
        return None
    for index in range(start + 1, len(lines)):
        if lines[index].strip() == "}":
            lines.insert(index, new_line)
            return "".join(lines)
    return None


def _apply_dependency_setup(
        reachability_repo_path: str,
        library_coordinate: str,
        entry: dict[str, str],
) -> dict[str, str]:
    """Idempotently add a dependency to the target library's test build.gradle."""
    coordinate = entry["coordinate"]
    scope = entry.get("scope", "testImplementation")
    result: dict[str, str] = {"kind": "dependency", "coordinate": coordinate}
    try:
        group, artifact, version = metadata_coordinate_parts(library_coordinate)
    except Exception:
        return {**result, "result": "skipped", "reason": "unresolved_library_coordinate"}
    if version is None:
        return {**result, "result": "skipped", "reason": "missing_library_version"}
    try:
        version_dir = resolve_test_dir(reachability_repo_path, group, artifact, version)
    except Exception as exc:
        return {**result, "result": "skipped", "reason": f"unresolved_test_dir:{type(exc).__name__}"}
    build_gradle = os.path.join(version_dir, "build.gradle")
    if not os.path.isfile(build_gradle):
        # New-library scaffold does not exist yet; falls back to advisory guidance.
        return {**result, "result": "skipped", "reason": "build_gradle_absent"}
    with open(build_gradle, "r", encoding="utf-8") as gradle_file:
        text = gradle_file.read()
    dep_group, dep_artifact, _dep_version = metadata_coordinate_parts(coordinate)
    if re.search(rf"""['"]{re.escape(dep_group)}:{re.escape(dep_artifact)}[:'"]""", text):
        return {**result, "result": "already_present", "target": os.path.relpath(build_gradle, reachability_repo_path)}
    updated = _insert_into_dependencies_block(text, f'    {scope} "{coordinate}"\n')
    if updated is None:
        return {**result, "result": "skipped", "reason": "no_dependencies_block"}
    with open(build_gradle, "w", encoding="utf-8") as gradle_file:
        gradle_file.write(updated)
    return {**result, "result": "applied", "target": os.path.relpath(build_gradle, reachability_repo_path)}


def _apply_docker_image_setup(reachability_repo_path: str, entry: dict[str, str]) -> dict[str, str]:
    """Idempotently add a Dockerfile pin to the allowed-docker-images directory."""
    image = entry["image"]
    slug = entry["slug"]
    result: dict[str, str] = {"kind": "docker_image", "image": image, "slug": slug}
    images_dir = os.path.join(reachability_repo_path, ALLOWED_DOCKER_IMAGES_RELDIR)
    if not os.path.isdir(images_dir):
        return {**result, "result": "skipped", "reason": "allowed_images_dir_absent"}
    target = os.path.join(images_dir, f"Dockerfile-{slug}")
    relative_target = os.path.relpath(target, reachability_repo_path)
    desired = f"FROM {image}\n"
    if os.path.isfile(target):
        with open(target, "r", encoding="utf-8") as image_file:
            existing = image_file.read()
        if existing.strip() == desired.strip():
            return {**result, "result": "already_present", "target": relative_target}
        # Do not clobber an existing pin that resolves to a different image.
        return {**result, "result": "skipped", "reason": "slug_conflict", "target": relative_target}
    with open(target, "w", encoding="utf-8") as image_file:
        image_file.write(desired)
    return {**result, "result": "applied", "target": relative_target}


def apply_library_preparation_setup(
        preflight: dict[str, Any] | None,
        reachability_repo_path: str,
) -> dict[str, Any] | None:
    """Apply deterministic preflight setup once and idempotently, recording results.

    Driver-owned step (§ORCH-forge-orchestration-spec.1.1): typed `dependency` and
    `docker_image` entries are applied as source edits; unappliable entries are
    left for the advisory guidance fallback. Mutates and returns the record.
    """
    if not isinstance(preflight, dict):
        return preflight
    if preflight.get("status") != "completed" or preflight.get("action") != "advisory_preparation":
        preflight.setdefault("applied_setup", [])
        return preflight
    library_coordinate = str(preflight.get("library") or "")
    applied: list[dict[str, str]] = []
    for entry in preflight.get("deterministic_setup") or []:
        kind = entry.get("kind") if isinstance(entry, dict) else None
        try:
            if kind == "dependency":
                applied.append(_apply_dependency_setup(reachability_repo_path, library_coordinate, entry))
            elif kind == "docker_image":
                applied.append(_apply_docker_image_setup(reachability_repo_path, entry))
        except Exception as exc:
            applied.append({"kind": str(kind), "result": "skipped", "reason": f"{type(exc).__name__}: {exc}"})
    preflight["applied_setup"] = applied
    return preflight


def prepare_library_preparation_preflight(
        preflight_path: str | None,
        reachability_repo_path: str,
) -> tuple[dict[str, Any] | None, str]:
    """Driver entry point: load the record, apply deterministic setup, render context.

    Returns the (mutated) record for metrics and the advisory-only prompt context.
    Must run after the reachability repo path is resolved so source edits land in
    the right worktree (§ORCH-forge-orchestration-spec.1.1).
    """
    preflight = load_library_preparation_preflight(preflight_path)
    apply_library_preparation_setup(preflight, reachability_repo_path)
    if isinstance(preflight, dict):
        applied = preflight.get("applied_setup") or []
        if applied:
            summary = ", ".join(
                f"{item.get('kind')}:{item.get('result')}"
                for item in applied if isinstance(item, dict)
            )
            log_stage("library-preflight", f"Applied deterministic setup: {summary}")
    context = format_library_preparation_preflight_context(preflight)
    return preflight, context


def _format_bullets(values: list[Any]) -> str:
    lines: list[str] = []
    for value in values:
        if isinstance(value, str) and value.strip():
            lines.append(f"- {value.strip()}")
    return "\n".join(lines)


def _describe_setup_entry(entry: dict[str, str]) -> str:
    """Render one deterministic-setup entry as agent-facing fallback work."""
    kind = entry.get("kind")
    if kind == "dependency":
        return f"Add {entry.get('scope', 'testImplementation')} dependency {entry.get('coordinate')}"
    if kind == "docker_image":
        return f"Use Docker image {entry.get('image')} (allow-list slug {entry.get('slug')})"
    return str(entry)


def _pending_setup_descriptions(preflight: dict[str, Any]) -> list[str]:
    """Describe deterministic entries the driver did not apply, for agent fallback."""
    results: dict[tuple[Any, Any], Any] = {}
    for item in preflight.get("applied_setup") or []:
        if isinstance(item, dict):
            results[(item.get("kind"), item.get("coordinate") or item.get("slug"))] = item.get("result")
    descriptions: list[str] = []
    for entry in preflight.get("deterministic_setup") or []:
        if not isinstance(entry, dict):
            continue
        key = (entry.get("kind"), entry.get("coordinate") or entry.get("slug"))
        if results.get(key) in {"applied", "already_present"}:
            continue
        descriptions.append(_describe_setup_entry(entry))
    return descriptions


def format_library_preparation_preflight_context(preflight: dict[str, Any] | None) -> str:
    """Render the advisory portion of a preflight record for workflow prompt context.

    Deterministic setup the driver already applied is intentionally omitted so an
    iterating workflow does not re-attempt a completed edit; only advisory guidance
    and unapplied-setup fallback reach the prompt (§ORCH-forge-orchestration-spec.1.1).
    """
    if not isinstance(preflight, dict):
        return NO_LIBRARY_PREPARATION_PREFLIGHT_CONTEXT

    action = str(preflight.get("action") or "no_action")
    summary = str(preflight.get("summary") or "").strip()
    if action != "advisory_preparation":
        reason = str(preflight.get("failure_reason") or "").strip()
        if reason:
            return f"{NO_LIBRARY_PREPARATION_PREFLIGHT_CONTEXT} Reason: {reason}"
        if summary:
            return f"{NO_LIBRARY_PREPARATION_PREFLIGHT_CONTEXT} Summary: {summary}"
        return NO_LIBRARY_PREPARATION_PREFLIGHT_CONTEXT

    guidance = str(preflight.get("agent_guidance") or "").strip()
    pending_setup = _format_bullets(_pending_setup_descriptions(preflight))
    risks = _format_bullets(preflight.get("risks") or [])

    sections = ["Library preparation preflight produced advisory guidance."]
    if summary:
        sections.extend(["", f"Summary: {summary}"])
    if guidance:
        sections.extend(["", "Agent guidance:", guidance])
    if pending_setup:
        sections.extend(["", "Setup to perform in the generated work:", pending_setup])
    if risks:
        sections.extend(["", "Risks:", risks])
    return "\n".join(sections)
