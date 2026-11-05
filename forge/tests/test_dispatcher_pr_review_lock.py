# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `in-review` lock of the review (§FS-published-pr-review.3, #11328).

Every launcher reviews as one account, so the earliest claim comment holds the
lock, and every exit of a live pass gives back what it took.
"""
from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Any

from git_scripts.github_cli import GitHubRateLimitExceeded
from tests.pr_review_fixture import LOGIN, NUMBER, ReviewFixture, _git


class ReviewLockTest(ReviewFixture):

    def claim_comment(self, token: str, hours_ago: float) -> dict[str, Any]:
        """A claim another launcher on the same account posted `hours_ago`."""
        created: datetime = datetime.now(timezone.utc) - timedelta(hours=hours_ago)
        return {"id": 900, "created_at": created.strftime("%Y-%m-%dT%H:%M:%SZ"), "author": {"login": LOGIN},
                "body": f"Forge is reviewing `{self.head}`.\n\n<!-- forge-review-claim:{self.head}:{token} -->"}

    def claim_tokens(self, number: int = NUMBER) -> list[str]:
        """The tokens of the claims left on the pull request."""
        return [comment["body"].rsplit(":", 1)[1].split()[0] for comment in self.state()["prs"][str(number)]["comments"]
                if "forge-review-claim:" in comment["body"]]

    def test_a_stale_in_review_lock_is_taken_over(self) -> None:
        state: dict[str, Any] = self.state()
        state["timeline"][str(NUMBER)] = ["2026-10-27T08:30:00Z"]
        self.save(state)
        self.update(labels=[*self.pull()["labels"], {"name": "in-review"}], comments=[self.claim_comment("dead", 4)])
        self.plan({"answer": self.answer("rerun", case=2), "commands": [["gh", "run", "rerun", "1", "--failed"]]})
        printed: str = self.run_pass()
        self.assertIn("is stale since", self.logged)
        self.assertIn("rerun: reran", printed)
        self.assertNotIn("in-review", self.labels())
        self.assertEqual([], self.claim_tokens())

    def test_a_claim_behind_another_live_claim_spends_no_agent(self) -> None:
        """Two launchers of one account passed the skips together; the earlier claim keeps the lock (#11328)."""
        self.green()
        self.update(comments=[self.claim_comment("0badc0de", 0.01)])
        self.run_pass()
        self.assertIn(f"#{NUMBER} is held by another review since", self.logged)
        self.assertEqual([], self.invocations())
        self.assertEqual(["0badc0de"], self.claim_tokens())
        self.assertIn("in-review", self.labels())

    def test_a_pull_request_that_ended_while_it_was_claimed_spends_no_agent(self) -> None:
        self.green()
        state: dict[str, Any] = self.state()
        state["on_claim"] = {"state": "MERGED"}
        self.save(state)
        self.run_pass()
        self.assertIn(f"#{NUMBER} is merged; skipping", self.logged)
        self.assertEqual([], self.invocations())
        self.assertNotIn("in-review", self.labels())
        self.assertEqual([], self.claim_tokens())

    def test_a_failed_review_releases_its_lock(self) -> None:
        self.plan({"answer": self.answer("rerun", case=2), "commands": [["gh", "run", "rerun", "1", "--failed"]]})
        self.fault("run list", "gh: Not Found (HTTP 404)")
        self.run_pass()
        self.assertEqual(1, len(self.invocations()))
        self.assertIn(f"[review] #{NUMBER}: Command", self.logged)
        self.assertNotIn("in-review", self.labels())
        self.assertEqual([], self.claim_tokens())

    def test_a_rate_limit_releases_its_lock_and_ends_the_pass(self) -> None:
        state: dict[str, Any] = self.state()
        state["prs"]["8"] = self.pull(8, "2026-10-02T00:00:00Z")
        _git("push", "-q", "origin", f"{self.head}:refs/pull/8/head", cwd=self.repo)
        self.save(state)
        self.plan({"answer": self.answer("rerun", case=2), "commands": [["gh", "run", "rerun", "1", "--failed"]]})
        self.fault("run list", "gh: API rate limit exceeded for user ID 1. (HTTP 403)")
        with self.assertRaises(GitHubRateLimitExceeded):
            self.run_pass()
        self.assertEqual(1, len(self.invocations()))
        self.assertNotIn("in-review", self.labels())
        self.assertEqual(([], []), (self.claim_tokens(), self.claim_tokens(8)))
        self.assertNotIn("in-review", {label["name"] for label in self.state()["prs"]["8"]["labels"]})
