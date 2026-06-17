#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

PORTICO_REPO_URL="${PORTICO_REPO_URL:-https://github.com/openlvc/portico.git}"
PORTICO_REF="${PORTICO_REF:-master}"
PORTICO_VERSION="${PORTICO_VERSION:-3.0.0-local}"
PORTICO_SRC="${PORTICO_SRC:-}"

DEST_DIR="${ROOT_DIR}/lib/maven-repository/org/porticoproject/portico/${PORTICO_VERSION}"
ARTIFACT_ID="portico"
GROUP_ID="org.porticoproject"

usage() {
    cat <<EOF
Usage: make vendor-portico [PORTICO_REF=<git-ref>] [PORTICO_VERSION=<version>]

Rebuild and vendor Portico into this repo's Maven file repository.

Environment:
  PORTICO_REPO_URL  Git URL to clone. Default: ${PORTICO_REPO_URL}
  PORTICO_REF       Git branch, tag, or SHA to build. Default: ${PORTICO_REF}
  PORTICO_VERSION   Maven version written under lib/maven-repository. Default: ${PORTICO_VERSION}
  PORTICO_SRC       Optional local Portico checkout to clone from instead of PORTICO_REPO_URL.

Examples:
  make vendor-portico
  make vendor-portico PORTICO_REF=v3.0.0 PORTICO_VERSION=3.0.0-local
  make vendor-portico PORTICO_SRC=/tmp/portico PORTICO_REF=my-branch
EOF
}

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
    usage
    exit 0
fi

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Required command not found: $1" >&2
        exit 1
    fi
}

checksum() {
    local algorithm="$1"
    local file="$2"

    case "${algorithm}" in
        md5)
            if command -v md5sum >/dev/null 2>&1; then
                md5sum "${file}" | awk '{print $1}'
            else
                md5 -q "${file}"
            fi
            ;;
        sha1)
            if command -v sha1sum >/dev/null 2>&1; then
                sha1sum "${file}" | awk '{print $1}'
            else
                shasum -a 1 "${file}" | awk '{print $1}'
            fi
            ;;
        sha256)
            if command -v sha256sum >/dev/null 2>&1; then
                sha256sum "${file}" | awk '{print $1}'
            else
                shasum -a 256 "${file}" | awk '{print $1}'
            fi
            ;;
        *)
            echo "Unknown checksum algorithm: ${algorithm}" >&2
            exit 1
            ;;
    esac
}

write_checksum() {
    local algorithm="$1"
    local extension="$2"
    local file="$3"

    checksum "${algorithm}" "${file}" > "${file}.${extension}"
}

require_command git
require_command awk
require_command find

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/portico-vendor.XXXXXX")"
cleanup() {
    rm -rf "${WORK_DIR}"
}
trap cleanup EXIT

PORTICO_CHECKOUT="${WORK_DIR}/portico"

if [[ -n "${PORTICO_SRC}" ]]; then
    echo "Cloning Portico from local checkout ${PORTICO_SRC}"
    git clone "${PORTICO_SRC}" "${PORTICO_CHECKOUT}"
else
    echo "Cloning Portico from ${PORTICO_REPO_URL}"
    git clone "${PORTICO_REPO_URL}" "${PORTICO_CHECKOUT}"
fi

cd "${PORTICO_CHECKOUT}"
git checkout "${PORTICO_REF}"

if [[ ! -x codebase/ant ]]; then
    echo "Expected Portico build script not found: codebase/ant" >&2
    exit 1
fi

echo "Building Portico ref ${PORTICO_REF}"
cd codebase
./ant java.jar.portico

PORTICO_JAR="$(find dist -path "*/lib/portico.jar" -type f | sort | tail -n 1)"
if [[ -z "${PORTICO_JAR}" ]]; then
    echo "Portico build did not produce dist/*/lib/portico.jar" >&2
    exit 1
fi

echo "Vendoring ${PORTICO_JAR} as ${GROUP_ID}:${ARTIFACT_ID}:${PORTICO_VERSION}"
mkdir -p "${DEST_DIR}"
cp "${PORTICO_JAR}" "${DEST_DIR}/${ARTIFACT_ID}-${PORTICO_VERSION}.jar"

cat > "${DEST_DIR}/${ARTIFACT_ID}-${PORTICO_VERSION}.pom" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>${GROUP_ID}</groupId>
    <artifactId>${ARTIFACT_ID}</artifactId>
    <version>${PORTICO_VERSION}</version>
</project>
EOF

for artifact in "${DEST_DIR}/${ARTIFACT_ID}-${PORTICO_VERSION}.jar" "${DEST_DIR}/${ARTIFACT_ID}-${PORTICO_VERSION}.pom"; do
    write_checksum md5 md5 "${artifact}"
    write_checksum sha1 sha1 "${artifact}"
    write_checksum sha256 sha256 "${artifact}"
done

echo "Vendored Portico files written to ${DEST_DIR}"
