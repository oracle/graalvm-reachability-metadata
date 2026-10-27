# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The one fix-request writer every sender calls, and its brief table.

A request carries the facts of one failure and nothing else: the return code,
the failing commands, one brief per recognized error, each command's output
tail, and the files the fix may edit (§AR-forge-workflow-prompts.5). The rules
live in the `fix` instruction, so a brief is one line of what to do, never a
rule. Sending a request arms the loop guard (§AR-forge-workflow-pipeline.6).
"""
from __future__ import annotations

import glob
import os
import re
from dataclasses import dataclass

from exit_codes import RETURN_CODES
from run_state import RunState, runtime_path, write_text

FIX_REQUEST: str = "fix-request.md"
DEFAULT_BRIEF: str = "Make the command pass."


@dataclass(frozen=True)
class Failure:
    """One failing command and its output tail."""

    command: str
    output: str
    # A program check names its own brief instead of matching the table.
    brief: str | None = None


@dataclass(frozen=True)
class Brief:
    """One row of the brief table: the error it recognizes and what to do about it."""

    error: str
    pattern: re.Pattern[str]
    text: str
    # Only commands naming one of these tasks are matched, when set.
    commands: tuple[str, ...] = ()
    # A matching exclusive row is the command's only brief.
    exclusive: bool = False


TYPE_REACHED: re.Pattern[str] = re.compile(r"^TypeReached: (\S+)", re.MULTILINE)
OUTSIDE_ALLOWED: re.Pattern[str] = re.compile(r"doesn't belong to any of the specified packages")

BRIEFS: tuple[Brief, ...] = (
    Brief("test compilation error",
          re.compile(r"compileTest\w*'? FAILED|> Task :\S*compileTest\w* FAILED|error: cannot find symbol|"
                     r"Compilation failed|^e: ", re.MULTILINE),
          "Move the tests to the API this version has, keeping what each asserts.", (), True),
    Brief("missing registration",
          re.compile(r"Missing\w*RegistrationError|MissingReflectionRegistration|MissingResourceRegistration"),
          "Add the entry the error names to the shipped metadata, with a `typeReached` condition in the "
          "allowed packages.", ("test",)),
    Brief("runtime class definition or bytecode generation",
          re.compile(r"cannot be defined at run ?time|Defining (hidden )?classes at runtime|"
                     r"No classes have been predefined|bytecode generation|ByteBuddy|Byte Buddy", re.IGNORECASE),
          "Reshape the test to avoid it, or delete that test.", ("nativeTestCompile", "test")),
    Brief("the native build or run names a flag",
          re.compile(r"--enable-url-protocols|URL protocol .* (is not|was not) (supported|enabled)|"
                     r"--initialize-at-(run|build)-time|AddAllCharsets|IncludeLocales|IncludeResourceBundles|"
                     r"AdditionalSecurityProviders|--add-opens|--add-exports|SharedArenaSupport|--future-defaults"),
          "Add it if it is on the allowed list, in its narrowest form.", ("nativeTestCompile", "test")),
    Brief("style violation",
          re.compile(r"."),
          "Run `spotlessApply`, then fix what `checkstyle` reports.", ("spotlessCheck", "checkstyle"), True),
    Brief("JVM test failure",
          re.compile(r"\d+ tests? completed, \d+ failed|There were failing tests|> Task :\S*(javaTest|test) FAILED"),
          "Adapt the test to this version's behavior; drop a test only for removed API."),
)


def test_packages(state: RunState) -> set[str]:
    """The packages the coordinate's test sources declare."""
    packages: set[str] = set()
    pattern: re.Pattern[str] = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
    for path in glob.glob(os.path.join(state.path(state.test_dir), "src", "test", "**", "*.*"), recursive=True):
        if path.endswith((".java", ".kt", ".scala", ".groovy")):
            with open(path, encoding="utf-8", errors="replace") as handle:
                packages.update(pattern.findall(handle.read()))
    return packages


def metadata_check_briefs(output: str, packages: set[str]) -> list[str]:
    """The `checkMetadataFiles` briefs: a test-only type, or a condition outside the allowed packages."""
    if not OUTSIDE_ALLOWED.search(output):
        return []
    reached: list[str] = TYPE_REACHED.findall(output)
    if any(name.startswith(package + ".") for name in reached for package in packages):
        return ["test-only type: Run `splitTestOnlyMetadata`."]
    return ["condition outside the allowed packages: Regenerate through the harness tasks; "
            "if another artifact owns the type, route it to that owner."]


def briefs_for(failure: Failure, packages: set[str]) -> list[str]:
    """One brief per recognized error in a command's output, or the default."""
    if failure.brief is not None:
        return [failure.brief]
    task: str = task_name(failure.command)
    found: list[str] = metadata_check_briefs(failure.output, packages) if task == "checkMetadataFiles" else []
    for brief in BRIEFS:
        if brief.commands and task not in brief.commands:
            continue
        if not brief.pattern.search(failure.output):
            continue
        if brief.exclusive:
            return [f"{brief.error}: {brief.text}"]
        found.append(f"{brief.error}: {brief.text}")
    return found or [DEFAULT_BRIEF]


def task_name(command: str) -> str:
    """The Gradle task of `[env NAME=value ...] ./gradlew [clean] <task> ...`, as a matrix entry reruns it."""
    words: list[str] = command.split()
    if "./gradlew" not in words:
        return words[1] if len(words) > 1 else command
    tasks: list[str] = [word for word in words[words.index("./gradlew") + 1:] if not word.startswith("-")]
    return next((task for task in tasks if task != "clean"), command)


def editable_paths(state: RunState, sender: str) -> list[str]:
    """The files the fix may edit: the test project, and the shipped metadata past `javaTest`.

    Past `javaTest`, metadata another library owns goes to that owner, entries only added
    (§root/FS-contribution-contract.2).
    """
    paths: list[str] = [state.path(state.test_dir) + "/**"]
    if sender != "measure":
        paths.append(state.path(os.path.join("metadata", state.group, state.artifact)) + "/**")
        paths.append("the owner's `metadata/<group>/<artifact>/<version>/reachability-metadata.json`, "
                     "through `routeForeignMetadata` only")
    if sender == "finalize":
        paths.append(state.path(os.path.join("stats", state.group, state.artifact)) + "/**")
    return paths


def render(state: RunState, sender: str, failures: list[Failure], briefs: list[str] | None = None) -> str:
    """The request; `briefs` replaces the per-command briefs, as the audit's one brief does."""
    packages: set[str] = test_packages(state)
    lines: list[str] = [f"# Fix: {state.coordinate}", f"Return: {RETURN_CODES[sender]}", "",
                        "## Failing commands" if briefs is None else "## Commands"]
    lines += [f"- {command}" for command in dict.fromkeys(failure.command for failure in failures)]
    lines += ["", "## Briefs"]
    if briefs is not None:
        lines += [f"- {sender}: {brief}" for brief in briefs]
    for failure in failures if briefs is None else []:
        # A program check's own brief already names the check.
        label: str = "" if failure.brief is not None else f"{task_name(failure.command)}: "
        lines += [f"- {label}{brief}" for brief in briefs_for(failure, packages)]
    outputs: list[Failure] = [failure for failure in failures if failure.output.strip()]
    lines += ["", "## Output"] if outputs else []
    for failure in outputs:
        lines += ["", f"### {failure.command}", "", "```text", failure.output.rstrip(), "```"]
    lines += ["", "## You may edit"]
    lines += [f"- {path}" for path in editable_paths(state, sender)]
    return "\n".join(lines) + "\n"


def write_fix_request(workspace: str, state: RunState, sender: str, failures: list[Failure],
                      briefs: list[str] | None = None) -> None:
    """Write the request for `fix` and arm the sender's loop guard; the caller saves the state."""
    write_text(runtime_path(workspace, FIX_REQUEST), render(state, sender, failures, briefs))
    state.pending_request = sender


def write_placeholder(workspace: str, sender: str) -> None:
    """A declared output must exist when its program exits 0; a placeholder is never handed to `fix`."""
    write_text(runtime_path(workspace, FIX_REQUEST), f"No fix request: `{sender}` passed.\n")


def guard_tripped(state: RunState, sender: str) -> bool:
    """Whether this failure follows the sender's own fix, so it must not ask again."""
    return state.pending_request == sender
