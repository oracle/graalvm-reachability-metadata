# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

"""
The Forge dispatcher package.

Modules follow the producer/consumer lifecycle boundaries declared in
§AR-forge-dispatcher-decomposition: shared foundations sit below the
issue-claiming producer side and the pull-request review consumer side, and
dependencies point strictly downward. `forge_metadata.py` remains the CLI
entry point (§AR-forge-orchestration), resolving supported GitHub issues
into isolated workflow runs (§FS-forge-issue-resolution-goal).
"""
