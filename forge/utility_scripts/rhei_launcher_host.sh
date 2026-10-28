# Copyright and related rights waived via CC0
#
# You should have received a copy of the CC0 legalcode along with this
# work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.

# Host preflight shared by the standalone Rhei launchers; source it, then call
# require_rhei_launcher_host. §FS-forge-host-requirements

version_at_least() {
    local current="$1"
    local required="$2"
    local current_major current_minor current_patch
    local required_major required_minor required_patch

    IFS=. read -r current_major current_minor current_patch <<< "$current"
    IFS=. read -r required_major required_minor required_patch <<< "$required"
    (( 10#$current_major > 10#$required_major
        || (10#$current_major == 10#$required_major && 10#$current_minor > 10#$required_minor)
        || (10#$current_major == 10#$required_major && 10#$current_minor == 10#$required_minor
            && 10#$current_patch >= 10#$required_patch) ))
}

# Usage: require_rhei_launcher_host WORKFLOW FORGE_DIR REPO_ROOT FAMILY EXECUTABLE MODEL [PROVIDER]
#        [SETUP_FAMILY SETUP_EXECUTABLE SETUP_MODEL]
# FAMILY is a Forge agent backend (pi, claude-code); PROVIDER only applies to pi. The optional
# setup triple checks a second agent, the forge-metadata setup agent
# (§FS-forge-agent-runtime-selection).
# Exits the caller when gh, python3, or rhei is missing, gh is too old to read
# --json projectItems, or the coverage-mode host gate fails.
require_rhei_launcher_host() {
    local workflow="$1"
    local forge_dir="$2"
    local repo_root="$3"
    local family="$4"
    local executable="$5"
    local model="$6"
    local provider="${7:-}"
    local setup_family="${8:-}"
    local setup_executable="${9:-}"
    local setup_model="${10:-}"
    local provider_arguments=()
    local setup_arguments=()
    local min_gh_version="2.39.0"
    local gh_version_output gh_version

    if ! command -v gh >/dev/null 2>&1; then
        printf 'ERROR: gh is required and must be available on PATH.\n' >&2
        exit 1
    fi
    gh_version_output="$(gh version 2>&1)" || {
        printf 'ERROR: gh version failed: %s\n' "$gh_version_output" >&2
        exit 1
    }
    if [[ ! "$gh_version_output" =~ gh[[:space:]]version[[:space:]]([0-9]+[.][0-9]+[.][0-9]+) ]]; then
        printf 'ERROR: Cannot determine the installed gh version from: %s\n' "$gh_version_output" >&2
        exit 1
    fi
    gh_version="${BASH_REMATCH[1]}"
    if ! version_at_least "$gh_version" "$min_gh_version"; then
        printf 'ERROR: gh %s is too old; gh %s or newer is required because issue selection reads --json projectItems.\n' \
            "$gh_version" "$min_gh_version" >&2
        exit 1
    fi
    if ! command -v python3 >/dev/null 2>&1; then
        printf 'ERROR: python3 is required and must be available on PATH.\n' >&2
        exit 1
    fi
    if ! command -v rhei >/dev/null 2>&1; then
        printf 'ERROR: rhei is required and must be available on PATH.\n' >&2
        exit 1
    fi

    [[ -z "$provider" ]] || provider_arguments=(--analysis-provider "$provider")
    [[ -z "$setup_family" ]] || setup_arguments=(--setup-agent "$setup_executable"
        --setup-family "$setup_family" --setup-model "$setup_model")
    printf 'Validating %s host requirements.\n' "$workflow"
    python3 "$forge_dir/utility_scripts/host_requirements.py" \
        --forge-dir "$forge_dir" \
        --reachability-metadata-path "$repo_root" \
        --mode coverage \
        --analysis-agent "$executable" \
        --analysis-family "$family" \
        --analysis-model "$model" \
        "${provider_arguments[@]}" \
        "${setup_arguments[@]}"
}
