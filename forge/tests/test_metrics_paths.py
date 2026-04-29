# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest

from ai_workflows.add_new_library_support import (
    resolve_add_new_library_support_metrics_json,
    write_add_new_library_support_metrics,
)
from ai_workflows.java_fail_workflow import JAVAC_CONFIG, resolve_fix_metrics_json, write_fix_metrics


def _minimal_run_metrics(library: str = "org.example:demo:1.0.0") -> dict:
    return {
        "timestamp": "2026-04-29T13:46:31Z",
        "library": library,
        "strategy_name": "basic_iterative_pi_gpt-5.4",
        "status": "success",
        "metrics": {
            "input_tokens_used": 1,
            "output_tokens_used": 2,
            "iterations": 1,
            "cost_usd": 0.001,
            "tested_library_loc": 3,
            "code_coverage_percent": 4.0,
            "metadata_entries": 5,
        },
        "artifacts": {
            "test_file": "tests/src/org.example/demo/1.0.0/src/test/java/DemoTest.java",
            "metadata_file": "metadata/org.example/demo/1.0.0/reachability-metadata.json",
        },
    }


def _with_post_generation_intervention(run_metrics: dict) -> dict:
    run_metrics = dict(run_metrics)
    run_metrics["post_generation_intervention"] = {
        "stage": "metadata_fix_failed",
        "intervention_file": "post-gen-interventions/org.example_demo_1.0.0.md",
        "analysis_markdown": "Manual intervention report.",
    }
    return run_metrics


def _public_execution_metrics(run_metrics: dict) -> dict:
    run_metrics = dict(run_metrics)
    run_metrics.pop("post_generation_intervention", None)
    return run_metrics


class MetricsPathTests(unittest.TestCase):
    def test_in_repo_add_new_library_support_metrics_resolves_to_stats_path(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            reachability_repo = os.path.join(temp_dir, "graalvm-reachability-metadata")
            forge_root = os.path.join(reachability_repo, "forge")
            metrics_repo_dir = os.path.join(forge_root, "script_run_metrics")
            os.makedirs(os.path.join(reachability_repo, "metadata"))
            os.makedirs(os.path.join(reachability_repo, "stats"))
            os.makedirs(metrics_repo_dir)

            run_metrics = _with_post_generation_intervention(_minimal_run_metrics())
            metrics_json = resolve_add_new_library_support_metrics_json(
                run_metrics=run_metrics,
                metrics_repo_dir=metrics_repo_dir,
                metrics_repo_root=forge_root,
                is_benchmark_mode=False,
            )

            self.assertEqual(
                metrics_json,
                os.path.join(
                    reachability_repo,
                    "stats",
                    "org.example",
                    "demo",
                    "1.0.0",
                    "execution-metrics.json",
                ),
            )

            write_add_new_library_support_metrics(
                run_metrics=run_metrics,
                metrics_json=metrics_json,
                is_benchmark_mode=False,
                package="org.example",
                artifact="demo",
                library_version="1.0.0",
                metrics_repo_root=forge_root,
            )

            self.assertFalse(os.path.exists(os.path.join(metrics_repo_dir, "add_new_library_support.json")))
            with open(metrics_json, "r", encoding="utf-8") as file_handle:
                persisted = json.load(file_handle)
            self.assertEqual(
                persisted,
                {"add_new_library_support:2026-04-29": _public_execution_metrics(run_metrics)},
            )
            with open(os.path.join(forge_root, ".pending_metrics.json"), "r", encoding="utf-8") as file_handle:
                pending = json.load(file_handle)
            self.assertEqual(pending, run_metrics)

    def test_in_repo_java_fail_metrics_resolves_to_stats_path(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            reachability_repo = os.path.join(temp_dir, "graalvm-reachability-metadata")
            forge_root = os.path.join(reachability_repo, "forge")
            metrics_repo_dir = os.path.join(forge_root, "script_run_metrics")
            os.makedirs(os.path.join(reachability_repo, "metadata"))
            os.makedirs(os.path.join(reachability_repo, "stats"))
            os.makedirs(metrics_repo_dir)

            run_metrics = _with_post_generation_intervention(_minimal_run_metrics())
            metrics_json = resolve_fix_metrics_json(
                config=JAVAC_CONFIG,
                run_metrics=run_metrics,
                metrics_repo_dir=metrics_repo_dir,
                metrics_repo_root=forge_root,
            )

            self.assertEqual(
                metrics_json,
                os.path.join(
                    reachability_repo,
                    "stats",
                    "org.example",
                    "demo",
                    "1.0.0",
                    "execution-metrics.json",
                ),
            )

            write_fix_metrics(
                config=JAVAC_CONFIG,
                run_metrics=run_metrics,
                metrics_repo_dir=metrics_repo_dir,
                metrics_repo_root=forge_root,
            )

            self.assertFalse(os.path.exists(os.path.join(metrics_repo_dir, "fix_javac_fail.json")))
            with open(metrics_json, "r", encoding="utf-8") as file_handle:
                persisted = json.load(file_handle)
            self.assertEqual(persisted, {"fix_javac_fail:2026-04-29": _public_execution_metrics(run_metrics)})
            with open(os.path.join(forge_root, ".pending_metrics.json"), "r", encoding="utf-8") as file_handle:
                pending = json.load(file_handle)
            self.assertEqual(pending, run_metrics)


if __name__ == "__main__":
    unittest.main()
