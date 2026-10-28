# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the run's token usage and cost (§FS-forge-run-metrics, §AR-forge-workflow-pipeline.4).

The records mirror one run whose `setup` state ran on Claude and whose `cover`
and `fix` states ran on the worker model.
"""
from __future__ import annotations

import os
import tempfile
import unittest
from typing import Any
from unittest import mock

from support import run_state

import publish  # noqa: E402
import run_tokens  # noqa: E402
from run_state import write_json  # noqa: E402
from utility_scripts.token_costs import calc_model_session_cost  # noqa: E402

WORKER: str = "openai-codex/gpt-5.6-luna"


def invocation(state: str, model: str, started_at: str, total: int, cached_read: int, cache_write: int,
               output: int, convention: str = "input-total-includes-cache") -> dict[str, Any]:
    """A Rhei accounting record of one agent invocation."""
    return {
        "state": state,
        "model": model,
        "started_at": started_at,
        "token_convention": convention,
        "tokens": {
            "input": {
                "total": {"value": total},
                "cached_read": {"value": cached_read},
                "cache_write": {"value": cache_write},
            },
            "output": {"total": {"value": output}, "cached_read": {"status": "unsupported"}},
        },
    }


# File names are content hashes, so their order is not the invocation order.
RECORDS: dict[str, dict[str, Any]] = {
    "19bb.json": invocation("fix", WORKER, "2026-10-27T23:14:35Z", 999572, 803328, 0, 2837),
    "3b0c.json": invocation("cover", WORKER, "2026-10-27T22:49:48Z", 478323, 409984, 0, 2431),
    "9735.json": invocation("setup", "opus", "2026-10-27T22:37:47Z", 562329, 512534, 49775, 4328),
}


class UsageTest(unittest.TestCase):

    def test_a_cache_write_counts_as_input_when_the_total_includes_the_cache(self) -> None:
        usage: run_tokens.Usage = run_tokens.usage_of(RECORDS["9735.json"])
        self.assertEqual((562329 - 512534, 512534, 4328), (usage.input, usage.cached, usage.output))

    def test_a_cache_write_is_added_to_input_when_the_total_excludes_the_cache(self) -> None:
        record: dict[str, Any] = invocation("setup", "opus", "", 20, 512534, 49775, 4328,
                                            convention=run_tokens.INPUT_EXCLUDES_CACHE)
        usage: run_tokens.Usage = run_tokens.usage_of(record)
        self.assertEqual((20 + 49775, 512534), (usage.input, usage.cached))


class RunTest(unittest.TestCase):

    def setUp(self) -> None:
        self.workspace: str = tempfile.mkdtemp()
        for name, record in RECORDS.items():
            write_json(os.path.join(self.workspace, run_tokens.ACCOUNTING_INVOCATIONS, name), record)

    def test_states_are_listed_in_invocation_order(self) -> None:
        self.assertEqual(["setup", "cover", "fix"], list(run_tokens.usage_by_state(self.workspace)))

    def test_the_totals_cover_every_input_token_of_every_state(self) -> None:
        usage: run_tokens.Usage = run_tokens.total_usage(self.workspace)
        self.assertEqual(562329 + 478323 + 999572, usage.input + usage.cached)
        self.assertEqual(4328 + 2431 + 2837, usage.output)

    def test_each_invocation_is_priced_at_its_own_model(self) -> None:
        expected: float = sum(
            calc_model_session_cost(model, usage.input, usage.cached, usage.output)
            for model, usage in (("opus", run_tokens.usage_of(RECORDS["9735.json"])),
                                 ("gpt-5.6-luna", run_tokens.usage_of(RECORDS["3b0c.json"])),
                                 ("gpt-5.6-luna", run_tokens.usage_of(RECORDS["19bb.json"]))))
        total: run_tokens.Usage = run_tokens.total_usage(self.workspace)
        at_worker_rates: float = calc_model_session_cost("gpt-5.6-luna", total.input, total.cached, total.output)
        self.assertAlmostEqual(expected, run_tokens.cost_usd(self.workspace), places=4)
        self.assertNotAlmostEqual(at_worker_rates, run_tokens.cost_usd(self.workspace), places=2)

    def test_the_run_metrics_and_the_per_state_table_follow_the_same_rule(self) -> None:
        state = run_state(skip_recorded=True, worker_agent=f"pi[high]:{WORKER}")
        with mock.patch.object(publish, "head", return_value="end"):
            metrics: dict[str, Any] = publish.run_metrics(self.workspace, state, publish.STATUS_SUCCESS)
        rows: list[dict[str, Any]] = publish.render(self.workspace, state, None)["token_usage"]
        total: run_tokens.Usage = run_tokens.total_usage(self.workspace)
        self.assertEqual(total.input, metrics["metrics"]["input_tokens_used"])
        self.assertEqual(total.cached, metrics["metrics"]["cached_input_tokens_used"])
        self.assertEqual(run_tokens.cost_usd(self.workspace), metrics["metrics"]["cost_usd"])
        self.assertEqual(total.input, sum(row["input"] for row in rows))
        self.assertEqual(total.cached, sum(row["cached"] for row in rows))
        self.assertEqual({"phase": "setup", "input": 562329 - 512534, "cached": 512534, "output": 4328}, rows[0])


if __name__ == "__main__":
    unittest.main()
