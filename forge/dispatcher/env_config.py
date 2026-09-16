# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Environment and CLI value parsing shared by dispatcher queues
(§AR-forge-dispatcher-decomposition, §FS-forge-run-requirements)."""

import argparse
import os
import re
import sys

from dispatcher.config import MAX_PARALLELISM, REVIEW_PERIOD_SUFFIX_SECONDS


def validate_parallelism(value: str) -> int:
    """Parse and validate the allowed forge_metadata parallelism."""
    parsed = int(value)
    if parsed < 1 or parsed > MAX_PARALLELISM:
        raise argparse.ArgumentTypeError(
            f"parallelism must be between 1 and {MAX_PARALLELISM}"
        )
    return parsed


def validate_non_negative_integer(value: str) -> int:
    """Parse and validate a non-negative integer argument."""
    parsed = int(value)
    if parsed < 0:
        raise argparse.ArgumentTypeError("value must be greater than or equal to 0")
    return parsed


def validate_review_period(value: str) -> int:
    """Parse a positive review period in seconds, supporting s/m/h/d suffixes."""
    normalized = value.strip().lower()
    match = re.fullmatch(r"(\d+)([smhd]?)", normalized)
    if match is None:
        raise argparse.ArgumentTypeError(
            "period must be a positive integer in seconds or use s/m/h/d suffixes"
        )

    amount = int(match.group(1))
    if amount < 1:
        raise argparse.ArgumentTypeError("period must be greater than 0")

    suffix = match.group(2) or "s"
    return amount * REVIEW_PERIOD_SUFFIX_SECONDS[suffix]


def get_env_non_negative_int(name: str, default: int) -> int:
    """Read a non-negative integer environment variable."""
    raw_value = os.environ.get(name)
    if raw_value is None or raw_value == "":
        return default
    try:
        value = int(raw_value)
    except ValueError:
        print(f"ERROR: {name} must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    if value < 0:
        print(f"ERROR: {name} must be a non-negative integer.", file=sys.stderr)
        sys.exit(1)
    return value


def get_env_parallelism(name: str, default: int) -> int:
    """Read an optional parallelism environment variable."""
    raw_value = os.environ.get(name)
    if raw_value is None or raw_value == "":
        return default
    try:
        return validate_parallelism(raw_value)
    except (ValueError, argparse.ArgumentTypeError) as exc:
        print(f"ERROR: {name} {exc}", file=sys.stderr)
        sys.exit(1)


def get_env_zero_one_bool(name: str, default: bool) -> bool:
    """Read an optional 0-or-1 boolean environment variable."""
    raw_value = os.environ.get(name)
    if raw_value is None or raw_value == "":
        return default
    if raw_value not in {"0", "1"}:
        print(f"ERROR: {name} must be 0 or 1.", file=sys.stderr)
        sys.exit(1)
    return raw_value == "1"
