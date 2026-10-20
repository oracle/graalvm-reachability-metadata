# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Exit codes the program states return; `states.yaml` routes on each one.

The table is §AR-real-use-test-workflow.4: the exit code is the whole decision
the orchestrator makes after a program (§FS-real-use-test-generation).
"""
from __future__ import annotations

OK: int = 0
BAD_INPUT: int = 2
INFRASTRUCTURE: int = 3
NATIVE_FAILED: int = 10
STYLE_FAILED: int = 10
NO_SCENARIO_LEFT: int = 20
