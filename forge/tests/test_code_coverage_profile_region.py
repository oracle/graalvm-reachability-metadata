# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the caller's line region around a missed invoke.

§AR-code-coverage-deep-navigation.3
"""

import unittest

from utility_scripts.code_coverage_jacoco import JacocoLineCoverage, JacocoMethodCoverage
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_graph import CallGraph
from utility_scripts.code_coverage_profile_miss import edge_miss_classification
from utility_scripts.code_coverage_profile_navigation import NavigationEvidence

from tests.test_code_coverage_profile_flow import _flow


def _store_method(name: str, params: tuple[str, ...], returns: str) -> MethodRef:
    return MethodRef("example.Store", name, params, returns)


def _store_coverage(ref: MethodRef, line: int, covered: bool) -> JacocoMethodCoverage:
    return JacocoMethodCoverage(
        method_ref=ref, covered=covered, source_path="example/Store.java",
        source_line=line, report_paths=("fixture.xml",),
    )


class NestedLambdaRegionTests(unittest.TestCase):
    """A lambda body nested in the caller does not end the caller's line region,
    though JaCoCo reports it as a method starting inside its creator
    (§AR-code-coverage-deep-navigation.3)."""

    # The line and control-flow tables are javac's for this source:
    #
    #     13  Queue<Chunk> queue = new PriorityQueue<>((a, b) -> {
    #     14      int diff = Integer.compare(a.priority, b.priority);
    #     15      return diff != 0 ? diff : Long.compare(a.block, b.block);
    #     16  });
    #     17  for (Chunk chunk : chunks) {
    #     18      if (chunk.isSaved() && chunk.block > startBlock) {
    #     19          chunk.priority = getMovePriority(chunk);
    #     20          queue.offer(chunk);
    #     ...
    #     31  SecureRandom random = new SecureRandom();
    #     32  Thread seeder = new Thread(() -> {
    #     33      byte[] seed = random.generateSeed(20);
    #     34      random.setSeed(seed);
    #     35  });
    #     36  try {
    #     37      seeder.start();
    #     38      seeder.join(400);
    #     39  } catch (Exception e) {
    #     40      warn("SecureRandom", e);
    FIND = _store_method("findChunksToMove", ("long",), "java.util.Queue")
    PRIORITY = _store_method("getMovePriority", ("example.Store$Chunk",), "int")
    RANDOM = _store_method("getSecureRandom", (), "java.security.SecureRandom")
    WARN = _store_method("warn", ("java.lang.String", "java.lang.Exception"), "void")

    def setUp(self) -> None:
        self.graph = CallGraph(
            methods={1: self.FIND, 2: self.PRIORITY, 3: self.RANDOM, 4: self.WARN},
            invoke_fan_out={10: [2], 11: [4]},
        )
        comparator: MethodRef = _store_method(
            "lambda$findChunksToMove$0", ("example.Store$Chunk", "example.Store$Chunk"), "int"
        )
        seeder: MethodRef = _store_method(
            "lambda$getSecureRandom$0", ("java.security.SecureRandom",), "void"
        )
        self.jacoco_methods: dict[str, JacocoMethodCoverage] = {
            ref.canonical_id: _store_coverage(ref, line, covered)
            for ref, line, covered in (
                (self.FIND, 13, True), (comparator, 14, False), (self.PRIORITY, 27, False),
                (self.RANDOM, 31, True), (seeder, 33, True), (self.WARN, 46, False),
            )
        }
        self.jacoco_lines: dict[str, dict[int, JacocoLineCoverage]] = {"example/Store.java": {
            line: JacocoLineCoverage(*counters) for line, counters in {
                13: (0, 5, 0, 0), 14: (6, 0, 0, 0), 15: (12, 0, 2, 0), 17: (0, 11, 0, 2),
                18: (0, 8, 2, 2), 19: (5, 0, 0, 0), 20: (4, 0, 0, 0), 22: (0, 1, 0, 0),
                23: (0, 2, 0, 0), 27: (2, 0, 0, 0), 31: (0, 4, 0, 0), 32: (0, 6, 0, 0),
                33: (0, 4, 0, 0), 34: (0, 3, 0, 0), 35: (0, 1, 0, 0), 37: (0, 2, 0, 0),
                38: (0, 3, 0, 0), 39: (1, 0, 0, 0), 40: (3, 0, 0, 0), 41: (0, 1, 0, 0),
                42: (0, 2, 0, 0), 46: (1, 0, 0, 0),
            }.items()
        }}
        self.evidence = NavigationEvidence(
            line_numbers={
                self.FIND.canonical_id: (
                    (0, 13), (13, 17), (46, 18), (64, 19), (75, 20), (84, 22), (87, 23),
                ),
                self.RANDOM.canonical_id: (
                    (0, 31), (8, 32), (22, 37), (26, 38), (33, 41), (36, 39), (37, 40), (43, 42),
                ),
            },
            # A straight-line method such as `getSecureRandom` has no flow row.
            flows={self.FIND.canonical_id: _flow(
                [(31, (34, 87)), (51, (54, 84)), (61, (64, 84))],
                {0: (24,), 24: (87, 34), 34: (84, 54), 54: (84, 64), 64: (84,), 84: (24,), 87: ()},
            )},
        )

    def _classify(self, caller: int, callee: int, bci: int, invoke_id: int, line: int) -> dict:
        edge: dict = {
            "caller": caller, "callee": callee, "bci": str(bci), "is_direct": "true",
            "kind": "call", "invoke_id": invoke_id, "source_line": line,
        }
        return edge_miss_classification(
            edge, self.graph, self.jacoco_methods, self.jacoco_lines, self.evidence
        )

    def test_a_lambda_above_a_guarded_call_keeps_the_guard_as_the_fork(self) -> None:
        classification: dict = self._classify(1, 2, 69, 10, 19)
        self.assertEqual(classification["kind"], "fork-not-taken")
        self.assertEqual(
            (classification["target"]["line"], classification["fork"]["line"]), (19, 18)
        )
        self.assertEqual(classification["fork"]["evidence"], "control-flow")

    def test_a_lambda_above_a_catch_keeps_the_call_line_as_the_target(self) -> None:
        classification: dict = self._classify(3, 4, 40, 11, 40)
        self.assertEqual(classification["kind"], "no-fork")
        self.assertEqual(classification["target"]["line"], 40)
        self.assertEqual(classification["nearestCovered"]["line"], 38)
