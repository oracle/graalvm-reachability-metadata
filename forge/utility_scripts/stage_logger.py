# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import sys
from typing import TextIO


ANSI_RESET = "\033[0m"
ANSI_BOLD_GREEN = "\033[1;32m"
ANSI_BOLD_RED = "\033[1;31m"
BANNER_WIDTH = 88


def log_stage(stage: str, message: str, indent_level: int = 0) -> None:
    """Print a workflow log line that starts with the current stage."""
    indent = "  " * indent_level
    print(f"[{stage}] {indent}{message}")


def log_status_banner(title: str, message: str, color: str, file: TextIO | None = None) -> None:
    """Print a highly visible colored multiline status banner."""
    output = sys.stdout if file is None else file
    delimiter = "=" * BANNER_WIDTH
    title_line = f" {title.upper()} ".center(BANNER_WIDTH, "=")
    print(
        f"\n{color}{delimiter}\n{title_line}\n{delimiter}\n{message}\n{delimiter}{ANSI_RESET}",
        file=output,
    )


def log_success_banner(message: str, file: TextIO | None = None) -> None:
    """Print a green success banner."""
    log_status_banner("SUCCESS", message, ANSI_BOLD_GREEN, file=file)


def log_failure_banner(message: str, file: TextIO | None = None) -> None:
    """Print a red failure banner."""
    log_status_banner("FAILED", message, ANSI_BOLD_RED, file=file)
