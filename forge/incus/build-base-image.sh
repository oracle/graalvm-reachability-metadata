#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
# Build the Forge base image used by `forge_metadata.py --incus`.
# §AR-forge-vm-runner-boundary §FS-forge-vm-isolated-execution
#
# This is the reproducible definition of the base image's contents. It runs on
# the HOST and drives Incus to:
#   1. launch a throwaway builder VM from a stock Debian image,
#   2. install Docker, copy the LOCAL GraalVM into the VM, bake the per-user VM
#      environment (incus/forge.env) into /etc/environment, clone the
#      reachability repo, and warm the Gradle caches,
#   3. publish the prepared disk as the reusable base image alias (forge-base),
#   4. delete the builder VM.
#
# GraalVM is not downloaded here: the VM uses the same GraalVM installation as
# your local machine, copied from the path you set in incus/forge.env
# (GRAALVM_HOME / JAVA_HOME), so generation inside the VM matches local. Keep
# that local install at GraalVM 25 (latest).
#
# The base image is built locally on each host and is never shipped: it is large
# and tooling-versioned, so the repository ships this script, the profile
# (forge.profile.yaml), and the environment template (forge.env.example), not
# the image bytes. Re-run this script to refresh the image when GraalVM, the
# checkout, the caches, or forge.env change.
#
# Per-run, secret, and run-specific state (the GitHub token, the issue number,
# the strategy, the log destination) is NOT baked in here; the runner injects it
# into each fresh VM at launch.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Per-user VM environment (GraalVM paths + any generation variables). Copied
# from forge.env.example; see that file for the format.
FORGE_ENV_FILE="${FORGE_INCUS_ENV_FILE:-$SCRIPT_DIR/forge.env}"

# Everything is overridable so the same script serves different machines.
IMAGE_ALIAS="${FORGE_INCUS_IMAGE:-forge-base}"
PROFILE="${FORGE_INCUS_PROFILE:-forge}"
BUILDER_VM="${FORGE_INCUS_BUILDER_VM:-forge-build}"
SOURCE_IMAGE="${FORGE_INCUS_SOURCE_IMAGE:-images:debian/12}"
REPO_URL="${FORGE_INCUS_REPO_URL:-https://github.com/oracle/graalvm-reachability-metadata.git}"
REPO_PATH="${FORGE_INCUS_REPO_PATH:-/root/graalvm-reachability-metadata}"
AGENT_TIMEOUT="${FORGE_INCUS_LAUNCH_TIMEOUT:-300}"

log() { printf '[build-base-image] %s\n' "$*"; }

# GraalVM homes the build copies into the VM and generation requires inside it.
# All three are preflighted by forge_metadata.validate_issue_processing_environment
# before any issue is processed, so each must resolve to a GraalVM with
# bin/native-image inside the VM:
#   GRAALVM_HOME          - primary GraalVM used for generation
#   GRAALVM_HOME_25_0     - GraalVM 25 post-generation validation lane
#   GRAALVM_HOME_LATEST_EA - latest early-access validation lane
GRAALVM_VARS=(GRAALVM_HOME GRAALVM_HOME_25_0 GRAALVM_HOME_LATEST_EA)

validate_graalvm() {
    local var="$1" home="${!1:-}"
    if [[ ! -f "$home/bin/native-image" ]]; then
        echo "ERROR: $var=$home does not look like a GraalVM install ($home/bin/native-image is missing)." >&2
        exit 1
    fi
}

load_forge_env() {
    if [[ ! -f "$FORGE_ENV_FILE" ]]; then
        echo "ERROR: $FORGE_ENV_FILE not found. Copy forge.env.example to forge.env and set the GraalVM homes for your machine; see forge/README.md." >&2
        exit 1
    fi
    # Export every assignment so the GraalVM homes are visible to the steps below.
    set -a
    # shellcheck disable=SC1090
    source "$FORGE_ENV_FILE"
    set +a
    local var home
    for var in "${GRAALVM_VARS[@]}"; do
        home="${!var:-}"
        if [[ -z "$home" ]]; then
            echo "ERROR: $var is not set in $FORGE_ENV_FILE. All of ${GRAALVM_VARS[*]} are required by Forge's pre-processing preflight; set it to a local GraalVM install with bin/native-image. See forge/README.md." >&2
            exit 1
        fi
        # Strip trailing slashes so `incus file push -r` copies the directory
        # itself, not just its contents.
        while [[ "$home" == */ && "$home" != "/" ]]; do home="${home%/}"; done
        printf -v "$var" '%s' "$home"
        validate_graalvm "$var"
    done
}

require_incus() {
    if ! command -v incus >/dev/null 2>&1; then
        echo "ERROR: 'incus' not found on PATH. Install and initialize Incus first; see forge/README.md." >&2
        exit 1
    fi
    if ! incus info >/dev/null 2>&1; then
        echo "ERROR: Incus daemon not reachable. Run 'incus admin init --minimal' and add your user to 'incus-admin'." >&2
        exit 1
    fi
}

ensure_profile() {
    if ! incus profile show "$PROFILE" >/dev/null 2>&1; then
        log "Creating Incus profile '$PROFILE' from forge.profile.yaml"
        incus profile create "$PROFILE"
        incus profile edit "$PROFILE" < "$SCRIPT_DIR/forge.profile.yaml"
    fi
}

remove_builder() {
    if incus info "$BUILDER_VM" >/dev/null 2>&1; then
        log "Removing builder VM '$BUILDER_VM'"
        incus delete "$BUILDER_VM" --force
    fi
}

wait_for_agent() {
    local deadline=$(( $(date +%s) + AGENT_TIMEOUT ))
    log "Waiting for the builder VM guest agent to accept commands..."
    until incus exec "$BUILDER_VM" -- true >/dev/null 2>&1; do
        if (( $(date +%s) >= deadline )); then
            echo "ERROR: builder VM '$BUILDER_VM' did not become ready within ${AGENT_TIMEOUT}s." >&2
            exit 1
        fi
        sleep 2
    done
}

# Copy each configured local GraalVM into the builder at the same path it has on
# the host, so every GraalVM home generation resolves (GRAALVM_HOME,
# GRAALVM_HOME_25_0, GRAALVM_HOME_LATEST_EA) matches local. Paths shared across
# vars (e.g. when GRAALVM_HOME and GRAALVM_HOME_25_0 use the same JDK 25) are
# copied once.
copy_local_graalvm() {
    local copied=() var home parent
    for var in "${GRAALVM_VARS[@]}"; do
        home="${!var:-}"
        if [[ " ${copied[*]-} " == *" $home "* ]]; then
            log "GraalVM for $var already copied ('$home'); skipping"
            continue
        fi
        copied+=("$home")
        parent="$(dirname "$home")"
        log "Copying local GraalVM for $var from '$home' into the builder VM"
        incus exec "$BUILDER_VM" -- mkdir -p "$parent"
        incus file push -r "$home" "$BUILDER_VM$parent/"
    done
}

# Bake the per-user environment into the VM's /etc/environment. `incus exec`
# uses non-login shells that ignore ~/.profile, so /etc/environment is how every
# later run sees GRAALVM_HOME, JAVA_HOME, PATH, and any extra generation vars.
write_vm_environment() {
    local env_tmp
    env_tmp="$(mktemp)"
    # forge.env assignments, minus comments, blank lines, and any leading export.
    grep -vE '^[[:space:]]*(#|$)' "$FORGE_ENV_FILE" \
        | sed -E 's/^[[:space:]]*export[[:space:]]+//' > "$env_tmp"
    # Ensure GraalVM bin is on PATH unless forge.env already sets PATH itself.
    if ! grep -q '^PATH=' "$env_tmp"; then
        echo "PATH=$GRAALVM_HOME/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin" >> "$env_tmp"
    fi
    log "Baking forge.env into the builder VM's /etc/environment"
    incus file push "$env_tmp" "$BUILDER_VM/etc/environment"
    rm -f "$env_tmp"
}

load_forge_env
require_incus
ensure_profile
remove_builder   # start clean if a previous build left a builder behind

log "Launching builder VM '$BUILDER_VM' from '$SOURCE_IMAGE' with profile '$PROFILE'"
incus launch "$SOURCE_IMAGE" "$BUILDER_VM" --vm --profile "$PROFILE"
# Tear the builder down on any failure from here on.
trap remove_builder EXIT
wait_for_agent

copy_local_graalvm
write_vm_environment

log "Provisioning the builder VM (Docker, repo checkout, warmed Gradle caches)"
incus exec "$BUILDER_VM" \
    --env GRAALVM_HOME="$GRAALVM_HOME" \
    --env JAVA_HOME="$GRAALVM_HOME" \
    --env REPO_URL="$REPO_URL" \
    --env REPO_PATH="$REPO_PATH" \
    -- bash -seuo pipefail <<'PROVISION'
export DEBIAN_FRONTEND=noninteractive

# 1. Base tooling + Docker. Test resources run as Docker containers INSIDE this
#    VM, so the VM needs a working Docker engine.
apt-get update
apt-get install -y --no-install-recommends ca-certificates curl git docker.io
systemctl enable --now docker

# 2. Reachability checkout + warmed Gradle caches: the expensive state every run
#    reuses. GraalVM is already in place (copied from the host) and exposed via
#    /etc/environment. Each run later refreshes this checkout to current master.
git clone "$REPO_URL" "$REPO_PATH"
cd "$REPO_PATH"
PATH="$GRAALVM_HOME/bin:$PATH" GRAALVM_HOME="$GRAALVM_HOME" JAVA_HOME="$GRAALVM_HOME" ./gradlew --no-daemon help
PROVISION

log "Stopping the builder VM before publishing"
incus stop "$BUILDER_VM"

log "Publishing the prepared disk as base image alias '$IMAGE_ALIAS'"
if incus image info "$IMAGE_ALIAS" >/dev/null 2>&1; then
    log "Replacing existing image '$IMAGE_ALIAS'"
    incus image delete "$IMAGE_ALIAS"
fi
incus publish "$BUILDER_VM" --alias "$IMAGE_ALIAS"

remove_builder
trap - EXIT

log "Done. Base image '$IMAGE_ALIAS' is in the local image store:"
incus image list "$IMAGE_ALIAS"
