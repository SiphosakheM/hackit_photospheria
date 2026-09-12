#!/usr/bin/env bash
set -euo pipefail

ARCHIVE_NAME="Siphosakhe_Msimango_Level1.zip"
STAGE_DIRECTORY_NAME="Siphosakhe_Msimango_Level1"
INPUT_STATE_FILE_PATH="shared_data/level_one_state.json"
OUTPUT_SOLUTION_FILE_PATH="level_1_greenhouse/outputs/solution.json"
DEFAULT_OPTIMIZATION_SEED_COUNT="32"

SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIRECTORY"

if ! command -v zip >/dev/null 2>&1; then
    echo "ERROR: the 'zip' utility is required to package the submission but was not found on PATH." >&2
    exit 1
fi

echo "==> Compiling the engine and running the full test suite (42 tests)..."
mvn -q -f level_1_greenhouse/pom.xml clean package

echo "==> Ensuring solution.json has been generated deterministically..."
if [ ! -f "$OUTPUT_SOLUTION_FILE_PATH" ]; then
    mvn -q -f level_1_greenhouse/pom.xml compile exec:java \
        -Dexec.args="${INPUT_STATE_FILE_PATH} ${OUTPUT_SOLUTION_FILE_PATH} ${DEFAULT_OPTIMIZATION_SEED_COUNT}"
fi

STAGING_ROOT_DIRECTORY="$(mktemp -d)"
STAGE_DIRECTORY="$STAGING_ROOT_DIRECTORY/$STAGE_DIRECTORY_NAME"
mkdir -p "$STAGE_DIRECTORY"
trap 'rm -rf "$STAGING_ROOT_DIRECTORY"' EXIT

echo "==> Copying required submission contents into the staging directory..."
cp -R level_1_greenhouse "$STAGE_DIRECTORY/level_1_greenhouse"
cp -R shared_data "$STAGE_DIRECTORY/shared_data"
cp README.md "$STAGE_DIRECTORY/README.md"

echo "==> Purging excluded directories (target/, .git/, .idea/, .vscode/)..."
rm -rf "$STAGE_DIRECTORY/level_1_greenhouse/target"
rm -rf "$STAGE_DIRECTORY/.git"
rm -rf "$STAGE_DIRECTORY/level_1_greenhouse/.git"
find "$STAGE_DIRECTORY" -type d -name .idea -prune -exec rm -rf {} +
find "$STAGE_DIRECTORY" -type d -name .vscode -prune -exec rm -rf {} +

echo "==> Packaging ${ARCHIVE_NAME}..."
rm -f "$SCRIPT_DIRECTORY/$ARCHIVE_NAME"
(
    cd "$STAGING_ROOT_DIRECTORY"
    zip -r -q "$SCRIPT_DIRECTORY/$ARCHIVE_NAME" "$STAGE_DIRECTORY_NAME" \
        -x "*/.git/*" \
        -x "*/.idea/*" \
        -x "*/.vscode/*" \
        -x "*/target/*"
)

echo "==> Done. Archive created at: $SCRIPT_DIRECTORY/$ARCHIVE_NAME"
echo
echo "Archive contents:"
unzip -l "$SCRIPT_DIRECTORY/$ARCHIVE_NAME"