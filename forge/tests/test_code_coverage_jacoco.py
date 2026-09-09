# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Line-level JaCoCo miss diagnoses (§AR-code-coverage-improvement.3.2)."""

import os
import tempfile
import unittest

from utility_scripts.code_coverage_jacoco import (
    JacocoCoverage,
    JacocoLineCoverage,
    load_jacoco_coverage,
)
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts import code_coverage_profile_report as report_module


JACOCO_XML = """\
<?xml version="1.0" encoding="UTF-8"?>
<report name="miss-classifications">
  <package name="okhttp3/internal/cache">
    <class name="okhttp3/internal/cache/CacheInterceptor"
           sourcefilename="CacheInterceptor.java">
      <method name="intercept" desc="()V" line="120">
        <counter type="METHOD" missed="0" covered="1"/>
      </method>
    </class>
    <class name="okhttp3/internal/cache/FaultHidingSink"
           sourcefilename="FaultHidingSink.java">
      <method name="write" desc="()V" line="32">
        <counter type="METHOD" missed="0" covered="1"/>
      </method>
    </class>
    <class name="okhttp3/internal/cache/DiskLruCache$2"
           sourcefilename="DiskLruCache.java">
      <method name="onException" desc="()V" line="318">
        <counter type="METHOD" missed="1" covered="0"/>
      </method>
    </class>
    <class name="okhttp3/internal/cache/DiskLruCache$Editor$1"
           sourcefilename="DiskLruCache.java">
      <method name="onException" desc="()V" line="905">
        <counter type="METHOD" missed="1" covered="0"/>
      </method>
    </class>
    <sourcefile name="CacheInterceptor.java">
      <line nr="120" mi="0" ci="3" mb="0" cb="0"/>
      <line nr="129" mi="0" ci="7" mb="2" cb="2"/>
      <line nr="132" mi="0" ci="5" mb="0" cb="0"/>
      <line nr="135" mi="4" ci="0" mb="2" cb="0"/>
      <line nr="137" mi="4" ci="0" mb="0" cb="0"/>
      <line nr="138" mi="1" ci="0" mb="0" cb="0"/>
      <line nr="140" mi="1" ci="0" mb="0" cb="0"/>
    </sourcefile>
    <sourcefile name="FaultHidingSink.java">
      <line nr="32" mi="0" ci="3" mb="1" cb="1"/>
      <line nr="37" mi="0" ci="4" mb="0" cb="0"/>
      <line nr="38" mi="1" ci="0" mb="0" cb="0"/>
      <line nr="39" mi="3" ci="0" mb="0" cb="0"/>
      <line nr="40" mi="3" ci="0" mb="0" cb="0"/>
      <line nr="41" mi="0" ci="1" mb="0" cb="0"/>
    </sourcefile>
  </package>
  <package name="okhttp3">
    <class name="okhttp3/Cache$1" sourcefilename="Cache.java">
      <method name="remove" desc="()V" line="153">
        <counter type="METHOD" missed="1" covered="0"/>
      </method>
    </class>
  </package>
  <package name="okhttp3/internal/http2">
    <class name="okhttp3/internal/http2/Http2Connection$6"
           sourcefilename="Http2Connection.java">
      <method name="execute" desc="()V" line="834">
        <counter type="METHOD" missed="0" covered="1"/>
      </method>
    </class>
    <class name="okhttp3/internal/http2/PushObserver$1"
           sourcefilename="PushObserver.java">
      <method name="onData" desc="()V" line="88">
        <counter type="METHOD" missed="1" covered="0"/>
      </method>
    </class>
    <sourcefile name="Http2Connection.java">
      <line nr="834" mi="0" ci="13" mb="0" cb="0"/>
      <line nr="835" mi="0" ci="9" mb="1" cb="1"/>
      <line nr="841" mi="1" ci="0" mb="0" cb="0"/>
      <line nr="843" mi="0" ci="1" mb="0" cb="0"/>
    </sourcefile>
  </package>
</report>
"""


class JacocoLineCoverageTest(unittest.TestCase):

    def setUp(self) -> None:
        self.directory: tempfile.TemporaryDirectory[str] = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.xml_path: str = os.path.join(self.directory.name, "jacoco.xml")
        with open(self.xml_path, "w", encoding="utf-8") as xml_file:
            xml_file.write(JACOCO_XML)
        self.coverage: JacocoCoverage = load_jacoco_coverage([self.xml_path])

    def _classification(
            self,
            caller: MethodRef,
            target: MethodRef,
            candidate_refs: list[MethodRef],
            invoke_id: int,
            bci: str,
            source_line: int,
    ) -> dict:
        methods: dict[int, MethodRef] = {
            1: caller,
            2: target,
            **{
                candidate_id: candidate
                for candidate_id, candidate in enumerate(candidate_refs, start=3)
            },
        }
        candidate_ids: list[int] = [
            method_id
            for method_id, method in methods.items()
            if method in candidate_refs
        ]
        edge: dict = {
            "caller": 1,
            "callee": 2,
            "bci": bci,
            "is_direct": "false",
            "kind": "call",
            "invoke_id": invoke_id,
            "source_line": source_line,
        }
        graph = report_module.CallGraph(
            methods=methods,
            key_to_id={method.canonical_id: method_id for method_id, method in methods.items()},
            reverse_adjacency={2: [edge]},
            invoke_fan_out={invoke_id: [2, *candidate_ids]},
        )
        record = report_module.NearCallRecord(
            coverage=self.coverage.methods[target.canonical_id],
            target_id=2,
            target_state=report_module.TargetState(),
            join_kind="public-entry",
            static_path=[1, 2],
            static_path_edges=[edge],
            sample=None,
            sampled_join_path_index=None,
        )
        return report_module._classify_miss(
            record, graph, self.coverage.methods, self.coverage.lines
        )

    def test_parses_line_counters_for_all_miss_shapes(self) -> None:
        cases: tuple[tuple[str, int, JacocoLineCoverage], ...] = (
            (
                "okhttp3/internal/cache/CacheInterceptor.java",
                129,
                JacocoLineCoverage(mi=0, ci=7, mb=2, cb=2),
            ),
            (
                "okhttp3/internal/cache/FaultHidingSink.java",
                39,
                JacocoLineCoverage(mi=3, ci=0, mb=0, cb=0),
            ),
            (
                "okhttp3/internal/http2/Http2Connection.java",
                834,
                JacocoLineCoverage(mi=0, ci=13, mb=0, cb=0),
            ),
        )
        for source_path, line, expected in cases:
            with self.subTest(source_path=source_path, line=line):
                self.assertEqual(self.coverage.lines[source_path][line], expected)

    def test_classifies_fork_not_taken(self) -> None:
        classification: dict = self._classification(
            MethodRef("okhttp3.internal.cache.CacheInterceptor", "intercept", (), "void"),
            MethodRef("okhttp3.Cache$1", "remove", (), "void"),
            [],
            101,
            "444",
            137,
        )

        self.assertEqual(classification["kind"], "fork-not-taken")
        self.assertEqual(classification["target"]["line"], 137)
        self.assertEqual(classification["fork"]["line"], 129)
        self.assertEqual((classification["fork"]["mb"], classification["fork"]["cb"]), (2, 2))
        rendered: str = "\n".join(report_module._classification_lines(classification))
        self.assertIn("CacheInterceptor.java:137` never ran", rendered)
        self.assertIn("CacheInterceptor.java:129` ran, 2 of 4 branches taken", rendered)

    def test_classifies_no_fork_exception_path(self) -> None:
        classification: dict = self._classification(
            MethodRef("okhttp3.internal.cache.FaultHidingSink", "write", (), "void"),
            MethodRef("okhttp3.internal.cache.DiskLruCache$2", "onException", (), "void"),
            [
                MethodRef(
                    "okhttp3.internal.cache.DiskLruCache$Editor$1",
                    "onException",
                    (),
                    "void",
                )
            ],
            102,
            "32",
            40,
        )

        self.assertEqual(classification["kind"], "no-fork")
        self.assertEqual(classification["target"]["line"], 40)
        self.assertEqual(classification["nearestCovered"]["line"], 37)
        self.assertIsNone(classification["fork"])
        rendered: str = "\n".join(report_module._classification_lines(classification))
        self.assertIn("FaultHidingSink.java:37", rendered)
        self.assertIn("reached only by an exception or external event", rendered)

    def test_covered_call_site_dispatches_elsewhere_before_fork(self) -> None:
        classification: dict = self._classification(
            MethodRef("okhttp3.internal.http2.Http2Connection$6", "execute", (), "void"),
            MethodRef("okhttp3.internal.http2.PushObserver$1", "onData", (), "void"),
            [
                MethodRef(
                    f"okhttp3.internal.http2.Http2ApiCoverageTest${index}",
                    "onData",
                    (),
                    "void",
                )
                for index in (2, 3, 6)
            ],
            103,
            "23",
            834,
        )

        self.assertEqual(classification["kind"], "dispatched-elsewhere")
        self.assertEqual(classification["target"]["line"], 834)
        self.assertIsNone(classification["fork"])
        suite_candidates: list[dict] = [
            candidate
            for candidate in classification["candidates"]
            if candidate["coverageSuite"]
        ]
        self.assertEqual(len(suite_candidates), 3)
        rendered: str = "\n".join(report_module._classification_lines(classification))
        self.assertIn("Http2Connection.java:834` RAN", rendered)
        self.assertIn("different implementation answered", rendered)
        self.assertEqual(rendered.count("[coverage suite]"), 3)


if __name__ == "__main__":
    unittest.main()
