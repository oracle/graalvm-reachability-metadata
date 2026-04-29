# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.


def log_stage(stage: str, message: str, indent_level: int = 0) -> None:
    """Print a workflow log line that starts with the current stage."""
    indent = "  " * indent_level
    print(f"[{stage}] {indent}{message}")
