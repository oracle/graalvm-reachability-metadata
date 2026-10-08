#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# The drain queue's first state. §FS-maintainer-agent-queues.3

# Decides, before any model is spent, whether a human-intervention pull request
# has anything to drain, and collects what failed when it does.
#
# In:  REPO, NUMBER, REPORT from the state machine; RHEI_RESULT_PATH from rhei.
# Exit: 0 failures are in $REPORT for hi-fix,
#       4 nothing to drain (closed, not GenAI, label gone or fixed, gate green, or
#         this head already escalated); the ticket ends without a model,
#       75 checks still running, 1 GitHub could not be read.
set -uo pipefail

: "${REPO:?the state machine must pass {meta.repo}}"
: "${NUMBER:?the state machine must pass {meta.number}}"
: "${REPORT:?the state machine must pass {output.<name>.path}}"
here=$(cd "$(dirname "$0")" && pwd)
mkdir -p "$(dirname "$REPORT")"

pr=$(gh pr view "$NUMBER" -R "$REPO" --json \
  state,title,url,labels,headRefName,headRefOid,closingIssuesReferences,comments 2>&1) \
  || { printf '# %s#%s could not be read\n\n```\n%s\n```\n' "$REPO" "$NUMBER" "$pr" > "$REPORT"; exit 1; }

state=$(jq -r .state <<<"$pr")
head=$(jq -r .headRefOid <<<"$pr")
labels=$(jq -r '[.labels[].name] | join(" ")' <<<"$pr")
has() { case " $labels " in *" $1 "*) return 0 ;; *) return 1 ;; esac; }

# Exit 4 routes straight to the final `done` state, and rhei requires a ticket
# that finishes to leave its result, so the report is written there too.
idle() {
  { echo "# $REPO#$NUMBER: nothing to drain"; echo; echo "- state: $state"; echo "- labels: $labels"
    echo "- head: $head"; echo; echo "$1"; } > "$REPORT"
  if [ -n "${RHEI_RESULT_PATH:-}" ]; then
    mkdir -p "$(dirname "$RHEI_RESULT_PATH")" && cp "$REPORT" "$RHEI_RESULT_PATH"
  fi
  exit 4
}

if [ "$state" != "OPEN" ] || ! has GenAI || ! has human-intervention || has human-intervention-fixed; then
  idle "The drain handles open GenAI pull requests still carrying \`human-intervention\`."
fi

# An escalation is per head: hi-fix marks its comment with the head SHA, so its
# own comment moving the pull request does not buy another paid round.
if jq -e --arg m "<!-- ephor-hi-drain:escalated:$head -->" 'any(.comments[]?; .body | contains($m))' <<<"$pr" >/dev/null; then
  idle "This head was already escalated by an earlier drain round; a new push re-opens it."
fi

answer=$(mktemp)
trap 'rm -f "$answer"' EXIT
failures=$(EPHOR_ANSWER="$answer" REPO="$REPO" NUMBER="$NUMBER" "$here/ci-failures.sh")
code=$?
[ "$code" -eq 75 ] && exit 75
[ "$code" -ne 0 ] && { echo "$failures" > "$REPORT"; exit 1; }

if [ "$(jq '.failures | length' "$answer")" -eq 0 ]; then
  idle "The gate is green: the label was applied for a reason that is not a failing check, so it is a maintainer's call."
fi

{
  echo "# $REPO#$NUMBER: $(jq -r .title <<<"$pr")"
  echo
  echo "- url: $(jq -r .url <<<"$pr")"
  echo "- head: $(jq -r .headRefName <<<"$pr") @ $head"
  echo "- labels: $labels"
  echo "- linked issues: $(jq -r '[.closingIssuesReferences[]? | "#\(.number)"] | join(", ")' <<<"$pr")"
  echo
  echo "$failures"
} > "$REPORT"
exit 0
