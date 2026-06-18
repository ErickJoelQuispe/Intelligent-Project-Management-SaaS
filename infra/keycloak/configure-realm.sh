#!/bin/bash
# =============================================================================
# configure-realm.sh
# Applies post-import configuration to the epm realm.
# Run this ONCE after the realm is imported (on fresh docker-compose up).
# Script is IDEMPOTENT — safe to run multiple times.
#
# Usage:
#   docker exec epm-keycloak bash /opt/keycloak/data/import/configure-realm.sh
# =============================================================================
set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="epm"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
KCADM="/opt/keycloak/bin/kcadm.sh"

echo "Waiting for Keycloak at $KEYCLOAK_URL..."

# ---------------------------------------------------------------------------
# 1. Authenticate via kcadm.sh
# ---------------------------------------------------------------------------
$KCADM config credentials \
  --server "$KEYCLOAK_URL" \
  --realm master \
  --user "$ADMIN_USER" \
  --password "$ADMIN_PASS"

# ---------------------------------------------------------------------------
# 2. tenant_id claim mapper (idempotent)
# ---------------------------------------------------------------------------

# Get epm-frontend client UUID using grep+sed (no python3 in Keycloak image)
CLIENT_UUID=$($KCADM get clients -r "$REALM" --fields id,clientId 2>/dev/null \
  | grep -B1 '"clientId" : "epm-frontend"' \
  | grep '"id"' \
  | sed 's/.*"id" : "\([^"]*\)".*/\1/')

echo "epm-frontend client UUID: $CLIENT_UUID"

# Check if tenant_id mapper already exists
EXISTING=$($KCADM get "clients/$CLIENT_UUID/protocol-mappers/models" -r "$REALM" 2>/dev/null \
  | grep -B1 '"name" : "tenant_id"' \
  | grep '"id"' \
  | sed 's/.*"id" : "\([^"]*\)".*/\1/')

if [ -n "$EXISTING" ]; then
  echo "tenant_id mapper already exists ($EXISTING) — skipping"
else
  echo "Adding tenant_id hardcoded claim mapper..."
  $KCADM create "clients/$CLIENT_UUID/protocol-mappers/models" -r "$REALM" -f - <<'JSON'
{
  "name": "tenant_id",
  "protocol": "openid-connect",
  "protocolMapper": "oidc-hardcoded-claim-mapper",
  "config": {
    "claim.name": "tenant_id",
    "claim.value": "00000000-0000-0000-0000-000000000001",
    "jsonType.label": "String",
    "id.token.claim": "true",
    "access.token.claim": "true",
    "userinfo.token.claim": "true"
  }
}
JSON
  echo "tenant_id mapper created OK"
fi

# ---------------------------------------------------------------------------
# 3. Helper: idempotent user creation
#    create_user_if_not_exists <username> <password> <role>
# ---------------------------------------------------------------------------
create_user_if_not_exists() {
  local USERNAME="$1"
  local PASSWORD="$2"
  local ROLE="$3"

  # Check if user already exists
  EXISTING_USER=$($KCADM get users -r "$REALM" \
    -q "username=$USERNAME" --fields id,username 2>/dev/null \
    | grep '"id"' \
    | sed 's/.*"id" : "\([^"]*\)".*/\1/')

  if [ -n "$EXISTING_USER" ]; then
    echo "User '$USERNAME' already exists ($EXISTING_USER) — updating password"
  else
    echo "Creating user '$USERNAME'..."
    $KCADM create users -r "$REALM" \
      -s username="$USERNAME" \
      -s email="$USERNAME" \
      -s enabled=true \
      -s emailVerified=true
    echo "User '$USERNAME' created OK"
  fi

  # Always set password (ensures it's correct even for re-runs)
  echo "Setting password for '$USERNAME'..."
  $KCADM set-password -r "$REALM" \
    --username "$USERNAME" \
    --new-password "$PASSWORD"
  echo "Password set OK for '$USERNAME'"

  # Assign realm role (idempotent)
  if [ -n "$ROLE" ]; then
    echo "Assigning role '$ROLE' to user '$USERNAME'..."
    $KCADM add-roles -r "$REALM" \
      --uusername "$USERNAME" \
      --rolename "$ROLE" 2>/dev/null \
      && echo "Role '$ROLE' assigned to '$USERNAME'" \
      || echo "Role '$ROLE' already assigned or not found — skipping"
  fi
}

# ---------------------------------------------------------------------------
# 4. Grant manage-users to epm-backend service account (idempotent)
# ---------------------------------------------------------------------------
echo "Granting manage-users role to service-account-epm-backend..."

SA_ID=$($KCADM get users -r "$REALM" \
  -q "username=service-account-epm-backend" --fields id \
  | grep '"id"' \
  | sed 's/.*"id" : "\([^"]*\)".*/\1/')

echo "Service account user ID: [$SA_ID]"

if [ -z "$SA_ID" ]; then
  echo "ERROR: service-account-epm-backend not found in realm $REALM"
  exit 1
fi

# add-roles with --cclientid fails if role already assigned; suppress only that error
$KCADM add-roles -r "$REALM" \
  --uid "$SA_ID" \
  --cclientid "realm-management" \
  --rolename "manage-users" \
  && echo "manage-users role granted to service-account-epm-backend" \
  || echo "manage-users already assigned — skipping"

# ---------------------------------------------------------------------------
# 5. Test user creation (idempotent)
# ---------------------------------------------------------------------------

create_user_if_not_exists "admin@epm.com" "Admin1234!" "ROLE_ADMIN"
create_user_if_not_exists "user@epm.com" "User1234!" "ROLE_USER"

echo "Realm configuration complete."
