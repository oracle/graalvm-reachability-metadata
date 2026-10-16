# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The `Observed` chain of each group of a deep prompt.

The sampled stacks run from the sampled library frame up to the JUnit test
method. For each group this module picks the most frequent stack that reaches
its divergence and keeps it whole from the test method down to the
divergence, assertion-library and JDK frames included. Each test frame carries
the line its compiled class attributes to the sampled bci
(§AR-code-coverage-deep-navigation.2).
"""

from __future__ import annotations

import os
from dataclasses import dataclass

from utility_scripts.code_coverage_model import MethodRef
from utility_scripts.code_coverage_profile_graph import (
    FACTORY_METHOD_HOLDER,
    SYNTHETIC_LAMBDA_CLASS_MARKER,
    CallGraph,
    resolve_graph_id,
    translated_ref,
)
from utility_scripts.code_coverage_profile_inputs import line_at
from utility_scripts.code_coverage_profile_records import translated_path
from utility_scripts.code_coverage_profile_routes import (
    FRAMEWORK_TYPE_PREFIXES,
    Sample,
    SampledProfile,
)
from utility_scripts.java_class_file import ClassFileError, MethodCode, read_class_file

#: A test project's suites: the sources and the classes the measurement's
#: Gradle run compiles them to. A test frame may come from either; the suite
#: never ranks it (§AR-code-coverage-deep-navigation.2).
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


def _is_lambda_class(ref: MethodRef) -> bool:
    return SYNTHETIC_LAMBDA_CLASS_MARKER in ref.owner


def _invoked_name(callee: MethodRef) -> str:
    """The name the caller's bytecode invokes: a factory stub stands for the
    constructor."""
    return "<init>" if callee.owner == FACTORY_METHOD_HOLDER else callee.name


@dataclass(frozen=True)
class TestSource:
    """Where a test class lives: its source and its class file."""

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
                    os.path.join(sources, outer),
                    os.path.join(self.path, classes, owner.replace(".", os.sep) + ".class"),
                )
                for sources, classes in TEST_SUITES
                if os.path.isfile(os.path.join(self.path, sources, outer))
            ), None)
        return self._sources[owner]

    def _methods(self, class_file: str) -> list[MethodCode]:
        if class_file not in self._classes:
            try:
                self._classes[class_file] = read_class_file(class_file)
            except (OSError, ClassFileError):
                self._classes[class_file] = []
        return self._classes[class_file]

    def _method(self, ref: MethodRef, class_file: str) -> MethodCode | None:
        named: list[MethodCode] = [
            method for method in self._methods(class_file) if method.name == ref.name
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
        if invoked is None or invoked[1] != _invoked_name(callee):
            return None
        return line_at(method.line_numbers, bci)

    def lambda_line(self, ref: MethodRef, callee: MethodRef | None) -> int | None:
        """The line of a lambda seen only through its generated class: the one
        line of the declaring class's lambda bodies that invokes the callee."""
        declaring: str = ref.owner.split(SYNTHETIC_LAMBDA_CLASS_MARKER, 1)[0]
        source: TestSource | None = self.source_of(declaring)
        if source is None or callee is None:
            return None
        lines: set[int | None] = {
            line_at(method.line_numbers, bci)
            for method in self._methods(source.class_file)
            if method.name.startswith("lambda$")
            for bci, (_, name) in method.invokes.items()
            if name == _invoked_name(callee)
        }
        return lines.pop() if len(lines) == 1 else None


@dataclass(frozen=True)
class ObservedFrame:
    """One frame of a group's `Observed` chain."""

    #: The frame as routes name it; a test lambda's owner is its declaring class.
    ref: MethodRef
    #: The test file holding the frame; `None` for any other frame.
    source: str | None = None
    #: The test frame's line, when the compiled class confirms it.
    line: int | None = None
    is_lambda: bool = False


@dataclass(frozen=True)
class GroupTrace:
    """One group's most frequent stack, from the test method down to the
    divergence."""

    chain: list[ObservedFrame]

    @property
    def test_files(self) -> list[str]:
        """The chain's test files, in call order."""
        files: list[str] = []
        for frame in self.chain:
            if frame.source is not None and frame.source not in files:
                files.append(frame.source)
        return files


def untraced(refs: list[MethodRef]) -> GroupTrace:
    """A chain of route frames alone, for a group no stack traces."""
    return GroupTrace([ObservedFrame(ref) for ref in refs])


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
        # A test lambda's generated class is the only frame some stacks keep
        # of it, so it stays a test frame.
        if self.test_project is None or (_is_synthetic(ref) and not _is_lambda_class(ref)):
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

        def preference(candidate: _Candidate) -> tuple[bool, int]:
            # A stack with a test frame before one without, whichever suite
            # the test is in; the most frequent stack within each. The route
            # may start at the test frame itself.
            tested: bool = any(
                self._test_source(ref) is not None
                for ref, _ in candidate.sample.full_path[:candidate.start + 1]
            )
            return (not tested, -candidate.count)

        if not stacks:
            return untraced(translated_path(list(divergence), self.graph))
        # `stacks` keeps sample order, so equal preferences resolve the same way
        # on every run.
        return self._chain(min(stacks.values(), key=preference))

    def _shown(self, ref: MethodRef) -> MethodRef | None:
        """A library frame as a route names it: a lambda body or class by its
        creator, a factory stub by its constructor, any other synthetic frame
        not at all."""
        static_id: int | None = resolve_graph_id(self.graph, ref)
        shown: MethodRef = translated_ref(static_id, self.graph) if static_id is not None else ref
        return None if _is_synthetic(shown) else shown

    def _test_frame(
            self,
            ref: MethodRef,
            bci: int,
            callee: MethodRef | None,
            source: TestSource,
    ) -> ObservedFrame | None:
        """A test frame with its line; a lambda by its declaring class."""
        assert self.test_project is not None
        if _is_lambda_class(ref):
            # The body frame, when the stack keeps it, carries the line.
            if callee is not None and callee.name.startswith("lambda$") \
                    and self._test_source(callee) is not None:
                return None
            declaring: str = ref.owner.split(SYNTHETIC_LAMBDA_CLASS_MARKER, 1)[0]
            return ObservedFrame(
                MethodRef(declaring, "lambda", (), "void"), source.source,
                self.test_project.lambda_line(ref, callee), True,
            )
        line: int | None = self.test_project.step_line(ref, bci, callee)
        if ref.name.startswith("lambda$"):
            return ObservedFrame(MethodRef(ref.owner, "lambda", (), "void"), source.source, line, True)
        return ObservedFrame(ref, source.source, line)

    def _chain(self, candidate: _Candidate) -> GroupTrace:
        """The stack from its outermost test frame, or without one from its
        outermost library frame, down to the divergence."""
        frames: list[tuple[MethodRef, int]] = candidate.sample.full_path
        first: int = next(
            (index for index in range(candidate.start + 1)
             if self._test_source(frames[index][0]) is not None),
            next(
                (index for index in range(candidate.start)
                 if self._is_library(frames[index][0]) and not _is_synthetic(frames[index][0])),
                candidate.start,
            ),
        )
        chain: list[ObservedFrame] = []
        for index in range(first, candidate.end + 1):
            ref, bci = frames[index]
            callee: MethodRef | None = frames[index + 1][0] if index + 1 < len(frames) else None
            source: TestSource | None = self._test_source(ref)
            frame: ObservedFrame | None
            if source is not None:
                frame = self._test_frame(ref, bci, callee, source)
            else:
                shown: MethodRef | None = self._shown(ref)
                frame = ObservedFrame(shown) if shown is not None else None
            if frame is not None and (not chain or chain[-1] != frame):
                chain.append(frame)
        return GroupTrace(chain)
