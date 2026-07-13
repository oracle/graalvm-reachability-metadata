#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
# End-to-end check for the code coverage improvement helpers
# (§WF-code-coverage-improvement.3.2). It exercises the real GraalVM
# PGO-sampling path and exact JaCoCo correlation against a tiny throwaway
# library, with no dependency on a checked-in metadata coordinate.
#
# It mirrors exactly what the `runNativeTestPGO` harness task does
# (`--pgo-sampling -H:PGOSamplingPeriodMicros=... -H:+PrintAnalysisCallTree
# -H:PrintAnalysisCallTreeType=CSV`, then run with `-XX:ProfilesDumpFile=`),
# then runs:
#   1. code_coverage_api_inventory.py   (jar -> api-inventory.json)
#   2. code_coverage_profile_report.py  (sampled iprof + call-tree CSVs +
#      public inventory + exact JaCoCo XML -> internal path guidance)
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

    public String render(String mode) {
        if ("shout".equals(mode)) {
            return shoutInternal();
        }
        return plainInternal();
    }

    private String plainInternal() {
        return "hello " + who;
    }

    // Reachable through render("shout"), but this E2E run selects "plain".
    private String shoutInternal() {
        return plainInternal().toUpperCase();
    }
}
JAVA

cat > "${WORK}/src/com/example/Demo.java" <<'JAVA'
package com.example;

public class Demo {
    public static void main(String[] args) {
        Greeter greeter = new Greeter("world");
        String mode = args.length == 0 ? "plain" : args[0];
        // Busy-loop for ~2 seconds so the PGO sampler observes real stacks.
        long deadline = System.nanoTime() + 2_000_000_000L;
        long total = 0;
        while (System.nanoTime() < deadline) {
            total += greeter.render(mode).length();
        }
        System.out.println(total);
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

echo "[3/5] run sampling image to emit .iprof (no args -> plain branch)"
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

# Synthesize the exact JaCoCo method evidence this branch-selecting JVM run
# would produce. JaCoCo remains the only covered/uncovered authority.
cat > "${WORK}/jacoco.xml" <<'XML'
<report name="pgo-e2e">
  <package name="com/example">
    <class name="com/example/Greeter" sourcefilename="Greeter.java">
      <method name="&lt;init&gt;" desc="(Ljava/lang/String;)V" line="6">
        <counter type="METHOD" missed="0" covered="1"/>
      </method>
      <method name="render" desc="(Ljava/lang/String;)Ljava/lang/String;" line="10">
        <counter type="METHOD" missed="0" covered="1"/>
      </method>
      <method name="plainInternal" desc="()Ljava/lang/String;" line="17">
        <counter type="METHOD" missed="0" covered="1"/>
      </method>
      <method name="shoutInternal" desc="()Ljava/lang/String;" line="22">
        <counter type="METHOD" missed="1" covered="0"/>
      </method>
    </class>
  </package>
</report>
XML

echo "[5/5] near-call correlation: sampled profile + call-tree CSVs + inventory"
python3 "${FORGE_DIR}/utility_scripts/code_coverage_profile_report.py" \
    --profile "${WORK}/profile.iprof" \
    --reports-dir "${WORK}/out/reports" \
    --api-inventory "${WORK}/inventory/api-inventory.json" \
    --jacoco-xml "${WORK}/jacoco.xml" \
    --coordinate com.example:demo:1.0.0 \
    --iteration 1 \
    --output-dir "${WORK}/discovery"

python3 - "${WORK}/discovery/discovery-report-1.json" <<'PY'
import json, sys
report = json.load(open(sys.argv[1]))
summary = report["summary"]
assert report["profileKind"] == "sampled-guidance"
assert summary["coverageSource"] == "jacoco"
assert summary["totalSampleCount"] > 0, "sampling produced no samples"
inventory_ids = {entry["id"] for entry in report["inventory"]}
internal = "com.example.Greeter#shoutInternal():java.lang.String"
assert internal not in inventory_ids, "private helper must not be a public API target"
deep = {entry["id"]: entry for entry in report["deepMethods"]}
assert deep[internal]["status"] == "uncovered"
bulk = {entry["id"]: entry for entry in report["bulkTargets"]}
assert internal in bulk, "uncovered internal helper must be prompt-actionable"
record = bulk[internal]
assert record["joinKind"] in ("sampled", "public-entry"), record
assert record["stepsRemaining"] >= 1
print(f"PASS: samples={summary['totalSampleCount']} joinKind={record['joinKind']} "
      f"steps={record['stepsRemaining']} listed={summary['listedUncovered']}")
PY

test -f "${WORK}/discovery/coverage-1.lcov"
echo "E2E OK"
