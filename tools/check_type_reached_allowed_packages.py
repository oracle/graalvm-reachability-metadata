#!/usr/bin/env python3

import json
from pathlib import Path
from typing import Any

ROOT = Path("metadata")
PREDEFINED_ALLOWED_PACKAGES = ("java.lang", "java.util")


def find_type_reached(node: Any, results: list[str]) -> None:
    if isinstance(node, dict):
        condition = node.get("condition")
        if isinstance(condition, dict):
            type_reached = condition.get("typeReached")
            if isinstance(type_reached, str):
                results.append(type_reached)
        for value in node.values():
            find_type_reached(value, results)
    elif isinstance(node, list):
        for item in node:
            find_type_reached(item, results)


def resolve_index_entry(index_entries: list[dict[str, Any]], version: str) -> dict[str, Any] | None:
    by_metadata_version = next(
        (entry for entry in index_entries if entry.get("metadata-version") == version),
        None,
    )
    if by_metadata_version is not None:
        return by_metadata_version

    return next(
        (
            entry
            for entry in index_entries
            if isinstance(entry.get("tested-versions"), list) and version in entry.get("tested-versions")
        ),
        None,
    )


def main() -> None:
    anomalies: list[dict[str, Any]] = []

    for reachability_metadata in sorted(ROOT.glob("*/*/*/reachability-metadata.json")):
        _, group, artifact, version, _ = reachability_metadata.parts[-5:]
        index_path = ROOT / group / artifact / "index.json"

        try:
            index_entries = json.loads(index_path.read_text())
        except Exception as exc:
            anomalies.append(
                {
                    "file": str(reachability_metadata),
                    "error": f"cannot parse index.json: {exc}",
                }
            )
            continue

        index_entry = resolve_index_entry(index_entries, version)
        allowed_packages = index_entry.get("allowed-packages") if index_entry else None

        try:
            metadata = json.loads(reachability_metadata.read_text())
        except Exception as exc:
            anomalies.append(
                {
                    "file": str(reachability_metadata),
                    "error": f"cannot parse reachability-metadata.json: {exc}",
                }
            )
            continue

        type_reached_values: list[str] = []
        find_type_reached(metadata, type_reached_values)

        for type_reached in type_reached_values:
            if any(package in type_reached for package in PREDEFINED_ALLOWED_PACKAGES):
                continue
            if not index_entry or not isinstance(allowed_packages, list) or all(
                package not in type_reached for package in allowed_packages
            ):
                anomalies.append(
                    {
                        "file": str(reachability_metadata),
                        "typeReached": type_reached,
                        "allowed-packages": allowed_packages,
                    }
                )

    print(json.dumps(anomalies, indent=2))


if __name__ == "__main__":
    main()
