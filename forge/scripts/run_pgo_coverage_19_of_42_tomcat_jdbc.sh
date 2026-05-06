#!/usr/bin/env bash
set -u -o pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
FORGE_DIR="$(cd -- "${SCRIPT_DIR}/.." && pwd)"
SCRIPT_NAME="$(basename -- "${BASH_SOURCE[0]}" .sh)"
export METRICS_REPO_PATH="${METRICS_REPO_PATH:-${FORGE_DIR}/output/${SCRIPT_NAME}}"

exec "${SCRIPT_DIR}/run_pgo_coverage_library.sh" "org.apache.tomcat:tomcat-jdbc:10.1.14"
