# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

from dispatcher_test_support import *  # noqa: F401,F403 - shared dispatcher test fixtures


class IssueFormGateClaimOrderTests(unittest.TestCase):
    """Claim preparation pins a fresh base and checks the isolated worktree."""

    def test_issue_base_is_fetched_and_resolved(self) -> None:
        completed_process = subprocess.CompletedProcess(args=[], returncode=0, stdout="")

        with patch.object(
                worktrees,
                "run_git_transport",
                return_value=completed_process,
        ) as run, patch.object(
                worktrees,
                "resolve_git_commit",
                return_value="base-sha",
        ) as resolve:
            commit = worktrees.fetch_issue_base_commit("/repo")

        self.assertEqual(commit, "base-sha")
        run.assert_called_once_with(
            [
                "fetch",
                "--quiet",
                "origin",
                "+master:refs/remotes/origin/master",
            ],
            cwd="/repo",
        )
        resolve.assert_called_once_with("/repo", "refs/remotes/origin/master")

    def test_continuation_rebases_onto_pinned_base_without_refetching_master(self) -> None:
        marker = SimpleNamespace(
            preserved_branch="ai/runner/preserved",
            issue_number=4242,
            continue_from="explore",
        )
        completed = subprocess.CompletedProcess(args=[], returncode=0, stdout="")

        with patch.object(
                continuation,
                "fetch_remote_branch",
                return_value="refs/remotes/origin/ai/runner/preserved",
        ) as fetch_branch, patch.object(
                subprocess,
                "run",
                return_value=completed,
        ) as run:
            resumed = continuation.checkout_continuation_branch(
                "/worktree",
                marker,
                "base-sha",
            )

        self.assertTrue(resumed)
        fetch_branch.assert_called_once_with("/worktree", "ai/runner/preserved")
        self.assertIn(
            call(
                ["git", "rebase", "base-sha"],
                cwd="/worktree",
                env=unittest.mock.ANY,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                check=False,
            ),
            run.call_args_list,
        )
