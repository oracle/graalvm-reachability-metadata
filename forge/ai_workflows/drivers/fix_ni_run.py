# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import argparse
import os
import subprocess
import sys

if __package__ in (None, ""):
    sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))

from ai_workflows.core.fix_metadata_codex import run_codex_metadata_fix
from git_scripts.common_git import build_ai_branch_name, delete_remote_branch_if_exists
from utility_scripts.gradle_environment import gradle_command_environment
from utility_scripts.library_finalization import run_library_finalization
from utility_scripts.library_preparation_preflight import (
    prepare_library_preparation_preflight,
)
from utility_scripts.repo_path_resolver import require_complete_reachability_repo, resolve_repo_roots
from utility_scripts.source_context import populate_artifact_urls


def build_parser():
    parser = argparse.ArgumentParser(
        prog="fix_ni_run.py",
        description="Run fixTestNativeImageRun Gradle task for a library version upgrade.",
        epilog=(
            "Example:\n"
            "  python3 ai_workflows/drivers/fix_ni_run.py \\\n"
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
    parser.add_argument(
        "--metrics-repo-path",
        help="Path where dispatcher-created workflow context artifacts are written.",
    )
    parser.add_argument(
        "--library-preparation-preflight-path",
        help="Path to the dispatcher-created library preparation preflight JSON record.",
    )
    return parser


def run_fix_test_native_image_run(
        reachability_metadata_path: str,
        current_coordinates: str,
        new_version: str,
) -> subprocess.CompletedProcess[str]:
    """Run the fixTestNativeImageRun Gradle task."""
    require_complete_reachability_repo(reachability_metadata_path)
    return subprocess.run(
        [
            "./gradlew", "fixTestNativeImageRun",
            f"-PtestLibraryCoordinates={current_coordinates}",
            f"-PnewLibraryVersion={new_version}",
        ],
        cwd=reachability_metadata_path,
        env=gradle_command_environment(reachability_metadata_path),
    )


def run_gradle_test(
        reachability_metadata_path: str,
        coordinates: str,
) -> subprocess.CompletedProcess[str]:
    """Run Gradle tests for the provided coordinates."""
    require_complete_reachability_repo(reachability_metadata_path)
    return subprocess.run(
        [
            "./gradlew", "test",
            f"-Pcoordinates={coordinates}",
        ],
        cwd=reachability_metadata_path,
        env=gradle_command_environment(reachability_metadata_path),
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
    """Run the Native Image fix driver.

    The single-run driver (§WF-forge-workflow-drivers) for
    §WF-native-image-run-fix-workflow.
    """
    args = build_parser().parse_args(sys.argv[1:] if argv is None else argv)

    reachability_metadata_path, _ = resolve_repo_roots(
        args.reachability_metadata_path,
        args.metrics_repo_path,
    )

    current_coordinates = args.coordinates
    new_version = args.new_version
    # Apply deterministic preflight setup into the resolved worktree before
    # generation; only advisory guidance reaches the prompt context.
    library_preparation_preflight, library_preparation_preflight_context = (
        prepare_library_preparation_preflight(
            args.library_preparation_preflight_path,
            reachability_metadata_path,
        )
    )
    if library_preparation_preflight is not None:
        print("[pipeline] Library preparation preflight:")
        print(library_preparation_preflight_context)

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
        generated_metadata_file = os.path.join(
            reachability_metadata_path,
            "metadata",
            group,
            artifact,
            new_version,
            "reachability-metadata.json",
        )
        if not os.path.isfile(generated_metadata_file):
            print(
                "ERROR: fixTestNativeImageRun failed before generating "
                f"{generated_metadata_file}. Skipping Codex metadata repair.",
                file=sys.stderr,
            )
            return result.returncode
        print(f"[pipeline] Detected missing metadata entries for {library}. Running Codex fix.")
        gradle_env = gradle_command_environment(reachability_metadata_path)
        codex_rc, _codex_log, _codex_timed_out = run_codex_metadata_fix(
            reachability_metadata_path,
            library,
            graalvm_home=gradle_env.get("GRAALVM_HOME"),
            library_preparation_preflight_context=library_preparation_preflight_context,
        )
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
