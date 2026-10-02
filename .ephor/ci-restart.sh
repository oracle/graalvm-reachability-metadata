#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# ephor's `restart` gate verb for this repository. §FS-maintainer-agent-queues.1

# Re-runs a pull request's GitHub Actions runs on its current head and commits
# nothing. The drain queue re-runs transient failures with it.
#
# In:  EPHOR_REPO, EPHOR_NUMBER, and EPHOR_RESTART — `failed` (the default) re-runs
#      only the failed jobs of failed runs, `all` re-runs every completed run.
# Exit: 0 re-runs were requested, 75 the runs are still going so nothing can be
#       re-run yet, 1 GitHub could not be read or refused a re-run.
set -uo pipefail

repo=${EPHOR_REPO:-${REPO:-}}
number=${EPHOR_NUMBER:-${NUMBER:-}}
scope=${EPHOR_RESTART:-failed}
[ -n "$repo" ] || repo=$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null)
if [ -z "$repo" ] || [ -z "$number" ]; then
  echo "ci-restart: no repository or pull request number was given" >&2
  exit 1
fi

head=$(gh pr view "$number" -R "$repo" --json headRefOid --jq .headRefOid 2>&1) || {
  echo "ci-restart: $repo#$number could not be read: $head" >&2
  exit 1
}
runs=$(gh run list -R "$repo" -c "$head" --limit 100 --json databaseId,workflowName,status,conclusion 2>&1) || {
  echo "ci-restart: the runs on $head could not be listed: $runs" >&2
  exit 1
}

case "$scope" in
  all)    pick='.[] | select(.status == "completed")' ;;
  failed) pick='.[] | select(.status == "completed" and (.conclusion | IN("failure","cancelled","timed_out","action_required")))' ;;
  *)      echo "ci-restart: unknown scope '$scope' (expected failed or all)" >&2; exit 1 ;;
esac
targets=$(jq -r "$pick | \"\(.databaseId) \(.workflowName)\"" <<<"$runs")

if [ -z "$targets" ]; then
  if jq -e 'any(.[]; .status != "completed")' <<<"$runs" >/dev/null; then
    echo "$repo#$number: runs on $head are still going; nothing to re-run yet" >&2
    exit 75
  fi
  echo "$repo#$number: nothing on $head matches scope '$scope'; nothing was re-run"
  exit 0
fi

refused=0
while read -r id name; do
  [ -n "$id" ] || continue
  if [ "$scope" = "failed" ]; then
    out=$(gh run rerun "$id" --failed -R "$repo" 2>&1)
  else
    out=$(gh run rerun "$id" -R "$repo" 2>&1)
  fi
  if [ $? -eq 0 ]; then
    echo "re-run requested: $name ($id)"
  else
    echo "re-run refused: $name ($id): $out" >&2
    refused=$((refused + 1))
  fi
done <<<"$targets"
[ "$refused" -eq 0 ]
