# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Utilities for source-derived dynamic-access call reports.

The neural dynamic-access workflow asks an agent to scan source context and write
raw dynamic-access call files. Those files intentionally mirror the raw files
created by the native-image dynamic-access instrumentation:

    reflection-calls.json
    resources-calls.json
    serialization-calls.json
    proxy-calls.json

Each file is a JSON object mapping a tracked runtime API to stack-frame strings.
This module canonicalizes that agent output once, freezes it, and repeatedly
correlates the same frozen seed with the current JaCoCo report.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from decimal import Decimal, ROUND_HALF_UP
from pathlib import Path
from typing import Any


SUPPORTED_REPORT_TYPES = ("reflection", "resources", "serialization", "proxy")
FRAME_PATTERN = re.compile(r"^(.+)\.([^.(]+|<init>|<clinit>)\(([^:()]+)(?::(\d+))?\)$")

TRACKED_APIS_BY_TYPE = {
    "reflection": {
        "java.lang.Class#forName(java.lang.String)",
        "java.lang.Class#forName(java.lang.String, boolean, java.lang.ClassLoader)",
        "java.lang.Class#getConstructor(java.lang.Class[])",
        "java.lang.Class#getConstructors()",
        "java.lang.Class#getDeclaredConstructor(java.lang.Class[])",
        "java.lang.Class#getDeclaredField(java.lang.String)",
        "java.lang.Class#getDeclaredMethod(java.lang.String, java.lang.Class[])",
        "java.lang.Class#getDeclaredMethods()",
        "java.lang.Class#getMethod(java.lang.String, java.lang.Class[])",
        "java.lang.Class#getMethods()",
        "java.lang.Class#newInstance()",
        "java.lang.ClassLoader#findSystemClass(java.lang.String)",
        "java.lang.ClassLoader#loadClass(java.lang.String)",
        "java.lang.reflect.Array#newInstance(java.lang.Class, int)",
        "java.lang.reflect.Constructor#newInstance(java.lang.Object[])",
        "java.lang.reflect.Field#get(java.lang.Object)",
        "java.lang.reflect.Method#invoke(java.lang.Object, java.lang.Object[])",
    },
    "resources": {
        "java.lang.Class#getResource(java.lang.String)",
        "java.lang.Class#getResourceAsStream(java.lang.String)",
        "java.lang.ClassLoader#getResource(java.lang.String)",
        "java.lang.ClassLoader#getResourceAsStream(java.lang.String)",
        "java.lang.ClassLoader#getResources(java.lang.String)",
    },
    "serialization": {
        "java.io.ObjectInputStream#readObject()",
        "java.io.ObjectInputStream#resolveClass(java.io.ObjectStreamClass)",
        "java.io.ObjectOutputStream#writeObject(java.lang.Object)",
    },
    "proxy": {
        "java.lang.reflect.Proxy#getProxyClass(java.lang.ClassLoader, java.lang.Class[])",
        "java.lang.reflect.Proxy#newProxyInstance(java.lang.ClassLoader, java.lang.Class[], java.lang.reflect.InvocationHandler)",
    },
}


@dataclass(frozen=True)
class ParsedFrame:
    class_name: str
    source_file: str
    line: int | None


@dataclass(frozen=True)
class NeuralSeed:
    path: str
    checksum: str
    total_calls: int


def canonicalize_neural_call_reports(raw_dir: str, seed_dir: str) -> NeuralSeed:
    """Validate agent-produced raw call files and write a canonical frozen seed."""
    raw_path = Path(raw_dir)
    if not raw_path.is_dir():
        raise ValueError(f"Neural dynamic-access raw output directory is missing: {raw_dir}")

    canonical: dict[str, dict[str, list[str]]] = {}
    for report_type in SUPPORTED_REPORT_TYPES:
        report_file = raw_path / f"{report_type}-calls.json"
        if not report_file.is_file():
            continue
        canonical[report_type] = _load_and_validate_raw_report(report_type, report_file)

    total_calls = sum(
        len(frames)
        for report in canonical.values()
        for frames in report.values()
    )
    if total_calls == 0:
        raise ValueError(
            "Neural dynamic-access preprocessing produced no supported runtime API call sites"
        )

    seed_path = Path(seed_dir)
    if seed_path.exists():
        shutil.rmtree(seed_path)
    seed_path.mkdir(parents=True, exist_ok=True)

    for report_type, report in sorted(canonical.items()):
        output_file = seed_path / f"{report_type}-calls.json"
        with output_file.open("w", encoding="utf-8") as handle:
            json.dump(report, handle, indent=2, ensure_ascii=False)
            handle.write("\n")

    checksum = compute_seed_checksum(str(seed_path))
    return NeuralSeed(path=str(seed_path), checksum=checksum, total_calls=total_calls)


def compute_seed_checksum(seed_dir: str) -> str:
    """Return a stable checksum for all canonical raw call files in a seed dir."""
    digest = hashlib.sha256()
    for path in sorted(Path(seed_dir).glob("*-calls.json")):
        digest.update(path.name.encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def write_neural_dynamic_access_coverage_report(
        coordinate: str,
        seed_dir: str,
        jacoco_report: str,
        output_path: str,
) -> dict[str, Any]:
    """Correlate frozen neural call files with JaCoCo and write coverage JSON."""
    seed_path = Path(seed_dir)
    if not seed_path.is_dir():
        raise ValueError(f"Neural dynamic-access seed directory is missing: {seed_dir}")

    covered_lines_by_source = _parse_jacoco_covered_lines(jacoco_report)
    call_sites_by_key: dict[str, dict[str, Any]] = {}
    for report_file in sorted(seed_path.glob("*-calls.json")):
        report_type = _report_type_from_path(report_file)
        if report_type not in SUPPORTED_REPORT_TYPES:
            continue
        raw_report = _load_and_validate_raw_report(report_type, report_file)
        for tracked_api, frames in raw_report.items():
            for frame in frames:
                parsed_frame = parse_frame(frame)
                key = "\0".join((report_type, tracked_api, frame))
                if key in call_sites_by_key:
                    continue
                covered = False
                if parsed_frame.line is not None:
                    covered_lines = covered_lines_by_source.get(
                        _source_key(parsed_frame.class_name, parsed_frame.source_file)
                    )
                    covered = covered_lines is not None and parsed_frame.line in covered_lines
                call_sites_by_key[key] = {
                    "metadataType": report_type,
                    "trackedApi": tracked_api,
                    "frame": frame,
                    "className": parsed_frame.class_name,
                    "sourceFile": parsed_frame.source_file,
                    "line": parsed_frame.line,
                    "covered": covered,
                }

    report = _build_coverage_report(coordinate, call_sites_by_key)
    output = Path(output_path)
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("w", encoding="utf-8") as handle:
        json.dump(report, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    return report


def dynamic_access_stats_from_coverage_report(report: dict[str, Any]) -> dict[str, Any]:
    """Return the stats/<group>/<artifact>/<version>/stats.json dynamicAccess shape."""
    totals = report.get("totals") or {}
    total_calls = int(totals.get("totalCalls", 0) or 0)
    covered_calls = int(totals.get("coveredCalls", 0) or 0)
    breakdown_totals: dict[str, set[str]] = {}
    breakdown_covered: dict[str, set[str]] = {}

    for class_entry in report.get("classes", []):
        for call_site in class_entry.get("callSites", []):
            metadata_type = str(call_site.get("metadataType") or "")
            key = "\0".join((
                metadata_type,
                str(call_site.get("trackedApi") or ""),
                str(call_site.get("frame") or ""),
            ))
            breakdown_totals.setdefault(metadata_type, set()).add(key)
            if bool(call_site.get("covered")):
                breakdown_covered.setdefault(metadata_type, set()).add(key)

    breakdown = {}
    for metadata_type in sorted(breakdown_totals):
        type_total = len(breakdown_totals[metadata_type])
        type_covered = len(breakdown_covered.get(metadata_type, set()))
        breakdown[metadata_type] = {
            "coverageRatio": _ratio(type_covered, type_total),
            "coveredCalls": type_covered,
            "totalCalls": type_total,
        }

    return {
        "breakdown": breakdown,
        "coverageRatio": _ratio(covered_calls, total_calls),
        "coveredCalls": covered_calls,
        "totalCalls": total_calls,
    }


def _load_and_validate_raw_report(report_type: str, path: Path) -> dict[str, list[str]]:
    with path.open("r", encoding="utf-8") as handle:
        payload = json.load(handle)
    if not isinstance(payload, dict):
        raise ValueError(f"{path} must contain a JSON object")

    supported_apis = TRACKED_APIS_BY_TYPE[report_type]
    canonical: dict[str, list[str]] = {}
    for tracked_api, frames in payload.items():
        if not isinstance(tracked_api, str) or not tracked_api:
            raise ValueError(f"{path} contains a non-string tracked API key")
        if tracked_api not in supported_apis:
            raise ValueError(f"{path} contains unsupported {report_type} API: {tracked_api}")
        if not isinstance(frames, list):
            raise ValueError(f"{path} value for {tracked_api} must be a list of stack frames")
        canonical_frames = []
        for frame in frames:
            if not isinstance(frame, str) or not frame:
                raise ValueError(f"{path} contains a non-string frame for {tracked_api}")
            parse_frame(frame)
            canonical_frames.append(frame)
        if canonical_frames:
            canonical[tracked_api] = sorted(set(canonical_frames))
    return dict(sorted(canonical.items()))


def parse_frame(frame: str) -> ParsedFrame:
    match = FRAME_PATTERN.match(frame)
    if match is None:
        raise ValueError(f"Invalid dynamic-access stack frame: {frame}")
    line = int(match.group(4)) if match.group(4) else None
    return ParsedFrame(
        class_name=match.group(1),
        source_file=match.group(3),
        line=line,
    )


def _parse_jacoco_covered_lines(jacoco_report: str) -> dict[str, set[int]]:
    report_path = Path(jacoco_report)
    if not report_path.is_file():
        raise FileNotFoundError(jacoco_report)
    root = ET.parse(report_path).getroot()
    covered_lines_by_source: dict[str, set[int]] = {}
    for package in root.findall("package"):
        package_name = package.get("name") or ""
        for source_file in package.findall("sourcefile"):
            source_name = source_file.get("name") or ""
            source_key = source_name if not package_name else f"{package_name}/{source_name}"
            covered_lines = set()
            for line in source_file.findall("line"):
                if int(line.get("ci") or 0) > 0:
                    covered_lines.add(int(line.get("nr") or 0))
            covered_lines_by_source[source_key] = covered_lines
    return covered_lines_by_source


def _report_type_from_path(path: Path) -> str:
    name = path.name
    if not name.endswith("-calls.json"):
        raise ValueError(f"Unsupported neural dynamic-access report filename: {name}")
    return name.removesuffix("-calls.json")


def _source_key(class_name: str, source_file: str) -> str:
    separator = class_name.rfind(".")
    if separator < 0:
        return source_file
    return f"{class_name[:separator].replace('.', '/')}/{source_file}"


def _build_coverage_report(coordinate: str, call_sites_by_key: dict[str, dict[str, Any]]) -> dict[str, Any]:
    classes: dict[str, list[dict[str, Any]]] = {}
    for call_site in call_sites_by_key.values():
        classes.setdefault(call_site["className"], []).append(call_site)

    class_entries = []
    for class_name, call_sites in sorted(classes.items()):
        sorted_call_sites = sorted(
            call_sites,
            key=lambda call_site: (
                call_site["metadataType"],
                call_site["trackedApi"],
                call_site["frame"],
            ),
        )
        covered_calls = sum(1 for call_site in sorted_call_sites if call_site["covered"])
        source_file = next(
            (call_site["sourceFile"] for call_site in sorted_call_sites if call_site.get("sourceFile")),
            None,
        )
        class_entries.append({
            "className": class_name,
            "sourceFile": source_file,
            "totalCalls": len(sorted_call_sites),
            "coveredCalls": covered_calls,
            "callSites": [
                {
                    "metadataType": call_site["metadataType"],
                    "trackedApi": call_site["trackedApi"],
                    "frame": call_site["frame"],
                    "line": call_site["line"],
                    "covered": call_site["covered"],
                }
                for call_site in sorted_call_sites
            ],
        })

    class_entries.sort(key=lambda entry: (-(entry["totalCalls"] - entry["coveredCalls"]), entry["className"]))
    total_calls = len(call_sites_by_key)
    covered_calls = sum(1 for call_site in call_sites_by_key.values() if call_site["covered"])
    return {
        "coordinate": coordinate,
        "hasDynamicAccess": total_calls > 0,
        "totals": {
            "totalCalls": total_calls,
            "coveredCalls": covered_calls,
        },
        "classes": class_entries,
    }


def _ratio(covered: int, total: int) -> float:
    if total == 0:
        return 1.0
    value = Decimal(covered) / Decimal(total)
    return float(value.quantize(Decimal("0.000001"), rounding=ROUND_HALF_UP))
