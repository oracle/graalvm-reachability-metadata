#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# Workspaces for the drain queue's pull requests. §FS-maintainer-agent-queues.3

# Makes the branch workspace of every open GenAI pull request in the feed that
# still carries `human-intervention`: an unattended sweep refuses a branch that
# is not on disk. Run it before `ephor work sync`, e.g. as the sync unit's
# ExecStartPre.
#
# Usage: hi-checkouts.sh <ephor project id>
set -uo pipefail

project=${1:?usage: hi-checkouts.sh <ephor project id>}
ephor feed --json --project "$project" 2>/dev/null |
  jq -r '.[] | select(.kind == "pr")
           | select((.raw.labels // []) | (index("human-intervention") != null)
                                          and (index("GenAI") != null)
                                          and (index("human-intervention-fixed") == null))
           | .id' |
  while IFS= read -r item; do
    [ -n "$item" ] || continue
    if ephor checkout --item "$item" --json >/dev/null 2>&1; then
      echo "workspace ready: $item"
    else
      echo "workspace not made: $item" >&2
    fi
  done
exit 0
