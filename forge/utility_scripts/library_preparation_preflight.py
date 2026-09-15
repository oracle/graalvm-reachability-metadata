# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Library-specific preparation preflight: input bundle, prompt, and agent run.

Record parsing and validation live in `library_preflight_record.py`; the
driver-side setup application in `library_preparation_setup.py`. Both are
re-exported here for existing callers.
"""

import json
import os
from typing import Any, Callable

from ai_workflows.agents.agent_runtime import AgentRunResult, get_setup_agent, setup_agent_run
from utility_scripts.library_preflight_record import (
    LIBRARY_PREFLIGHT_SKIP_VERDICT_LABELS,
    _completed_library_preflight_record,
    _degraded_library_preflight_record,
    _extract_preflight_json_response,
    _parse_skipped_versions,
    _target_library_for_preflight,
    _truncate_preflight_text,
)
from utility_scripts.metadata_index import (
    coordinate_parts as metadata_coordinate_parts,
    resolve_test_dir,
)
from utility_scripts.run_location import (
    PHASE_SETUP,
    STEP_NEURAL_SETUP,
    log_step_progress,
)
from utility_scripts.stage_logger import log_detail
from utility_scripts.task_logs import display_log_path

LIBRARY_PREPARATION_PREFLIGHT_FILENAME = ".library_preparation_preflight.json"
LIBRARY_PREFLIGHT_TIMEOUT_SECONDS = 900
LIBRARY_PREFLIGHT_MAX_ISSUE_BODY_CHARS = 8000
LIBRARY_PREFLIGHT_MAX_TEST_FILES = 40
# Deterministic setup the driver applies itself, idempotently, as source edits
# (§AR-forge-orchestration.1.1). Anything else stays advisory guidance.
LIBRARY_PREFLIGHT_DETERMINISTIC_KINDS = ("dependency", "docker_image")


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


def _skip_verdict_prompt_section(input_bundle: dict[str, Any]) -> str:
    """Render the unsupportable-version diagnosis for issues with failure evidence.

    The five-step bar and the strict default come from
    §FS-unsupportable-version-diagnosis; the disposition it feeds is
    §root/FS-contribution-contract.5.4.
    """
    issue = input_bundle.get("issue") if isinstance(input_bundle.get("issue"), dict) else {}
    if str(issue.get("label") or "") not in LIBRARY_PREFLIGHT_SKIP_VERDICT_LABELS:
        return ""
    library = str(input_bundle.get("library") or "")
    version = library.split(":")[-1]
    return (
        "Separately, decide whether this library version can be supported under GraalVM "
        "Native Image at all. A version is unsupportable only when covering its "
        "dynamic-access calls requires behavior Native Image does not support: runtime "
        "bytecode generation or class definition, runtime lambda definition, Java agent "
        "self-attach, class redefinition or instrumentation, native-image substitution "
        "paths, or URL/plugin/OSGi class loader paths that introduce classes not in the "
        "image.\n\n"
        "Run these steps in order. An unsupportable verdict requires ALL of them; stay "
        "with the two actions above the moment one fails:\n"
        "1. FAILURE: state the failing operation from the issue's failure evidence alone.\n"
        "2. TRACE: follow the failing symbol into the library's own source and quote the "
        "code performing the operation.\n"
        "3. CLASSIFY: show the operation is in the list above. Anything reachability "
        "metadata can register is repairable.\n"
        "4. SOLE PATH: prove every public API route the metadata would justify passes "
        "through this operation, with no configuration, property, or fallback branch "
        "around it. One viable fallback means repairable.\n"
        "5. RANGE: list newer released versions whose sources you verified share the "
        "same mechanism. Include only versions you actually verified; when you cannot "
        f"verify newer versions, record only {version}.\n\n"
        "On an unsupportable verdict, return instead:\n"
        "{\n"
        '  "action": "skip_unsupported",\n'
        '  "summary": "one sentence naming the mechanism and why the image cannot perform it",\n'
        '  "skipped_versions": [{"version": "...", "reason": "mechanism sentence; later '
        "versions: '; unchanged from <first>'\"}, ...]\n"
        "}\n"
        f"skipped_versions must include {version}; reasons are published verbatim in "
        "index.json and the pull request, so write them for a reader who has not seen "
        "this analysis. Never base the verdict on a red test, a library's own error "
        "type, or its error message — only on the mechanism you traced in the library's "
        "source. Any uncertainty at any step means repairable: attempting the fix is "
        "the safe default, a wrong skip freezes versions that were never attempted.\n\n"
    )


def _library_preflight_prompt(input_bundle: dict[str, Any]) -> str:
    """Build the LLM prompt instructing the agent to research preparation needs."""
    return (
        "You are preparing a Forge workflow for a library issue. Decide whether the "
        "library needs setup beyond the normal generated test scaffold before "
        "meaningful coverage is reachable.\n\n"
        "Research the library yourself to make this decision. Investigate the resolved "
        "artifact and its dependencies, how the library is normally used, and its "
        "documentation. The JSON context below is only a starting point, not the limit "
        "of what you may consider. Do not modify the repository or apply any setup "
        "yourself — return only the decision. Local Gradle and Native Image verification "
        "remain authoritative.\n\n"
        "The setup can be one of three things: an extra Maven dependency, a Docker "
        "image the tests need, or an environment/system property the generated tests "
        "must set. Represent dependencies and Docker images as `deterministic_setup` "
        "entries the driver can apply before generation:\n"
        '  - {"kind": "dependency", "coordinate": "group:artifact:version", '
        '"scope": "testImplementation", "reason": "..."}\n'
        '  - {"kind": "docker_image", "image": "name:tag", "slug": "short-slug", "reason": "..."}\n'
        "If a test needs a Docker image that is already pinned under "
        "`tests/tck-build-logic/src/main/resources/allowed-docker-images/`, reuse the exact "
        "tag from that `Dockerfile-<slug>` instead of introducing a new one, since the "
        "allow-list permits a single shared tag per image.\n"
        "Put environment variables, system properties, and other test-code setup in "
        "`agent_guidance`. Do not restate deterministic entries as guidance.\n\n"
        "Return a single JSON object with this shape:\n"
        "{\n"
        '  "action": "no_action" | "advisory_preparation",\n'
        '  "summary": "short reason",\n'
        '  "deterministic_setup": [ ... dependency or docker_image entries, if any ... ],\n'
        '  "agent_guidance": "environment/system property/test setup guidance, if any",\n'
        '  "risks": ["risk notes, if any"]\n'
        "}\n\n"
        "Choose no_action unless your research finds concrete evidence that extra setup is needed.\n\n"
        f"{_skip_verdict_prompt_section(input_bundle)}"
        "Context (starting point):\n"
        f"{json.dumps(input_bundle, indent=2, ensure_ascii=False)}"
    )


def _write_text_artifact(root: str, file_name: str, content: str) -> None:
    """Write a preflight text artifact next to the record it belongs to."""
    os.makedirs(root, exist_ok=True)
    with open(os.path.join(root, file_name), "w", encoding="utf-8") as artifact_file:
        artifact_file.write(content)


def _preflight_artifact_root(claimed_issue: Any) -> str:
    """Return the directory used for dispatcher preflight handoff and evidence."""
    return getattr(claimed_issue, "preflight_info_path", None) or claimed_issue.scratch_metrics_repo_path


def _write_and_log_preflight(
        claimed_issue: Any,
        record: dict[str, Any],
        session_log_path: str | None = None,
) -> str:
    """Persist the record and log a one-line outcome, covering every decision path.

    The session log path is printed, never persisted: the record is committed to
    `stats/`, where a path into the operator's gitignored `logs/` tree resolves for
    no reader and an absolute one carries a home directory into a public
    repository. §FS-durable-generation-logs
    """
    detail = f"status={record.get('status')} action={record.get('action')}"
    setup_count = len(record.get("deterministic_setup") or [])
    if setup_count:
        detail += f" deterministic_setup={setup_count}"
    failure_reason = record.get("failure_reason")
    if failure_reason:
        detail += f" reason={failure_reason}"
    log_detail(
        "library-preflight",
        f"Preflight decision for issue #{record.get('issue_number')}: {detail}",
    )
    library = str(record.get("library") or "unknown library")
    if record.get("status") == "completed":
        outcome = str(record.get("action") or "no_action").replace("_", " ")
        if setup_count:
            outcome += f", {setup_count} deterministic setup item(s)"
        log_step_progress(
            PHASE_SETUP,
            STEP_NEURAL_SETUP,
            f"Library preflight completed for {library}: {outcome}",
        )
    else:
        log_suffix = (
            f" (log: {display_log_path(session_log_path)})" if session_log_path else ""
        )
        failure_text = str(failure_reason or "no usable decision")
        log_step_progress(
            PHASE_SETUP,
            STEP_NEURAL_SETUP,
            f"Library preflight degraded for {library}: {failure_text}{log_suffix}",
        )
    return write_library_preparation_preflight(_preflight_artifact_root(claimed_issue), record)


def run_library_preparation_preflight(
        claimed_issue: Any,
        issue_body_provider: Callable[[int], str],
) -> str:
    """Run and persist the library-specific preparation preflight before workflow dispatch.

    Preflight researches a library and decides which deterministic setup it
    needs, before the tree holds any generated work, so it runs on the setup
    role (§FS-forge-agent-runtime-selection). The role owns the backend and the
    model; the record reports what actually ran.
    """
    selection = get_setup_agent()
    input_bundle = build_library_preflight_input_bundle(
        claimed_issue,
        issue_body_provider,
    )
    log_step_progress(
        PHASE_SETUP,
        STEP_NEURAL_SETUP,
        f"Running library preflight for {input_bundle.get('library')}",
    )
    log_detail(
        "library-preflight",
        (
            f"Running preflight for issue #{claimed_issue.issue['number']} "
            "(live) "
            f"library={input_bundle.get('library')} "
            f"agent={selection.backend} model={selection.model}"
        ),
    )
    prompt = _library_preflight_prompt(input_bundle)
    preflight_artifact_root = _preflight_artifact_root(claimed_issue)
    # The prompt and the response stay on disk as run evidence; only their paths
    # are kept out of the committed record.
    _write_text_artifact(
        preflight_artifact_root,
        "library-preflight-prompt.txt",
        prompt,
    )
    session_log_path: str | None = None
    model_name = selection.model

    result: AgentRunResult | None = None
    try:
        result = setup_agent_run(
            working_dir=claimed_issue.worktree_path,
            context=prompt,
            task_type="library-preparation-preflight",
            library=str(input_bundle.get("library") or ""),
            timeout=LIBRARY_PREFLIGHT_TIMEOUT_SECONDS,
        )
        if result.return_code != 0:
            raise RuntimeError(
                f"preflight agent exited {result.return_code}"
                + (" (timed out)" if result.timed_out else "")
            )
        response_text = result.response
        _write_text_artifact(
            preflight_artifact_root,
            "library-preflight-response.txt",
            response_text,
        )
        session_log_path = result.session_log_path
        response_payload = _extract_preflight_json_response(response_text)
        record = _completed_library_preflight_record(
            claimed_issue,
            input_bundle,
            response_payload,
            model_name,
            result,
        )
    except Exception as exc:
        if session_log_path is None and result is not None:
            session_log_path = result.session_log_path
        record = _degraded_library_preflight_record(
            claimed_issue,
            input_bundle,
            f"{type(exc).__name__}: {exc}",
            model_name=model_name,
        )
    return _write_and_log_preflight(claimed_issue, record, session_log_path)


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


def preflight_skip_record_entries(
        preflight: dict[str, Any] | None,
        target_version: str,
) -> list[dict[str, str]] | None:
    """Return the validated skip-record entries a driver should honor, or None.

    Strict on every field so a malformed record falls through to normal
    generation (§FS-unsupportable-version-diagnosis).
    """
    if not isinstance(preflight, dict):
        return None
    if preflight.get("status") != "completed" or preflight.get("action") != "skip_unsupported":
        return None
    try:
        return _parse_skipped_versions(preflight.get("skipped_versions"), target_version)
    except ValueError:
        return None
