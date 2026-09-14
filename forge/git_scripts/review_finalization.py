# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Exact finalization command for the authoritative local reviewer.

The command owns every mutation-producing finalization duty and admits a verdict
only after a successful run leaves the publishable tree unchanged.
§FS-local-branch-review §AR-forge-driver-finalization
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import sys

from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.library_finalization import run_library_finalization
from utility_scripts.logged_command import run_logged_command
from utility_scripts.metadata_index import resolve_metadata_version


def _run_gradle_test(
        repo_path: str,
        coordinates: str,
        lane_name: str,
        environment: dict[str, str],
) -> bool:
    """Run one reviewer finalization lane with durable output."""
    result = run_logged_command(
        ["./gradlew", "test", f"-Pcoordinates={coordinates}"],
        cwd=repo_path,
        env=gradle_command_environment(repo_path, environment),
        task_type="local-review-finalization",
        subject=coordinates,
        action=lane_name,
        stage="finalization",
    )
    return result.returncode == 0


def publishable_tree_digest(repo_path: str) -> str:
    """Hash committed and non-ignored uncommitted content in the review worktree."""
    digest = hashlib.sha256()
    digest.update(subprocess.check_output(
        ["git", "rev-parse", "HEAD"], cwd=repo_path,
    ))
    digest.update(subprocess.check_output(
        ["git", "diff", "--binary", "HEAD", "--"], cwd=repo_path,
    ))
    untracked_output = subprocess.check_output(
        ["git", "ls-files", "--others", "--exclude-standard", "-z"],
        cwd=repo_path,
    )
    untracked_paths = (
        path for path in untracked_output.split(b"\0")
        if path and not os.fsdecode(path).startswith(".forge-local-review-")
    )
    for raw_path in sorted(untracked_paths):
        path = os.fsdecode(raw_path)
        absolute_path = os.path.join(repo_path, path)
        digest.update(raw_path)
        digest.update(b"\0")
        if os.path.islink(absolute_path):
            digest.update(os.fsencode(os.readlink(absolute_path)))
            continue
        with open(absolute_path, "rb") as untracked_file:
            for chunk in iter(lambda: untracked_file.read(1024 * 1024), b""):
                digest.update(chunk)
    return digest.hexdigest()


def write_finalization_receipt(
        receipt_path: str,
        repo_path: str,
        coordinates: str,
        base_commit: str,
        tree_digest: str,
) -> None:
    """Record the stable tree accepted by the exact finalization command."""
    payload: dict[str, str] = {
        "coordinates": coordinates,
        "base_commit": base_commit,
        "head": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=repo_path, text=True,
        ).strip(),
        "tree_digest": tree_digest,
    }
    with open(receipt_path, "w", encoding="utf-8") as receipt_file:
        json.dump(payload, receipt_file, indent=2)
        receipt_file.write("\n")


def finalization_receipt_matches(
        receipt_path: str,
        repo_path: str,
        coordinates: str,
        base_commit: str,
) -> bool:
    """Return whether the receipt proves finalization passed on the current tree."""
    try:
        with open(receipt_path, "r", encoding="utf-8") as receipt_file:
            payload = json.load(receipt_file)
    except (OSError, json.JSONDecodeError):
        return False
    return payload == {
        "coordinates": coordinates,
        "base_commit": base_commit,
        "head": subprocess.check_output(
            ["git", "rev-parse", "HEAD"], cwd=repo_path, text=True,
        ).strip(),
        "tree_digest": publishable_tree_digest(repo_path),
    }


def run_review_finalization(
        repo_path: str,
        coordinates: str,
        base_commit: str,
) -> bool:
    """Run all reviewer-owned finalization with secondary agents disabled."""
    current_environment: dict[str, str] = dict(os.environ)
    future_defaults_environment: dict[str, str] = dict(os.environ)
    future_defaults_environment["GVM_TCK_NATIVE_IMAGE_MODE"] = "future-defaults-all"
    graalvm_25_home: str | None = os.environ.get("GRAALVM_HOME_25_0")
    if not graalvm_25_home:
        print("ERROR: GRAALVM_HOME_25_0 is required for review finalization.", file=sys.stderr)
        return False
    graalvm_25_environment: dict[str, str] = dict(os.environ)
    graalvm_25_environment["GRAALVM_HOME"] = graalvm_25_home
    graalvm_25_environment["JAVA_HOME"] = graalvm_25_home
    graalvm_25_environment.pop("GVM_TCK_NATIVE_IMAGE_MODE", None)
    for lane_name, environment in (
            ("review current-defaults latest GraalVM test", current_environment),
            ("review future-defaults latest GraalVM test", future_defaults_environment),
            ("review current-defaults GraalVM 25 test", graalvm_25_environment),
    ):
        if not _run_gradle_test(repo_path, coordinates, lane_name, environment):
            return False

    group, artifact, version = coordinates.split(":")
    libraries: list[str] = [coordinates]
    metadata_version: str = resolve_metadata_version(repo_path, group, artifact, version)
    metadata_coordinates: str = f"{group}:{artifact}:{metadata_version}"
    if metadata_coordinates not in libraries:
        libraries.append(metadata_coordinates)
    for library in libraries:
        library_version: str = library.rsplit(":", 1)[-1]
        if not run_library_finalization(
                repo_path=repo_path,
                library=library,
                group=group,
                artifact=artifact,
                library_version=library_version,
                base_commit=base_commit,
                allow_agent_repairs=False,
        ):
            return False
    return True


def build_parser() -> argparse.ArgumentParser:
    """Build the exact command exposed to the local reviewer."""
    parser = argparse.ArgumentParser(
        description="Run authoritative local-review finalization without nested agents.",
    )
    parser.add_argument("--repo-path", default=os.getcwd())
    parser.add_argument("--coordinates", required=True)
    parser.add_argument("--base-commit", required=True)
    parser.add_argument("--receipt-path", required=True)
    return parser


def main(argv: list[str] | None = None) -> int:
    """Require finalization to pass without changing the publishable tree."""
    args = build_parser().parse_args(argv)
    repo_path: str = os.path.abspath(args.repo_path)
    receipt_path: str = os.path.abspath(args.receipt_path)
    if os.path.isfile(receipt_path):
        os.remove(receipt_path)
    before_digest: str = publishable_tree_digest(repo_path)
    if not run_review_finalization(repo_path, args.coordinates, args.base_commit):
        return 1
    after_digest: str = publishable_tree_digest(repo_path)
    if after_digest != before_digest:
        print(
            "ERROR: Review finalization changed the publishable tree; inspect its changes "
            "and run this command again before writing the verdict.",
            file=sys.stderr,
        )
        return 1
    write_finalization_receipt(
        receipt_path=receipt_path,
        repo_path=repo_path,
        coordinates=args.coordinates,
        base_commit=args.base_commit,
        tree_digest=after_digest,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
