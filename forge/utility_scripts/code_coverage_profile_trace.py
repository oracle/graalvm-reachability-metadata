# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Which test reaches each observed group of a deep prompt.

The sampled stacks run from the sampled library frame up to the JUnit test
method. For each group this module picks the most frequent stack that reaches
its divergence and splits it at the test: the library frames from the test's
call down to the divergence become the `Observed` chain, and the test frames
become the `Called from test` block, each step at the line its compiled class
attributes to the sampled bci (§AR-code-coverage-deep-navigation.2).
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field

from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_graph import (
    FACTORY_METHOD_HOLDER,
    SYNTHETIC_LAMBDA_CLASS_MARKER,
    CallGraph,
    resolve_graph_id,
    translated_ref,
)
from utility_scripts.code_coverage_profile_inputs import line_at
from utility_scripts.code_coverage_profile_records import simple_owner, translated_path
from utility_scripts.code_coverage_profile_routes import (
    FRAMEWORK_TYPE_PREFIXES,
    Sample,
    SampledProfile,
)
from utility_scripts.java_class_file import ClassFileError, MethodCode, read_class_file

#: A test project's suites: the sources and the classes the measurement's
#: Gradle run compiles them to. The coverage suite comes first, since its tests
#: are preferred (§AR-code-coverage-deep-navigation.2).
TEST_SUITES: tuple[tuple[str, str], ...] = (
    ("code-coverage-improvement/src/test/java", "build/classes/java/codeCoverage"),
    ("src/test/java", "build/classes/java/test"),
)

#: Owners of frames no source names: lambda classes and forms, factory stubs,
#: and reflection accessors.
SYNTHETIC_FRAME_MARKERS: tuple[str, ...] = (
    SYNTHETIC_LAMBDA_CLASS_MARKER,
    FACTORY_METHOD_HOLDER,
    "LambdaForm$",
    "ReflectionAccessorHolder",
)


def _is_synthetic(ref: MethodRef) -> bool:
    return any(marker in ref.owner for marker in SYNTHETIC_FRAME_MARKERS)


@dataclass(frozen=True)
class TestSource:
    """Where a test class lives: its suite's rank, source, and class file."""

    suite: int
    source: str
    class_file: str


class TestProject:
    """The indexed test project whose suites hold the test frames.

    A frame is a test frame when its source file exists in a suite, whatever
    its package (§AR-code-coverage-deep-navigation.2).
    """

    def __init__(self, path: str) -> None:
        self.path: str = os.path.abspath(path)
        self._sources: dict[str, TestSource | None] = {}
        self._classes: dict[str, list[MethodCode]] = {}

    def source_of(self, owner: str) -> TestSource | None:
        if owner not in self._sources:
            outer: str = owner.split("$", 1)[0].replace(".", os.sep) + ".java"
            self._sources[owner] = next((
                TestSource(
                    rank,
                    os.path.join(sources, outer),
                    os.path.join(self.path, classes, owner.replace(".", os.sep) + ".class"),
                )
                for rank, (sources, classes) in enumerate(TEST_SUITES)
                if os.path.isfile(os.path.join(self.path, sources, outer))
            ), None)
        return self._sources[owner]

    def _method(self, ref: MethodRef, class_file: str) -> MethodCode | None:
        if class_file not in self._classes:
            try:
                self._classes[class_file] = read_class_file(class_file)
            except (OSError, ClassFileError):
                self._classes[class_file] = []
        named: list[MethodCode] = [
            method for method in self._classes[class_file] if method.name == ref.name
        ]
        exact: MethodCode | None = next(
            (method for method in named if method.params == ref.params), None
        )
        return exact or (named[0] if len(named) == 1 else None)

    def step_line(self, ref: MethodRef, bci: int, callee: MethodRef | None) -> int | None:
        """The line of a test frame's call, or `None` when the compiled class
        no longer invokes the frame the sample shows next at that bci."""
        source: TestSource | None = self.source_of(ref.owner)
        method: MethodCode | None = self._method(ref, source.class_file) if source else None
        if method is None or callee is None:
            return None
        invoked: tuple[str, str] | None = method.invokes.get(bci)
        # A factory stub stands for the constructor the bytecode invokes.
        expected: str = "<init>" if callee.owner == FACTORY_METHOD_HOLDER else callee.name
        if invoked is None or invoked[1] != expected:
            return None
        return line_at(method.line_numbers, bci)


@dataclass(frozen=True)
class TestStep:
    ref: MethodRef
    line: int | None
    source: str


@dataclass(frozen=True)
class GroupTrace:
    """One group's most frequent stack, split at the test."""

    #: Library frames, from the test's call down to the divergence, named as
    #: routes name them.
    chain: list[MethodRef]
    #: Test frames in call order; empty when the stack holds no test frame.
    steps: list[TestStep] = field(default_factory=list)


@dataclass
class _Candidate:
    sample: Sample
    count: int
    #: Full-path index of the divergence's first and last frame.
    start: int
    end: int


class SampleTrace:
    """Traces groups through every sampled stack of one profile."""

    def __init__(
            self,
            profile: SampledProfile,
            graph: CallGraph,
            test_project: TestProject | None = None,
            library_owners: set[str] | None = None,
    ) -> None:
        self.profile: SampledProfile = profile
        self.graph: CallGraph = graph
        self.test_project: TestProject | None = test_project
        self.library_owners: set[str] | None = library_owners
        self._groups: dict[tuple[int, ...], GroupTrace] = {}
        self._occurrences: dict[int, list[tuple[Sample, int]]] | None = None

    def _test_source(self, ref: MethodRef) -> TestSource | None:
        if self.test_project is None or _is_synthetic(ref):
            return None
        return self.test_project.source_of(ref.owner)

    def _is_library(self, ref: MethodRef) -> bool:
        if self.library_owners is not None:
            return ref.owner in self.library_owners
        return not ref.owner.startswith((*FRAMEWORK_TYPE_PREFIXES, "com.oracle.svm."))

    def _candidates(self, divergence: tuple[int, ...]) -> list[_Candidate]:
        """Every sample whose stack holds the divergence, at its outermost spot."""
        if self._occurrences is None:
            self._occurrences = {}
            for sample in self.profile.samples:
                for position, (static_id, _) in enumerate(sample.path):
                    self._occurrences.setdefault(static_id, []).append((sample, position))
        candidates: list[_Candidate] = []
        seen: set[str] = set()
        for sample, position in self._occurrences.get(divergence[0], []):
            ids: tuple[int, ...] = tuple(
                static_id for static_id, _ in sample.path[position:position + len(divergence)]
            )
            if sample.context_id in seen or ids != divergence:
                continue
            seen.add(sample.context_id)
            candidates.append(_Candidate(
                sample, sample.count, sample.path_full_indexes[position],
                sample.path_full_indexes[position + len(divergence) - 1],
            ))
        return candidates

    def group(self, divergence: tuple[int, ...]) -> GroupTrace:
        """The trace of the group whose routes diverge at these static ids: the
        join frame, then its observed callee when the sample has one."""
        if divergence not in self._groups:
            self._groups[divergence] = self._trace(divergence)
        return self._groups[divergence]

    def _trace(self, divergence: tuple[int, ...]) -> GroupTrace:
        # One stack per distinct path from the root down to the divergence.
        stacks: dict[tuple, _Candidate] = {}
        for candidate in self._candidates(divergence):
            frames: list[tuple[MethodRef, int]] = candidate.sample.full_path[:candidate.end]
            key: tuple = tuple((ref.canonical_id, bci) for ref, bci in frames)
            if key in stacks:
                stacks[key].count += candidate.count
            else:
                stacks[key] = candidate

        def preference(candidate: _Candidate) -> tuple[int, int]:
            # A coverage-suite test first, then a regular one, then none; the
            # most frequent stack within each.
            sources: list[TestSource] = [
                source for ref, _ in candidate.sample.full_path[:candidate.start]
                if (source := self._test_source(ref)) is not None
            ]
            return (sources[0].suite if sources else len(TEST_SUITES), -candidate.count)

        if not stacks:
            return GroupTrace(translated_path(list(divergence), self.graph))
        # `stacks` keeps sample order, so equal preferences resolve the same way
        # on every run.
        return self._split(min(stacks.values(), key=preference))

    def _shown(self, ref: MethodRef) -> MethodRef | None:
        """A library frame as a route names it: a lambda body or class by its
        creator, a factory stub by its constructor, any other synthetic frame
        not at all."""
        static_id: int | None = resolve_graph_id(self.graph, ref)
        shown: MethodRef = translated_ref(static_id, self.graph) if static_id is not None else ref
        return None if _is_synthetic(shown) else shown

    def _split(self, candidate: _Candidate) -> GroupTrace:
        frames: list[tuple[MethodRef, int]] = candidate.sample.full_path
        tests: list[int] = [
            index for index in range(candidate.start)
            if self._test_source(frames[index][0]) is not None
        ]
        first: int = tests[-1] + 1 if tests else next(
            (index for index in range(candidate.start)
             if self._is_library(frames[index][0]) and not _is_synthetic(frames[index][0])),
            candidate.start,
        )
        chain: list[MethodRef] = []
        for ref, _ in frames[first:candidate.end + 1]:
            shown: MethodRef | None = self._shown(ref)
            if shown is not None and (not chain or chain[-1] != shown):
                chain.append(shown)
        steps: list[TestStep] = []
        for index in tests:
            ref, bci = frames[index]
            source: TestSource | None = self._test_source(ref)
            assert source is not None and self.test_project is not None
            callee: MethodRef | None = frames[index + 1][0] if index + 1 < len(frames) else None
            line: int | None = self.test_project.step_line(ref, bci, callee)
            steps.append(TestStep(ref, line, source.source))
        return GroupTrace(chain, steps)


def _step_label(ref: MethodRef) -> str:
    if ref.name.startswith("lambda$"):
        return "lambda"
    return f"{simple_owner(ref.owner)}()" if ref.name == "<init>" else f"{ref.name}()"


def called_from_test_lines(trace: GroupTrace) -> list[str]:
    """`Called from test`: the test JUnit invoked, then one step per test
    frame, each one level deeper than its caller (§AR-code-coverage-deep-navigation.2)."""
    if not trace.steps:
        return []
    head: TestStep = trace.steps[0]
    lines: list[str] = [
        f"Called from test `{simple_owner(head.ref.owner)}.{_step_label(head.ref)}` "
        f"in `{head.source}`:"
    ]
    for depth, step in enumerate(trace.steps):
        line: str = f"line {step.line}" if step.line is not None else "line ?"
        where: str = f" in `{step.source}`" if step.source != head.source else ""
        lines.append(f"  {'    ' * depth}{line}{where}: {_step_label(step.ref)}")
    return lines

