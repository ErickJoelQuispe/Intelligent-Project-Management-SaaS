#!/bin/bash
# =============================================================================
# sync-dockerfiles.sh
# Ensures every service Dockerfile contains a COPY line for EACH module's
# pom.xml declared in the monorepo root pom.xml.
#
# Maven reactor resolution requires ALL sibling pom.xml files to be present
# in the build context, even for modules that are not being compiled.
#
# Usage:
#   ./scripts/sync-dockerfiles.sh            # apply mode (writes changes)
#   ./scripts/sync-dockerfiles.sh --check    # check mode (exits 1 if out of sync)
#
# Add to CI:
#   - name: Verify Dockerfile pom.xml COPY lines
#     run: ./scripts/sync-dockerfiles.sh --check
# =============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ROOT_POM="$REPO_ROOT/pom.xml"
SERVICES_DIR="$REPO_ROOT/services"

CHECK_MODE=false
if [[ "${1:-}" == "--check" ]]; then
  CHECK_MODE=true
fi

# ---------------------------------------------------------------------------
# 1. Extract module paths from root pom.xml
#    Matches lines like: <module>services/foo-service</module>
# ---------------------------------------------------------------------------
MODULES=()
while IFS= read -r line; do
  # Extract path between <module> tags
  if [[ "$line" =~ \<module\>(.*)\</module\> ]]; then
    MODULES+=("${BASH_REMATCH[1]}")
  fi
done < "$ROOT_POM"

if [[ ${#MODULES[@]} -eq 0 ]]; then
  echo "ERROR: No modules found in $ROOT_POM" >&2
  exit 1
fi

echo "Modules declared in root pom.xml (${#MODULES[@]}):"
for m in "${MODULES[@]}"; do
  echo "  - $m"
done
echo ""

# ---------------------------------------------------------------------------
# 2. For each service Dockerfile, verify/add COPY lines for all modules
# ---------------------------------------------------------------------------
OVERALL_CHANGES=0

for DOCKERFILE in "$SERVICES_DIR"/*/Dockerfile; do
  SERVICE_DIR="$(dirname "$DOCKERFILE")"
  SERVICE_NAME="$(basename "$SERVICE_DIR")"

  CHANGES=0
  MISSING_LINES=()

  for MODULE_PATH in "${MODULES[@]}"; do
    EXPECTED_COPY="COPY ${MODULE_PATH}/pom.xml ${MODULE_PATH}/"
    if ! grep -qF "$EXPECTED_COPY" "$DOCKERFILE"; then
      MISSING_LINES+=("$EXPECTED_COPY")
      CHANGES=$((CHANGES + 1))
    fi
  done

  if [[ $CHANGES -eq 0 ]]; then
    echo "[$SERVICE_NAME] OK — all ${#MODULES[@]} COPY lines present"
    continue
  fi

  OVERALL_CHANGES=$((OVERALL_CHANGES + CHANGES))

  if $CHECK_MODE; then
    echo "[$SERVICE_NAME] MISSING $CHANGES COPY line(s):"
    for line in "${MISSING_LINES[@]}"; do
      echo "  + $line"
    done
  else
    echo "[$SERVICE_NAME] Adding $CHANGES missing COPY line(s)..."

    # Find the insertion point: the line BEFORE the first "RUN ./mvnw dependency:go-offline"
    # We insert the new COPY lines just before that RUN command.
    TEMP_FILE=$(mktemp)

    INSERTED=false
    while IFS= read -r line; do
      if [[ "$INSERTED" == false ]] && [[ "$line" =~ ^RUN.*mvnw.*dependency:go-offline ]]; then
        # Insert missing COPY lines first
        for copy_line in "${MISSING_LINES[@]}"; do
          echo "$copy_line" >> "$TEMP_FILE"
        done
        INSERTED=true
      fi
      echo "$line" >> "$TEMP_FILE"
    done < "$DOCKERFILE"

    mv "$TEMP_FILE" "$DOCKERFILE"
    echo "[$SERVICE_NAME] Updated successfully"
  fi
done

echo ""

# ---------------------------------------------------------------------------
# 3. Report result
# ---------------------------------------------------------------------------
if [[ $OVERALL_CHANGES -gt 0 ]]; then
  if $CHECK_MODE; then
    echo "CHECK FAILED: $OVERALL_CHANGES COPY line(s) missing across Dockerfiles." >&2
    echo "Run './scripts/sync-dockerfiles.sh' (without --check) to fix automatically." >&2
    exit 1
  else
    echo "Done: $OVERALL_CHANGES COPY line(s) added across Dockerfiles."
  fi
else
  echo "All Dockerfiles are in sync with root pom.xml."
fi
