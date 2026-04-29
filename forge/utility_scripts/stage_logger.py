"""
Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
"""


def log_stage(stage: str, message: str, indent_level: int = 0) -> None:
    """Print a workflow log line that starts with the current stage."""
    indent = "  " * indent_level
    print(f"[{stage}] {indent}{message}")
