# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The contribution's closed file set, decided once for every check that needs it.

A path belongs to the contribution of one coordinate exactly when
§root/FS-contribution-contract.2 admits it: the version's own files, an
allowed-Docker-image file whose image the test lists, routed owner metadata
with entries only added, and the new directories that move a version off a
shared suite. The scope gate, the checkpoints, publication, and the check of a
review fix all ask this module (§AR-expected-paths).
"""
from __future__ import annotations

import json
import os
import subprocess
from collections import Counter
from typing import Any, Iterable

LEGACY_CONFIGS: frozenset[str] = frozenset({
    "reflect-config.json", "resource-config.json", "proxy-config.json", "serialization-config.json",
    "jni-config.json", "predefined-classes-config.json",
})
STATS_FILES: frozenset[str] = frozenset({"stats.json", "execution-metrics.json", "forge-publication.json"})
METADATA_FILE: str = "reachability-metadata.json"
ALLOWED_IMAGES_DIR: str = "tests/tck-build-logic/src/main/resources/allowed-docker-images/"
REQUIRED_IMAGES_FILE: str = "required-docker-images.txt"


def roots(coordinate: str) -> list[str]:
    """The directories that hold the coordinate's own files, every version included."""
    group, artifact, _ = coordinate.split(":")
    return [os.path.join(top, group, artifact) for top in ("metadata", "tests/src", "stats")]


def stray_paths(repo_path: str, coordinate: str, base: str, paths: Iterable[str],
                head: str | None = None) -> list[str]:
    """The paths among `paths` that leave the coordinate's file set.

    `base` is the commit the contribution starts from, and `head` the commit that
    holds it, or `None` for the working tree.
    """
    files: _Files = _Files(repo_path, base, head)
    changed: list[str] = sorted(set(paths))
    group, artifact, version = coordinate.split(":")
    owners: set[tuple[str, str, str]] = {owner for owner in map(_owner_of, changed)
                                         if owner is not None and owner[:2] != (group, artifact)
                                         and _routed_owner(files, owner)}
    sharing: dict[str, str] = _sharing_versions(files, group, artifact, version)
    return [path for path in changed
            if os.path.basename(path) in LEGACY_CONFIGS
            or not (_own(path, group, artifact, version)
                    or _owner_path(path, owners)
                    or _leaves_shared_suite(files, path, group, artifact, sharing)
                    or _allowed_image(files, path, group, artifact, version))]


def contribution_paths(repo_path: str, coordinate: str, base: str, paths: Iterable[str]) -> list[str]:
    """What a checkpoint stages: the coordinate's roots and every admitted path outside them."""
    stray: set[str] = set(stray_paths(repo_path, coordinate, base, paths))
    own: list[str] = roots(coordinate)
    return own + sorted(path for path in set(paths) - stray if not any(_inside(path, root) for root in own))


class _Files:
    """File contents at `base` and at `head` (`None`: the working tree)."""

    def __init__(self, repo_path: str, base: str, head: str | None) -> None:
        self.repo_path: str = repo_path
        self.base: str = base
        self.head: str | None = head

    def read(self, path: str, at_base: bool = False) -> str | None:
        ref: str | None = self.base if at_base else self.head
        if ref is None:
            absolute: str = os.path.join(self.repo_path, path)
            if not os.path.isfile(absolute):
                return None
            with open(absolute, encoding="utf-8") as handle:
                return handle.read()
        shown: subprocess.CompletedProcess[str] = subprocess.run(
            ["git", "show", f"{ref}:{path}"], cwd=self.repo_path, capture_output=True, text=True)
        return shown.stdout if shown.returncode == 0 else None

    def json(self, path: str, at_base: bool = False) -> Any:
        text: str | None = self.read(path, at_base)
        if text is None:
            return None
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            return None


def _inside(path: str, root: str) -> bool:
    return path == root or path.startswith(root.rstrip("/") + "/")


def _own(path: str, group: str, artifact: str, version: str) -> bool:
    stats_dir: str = f"stats/{group}/{artifact}/{version}"
    return (path in (f"metadata/{group}/{artifact}/index.json", f"metadata/{group}/{artifact}/{version}/{METADATA_FILE}")
            or _inside(path, f"tests/src/{group}/{artifact}/{version}")
            or (os.path.dirname(path) == stats_dir and os.path.basename(path) in STATS_FILES))


def _owner_of(path: str) -> tuple[str, str, str] | None:
    parts: list[str] = path.split("/")
    if len(parts) == 5 and parts[0] == "metadata" and parts[4] == METADATA_FILE:
        return parts[1], parts[2], parts[3]
    return None


def _entries(document: Any) -> Counter[str]:
    """Each entry of a metadata document, keyed by its section, as canonical JSON."""
    found: Counter[str] = Counter()
    for section, entries in (document or {}).items() if isinstance(document, dict) else []:
        for entry in entries if isinstance(entries, list) else [entries]:
            found[json.dumps([section, entry], sort_keys=True)] += 1
    return found


def _index_entry(files: _Files, group: str, artifact: str, version: str) -> dict[str, Any] | None:
    for at_base in (False, True):
        index: Any = files.json(f"metadata/{group}/{artifact}/index.json", at_base)
        for entry in index if isinstance(index, list) else []:
            if isinstance(entry, dict) and entry.get("metadata-version") == version:
                return entry
    return None


def _routed_owner(files: _Files, owner: tuple[str, str, str]) -> bool:
    """Whether the owner's metadata only gained entries, each conditioned on the owner's packages.

    §root/FS-contribution-contract.2
    """
    group, artifact, version = owner
    if files.read(f"metadata/{group}/{artifact}/index.json", at_base=True) is None:
        return False
    entry: dict[str, Any] | None = _index_entry(files, group, artifact, version)
    packages: list[str] = list((entry or {}).get("allowed-packages") or [])
    path: str = f"metadata/{group}/{artifact}/{version}/{METADATA_FILE}"
    before: Counter[str] = _entries(files.json(path, at_base=True))
    after: Counter[str] = _entries(files.json(path))
    if not packages or not after or before - after:
        return False
    added: list[Any] = [json.loads(key)[1] for key in (after - before).elements()]
    return bool(added) and all(_conditioned(entry, packages) for entry in added)


def _conditioned(entry: Any, packages: list[str]) -> bool:
    """`typeReached` names an owner package, as `MetadataEntryOwnership.matchesAllowedPackage` does."""
    condition: Any = entry.get("condition") if isinstance(entry, dict) else None
    reached: Any = condition.get("typeReached") if isinstance(condition, dict) else None
    return isinstance(reached, str) and any(package in reached for package in packages)


def _owner_path(path: str, owners: set[tuple[str, str, str]]) -> bool:
    for group, artifact, version in owners:
        stats_dir: str = f"stats/{group}/{artifact}/{version}"
        if path in (f"metadata/{group}/{artifact}/{version}/{METADATA_FILE}", f"metadata/{group}/{artifact}/index.json"):
            return True
        if os.path.dirname(path) == stats_dir and os.path.basename(path) == "stats.json":
            return True
    return False


def _sharing_versions(files: _Files, group: str, artifact: str, version: str) -> dict[str, str]:
    """Each other version that tested with the coordinate's test directory at `base`, mapped to
    the metadata version that shipped for it."""
    index: Any = files.json(f"metadata/{group}/{artifact}/index.json", at_base=True)
    sharing: dict[str, str] = {}
    for entry in index if isinstance(index, list) else []:
        if not isinstance(entry, dict):
            continue
        metadata_version: str = str(entry.get("metadata-version"))
        if str(entry.get("test-version") or metadata_version) != version:
            continue
        for tested in [metadata_version, *(entry.get("tested-versions") or [])]:
            if str(tested) != version:
                sharing[str(tested)] = metadata_version
    return sharing


def _leaves_shared_suite(files: _Files, path: str, group: str, artifact: str, sharing: dict[str, str]) -> bool:
    """A new directory of a version leaving the coordinate's shared suite; its metadata is a copy.

    §root/FS-contribution-contract.2
    """
    for version, metadata_version in sharing.items():
        tests: str = f"tests/src/{group}/{artifact}/{version}"
        if _inside(path, tests):
            return files.read(path, at_base=True) is None
        if path == f"stats/{group}/{artifact}/{version}/stats.json":
            return True
        if path == f"metadata/{group}/{artifact}/{version}/{METADATA_FILE}":
            shipped: str | None = files.read(f"metadata/{group}/{artifact}/{metadata_version}/{METADATA_FILE}",
                                             at_base=True)
            return files.read(path, at_base=True) is None and shipped is not None and files.json(path) == json.loads(shipped)
    return False


def _allowed_image(files: _Files, path: str, group: str, artifact: str, version: str) -> bool:
    """An allowed-Docker-image file whose `FROM` image the coordinate's test lists."""
    if not path.startswith(ALLOWED_IMAGES_DIR):
        return False
    text: str | None = files.read(path)
    required: str | None = files.read(f"tests/src/{group}/{artifact}/{version}/{REQUIRED_IMAGES_FILE}")
    if text is None or required is None:
        return False
    images: set[str] = {line.split()[1] for line in text.splitlines() if line.strip().upper().startswith("FROM ")
                        and len(line.split()) > 1}
    return bool(images) and images <= {line.strip() for line in required.splitlines() if line.strip()}
