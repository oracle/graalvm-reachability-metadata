# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Create the data-only handoff consumed by trusted Actions publication.

The descriptor is committed with the generated library changes and is the only
feature-branch input accepted by the GitHub publisher (§GIT-publication-descriptor).
"""

from __future__ import annotations

import copy
import hashlib
import json
import os
import re
import subprocess
from dataclasses import dataclass, field
from typing import Any

from jsonschema import Draft202012Validator, FormatChecker

from git_scripts.common_git import get_forge_revision_info, parse_coordinate_parts
from utility_scripts.local_ci_verification import LocalCIVerificationResult
from utility_scripts.metrics_writer import execution_metrics_path_for_library, read_pending_metrics

DESCRIPTOR_FILENAME: str = "forge-publication.json"
DESCRIPTOR_SCHEMA_RELATIVE_PATH: str = os.path.join(
    ".github", "scripts", "forge_pr_publisher", "schema.json",
)
SCHEMA_VERSION: int = 1
ELIGIBLE_STATUSES: set[str] = {"success", "success_with_intervention", "chunk_ready"}


@dataclass(frozen=True)
class PublicationDescriptorInput:
    """Workflow-owned facts needed to create one publication descriptor."""

    issue_number: int
    task_type: str
    template_type: str
    status: str
    timestamp: str
    run_metrics: dict[str, Any] | None = None
    previous_coordinates: str | None = None
    chunked_dynamic_access: bool = False
    chunk_final: bool = True
    follow_ups: list[dict[str, Any]] = field(default_factory=list)
    render: dict[str, Any] = field(default_factory=dict)


def descriptor_input_from_pending_metrics(
        *,
        metrics_repo_path: str,
        issue_number: int,
        task_type: str,
        template_type: str,
        previous_coordinates: str | None = None,
        chunked_dynamic_access: bool = False,
        chunk_final: bool = True,
        follow_ups: list[dict[str, Any]] | None = None,
        render: dict[str, Any] | None = None,
) -> PublicationDescriptorInput:
    """Build a descriptor input from the durable pending run metrics."""
    run_metrics = read_pending_metrics(metrics_repo_path)
    timestamp = run_metrics.get("timestamp")
    status = run_metrics.get("status")
    if not isinstance(timestamp, str) or not timestamp:
        raise TypeError("ERROR: Pending publication metrics require a timestamp")
    if not isinstance(status, str) or not status:
        raise TypeError("ERROR: Pending publication metrics require a status")
    return PublicationDescriptorInput(
        issue_number=issue_number,
        task_type=task_type,
        template_type=template_type,
        status=status,
        timestamp=timestamp,
        run_metrics=run_metrics,
        previous_coordinates=previous_coordinates,
        chunked_dynamic_access=chunked_dynamic_access,
        chunk_final=chunk_final,
        follow_ups=list(follow_ups or []),
        render=dict(render or {}),
    )


def build_publication_id(
        issue_number: int,
        timestamp: str,
        coordinates: str,
        task_type: str,
) -> str:
    """Build a stable ID from durable run facts so continuation reuses it."""
    identity = json.dumps(
        {
            "issue_number": issue_number,
            "timestamp": timestamp,
            "coordinates": coordinates,
            "task_type": task_type,
        },
        sort_keys=True,
        separators=(",", ":"),
    )
    digest = hashlib.sha256(identity.encode("utf-8")).hexdigest()[:12]
    compact_timestamp = re.sub(r"[^0-9]", "", timestamp)[:20]
    if not compact_timestamp:
        raise ValueError("Publication timestamp must contain a date or time")
    return f"forge-{issue_number}-{compact_timestamp}-{digest}"


def build_publication_branch(producer: str, branch_suffix: str, publication_id: str) -> str:
    """Build the unique upstream branch named by the publication descriptor."""
    normalized_suffix = re.sub(r"[^A-Za-z0-9._-]+", "-", branch_suffix).strip("-.")
    if not normalized_suffix:
        raise ValueError("Publication branch suffix must contain a valid branch character")
    max_suffix_length = 198 - len(producer) - len(publication_id)
    normalized_suffix = normalized_suffix[:max_suffix_length].rstrip("-.")
    return f"ai/{producer}/{normalized_suffix}-{publication_id}"


def publication_descriptor_path(repo_path: str, coordinates: str) -> str:
    """Return the fixed coordinate-local publication descriptor path."""
    group, artifact, version = parse_coordinate_parts(coordinates)
    return os.path.join(repo_path, "stats", group, artifact, version, DESCRIPTOR_FILENAME)


def load_publication_schema(repo_path: str) -> dict[str, Any]:
    """Load the publisher schema from the reachability repository checkout."""
    schema_path = os.path.join(repo_path, DESCRIPTOR_SCHEMA_RELATIVE_PATH)
    with open(schema_path, "r", encoding="utf-8") as schema_file:
        schema = json.load(schema_file)
    if not isinstance(schema, dict):
        raise TypeError(f"ERROR: Expected publication schema object in {schema_path}")
    return schema


def validate_publication_descriptor(repo_path: str, descriptor: dict[str, Any]) -> None:
    """Validate schema and cross-field invariants before committing the handoff."""
    validator = Draft202012Validator(
        load_publication_schema(repo_path),
        format_checker=FormatChecker(),
    )
    validator.validate(descriptor)

    library = descriptor["library"]
    expected_coordinates = f"{library['group']}:{library['artifact']}:{library['version']}"
    if library["coordinates"] != expected_coordinates:
        raise ValueError("Publication library coordinate fields do not agree")
    producer = str(descriptor["producer"])
    publication_id = str(descriptor["publication_id"])
    branch = str(descriptor["branch"])
    if not branch.startswith(f"ai/{producer}/") or not branch.endswith(f"-{publication_id}"):
        raise ValueError("Publication branch does not match its producer and publication ID")


def write_publication_descriptor(
        *,
        repo_path: str,
        coordinates: str,
        producer: str,
        branch: str,
        publication_id: str,
        base_commit: str,
        descriptor_input: PublicationDescriptorInput,
        local_ci_verification: LocalCIVerificationResult,
        local_review: dict[str, Any] | None = None,
) -> str:
    """Write and validate the descriptor that Actions will treat as data."""
    if descriptor_input.status not in ELIGIBLE_STATUSES:
        raise ValueError(f"Publication status is not PR-eligible: {descriptor_input.status}")
    group, artifact, version = parse_coordinate_parts(coordinates)
    forge_branch, forge_commit = get_forge_revision_info()
    descriptor: dict[str, Any] = {
        "schema_version": SCHEMA_VERSION,
        "publication_id": publication_id,
        "timestamp": descriptor_input.timestamp,
        "branch": branch,
        "producer": producer,
        "base_commit": _resolve_commit(repo_path, base_commit),
        "issue_number": descriptor_input.issue_number,
        "library": _library_payload(group, artifact, version),
        "task_type": descriptor_input.task_type,
        "template_type": descriptor_input.template_type,
        "metrics": _metrics_payload(repo_path, coordinates, descriptor_input.run_metrics),
        "local_ci_verification": _local_ci_payload(repo_path, local_ci_verification),
        "forge": {
            "monitored_branch": os.environ.get("FORGE_MONITORED_BRANCH") or forge_branch,
            "branch": forge_branch,
            "commit": forge_commit,
        },
        "modifiers": {
            "chunked_dynamic_access": descriptor_input.chunked_dynamic_access,
            "chunk_final": descriptor_input.chunk_final,
            "human_intervention": local_ci_verification.human_intervention_required,
        },
        "follow_ups": copy.deepcopy(descriptor_input.follow_ups),
        "render": copy.deepcopy(descriptor_input.render),
    }
    if local_review is not None:
        # Optional so a publication resumed from a marker written before the
        # review phase existed still validates. §FS-local-branch-review
        descriptor["local_review"] = copy.deepcopy(local_review)
    if descriptor_input.previous_coordinates:
        previous_group, previous_artifact, previous_version = parse_coordinate_parts(
            descriptor_input.previous_coordinates,
        )
        descriptor["previous_library"] = _library_payload(
            previous_group, previous_artifact, previous_version,
        )
    run_metrics = descriptor_input.run_metrics
    if isinstance(run_metrics, dict):
        strategy_name = run_metrics.get("strategy_name")
        if isinstance(strategy_name, str) and strategy_name:
            descriptor["strategy_name"] = strategy_name
        intervention = run_metrics.get("post_generation_intervention")
        if isinstance(intervention, dict):
            descriptor["post_generation_intervention"] = copy.deepcopy(intervention)

    validate_publication_descriptor(repo_path, descriptor)
    descriptor_path = publication_descriptor_path(repo_path, coordinates)
    os.makedirs(os.path.dirname(descriptor_path), exist_ok=True)
    with open(descriptor_path, "w", encoding="utf-8") as descriptor_file:
        json.dump(descriptor, descriptor_file, indent=2, sort_keys=True, ensure_ascii=False)
        descriptor_file.write("\n")
    return descriptor_path


def _local_ci_payload(
        repo_path: str,
        verification: LocalCIVerificationResult,
) -> dict[str, Any]:
    """Normalize local verification commit references for trusted validation."""
    payload = verification.to_metrics()
    payload["base_commit"] = _resolve_commit(repo_path, verification.base_commit)
    if verification.final_commit:
        payload["final_commit"] = _resolve_commit(repo_path, verification.final_commit)
    return payload

def _metrics_payload(
        repo_path: str,
        coordinates: str,
        run_metrics: dict[str, Any] | None,
) -> dict[str, Any] | None:
    if run_metrics is None:
        return None
    timestamp = run_metrics.get("timestamp")
    metrics = run_metrics.get("metrics")
    if not isinstance(timestamp, str) or not timestamp:
        raise TypeError("ERROR: Publication run metrics require a timestamp")
    if not isinstance(metrics, dict):
        raise TypeError("ERROR: Publication run metrics require a metrics object")
    metrics_path = execution_metrics_path_for_library(repo_path, coordinates)
    payload: dict[str, Any] = {
        "path": os.path.relpath(metrics_path, repo_path).replace(os.sep, "/"),
        "timestamp": timestamp,
        "summary": copy.deepcopy(metrics),
    }
    for key in ("agent", "model"):
        value = run_metrics.get(key)
        if isinstance(value, str) and value:
            payload[key] = value
    return payload


def _library_payload(group: str, artifact: str, version: str) -> dict[str, str]:
    return {
        "group": group,
        "artifact": artifact,
        "version": version,
        "coordinates": f"{group}:{artifact}:{version}",
    }


def _resolve_commit(repo_path: str, commitish: str) -> str:
    return subprocess.check_output(
        ["git", "rev-parse", f"{commitish}^{{commit}}"],
        cwd=repo_path,
        text=True,
    ).strip()
