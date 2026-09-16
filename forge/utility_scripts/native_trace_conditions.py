# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Derive native-trace condition packages for a coordinate.

Native tracing conditions must be package roots that can actually appear on
the access stack (§FS-native-test-verification-gate); this module derives them
from the coordinate's user-code filter and filters out generated test packages.
"""

from __future__ import annotations

import json
import os
import re


def _default_condition_packages(reachability_repo_path: str, coordinate: str) -> list[str]:
    """Return native-trace condition packages for ``coordinate``.

    Native tracing conditions must be package roots that can actually appear on
    the access stack (§FS-native-test-verification-gate). Maven groups are only
    a fallback because many artifacts execute code outside the group-shaped
    package, e.g. ``org.apache.tomcat.embed`` artifacts using
    ``org.apache.catalina``.
    """
    packages = _condition_packages_from_user_code_filter(reachability_repo_path, coordinate)
    if packages:
        return packages
    return [coordinate.split(":", 1)[0]]


def _condition_packages_from_user_code_filter(
        reachability_repo_path: str,
        coordinate: str,
) -> list[str]:
    """Read trace condition packages from the coordinate's user-code filter."""
    try:
        group, artifact, version = coordinate.split(":", 2)
    except ValueError:
        return []
    filter_path = os.path.join(
        reachability_repo_path,
        "tests",
        "src",
        group,
        artifact,
        version,
        "user-code-filter.json",
    )
    try:
        with open(filter_path, "r", encoding="utf-8") as handle:
            payload = json.load(handle)
    except (OSError, json.JSONDecodeError, UnicodeDecodeError):
        return []
    rules = payload.get("rules") if isinstance(payload, dict) else None
    if not isinstance(rules, list):
        return []

    raw_packages: list[str] = []
    for rule in rules:
        if not isinstance(rule, dict):
            continue
        include_pattern = rule.get("includeClasses")
        if not isinstance(include_pattern, str):
            continue
        package_name = _include_classes_pattern_to_package(include_pattern)
        if package_name:
            raw_packages.append(package_name)

    unique_packages = _deduplicate(raw_packages)
    if not unique_packages:
        return []

    test_packages = _test_source_packages(reachability_repo_path, group, artifact, version)
    filtered_packages = [
        package_name
        for package_name in unique_packages
        if not _is_test_only_condition_package(package_name, test_packages, group)
    ]
    return filtered_packages or unique_packages


def _include_classes_pattern_to_package(include_pattern: str) -> str | None:
    """Convert a ``user-code-filter.json`` include pattern to a package root."""
    pattern = include_pattern.strip()
    for suffix in (".**", ".*"):
        if pattern.endswith(suffix):
            pattern = pattern[:-len(suffix)]
            break
    if "*" in pattern or not pattern:
        return None
    if "." in pattern and pattern.rsplit(".", 1)[-1][:1].isupper():
        pattern = pattern.rsplit(".", 1)[0]
    if not _is_java_package_name(pattern):
        return None
    return pattern


def _is_java_package_name(package_name: str) -> bool:
    return bool(re.fullmatch(
        r"[A-Za-z_$][A-Za-z0-9_$]*(\.[A-Za-z_$][A-Za-z0-9_$]*)*",
        package_name,
    ))


def _test_source_packages(
        reachability_repo_path: str,
        group: str,
        artifact: str,
        version: str,
) -> set[str]:
    test_dir = os.path.join(
        reachability_repo_path,
        "tests",
        "src",
        group,
        artifact,
        version,
        "src",
        "test",
    )
    packages: set[str] = set()
    if not os.path.isdir(test_dir):
        return packages
    for root, _dirs, files in os.walk(test_dir):
        for file_name in files:
            if file_name.endswith((".java", ".kt", ".scala")):
                package_name = _read_source_package(os.path.join(root, file_name))
                if package_name:
                    packages.add(package_name)
    return packages


def _read_source_package(path: str) -> str | None:
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as handle:
            for _index in range(80):
                line = handle.readline()
                if not line:
                    break
                match = re.match(
                    r"\s*package\s+([A-Za-z_$][A-Za-z0-9_$]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)*)\s*;?",
                    line,
                )
                if match:
                    return match.group(1)
    except OSError:
        return None
    return None


def _is_test_only_condition_package(
        package_name: str,
        test_packages: set[str],
        group: str,
) -> bool:
    if _package_overlaps(package_name, group):
        return False
    return any(
        _package_overlaps(package_name, test_package)
        for test_package in test_packages
    )


def _package_overlaps(left: str, right: str) -> bool:
    return (
        left == right
        or left.startswith(f"{right}.")
        or right.startswith(f"{left}.")
    )


def _deduplicate(values: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for value in values:
        if value in seen:
            continue
        seen.add(value)
        result.append(value)
    return result
