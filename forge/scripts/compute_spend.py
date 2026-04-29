#!/usr/bin/env python3
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Summarize recorded AI workflow spend from metrics JSON files."""

from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterable


DEFAULT_INPUTS = ("script_run_metrics", "benchmark_run_metrics")


@dataclass(frozen=True)
class RunCost:
    path: Path
    timestamp: str | None
    library: str
    status: str
    model: str
    strategy: str
    cost: Decimal

    @property
    def month(self) -> str:
        if not self.timestamp:
            return "(missing)"

        try:
            return datetime.fromisoformat(self.timestamp.replace("Z", "+00:00")).strftime("%Y-%m")
        except ValueError:
            return "(invalid)"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Compute total cost_usd from metadata-forge metrics JSON files.",
    )
    parser.add_argument(
        "paths",
        nargs="*",
        type=Path,
        help=(
            "Files or directories to scan. Defaults to existing "
            "script_run_metrics and benchmark_run_metrics directories."
        ),
    )
    parser.add_argument(
        "--top",
        type=int,
        default=10,
        help="Number of most expensive runs to show. Use 0 to hide this section.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit a machine-readable JSON summary instead of tables.",
    )
    return parser.parse_args()


def input_paths(paths: list[Path]) -> list[Path]:
    if paths:
        return paths

    return [Path(path) for path in DEFAULT_INPUTS if Path(path).exists()]


def json_files(paths: Iterable[Path]) -> list[Path]:
    files: list[Path] = []
    for path in paths:
        if path.is_dir():
            files.extend(sorted(path.rglob("*.json")))
        elif path.is_file() and path.suffix == ".json":
            files.append(path)
        else:
            print(f"warning: skipping missing or non-JSON path: {path}", file=sys.stderr)

    return sorted(set(files))


def metric_records(value: Any) -> Iterable[dict[str, Any]]:
    if isinstance(value, dict):
        metrics = value.get("metrics")
        if isinstance(metrics, dict) and "cost_usd" in metrics:
            yield value

        for child in value.values():
            yield from metric_records(child)
    elif isinstance(value, list):
        for item in value:
            yield from metric_records(item)


def decimal_value(value: Any) -> Decimal | None:
    if value is None:
        return None

    try:
        return Decimal(str(value))
    except (InvalidOperation, ValueError):
        return None


def load_costs(files: Iterable[Path]) -> tuple[list[RunCost], int]:
    costs: list[RunCost] = []
    skipped = 0

    for path in files:
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            print(f"warning: failed to read {path}: {exc}", file=sys.stderr)
            skipped += 1
            continue

        for record in metric_records(data):
            cost = decimal_value(record.get("metrics", {}).get("cost_usd"))
            if cost is None:
                skipped += 1
                continue

            costs.append(
                RunCost(
                    path=path,
                    timestamp=string_or_none(record.get("timestamp")),
                    library=string_or_default(record.get("library"), "(unknown library)"),
                    status=string_or_default(record.get("status"), "(unknown status)"),
                    model=string_or_default(record.get("model"), "(unknown model)"),
                    strategy=string_or_default(record.get("strategy_name"), "(unknown strategy)"),
                    cost=cost,
                )
            )

    return costs, skipped


def string_or_none(value: Any) -> str | None:
    return value if isinstance(value, str) and value else None


def string_or_default(value: Any, default: str) -> str:
    return value if isinstance(value, str) and value else default


def group_sum(costs: Iterable[RunCost], attr: str) -> list[tuple[str, int, Decimal]]:
    totals: dict[str, Decimal] = defaultdict(Decimal)
    counts: dict[str, int] = defaultdict(int)

    for run_cost in costs:
        key = str(getattr(run_cost, attr))
        totals[key] += run_cost.cost
        counts[key] += 1

    return sorted(
        ((key, counts[key], total) for key, total in totals.items()),
        key=lambda item: (-item[2], item[0]),
    )


def money(value: Decimal) -> str:
    return f"${value.quantize(Decimal('0.01'))}"


def print_group(title: str, rows: list[tuple[str, int, Decimal]]) -> None:
    print(f"\n{title}")
    print("-" * len(title))
    print(f"{'cost':>12}  {'runs':>5}  name")
    for name, count, total in rows:
        print(f"{money(total):>12}  {count:>5}  {name}")


def print_human_summary(costs: list[RunCost], files: list[Path], skipped: int, top: int) -> None:
    total = sum((run_cost.cost for run_cost in costs), Decimal("0"))

    print(f"Total spent: {money(total)}")
    print(f"Runs counted: {len(costs)}")
    print(f"Files scanned: {len(files)}")
    if skipped:
        print(f"Skipped entries/files: {skipped}")

    print_group("By file", group_sum(costs, "path"))
    print_group("By status", group_sum(costs, "status"))
    print_group("By model", group_sum(costs, "model"))
    print_group("By month", group_sum(costs, "month"))

    if top > 0:
        top_runs = sorted(costs, key=lambda run_cost: run_cost.cost, reverse=True)[:top]
        print(f"\nTop {len(top_runs)} runs")
        print("-" * (len(str(len(top_runs))) + 9))
        print(f"{'cost':>12}  {'date':<10}  {'status':<25}  library")
        for run_cost in top_runs:
            date = (run_cost.timestamp or "(missing)")[:10]
            print(
                f"{money(run_cost.cost):>12}  {date:<10}  "
                f"{run_cost.status[:25]:<25}  {run_cost.library}"
            )


def json_summary(costs: list[RunCost], files: list[Path], skipped: int, top: int) -> dict[str, Any]:
    def group(attr: str) -> list[dict[str, Any]]:
        return [
            {"name": name, "runs": count, "cost_usd": float(total)}
            for name, count, total in group_sum(costs, attr)
        ]

    top_runs = sorted(costs, key=lambda run_cost: run_cost.cost, reverse=True)[: max(top, 0)]
    return {
        "total_cost_usd": float(sum((run_cost.cost for run_cost in costs), Decimal("0"))),
        "runs_counted": len(costs),
        "files_scanned": [str(path) for path in files],
        "skipped_entries_or_files": skipped,
        "by_file": group("path"),
        "by_status": group("status"),
        "by_model": group("model"),
        "by_month": group("month"),
        "top_runs": [
            {
                "cost_usd": float(run_cost.cost),
                "timestamp": run_cost.timestamp,
                "library": run_cost.library,
                "status": run_cost.status,
                "model": run_cost.model,
                "strategy": run_cost.strategy,
                "file": str(run_cost.path),
            }
            for run_cost in top_runs
        ],
    }


def main() -> int:
    args = parse_args()
    paths = input_paths(args.paths)
    files = json_files(paths)

    if not files:
        print("No metrics JSON files found.", file=sys.stderr)
        return 1

    costs, skipped = load_costs(files)
    if args.json:
        print(json.dumps(json_summary(costs, files, skipped, args.top), indent=2, sort_keys=True))
    else:
        print_human_summary(costs, files, skipped, args.top)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
