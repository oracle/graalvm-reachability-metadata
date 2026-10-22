#!/usr/bin/env bash
# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
#
# Provision the Forge user inside a VM launched from forge/incus/forge-vm.yaml
# (§FS-forge-isolated-host). Runs as the unprivileged `forge` user after
# cloud-init has installed the root-level tools; it needs no sudo and is safe
# to re-run. It installs what the host gate (§FS-forge-host-requirements)
# checks for an issue run: the repository clone, the three GraalVM lanes
# resolved from the same sources the gate reads, the agent CLIs, and the Forge
# Python environment. Credentials are not handled here.

set -euo pipefail

FORGE_REPO_URL="${FORGE_REPO_URL:-https://github.com/oracle/graalvm-reachability-metadata.git}"
FORGE_REPO_BRANCH="${FORGE_REPO_BRANCH:-master}"
FORGE_REPO_DIR="${FORGE_REPO_DIR:-$HOME/graalvm-reachability-metadata}"
GRAALVM_ROOT="${GRAALVM_ROOT:-$HOME/graalvm}"
# npm packages that provide the `pi` and `codex` backends; set to "" to skip.
FORGE_AGENT_PACKAGES="${FORGE_AGENT_PACKAGES-@earendil-works/pi-coding-agent @openai/codex}"
ENV_FILE="$HOME/.forge-vm.env"

GA_RELEASE_URL="https://api.github.com/repos/graalvm/graalvm-ce-builds/releases/latest"
EA_MANIFEST_URL="https://raw.githubusercontent.com/graalvm/oracle-graalvm-ea-builds/main/versions/latest-ea.json"
ORACLE_ARCHIVE_URL="https://download.oracle.com/graalvm"

log() { printf '[forge-vm] %s\n' "$*"; }
fail() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

require_root_tools() {
    [[ -f /var/lib/cloud/forge-root-tools-ready ]] \
        || fail "cloud-init has not finished; run \`cloud-init status --wait\` and check \`cloud-init status --long\`."
    local tool
    for tool in git curl python3 gh docker node npm grype; do
        command -v "$tool" >/dev/null 2>&1 || fail "\`$tool\` is missing although cloud-init finished; inspect /var/log/cloud-init-output.log."
    done
    docker info >/dev/null 2>&1 || fail "the Docker daemon is not reachable as $(id -un); log out and in so the docker group applies."
}

graalvm_arch() {
    case "$(uname -m)" in
        x86_64) echo x64 ;;
        aarch64) echo aarch64 ;;
        *) fail "unsupported architecture $(uname -m)" ;;
    esac
}

json_query() { # stdin: JSON; $1: Python expression over `data`
    python3 -c 'import json, sys; data = json.load(sys.stdin); print(eval(sys.argv[1]))' "$1"
}

install_graalvm() { # $1: lane directory, $2: archive URL
    local dest="$1" url="$2" archive
    if [[ -x "$dest/bin/native-image" ]]; then
        log "$dest is already installed"
        return
    fi
    archive="$(mktemp --suffix=.tar.gz)"
    log "Downloading $url"
    curl -fsSL --retry 3 -o "$archive" "$url"
    rm -rf "$dest"
    mkdir -p "$dest"
    tar -xzf "$archive" -C "$dest" --strip-components=1
    rm -f "$archive"
    [[ -x "$dest/bin/native-image" ]] || fail "$url did not unpack to a GraalVM with bin/native-image"
}

clone_repository() {
    if [[ -d "$FORGE_REPO_DIR/.git" ]]; then
        log "$FORGE_REPO_DIR already cloned"
        return
    fi
    log "Cloning $FORGE_REPO_URL ($FORGE_REPO_BRANCH) into $FORGE_REPO_DIR"
    git clone --branch "$FORGE_REPO_BRANCH" "$FORGE_REPO_URL" "$FORGE_REPO_DIR"
}

install_graalvm_lanes() {
    local arch ga_json ga_version ga_url pinned major ea_json ea_version ea_url
    arch="$(graalvm_arch)"
    mkdir -p "$GRAALVM_ROOT"

    ga_json="$(curl -fsSL "$GA_RELEASE_URL")"
    ga_version="$(json_query "__import__('re').fullmatch(r'(?:graal-|jdk-)(\\d+(?:\\.\\d+)+)', data['tag_name']).group(1)" <<<"$ga_json")"
    ga_url="$(json_query "[a['browser_download_url'] for a in data['assets'] if a['name'].startswith('graalvm-community-jdk-') and a['name'].endswith('_linux-${arch}_bin.tar.gz')][0]" <<<"$ga_json")"
    GA_HOME="$GRAALVM_ROOT/ga-$ga_version"
    install_graalvm "$GA_HOME" "$ga_url"

    pinned="$(json_query "data['GRAALVM_HOME_25_0']" <"$FORGE_REPO_DIR/forge/graalvm-versions.json")"
    major="${pinned%%.*}"
    PINNED_HOME="$GRAALVM_ROOT/25.0-$pinned"
    install_graalvm "$PINNED_HOME" "$ORACLE_ARCHIVE_URL/$major/archive/graalvm-jdk-${pinned}_linux-${arch}_bin.tar.gz"

    ea_json="$(curl -fsSL "$EA_MANIFEST_URL")"
    ea_version="$(json_query "data['version']" <<<"$ea_json")"
    ea_url="$(json_query "data['download_base_url'] + [f['filename'] for f in data['files'] if f['platform'] == 'linux' and f['arch'] == '${arch}'][0]" <<<"$ea_json")"
    EA_HOME="$GRAALVM_ROOT/ea-$ea_version"
    install_graalvm "$EA_HOME" "$ea_url"
}

install_agent_clis() {
    if [[ -z "$FORGE_AGENT_PACKAGES" ]]; then
        log "FORGE_AGENT_PACKAGES is empty; skipping agent CLIs"
        return
    fi
    npm config set prefix "$HOME/.npm-global"
    # shellcheck disable=SC2086 # intentional word splitting of the package list
    npm install -g $FORGE_AGENT_PACKAGES
}

install_forge_environment() {
    local forge_dir="$FORGE_REPO_DIR/forge"
    [[ -x "$forge_dir/.venv/bin/pip" ]] || python3 -m venv "$forge_dir/.venv"
    "$forge_dir/.venv/bin/pip" install --quiet -e "$forge_dir"
}

write_environment() {
    cat >"$ENV_FILE" <<ENV
# Written by forge/incus/provision.sh; sourced from ~/.profile.
export GRAALVM_HOME="$GA_HOME"
export GRAALVM_HOME_25_0="$PINNED_HOME"
export GRAALVM_HOME_LATEST_EA="$EA_HOME"
export JAVA_HOME="\$GRAALVM_HOME"
export PATH="\$HOME/.npm-global/bin:\$JAVA_HOME/bin:\$PATH"
export PYTHON_BIN="$FORGE_REPO_DIR/forge/.venv/bin/python"
ENV
    grep -qF "$ENV_FILE" "$HOME/.profile" 2>/dev/null \
        || printf '\n[ -f "%s" ] && . "%s"\n' "$ENV_FILE" "$ENV_FILE" >>"$HOME/.profile"
}

report() {
    log "GraalVM lanes:"
    local lane
    for lane in "$GA_HOME" "$PINNED_HOME" "$EA_HOME"; do
        printf '  %s: %s\n' "$lane" "$("$lane/bin/native-image" --version | head -n 1)"
    done
    log "Provisioning finished. Copy credentials in, then validate the host from a login shell:"
    printf '  cd %s/forge && "$PYTHON_BIN" utility_scripts/host_requirements.py --forge-dir . --verbose\n' "$FORGE_REPO_DIR"
}

require_root_tools
clone_repository
install_graalvm_lanes
install_agent_clis
install_forge_environment
write_environment
report
