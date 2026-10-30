# Copyright and related rights waived via CC0. See /LICENSE.
"""Exercise admission and recovery. §FS-tests.3.6, §AR-layered-tests"""

from __future__ import annotations

import copy
import json
import os
import sys
import tempfile
import unittest
from datetime import timedelta
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from layered_queue.cli import create_queue, discover, main, shard_environment
from layered_queue.dispatcher import LIMIT, now, reconcile, run_name
from layered_queue.github import load_state


class FakeGitHub:
    def __init__(self, count: int = 160) -> None:
        self.source: dict = {"id": 10, "head_sha": "a" * 40, "head_branch": "branch",
                             "created_at": now().isoformat(), "status": "completed", "conclusion": "success",
                             "path": ".github/workflows/layered-tests.yml", "html_url": "https://example/sweep"}
        self.state: dict = {"version": 1, "sweep": 10, "created": now().isoformat(), "workflow_ref": "branch",
                           "shards": [{"lane": "Shared" if i < 32 else "Dedicated", "coordinates": str(i)}
                                      for i in range(count)]}
        self.check: dict = {"id": 7, "external_id": "layered-sweep:10", "output": {"text": json.dumps(self.state)}}
        self.runs: dict[int, dict] = {}
        self.jobs: dict[int, list[dict]] = {}
        self.dispatches: list[int] = []
        self.cancellations: list[int] = []
        self.lose_response: bool = False
        self.hide_runs: bool = False
        self.branch_sha: str = self.source["head_sha"]
        self.annotations: list[dict] = []

    def request(self, method: str, path: str, data: dict | None = None) -> dict:
        if path == "check-runs/7" and method == "PATCH":
            self.check.update(copy.deepcopy(data))
            return self.check
        if path == "check-runs" and method == "POST":
            self.check.update(copy.deepcopy(data))
            return self.check
        if path == "commits/branch":
            return {"sha": self.branch_sha}
        if path == "check-runs/100/annotations?per_page=100":
            return self.annotations
        if path.endswith("/dispatches"):
            index: int = int(data["inputs"]["shard"])
            run_id: int = len(self.runs) + 100
            self.runs[run_id] = {"id": run_id, "status": "queued", "conclusion": None,
                                 "created_at": now().isoformat(), "html_url": f"https://example/{run_id}",
                                 "display_title": run_name(10, index, data["inputs"]["dispatch-token"])}
            self.jobs[run_id] = [{"name": "shard / Execute shard", "status": "queued", "runner_id": 0,
                                  "started_at": now().isoformat(), "steps": []}]
            self.dispatches.append(index)
            if self.lose_response:
                self.lose_response = False
                raise TimeoutError("Response lost after GitHub accepted the dispatch")
            return {"workflow_run_id": run_id, "html_url": self.runs[run_id]["html_url"]}
        if path.endswith("/cancel"):
            run_id = int(path.split("/")[-2])
            self.cancellations.append(run_id)
            self.runs[run_id].update(status="completed", conclusion="cancelled")
            return {}
        if path.startswith("actions/runs/"):
            run_id = int(path.split("/")[-1])
            return self.source if run_id == 10 else self.runs[run_id]
        raise AssertionError((method, path, data))

    def pages(self, path: str, key: str, **parameters: object) -> list[dict]:
        if key == "workflow_runs":
            return [] if self.hide_runs else list(self.runs.values())
        if key == "check_runs":
            return [self.check]
        if key == "jobs":
            return self.jobs[int(path.split("/")[-2])]
        raise AssertionError((path, key))

    def pump(self, cancel: bool = False) -> None:
        # Restart from the remote record every time, rather than retaining process memory.
        reconcile(self, self.check, load_state(self.check), self.source, cancel)

    def finish(self, run_id: int, conclusion: str = "success") -> None:
        # The test is finished while its workflow's refill job is still running.
        self.runs[run_id]["status"] = "in_progress"
        self.jobs[run_id][0].update(status="completed", conclusion=conclusion, runner_id=42)


class LayeredQueueTests(unittest.TestCase):
    def test_refills_from_shared_queue_while_one_slow_shard_remains(self) -> None:
        api = FakeGitHub()
        api.pump()
        self.assertEqual(len(api.dispatches), LIMIT)
        slow: int = min(api.runs)
        while len(api.dispatches) < 160:
            active: list[int] = [run for run, jobs in api.jobs.items() if jobs[0]["status"] != "completed"]
            self.assertLessEqual(len(active), LIMIT)
            api.finish(max(active))
            api.pump()
            self.assertEqual(api.jobs[slow][0]["status"], "queued")
        for run_id in api.runs:
            api.finish(run_id)
        api.pump()
        self.assertEqual(sorted(api.dispatches), list(range(160)))
        self.assertEqual(api.check["conclusion"], "success")

    def test_no_refill_when_all_slots_are_queued(self) -> None:
        api = FakeGitHub()
        api.pump()
        api.pump()
        self.assertEqual(len(api.dispatches), LIMIT)
        self.assertEqual(api.check["status"], "in_progress")

    def test_failed_test_releases_slot_without_retrying_it(self) -> None:
        api = FakeGitHub(33)
        api.pump()
        api.finish(100, "failure")
        api.pump()
        self.assertEqual(api.dispatches, list(range(33)))
        for run in list(api.runs)[1:]:
            api.finish(run)
        api.pump()
        self.assertEqual(api.check["conclusion"], "failure")

    def test_lost_dispatch_response_is_recovered_without_duplicate(self) -> None:
        api = FakeGitHub(2)
        api.lose_response = True
        with self.assertRaises(TimeoutError):
            api.pump()
        self.assertIn("token", load_state(api.check)["shards"][0])
        api.pump()
        self.assertEqual(api.dispatches, [0, 1])
        self.assertEqual(load_state(api.check)["shards"][0]["run"], 100)

    def test_ambiguous_submission_pauses_until_visible(self) -> None:
        api = FakeGitHub(2)
        api.lose_response = True
        with self.assertRaises(TimeoutError):
            api.pump()
        api.hide_runs = True
        api.pump()
        self.assertEqual(api.dispatches, [0])
        self.assertEqual(api.check["status"], "in_progress")
        api.hide_runs = False
        api.pump()
        self.assertEqual(api.dispatches, [0, 1])

    def test_queue_expiry_retries_only_work_that_never_started(self) -> None:
        for assigned in (False, True):
            with self.subTest(assigned=assigned):
                api = FakeGitHub(1)
                api.pump()
                api.runs[100].update(status="completed", conclusion="timed_out",
                                     created_at=(now() - timedelta(hours=25)).isoformat())
                api.jobs[100][0].update(status="completed", conclusion="timed_out")
                if assigned:
                    api.jobs[100][0]["runner_id"] = 42
                api.pump()
                self.assertEqual(api.dispatches, [0] if assigned else [0, 0])
                self.assertEqual(api.check["status"], "completed" if assigned else "in_progress")

    def test_cancellation_stops_admission_and_cancels_outstanding_runs(self) -> None:
        api = FakeGitHub()
        api.pump()
        api.finish(130, "cancelled")
        api.pump()
        self.assertEqual(len(api.dispatches), LIMIT)
        self.assertEqual(set(api.cancellations), set(api.runs) - {130})
        self.assertEqual(api.check["conclusion"], "cancelled")

    def test_queue_expiry_cancellation_is_distinguished_from_manual_cancellation(self) -> None:
        for expired in (False, True):
            with self.subTest(expired=expired):
                api = FakeGitHub(1)
                api.pump()
                api.runs[100].update(status="in_progress",
                                     created_at=(now() - timedelta(hours=25)).isoformat())
                api.jobs[100][0].update(status="completed", conclusion="cancelled",
                                        check_run_url="https://api.github.com/repos/o/r/check-runs/100")
                # The refill job starting does not mean the native test acquired a runner.
                api.jobs[100].append({"name": "refill / dispatch", "status": "in_progress", "runner_id": 42})
                if expired:
                    api.annotations = [{"message": "The job was not acquired by Runner of type hosted even after multiple attempts"}]
                api.pump()
                self.assertEqual(api.dispatches, [0, 0] if expired else [0])
                self.assertEqual(api.check["status"], "in_progress" if expired else "completed")

    def test_explicit_cancellation_during_ambiguous_dispatch_recovers_then_cancels(self) -> None:
        api = FakeGitHub(1)
        api.lose_response = True
        with self.assertRaises(TimeoutError):
            api.pump()
        api.hide_runs = True
        api.pump(cancel=True)
        self.assertFalse(load_state(api.check)["cleanup_complete"])
        api.hide_runs = False
        api.pump()
        self.assertEqual(api.cancellations, [100])
        self.assertTrue(load_state(api.check)["cleanup_complete"])

    def test_workflow_revision_change_fails_incomplete_sweep_and_cancels_workers(self) -> None:
        api = FakeGitHub()
        api.pump()
        api.branch_sha = "b" * 40
        api.pump()
        self.assertEqual(api.check["conclusion"], "failure")
        self.assertEqual(len(api.cancellations), LIMIT)
        self.assertEqual(len(api.dispatches), LIMIT)

    def test_completed_success_is_not_rewritten_when_workflow_branch_moves(self) -> None:
        api = FakeGitHub(1)
        api.pump()
        api.finish(100)
        api.pump()
        api.branch_sha = "b" * 40
        api.pump()
        self.assertEqual(api.check["conclusion"], "success")

    def test_missing_test_job_cannot_make_a_green_sweep(self) -> None:
        api = FakeGitHub(1)
        api.pump()
        api.jobs[100] = []
        api.runs[100].update(status="completed", conclusion="success")
        api.pump()
        self.assertEqual(api.check["conclusion"], "failure")

    def test_deadline_reports_untested_work_instead_of_passing(self) -> None:
        api = FakeGitHub()
        state: dict = load_state(api.check)
        state["created"] = (now() - timedelta(days=8)).isoformat()
        api.check["output"]["text"] = json.dumps(state)
        api.pump()
        self.assertEqual(api.dispatches, [])
        self.assertEqual(api.check["conclusion"], "failure")


class LayeredQueueConfigurationTests(unittest.TestCase):
    def test_callback_coalesces_only_after_the_previous_refill_is_persisted(self) -> None:
        for refill_complete in (False, True):
            with self.subTest(refill_complete=refill_complete):
                api = FakeGitHub(33)
                api.pump()
                api.finish(100)
                if refill_complete:
                    api.pump()
                else:
                    # Simulate a controller dying between recording the outcome and refilling.
                    state: dict = load_state(api.check)
                    state["shards"][0]["outcome"] = "success"
                    api.check["output"]["text"] = json.dumps(state)
                with patch("layered_queue.cli.GitHub", return_value=api), \
                        patch("sys.argv", ["cli", "reconcile"]), \
                        patch.dict(os.environ, SWEEP_RUN_ID="10", COMPLETED_RUN_ID="100"):
                    main()
                self.assertEqual(api.dispatches, list(range(33)))

    def test_failed_base_preparation_keeps_dedicated_coverage(self) -> None:
        api = FakeGitHub(2)
        plan: dict = {"include": [{"lane": {"name": lane}, "coordinates": "demo:lib:1"}
                                 for lane in ("Shared", "Dedicated")]}
        with patch.dict(os.environ, COORDINATE_MATRIX=json.dumps(plan), TEST_REVISION="revision",
                        NATIVE_IMAGE_MODE="current-defaults", GRAALVM_ARTIFACT="graalvm", BASE_ARTIFACT=""):
            create_queue(api, api.source, 10)
        api.pump()
        self.assertEqual(api.dispatches, [1])
        api.finish(100)
        api.pump()
        self.assertEqual(api.check["conclusion"], "failure")

    def test_recovery_discovers_pending_and_interrupted_cancellation_only(self) -> None:
        api = FakeGitHub(1)
        original_pages = api.pages
        def pages(path: str, key: str, **parameters: object) -> list[dict]:
            if key == "artifacts":
                return [{"workflow_run": {"id": 10}, "expired": False}]
            return original_pages(path, key, **parameters)
        api.pages = pages
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "output"
            with patch.dict(os.environ, GITHUB_OUTPUT=str(path)):
                for status, stop, cleanup, expected in [
                    ("in_progress", None, False, True),
                    ("completed", None, False, False),
                    ("completed", "cancelled", False, True),
                    ("completed", "cancelled", True, False),
                ]:
                    with self.subTest(status=status, stop=stop, cleanup=cleanup):
                        api.check["status"] = status
                        state: dict = load_state(api.check)
                        state.update(stop=stop, cleanup_complete=cleanup)
                        api.check["output"]["text"] = json.dumps(state)
                        path.write_text("")
                        discover(api)
                        self.assertIn(f"found={str(expected).lower()}", path.read_text())

    def test_worker_loads_exact_artifacts_and_rejects_duplicate_or_wrong_token(self) -> None:
        api = FakeGitHub(1)
        state: dict = load_state(api.check)
        state.update(revision="revision", mode="current-defaults", graalvm_artifact="jdk-10-1", base_artifact="base-10-1")
        api.check["output"]["text"] = json.dumps(state)
        api.pump()
        token: str = load_state(api.check)["shards"][0]["token"]
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "env"
            with patch.dict(os.environ, GITHUB_ENV=str(path), GITHUB_SHA=api.source["head_sha"],
                            GITHUB_RUN_ID="100", SHARD_INDEX="0", DISPATCH_TOKEN=token):
                shard_environment(api, 10)
                self.assertIn("GRAALVM_ARTIFACT=jdk-10-1", path.read_text())
                self.assertIn("shared-layered-test-exclusions.txt", path.read_text())
                with patch.dict(os.environ, GITHUB_RUN_ID="101"):
                    with self.assertRaises(ValueError):
                        shard_environment(api, 10)
                with patch.dict(os.environ, DISPATCH_TOKEN="wrong"):
                    with self.assertRaises(ValueError):
                        shard_environment(api, 10)


if __name__ == "__main__":
    unittest.main()
