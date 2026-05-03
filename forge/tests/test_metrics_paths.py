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
from utility_scripts.library_stats import load_library_stats_entry, resolve_stats_file_path
from utility_scripts.metrics_writer import (
    count_metadata_entries,
    count_test_only_metadata_entries,
    create_run_metrics_output_json,
    resolve_artifact_paths,
)


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


class DummyAgent:
    total_tokens_sent = 1
    total_tokens_received = 2
    cached_input_tokens_used = 0


class MetricsPathTests(unittest.TestCase):
    def test_count_metadata_entries_uses_metadata_version_for_tested_version(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            metadata_dir = os.path.join(temp_dir, "metadata", "org.example", "demo", "1.0.0")
            metadata_index_dir = os.path.dirname(metadata_dir)
            os.makedirs(metadata_dir)
            with open(os.path.join(metadata_index_dir, "index.json"), "w", encoding="utf-8") as file:
                json.dump(
                    [
                        {
                            "metadata-version": "1.0.0",
                            "tested-versions": ["1.0.0", "1.0.1"],
                        }
                    ],
                    file,
                )
            with open(os.path.join(metadata_dir, "reachability-metadata.json"), "w", encoding="utf-8") as file:
                json.dump(
                    {
                        "reflection": [
                            {
                                "type": "org.example.Demo",
                                "methods": [{"name": "create"}],
                            }
                        ]
                    },
                    file,
                )

            self.assertEqual(count_metadata_entries(temp_dir, "org.example", "demo", "1.0.1"), 2)

    def test_resolve_artifact_paths_uses_metadata_version_for_tested_version(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            metadata_dir = os.path.join(temp_dir, "metadata", "org.example", "demo", "1.0.0")
            metadata_index_dir = os.path.dirname(metadata_dir)
            tests_root = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "java",
            )
            os.makedirs(metadata_dir)
            os.makedirs(tests_root)
            with open(os.path.join(metadata_index_dir, "index.json"), "w", encoding="utf-8") as file:
                json.dump(
                    [
                        {
                            "metadata-version": "1.0.0",
                            "tested-versions": ["1.0.0", "1.0.1"],
                        }
                    ],
                    file,
                )
            with open(os.path.join(metadata_dir, "reachability-metadata.json"), "w", encoding="utf-8") as file:
                json.dump({"reflection": [{"type": "org.example.Demo"}]}, file)
            with open(os.path.join(tests_root, "DemoTest.java"), "w", encoding="utf-8") as file:
                file.write("class DemoTest {}\n")

            _test_file, metadata_file = resolve_artifact_paths(
                temp_dir,
                "org.example",
                "demo",
                "1.0.1",
                tests_root,
            )

            self.assertEqual(
                metadata_file,
                os.path.join("metadata", "org.example", "demo", "1.0.0", "reachability-metadata.json"),
            )

    def test_test_version_resolution_uses_first_tested_versions_entry(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            metadata_index_dir = os.path.join(temp_dir, "metadata", "org.example", "demo")
            shared_metadata_dir = os.path.join(metadata_index_dir, "11.14.1")
            exact_metadata_dir = os.path.join(metadata_index_dir, "10.20.1")
            shared_test_metadata_dir = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "11.14.1",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            exact_test_metadata_dir = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "10.20.1",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            os.makedirs(shared_metadata_dir)
            os.makedirs(exact_metadata_dir)
            os.makedirs(shared_test_metadata_dir)
            os.makedirs(exact_test_metadata_dir)
            with open(os.path.join(metadata_index_dir, "index.json"), "w", encoding="utf-8") as file:
                json.dump(
                    [
                        {
                            "metadata-version": "11.14.1",
                            "tested-versions": ["10.20.1", "11.14.1"],
                        },
                        {
                            "metadata-version": "10.20.1",
                            "tested-versions": ["10.20.1"],
                        },
                    ],
                    file,
                )
            with open(
                    os.path.join(shared_test_metadata_dir, "reachability-metadata.json"),
                    "w",
                    encoding="utf-8",
            ) as file:
                json.dump({"reflection": [{"type": "org.example.Shared"}]}, file)
            with open(
                    os.path.join(shared_metadata_dir, "reachability-metadata.json"),
                    "w",
                    encoding="utf-8",
            ) as file:
                json.dump({"reflection": [{"type": "org.example.Shared"}]}, file)
            with open(
                    os.path.join(exact_test_metadata_dir, "reachability-metadata.json"),
                    "w",
                    encoding="utf-8",
            ) as file:
                json.dump({"reflection": [{"type": "org.example.Exact"}, {"type": "org.example.Other"}]}, file)
            with open(
                    os.path.join(exact_metadata_dir, "reachability-metadata.json"),
                    "w",
                    encoding="utf-8",
            ) as file:
                json.dump({"reflection": [{"type": "org.example.Exact"}, {"type": "org.example.Other"}]}, file)

            self.assertEqual(count_metadata_entries(temp_dir, "org.example", "demo", "10.20.1"), 1)
            self.assertEqual(count_test_only_metadata_entries(temp_dir, "org.example", "demo", "10.20.1"), 1)

    def test_stats_resolution_uses_first_tested_versions_entry(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            metadata_index_dir = os.path.join(temp_dir, "metadata", "org.example", "demo")
            shared_metadata_dir = os.path.join(metadata_index_dir, "11.14.1")
            exact_metadata_dir = os.path.join(metadata_index_dir, "10.20.1")
            shared_stats_dir = os.path.join(temp_dir, "stats", "org.example", "demo", "11.14.1")
            exact_stats_dir = os.path.join(temp_dir, "stats", "org.example", "demo", "10.20.1")
            os.makedirs(shared_metadata_dir)
            os.makedirs(exact_metadata_dir)
            os.makedirs(shared_stats_dir)
            os.makedirs(exact_stats_dir)
            with open(os.path.join(metadata_index_dir, "index.json"), "w", encoding="utf-8") as file:
                json.dump(
                    [
                        {
                            "metadata-version": "11.14.1",
                            "tested-versions": ["10.20.1", "11.14.1"],
                        },
                        {
                            "metadata-version": "10.20.1",
                            "tested-versions": ["10.20.1"],
                        },
                    ],
                    file,
                )
            with open(os.path.join(shared_stats_dir, "stats.json"), "w", encoding="utf-8") as file:
                json.dump(
                    {
                        "versions": [
                            {
                                "version": "10.20.1",
                                "dynamicAccess": {
                                    "coveredCalls": 1,
                                    "totalCalls": 4,
                                    "coverageRatio": 0.25,
                                    "breakdown": {},
                                },
                            }
                        ]
                    },
                    file,
                )
            with open(os.path.join(exact_stats_dir, "stats.json"), "w", encoding="utf-8") as file:
                json.dump(
                    {
                        "versions": [
                            {
                                "version": "10.20.1",
                                "dynamicAccess": {
                                    "coveredCalls": 9,
                                    "totalCalls": 9,
                                    "coverageRatio": 1.0,
                                    "breakdown": {},
                                },
                            }
                        ]
                    },
                    file,
                )

            self.assertEqual(
                resolve_stats_file_path(temp_dir, "org.example", "demo", "10.20.1"),
                os.path.join(shared_stats_dir, "stats.json"),
            )
            stats_entry = load_library_stats_entry(temp_dir, "org.example", "demo", "10.20.1")
            self.assertEqual(stats_entry["dynamicAccess"]["coveredCalls"], 1)

    def test_count_test_only_metadata_entries_counts_direct_native_image_reachability_file(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            test_metadata_dir = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            os.makedirs(test_metadata_dir)
            with open(os.path.join(test_metadata_dir, "reachability-metadata.json"), "w", encoding="utf-8") as file:
                json.dump(
                    {
                        "reflection": [
                            {
                                "condition": {"typeReached": "org.example.Demo"},
                                "type": "org.example.TestFixture",
                                "methods": [{"name": "create"}, {"name": "read"}],
                            }
                        ]
                    },
                    file,
                )

            self.assertEqual(count_test_only_metadata_entries(temp_dir, "org.example", "demo", "1.0.0"), 3)

    def test_create_run_metrics_includes_test_only_metadata_entries_only_when_positive(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            tests_root = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "java",
            )
            metadata_dir = os.path.join(temp_dir, "metadata", "org.example", "demo", "1.0.0")
            metadata_index_dir = os.path.join(temp_dir, "metadata", "org.example", "demo")
            stats_dir = os.path.join(temp_dir, "stats", "org.example", "demo", "1.0.0")
            test_metadata_dir = os.path.join(
                temp_dir,
                "tests",
                "src",
                "org.example",
                "demo",
                "1.0.0",
                "src",
                "test",
                "resources",
                "META-INF",
                "native-image",
            )
            os.makedirs(tests_root)
            os.makedirs(metadata_dir)
            os.makedirs(stats_dir)
            os.makedirs(test_metadata_dir)
            with open(os.path.join(tests_root, "DemoTest.java"), "w", encoding="utf-8") as file:
                file.write("class DemoTest {}\n")
            with open(os.path.join(metadata_dir, "reachability-metadata.json"), "w", encoding="utf-8") as file:
                json.dump({"reflection": [{"type": "org.example.Demo"}]}, file)
            with open(os.path.join(metadata_index_dir, "index.json"), "w", encoding="utf-8") as file:
                json.dump(
                    [
                        {
                            "metadata-version": "1.0.0",
                            "tested-versions": ["1.0.0"],
                        }
                    ],
                    file,
                )
            with open(os.path.join(test_metadata_dir, "reachability-metadata.json"), "w", encoding="utf-8") as file:
                json.dump({"reflection": [{"type": "org.example.TestFixture"}]}, file)
            with open(os.path.join(stats_dir, "stats.json"), "w", encoding="utf-8") as file:
                json.dump(
                    {
                        "versions": [
                            {
                                "version": "1.0.0",
                                "libraryCoverage": {
                                    "line": {
                                        "covered": 1,
                                        "missed": 0,
                                        "total": 1,
                                        "ratio": 1.0,
                                    }
                                },
                            }
                        ]
                    },
                    file,
                )

            run_metrics = create_run_metrics_output_json(
                repo_path=temp_dir,
                package="org.example",
                artifact="demo",
                library_version="1.0.0",
                agent=DummyAgent(),
                model_name="oca/gpt-5.4",
                global_iterations=1,
                tests_root=tests_root,
                strategy_name="basic_iterative_pi_gpt-5.4",
                status="success",
            )

            self.assertEqual(run_metrics["metrics"]["metadata_entries"], 1)
            self.assertEqual(run_metrics["metrics"]["test_only_metadata_entries"], 1)

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
