# Copyright and related rights waived via CC0. See /LICENSE.
"""Workflow entry points for the shared queue. §FS-tests.3.6, §AR-layered-tests"""

from __future__ import annotations

import json
import os
import sys
from pathlib import Path

from layered_queue.dispatcher import LIMIT, now, reconcile
from layered_queue.github import GitHub, find_queue, load_state


def output(name: str, value: str) -> None:
    if "\n" in value or "\r" in value:
        raise ValueError("Workflow outputs must be single-line values")
    with Path(os.environ["GITHUB_OUTPUT"]).open("a") as stream:
        stream.write(f"{name}={value}\n")


def create_queue(api: GitHub, source: dict, sweep: int) -> dict:
    plan: list[dict] = json.loads(os.environ["COORDINATE_MATRIX"])["include"]
    shards: list[dict] = [{"lane": item["lane"]["name"], "coordinates": item["coordinates"]}
                          for item in plan]
    if not 0 < len(shards) <= 160 or any(item["lane"] not in {"Shared", "Dedicated"} for item in shards):
        raise ValueError("Invalid layered shard plan")
    state: dict = {
        "version": 1, "sweep": sweep, "created": now().isoformat(), "shards": shards,
        "workflow_ref": source["head_branch"],
        "revision": os.environ["TEST_REVISION"], "mode": os.environ["NATIVE_IMAGE_MODE"],
        "graalvm_artifact": os.environ["GRAALVM_ARTIFACT"],
        "base_artifact": os.environ.get("BASE_ARTIFACT", ""),
    }
    if not state["graalvm_artifact"]:
        raise ValueError("Cannot start a sweep without its prepared GraalVM")
    # A failed shared-base build must not suppress dedicated coverage.
    if not state["base_artifact"]:
        for shard in shards:
            if shard["lane"] == "Shared":
                shard["outcome"] = "base preparation failed"
    return api.request("POST", "check-runs", {
        "name": f"Layered sweep {sweep}", "head_sha": source["head_sha"],
        "external_id": f"layered-sweep:{sweep}", "status": "in_progress",
        "details_url": source["html_url"],
        "output": {"title": "Sweep pending", "summary": "No shard has been submitted yet.",
                   "text": json.dumps(state, separators=(",", ":"))},
    })


def discover(api: GitHub) -> None:
    # The registry artifact outlives the seven-day sweep deadline, including stopped queues
    # whose cancellation cleanup was interrupted. It contains no mutable scheduling state.
    artifacts: list[dict] = api.pages("actions/artifacts", "artifacts", name="layered-queue")
    sweeps: list[str] = []
    for sweep in sorted({artifact["workflow_run"]["id"] for artifact in artifacts if not artifact["expired"]}):
        _, check = find_queue(api, sweep)
        if check:
            state: dict = load_state(check)
            if check["status"] != "completed" or (state.get("stop") and not state.get("cleanup_complete")):
                sweeps.append(str(sweep))
    output("matrix", json.dumps({"sweep": sweeps or [""]}))
    output("found", str(bool(sweeps)).lower())


def shard_environment(api: GitHub, sweep: int) -> None:
    source, check = find_queue(api, sweep)
    if check is None:
        raise ValueError("Missing durable sweep state")
    state: dict = load_state(check)
    index: int = int(os.environ["SHARD_INDEX"])
    if not 0 <= index < len(state["shards"]):
        raise ValueError("Invalid shard index")
    shard: dict = state["shards"][index]
    if state.get("stop") or shard.get("outcome"):
        raise ValueError("Sweep or shard is already finished")
    if os.environ["DISPATCH_TOKEN"] != shard.get("token"):
        raise ValueError("Shard does not match the recorded dispatch")
    if shard.get("run") and shard["run"] != int(os.environ["GITHUB_RUN_ID"]):
        raise ValueError("This dispatch was already claimed by another run")
    if os.environ["GITHUB_SHA"] != source["head_sha"]:
        raise ValueError("Workflow revision changed; start a new sweep")
    lane: str = shard["lane"]
    prefix: str = "shared" if lane == "Shared" else "dedicated"
    values: dict[str, str] = {
        "TEST_REVISION": state["revision"], "PRODUCER_RUN": str(sweep),
        "GRAALVM_ARTIFACT": state["graalvm_artifact"], "BASE_ARTIFACT": state["base_artifact"],
        "GVM_TCK_NATIVE_IMAGE_MODE": state["mode"], "COORDINATES": shard["coordinates"],
        "LAYER_NAME": lane, "LAYER_TASK": "testSharedLayer" if lane == "Shared" else "testDedicatedLayer",
        "FAILURE_REPORT_NAME": f"{prefix}-layer-test-failures.txt",
        "EXCLUDED_COORDINATES_FILE": f"tests/tck-build-logic/src/main/resources/{prefix}-layer{'ed' if lane == 'Shared' else ''}-test-exclusions.txt",
        "DELETE_DEDICATED_LAYER": str(lane == "Dedicated").lower(),
    }
    with Path(os.environ["GITHUB_ENV"]).open("a") as environment:
        for name, value in values.items():
            if "\n" in value or "\r" in value:
                raise ValueError("Invalid multiline shard configuration")
            environment.write(f"{name}={value}\n")


def main() -> None:
    api = GitHub()
    command: str = sys.argv[1]
    if command == "discover":
        discover(api)
        return
    sweep: int = int(os.environ["SWEEP_RUN_ID"])
    if command == "shard":
        shard_environment(api, sweep)
        return
    source, check = find_queue(api, sweep)
    if command == "initialize":
        if check is None:
            check = create_queue(api, source, sweep)
        Path(os.environ["RUNNER_TEMP"], "layered-queue.json").write_text(json.dumps({"check": check["id"]}))
        with Path(os.environ["GITHUB_STEP_SUMMARY"]).open("a") as summary:
            summary.write(f"The sweep result is tracked by [Layered sweep {sweep}]({check['html_url']}). "
                          "Preparation finishing does not mean the tests have passed.\n")
    elif command == "reconcile":
        if check is None:
            raise ValueError("Missing queue; rerun the sweep initialization")
        state: dict = load_state(check)
        completed: str = os.environ.get("COMPLETED_RUN_ID", "")
        active: int = sum(bool(shard.get("token")) and not shard.get("outcome")
                          for shard in state["shards"])
        admitted: bool = all(shard.get("token") or shard.get("outcome") for shard in state["shards"])
        if completed and any(shard.get("run") == int(completed) and shard.get("outcome")
                             for shard in state["shards"]) and not state.get("stop") and not state.get("warning") \
                and (active >= LIMIT or admitted):
            return
        reconcile(api, check, state, source, os.environ.get("CANCEL_SWEEP") == "true")
    else:
        raise ValueError(f"Unknown queue command: {command}")


if __name__ == "__main__":
    main()
