# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""The scripted `gradlew` and `gh` the workflow tests install in a fake repository.

They stand in for the harness (§AR-real-use-test-harness.2) and GitHub, driven by a
control file the test writes.
"""
from __future__ import annotations

# The scripted harness. Control file keys: scaffoldLanguage (the test source
# directory the scaffold creates, java by default), unresolvable (the filter task fails),
# spotlessFailing (a file spotless reports), checkstyleFailing (classes checkstyle
# rejects), nativeNeeds
# (class -> typeReached it needs), nativeUnfixable, collected (the entries the
# native-image agent writes when realUseJavaTest runs with an agent output),
# jacocoFails (the coverage run fails).
FAKE_GRADLEW: str = r'''#!/usr/bin/env python3
import glob, json, os, sys
task = next(arg for arg in sys.argv[1:] if not arg.startswith("-"))
control = json.load(open(os.environ["FAKE_GRADLE_CONTROL"]))
root = os.path.dirname(os.path.abspath(__file__))
project = os.path.join(root, "real-use-tests", "src", "org.example", "lib", "1.0.0")
java = os.path.join(project, "src", "test", "java")
def present():
    names = []
    for path in glob.glob(os.path.join(java, "**", "*.java"), recursive=True):
        names.append(os.path.relpath(path, java)[:-5].replace(os.sep, "."))
    return names
def report(lane, failing):
    out = os.path.join(project, "build", "test-results", lane)
    os.makedirs(out, exist_ok=True)
    cases = "".join(
        '<testcase classname="%s" name="t">%s</testcase>' % (name, "<failure/>" if name in failing else "")
        for name in present())
    open(os.path.join(out, "TEST-all.xml"), "w").write("<testsuite>%s</testsuite>" % cases)
    return 1 if failing else 0
agent_output = next((arg.split("=", 1)[1] for arg in sys.argv[1:]
                     if arg.startswith("-Ptck.realUse.agentOutput=")), None)
def native_failing():
    local = os.path.join(project, "src", "test", "resources", "META-INF", "native-image", "reachability-metadata.json")
    reached = set()
    if os.path.exists(local):
        for entry in json.load(open(local)).get("reflection", []):
            reached.add(entry["condition"]["typeReached"])
    needs = control.get("nativeNeeds", {})
    return [name for name in present()
            if name in control.get("nativeUnfixable", []) or (name in needs and needs[name] not in reached)]
if task == "scaffoldRealUseTest":
    os.makedirs(os.path.join(project, "src", "test", control.get("scaffoldLanguage", "java")), exist_ok=True)
    open(os.path.join(project, "build.gradle"), "w").write("// scaffold\n")
elif task == "generateRealUseFilter":
    if control.get("unresolvable"):
        sys.exit(1)
    rules = [{"excludeClasses": "**"}, {"includeClasses": "org.example.lib.**"}]
    open(os.path.join(project, "user-code-filter.json"), "w").write(json.dumps({"rules": rules}))
elif task == "realUseJavaTest":
    if agent_output:
        out = open(os.path.join(agent_output, "reachability-metadata.json"), "w")
        out.write(json.dumps({"reflection": control.get("collected", [])}))
elif task == "spotlessCheck":
    if control.get("spotlessFailing"):
        print("The following files had format violations: %s" % control["spotlessFailing"])
        sys.exit(1)
elif task == "checkstyle":
    failing = [n for n in present() if n in control.get("checkstyleFailing", [])]
    for name in failing:
        print("[ERROR] %s.java:1: bad style" % os.path.join(java, *name.split(".")))
    sys.exit(1 if failing else 0)
elif task == "realUseNativeTest":
    sys.exit(report("test-native", native_failing()))
elif task == "realUseJacocoReport":
    if control.get("jacocoFails"):
        sys.exit(1)
    out = os.path.join(project, "build", "reports", "jacoco", "test")
    os.makedirs(out, exist_ok=True)
    open(os.path.join(out, "jacocoTestReport.xml"), "w").write(
        '<report name="lib"><package name="org/example/lib">'
        '<counter type="LINE" missed="1" covered="1"/></package>'
        '<counter type="INSTRUCTION" missed="30" covered="70"/>'
        '<counter type="LINE" missed="5" covered="15"/>'
        '<counter type="METHOD" missed="2" covered="8"/></report>')
'''

# The fake `gh`: serves the control file's `issue` for `gh issue view` and the
# login for `gh api user`, which fails when the control file says so.
FAKE_GH: str = r'''#!/usr/bin/env python3
import json, os, sys
control = json.load(open(os.environ["FAKE_GRADLE_CONTROL"]))
if sys.argv[1:3] == ["api", "user"]:
    if control.get("loginFails"):
        print("gh: not authenticated", file=sys.stderr)
        sys.exit(1)
    print("octocat")
    sys.exit(0)
issue = control.get("issue")
if sys.argv[1:3] != ["issue", "view"] or issue is None:
    print("issue not found", file=sys.stderr)
    sys.exit(1)
print(json.dumps(issue))
'''
