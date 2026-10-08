#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# ephor's `failures` gate verb for this repository. §FS-maintainer-agent-queues.1

# What failed on a pull request's GitHub Actions gate, read with `gh`, because
# the GitHub forge ephor watches reports counts but not jobs.
#
# In:  EPHOR_REPO and EPHOR_NUMBER (or REPO and NUMBER when another script calls it).
# Out: a Markdown report on stdout, and the answer envelope at $EPHOR_ANSWER
#      when ephor asks for one.
# Exit: 0 answered (an empty failure list means nothing failed),
#       75 checks are still running and none has failed yet, 1 GitHub could not be read.
set -uo pipefail

repo=${EPHOR_REPO:-${REPO:-}}
number=${EPHOR_NUMBER:-${NUMBER:-}}
[ -n "$repo" ] || repo=$(gh repo view --json nameWithOwner --jq .nameWithOwner 2>/dev/null)
if [ -z "$repo" ] || [ -z "$number" ]; then
  echo "ci-failures: no repository or pull request number was given" >&2
  exit 1
fi

pr=$(gh pr view "$number" -R "$repo" --json headRefOid,statusCheckRollup 2>&1) || {
  echo "ci-failures: $repo#$number could not be read: $pr" >&2
  exit 1
}

checks=$(jq -c '[.statusCheckRollup[]? | {
  name: (.name // .context // "?"),
  status: (.status // ""),
  conclusion: (.conclusion // .state // ""),
  url: (.detailsUrl // .targetUrl // "")
}]' <<<"$pr")
pending=$(jq '[.[] | select(.status != "" and .status != "COMPLETED")] | length' <<<"$checks")
failed=$(jq -c '[.[] | select(.conclusion | IN("FAILURE","CANCELLED","TIMED_OUT","ACTION_REQUIRED","ERROR"))]' <<<"$checks")
nfailed=$(jq length <<<"$failed")

if [ "$nfailed" -eq 0 ] && [ "$pending" -gt 0 ]; then
  echo "$repo#$number: $pending check(s) still running, nothing failed yet" >&2
  exit 75
fi

if [ -n "${EPHOR_ANSWER:-}" ]; then
  jq -n --argjson f "$failed" --arg s "$nfailed failing check(s)" \
    '{v: 1, summary: $s, failures: [$f[] | {job: .name, url: .url}]}' > "$EPHOR_ANSWER"
fi

echo "## Failing checks on $repo#$number @ $(jq -r .headRefOid <<<"$pr")"
echo
echo "- still running: $pending"
echo
if [ "$nfailed" -eq 0 ]; then
  echo "Nothing failed."
  exit 0
fi
jq -r '.[] | "- \(.name) — \(.conclusion) — \(.url)"' <<<"$failed"
echo

# The tail of each failing job's log is where Gradle and JUnit put the verdict.
# Job ids come from the check URL (…/actions/runs/<run>/job/<job>).
while IFS= read -r job; do
  [ -n "$job" ] || continue
  name=$(jq -r --arg j "$job" '.[] | select(.url | endswith("/job/" + $j)) | .name' <<<"$failed" | head -1)
  echo "### Job $job — $name"
  echo
  echo '```'
  gh run view --job "$job" -R "$repo" --log-failed 2>&1 \
    | sed -E 's/^[^\t]*\t[^\t]*\t//; s/^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9:.]+Z //' \
    | grep -vE '^\s*$' | tail -n 150
  echo '```'
  echo
done < <(jq -r '.[].url | capture("/job/(?<id>[0-9]+)$").id' <<<"$failed" | sort -u)
exit 0
