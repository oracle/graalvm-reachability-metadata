# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Push a real-use generation run's project through the shared publication pipeline.

The issue-less `real-use-tests` route (§AR-real-use-tests-publication): the run
already committed its coordinate project, so staging only picks up what is left,
and the trusted publisher opens the pull request from the descriptor.
"""

import argparse
import json
import os
from typing import Any

from git_scripts.branch_publication import publish_branch
from git_scripts.common_git import ensure_gh_authenticated, parse_coordinate_parts, stage_and_commit
from git_scripts.publication_descriptor import PublicationDescriptorInput

TASK_TYPE: str = "real-use-tests"


def build_descriptor_input(timestamp: str, real_use: dict[str, Any]) -> PublicationDescriptorInput:
    """The descriptor input: no issue, and the facts the trusted template reports."""
    return PublicationDescriptorInput(
        issue_number=None,
        task_type=TASK_TYPE,
        template_type=TASK_TYPE,
        status="success",
        timestamp=timestamp,
        render={"real_use": real_use},
    )


def push_real_use_branch(repo_path: str, coordinates: str, timestamp: str, real_use: dict[str, Any]) -> str:
    """Stage the real-use project, verify, write the descriptor, and push."""
    group, artifact, version = parse_coordinate_parts(coordinates)
    project = os.path.join("real-use-tests", "src", group, artifact, version)
    branch, _verification = publish_branch(
        repo_path=repo_path,
        branch_suffix=f"real-use-{artifact}-{version}",
        coordinates=coordinates,
        stage=lambda: stage_and_commit([project], f"Add real-use tests for {artifact} {version}", cwd=repo_path),
        descriptor_input=build_descriptor_input(timestamp, real_use),
    )
    return branch


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="publish_real_use_tests.py",
        description="Push a real-use project branch for the trusted publisher.",
    )
    parser.add_argument("--repo-path", required=True, help="The run worktree holding the committed project")
    parser.add_argument("--coordinates", required=True, help="Coordinates in group:artifact:version form")
    parser.add_argument("--timestamp", required=True, help="The run's start time, its durable identity")
    parser.add_argument("--render-json", required=True, help="JSON file with the facts the template reports")
    return parser


def main(argv: list[str] | None = None) -> None:
    args = build_parser().parse_args(argv)
    with open(args.render_json, "r", encoding="utf-8") as handle:
        real_use: dict[str, Any] = json.load(handle)
    ensure_gh_authenticated()
    branch = push_real_use_branch(args.repo_path, args.coordinates, args.timestamp, real_use)
    print(f"Pushed {branch} for {args.coordinates}.")


if __name__ == "__main__":
    main()
