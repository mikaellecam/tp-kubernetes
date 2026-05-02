#!/usr/bin/env bash
# LaboTrack RunBook — one command from zero to a meshed cluster.
# Run from inside WSL2 Ubuntu. Requires: docker, minikube, kubectl, linkerd, mvn (none if STRATEGY=in-cluster).
#
# Usage:
#   bash labotrack/runbook.sh                       # default: registry strategy
#   STRATEGY=in-cluster bash labotrack/runbook.sh   # build images directly inside Minikube's docker (no push)
#   SKIP_LINKERD=1 bash labotrack/runbook.sh        # deploy without Linkerd
#   SKIP_BUILD=1   bash labotrack/runbook.sh        # reuse existing images
#
# Idempotent: re-running picks up where the previous run left off.

set -euo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
LABO_DIR="${REPO_ROOT}/labotrack"
SERVICES=(sample-api analysis-api result-frontend)
STRATEGY="${STRATEGY:-registry}"   # registry | in-cluster
TAG="${TAG:-1.0}"
NS=labotrack

log() { printf '\n\033[1;36m=== %s ===\033[0m\n' "$*"; }

require() {
  command -v "$1" >/dev/null 2>&1 || { echo "Missing $1 in PATH" >&2; exit 1; }
}

require docker
require kubectl
require minikube

# ---------- 1. Minikube ----------
log "Starting Minikube"
if minikube status >/dev/null 2>&1; then
  echo "Minikube already running."
else
  minikube start --driver=docker --cpus=4 --memory=6g --addons=metrics-server
fi

# ---------- 2. Registry (registry strategy only) ----------
REGISTRY_PF_PID=""
trap '[[ -n "${REGISTRY_PF_PID}" ]] && kill "${REGISTRY_PF_PID}" 2>/dev/null || true' EXIT

if [[ "${STRATEGY}" == "registry" ]]; then
  log "Enabling minikube registry addon"
  minikube addons enable registry

  log "Forwarding registry to localhost:5000"
  kubectl -n kube-system rollout status deploy/registry --timeout=120s
  kubectl -n kube-system port-forward --address 127.0.0.1 svc/registry 5000:80 >/dev/null 2>&1 &
  REGISTRY_PF_PID=$!
  sleep 2
fi

# ---------- 3. Linkerd ----------
if [[ "${SKIP_LINKERD:-0}" != "1" ]]; then
  require linkerd

  log "Installing Gateway API CRDs (Linkerd prerequisite)"
  if ! kubectl get crd httproutes.gateway.networking.k8s.io >/dev/null 2>&1; then
    kubectl apply --server-side -f \
      https://github.com/kubernetes-sigs/gateway-api/releases/download/v1.2.1/standard-install.yaml
  fi

  log "Installing Linkerd CRDs + control plane"
  if ! kubectl get ns linkerd >/dev/null 2>&1; then
    linkerd install --crds | kubectl apply -f -
    linkerd install --set proxyInit.runAsRoot=true | kubectl apply -f -
  fi
  linkerd check

  log "Installing Linkerd Viz"
  if ! kubectl get ns linkerd-viz >/dev/null 2>&1; then
    linkerd viz install | kubectl apply -f -
  fi
  linkerd viz check
fi

# ---------- 4. Build images ----------
build_image() {
  local svc="$1"
  local dir="${LABO_DIR}/services/${svc}"
  local image
  if [[ "${STRATEGY}" == "in-cluster" ]]; then
    image="${svc}:${TAG}"
  else
    image="localhost:5000/${svc}:${TAG}"
  fi
  log "Building ${image}"
  docker build -t "${image}" "${dir}"
  if [[ "${STRATEGY}" == "registry" ]]; then
    docker push "${image}"
  fi
}

if [[ "${SKIP_BUILD:-0}" != "1" ]]; then
  if [[ "${STRATEGY}" == "in-cluster" ]]; then
    eval "$(minikube docker-env)"
  fi
  for svc in "${SERVICES[@]}"; do
    build_image "${svc}"
  done
  if [[ "${STRATEGY}" == "in-cluster" ]]; then
    eval "$(minikube docker-env -u)"
  fi
fi

# ---------- 5. Apply manifests ----------
log "Applying Kubernetes manifests"
kubectl apply -f "${LABO_DIR}/manifests/00-namespace.yaml"
kubectl apply -f "${LABO_DIR}/manifests/10-postgres.yaml"
kubectl -n "${NS}" rollout status statefulset/postgres --timeout=180s

# Patch image references for in-cluster strategy (no registry prefix).
if [[ "${STRATEGY}" == "in-cluster" ]]; then
  for svc in "${SERVICES[@]}"; do
    sed "s#localhost:5000/${svc}:${TAG}#${svc}:${TAG}#g" \
      "${LABO_DIR}/manifests/$(case "${svc}" in
        sample-api)      echo 20-sample-api.yaml ;;
        analysis-api)    echo 30-analysis-api.yaml ;;
        result-frontend) echo 40-result-frontend.yaml ;;
      esac)" | kubectl apply -f -
  done
else
  kubectl apply -f "${LABO_DIR}/manifests/20-sample-api.yaml"
  kubectl apply -f "${LABO_DIR}/manifests/30-analysis-api.yaml"
  kubectl apply -f "${LABO_DIR}/manifests/40-result-frontend.yaml"
fi

if [[ "${SKIP_LINKERD:-0}" != "1" ]]; then
  log "Applying Linkerd ServiceProfile and AuthorizationPolicy"
  kubectl apply -f "${LABO_DIR}/manifests/60-linkerd-serviceprofile.yaml"
  kubectl apply -f "${LABO_DIR}/manifests/70-linkerd-authz.yaml"
fi

log "Waiting for rollouts"
kubectl -n "${NS}" rollout status deploy/sample-api      --timeout=240s
kubectl -n "${NS}" rollout status deploy/analysis-api    --timeout=240s
kubectl -n "${NS}" rollout status deploy/result-frontend --timeout=240s

# ---------- 6. Print URLs ----------
log "All set."
echo "Frontend URL:   $(minikube service result-frontend -n ${NS} --url)"
if [[ "${SKIP_LINKERD:-0}" != "1" ]]; then
  echo "Linkerd Viz:    run 'linkerd viz dashboard' in a separate terminal."
  echo "mTLS edges:     linkerd -n ${NS} viz edges deploy"
  echo "Live tap:       linkerd -n ${NS} viz tap deploy/analysis-api"
fi
echo "Pods:"
kubectl -n "${NS}" get pods -o wide
