#!/bin/bash
# =============================================================================
# configure-realm.sh
# Applies post-import configuration to the epm realm.
# Run this ONCE after the realm is imported (on fresh docker-compose up).
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

# Login
/opt/keycloak/bin/kcadm.sh config credentials \
  --server "$KEYCLOAK_URL" \
  --realm master \
  --user "$ADMIN_USER" \
  --password "$ADMIN_PASS"

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

echo "Realm configuration complete."
