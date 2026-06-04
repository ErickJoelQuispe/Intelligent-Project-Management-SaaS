#!/bin/bash
# =============================================================================
# configure-realm.sh
# Applies post-import configuration to the epm realm.
# Run this ONCE after the realm is imported (on fresh docker-compose up).
# Script is IDEMPOTENT — safe to run multiple times.
#
# Usage:
#   docker exec epm-keycloak bash /configure-realm.sh
#   OR run from host with KEYCLOAK_URL set to the container URL.
# =============================================================================
set -e

KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8080}"
REALM="epm"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"

echo "Waiting for Keycloak at $KEYCLOAK_URL..."

# ---------------------------------------------------------------------------
# 1. Authenticate via kcadm.sh
# ---------------------------------------------------------------------------
/opt/keycloak/bin/kcadm.sh config credentials \
  --server "$KEYCLOAK_URL" \
  --realm master \
  --user "$ADMIN_USER" \
  --password "$ADMIN_PASS"

# ---------------------------------------------------------------------------
# 2. tenant_id claim mapper (idempotent)
# ---------------------------------------------------------------------------

# Get epm-frontend client ID
CLIENT_UUID=$(/opt/keycloak/bin/kcadm.sh get clients -r "$REALM" --fields id,clientId 2>/dev/null \
  | python3 -c "import sys,json; clients=json.load(sys.stdin); print(next(c['id'] for c in clients if c['clientId']=='epm-frontend'))")

echo "epm-frontend client UUID: $CLIENT_UUID"

# Check if tenant_id mapper already exists
EXISTING=$(/opt/keycloak/bin/kcadm.sh get "clients/$CLIENT_UUID/protocol-mappers/models" -r "$REALM" 2>/dev/null \
  | python3 -c "import sys,json; mappers=json.load(sys.stdin); print(next((m['id'] for m in mappers if m['name']=='tenant_id'), ''))")

if [ -n "$EXISTING" ]; then
  echo "tenant_id mapper already exists ($EXISTING) — skipping"
else
  echo "Adding tenant_id hardcoded claim mapper..."
  /opt/keycloak/bin/kcadm.sh create "clients/$CLIENT_UUID/protocol-mappers/models" -r "$REALM" -f - <<'JSON'
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

  # Check if user already exists (kcadm returns a list; empty = not found)
  EXISTING_USER=$(/opt/keycloak/bin/kcadm.sh get users -r "$REALM" \
    -q "username=$USERNAME" --fields id,username 2>/dev/null \
    | python3 -c "import sys,json; users=json.load(sys.stdin); print(next((u['id'] for u in users if u['username']=='$USERNAME'), ''))")

  if [ -n "$EXISTING_USER" ]; then
    echo "User '$USERNAME' already exists ($EXISTING_USER) — skipping creation"
  else
    echo "Creating user '$USERNAME'..."
    /opt/keycloak/bin/kcadm.sh create users -r "$REALM" \
      -s username="$USERNAME" \
      -s email="$USERNAME" \
      -s enabled=true \
      -s emailVerified=true

    # Set password (temporary=false → user does not need to reset on first login)
    USER_ID=$(/opt/keycloak/bin/kcadm.sh get users -r "$REALM" \
      -q "username=$USERNAME" --fields id 2>/dev/null \
      | python3 -c "import sys,json; users=json.load(sys.stdin); print(users[0]['id'])")

    /opt/keycloak/bin/kcadm.sh set-password -r "$REALM" \
      --username "$USERNAME" \
      --new-password "$PASSWORD" \
      --temporary false

    echo "User '$USERNAME' created (id=$USER_ID)"
  fi

  # Assign realm role (idempotent — kcadm add-roles is safe if role already assigned)
  if [ -n "$ROLE" ]; then
    echo "Assigning role '$ROLE' to user '$USERNAME'..."
    /opt/keycloak/bin/kcadm.sh add-roles -r "$REALM" \
      --uusername "$USERNAME" \
      --rolename "$ROLE" 2>/dev/null && echo "Role '$ROLE' assigned to '$USERNAME'" \
      || echo "Role '$ROLE' already assigned or not found for '$USERNAME' — skipping"
  fi
}

# ---------------------------------------------------------------------------
# 4. Test user creation (idempotent)
# ---------------------------------------------------------------------------

# admin@epm.com — gets ROLE_ADMIN
create_user_if_not_exists "admin@epm.com" "Admin1234!" "ROLE_ADMIN"

# user@epm.com — gets ROLE_USER
create_user_if_not_exists "user@epm.com" "User1234!" "ROLE_USER"

echo "Realm configuration complete."
