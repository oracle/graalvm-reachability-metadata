# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

import json
import os
import tempfile
import unittest

from utility_scripts.schema_validator import validate_run_metrics


class RunMetricsSchemaTests(unittest.TestCase):
    def test_run_metrics_schema_accepts_chunk_ready_status(self) -> None:
        run_metrics = [
            {
                "timestamp": "2026-05-02T00:00:00Z",
                "library": "org.example:lib:1.0.0",
                "strategy_name": "dynamic_access_main_sources_pi_gpt-5.5",
                "status": "chunk_ready",
                "metrics": {
                    "input_tokens_used": 0,
                    "output_tokens_used": 0,
                    "iterations": 0,
                    "cost_usd": 0,
                    "tested_library_loc": 1,
                    "code_coverage_percent": 1.0,
                    "metadata_entries": 0,
                },
                "artifacts": {
                    "test_file": "tests/src/org.example/lib/1.0.0/src/test/java/org/example/LibTest.java",
                    "metadata_file": "metadata/org.example/lib/1.0.0/reflect-config.json",
                },
            },
        ]

        with tempfile.TemporaryDirectory() as tmpdir:
            metrics_path = os.path.join(tmpdir, "results.json")
            with open(metrics_path, "w", encoding="utf-8") as metrics_file:
                json.dump(run_metrics, metrics_file)

            validate_run_metrics(metrics_path)


if __name__ == "__main__":
    unittest.main()
