# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import subprocess
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

from git_scripts import local_branch_review as module
from utility_scripts.local_ci_verification import LocalCIVerificationResult
from utility_scripts.metrics_writer import read_pending_metrics, write_pending_metrics


def _git(repo_path: str, *args: str) -> str:
    return subprocess.check_output(["git", *args], cwd=repo_path, text=True).strip()


def _write(repo_path: str, relative_path: str, contents: str) -> None:
    absolute_path = os.path.join(repo_path, relative_path)
    os.makedirs(os.path.dirname(absolute_path), exist_ok=True)
    with open(absolute_path, "w", encoding="utf-8") as target_file:
        target_file.write(contents)


def _init_repo(repo_path: str) -> None:
    subprocess.run(["git", "init", "-q"], cwd=repo_path, check=True)
    subprocess.run(["git", "config", "user.name", "Forge Test"], cwd=repo_path, check=True)
    subprocess.run(
        ["git", "config", "user.email", "forge-test@example.invalid"],
        cwd=repo_path,
        check=True,
    )
    _write(repo_path, "source.txt", "before\n")
    subprocess.run(["git", "add", "."], cwd=repo_path, check=True)
    subprocess.run(["git", "commit", "-qm", "Initial"], cwd=repo_path, check=True)


def _tree(digest: str = "stable") -> module.PublishableTree:
    return module.PublishableTree(head="reviewed", digest=digest, status=())


def _verdict() -> module.LocalReviewVerdict:
    return module.LocalReviewVerdict(
        decision="approved",
        review_comment="Checked every applicable rule.",
        finding_title="Unsafe test shortcut",
        finding_body="The test called an internal method directly.",
        fix_note="Replaced the shortcut with the supported public API.",
    )


class LocalBranchReviewTests(unittest.TestCase):
    def test_verdict_reader_preserves_every_reviewer_field(self) -> None:
        payload = {
            "decision": "approved",
            "review_comment": "Checked every rule.\n",
            "finding_title": "Reusable title",
            "finding_body": "Concrete body.\n",
            "fix_note": "Changed the public call path.\n",
        }
        with tempfile.TemporaryDirectory() as directory:
            verdict_path = os.path.join(directory, "verdict.json")
            with open(verdict_path, "w", encoding="utf-8") as verdict_file:
                json.dump(payload, verdict_file)

            verdict = module._read_verdict(verdict_path)

        self.assertIsNotNone(verdict)
        self.assertEqual(verdict.decision, payload["decision"])
        self.assertEqual(verdict.review_comment, payload["review_comment"])
        self.assertEqual(verdict.finding_title, payload["finding_title"])
        self.assertEqual(verdict.finding_body, payload["finding_body"])
        self.assertEqual(verdict.fix_note, payload["fix_note"])

    def test_git_detects_reviewer_edits_and_ignores_findings(self) -> None:
        with tempfile.TemporaryDirectory() as repo_path:
            subprocess.run(["git", "init", "-q"], cwd=repo_path, check=True)
            subprocess.run(["git", "config", "user.name", "Forge Test"], cwd=repo_path, check=True)
            subprocess.run(
                ["git", "config", "user.email", "forge-test@example.invalid"],
                cwd=repo_path,
                check=True,
            )
            os.makedirs(os.path.join(repo_path, "forge"))
            source_path = os.path.join(repo_path, "source.txt")
            findings_path = os.path.join(repo_path, module.FINDINGS_RELATIVE_PATH)
            with open(source_path, "w", encoding="utf-8") as source_file:
                source_file.write("before\n")
            with open(findings_path, "w", encoding="utf-8") as findings_file:
                findings_file.write("original findings\n")
            subprocess.run(["git", "add", "."], cwd=repo_path, check=True)
            subprocess.run(["git", "commit", "-qm", "Initial"], cwd=repo_path, check=True)
            original_commit = _git(repo_path, "rev-parse", "HEAD")

            with open(source_path, "w", encoding="utf-8") as source_file:
                source_file.write("reviewer edit\n")
            with open(findings_path, "w", encoding="utf-8") as findings_file:
                findings_file.write("reviewer must not own this file\n")

            changed_paths = module._commit_reviewer_edits(repo_path, original_commit)

            self.assertEqual(changed_paths, ["source.txt"])
            self.assertNotEqual(_git(repo_path, "rev-parse", "HEAD"), original_commit)
            with open(findings_path, encoding="utf-8") as findings_file:
                self.assertEqual(findings_file.read(), "original findings\n")

    def test_unchanged_tree_does_not_rerun_checks(self) -> None:
        verification = LocalCIVerificationResult(status="success", base_commit="base")
        descriptor_input = SimpleNamespace(timestamp="2026-08-25T10:00:00Z")
        execution = module.ReviewExecution(_verdict(), "task-logs/review.log", [])

        with patch.object(module, "_load_persisted_outcome", return_value=None), \
                patch.object(module, "_git_stdout", side_effect=["base", "head"]), \
                patch.object(module, "_request_review", return_value=execution), \
                patch.object(module, "_record_outcome_finding"), \
                patch.object(module, "_persist_outcome"), \
                patch.object(module, "run_local_ci_verification") as local_ci:
            outcome = module.run_local_branch_review(
                repo_path="/repo",
                coordinates="org.example:demo:1.0.0",
                base_commit="base",
                task_type="library-new-request",
                local_ci_verification=verification,
                descriptor_input=descriptor_input,
            )

        self.assertIs(outcome.local_ci_verification, verification)
        local_ci.assert_not_called()

    def test_changed_tree_reruns_only_the_gate(self) -> None:
        verification = LocalCIVerificationResult(status="success", base_commit="base")
        reverified = LocalCIVerificationResult(status="success", base_commit="base")
        descriptor_input = SimpleNamespace(timestamp="2026-08-25T10:00:00Z")
        execution = module.ReviewExecution(_verdict(), "task-logs/review.log", ["source.txt"])

        with patch.object(module, "_load_persisted_outcome", return_value=None), \
                patch.object(module, "_git_stdout", side_effect=["base", "head"]), \
                patch.object(module, "_request_review", return_value=execution), \
                patch.object(module, "_record_outcome_finding"), \
                patch.object(module, "_persist_outcome"), \
                patch.object(module, "_capture_publishable_tree", return_value=_tree()), \
                patch.object(module, "run_local_ci_verification", return_value=reverified) as local_ci:
            outcome = module.run_local_branch_review(
                repo_path="/repo",
                coordinates="org.example:demo:1.0.0",
                base_commit="base",
                task_type="library-new-request",
                local_ci_verification=verification,
                descriptor_input=descriptor_input,
            )

        self.assertIs(outcome.local_ci_verification, reverified)
        local_ci.assert_called_once_with(
            repo_path="/repo",
            coordinates="org.example:demo:1.0.0",
            base_commit="base",
            metrics_repo_path=None,
            max_fixup_attempts=0,
        )

    def test_failed_gate_resets_and_restores_the_verified_record(self) -> None:
        verification = LocalCIVerificationResult(status="success", base_commit="base")
        failed = LocalCIVerificationResult(
            status="failure",
            base_commit="base",
            failure_gate="validate-index-files",
        )
        verdict = _verdict()
        outcome = module.LocalBranchReviewOutcome(
            model="gpt-test",
            session_log_path="task-logs/review.log",
            local_ci_verification=verification,
            verdict=verdict,
            changed_paths=["source.txt"],
        )

        with patch.object(
                module,
                "run_local_ci_verification",
                side_effect=module.LocalCIVerificationError(failed),
            ) as local_ci, patch.object(
                module, "_git_stdout", return_value="reviewed",
            ), patch.object(module, "_capture_publishable_tree", return_value=_tree()), \
                patch.object(module, "_reset_reviewer_edits") as reset:
            module._verify_reviewer_edits(
                repo_path="/repo",
                coordinates="org.example:demo:1.0.0",
                base_commit="base",
                verified_sha="verified",
                metrics_repo_path="/metrics",
                original_verification=verification,
                outcome=outcome,
            )

        self.assertEqual(outcome.verdict.decision, "rejected")
        self.assertEqual(outcome.verdict.action, "human-intervention")
        self.assertIn("validate-index-files", outcome.verdict.finding_body)
        reset.assert_called_once_with("/repo", "verified", "/metrics", verification)
        local_ci.assert_called_once_with(
            repo_path="/repo",
            coordinates="org.example:demo:1.0.0",
            base_commit="base",
            metrics_repo_path="/metrics",
            max_fixup_attempts=0,
        )

    def test_scratch_file_does_not_revert_a_reviewer_repair(self) -> None:
        """An unignored non-contribution file is not a gate mutation. §FS-local-branch-review"""
        verification = LocalCIVerificationResult(status="success", base_commit="base")
        reverified = LocalCIVerificationResult(status="success", base_commit="base")
        verdict = _verdict()
        outcome = module.LocalBranchReviewOutcome(
            model="gpt-test",
            session_log_path="task-logs/review.log",
            local_ci_verification=verification,
            verdict=verdict,
            changed_paths=["source.txt"],
        )

        with tempfile.TemporaryDirectory() as repo_path:
            _init_repo(repo_path)
            base_commit = _git(repo_path, "rev-parse", "HEAD")
            _write(repo_path, "source.txt", "reviewer repair\n")
            subprocess.run(["git", "commit", "-aqm", "Repair"], cwd=repo_path, check=True)
            _write(repo_path, "forge/.library_update_target.json", "{}\n")

            with patch.object(
                    module, "run_local_ci_verification", return_value=reverified,
            ), patch.object(module, "_reset_reviewer_edits") as reset:
                module._verify_reviewer_edits(
                    repo_path=repo_path,
                    coordinates="org.example:demo:1.0.0",
                    base_commit=base_commit,
                    verified_sha=base_commit,
                    metrics_repo_path=None,
                    original_verification=verification,
                    outcome=outcome,
                )

        reset.assert_not_called()
        self.assertIs(outcome.verdict, verdict)
        self.assertEqual(outcome.verdict.decision, "approved")
        self.assertIs(outcome.local_ci_verification, reverified)

    def test_gate_changing_a_contribution_path_reverts_and_names_it(self) -> None:
        """Only a publishable difference produces the mutation label. §FS-local-branch-review"""
        verification = LocalCIVerificationResult(status="success", base_commit="base")
        reverified = LocalCIVerificationResult(status="success", base_commit="base")
        outcome = module.LocalBranchReviewOutcome(
            model="gpt-test",
            session_log_path="task-logs/review.log",
            local_ci_verification=verification,
            verdict=_verdict(),
            changed_paths=["source.txt"],
        )

        with tempfile.TemporaryDirectory() as repo_path:
            _init_repo(repo_path)
            base_commit = _git(repo_path, "rev-parse", "HEAD")
            _write(repo_path, "forge/.library_update_target.json", "{}\n")

            def mutating_gate(**_: object) -> LocalCIVerificationResult:
                _write(repo_path, "source.txt", "gate rewrote this\n")
                return reverified

            with patch.object(
                    module, "run_local_ci_verification", side_effect=mutating_gate,
            ), patch.object(module, "_reset_reviewer_edits") as reset:
                module._verify_reviewer_edits(
                    repo_path=repo_path,
                    coordinates="org.example:demo:1.0.0",
                    base_commit=base_commit,
                    verified_sha=base_commit,
                    metrics_repo_path=None,
                    original_verification=verification,
                    outcome=outcome,
                )

        reset.assert_called_once_with(repo_path, base_commit, None, verification)
        self.assertEqual(outcome.verdict.decision, "rejected")
        self.assertEqual(outcome.verdict.action, "human-intervention")
        self.assertIn("pre-publication-gate-mutated-reviewed-tree", outcome.verdict.finding_body)
        self.assertIn("source.txt", outcome.verdict.finding_body)
        self.assertNotIn(".library_update_target.json", outcome.verdict.finding_body)
        self.assertIs(outcome.local_ci_verification, verification)

    def test_persisted_verdict_is_scoped_to_publication_timestamp(self) -> None:
        verification = LocalCIVerificationResult(status="success", base_commit="base")
        descriptor_input = SimpleNamespace(timestamp="2026-08-25T10:00:00Z")
        outcome = module.LocalBranchReviewOutcome(
            model="gpt-test",
            session_log_path="task-logs/review.log",
            local_ci_verification=verification,
            verdict=_verdict(),
        )
        with tempfile.TemporaryDirectory() as metrics_path:
            write_pending_metrics(metrics_path, {"status": "success"})
            module._persist_outcome(
                metrics_path,
                descriptor_input,
                outcome,
            )

            loaded = module._load_persisted_outcome(
                metrics_path,
                verification,
                descriptor_input,
            )
            stale = module._load_persisted_outcome(
                metrics_path,
                verification,
                SimpleNamespace(timestamp="2026-08-25T10:00:01Z"),
            )
            state = read_pending_metrics(metrics_path)[module.LOCAL_REVIEW_METRICS_KEY]

        self.assertIsNotNone(loaded)
        self.assertEqual(loaded.verdict, outcome.verdict)
        self.assertIsNone(stale)
        self.assertEqual(set(state), {"timestamp", "outcome"})
        self.assertEqual(state["outcome"]["decision"], "approved")

    def test_request_review_uses_centralized_analysis_agent(self) -> None:
        verdict = _verdict()
        verification = LocalCIVerificationResult(status="success", base_commit="base")
        descriptor_input = SimpleNamespace(render={})
        result = SimpleNamespace(
            return_code=0,
            log_path="/task-logs/local-review/codex.log",
            timed_out=False,
        )

        with tempfile.TemporaryDirectory() as directory:
            review_worktree = os.path.join(directory, "review-worktree")
            os.makedirs(review_worktree)
            with patch.object(
                    module,
                    "build_timestamped_task_log_path",
                    return_value=os.path.join(directory, "unavailable.log"),
                ), patch.object(
                    module, "_create_review_worktree", return_value=review_worktree,
                ), patch.object(module, "_write_evidence"), \
                patch.object(module, "_build_review_prompt", return_value="same prompt"), \
                patch.object(
                    module,
                    "get_analysis_agent",
                    return_value=SimpleNamespace(backend="codex", model="central-model"),
                ), patch.object(
                    module, "analysis_agent_run", return_value=result,
                ) as run_agent, patch.object(
                    module, "_read_verdict", return_value=verdict,
                ), patch.object(
                    module, "_commit_reviewer_edits", return_value=[],
                ), patch.object(module, "_remove_review_worktree") as remove_worktree:
                execution = module._request_review(
                    repo_path="/repo",
                    coordinates="org.example:demo:1.0.0",
                    base_sha="base",
                    verified_sha="verified",
                    task_type="library-new-request",
                    review_model="central-model",
                    local_ci_verification=verification,
                    descriptor_input=descriptor_input,
                )

        run_agent.assert_called_once_with(
            working_dir=review_worktree,
            context="same prompt",
            task_type="local-branch-review",
            library="org.example:demo:1.0.0",
            timeout=module.LOCAL_REVIEW_TIMEOUT_SECONDS,
            model="central-model",
            thinking_level="high",
        )
        self.assertEqual(execution.verdict, verdict)
        self.assertEqual(
            execution.session_log_path, module.display_log_path(result.log_path),
        )
        remove_worktree.assert_called_once_with("/repo", review_worktree)

    def test_review_prompt_finishes_finalization_before_verdict(self) -> None:
        prompt = module._build_review_prompt(
            coordinates="org.example:demo:1.0.0",
            base_sha="base",
            verified_sha="head",
            task_type="library-new-request",
            evidence_path="/tmp/evidence.json",
            verdict_path="/tmp/verdict.json",
            finalization_receipt_path="/tmp/finalization-receipt.json",
        )

        self.assertIn("This is the only semantic repair pass", prompt)
        self.assertIn("git_scripts.review_finalization", prompt)
        self.assertIn("foreign-metadata routing", prompt)
        self.assertLess(
            prompt.index("git_scripts.review_finalization"),
            prompt.index("Write exactly one JSON verdict"),
        )

    def test_review_model_uses_centralized_analysis_selection(self) -> None:
        selection = SimpleNamespace(model="central-model")
        with patch.object(module, "get_analysis_agent", return_value=selection):
            self.assertEqual(module.review_model_name(), "central-model")

    def test_explicit_analysis_model_wins_over_caller_preference(self) -> None:
        selection = SimpleNamespace(model="operator-model")
        with patch.dict(os.environ, {"FORGE_ANALYSIS_MODEL": "operator-model"}), \
                patch.object(module, "get_analysis_agent", return_value=selection):
            self.assertEqual(
                module.review_model_name("caller-model"), "operator-model",
            )


if __name__ == "__main__":
    unittest.main()
