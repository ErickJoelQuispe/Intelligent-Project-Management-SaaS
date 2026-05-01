#!/bin/bash
# =============================================================================
# topics-init.sh
# Crea todos los topics del sistema en Kafka.
#
# Idempotente: se puede correr múltiples veces sin error (--if-not-exists).
# Prerequisito: Kafka debe estar corriendo en localhost:9092
# Uso: ./infra/kafka/topics-init.sh
# =============================================================================
set -e

BOOTSTRAP="localhost:9092"

create_topic() {
    local topic=$1
    local partitions=${2:-3}
    local retention_ms=${3:-604800000}  # 7 días por default

    docker exec epm-kafka kafka-topics \
        --bootstrap-server $BOOTSTRAP \
        --create \
        --if-not-exists \
        --topic "$topic" \
        --partitions "$partitions" \
        --replication-factor 1 \
        --config "retention.ms=$retention_ms"

    echo "  ✅ $topic"
}

echo ""
echo "🚀 Creating Kafka topics..."
echo ""

# ── Auth context ─────────────────────────────────────────────────
echo "── auth ──"
create_topic "auth.account.registered"
create_topic "auth.account.logged-in"
create_topic "auth.account.locked"

# ── User context ──────────────────────────────────────────────────
echo "── user ──"
create_topic "user.profile.updated"
create_topic "user.team.created"
create_topic "user.team.member-joined"
create_topic "user.team.member-left"
create_topic "user.team.deleted"

# ── Project context ───────────────────────────────────────────────
echo "── project ──"
create_topic "project.project.created"
create_topic "project.project.updated"
create_topic "project.project.archived"
create_topic "project.team.assigned"

# ── Task context ──────────────────────────────────────────────────
echo "── task ──"
create_topic "task.task.created"
create_topic "task.task.updated"
create_topic "task.task.status-changed"
create_topic "task.task.assigned"
create_topic "task.task.deleted"

# ── AI context ────────────────────────────────────────────────────
echo "── ai ──"
create_topic "ai.request.requested"
create_topic "ai.tasks.generated"
create_topic "ai.summary.generated"
create_topic "ai.request.failed"

# ── Notification context ──────────────────────────────────────────
echo "── notification ──"
create_topic "notification.notification.created"

# ── Dead Letter Topics ────────────────────────────────────────────
echo "── DLT ──"
create_topic "auth.account.registered.DLT"     1
create_topic "user.team.created.DLT"            1
create_topic "project.project.created.DLT"      1
create_topic "task.task.created.DLT"            1
create_topic "ai.tasks.generated.DLT"           1

echo ""
echo "✅ All topics created successfully"
echo ""

# Verificación final
echo "📋 Topic list:"
docker exec epm-kafka kafka-topics \
    --bootstrap-server $BOOTSTRAP \
    --list
