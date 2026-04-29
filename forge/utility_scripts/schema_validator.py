# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
Helper module for validating Metadata Forge metric output, strategy and benchmark configuration files.
Validates a file against the JSON Schema at `schemas/`.
"""

import argparse
import json
import os
import sys
from typing import Any, Dict

from jsonschema import Draft202012Validator, FormatChecker

SCHEMA_NAME_MAP = {
    "run_metrics_output": "run_metrics_output_schema.json",
    "benchmark_run_metrics": "benchmark_run_metrics_schema.json",
    "benchmark_suite": "benchmark_suite_schema.json",
    "strategy": "strategy_schema.json",
}


def _get_schema_path(schema_name: str) -> str:
    if schema_name not in SCHEMA_NAME_MAP:
        allowed = ", ".join(sorted(set(SCHEMA_NAME_MAP)))
        raise ValueError(f"ERROR: Unknown schema name '{schema_name}'. Allowed values: {allowed}")

    repo_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    schema_file = SCHEMA_NAME_MAP[schema_name]
    if schema_name == "run_metrics_output":
        return os.path.abspath(os.path.join(repo_root, os.pardir, "stats", "schemas", schema_file))
    return os.path.join(repo_root, "schemas", schema_file)


def _load_schema(schema_name: str) -> Dict[str, Any]:
    schema_path = _get_schema_path(schema_name)
    with open(schema_path, "r", encoding="utf-8") as file:
        return json.load(file)


def _validate_against_schema(data, schema_name: str):
    schema = _load_schema(schema_name)
    validator = Draft202012Validator(schema, format_checker=FormatChecker())
    validator.validate(data)


def validate_json_file(file_path: str, schema_name: str):
    """Validate a JSON file against the schema identified by schema_name."""
    if not os.path.isfile(file_path):
        raise FileNotFoundError(f"JSON file not found: {file_path}")

    with open(file_path, "r", encoding="utf-8") as file:
        data = json.load(file)

    _validate_against_schema(data, schema_name)


def validate_run_metrics(file_path: str):
    """Validate against `stats/schemas/run_metrics_output_schema.json`."""
    validate_json_file(file_path, "run_metrics_output")


def validate_benchmark_run_metrics(file_path: str):
    """Validate against `schemas/benchmark_run_metrics_schema.json`."""
    validate_json_file(file_path, "benchmark_run_metrics")


def validate_benchmark_suite(file_path: str):
    """Validate against `schemas/benchmark_suite_schema.json`."""
    validate_json_file(file_path, "benchmark_suite")


def validate_strategy(file_path: str):
    """Validate against `schemas/strategy_schema.json`."""
    validate_json_file(file_path, "strategy")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="schema_validator.py",
        description="Validate a JSON file against one of Metadata Forge schemas.",
        epilog=(
            "Example:\n"
            "  python3 utility_scripts/schema_validator.py run_metrics_output output/results.json"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument(
        "schema_name",
        choices=["run_metrics_output", "benchmark_run_metrics", "benchmark_suite", "strategy"],
        help="Schema alias to validate against.",
    )
    parser.add_argument("file_path", help="Path to the JSON file to validate.")
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args(sys.argv[1:])

    validate_json_file(args.file_path, args.schema_name)
    print(f"Validation successful [{args.schema_name}]: {args.file_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
