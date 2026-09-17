# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Driver-side application of the library preparation preflight.

Applies typed deterministic setup entries as idempotent source edits and
renders the advisory-only prompt context (§AR-forge-orchestration.1.1). The
preflight run and its record shapes live in `library_preparation_preflight.py`.
"""

import os
import re
from typing import Any

from utility_scripts.metadata_index import (
    coordinate_parts as metadata_coordinate_parts,
    resolve_test_dir,
)
from utility_scripts.stage_logger import log_detail

NO_LIBRARY_PREPARATION_PREFLIGHT_CONTEXT = (
    "Library preparation preflight did not request additional setup."
)
ALLOWED_DOCKER_IMAGES_RELDIR = os.path.join(
    "tests", "tck-build-logic", "src", "main", "resources", "allowed-docker-images"
)


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


def _ensure_required_docker_image(reachability_repo_path: str, library_coordinate: str, image: str) -> None:
    """Declare `image` in the library's `required-docker-images.txt` once its test dir exists.

    For a new library the test dir is created by `scaffold`, so at preflight time this is a
    no-op and the driver re-applies the setup after scaffold.
    """
    parts = library_coordinate.split(":")
    if len(parts) != 3:
        return
    group, artifact, version = parts
    test_dir = os.path.join(reachability_repo_path, "tests", "src", group, artifact, version)
    if not os.path.isdir(test_dir):
        return
    required_path = os.path.join(test_dir, "required-docker-images.txt")
    lines: list[str] = []
    if os.path.isfile(required_path):
        with open(required_path, "r", encoding="utf-8") as required_file:
            lines = [line.strip() for line in required_file if line.strip()]
    if image in lines:
        return
    lines.append(image)
    with open(required_path, "w", encoding="utf-8") as required_file:
        required_file.write("\n".join(lines) + "\n")


def _apply_docker_image_setup(reachability_repo_path: str, entry: dict[str, str], library_coordinate: str) -> dict[str, str]:
    """Idempotently add a Dockerfile pin to the allowed-docker-images directory."""
    image = entry["image"]
    slug = entry["slug"]
    result: dict[str, str] = {"kind": "docker_image", "image": image, "slug": slug}
    images_dir = os.path.join(reachability_repo_path, ALLOWED_DOCKER_IMAGES_RELDIR)
    if not os.path.isdir(images_dir):
        return {**result, "result": "skipped", "reason": "allowed_images_dir_absent"}
    _ensure_required_docker_image(reachability_repo_path, library_coordinate, image)
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
        only_kinds: set[str] | None = None,
) -> dict[str, Any] | None:
    """Apply deterministic preflight setup once and idempotently, recording results.

    Driver-owned step (§AR-forge-orchestration.1.1): typed `dependency` and
    `docker_image` entries are applied as source edits; unappliable entries are
    left for the advisory guidance fallback. Mutates and returns the record.

    `only_kinds` restricts which entry kinds are (re)applied on this pass; entries of
    other kinds keep their prior `applied_setup` result untouched. The post-scaffold
    reapply passes `{"docker_image"}` so it re-pins allow-list Dockerfiles without
    re-touching dependencies the model's already-rendered context still lists as
    pending work.
    """
    if not isinstance(preflight, dict):
        return preflight
    if preflight.get("status") != "completed" or preflight.get("action") != "advisory_preparation":
        preflight.setdefault("applied_setup", [])
        return preflight
    library_coordinate = str(preflight.get("library") or "")
    previous: dict[tuple[Any, Any], dict[str, str]] = {}
    for item in preflight.get("applied_setup") or []:
        if isinstance(item, dict):
            previous[(item.get("kind"), item.get("coordinate") or item.get("slug"))] = item
    applied: list[dict[str, str]] = []
    for entry in preflight.get("deterministic_setup") or []:
        kind = entry.get("kind") if isinstance(entry, dict) else None
        key = (kind, entry.get("coordinate") or entry.get("slug")) if isinstance(entry, dict) else (kind, None)
        if only_kinds is not None and kind not in only_kinds:
            if key in previous:
                applied.append(previous[key])
            continue
        try:
            if kind == "dependency":
                applied.append(_apply_dependency_setup(reachability_repo_path, library_coordinate, entry))
            elif kind == "docker_image":
                applied.append(_apply_docker_image_setup(reachability_repo_path, entry, library_coordinate))
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
    the right worktree (§AR-forge-orchestration.1.1).
    """
    # Imported here to break the cycle with the re-export shim in
    # library_preparation_preflight.
    from utility_scripts.library_preparation_preflight import load_library_preparation_preflight
    preflight = load_library_preparation_preflight(preflight_path)
    apply_library_preparation_setup(preflight, reachability_repo_path)
    if isinstance(preflight, dict):
        applied = preflight.get("applied_setup") or []
        if applied:
            summary = ", ".join(
                f"{item.get('kind')}:{item.get('result')}"
                for item in applied if isinstance(item, dict)
            )
            log_detail("library-preflight", f"Applied deterministic setup: {summary}")
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


def _applied_setup_descriptions(preflight: dict[str, Any]) -> list[str]:
    """Describe deterministic setup already applied or already present."""
    setup_by_key: dict[tuple[Any, Any], dict[str, str]] = {}
    for entry in preflight.get("deterministic_setup") or []:
        if isinstance(entry, dict):
            setup_by_key[(entry.get("kind"), entry.get("coordinate") or entry.get("slug"))] = entry

    descriptions: list[str] = []
    for item in preflight.get("applied_setup") or []:
        if not isinstance(item, dict) or item.get("result") not in {"applied", "already_present"}:
            continue
        key = (item.get("kind"), item.get("coordinate") or item.get("slug"))
        entry = setup_by_key.get(key, item)
        state = "already present" if item.get("result") == "already_present" else "applied"
        descriptions.append(f"{_describe_setup_entry(entry)} ({state})")
    return descriptions


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
    """Render preflight summary, applied setup, and guidance for workflow prompts."""
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
    applied_setup = _format_bullets(_applied_setup_descriptions(preflight))
    pending_setup = _format_bullets(_pending_setup_descriptions(preflight))
    risks = _format_bullets(preflight.get("risks") or [])

    sections = ["Library preparation preflight produced setup context."]
    if summary:
        sections.extend(["", f"Summary: {summary}"])
    if applied_setup:
        sections.extend(["", "Already applied by Forge:", applied_setup])
    if guidance:
        sections.extend(["", "Agent guidance:", guidance])
    if pending_setup:
        sections.extend(["", "Setup to perform in the generated work:", pending_setup])
    if risks:
        sections.extend(["", "Risks:", risks])
    return "\n".join(sections)
