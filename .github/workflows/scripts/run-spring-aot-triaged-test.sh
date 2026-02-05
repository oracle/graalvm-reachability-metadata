#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

set -euo pipefail

if [ $# -ne 3 ]; then
  echo "Usage: $0 <spring_dir> <project> <branch>"
  echo "Example: $0 \"\${{ github.workspace }}/spring-aot-smoke-tests\" \":data:data-mongodb\" 3.5.x"
  exit 1
fi

SPRING_DIR="$1"
P="$2"
BRANCH="$3"

if [ ! -d "$SPRING_DIR" ]; then
  echo "::error:: Spring AOT path does not exist: $SPRING_DIR"
  exit 1
fi

echo "Running native tests for project '$P' in '$SPRING_DIR' (branch=$BRANCH)"
cd "$SPRING_DIR"

# Point spring-aot-smoke-tests to local reachability metadata
META_DIR="$(cd "$SPRING_DIR/.." && pwd)/metadata"
META_URL="file://${META_DIR}"
GP="${SPRING_DIR}/gradle.properties"

if [ ! -f "$GP" ]; then
  echo "gradle.properties not found at $GP"
  exit 1
fi

# Comment out version pin
sed -i -E 's|^reachabilityMetadataVersion=.*|#reachabilityMetadataVersion=|' "$GP"

# Set or add reachabilityMetadataUrl to local path
if grep -Eq '^[#]*reachabilityMetadataUrl=' "$GP"; then
  sed -i -E "s|^[#]*reachabilityMetadataUrl=.*|reachabilityMetadataUrl=${META_URL}|" "$GP"
else
  printf "\nreachabilityMetadataUrl=%s\n" "$META_URL" >> "$GP"
fi

echo "----- gradle.properties (reachability metadata overrides) -----"
grep -E 'reachabilityMetadata(Url|Version)=' -n "$GP" || true

set +e
./gradlew --no-daemon --continue "${P}:nativeTest" "${P}:nativeAppTest"
EXIT_CODE=$?
set -e

if [ $EXIT_CODE -eq 0 ]; then
  echo "✅ Test $P passed!"
  exit 0
fi

echo "❌ Test $P failed. Triaging..."

# 1. Check local build.gradle for 'expectedToFail'
REL_PATH=$(echo "${P//:/ /}" | xargs | tr ' ' '/')
GRADLE_FILE="${SPRING_DIR}/${REL_PATH}/build.gradle"

if [ -f "$GRADLE_FILE" ] && grep -q "expectedToFail" "$GRADLE_FILE"; then
  echo "::warning:: Known failure (expectedToFail found in $P/build.gradle). Skipping."
  exit 0
fi

# 2. Check Upstream CI via API using YAML filename
echo "🔍 Checking upstream CI for $P on branch $BRANCH..."

# Convert :data:data-mongodb to data-data-mongodb
CLEAN_PATH=$(echo "$P" | sed 's/^://' | sed 's/:/-/g')
WF_FILENAME="${BRANCH}-${CLEAN_PATH}.yml"

API_URL="https://api.github.com/repos/spring-projects/spring-aot-smoke-tests/actions/workflows/${WF_FILENAME}/runs?per_page=1"

AUTH_HEADER=()
if [ -n "${GITHUB_TOKEN:-}" ]; then
  AUTH_HEADER=(-H "Authorization: Bearer $GITHUB_TOKEN")
fi

# Fetch latest run data
RUN_DATA=$(curl -s "${AUTH_HEADER[@]}" "$API_URL")

# Check if workflow exists
if echo "$RUN_DATA" | jq -e '.message == "Not Found"' > /dev/null; then
  echo "::error:: Workflow $WF_FILENAME not found upstream. Failing build."
  exit 1
fi

LATEST_CONCLUSION=$(echo "$RUN_DATA" | jq -r '.workflow_runs[0].conclusion')
LATEST_URL=$(echo "$RUN_DATA" | jq -r '.workflow_runs[0].html_url')

if [ "$LATEST_CONCLUSION" == "failure" ]; then
  echo "::warning:: Pardon: Upstream is also failing at $LATEST_URL"
  exit 0
else
  echo "::error:: Test $P failed, but it is PASSING upstream ($LATEST_CONCLUSION). This is a new regression!"
  exit 1
fi
