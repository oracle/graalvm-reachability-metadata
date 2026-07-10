#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
# End-to-end check for the code coverage improvement helpers
# (§forge/WF-code-coverage-improvement). It exercises the real GraalVM
# PGO-sampling path and the deterministic helpers against a tiny throwaway
# library, with no dependency on a checked-in metadata coordinate.
#
# It mirrors exactly what the `runNativeTestPGO` harness task does
# (`--pgo-sampling -H:PGOSamplingPeriodMicros=... -H:+PrintAnalysisCallTree
# -H:PrintAnalysisCallTreeType=CSV`, then run with `-XX:ProfilesDumpFile=`),
# then runs:
#   1. code_coverage_api_inventory.py   (jar -> api-inventory.json)
#   2. code_coverage_profile_report.py  (sampled iprof + call-tree CSVs +
#      inventory + JaCoCo cover report -> near-call discovery)
#
# Requires an Oracle GraalVM whose native-image supports `--pgo-sampling`
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

# Builds without PGO treat `--pgo-sampling` as a positional main class and
# fail with "not a valid mainclass"; PGO-capable builds consume it as an option.
PGO_PROBE="$("${NI}" --pgo-sampling -cp / __pgo_probe__ 2>&1 || true)"
if grep -q "not a valid mainclass" <<<"${PGO_PROBE}"; then
    echo "SKIP: this native-image build does not support --pgo-sampling (needs Oracle GraalVM PGO)." >&2
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
    // so it must show up as uncovered with a near-call record in the report.
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
        // Busy-loop for ~2 seconds so the PGO sampler observes real stacks.
        long deadline = System.nanoTime() + 2_000_000_000L;
        long total = 0;
        while (System.nanoTime() < deadline) {
            total += greeter.greet().length();
        }
        System.out.println(total);
        if (args.length > 3) {
            System.out.println(greeter.shout());
        }
    }
}
JAVA

echo "[1/5] compile + jar"
( cd "${WORK}/src" && "${JAVAC}" -g com/example/*.java && "${JAR}" cf "${WORK}/demo.jar" com/example/*.class )

echo "[2/5] build PGO-sampling image with analysis call-tree CSV dump"
( cd "${WORK}/out" && "${NI}" --pgo-sampling \
        -H:+UnlockExperimentalVMOptions -H:PGOSamplingPeriodMicros=100 \
        -H:+PrintAnalysisCallTree -H:PrintAnalysisCallTreeType=CSV -H:-UnlockExperimentalVMOptions \
        -cp "${WORK}/demo.jar" com.example.Demo demo >/dev/null )

echo "[3/5] run sampling image to emit .iprof (no args -> shout() not executed)"
( cd "${WORK}/out" && ./demo "-XX:ProfilesDumpFile=${WORK}/profile.iprof" >/dev/null )
CALL_TREE_METHODS="$(ls "${WORK}/out/reports/"call_tree_methods_*.csv | head -1)"
test -f "${WORK}/profile.iprof" || { echo "FAIL: no .iprof produced" >&2; exit 1; }
test -f "${CALL_TREE_METHODS}" || { echo "FAIL: no call_tree CSV dump produced" >&2; exit 1; }

echo "[4/5] generate API inventory from the jar"
python3 "${FORGE_DIR}/utility_scripts/code_coverage_api_inventory.py" \
    --coordinate com.example:demo:1.0.0 \
    --library-jar "${WORK}/demo.jar" \
    --include-package com.example \
    --output-dir "${WORK}/inventory" >/dev/null
test -f "${WORK}/inventory/api-inventory.json"

# The workflow takes covered/uncovered statuses from the phase-5 JaCoCo
# api-cover report; synthesize the one this run would produce.
cat > "${WORK}/api-cover-report.json" <<'JSON'
{
  "coordinate": "com.example:demo:1.0.0",
  "targets": [
    {"id": "com.example.Greeter#<init>(java.lang.String):void", "status": "covered"},
    {"id": "com.example.Greeter#greet():java.lang.String", "status": "covered"},
    {"id": "com.example.Greeter#shout():java.lang.String", "status": "uncovered"}
  ]
}
JSON

echo "[5/5] near-call correlation: sampled profile + call-tree CSVs + inventory"
python3 "${FORGE_DIR}/utility_scripts/code_coverage_profile_report.py" \
    --profile "${WORK}/profile.iprof" \
    --reports-dir "${WORK}/out/reports" \
    --api-inventory "${WORK}/inventory/api-inventory.json" \
    --api-cover-report "${WORK}/api-cover-report.json" \
    --coordinate com.example:demo:1.0.0 \
    --iteration 1 \
    --output-dir "${WORK}/discovery"

python3 - "${WORK}/discovery/discovery-report-1.json" <<'PY'
import json, sys
report = json.load(open(sys.argv[1]))
summary = report["summary"]
assert report["profileKind"] == "sampled"
assert summary["coverageSource"] == "jacoco"
assert summary["totalSampleCount"] > 0, "sampling produced no samples"
status = {entry["id"]: entry["status"] for entry in report["inventory"]}
greet = "com.example.Greeter#greet():java.lang.String"
shout = "com.example.Greeter#shout():java.lang.String"
assert status.get(greet) == "covered", f"greet should be covered, got {status.get(greet)}"
assert status.get(shout) == "uncovered", f"shout should be uncovered, got {status.get(shout)}"
bulk = {entry["id"]: entry for entry in report["bulkTargets"]}
assert shout in bulk, "shout must be listed in the bulk near-call targets"
record = bulk[shout]
assert record["joinKind"] in ("sampled", "entry"), f"shout needs a route, got {record['joinKind']}"
assert record["stepsRemaining"] >= 1
print(f"PASS: samples={summary['totalSampleCount']} joinKind={record['joinKind']} "
      f"steps={record['stepsRemaining']} listed={summary['listedUncovered']}")
PY

test -f "${WORK}/discovery/coverage-1.lcov"
echo "E2E OK"
