# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Run log readers shared by review and failure analysis
(§AR-forge-dispatcher-decomposition, §FS-durable-generation-logs)."""


import json
import os
from ai_workflows.agents.codex_agent import extract_codex_token_usage
from utility_scripts.token_costs import calc_model_session_cost

import subprocess
import sys

def read_log_tail(log_path: str, max_lines: int = 20) -> str:
    """Return the last lines from a log file when it exists."""
    if not os.path.isfile(log_path):
        return ""
    with open(log_path, "r", encoding="utf-8") as log_file:
        return "\n".join(log_file.read().strip().splitlines()[-max_lines:])


def read_log_text(log_path: str) -> str:
    """Return the complete text from a log file when it exists."""
    if not os.path.isfile(log_path):
        return ""
    with open(log_path, "r", encoding="utf-8") as log_file:
        return log_file.read().strip()


def extract_codex_final_message(log_path: str) -> str:
    """Return the final assistant message from a Codex JSONL log."""
    if not os.path.isfile(log_path):
        return ""

    final_message = ""
    with open(log_path, "r", encoding="utf-8") as log_file:
        for line in log_file:
            line = line.strip()
            if not line:
                continue
            try:
                payload = json.loads(line)
            except json.JSONDecodeError:
                continue
            if payload.get("type") != "item.completed":
                continue
            item = payload.get("item", {})
            if item.get("type") == "agent_message":
                final_message = item.get("text", "") or final_message
    return final_message.strip()


def extract_codex_token_usage_summary(log_path: str, model_name: str | None = None) -> str:
    """Return a human-readable Codex token-usage line from a JSONL log, or '' when unavailable.

    Includes the session cost derived from `model_name`'s per-token rates.
    """
    if not os.path.isfile(log_path):
        return ""
    with open(log_path, "r", encoding="utf-8") as log_file:
        usage = extract_codex_token_usage(log_file.read())
    if usage is None:
        return ""
    input_tokens, cached_input_tokens, output_tokens = usage
    cost_usd = calc_model_session_cost(model_name, input_tokens, cached_input_tokens, output_tokens)
    return (
        f"input={input_tokens} cached_input={cached_input_tokens} output={output_tokens} "
        f"cost=${cost_usd:.4f}"
    )


def output_tail(output: str | None, max_lines: int = 80) -> str:
    """Return the final lines from command output."""
    if not output:
        return ""
    return "\n".join(output.strip().splitlines()[-max_lines:])


def run_checked_command(
        command: list[str],
        cwd: str,
        error_message: str,
) -> subprocess.CompletedProcess:
    """Run a command and report its captured output on failure."""
    try:
        return subprocess.run(
            command,
            cwd=cwd,
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
        )
    except subprocess.CalledProcessError as exc:
        tail = output_tail(exc.stdout)
        print(f"ERROR: {error_message}" + (f":\n{tail}" if tail else ""), file=sys.stderr)
        raise
