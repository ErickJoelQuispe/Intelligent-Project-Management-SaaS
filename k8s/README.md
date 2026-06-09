# EPM — Local Kubernetes Guide (Phase 10)

Run the full EPM stack on a local [Kind](https://kind.sigs.k8s.io/) cluster with nginx Ingress, no cloud account required.

---

## Prerequisites

| Tool | Version | Install |
|------|---------|---------|
| Docker | 24+ (running) | https://docs.docker.com/get-docker/ |
| `kind` | v0.24+ | `brew install kind` / https://kind.sigs.k8s.io/docs/user/quick-start/#installation |
| `kubectl` | 1.31+ | `brew install kubectl` / https://kubernetes.io/docs/tasks/tools/ |
| `yq` (optional) | 4.x | `brew install yq` — useful for YAML inspection |

> **Tip:** verify everything is ready before proceeding:
> ```bash
> docker info && kind version && kubectl version --client
> ```

---

## Step 1 — Create the Kind cluster

```bash
kind create cluster --config k8s/ingress/kind-config.yaml --name epm
```

The `kind-config.yaml` labels the control-plane node `ingress-ready=true` and maps host ports 80 and 443 so the nginx Ingress controller can be reached from `http://localhost`.

Verify the cluster is up:

```bash
kubectl cluster-info --context kind-epm
```

---

## Step 2 — Install the nginx Ingress controller

```bash
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/controller-v1.12.0/deploy/static/provider/kind/deploy.yaml
```

Wait until the controller pod is ready (up to 90 seconds):

```bash
kubectl wait --namespace ingress-nginx \
  --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller \
  --timeout=90s
```

---

## Step 3 — Apply manifests (order matters)

Dependencies flow downward: namespace → infra → apps → ingress. Apply them in this exact order:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/infra/
kubectl apply -f k8s/apps/
kubectl apply -f k8s/ingress/
```

---

## Step 4 — Wait for pods to be ready

```bash
kubectl get pods -n epm -w
```

> **Expected startup time: 3–5 minutes.**
>
> - PostgreSQL and Kafka must reach `Running` (and pass readiness probes) **before** application pods start successfully.
> - JVM warm-up adds ~60–90 s per Spring Boot service.
> - Keycloak performs realm import on first start — this can take an extra 30–60 s.
>
> If a pod stays in `CrashLoopBackOff` during the first 2 minutes, that is normal — Kubernetes will retry once infra dependencies are ready.

Once stable, all 14 pods should show `Running`:

| Pod | Notes |
|-----|-------|
| `epm-postgres-0` | StatefulSet |
| `epm-kafka-0` | StatefulSet, KRaft mode |
| `epm-redis-*` | Deployment |
| `epm-keycloak-*` | Deployment, realm import on start |
| `epm-config-*` | Config baked at deploy time (see Known Limitations) |
| `epm-discovery-*` | Eureka server |
| `epm-api-gateway-*` | |
| `epm-auth-*` | |
| `epm-user-*` | |
| `epm-project-*` | |
| `epm-task-*` | |
| `epm-ai-*` | |
| `epm-notification-*` | |
| `epm-frontend-*` | nginx, static build |

---

## Step 5 — Access the application

Open your browser:

```
http://localhost
```

- **Frontend** — served at `/`
- **API** — routed via `/api/...` → `epm-api-gateway:8080`

---

## Step 6 — Teardown

```bash
kind delete cluster --name epm
```

This removes the cluster and all associated container state. Persistent volumes are also deleted.

---

## Known Limitations in Phase 10

| Limitation | Detail |
|-----------|--------|
| **Config service — no hot reload** | In docker-compose, `config-service` reads from a live `config-repo` bind mount. In K8s the config is baked into a ConfigMap at deploy time (see ADR-008). Changing a config property requires re-applying the ConfigMap and restarting the config-service pod. |
| **Keycloak realm ConfigMap size** | `realm-export.json` is embedded in a ConfigMap (~82 KB). This works for local Kind but approaches the etcd object size limit. For production, use an init-container or external volume to deliver the realm file. |
| **Plain base64 Secrets** | All Secrets in `k8s/infra/secrets.yaml` use plain base64 encoding — anyone with `kubectl get secret` can decode them. Replace placeholder values AND migrate to Sealed Secrets or HashiCorp Vault before any non-local deployment. |
| **No HPA, cert-manager, or TLS** | Horizontal Pod Autoscaler, cert-manager TLS termination, Kustomize overlays, and ArgoCD GitOps are deferred to Phase 11. |

---

## Secrets — Before You Deploy Anywhere Real

`k8s/infra/secrets.yaml` contains placeholder base64 values. **Do not use them outside a local laptop.**

Replace every placeholder before deploying to any shared or production environment:

```bash
# Generate a new base64 value
echo -n "your-real-password" | base64
```

Update the corresponding key in `k8s/infra/secrets.yaml`, then re-apply:

```bash
kubectl apply -f k8s/infra/secrets.yaml -n epm
# Restart affected pods to pick up the new secret
kubectl rollout restart deployment -n epm
```

For production use Sealed Secrets:
```bash
kubeseal --format=yaml < k8s/infra/secrets.yaml > k8s/infra/secrets-sealed.yaml
```
