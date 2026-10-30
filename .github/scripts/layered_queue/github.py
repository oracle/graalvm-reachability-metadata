# Copyright and related rights waived via CC0. See /LICENSE.
"""GitHub persistence and dispatch for the sweep. §FS-tests.3.6, §AR-layered-tests"""

from __future__ import annotations

import json
import os
from typing import Any
from urllib.error import HTTPError
from urllib.parse import urlencode
from urllib.request import Request, urlopen


class GitHub:
    def __init__(self) -> None:
        self.repository: str = os.environ["GITHUB_REPOSITORY"]
        self.base: str = os.environ.get("GITHUB_API_URL", "https://api.github.com")
        self.token: str = os.environ["GH_TOKEN"]

    def request(self, method: str, path: str, data: dict | None = None) -> Any:
        # Writes are never retried: a lost dispatch response may already have started a run.
        request = Request(f"{self.base}/repos/{self.repository}/{path}", method=method,
                          data=None if data is None else json.dumps(data).encode(), headers={
                              "Authorization": f"Bearer {self.token}",
                              "Accept": "application/vnd.github+json",
                              "Content-Type": "application/json",
                              "X-GitHub-Api-Version": "2026-03-10",
                          })
        try:
            with urlopen(request, timeout=60) as response:
                body: bytes = response.read()
                return json.loads(body) if body else None
        except HTTPError as error:
            raise RuntimeError(f"GitHub {method} {path}: HTTP {error.code}") from error

    def pages(self, path: str, key: str, **parameters: Any) -> list[dict]:
        items: list[dict] = []
        page: int = 1
        while True:
            query: str = urlencode(dict(parameters, per_page=100, page=page))
            batch: list[dict] = self.request("GET", f"{path}?{query}")[key]
            items.extend(batch)
            if len(batch) < 100:
                return items
            page += 1


def find_queue(api: GitHub, sweep: int) -> tuple[dict, dict | None]:
    source: dict = api.request("GET", f"actions/runs/{sweep}")
    if source["path"] != ".github/workflows/layered-tests.yml":
        raise ValueError("The sweep must originate in layered-tests.yml")
    checks: list[dict] = api.pages(f"commits/{source['head_sha']}/check-runs", "check_runs",
                                  check_name=f"Layered sweep {sweep}", filter="all")
    matches: list[dict] = [check for check in checks
                           if check.get("external_id") == f"layered-sweep:{sweep}"]
    if len(matches) > 1:
        raise ValueError("Multiple queue records for one sweep; refusing duplicate admission")
    return source, matches[0] if matches else None


def load_state(check: dict) -> dict:
    state: dict = json.loads(check["output"]["text"])
    if state.get("version") != 1:
        raise ValueError("Unsupported layered queue version")
    return state


def save(api: GitHub, check: dict, state: dict) -> None:
    outcomes: list[str] = [item["outcome"] for item in state["shards"] if item.get("outcome")]
    complete: bool = len(outcomes) == len(state["shards"])
    stopped: bool = bool(state.get("stop"))
    conclusion: str | None = None
    if complete or stopped:
        conclusion = "success" if complete and set(outcomes) == {"success"} else "failure"
        if state.get("stop") == "cancelled":
            conclusion = "cancelled"
    rows: list[str] = [f"Completed: {len(outcomes)}/{len(state['shards'])}."]
    if state.get("stop"):
        rows.append(f"Stopped: {state['stop']}.")
    if state.get("warning"):
        rows.append(state["warning"])
    rows += [f"- {item['lane']} {item['coordinates']}: {item.get('outcome', 'pending')}"
             + (f" ([run]({item['url']}))" if item.get("url") else "")
             for item in state["shards"]]
    payload: dict = {
        "status": "completed" if conclusion else "in_progress",
        "output": {"title": f"{len(outcomes)}/{len(state['shards'])} shards completed",
                   "summary": "\n".join(rows),
                   "text": json.dumps(state, separators=(",", ":"))},
    }
    if conclusion:
        payload["conclusion"] = conclusion
    api.request("PATCH", f"check-runs/{check['id']}", payload)
