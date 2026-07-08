#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
VERSION_FILE="$PROJECT_ROOT/version.properties"
GITHUB_REPO="${LINK_ANDROID_GITHUB_REPO:-Maurix09/habitergy-link-android}"
STANDALONE_REPO="${LINK_ANDROID_REPO:-https://github.com/${GITHUB_REPO}.git}"
STANDALONE_DIR="${LINK_ANDROID_STANDALONE_DIR:-/tmp/habitergy-link-android}"

usage() {
    cat <<'EOF'
Usage: ./scripts/release.sh [patch|minor|X.Y.Z]

Bump version, commit, tag and push to GitHub.

  patch (default)  0.1.3 -> 0.1.4
  minor            0.1.12 -> 0.2.0
  X.Y.Z            set an explicit version (e.g. 0.2.0)

Version source priority:
  1. Latest git tag v* on the standalone repo
  2. version.properties in this project

Environment variables:
  LINK_ANDROID_STANDALONE_DIR  Path to standalone git clone (default: /tmp/habitergy-link-android)
  LINK_ANDROID_REPO            Standalone repo URL
  LINK_ANDROID_GITHUB_REPO     GitHub repo slug for gh CLI (default: Maurix09/habitergy-link-android)
EOF
}

log() {
    printf '==> %s\n' "$1"
}

die() {
    printf 'ERROR: %s\n' "$1" >&2
    exit 1
}

read_version_props() {
    [ -f "$VERSION_FILE" ] || die "Missing $VERSION_FILE"
    # shellcheck disable=SC1090
    source <(grep -E '^version(Name|Code)=' "$VERSION_FILE" | sed 's/^/export /')
    VERSION_NAME="${versionName:?}"
    VERSION_CODE="${versionCode:?}"
}

write_version_props() {
    local name="$1"
    local code="$2"
    cat >"$VERSION_FILE" <<EOF
versionName=${name}
versionCode=${code}
EOF
}

update_agents_md() {
    local version="$1"
    local file="$PROJECT_ROOT/AGENTS.md"
    [ -f "$file" ] || return 0
    sed -i "s/| \*\*Versión actual\*\* | \`[^\`]*\`/| **Versión actual** | \`${version}\`/" "$file"
}

parse_semver() {
    local raw="${1#v}"
    MAJOR="${raw%%.*}"
    local rest="${raw#*.}"
    MINOR="${rest%%.*}"
    PATCH="${rest#*.}"
    [ "$PATCH" = "$rest" ] && PATCH=0
    [[ "$MAJOR" =~ ^[0-9]+$ ]] || die "Invalid version: $1"
    [[ "$MINOR" =~ ^[0-9]+$ ]] || die "Invalid version: $1"
    [[ "$PATCH" =~ ^[0-9]+$ ]] || die "Invalid version: $1"
}

bump_patch() {
    PATCH=$((PATCH + 1))
    printf '%s.%s.%s' "$MAJOR" "$MINOR" "$PATCH"
}

bump_minor() {
    MINOR=$((MINOR + 1))
    PATCH=0
    printf '%s.%s.%s' "$MAJOR" "$MINOR" "$PATCH"
}

get_latest_tag_version() {
    local git_root="$1"
    git -C "$git_root" fetch --tags origin >/dev/null 2>&1 || true
    local latest
    latest="$(git -C "$git_root" tag -l 'v*' --sort=-v:refname | head -n 1 || true)"
    if [ -n "$latest" ]; then
        printf '%s' "${latest#v}"
        return 0
    fi
    read_version_props
    printf '%s' "$VERSION_NAME"
}

compute_next_version() {
    local bump_type="$1"
    local base_version="$2"

    if [[ "$bump_type" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
        printf '%s' "$bump_type"
        return 0
    fi

    parse_semver "$base_version"
    case "$bump_type" in
        patch) bump_patch ;;
        minor) bump_minor ;;
        *) die "Unknown bump type: $bump_type" ;;
    esac
}

ensure_standalone_clone() {
    if [ -d "$STANDALONE_DIR/.git" ]; then
        git -C "$STANDALONE_DIR" fetch origin >/dev/null 2>&1 || true
        return 0
    fi

    log "Cloning standalone repo into $STANDALONE_DIR"
    git clone "$STANDALONE_REPO" "$STANDALONE_DIR"
}

sync_to_standalone() {
    log "Syncing project files to standalone repo"
    rsync -a --delete \
        --exclude '.git/' \
        --exclude 'build/' \
        --exclude '.gradle/' \
        --exclude 'local.properties' \
        --exclude '.idea/' \
        --exclude '*.iml' \
        "$PROJECT_ROOT/" "$STANDALONE_DIR/"
}

commit_tag_and_push() {
    local git_root="$1"
    local version="$2"
    local tag="v${version}"

    if [ -z "$(git -C "$git_root" status --porcelain)" ]; then
        die "No changes to release in $git_root"
    fi

    git -C "$git_root" add -A
    git -C "$git_root" commit -m "$(cat <<EOF
release: ${tag}

Bump versionName to ${version} and versionCode for Habitergy Link Android.
EOF
)"

    if git -C "$git_root" rev-parse "$tag" >/dev/null 2>&1; then
        die "Tag ${tag} already exists"
    fi

    git -C "$git_root" tag -a "$tag" -m "Habitergy Link ${tag}"
    git -C "$git_root" push origin main
    git -C "$git_root" push origin "$tag"

    if command -v gh >/dev/null 2>&1; then
        if gh auth status >/dev/null 2>&1; then
            gh release create "$tag" \
                --repo "$GITHUB_REPO" \
                --title "$tag" \
                --notes "Habitergy Link ${tag}" >/dev/null 2>&1 \
                || log "GitHub Release ${tag} may already exist or could not be created"
        else
            log "gh not authenticated; skipped GitHub Release creation"
        fi
    fi
}

main() {
    local bump_type="${1:-patch}"

    case "$bump_type" in
        -h|--help|help) usage; exit 0 ;;
    esac

    case "$bump_type" in
        patch|minor) ;;
        [0-9]*.[0-9]*.[0-9]*) ;;
        *) die "Invalid argument: $bump_type. Run ./scripts/release.sh --help" ;;
    esac

    ensure_standalone_clone

    local base_version
    base_version="$(get_latest_tag_version "$STANDALONE_DIR")"
    local new_version
    new_version="$(compute_next_version "$bump_type" "$base_version")"

    local new_code
    new_code="$(($(grep '^versionCode=' "$STANDALONE_DIR/version.properties" | cut -d= -f2) + 1))"

    log "Base version: ${base_version}"
    log "New version:  ${new_version} (versionCode -> ${new_code})"
    log "Bump type:    ${bump_type}"

    write_version_props "$new_version" "$new_code"
    update_agents_md "$new_version"
    sync_to_standalone
    commit_tag_and_push "$STANDALONE_DIR" "$new_version"

    log "Released ${new_version} successfully"
    log "Repo: https://github.com/${GITHUB_REPO}"
    log "Tag:  v${new_version}"
}

main "$@"
