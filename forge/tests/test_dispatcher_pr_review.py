# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The review of published pull requests (§FS-published-pr-review): its skips, its
prompt, and the outcomes it confirms, on the fixture of `pr_review_fixture`.
"""
from __future__ import annotations

import json
import unittest
from typing import Any
from unittest.mock import patch

import yaml

from dispatcher import pr_review, pr_review_state
from dispatcher.config import (LABEL_FORGE_MERGE_FOLLOW_UP, LABEL_GRAALVM_BUG, LABEL_HUMAN_INTERVENTION,
                               LABEL_HUMAN_INTERVENTION_FIXED, SUPPORTED_FORGE_REVIEW_TASK_TYPES)
from tests.pr_review_fixture import BRANCH, LOGIN, NUMBER, TEST, ReviewFixture, _git, check

# The body the trusted publisher renders for a non-final chunk (§FS-forge-chunked-dynamic-access).
CHUNK_BODY: str = "\n## What does this PR do?\n\nRefs: #1412\n\nSummary:\n- Chunked dynamic-access: yes\n"


class ReviewTest(ReviewFixture):

    def test_every_skip_reason_spends_no_agent(self) -> None:
        cases: list[tuple[dict[str, Any], str]] = [
            ({"state": "MERGED"}, "is merged"),
            ({"state": "CLOSED"}, "is closed"),
            ({"reviewDecision": "APPROVED"}, "is approved"),
            ({"labels": [{"name": "library-new-request"}, {"name": "reviewed"}]}, "is labeled reviewed"),
            ({"labels": [{"name": "library-new-request"}, {"name": LABEL_HUMAN_INTERVENTION}]},
             f"is labeled {LABEL_HUMAN_INTERVENTION}"),
            ({"labels": [{"name": "library-new-request"}, {"name": LABEL_GRAALVM_BUG}]},
             f"is labeled {LABEL_GRAALVM_BUG}, blocked on a GraalVM defect"),
            ({"labels": [{"name": "library-new-request"}, {"name": "in-review"}]}, "is held by another review"),
            ({"headRefName": "feature/x"}, "is not a Forge branch"),
            ({"headRepository": {"nameWithOwner": "fork/repo"}}, "is from another repository"),
            ({"labels": [{"name": "GenAI"}]}, "is not a Forge route"),
        ]
        state: dict[str, Any] = self.state()
        state["timeline"][str(NUMBER)] = ["2026-10-27T11:00:00Z"]
        self.save(state)
        for changes, reason in cases:
            self.update(**{**self.pull(), **changes})
            self.run_pass()
            self.assertIn(f"#{NUMBER} {reason}", self.logged)
        self.update(**self.pull())
        for checks, reason in (([check("lint"), check("test", "in_progress", None)], "waiting for 1 running checks"),
                               ([], "waiting for its first check")):
            state = self.state()
            state["checks"][self.head] = checks
            self.save(state)
            self.run_pass()
            self.assertIn(reason, self.logged)
        self.assertEqual([], self.invocations())

    def test_a_maintainer_fix_reopens_a_reviewed_pull_request(self) -> None:
        self.green()
        self.update(labels=[{"name": "library-new-request"}, {"name": "reviewed"},
                            {"name": LABEL_HUMAN_INTERVENTION_FIXED}])
        self.run_pass()
        self.assertEqual(1, len(self.invocations()))

    def test_a_maintainer_fix_does_not_reopen_a_pull_request_blocked_on_graalvm(self) -> None:
        self.green()
        self.update(labels=[{"name": "library-new-request"}, {"name": "reviewed"},
                            {"name": LABEL_HUMAN_INTERVENTION_FIXED}, {"name": LABEL_GRAALVM_BUG}])
        self.run_pass()
        self.assertIn(f"#{NUMBER} is labeled {LABEL_GRAALVM_BUG}", self.logged)
        self.assertEqual([], self.invocations())

    def test_approval_is_offered_only_on_a_green_head_and_ends_the_review(self) -> None:
        self.plan({"answer": None})
        self.run_pass()
        self.assertIn("so this case does not apply: do not approve", self.invocations()[0]["prompt"])
        self.green()
        self.update(comments=[], labels=self.pull()["labels"])
        self.plan({"answer": self.answer("approve", case=1, cause="all green"),
                   "commands": [["gh", "pr", "review", str(NUMBER), "--approve"],
                                ["gh", "pr", "merge", str(NUMBER), "--auto", "--squash"]]})
        printed: str = self.run_pass()
        self.assertIn(f"--match-head-commit {self.head}", self.invocations()[-1]["prompt"])
        self.assertIn("approve (terminal): approved", printed)
        self.assertEqual({"GenAI", "library-new-request", "reviewed"}, self.labels())

    def test_a_non_final_chunk_is_labeled_for_follow_up_before_its_agent_may_merge_it(self) -> None:
        self.green()
        self.update(body=CHUNK_BODY)
        self.plan({"answer": self.answer("approve", case=1, cause="all green"),
                   "commands": [["gh", "pr", "review", str(NUMBER), "--approve"],
                                ["gh", "pr", "merge", str(NUMBER), "--auto", "--squash"]]})
        self.assertIn("approve (terminal): approved", self.run_pass())
        self.assertIn(LABEL_FORGE_MERGE_FOLLOW_UP, self.labels())
        with open(self.state_path + ".calls", encoding="utf-8") as handle:
            calls: list[list[str]] = [json.loads(line) for line in handle]
        labeled: int = calls.index(next(call for call in calls
                                        if call[-1] == f"labels[]={LABEL_FORGE_MERGE_FOLLOW_UP}"))
        self.assertLess(labeled, calls.index(["pr", "review", str(NUMBER), "--approve"]))

    def test_only_a_pull_request_whose_merge_releases_an_issue_is_labeled_for_follow_up(self) -> None:
        cases: list[tuple[str, bool]] = [
            ("Fixes: #1412\n\nSummary:\n- Chunked dynamic-access: yes\n", False),
            ("Fixes: #1412\n", False),
            ("Fixes: #1412\n\nRefs: #1500\nForge-Unblocks-Issue: #1500\n", True),
        ]
        for body, labeled in cases:
            with self.subTest(body=body):
                self.update(**{**self.pull(), "body": body})
                self.run_pass()
                self.assertEqual(labeled, LABEL_FORGE_MERGE_FOLLOW_UP in self.labels())

    def test_an_approval_on_a_failed_head_is_escalated(self) -> None:
        self.plan({"answer": self.answer("approve", case=1)})
        self.run_pass()
        self.assertIn("approved a head whose checks failed", self.state()["issues"][0]["body"])
        self.assertTrue({LABEL_HUMAN_INTERVENTION, "reviewed"} <= self.labels())

    def test_an_unconfirmed_approval_is_escalated(self) -> None:
        self.green()
        self.plan({"answer": self.answer("approve", case=1)})
        self.run_pass()
        self.assertIn("could not be confirmed", self.state()["issues"][0]["body"])

    def test_a_rerun_is_not_terminal_and_is_bounded_to_once_per_head(self) -> None:
        self.plan({"answer": self.answer("rerun", case=2), "commands": [["gh", "run", "rerun", "1", "--failed"]]})
        self.run_pass()
        self.assertIn(f"<!-- forge-review:{self.head}:rerun -->", self.comments()[-1])
        self.assertEqual({"GenAI", "library-new-request"}, self.labels())
        self.assertIn("1 rerun of this head", self.invocations()[0]["prompt"])
        # The rerun failed again: the next pass says no rerun is left, and a second one ends in an issue.
        state: dict[str, Any] = self.state()
        state["runs"][self.head] = [{"databaseId": 1, "status": "completed", "attempt": 2}]
        self.save(state)
        self.run_pass()
        self.assertIn("0 rerun of this head", self.invocations()[1]["prompt"])
        self.assertIn("already rerun once", self.state()["issues"][0]["body"])
        self.assertTrue({LABEL_HUMAN_INTERVENTION, "reviewed"} <= self.labels())

    def test_a_pushed_fix_is_confirmed_marked_and_not_terminal(self) -> None:
        self.plan({"answer": self.answer("fix"), "files": {TEST: "class LibTest { }\n"},
                   "commands": [["git", "push", "-q", "origin", "HEAD:refs/heads/{branch}",
                                 f"HEAD:refs/pull/{NUMBER}/head"]]})
        printed: str = self.run_pass()
        self.assertNotEqual(self.head, self.remote_head())
        self.assertIn("fix: pushed", printed)
        self.assertIn(f"<!-- forge-review:{self.head}:push -->", self.comments()[-1])
        self.assertNotIn("reviewed", self.labels())
        self.assertNotIn("in-review", self.labels())

    def test_the_third_fix_push_is_escalated(self) -> None:
        self.update(comments=[{"author": {"login": LOGIN}, "body": f"<!-- forge-review:{'a' * 40}:push -->"},
                              {"author": {"login": LOGIN}, "body": f"<!-- forge-review:{'b' * 40}:push -->"},
                              {"author": {"login": "someone"}, "body": f"<!-- forge-review:{'c' * 40}:push -->"}])
        self.plan({"answer": self.answer("fix"), "files": {TEST: "class LibTest { }\n"}})
        self.run_pass()
        self.assertIn("0 fix\npushes", self.invocations()[0]["prompt"])
        self.assertIn("no fix push left", self.state()["issues"][0]["body"])
        self.assertTrue({LABEL_HUMAN_INTERVENTION, "reviewed"} <= self.labels())

    def test_a_fix_outside_the_contribution_is_escalated(self) -> None:
        self.plan({"answer": self.answer("fix"), "files": {"README.md": "changed\n"},
                   "commands": [["git", "push", "-q", "origin", "HEAD:refs/heads/{branch}",
                                 f"HEAD:refs/pull/{NUMBER}/head"]]})
        self.run_pass()
        self.assertIn("changes README.md", self.state()["issues"][0]["body"])

    def test_a_fix_to_another_version_is_escalated(self) -> None:
        other: str = "tests/src/org.example/lib/0.9.0/LibTest.java"
        self.plan({"answer": self.answer("fix"), "files": {other: "class LibTest { }\n"},
                   "commands": [["git", "push", "-q", "origin", "HEAD:refs/heads/{branch}",
                                 f"HEAD:refs/pull/{NUMBER}/head"]]})
        self.run_pass()
        self.assertIn(f"changes {other}", self.state()["issues"][0]["body"])

    def test_a_fix_may_route_metadata_to_its_owner(self) -> None:
        owner: str = "metadata/org.example/owner/2.0.0/reachability-metadata.json"
        _git("checkout", "-q", BRANCH, cwd=self.repo)
        self.write("metadata/org.example/owner/index.json",
                   json.dumps([{"metadata-version": "2.0.0", "allowed-packages": ["org.example.owner"]}]))
        self.write(owner, json.dumps({"reflection": []}))
        _git("add", "-A", cwd=self.repo)
        _git("commit", "-q", "-m", "owner hosted", cwd=self.repo)
        self.head = _git("rev-parse", "HEAD", cwd=self.repo)
        _git("push", "-q", "origin", BRANCH, f"{BRANCH}:refs/pull/{NUMBER}/head", cwd=self.repo)
        _git("checkout", "-q", "master", cwd=self.repo)
        state: dict[str, Any] = self.state()
        state["checks"][self.head] = [check("test (25)", conclusion="failure")]
        self.save(state)
        routed: dict[str, Any] = {"reflection": [{"type": "org.example.owner.Codec",
                                                  "condition": {"typeReached": "org.example.owner.Api"}}]}
        self.plan({"answer": self.answer("fix"), "files": {owner: json.dumps(routed)},
                   "commands": [["git", "push", "-q", "origin", "HEAD:refs/heads/{branch}",
                                 f"HEAD:refs/pull/{NUMBER}/head"]]})
        self.assertIn("fix: pushed", self.run_pass())

    def test_a_fix_is_judged_by_its_own_paths_not_the_head_s(self) -> None:
        _git("checkout", "-q", BRANCH, cwd=self.repo)
        self.write("metadata/org.example/sibling/1.0.0/reachability-metadata.json", "{}\n")
        _git("add", "-A", cwd=self.repo)
        _git("commit", "-q", "-m", "sibling split", cwd=self.repo)
        self.head = _git("rev-parse", "HEAD", cwd=self.repo)
        _git("push", "-q", "origin", BRANCH, f"{BRANCH}:refs/pull/{NUMBER}/head", cwd=self.repo)
        _git("checkout", "-q", "master", cwd=self.repo)
        state: dict[str, Any] = self.state()
        state["checks"][self.head] = [check("test (25)", conclusion="failure")]
        self.save(state)
        self.plan({"answer": self.answer("fix"), "files": {TEST: "class LibTest { }\n"},
                   "commands": [["git", "push", "-q", "origin", "HEAD:refs/heads/{branch}",
                                 f"HEAD:refs/pull/{NUMBER}/head"]]})
        self.assertIn("fix: pushed", self.run_pass())

    def test_a_fix_may_merge_master(self) -> None:
        self.write("README.md", "moved on\n")
        _git("commit", "-q", "-am", "master moves", cwd=self.repo)
        _git("push", "-q", "origin", "master", cwd=self.repo)
        self.plan({"answer": self.answer("fix"), "files": {TEST: "class LibTest { }\n"},
                   "commands": [["git", "-c", "user.name=A", "-c", "user.email=a@example.com", "merge", "-q",
                                 "--no-edit", "origin/master"],
                                ["git", "push", "-q", "origin", "HEAD:refs/heads/{branch}",
                                 f"HEAD:refs/pull/{NUMBER}/head"]]})
        self.assertIn("fix: pushed", self.run_pass())

    def test_an_issue_reuses_its_signature_and_ends_the_review(self) -> None:
        state: dict[str, Any] = self.state()
        state["issues"].append({"number": 42, "state": "OPEN", "title": "NI 25.0 bug",
                                "body": "...\n\n<!-- forge-ci-signature: ni-25.0-type-not-found -->"})
        self.save(state)
        self.update(labels=[*self.pull()["labels"], {"name": LABEL_HUMAN_INTERVENTION_FIXED}])
        self.plan({"answer": self.answer("issue", case=4, cause="Native Image 25.0 bug",
                                         issue={"title": "t", "body": "b", "signature": "NI 25.0 type not found"})})
        printed: str = self.run_pass()
        self.assertIn("issue (terminal): reused #42", printed)
        self.assertEqual(1, len(self.state()["issues"]))
        self.assertEqual({"GenAI", "library-new-request", LABEL_HUMAN_INTERVENTION, "reviewed"}, self.labels())
        self.assertIn("tracked in #42", self.comments()[-1])

    def signed_issues(self) -> None:
        """Open issues the review may reuse, and ones it must not list."""
        def issue(number: int, key: str | None, state: str = "OPEN", *labels: str) -> dict[str, Any]:
            return {"number": number, "state": state, "title": f"Failure {number}",
                    "body": "..." + (f"\n\n<!-- forge-ci-signature: {key} -->" if key else ""),
                    "labels": [{"name": name} for name in labels]}
        state: dict[str, Any] = self.state()
        state["issues"] = [issue(39, "older-failure"), issue(40, "spring-side-failure"),
                           issue(41, "ni-25.0-clinit-mismatch", "OPEN", LABEL_GRAALVM_BUG),
                           issue(42, "closed-failure", "CLOSED"), issue(43, "pr-7-0123456789ab-unverified"),
                           issue(44, None)]
        self.save(state)

    def test_the_prompt_lists_open_signature_issues_graalvm_bug_first(self) -> None:
        self.signed_issues()
        with patch.object(pr_review, "ISSUE_LIMIT", 2):
            self.run_pass(dry_run=True)
        prompt: str = self.invocations()[0]["prompt"]
        listed: str = (f"- #41 `ni-25.0-clinit-mismatch` [{LABEL_GRAALVM_BUG}]: Failure 41\n"
                       f"- #40 `spring-side-failure`: Failure 40\n")
        self.assertIn(listed, prompt)
        for absent in ("older-failure", "closed-failure", "pr-7-0123456789ab", "#44"):
            self.assertNotIn(absent, prompt)

    def test_an_answer_with_a_listed_signature_links_that_issue(self) -> None:
        self.signed_issues()
        self.plan({"answer": self.answer("issue", case=4, cause="Native Image 25.0 bug",
                                         issue={"title": "t", "body": "b", "signature": "ni-25.0-clinit-mismatch"})})
        self.assertIn("issue (terminal): reused #41", self.run_pass())
        self.assertEqual(6, len(self.state()["issues"]))
        self.assertIn("tracked in #41", self.comments()[-1])
        self.assertTrue({LABEL_HUMAN_INTERVENTION, "reviewed"} <= self.labels())

    def test_a_failure_no_open_issue_tracks_opens_one(self) -> None:
        self.signed_issues()
        self.plan({"answer": self.answer("issue", case=3, cause="Native Image 25.0 bug",
                                         issue={"title": "New", "body": "b", "signature": "ni-25.0-new-failure"})})
        self.assertIn("issue (terminal): opened #506", self.run_pass())
        opened: dict[str, Any] = self.state()["issues"][-1]
        self.assertEqual(("New", "b\n\n<!-- forge-ci-signature: ni-25.0-new-failure -->"),
                         (opened["title"], opened["body"]))
        self.assertIn("tracked in #506", self.comments()[-1])

    def test_a_missing_answer_is_escalated_with_the_evidence(self) -> None:
        self.run_pass()
        body: str = self.state()["issues"][0]["body"]
        self.assertIn("missing or malformed", body)
        self.assertIn("toLeakAwareBuffer", body)

    def test_a_pass_reviews_at_most_three_pull_requests_oldest_first(self) -> None:
        state: dict[str, Any] = self.state()
        for number in (8, 9, 10):
            state["prs"][str(number)] = self.pull(number, f"2026-09-0{number - 7}T00:00:00Z")
            _git("push", "-q", "origin", f"{self.head}:refs/pull/{number}/head", cwd=self.repo)
        self.save(state)
        self.run_pass(dry_run=True)
        invoked: list[str] = [line for line in self.logged.splitlines() if "invoking the agent" in line]
        self.assertEqual(["#8", "#9", "#10"], [line.split()[1] for line in invoked])

    def test_a_dry_run_writes_nothing_and_withholds_credentials(self) -> None:
        self.update(body=CHUNK_BODY)
        self.plan({"answer": self.answer("fix"), "files": {TEST: "class LibTest { }\n"}})
        printed: str = self.run_pass(dry_run=True)
        self.assertIn("fix: would push", printed)
        self.assertEqual((self.head, [], [], {"GenAI", "library-new-request"}),
                         (self.remote_head(), self.comments(), self.state()["issues"], self.labels()))
        invocation: dict[str, Any] = self.invocations()[0]
        self.assertEqual("forge-review-dry-run", invocation["GH_TOKEN"])
        self.assertIn("pushinsteadof git@github.com:", invocation["push_rewrites"])
        self.assertIn("This is a dry run.", invocation["prompt"])
        with open(self.state_path + ".calls", encoding="utf-8") as handle:
            calls: str = handle.read()
        for write in ('"edit"', '"comment"', '"create"', '"rerun"', '"review"', '"merge"', '"POST"', '"DELETE"'):
            self.assertNotIn(write, calls)

    def test_a_dry_run_replays_a_closed_pull_request_at_an_earlier_head(self) -> None:
        self.update(state="MERGED", reviewDecision="APPROVED",
                    labels=[*self.pull()["labels"], {"name": LABEL_HUMAN_INTERVENTION}])
        self.plan({"answer": self.answer("issue", case=4, cause="Native Image 25.0 bug",
                                         issue={"title": "t", "body": "b", "signature": "ni-bug"})})
        printed: str = self.run_pass(dry_run=True, numbers=[NUMBER], head=self.head)
        self.assertIn(f"#{NUMBER} is merged; replaying it", self.logged)
        self.assertIn("issue (terminal): would open \"t\" [ni-bug]", printed)
        self.assertIn("case 4, Native Image 25.0 bug", printed)

    def test_the_fix_state_metadata_rules_reach_the_prompt(self) -> None:
        rules: str = pr_review.metadata_rules()
        self.assertTrue(rules.startswith("Metadata\n"))
        self.assertIn("typeReached", rules)
        self.assertIn("Native Image", rules)
        self.assertNotIn("Before your final message", rules)

    def test_the_primary_error_decides_in_the_review_and_the_fix_state(self) -> None:
        """A suppressed `HostedError` is not the GraalVM defect (§root/FS-graalvm-blocked-work.1)."""
        rule: str = ("The primary error decides: a `VMError` or `HostedError` reported only as `Suppressed:` beside "
                     "another failure, such as oracle/graal#14823's \"Reflection metadata cannot be queried before "
                     "analysis is complete\", is not the cause; handle the primary error under the usual rules.")
        self.run_pass()
        with open(pr_review.STATES, encoding="utf-8") as handle:
            fix: str = yaml.safe_load(handle)["states"]["fix"]["instructions"]
        for prompt in (self.invocations()[0]["prompt"], fix):
            self.assertIn(rule, " ".join(prompt.split()))


class RouteLabelTest(unittest.TestCase):

    def test_every_repair_label_marks_a_forge_pull_request(self) -> None:
        """Each `fixes-*` label of §FS-forge-scope, as the trusted publisher applies it beside `GenAI`."""
        for label in ("fixes-javac-fail", "fixes-java-run-fail", "fixes-native-image-build-fail",
                      "fixes-native-image-run-fail"):
            with self.subTest(label=label):
                pull: dict[str, Any] = {"labels": [{"name": "GenAI"}, {"name": label}]}
                self.assertEqual(label, pr_review_state.route_label(pull))
                self.assertIn(label, SUPPORTED_FORGE_REVIEW_TASK_TYPES)


if __name__ == "__main__":
    unittest.main()
