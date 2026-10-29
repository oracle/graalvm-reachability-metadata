# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""Find the user-code filter a test project's native-image agent reads.

Most projects use the scaffold's `user-code-filter.json`, but an agent block in
`build.gradle` may name another file as `userCodeFilterPath`. The setup tail
(§AR-forge-workflow-pipeline.4.1) and the native-trace condition packages
(§FS-native-test-verification-gate) read the file the agent reads.
"""
from __future__ import annotations

import os
import re

DEFAULT_FILTER: str = "user-code-filter.json"
CONFIGURED_FILTER: re.Pattern[str] = re.compile(r"""\buserCodeFilterPath\s*=\s*["']([^"']+)["']""")


def user_code_filter_path(test_dir: str) -> str:
    """The filter the agent of `test_dir` reads (§AR-forge-workflow-pipeline.4.1).

    That is the last `userCodeFilterPath` its `build.gradle` names, since the last
    assignment wins, else `user-code-filter.json`.
    """
    try:
        with open(os.path.join(test_dir, "build.gradle"), encoding="utf-8") as handle:
            named: list[str] = CONFIGURED_FILTER.findall(handle.read())
    except OSError:
        named = []
    return os.path.join(test_dir, named[-1] if named else DEFAULT_FILTER)
