# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Tests for the test that reaches each observed group, and for the class-file
reader that resolves its steps' lines.

§AR-code-coverage-deep-navigation.2
"""

import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

import yaml

from utility_scripts import code_coverage_profile_report as report_module
from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_graph import FACTORY_METHOD_HOLDER, CallGraph
from utility_scripts.code_coverage_profile_render import write_markdown
from utility_scripts.code_coverage_profile_routes import SampledProfile, sampled_profile_from_document
from utility_scripts.code_coverage_profile_trace import (
    GroupTrace,
    SampleTrace,
    TestProject,
    called_from_test_lines,
)
from utility_scripts.java_class_file import descriptor_params, read_class_file

from tests.code_coverage_profile_support import FIXTURES, JACOCO_PATH, _coverage
from tests.code_coverage_rank_test_utils import _java_tool

LIBRARY_SOURCE: str = """package org.lib;

public class Store implements AutoCloseable {
    public void flush() {
    }

    @Override
    public void close() {
    }
}
"""

# Line 8 hands a lambda to the helper, line 13 calls it, and javac puts the
# implicit `close()` of the normal path on line 14, the block's closing brace.
TEST_SOURCE: str = """package org.lib.coverage;

import java.util.function.Consumer;
import org.lib.Store;

public class StoreCoverageTest {
    public void flushes() {
        withStore(store -> store.flush());
    }

    void withStore(Consumer<Store> body) {
        try (Store store = new Store()) {
            body.accept(store);
        }
    }
}
"""

COVERAGE_SOURCE: str = "code-coverage-improvement/src/test/java/org/lib/coverage/StoreCoverageTest.java"
TEST_OWNER: str = "org.lib.coverage.StoreCoverageTest"

JUNIT = MethodRef("org.junit.platform.commons.util.ReflectionUtils", "invokeMethod", (), "void")
FLUSHES = MethodRef(TEST_OWNER, "flushes", (), "void")
WITH_STORE = MethodRef(TEST_OWNER, "withStore", ("java.util.function.Consumer",), "void")
LAMBDA_CLASS = MethodRef(f"{TEST_OWNER}$$Lambda.0x1", "accept", ("java.lang.Object",), "void")
LAMBDA_BODY = MethodRef(TEST_OWNER, "lambda$flushes$0", ("org.lib.Store",), "void")
FLUSH = MethodRef("org.lib.Store", "flush", (), "void")
WRITE = MethodRef("org.lib.Store", "write", (), "void")
CLOSE = MethodRef("org.lib.Store", "close", (), "void")
REGULAR = MethodRef("org.lib.StoreTest", "test", (), "void")
THREAD = MethodRef("java.lang.Thread", "run", (), "void")
WORKER = MethodRef("org.lib.Worker", "run", (), "void")
STUB = MethodRef(FACTORY_METHOD_HOLDER, "Store_constructor_1", (), "org.lib.Store")
CONSTRUCTOR = MethodRef("org.lib.Store", "<init>", (), "void")
LAMBDA_FORM = MethodRef("java.lang.invoke.LambdaForm$MH.0x2", "invoke", (), "void")

REFS: list[MethodRef] = [
    JUNIT, FLUSHES, WITH_STORE, LAMBDA_CLASS, LAMBDA_BODY, FLUSH, WRITE, CLOSE,
    REGULAR, THREAD, WORKER, STUB, CONSTRUCTOR, LAMBDA_FORM,
]


@unittest.skipIf(_java_tool("javac") is None, "javac is required")
class TestTraceTests(unittest.TestCase):

    @classmethod
    def setUpClass(cls) -> None:
        cls.work = tempfile.mkdtemp(prefix="test-trace-")
        cls.project = os.path.join(cls.work, "project")
        library: str = os.path.join(cls.work, "library")
        for path, text in (
                (os.path.join(library, "org/lib/Store.java"), LIBRARY_SOURCE),
                (os.path.join(cls.project, COVERAGE_SOURCE), TEST_SOURCE),
                (os.path.join(cls.project, "src/test/java/org/lib/StoreTest.java"), "class StoreTest {}\n"),
        ):
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8") as source:
                source.write(text)
        classes: str = os.path.join(cls.project, "build/classes/java/codeCoverage")
        javac: str | None = _java_tool("javac")
        assert javac is not None
        subprocess.run([javac, "-d", os.path.join(cls.work, "lib-classes"),
                        os.path.join(library, "org/lib/Store.java")], check=True)
        subprocess.run([javac, "-cp", os.path.join(cls.work, "lib-classes"), "-d", classes,
                        os.path.join(cls.project, COVERAGE_SOURCE)], check=True)
        methods = read_class_file(os.path.join(classes, "org/lib/coverage/StoreCoverageTest.class"))
        cls.bci = {
            (method.name, name): bci
            for method in methods for bci, (_, name) in sorted(method.invokes.items(), reverse=True)
        }

    @classmethod
    def tearDownClass(cls) -> None:
        shutil.rmtree(cls.work, True)

    def _profile(self, stacks: list[tuple[list[tuple[MethodRef, int]], int]]) -> SampledProfile:
        """Samples from root-first `(frame, bci)` stacks and their counts."""
        ids: dict[MethodRef, int] = {ref: index for index, ref in enumerate(REFS, start=1)}
        document: dict = {"samplingProfiles": [
            {"ctx": "<".join(f"{ids[ref]}:{bci}" for ref, bci in reversed(stack)),
             "records": [count]}
            for stack, count in stacks
        ]}
        return sampled_profile_from_document(
            document, self.graph, {index: ref for ref, index in ids.items()}, "fixture.iprof",
        )

    def setUp(self) -> None:
        methods: dict[int, MethodRef] = dict(enumerate(REFS, start=1))
        self.graph = CallGraph(
            methods=methods,
            key_to_id={ref.canonical_id: static_id for static_id, ref in methods.items()},
            path_aliases={REFS.index(STUB) + 1: CONSTRUCTOR},
        )

    def _id(self, ref: MethodRef) -> int:
        return REFS.index(ref) + 1

    def _through_lambda(self) -> list[tuple[MethodRef, int]]:
        return [
            (JUNIT, 9),
            (FLUSHES, self.bci[("flushes", "withStore")]),
            (WITH_STORE, self.bci[("withStore", "accept")]),
            (LAMBDA_CLASS, 5),
            (LAMBDA_BODY, self.bci[("lambda$flushes$0", "flush")]),
            (FLUSH, 4),
            (WRITE, 0),
        ]

    def _trace(self, stacks: list, owners: set[str] | None = None) -> SampleTrace:
        return SampleTrace(self._profile(stacks), self.graph, TestProject(self.project), owners)

    def test_a_test_frame_is_one_whose_source_is_in_a_suite_not_its_package(self) -> None:
        project = TestProject(self.project)
        self.assertEqual(project.source_of(f"{TEST_OWNER}$Inner").source, COVERAGE_SOURCE)
        self.assertEqual(project.source_of("org.lib.StoreTest").suite, 1)
        self.assertIsNone(project.source_of("org.lib.Store"))
        self.assertIsNone(project.source_of("org.lib.ReplayTest"))

    def test_steps_follow_the_test_through_its_helper_and_lambda(self) -> None:
        trace: GroupTrace = self._trace([(self._through_lambda(), 3)]).group(
            (self._id(FLUSH), self._id(WRITE)))
        self.assertEqual(trace.chain, [FLUSH, WRITE])
        self.assertEqual(called_from_test_lines(trace), [
            f"Called from test `StoreCoverageTest.flushes()` in `{COVERAGE_SOURCE}`:",
            "  line 8: flushes()",
            "      line 13: withStore()",
            "          line 8: lambda",
        ])

    def test_a_try_with_resources_close_reads_the_line_javac_gives_it(self) -> None:
        stack: list[tuple[MethodRef, int]] = [
            (JUNIT, 9),
            (FLUSHES, self.bci[("flushes", "withStore")]),
            (WITH_STORE, self.bci[("withStore", "close")]),
            (CLOSE, 0),
        ]
        trace: GroupTrace = self._trace([(stack, 1)]).group((self._id(CLOSE),))
        self.assertEqual(called_from_test_lines(trace)[2], "      line 14: withStore()")

    def test_a_bci_that_no_longer_invokes_the_next_frame_reads_a_question_mark(self) -> None:
        stack: list[tuple[MethodRef, int]] = self._through_lambda()
        stack[1] = (FLUSHES, 0)
        trace: GroupTrace = self._trace([(stack, 1)]).group((self._id(FLUSH), self._id(WRITE)))
        self.assertEqual(called_from_test_lines(trace)[1], "  line ?: flushes()")

    def test_a_coverage_suite_test_wins_over_a_more_frequent_regular_one(self) -> None:
        regular: list[tuple[MethodRef, int]] = [(JUNIT, 9), (REGULAR, 3), (FLUSH, 4), (WRITE, 0)]
        trace: GroupTrace = self._trace([(regular, 50), (self._through_lambda(), 1)]).group(
            (self._id(FLUSH), self._id(WRITE)))
        self.assertEqual(trace.steps[0].ref, FLUSHES)
        alone: GroupTrace = self._trace([(regular, 50)]).group((self._id(FLUSH), self._id(WRITE)))
        self.assertEqual([step.source for step in alone.steps], ["src/test/java/org/lib/StoreTest.java"])

    def test_a_stack_without_a_test_starts_at_its_outermost_library_frame(self) -> None:
        stack: list[tuple[MethodRef, int]] = [(THREAD, 2), (WORKER, 7), (FLUSH, 4), (WRITE, 0)]
        trace: GroupTrace = self._trace([(stack, 1)], {"org.lib.Worker", "org.lib.Store"}).group(
            (self._id(FLUSH), self._id(WRITE)))
        self.assertEqual(trace.chain, [WORKER, FLUSH, WRITE])
        self.assertEqual(called_from_test_lines(trace), [])

    def test_synthetic_frames_drop_and_a_factory_stub_reads_as_its_constructor(self) -> None:
        stack: list[tuple[MethodRef, int]] = [
            (THREAD, 2), (WORKER, 7), (LAMBDA_FORM, 1), (STUB, 1), (CONSTRUCTOR, 3), (FLUSH, 4), (WRITE, 0),
        ]
        trace: GroupTrace = self._trace([(stack, 1)], {"org.lib.Worker", "org.lib.Store"}).group(
            (self._id(FLUSH), self._id(WRITE)))
        self.assertEqual(trace.chain, [WORKER, CONSTRUCTOR, FLUSH, WRITE])


    def test_the_prompt_states_the_project_and_ends_each_group_with_its_test(self) -> None:
        self.graph.adjacency = {self._id(FLUSH): [{
            "caller": self._id(FLUSH), "callee": self._id(WRITE), "bci": "1",
            "is_direct": "true", "kind": "call", "invoke_id": 1,
        }]}
        profile: SampledProfile = self._profile([(self._through_lambda()[:-1], 2)])
        report, records = report_module.correlate(
            profile, self.graph, {"targets": [{"id": FLUSH.canonical_id, "kind": "method"}]},
            {FLUSH.canonical_id: _coverage(FLUSH, covered=True), WRITE.canonical_id: _coverage(WRITE)},
        )
        path: str = os.path.join(self.work, "prompt.md")
        write_markdown(report, records, self.graph, "org.lib:store:1", 0, path,
                       trace=SampleTrace(profile, self.graph, TestProject(self.project)))
        with open(path, encoding="utf-8") as prompt:
            markdown: str = prompt.read()

        self.assertIn(f"Test project: `{self.project}`; test files are relative to it.", markdown)
        group: str = markdown.split("Observed:\n", 1)[1]
        self.assertTrue(group.startswith("`Store.flush()`\n\nUncovered paths:\n"))
        self.assertLess(group.index("Reached only through an exception:"),
                        group.index("Called from test `StoreCoverageTest.flushes()`"))


class TestProjectWiringTests(unittest.TestCase):
    """`deep-measure` hands the analyzer the indexed test project, and the
    analyzer hands it to the prompt writer."""

    def test_the_analyzer_traces_groups_through_the_test_project(self) -> None:
        work: str = tempfile.mkdtemp(prefix="test-project-cli-")
        self.addCleanup(shutil.rmtree, work, True)
        argv: list[str] = [
            "code_coverage_profile_report.py",
            "--profile", os.path.join(FIXTURES, "near_call_sampling.iprof"),
            "--reports-dir", FIXTURES,
            "--api-inventory", os.path.join(FIXTURES, "near_call_inventory.json"),
            "--jacoco-xml", JACOCO_PATH,
            "--test-project", "/project/tests/src/com.example/demo/1.0.0",
            "--coordinate", "com.example:demo:1.0.0",
            "--output-dir", work,
        ]

        with mock.patch.object(sys, "argv", argv), \
                mock.patch.object(report_module, "write_prompts", return_value=[]) as writer:
            report_module.main()

        trace: SampleTrace = writer.call_args.args[7]
        self.assertEqual(trace.test_project.path, "/project/tests/src/com.example/demo/1.0.0")

    def test_deep_measure_passes_the_resolved_test_project(self) -> None:
        forge_root: str = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
        states_path: str = os.path.join(
            forge_root, ".agents", "rhei", "templates", "code-coverage-improvement", "states.yaml",
        )
        with open(states_path, encoding="utf-8") as states_file:
            states: dict = yaml.safe_load(re.sub(r"\{\{[^{}]+\}\}", "1", states_file.read()))["states"]
        program: str = " ".join(states["deep-measure"]["program"].split())

        self.assertIn("subproject = Path(resolve_test_dir(worktree, group, artifact, version))", program)
        self.assertIn('"--test-project", subproject', program)


class ClassFileTests(unittest.TestCase):

    def test_descriptor_parameters_read_in_source_form(self) -> None:
        self.assertEqual(
            descriptor_params("(I[[Ljava/lang/String;Ljava/util/Map$Entry;[B)V"),
            ("int", "java.lang.String[][]", "java.util.Map$Entry", "byte[]"),
        )


if __name__ == "__main__":
    unittest.main()
