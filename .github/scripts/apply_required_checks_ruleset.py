#!/usr/bin/env python3
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Apply or check the default-branch required-status-checks ruleset.

The ruleset is kept as code so the merge gate is reviewable and reproducible.
§FS-repository-functional-spec.5.3 §AR-required-status-checks
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

import yaml

REPO_ROOT = Path(__file__).resolve().parents[2]
RULESET_PATH = REPO_ROOT / ".github" / "rulesets" / "master-required-checks.json"
WORKFLOWS_DIR = REPO_ROOT / ".github" / "workflows"
DEFAULT_REPOSITORY = "oracle/graalvm-reachability-metadata"


def load_ruleset() -> dict:
    """Read the committed ruleset definition."""
    return json.loads(RULESET_PATH.read_text(encoding="utf-8"))


def required_contexts(ruleset: dict) -> list[str]:
    """Return the status-check contexts the ruleset requires, in file order."""
    for rule in ruleset["rules"]:
        if rule["type"] == "required_status_checks":
            return [check["context"] for check in rule["parameters"]["required_status_checks"]]
    raise SystemExit("ERROR: the ruleset declares no required_status_checks rule")


def pull_request_job_names() -> set[str]:
    """Collect the display names of every job in a workflow that runs on pull_request."""
    names: set[str] = set()
    for workflow_path in sorted(WORKFLOWS_DIR.glob("*.yml")):
        workflow = yaml.safe_load(workflow_path.read_text(encoding="utf-8"))
        triggers = workflow.get("on", workflow.get(True, {}))
        if not isinstance(triggers, dict) or "pull_request" not in triggers:
            continue
        for job_id, job in workflow.get("jobs", {}).items():
            names.add(str(job.get("name", job_id)))
    return names


def check_contexts(ruleset: dict) -> list[str]:
    """Return the required contexts that no pull_request workflow job produces."""
    job_names = pull_request_job_names()
    return [context for context in required_contexts(ruleset) if context not in job_names]


def gh_api(method: str, path: str, payload: dict | None = None) -> object:
    """Call the GitHub REST API through the authenticated `gh` CLI."""
    command = ["gh", "api", "-X", method, path]
    if payload is not None:
        command += ["--input", "-"]
    result = subprocess.run(
        command,
        input=json.dumps(payload) if payload is not None else None,
        capture_output=True,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        raise SystemExit(f"ERROR: gh api {method} {path} failed:\n{result.stderr.strip()}")
    return json.loads(result.stdout) if result.stdout.strip() else {}


def apply(repository: str, ruleset: dict) -> None:
    """Create the ruleset, or update the existing one with the same name (needs repo admin)."""
    existing = gh_api("GET", f"repos/{repository}/rulesets")
    match = next((item for item in existing if item.get("name") == ruleset["name"]), None)
    if match is None:
        created = gh_api("POST", f"repos/{repository}/rulesets", ruleset)
        print(f"Created ruleset {created.get('id')} '{ruleset['name']}' on {repository}.")
        return
    gh_api("PUT", f"repos/{repository}/rulesets/{match['id']}", ruleset)
    print(f"Updated ruleset {match['id']} '{ruleset['name']}' on {repository}.")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repository", default=DEFAULT_REPOSITORY, help="owner/name to apply the ruleset to")
    parser.add_argument("--check", action="store_true", help="only verify the contexts match workflow job names")
    args = parser.parse_args()

    ruleset = load_ruleset()
    missing = check_contexts(ruleset)
    if missing:
        print("ERROR: required contexts with no matching pull_request workflow job name:", file=sys.stderr)
        for context in missing:
            print(f"  - {context}", file=sys.stderr)
        return 1
    print(f"All {len(required_contexts(ruleset))} required contexts match a pull_request workflow job.")
    if not args.check:
        apply(args.repository, ruleset)
    return 0


if __name__ == "__main__":
    sys.exit(main())
