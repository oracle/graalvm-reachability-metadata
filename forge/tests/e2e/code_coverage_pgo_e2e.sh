#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
# End-to-end check for the code coverage improvement helpers
# (§forge/WF-code-coverage-improvement). It exercises the real GraalVM
# instrumented-PGO path and the deterministic helpers against a tiny throwaway
# library, with no dependency on a checked-in metadata coordinate.
#
# It mirrors exactly what the `runNativeTestPGO` harness task does
# (`--pgo-instrument -g -H:+PrintAnalysisCallTree`, then run with
# `-XX:ProfilesDumpFile=`), then runs:
#   1. code_coverage_api_inventory.py   (jar -> api-inventory.json)
#   2. code_coverage_profile_report.py  (iprof + call_tree + inventory -> discovery)
#
# Requires an Oracle GraalVM whose native-image supports `--pgo-instrument`
# (a Community/dev build without PGO will be detected and the script will exit
# with status 2 so it is skipped rather than failing).
#
# Usage:
#   JAVA_HOME=/path/to/oracle-graalvm forge/tests/e2e/code_coverage_pgo_e2e.sh
set -euo pipefail

FORGE_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
export PYTHONPATH="${FORGE_DIR}${PYTHONPATH:+:${PYTHONPATH}}"
JAVA_HOME="${JAVA_HOME:-${GRAALVM_HOME:-}}"
if [[ -z "${JAVA_HOME}" || ! -x "${JAVA_HOME}/bin/native-image" ]]; then
    echo "SKIP: JAVA_HOME/GRAALVM_HOME with bin/native-image is required." >&2
    exit 2
fi
NI="${JAVA_HOME}/bin/native-image"
JAVAC="${JAVA_HOME}/bin/javac"
JAR="${JAVA_HOME}/bin/jar"

# Builds without PGO treat `--pgo-instrument` as a positional main class and
# fail with "not a valid mainclass"; PGO-capable builds consume it as an option.
PGO_PROBE="$("${NI}" --pgo-instrument -cp / __pgo_probe__ 2>&1 || true)"
if grep -q "not a valid mainclass" <<<"${PGO_PROBE}"; then
    echo "SKIP: this native-image build does not support --pgo-instrument (needs Oracle GraalVM PGO)." >&2
    exit 2
fi

WORK="$(mktemp -d)"
trap 'rm -rf "${WORK}"' EXIT
mkdir -p "${WORK}/src/com/example" "${WORK}/out"

cat > "${WORK}/src/com/example/Greeter.java" <<'JAVA'
package com.example;

public class Greeter {
    private final String who;

    public Greeter(String who) {
        this.who = who;
    }

    public String greet() {
        return "hello " + who;
    }

    // Reachable from main behind a runtime branch that the e2e run never takes,
    // so it must show up as reachable-but-uncovered in the discovery report.
    public String shout() {
        return greet().toUpperCase();
    }
}
JAVA

cat > "${WORK}/src/com/example/Demo.java" <<'JAVA'
package com.example;

public class Demo {
    public static void main(String[] args) {
        Greeter greeter = new Greeter("world");
        System.out.println(greeter.greet());
        if (args.length > 3) {
            System.out.println(greeter.shout());
        }
    }
}
JAVA

echo "[1/5] compile + jar"
( cd "${WORK}/src" && "${JAVAC}" -g com/example/*.java && "${JAR}" cf "${WORK}/demo.jar" com/example/*.class )

echo "[2/5] build instrumented PGO image with analysis call-tree dump"
( cd "${WORK}/out" && "${NI}" --pgo-instrument -g \
        -H:+UnlockExperimentalVMOptions -H:+PrintAnalysisCallTree -H:-UnlockExperimentalVMOptions \
        -cp "${WORK}/demo.jar" com.example.Demo demo >/dev/null )

echo "[3/5] run instrumented image to emit .iprof (no args -> shout() not executed)"
( cd "${WORK}/out" && ./demo "-XX:ProfilesDumpFile=${WORK}/profile.iprof" >/dev/null )
CALL_TREE="$(ls "${WORK}/out/reports/"call_tree_*.txt | head -1)"
test -f "${WORK}/profile.iprof" || { echo "FAIL: no .iprof produced" >&2; exit 1; }
test -f "${CALL_TREE}" || { echo "FAIL: no call_tree produced" >&2; exit 1; }

echo "[4/5] generate API inventory from the jar"
python3 "${FORGE_DIR}/utility_scripts/code_coverage_api_inventory.py" \
    --coordinate com.example:demo:1.0.0 \
    --library-jar "${WORK}/demo.jar" \
    --include-package com.example \
    --output-dir "${WORK}/inventory" >/dev/null
test -f "${WORK}/inventory/api-inventory.json"

echo "[5/5] correlate PGO profile + call tree against the inventory"
python3 "${FORGE_DIR}/utility_scripts/code_coverage_profile_report.py" \
    --profile "${WORK}/profile.iprof" \
    --call-tree "${CALL_TREE}" \
    --api-inventory "${WORK}/inventory/api-inventory.json" \
    --coordinate com.example:demo:1.0.0 \
    --iteration 1 \
    --output-dir "${WORK}/discovery"

python3 - "${WORK}/discovery/discovery-report-1.json" <<'PY'
import json, sys
report = json.load(open(sys.argv[1]))
summary = report["summary"]
assert summary["executedMethods"] > 0, "no executed methods parsed from profile contexts"
assert report["profileKind"] == "instrumented"
status = {entry["id"]: entry["status"] for entry in report["inventory"]}
greet = "com.example.Greeter#greet():java.lang.String"
shout = "com.example.Greeter#shout():java.lang.String"
assert status.get(greet) == "covered", f"greet should be covered, got {status.get(greet)}"
assert status.get(shout) == "reachable-uncovered", \
    f"shout should be reachable-but-uncovered, got {status.get(shout)}"
print(f"PASS: executed={summary['executedMethods']} reachable={summary['reachableMethods']} "
      f"greet=covered shout=reachable-uncovered")
PY

test -f "${WORK}/discovery/coverage-1.lcov"
echo "E2E OK"
