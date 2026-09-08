# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Describe the resolved edit scope a generation prompt is confined to."""

import os


def format_resolved_edit_scope_context(
        repo_path: str,
        test_dir: str,
        test_source_root: str,
        build_gradle_file: str,
) -> str:
    """Describe the resolved edit scope for agent prompts.

    Every driver names the same scope, so a routed repair confines generation
    exactly as the coverage route does (§forge/AR-forge-drivers).
    """
    return (
        "Resolved edit scope:\n"
        f"- Repository root: `{repo_path}`\n"
        f"- Target test project directory: `{test_dir}` "
        f"(`{os.path.relpath(test_dir, repo_path)}`)\n"
        f"- Target test source root: `{test_source_root}` "
        f"(`{os.path.relpath(test_source_root, repo_path)}`)\n"
        f"- Target build file: `{build_gradle_file}` "
        f"(`{os.path.relpath(build_gradle_file, repo_path)}`)\n\n"
        "Only create or update tests under the target test source root above. "
        "Only update support files inside the target test project directory when the new tests require it. "
        "Do not edit cloned baseline test directories or other versioned test directories. "
        "Do not edit metadata files directly except where a prompt explicitly permits a "
        "reporter-requested entry."
    )
