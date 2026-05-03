# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import subprocess
import sys

from ai_workflows.fix_metadata_codex import run_codex_metadata_fix
from git_scripts.common_git import build_ai_branch_name, delete_remote_branch_if_exists
from utility_scripts.library_finalization import run_library_finalization
from utility_scripts.repo_path_resolver import resolve_repo_roots
from utility_scripts.source_context import populate_artifact_urls


def build_parser():
    parser = argparse.ArgumentParser(
        prog="fix_ni_run.py",
        description="Run fixTestNativeImageRun Gradle task for a library version upgrade.",
        epilog=(
            "Example:\n"
            "  python3 -m ai_workflows.fix_ni_run \\\n"
            "      --coordinates com.example:lib:1.2.3 \\\n"
            "      --new-version 1.2.4 \\\n"
            "      --reachability-metadata-path /path/to/graalvm-reachability-metadata\n"
        ),
        formatter_class=argparse.RawTextHelpFormatter,
    )
    parser.add_argument(
        "--coordinates",
        required=True,
        help="Maven coordinates Group:Artifact:Version for the current library version",
    )
    parser.add_argument(
        "--new-version",
        required=True,
        help="Target library version which needs a fix",
    )
    parser.add_argument(
        "--reachability-metadata-path",
        help=(
            "Path to the graalvm-reachability-metadata repository. "
            "If omitted, the parent checkout of this Forge directory is used."
        ),
    )
    return parser


def run_fix_test_native_image_run(
        reachability_metadata_path: str,
        current_coordinates: str,
        new_version: str,
) -> subprocess.CompletedProcess[str]:
    """Run the fixTestNativeImageRun Gradle task."""
    return subprocess.run(
        [
            "./gradlew", "fixTestNativeImageRun",
            f"-PtestLibraryCoordinates={current_coordinates}",
            f"-PnewLibraryVersion={new_version}",
        ],
        cwd=reachability_metadata_path,
    )


def run_gradle_test(
        reachability_metadata_path: str,
        coordinates: str,
):
    """Run Gradle tests for the provided coordinates."""
    return subprocess.run(
        [
            "./gradlew", "test",
            f"-Pcoordinates={coordinates}",
        ],
        cwd=reachability_metadata_path,
    )


def create_or_switch_branch(reachability_metadata_path: str, branch: str) -> None:
    """Reset the workflow branch to the current detached HEAD."""
    delete_remote_branch_if_exists(branch, cwd=reachability_metadata_path)
    subprocess.run(
        ["git", "switch", "-C", branch],
        cwd=reachability_metadata_path,
        check=True,
    )


def main(argv=None) -> int:
    """Run the Native Image fix workflow."""
    args = build_parser().parse_args(sys.argv[1:] if argv is None else argv)

    reachability_metadata_path, _ = resolve_repo_roots(
        args.reachability_metadata_path,
        None,
    )

    current_coordinates = args.coordinates
    new_version = args.new_version

    group, artifact, _ = current_coordinates.split(":")
    branch = build_ai_branch_name(
        f"fix-native-image-run-{group}-{artifact}-{new_version}",
        cwd=reachability_metadata_path,
    )
    print(f"[pipeline] Creating branch: {branch}")
    create_or_switch_branch(reachability_metadata_path, branch)

    print(f"[pipeline] Running fixTestNativeImageRun for: {current_coordinates} -> {new_version}")
    result = run_fix_test_native_image_run(
        reachability_metadata_path=reachability_metadata_path,
        current_coordinates=current_coordinates,
        new_version=new_version,
    )

    if result.returncode != 0:
        library = f"{group}:{artifact}:{new_version}"
        print(f"[pipeline] Detected missing metadata entries for {library}. Running Codex fix.")
        codex_rc, _codex_log, _codex_timed_out = run_codex_metadata_fix(reachability_metadata_path, library)
        if codex_rc != 0:
            print(f"ERROR: Codex failed with return code: {codex_rc}", file=sys.stderr)
            return codex_rc
        print(f"[pipeline] Codex metadata fix completed. Running Gradle test for {library}.")
        result = run_gradle_test(
            reachability_metadata_path=reachability_metadata_path,
            coordinates=library,
        )
        if result.returncode != 0:
            print("[pipeline] Gradle test failed after Codex metadata fix. Skipping PR creation.", file=sys.stderr)
            return result.returncode

    library = f"{group}:{artifact}:{new_version}"
    populate_artifact_urls(reachability_metadata_path, library)

    if not run_library_finalization(
        repo_path=reachability_metadata_path,
        library=library,
        group=group,
        artifact=artifact,
        library_version=new_version,
    ):
        return 1

    print(f"[pipeline] Workflow succeeded for {current_coordinates}")
    return 0


if __name__ == "__main__":
    if any(a in ("-h", "--help") for a in sys.argv[1:]):
        build_parser().print_help()
        sys.exit(0)
    sys.exit(main())
