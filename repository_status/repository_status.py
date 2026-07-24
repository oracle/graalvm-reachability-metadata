#!/usr/bin/env python3
"""Report repository issue progress for humans or agents. §FS-repository-status-report"""

from __future__ import annotations

import argparse
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Sequence


SCRIPT_DIRECTORY: Path = Path(__file__).resolve().parent
REPOSITORY_ROOT: Path = SCRIPT_DIRECTORY.parent
if str(REPOSITORY_ROOT) not in sys.path:
    sys.path.insert(0, str(REPOSITORY_ROOT))

from repository_status.github_source import fetch_snapshot  # noqa: E402
from repository_status.measurements import build_report  # noqa: E402
from repository_status.model import Policy, StatusReportError, load_policy  # noqa: E402
from repository_status.renderers import render_html, render_json  # noqa: E402


DEFAULT_POLICY: Path = SCRIPT_DIRECTORY / "policy.json"
DEFAULT_HUMAN_OUTPUT: Path = REPOSITORY_ROOT / "build/reports/repository-status.html"


def positive_integer(value: str) -> int:
    """Parse an argparse positive integer."""
    try:
        parsed_value: int = int(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("must be an integer") from exc
    if parsed_value <= 0:
        raise argparse.ArgumentTypeError("must be greater than zero")
    return parsed_value


def create_parser() -> argparse.ArgumentParser:
    """Create the command-line contract."""
    parser: argparse.ArgumentParser = argparse.ArgumentParser(
        description=(
            "Measure unresolved issue priority, duration, age debt, project state, "
            "and recent issue flow."
        )
    )
    mode: argparse._MutuallyExclusiveGroup = parser.add_mutually_exclusive_group(
        required=True
    )
    mode.add_argument(
        "--human",
        action="store_true",
        help="write the self-contained HTML dashboard",
    )
    mode.add_argument(
        "--agent",
        action="store_true",
        help="write the schema-versioned JSON report to stdout",
    )
    parser.add_argument(
        "--output",
        help=(
            "HTML destination for --human; defaults to "
            "build/reports/repository-status.html, use '-' for stdout"
        ),
    )
    parser.add_argument(
        "--window-days",
        type=positive_integer,
        help="recent-flow window; defaults to the policy value",
    )
    parser.add_argument(
        "--issue-limit",
        type=positive_integer,
        default=10_000,
        help="fail rather than silently truncate when either GitHub query reaches this limit",
    )
    parser.add_argument(
        "--policy",
        type=Path,
        default=DEFAULT_POLICY,
        help=f"measurement policy path (default: {DEFAULT_POLICY})",
    )
    return parser


def run(arguments: Sequence[str] | None = None) -> int:
    """Fetch, measure, and render one status report."""
    parser: argparse.ArgumentParser = create_parser()
    options: argparse.Namespace = parser.parse_args(arguments)
    if options.agent and options.output is not None:
        parser.error("--output is only valid with --human; --agent always writes JSON to stdout")

    try:
        policy: Policy = load_policy(options.policy)
        window_days: int = options.window_days or policy.default_window_days
        generated_at: datetime = datetime.now(timezone.utc)
        open_issues, recently_closed_issues = fetch_snapshot(
            policy=policy,
            generated_at=generated_at,
            window_days=window_days,
            issue_limit=options.issue_limit,
        )
        report = build_report(
            policy=policy,
            open_issues=open_issues,
            recently_closed_issues=recently_closed_issues,
            generated_at=generated_at,
            window_days=window_days,
        )
        if options.agent:
            sys.stdout.write(render_json(report))
            return 0

        html_report: str = render_html(report)
        output_value: str = options.output or str(DEFAULT_HUMAN_OUTPUT)
        if output_value == "-":
            sys.stdout.write(html_report)
            return 0
        output_path: Path = Path(output_value).expanduser().resolve()
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(html_report, encoding="utf-8")
        print(f"Wrote repository status report to {output_path}")
        return 0
    except (StatusReportError, OSError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(run())
