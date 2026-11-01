# Copyright and related rights waived via CC0. See /LICENSE.
"""Refill the shared queue after any shard finishes. §FS-tests.3.6, §AR-layered-tests"""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from uuid import uuid4

from layered_queue.github import GitHub, save

LIMIT = 32
WORKFLOW = "layered-tests.yml"


def now() -> datetime:
    return datetime.now(timezone.utc)


def run_name(sweep: int, index: int, token: str) -> str:
    return f"Layered shard {sweep} {index} {token}"


def execution_job(jobs: list[dict]) -> dict | None:
    return next((job for job in jobs if job["name"].endswith("/ Execute shard")), None)


def queue_expired(api: GitHub, run: dict, job: dict | None) -> bool:
    # API 'started_at' may be set while queued. Runner assignment/steps prove execution.
    if not job or job["status"] != "completed" or job.get("runner_id"):
        return False
    if any(step.get("started_at") for step in job.get("steps", [])):
        return False
    age: timedelta = now() - datetime.fromisoformat(run["created_at"].replace("Z", "+00:00"))
    if age < timedelta(hours=24):
        return False
    if job.get("conclusion") in {"timed_out", "startup_failure", "failure"}:
        return True
    if job.get("conclusion") == "cancelled" and job.get("check_run_url"):
        # Automatic queue cancellation and manual cancellation share a conclusion.
        # Only GitHub's runner-acquisition annotation justifies retrying the former.
        check_id: str = job["check_run_url"].rsplit("/", 1)[-1]
        annotations: list[dict] = api.request("GET", f"check-runs/{check_id}/annotations?per_page=100")
        return any("The job was not acquired by Runner" in item["message"] for item in annotations)
    return False


def reconcile(api: GitHub, check: dict, state: dict, source: dict, cancel: bool = False) -> None:
    """Caller holds the per-sweep Actions concurrency lock across every state update."""
    if cancel or source.get("conclusion") == "cancelled":
        state["stop"] = "cancelled"
    if not state.get("stop") and now() - datetime.fromisoformat(state["created"]) > timedelta(days=7):
        state["stop"] = "seven-day sweep deadline exceeded; unfinished shards are untested"
    state.pop("warning", None)
    # Resolve persisted dispatch intents before considering any further submission.
    unresolved: list[tuple[int, dict]] = [(index, shard) for index, shard in enumerate(state["shards"])
                                         if shard.get("token") and not shard.get("run")]
    if unresolved:
        runs: list[dict] = api.pages(f"actions/workflows/{WORKFLOW}/runs", "workflow_runs",
                                     event="workflow_dispatch", created=">=" + source["created_at"])
        for index, shard in unresolved:
            matches: list[dict] = [run for run in runs
                                   if run["display_title"] == run_name(state["sweep"], index, shard["token"])]
            if len(matches) > 1:
                raise ValueError("Duplicate dispatch identity; manual reconciliation required")
            if matches:
                shard.update(run=matches[0]["id"], url=matches[0]["html_url"])
            else:
                state["warning"] = "A dispatch response is unresolved; admission is paused to avoid duplicates."
    active: int = 0
    for shard in state["shards"]:
        if shard.get("outcome") or not shard.get("run"):
            continue
        run: dict = api.request("GET", f"actions/runs/{shard['run']}")
        jobs: list[dict] = api.pages(f"actions/runs/{shard['run']}/jobs", "jobs")
        job: dict | None = execution_job(jobs)
        if not state.get("stop") and queue_expired(api, run, job):
            shard.setdefault("expired_runs", []).append(shard.pop("run"))
            shard.pop("token", None)
            shard.pop("url", None)
        elif job and job["status"] == "completed" and job.get("conclusion") != "skipped":
            shard["outcome"] = job["conclusion"]
            if job["conclusion"] == "cancelled":
                state["stop"] = "cancelled"
        elif run["status"] == "completed":
            shard["outcome"] = run.get("conclusion") or "failure"
            # A skipped/missing execution job is never evidence of passing tests.
            if shard["outcome"] == "success":
                shard["outcome"] = "untested"
            if shard["outcome"] == "cancelled":
                state["stop"] = "cancelled"
        else:
            active += 1
    save(api, check, state)
    if state.get("stop"):
        for shard in state["shards"]:
            if shard.get("run") and not shard.get("outcome"):
                run = api.request("GET", f"actions/runs/{shard['run']}")
                if run["status"] != "completed":
                    api.request("POST", f"actions/runs/{shard['run']}/cancel")
        state["cleanup_complete"] = not any(shard.get("token") and not shard.get("run")
                                            for shard in state["shards"])
        save(api, check, state)
        return
    if state.get("warning") or all(shard.get("outcome") for shard in state["shards"]):
        return
    # workflow_dispatch accepts a branch/tag, not an arbitrary SHA. Refuse branch drift.
    branch: dict = api.request("GET", f"commits/{state['workflow_ref']}")
    if branch["sha"] != source["head_sha"]:
        state["stop"] = "workflow revision changed; start a new sweep"
        reconcile(api, check, state, source)
        return
    for index, shard in enumerate(state["shards"]):
        if active >= LIMIT:
            break
        if shard.get("run") or shard.get("outcome") or shard.get("token"):
            continue
        shard["token"] = uuid4().hex
        # Persist the intent first. A crash or lost POST response must not redispatch it.
        save(api, check, state)
        response: dict = api.request("POST", f"actions/workflows/{WORKFLOW}/dispatches", {
            "ref": state["workflow_ref"],
            "inputs": {"sweep-run-id": str(state["sweep"]), "shard": str(index),
                       "dispatch-token": shard["token"]},
        })
        shard.update(run=response["workflow_run_id"], url=response["html_url"])
        active += 1
        save(api, check, state)
