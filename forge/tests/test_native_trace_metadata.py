# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Unit tests for staged trace-metadata inspection, merging, and finalization.

Covers convergence hashing, native-image-utils delegation, per-cycle metadata
logging, and durable aggregation through the gate's outer loop.
"""

from __future__ import annotations

import io
import json
import os
import subprocess
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

# Tests run from the forge/ directory in CI; make the package imports work
# whether the test is invoked via pytest or `python -m unittest`.
_FORGE_ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(_FORGE_ROOT))

from utility_scripts import native_test_verification as ntv  # noqa: E402
from utility_scripts import native_trace_metadata as ntm  # noqa: E402

from tests.native_gate_support import (  # noqa: E402
    GATE_SUBPROCESS_RUN,
    GateHarness,
    _command_property,
    _make_complete_reachability_repo,
    _rmtree,
)


class ClassKeyTests(unittest.TestCase):

    def test_replaces_dollar_signs(self) -> None:
        self.assertEqual(
            ntm.class_key_from_class_name("com.foo.Bar$Inner"),
            "com.foo.Bar_Inner",
        )

    def test_keeps_dots_dashes_underscores(self) -> None:
        self.assertEqual(
            ntm.class_key_from_class_name("com.foo.Bar-Baz_qux"),
            "com.foo.Bar-Baz_qux",
        )


class CollectEntriesTests(unittest.TestCase):
    """Convergence semantics for the trace driver."""

    def _make_run(self, contents: dict[str, object]) -> str:
        run_dir = tempfile.mkdtemp(prefix="trace-run-")
        self.addCleanup(_rmtree, run_dir)
        for name, payload in contents.items():
            target = Path(run_dir) / name
            target.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(payload, (dict, list)):
                target.write_text(json.dumps(payload), encoding="utf-8")
            else:
                target.write_bytes(payload)  # type: ignore[arg-type]
        return run_dir

    def test_identical_runs_produce_identical_sets(self) -> None:
        a = self._make_run({"reachability-metadata.json": {"reflection": [{"type": "Foo"}]}})
        b = self._make_run({"reachability-metadata.json": {"reflection": [{"type": "Foo"}]}})
        self.assertEqual(ntm._metadata_entries(a), ntm._metadata_entries(b))

    def test_added_entry_changes_set(self) -> None:
        a = self._make_run({"m.json": {"reflection": [{"type": "Foo"}]}})
        b = self._make_run({"m.json": {"reflection": [{"type": "Foo"}, {"type": "Bar"}]}})
        added = ntm._metadata_entries(b) - ntm._metadata_entries(a)
        self.assertTrue(added, "adding a reflection entry must yield delta")

    def test_non_json_files_use_stable_hash(self) -> None:
        # Same opaque bytes in two separate runs must produce the same entry
        # string — guarding against hash() salting.
        a = self._make_run({"opaque.bin": b"\x00\x01\x02"})
        b = self._make_run({"opaque.bin": b"\x00\x01\x02"})
        self.assertEqual(ntm._metadata_entries(a), ntm._metadata_entries(b))

    def test_different_opaque_bytes_diverge(self) -> None:
        a = self._make_run({"opaque.bin": b"AAA"})
        b = self._make_run({"opaque.bin": b"BBB"})
        self.assertNotEqual(ntm._metadata_entries(a), ntm._metadata_entries(b))


class MetadataAggregationTests(unittest.TestCase):
    """Durable trace aggregation is delegated to native-image-utils."""

    def setUp(self) -> None:
        self.repo = tempfile.mkdtemp(prefix="repo-")
        self.addCleanup(_rmtree, self.repo)
        _make_complete_reachability_repo(self.repo)
        self.metadata_dir = Path(self.repo) / "metadata" / "g" / "a" / "1.0"
        self.metadata_dir.mkdir(parents=True)
        self.output_dir = tempfile.mkdtemp(prefix="native-trace-output-")
        self.addCleanup(_rmtree, self.output_dir)

    def test_uses_native_image_utils_to_preserve_order_sensitive_metadata(self) -> None:
        durable_metadata_path = self.metadata_dir / "reachability-metadata.json"
        durable_metadata_path.write_text(
            json.dumps({
                "reflection": [
                    {
                        "type": "com.example.Target",
                        "methods": [
                            {
                                "name": "m",
                                "parameterTypes": ["java.lang.String", "int"],
                            }
                        ],
                    }
                ]
            }),
            encoding="utf-8",
        )
        Path(self.output_dir, "reachability-metadata.json").write_text(
            json.dumps({
                "reflection": [
                    {
                        "type": "com.example.Target",
                        "methods": [
                            {
                                "name": "m",
                                "parameterTypes": ["int", "java.lang.String"],
                            }
                        ],
                    }
                ]
            }),
            encoding="utf-8",
        )
        merge_calls: list[list[str]] = []

        with patch(
            GATE_SUBPROCESS_RUN,
            side_effect=self._fake_native_image_utils_merge(merge_calls),
        ):
            self.assertTrue(ntm._finalize_staged_metadata(self.repo, "g:a:1.0", [self.output_dir]))

        durable_metadata = json.loads(durable_metadata_path.read_text(encoding="utf-8"))
        methods = [
            entry["methods"][0]["parameterTypes"]
            for entry in durable_metadata["reflection"]
        ]
        self.assertIn(["java.lang.String", "int"], methods)
        self.assertIn(["int", "java.lang.String"], methods)
        self.assertEqual(len(methods), 2)
        self.assertEqual(len(merge_calls), 1)
        self.assertIn(str(self.metadata_dir), _command_property(" ".join(merge_calls[0]), "-PinputDirs"))
        self.assertIn(self.output_dir, _command_property(" ".join(merge_calls[0]), "-PinputDirs"))

    @staticmethod
    def _fake_native_image_utils_merge(calls: list[list[str]]):
        def _fake(cmd, **kwargs):  # type: ignore[no-untyped-def]
            calls.append(list(cmd))
            input_dirs = next(
                (a.split("=", 1)[1] for a in cmd if a.startswith("-PinputDirs=")),
                "",
            ).split(",")
            output_dir = next(
                (a.split("=", 1)[1] for a in cmd if a.startswith("-PoutputDir=")),
                None,
            )
            merged_reflection = []
            for input_dir in input_dirs:
                metadata_path = Path(input_dir) / "reachability-metadata.json"
                if metadata_path.is_file():
                    try:
                        payload = json.loads(metadata_path.read_text(encoding="utf-8"))
                    except json.JSONDecodeError:
                        return subprocess.CompletedProcess(cmd, 1)
                    merged_reflection.extend(payload.get("reflection", []))
            if output_dir:
                Path(output_dir).mkdir(parents=True, exist_ok=True)
                Path(output_dir, "reachability-metadata.json").write_text(
                    json.dumps({"reflection": merged_reflection}),
                    encoding="utf-8",
                )
            return subprocess.CompletedProcess(cmd, 0)

        return _fake


class PrintCollectedMetadataTests(unittest.TestCase):
    """Readable logging of per-cycle trace metadata."""

    def setUp(self) -> None:
        verbose = patch.dict(os.environ, {"FORGE_VERBOSE": "1"})
        verbose.start()
        self.addCleanup(verbose.stop)

    def test_prints_metadata_summary_without_json_contents(self) -> None:
        run_dir = tempfile.mkdtemp(prefix="trace-run-")
        self.addCleanup(_rmtree, run_dir)
        metadata_file = Path(run_dir) / "reachability-metadata.json"
        metadata_file.write_text(
            json.dumps({"reflection": [{"type": "com.example.Foo"}]}),
            encoding="utf-8",
        )

        output = io.StringIO()
        with redirect_stdout(output):
            ntm._print_collected_metadata(run_dir, 2)

        printed = output.getvalue()
        self.assertIn("cycle 2: collected metadata from 1 file(s)", printed)
        self.assertNotIn("reachability-metadata.json:", printed)
        self.assertNotIn('"type": "com.example.Foo"', printed)

    def test_prints_none_for_empty_trace_dir(self) -> None:
        run_dir = tempfile.mkdtemp(prefix="trace-run-")
        self.addCleanup(_rmtree, run_dir)

        output = io.StringIO()
        with redirect_stdout(output):
            ntm._print_collected_metadata(run_dir, 1)

        self.assertIn("cycle 1: collected metadata: none", output.getvalue())

    def test_ignores_binary_exit_sentinel(self) -> None:
        run_dir = tempfile.mkdtemp(prefix="trace-run-")
        self.addCleanup(_rmtree, run_dir)
        Path(run_dir, "binary-exit-code").write_text("172", encoding="utf-8")

        output = io.StringIO()
        with redirect_stdout(output):
            ntm._print_collected_metadata(run_dir, 1)

        printed = output.getvalue()
        self.assertIn("cycle 1: collected metadata: none", printed)
        self.assertNotIn("binary-exit-code:", printed)


class GateMetadataAggregationTests(GateHarness):
    """Durable aggregation and merge behavior through the gate's outer loop."""

    def test_routes_to_codex_when_172_repeats_same_metadata(self) -> None:
        fake, _calls = self._fake_run_factory([172, 172], repeated_metadata=True)
        output = io.StringIO()
        with patch(
                GATE_SUBPROCESS_RUN,
                side_effect=fake,
        ), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ), redirect_stdout(output):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )

        self.assertEqual(result.status, ntv.STATUS_PASSED_WITH_INTERVENTION)
        self.assertEqual(result.iterations_used, 2)
        self.assertEqual(len(result.accepted_run_dirs), 1)
        printed = output.getvalue()
        self.assertIn("metadata progress stalled (no new trace metadata entries)", printed)
        self.assertIn("accepted_runs=1", printed)
        self.assertIn("accepted_unique_entries=1", printed)
        self.assertIn("current_cycle_entries=1", printed)
        self.assertIn("binary exited 172 without new trace metadata", printed)

    def test_prints_aggregated_metadata_path_after_merge(self) -> None:
        fake, _calls = self._fake_run_factory([172, 0])
        output = io.StringIO()
        with patch(
            GATE_SUBPROCESS_RUN,
            side_effect=fake,
        ), redirect_stdout(output):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED)
        self.assertIn(
            os.path.join(self.output_dir, "trace", "reachability-metadata.json"),
            output.getvalue(),
        )

    def test_aggregates_trace_metadata_into_durable_library_metadata(self) -> None:
        metadata_dir = Path(self.repo) / "metadata" / "g" / "a" / "1.0"
        metadata_dir.mkdir(parents=True)
        durable_metadata_path = metadata_dir / "reachability-metadata.json"
        durable_metadata_path.write_text(
            json.dumps({"reflection": [{"type": "com.example.Existing"}]}),
            encoding="utf-8",
        )
        fake, _calls = self._fake_run_factory([172, 0])

        with patch(GATE_SUBPROCESS_RUN, side_effect=fake):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )

        self.assertEqual(result.status, ntv.STATUS_PASSED)
        durable_metadata = json.loads(durable_metadata_path.read_text(encoding="utf-8"))
        reflected_types = {
            entry["type"]
            for entry in durable_metadata["reflection"]
        }
        self.assertIn("com.example.Existing", reflected_types)
        self.assertTrue(
            any(entry.startswith("com.example.Generated") for entry in reflected_types),
            durable_metadata,
        )

    def test_aggregates_trace_metadata_into_index_resolved_metadata_version(self) -> None:
        metadata_root = Path(self.repo) / "metadata" / "g" / "a"
        metadata_dir = metadata_root / "1.0"
        metadata_dir.mkdir(parents=True)
        (metadata_root / "index.json").write_text(
            json.dumps([
                {
                    "metadata-version": "1.0",
                    "tested-versions": ["1.0", "1.1"],
                }
            ]),
            encoding="utf-8",
        )
        durable_metadata_path = metadata_dir / "reachability-metadata.json"
        durable_metadata_path.write_text(
            json.dumps({"reflection": [{"type": "com.example.Existing"}]}),
            encoding="utf-8",
        )
        fake, _calls = self._fake_run_factory([172, 0])

        with patch(GATE_SUBPROCESS_RUN, side_effect=fake):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.1",
                output_dir=self.output_dir,
                max_iterations=5,
            )

        self.assertEqual(result.status, ntv.STATUS_PASSED)
        durable_metadata = json.loads(durable_metadata_path.read_text(encoding="utf-8"))
        reflected_types = {
            entry["type"]
            for entry in durable_metadata["reflection"]
        }
        self.assertIn("com.example.Existing", reflected_types)
        self.assertTrue(
            any(entry.startswith("com.example.Generated") for entry in reflected_types),
            durable_metadata,
        )
        self.assertFalse(
            (metadata_root / "1.1" / "reachability-metadata.json").exists(),
        )

    def test_merge_failure_returns_failed_without_invoking_codex(self) -> None:
        # Spec carve-out: post-success merge failures terminate as FAILED
        # directly because they are infrastructure problems codex cannot repair.
        fake, _calls = self._fake_run_factory([0], metadata_exit_codes={0})

        def merge_fails(cmd, **kwargs):  # type: ignore[no-untyped-def]
            if "mergeNativeTraceMetadata" in cmd:
                return subprocess.CompletedProcess(cmd, 1)
            return fake(cmd, **kwargs)

        with patch(
            GATE_SUBPROCESS_RUN,
            side_effect=merge_fails,
        ), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ) as codex_mock:
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )

        self.assertEqual(result.status, ntv.STATUS_FAILED)
        codex_mock.assert_not_called()
        self.assertEqual(result.intervention_records, [])

    def test_aggregate_failure_returns_failed_without_invoking_codex(self) -> None:
        # Malformed durable metadata makes final native-image-utils merge fail; the
        # gate must surface FAILED directly per the spec carve-out.
        metadata_dir = Path(self.repo) / "metadata" / "g" / "a" / "1.0"
        metadata_dir.mkdir(parents=True)
        (metadata_dir / "reachability-metadata.json").write_text(
            "this is not valid json",
            encoding="utf-8",
        )
        fake, _calls = self._fake_run_factory([172, 0])

        with patch(
            GATE_SUBPROCESS_RUN,
            side_effect=fake,
        ), patch(
            "utility_scripts.native_test_verification.run_native_test_fix",
            return_value=(0, "/tmp/codex.log", False, None),
        ) as codex_mock:
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )

        self.assertEqual(result.status, ntv.STATUS_FAILED)
        codex_mock.assert_not_called()
        self.assertEqual(result.intervention_records, [])

    def test_merges_final_passing_run_when_it_collected_metadata(self) -> None:
        fake, calls = self._fake_run_factory([0], metadata_exit_codes={0})
        with patch(GATE_SUBPROCESS_RUN, side_effect=fake):
            result = ntv.verify_native_test_passes(
                reachability_repo_path=self.repo,
                coordinate="g:a:1.0",
                output_dir=self.output_dir,
                max_iterations=5,
            )
        self.assertEqual(result.status, ntv.STATUS_PASSED)
        merge_calls = [call for call in calls if "mergeNativeTraceMetadata" in call]
        self.assertEqual(len(merge_calls), 2)
        input_dirs = next(arg for arg in merge_calls[0] if arg.startswith("-PinputDirs="))
        self.assertIn("cycle-0", input_dirs)
        durable_input_dirs = next(arg for arg in merge_calls[1] if arg.startswith("-PinputDirs="))
        self.assertIn(os.path.join(self.output_dir, "trace"), durable_input_dirs)


if __name__ == "__main__":
    unittest.main()
