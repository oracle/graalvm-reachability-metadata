# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest
from unittest.mock import patch

from git_scripts import publish_code_coverage_improvement as module

FINAL_METRICS = os.path.join(
    os.path.dirname(__file__), "fixtures", "code_coverage", "final_metrics.json",
)


class CoveragePublicationTests(unittest.TestCase):

    def _metrics(self) -> dict:
        with open(FINAL_METRICS, encoding="utf-8") as fixture:
            return json.load(fixture)

    def test_token_usage_orders_phases_and_skips_unmeasured(self) -> None:
        with tempfile.TemporaryDirectory() as accounting:
            tasks = os.path.join(accounting, "tasks")
            os.makedirs(tasks)
            for phase, value in (("deep-coverage", 7), ("convert", 3)):
                with open(
                    os.path.join(tasks, f"ws.code-coverage-{phase}.json"),
                    "w",
                    encoding="utf-8",
                ) as handle:
                    json.dump({"direct": {
                        "input_total": {"value": value},
                        "input_cached_read": {"value": value * 2},
                        "output_total": {"value": None},
                    }}, handle)

            rows = module.load_token_usage(accounting)

        self.assertEqual([row["phase"] for row in rows], ["convert", "deep-coverage"])
        self.assertEqual(rows[0], {"phase": "convert", "input": 3, "cached": 6, "output": None})

    def test_token_usage_is_empty_without_accounting_directory(self) -> None:
        with tempfile.TemporaryDirectory() as empty:
            self.assertEqual(module.load_token_usage(empty), [])

    def test_commit_subject_is_at_most_sixty_characters(self) -> None:
        coordinate = f"com.{'verylong.' * 10}:artifact-with-long-name:1.0.0"

        subject = module._coverage_commit_subject(coordinate)

        self.assertEqual(len(subject), module.MAX_COMMIT_SUBJECT_LENGTH)
        self.assertTrue(subject.startswith("Improve code coverage for "))
        self.assertTrue(subject.endswith("..."))

    def test_stages_indexed_test_and_metadata_versions(self) -> None:
        with tempfile.TemporaryDirectory(prefix="coverage-stage-") as repo_path:
            group = "com.example"
            artifact = "demo"
            requested_version = "1.1.0"
            test_version = "shared-tests"
            metadata_version = "1.0.0"
            index_dir = os.path.join(repo_path, "metadata", group, artifact)
            os.makedirs(index_dir)
            with open(
                    os.path.join(index_dir, "index.json"),
                    "w",
                    encoding="utf-8",
            ) as index_file:
                json.dump(
                    [{
                        "metadata-version": metadata_version,
                        "test-version": test_version,
                        "tested-versions": [requested_version],
                    }],
                    index_file,
                )
            os.makedirs(os.path.join(index_dir, metadata_version))
            coverage_suite = os.path.join(
                "tests",
                "src",
                group,
                artifact,
                test_version,
                "code-coverage-improvement",
            )
            os.makedirs(os.path.join(repo_path, coverage_suite))

            with patch.object(module, "stage_and_commit") as stage_and_commit:
                module.stage_coverage_paths(
                    repo_path,
                    group,
                    artifact,
                    requested_version,
                    coverage_suite,
                )

        staged_paths, subject = stage_and_commit.call_args.args
        mapped_test_dir = os.path.join(
            "tests", "src", group, artifact, test_version
        )
        requested_test_dir = os.path.join(
            "tests", "src", group, artifact, requested_version
        )
        mapped_metadata_dir = os.path.join(
            "metadata", group, artifact, metadata_version
        )
        requested_metadata_dir = os.path.join(
            "metadata", group, artifact, requested_version
        )
        self.assertIn(mapped_test_dir, staged_paths)
        self.assertNotIn(requested_test_dir, staged_paths)
        self.assertEqual(
            subject,
            module._coverage_commit_subject(
                f"{group}:{artifact}:{requested_version}"
            ),
        )
        self.assertIn(mapped_metadata_dir, staged_paths)
        self.assertNotIn(requested_metadata_dir, staged_paths)
        self.assertEqual(stage_and_commit.call_args.kwargs["cwd"], repo_path)

    def _publish(
            self,
            branch_suffix: str | None = None,
            worker_agent: str = "pi[high]:openai-codex/gpt-5.6-luna",
            token_usage: list[dict] | None = None,
    ):
        """Return the `publish_branch` keyword arguments one publication produced."""
        with tempfile.TemporaryDirectory(prefix="coverage-publish-") as repo_path:
            finalization = os.path.join(repo_path, "finalization")
            os.makedirs(finalization)
            with open(
                    os.path.join(finalization, "final-metrics.json"),
                    "w",
                    encoding="utf-8",
            ) as metrics_file:
                json.dump(self._metrics(), metrics_file)
            coverage_suite = self._metrics()["coverageSuitePath"]
            os.makedirs(os.path.join(repo_path, coverage_suite))

            with patch.object(
                    module, "publish_branch", return_value=("ai/kimeta/b", None)
            ) as publish_branch, \
                    patch.object(
                        module, "load_token_usage", return_value=token_usage or []
                    ):
                module.publish(
                    repo_path,
                    "com.example:demo:1.0.0",
                    worker_agent,
                    8380,
                    finalization,
                    coverage_suite,
                    branch_suffix=branch_suffix,
                )

        return publish_branch.call_args.kwargs

    def _branch_suffix(self, **kwargs) -> str:
        return self._publish(**kwargs)["branch_suffix"]

    def test_branch_names_the_generating_model_without_a_suffix(self) -> None:
        self.assertEqual(
            self._branch_suffix(branch_suffix=None),
            "code-coverage-demo-1.0.0-gpt-5.6-luna",
        )
        self.assertEqual(
            self._branch_suffix(branch_suffix=""),
            "code-coverage-demo-1.0.0-gpt-5.6-luna",
        )

    def test_branch_model_separates_runs_of_one_coordinate(self) -> None:
        # Two models measuring one coordinate must not resolve to one branch,
        # and so to one pull request.
        self.assertEqual(
            self._branch_suffix(worker_agent="pi:openai-codex/gpt-5.6-sol"),
            "code-coverage-demo-1.0.0-gpt-5.6-sol",
        )
        self.assertNotEqual(
            self._branch_suffix(worker_agent="pi:openai-codex/gpt-5.6-sol"),
            self._branch_suffix(worker_agent="pi[high]:openai-codex/gpt-5.6-luna"),
        )

    def test_branch_suffix_labels_runs_that_share_a_model(self) -> None:
        self.assertEqual(
            self._branch_suffix(branch_suffix="cap400"),
            "code-coverage-demo-1.0.0-gpt-5.6-luna-cap400",
        )

    def test_publication_hands_the_descriptor_the_render_inputs(self) -> None:
        usage = [{"phase": "convert", "input": 1000, "cached": 2000, "output": 30}]

        descriptor_input = self._publish(token_usage=usage)["descriptor_input"]

        self.assertEqual(descriptor_input.task_type, "code-coverage-improvement")
        self.assertEqual(descriptor_input.template_type, "code-coverage-improvement")
        self.assertEqual(descriptor_input.issue_number, 8380)
        self.assertEqual(descriptor_input.status, "success")
        self.assertEqual(descriptor_input.render["worker_model"], "gpt-5.6-luna")
        self.assertEqual(descriptor_input.render["token_usage"], usage)
        self.assertEqual(
            descriptor_input.render["code_coverage"]["coordinate"],
            "com.example:demo:1.0.0",
        )

    def test_publication_timestamp_comes_from_finalization(self) -> None:
        # Publication identity must survive a retried publication state, so the
        # timestamp is the finalized run's, never the wall clock.
        descriptor_input = self._publish()["descriptor_input"]

        self.assertEqual(descriptor_input.timestamp, self._metrics()["generatedAt"])

    def test_descriptor_carries_only_the_rendered_evidence(self) -> None:
        descriptor_input = self._publish()["descriptor_input"]

        evidence = descriptor_input.render["code_coverage"]
        self.assertNotIn("targets", evidence)
        self.assertNotIn("pgoGuidance", evidence)
        self.assertNotIn("validationCommands", evidence)

    def test_model_slug_reads_the_model_out_of_a_rhei_target(self) -> None:
        self.assertEqual(
            module.model_slug("pi[high]:openai-codex/gpt-5.6-luna"),
            "gpt-5.6-luna",
        )
        self.assertEqual(
            module.model_slug("codex[xhigh]:openai:gpt-5.5"), "gpt-5.5"
        )
        self.assertEqual(module.model_slug("gpt-5.6-sol"), "gpt-5.6-sol")

    def test_model_slug_rejects_a_target_without_a_model(self) -> None:
        with self.assertRaises(SystemExit):
            module.model_slug("pi[high]:openai-codex/")


if __name__ == "__main__":
    unittest.main()
