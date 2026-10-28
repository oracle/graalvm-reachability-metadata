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
# generateFails (only `generateMetadata` fails), nativeFails (until the shipped
# metadata names `Fixed`), checkFails,
# styleFails (the failing style task), listFails, listsNoCalls (the listing
# succeeds without output, as for a library without calls; an empty stored
# listing then reports no calls), coverageFails, indexFails, and
# stats (the dynamicAccess generateLibraryStats writes; else the coverage report's),
# and matrixFails (the native-image mode whose `test` fails, as a missing registration).
FAKE_GRADLEW: str = r'''#!/usr/bin/env python3
import glob, json, os, sys
args = sys.argv[1:]
if args == ["--stop"]:
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
mode = os.environ.get("GVM_TCK_NATIVE_IMAGE_MODE")
lane = " [%s %s %s]" % (os.environ.get("GVM_TCK_LV"), mode, os.environ.get("JAVA_HOME")) if mode else ""
with open(os.path.join(root, "gradle-calls.log"), "a") as log:
    log.write(" ".join(args) + lane + "\n")
if task == "scaffold":
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
elif task == "generateMetadata":
    if "--agentAllowedPackages=fromJar" in args:
        open(os.path.join(project, "user-code-filter.json"), "w").write('{"rules": []}\n')
    if control.get("javaFails") or control.get("generateFails"):
        print("There were failing tests")
        sys.exit(1)
elif task == "javaTest":
    if control.get("javaFails"):
        print("2 tests completed, 1 failed")
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
        sys.exit(1)
    if control.get("listsNoCalls"):
        print("No dynamic access JSON files were generated because the output directory is missing")
        sys.exit(0)
    out = os.path.join(project, "build", "native", "nativeTestCompile", "dynamic-access")
    os.makedirs(out, exist_ok=True)
    open(os.path.join(out, "lib.json"), "w").write("[]")
elif task == "generateDynamicAccessCoverageReport":
    stored = prop("tck.dynamicAccess.callSites")
    if stored is None or not os.path.isdir(stored) or control.get("javaFails") or control.get("coverageFails"):
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

# The fake `gh`: serves the control file's `issue`, the login, and records edits.
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
        print("issue not found", file=sys.stderr)
        sys.exit(1)
    print(json.dumps(control["issue"]))
    sys.exit(0)
if sys.argv[1:3] == ["issue", "edit"]:
    sys.exit(0)
print("unsupported gh call: %s" % sys.argv[1:], file=sys.stderr)
sys.exit(1)
'''
