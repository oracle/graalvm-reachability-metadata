# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The scripted `gradlew` and `gh` the workflow tests install in a fake repository.

They stand in for the harness and GitHub, driven by a control file the test
writes, so the program tests can check the routing of §AR-forge-workflow-pipeline.3
without Gradle, Native Image, or the network.
"""
from __future__ import annotations

# Control keys: classes (class -> total dynamic-access calls; a test file whose
# name starts with the class's simple name covers one call), javaFails,
# extensionFails (only the code-coverage-improvement suite's `codeCoverageTest` fails),
# unresolved (`javaTest` fails on a dependency that does not resolve; "unreachable":
# on Maven Central answering 502), filterFails (a plain `generateUserCodeFilter`
# prints this; from the JAR, "unresolved" as above, else this), scaffoldFails
# ("variant": the library has no JVM variant, "unreachable" as above, "bom": it
# has no JAR, "jar": its POM is served but not its JAR, "elsewhere": an artifact
# of the harness's own build is missing, else otherwise), generateFails (only
# `generateMetadata` fails), nativeFails (until the shipped
# metadata names `Fixed`), checkFails,
# styleFails (the failing style task), listFails, listsNoCalls (the listing
# succeeds without output, as for a library without calls; an empty stored
# listing then reports no calls), coverageFails, indexFails, and
# stats (the dynamicAccess generateLibraryStats writes; else the coverage report's),
# matrixFails (the native-image mode whose `test` fails, as a missing registration),
# and unreachableIn (the task, in any lane, that fails on Maven Central answering 502).
# Beside them, a `stale-daemon` file in the Gradle user home names the task a daemon
# of that home, holding stale build logic, fails before Gradle creates it, until a
# `--stop` against that home ends the daemon.
FAKE_GRADLEW: str = r'''#!/usr/bin/env python3
import glob, json, os, sys
args = sys.argv[1:]
stale = os.path.join(os.environ.get("GRADLE_USER_HOME", "/nonexistent"), "stale-daemon")
if "--stop" in args:
    if os.path.exists(stale):
        os.remove(stale)
    open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "gradle-calls.log"), "a").write("--stop\n")
    sys.exit(0)
task = next(arg for arg in args if not arg.startswith("-") and arg != "clean")
control = json.load(open(os.environ["FAKE_FORGE_CONTROL"]))
root = os.path.dirname(os.path.abspath(__file__))
coordinate = next((arg.split("=", 1)[1] for arg in args if arg.startswith(("-Pcoordinates=", "--coordinates="))), "")
prop = lambda name: next((arg.split("=", 1)[1] for arg in args if arg.startswith("-P" + name + "=")), None)
group, artifact, version = (coordinate.split(":") + ["", "", ""])[:3]
project = os.path.join(root, "tests", "src", group, artifact, version)
metadata = os.path.join(root, "metadata", group, artifact, version, "reachability-metadata.json")
def tests():
    return [os.path.basename(path) for path in glob.glob(os.path.join(project, "src", "test", "java", "**", "*.java"), recursive=True)]
def fail_build(cause):
    # Longer than an excerpt, with the cause among the task lines and Gradle's own failure lines.
    for number in range(30):
        print("> Task :tck-build-logic:step%d UP-TO-DATE" % number)
    print("> Task :%s FAILED\nFAILURE: Build failed with an exception.\n* What went wrong:" % task)
    print("Execution failed for task ':%s'." % task)
    print(cause)
    print("BUILD FAILED in 4s")
    sys.exit(1)
def fail_unresolved():
    fail_build("\n".join("> Could not resolve all files for configuration ':%s'.\n"
                         "   > Could not find org.example:missing:1.0.\n     Required by:\n         root project 'lib'"
                         % configuration for configuration in ("testCompileClasspath", "testRuntimeClasspath")))
def fail_resolving(configuration, cause):
    # Gradle's wrappers around the innermost cause the coordinate does not resolve for.
    fail_build("> Could not resolve all files for configuration ':%s'.\n"
               "   > Could not resolve %s.\n     Required by:\n         root project 'reachability-metadata'\n%s"
               % (configuration, coordinate, cause))
def fail_unreachable(configuration):
    pom = "https://repo.maven.apache.org/maven2/%s/%s/%s/%s-%s.pom" % (group.replace(".", "/"), artifact, version,
                                                                      artifact, version)
    fail_resolving(configuration, "      > Could not get resource '%s'.\n         > Could not GET '%s'. Received "
                                  "status code 502 from server: Bad Gateway" % (pom, pom))
def fail_scaffold(failure):
    if failure == "variant":
        fail_resolving("detachedConfiguration1",
                       "      > No matching variant of %s was found. The consumer was configured to find attribute "
                       "'org.gradle.jvm.environment' with value 'standard-jvm' but:\n"
                       "          - Variant 'wasmJsApiElements' declares attribute 'org.gradle.category' with "
                       "value 'library':\n"
                       "              - Incompatible because this component declares attribute "
                       "'org.gradle.jvm.environment' with value 'non-jvm'\n\n* Try:\n"
                       "> No matching variant errors are explained in more detail at https://docs.gradle.org/"
                       % coordinate)
    if failure == "unreachable":
        fail_unreachable("detachedConfiguration1")
    if failure == "bom":
        fail_build("> Failed to resolve JAR for %s" % coordinate)
    if failure == "jar":
        jar = "%s-%s.jar" % (artifact, version)
        fail_build("> Could not resolve all files for configuration ':detachedConfiguration1'.\n"
                   "   > Could not find %s (%s).\n     Searched in the following locations:\n"
                   "         https://repo.maven.apache.org/maven2/%s/%s/%s/%s"
                   % (jar, coordinate, group.replace(".", "/"), artifact, version, jar))
    if failure == "elsewhere":
        fail_build("> Could not resolve all files for configuration ':tck-build-logic:compileClasspath'.\n"
                   "   > Could not find org.example:harness-plugin:1.0.\n     Required by:\n"
                   "         project ':tck-build-logic'")
    print("Execution failed for task ':scaffold'.\n> Failed to write the index: Permission denied")
    sys.exit(1)
mode = os.environ.get("GVM_TCK_NATIVE_IMAGE_MODE")
lane = " [%s %s %s]" % (os.environ.get("GVM_TCK_LV"), mode, os.environ.get("JAVA_HOME")) if mode else ""
with open(os.path.join(root, "gradle-calls.log"), "a") as log:
    log.write(" ".join(args) + lane + "\n")
if os.path.exists(stale) and open(stale).read() == task:
    print("FAILURE: Build failed with an exception.\n* What went wrong:")
    print("A problem occurred configuring root project 'reachability-metadata'.\n> Could not create task ':%s'.\n"
          "   > Could not generate a decorated class for type %sTask.\n"
          "      > com/fasterxml/jackson/databind/JsonNode" % (task, task[0].upper() + task[1:]))
    sys.exit(1)
if control.get("unreachableIn") == task:
    fail_unreachable("testRuntimeClasspath")
if task == "scaffold":
    if control.get("scaffoldFails"):
        fail_scaffold(control["scaffoldFails"])
    os.makedirs(os.path.join(project, "src", "test", "java", "org_example"), exist_ok=True)
    open(os.path.join(project, "build.gradle"), "w").write("// scaffold\n")
    open(os.path.join(project, "src", "test", "java", "org_example", "Placeholder.java"), "w").write("package org_example;\n")
    os.makedirs(os.path.dirname(metadata), exist_ok=True)
    open(metadata, "w").write("{}\n")
    index = os.path.join(root, "metadata", group, artifact, "index.json")
    entries = json.load(open(index)) if os.path.exists(index) else []
    entries.append({"metadata-version": version, "tested-versions": [version], "latest": True,
                    "allowed-packages": [group]})
    json.dump(entries, open(index, "w"), indent=2)
elif task == "generateUserCodeFilter":
    if control.get("filterFails") == "unresolved":
        fail_unresolved()
    if control.get("filterFails"):
        print(control["filterFails"])
        sys.exit(1)
    open(os.path.join(project, "user-code-filter.json"), "w").write('{"rules": []}\n')
elif task == "generateMetadata":
    if "--agentAllowedPackages=fromJar" in args:
        if control.get("filterFails") == "unresolved":
            fail_unresolved()
        if control.get("filterFails"):
            print("Execution failed for task ':generateMetadata'.\n> Process 'command java' finished with non-zero exit value 1")
            sys.exit(1)
        open(os.path.join(project, "user-code-filter.json"), "w").write('{"rules": []}\n')
    if control.get("javaFails") or control.get("generateFails"):
        print("There were failing tests")
        sys.exit(1)
elif task == "javaTest":
    if control.get("unresolved") == "unreachable":
        fail_unreachable("testRuntimeClasspath")
    if control.get("unresolved"):
        fail_unresolved()
    if control.get("javaFails"):
        print("2 tests completed, 1 failed")
        sys.exit(1)
elif task == "codeCoverageTest":
    if control.get("extensionFails"):
        print("error: variable result is already defined")
        sys.exit(1)
elif task == "checkMetadataFiles":
    if control.get("checkFails"):
        print("ERROR: In file x\nEntry: e\nTypeReached: org.other.Type\ndoesn't belong to any of the specified packages: [org.example]")
        sys.exit(1)
elif task == "nativeTest":
    print("Task 'nativeTest' is ambiguous in root project 'reachability-metadata'.")
    sys.exit(1)
elif task == "test" and mode is None:
    fixed = os.path.exists(metadata) and "Fixed" in open(metadata).read()
    if control.get("nativeFails") and not fixed:
        print("org.graalvm.nativeimage.MissingReflectionRegistrationError: org.example.lib.Codec")
        sys.exit(1)
elif task == "generateDynamicAccessReport":
    if control.get("listFails"):
        fail_build("Fatal error: com.oracle.graal.pointsto.constraints.UnsupportedFeatureException: "
                   "Unresolved type found: org.example.missing.Location")
    if control.get("listsNoCalls"):
        print("No dynamic access JSON files were generated because the output directory is missing")
        sys.exit(0)
    out = os.path.join(project, "build", "native", "nativeTestCompile", "dynamic-access")
    os.makedirs(out, exist_ok=True)
    open(os.path.join(out, "lib.json"), "w").write("[]")
elif task == "generateDynamicAccessCoverageReport":
    stored = prop("tck.dynamicAccess.callSites")
    if stored is None or not os.path.isdir(stored) or any(
            control.get(key) for key in ("javaFails", "extensionFails", "coverageFails")):
        print("the coverage report failed")
        sys.exit(1)
    names = tests()
    classes = []
    for name, total in (control.get("classes", {}) if os.listdir(stored) else {}).items():
        simple = name.rsplit(".", 1)[-1]
        covered = min(total, sum(1 for test in names if test.startswith(simple)))
        sites = [{"metadataType": "reflection", "trackedApi": "java.lang.Class#forName(java.lang.String)",
                  "frame": "%s.load(%s.java:%d)" % (name, simple, line), "line": line, "covered": line <= covered}
                 for line in range(1, total + 1)]
        classes.append({"className": name, "sourceFile": simple + ".java", "totalCalls": total,
                        "coveredCalls": covered, "callSites": sites})
    report = {"coordinate": coordinate, "hasDynamicAccess": bool(classes),
              "totals": {"totalCalls": sum(c["totalCalls"] for c in classes),
                         "coveredCalls": sum(c["coveredCalls"] for c in classes)},
              "classes": classes}
    out = os.path.join(project, "build", "reports", "dynamic-access")
    os.makedirs(out, exist_ok=True)
    json.dump(report, open(os.path.join(out, "dynamic-access-coverage.json"), "w"))
elif task == "test":
    if mode is not None and control.get("matrixFails") == mode:
        print("org.graalvm.nativeimage.MissingReflectionRegistrationError: org.example.lib.Codec")
        sys.exit(1)
elif task == "validateIndexFiles":
    if control.get("indexFails"):
        print("ERROR: index.json does not validate")
        sys.exit(1)
elif task in ("spotlessApply", "spotlessCheck", "checkstyle"):
    if control.get("styleFails") == task:
        print("[ERROR] Test.java:1: bad style")
        sys.exit(1)
elif task == "generateLibraryStats":
    stats = os.path.join(root, "stats", group, artifact, version, "stats.json")
    os.makedirs(os.path.dirname(stats), exist_ok=True)
    report = os.path.join(project, "build", "reports", "dynamic-access", "dynamic-access-coverage.json")
    dynamic = "N/A"
    if os.path.exists(report):
        totals = json.load(open(report))["totals"]
        dynamic = {"coveredCalls": totals["coveredCalls"], "totalCalls": totals["totalCalls"],
                   "coverageRatio": round(totals["coveredCalls"] / max(totals["totalCalls"], 1), 6), "breakdown": {}}
    dynamic = control.get("stats", dynamic)
    json.dump({"versions": [{"version": version, "dynamicAccess": dynamic}]}, open(stats, "w"))
'''

# The fake `gh`: serves the control file's `issue`, or fails with its `issueError`, the login,
# and records edits.
FAKE_GH: str = r'''#!/usr/bin/env python3
import json, os, sys
control = json.load(open(os.environ["FAKE_FORGE_CONTROL"]))
with open(os.environ["FAKE_FORGE_CONTROL"] + ".gh.log", "a") as log:
    log.write(" ".join(sys.argv[1:]) + "\n")
if sys.argv[1:3] == ["api", "user"]:
    print(control.get("login", "octocat"))
    sys.exit(0)
if sys.argv[1:3] == ["issue", "view"]:
    if control.get("issue") is None:
        print(control.get("issueError", "issue not found"), file=sys.stderr)
        sys.exit(1)
    print(json.dumps(control["issue"]))
    sys.exit(0)
if sys.argv[1:3] == ["issue", "edit"]:
    sys.exit(0)
print("unsupported gh call: %s" % sys.argv[1:], file=sys.stderr)
sys.exit(1)
'''
