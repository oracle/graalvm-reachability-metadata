# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Exit codes the program states return; `states.yaml` routes on each one.

The table is §AR-forge-workflow-pipeline.2: a program's exit code is the whole
decision the orchestrator makes after it (§FS-forge-issue-resolution-goal).
"""
from __future__ import annotations

OK: int = 0
BAD_INPUT: int = 2
INFRASTRUCTURE: int = 3
AGENT_WORK: int = 10
JVM_TESTS_FAIL: int = 11
STILL_FAILING: int = 12
NOTHING_TO_DO: int = 20
# `setup-check` only: the setup verdict is a skip record (§FS-unsupportable-version-diagnosis.3).
SKIP_RECORDED: int = 21
# `prepare` only: the artifact's index is the record (§FS-not-for-native-image-record.2).
NOT_FOR_NATIVE_IMAGE_RECORDED: int = 22
CANNOT_FIX: int = 40

# The `fix-route` exits, one per sender (§AR-forge-workflow-pipeline.2).
RETURN_JVM: int = 30
RETURN_MEASURE: int = 31
RETURN_NATIVE: int = 32
RETURN_FINALIZE: int = 33
RETURN_AUDIT: int = 34
RETURN_NATIVE_REPAIR: int = 35
RETURN_CODES: dict[str, int] = {
    "jvm": RETURN_JVM,
    "measure": RETURN_MEASURE,
    "native": RETURN_NATIVE,
    "finalize": RETURN_FINALIZE,
    "audit": RETURN_AUDIT,
    "native-repair": RETURN_NATIVE_REPAIR,
}
