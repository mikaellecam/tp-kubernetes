#!/usr/bin/env bash
# Bootstrap WSL2 Ubuntu 24.04 with all the tooling required for the TP.
# Run with sudo or as root (a sudoer is created if needed).
# Idempotent: safe to re-run.

set -euo pipefail

log() { printf '\n\033[1;36m=== %s ===\033[0m\n' "$*"; }

require_root() {
  if [[ $EUID -ne 0 ]]; then
    echo "This script must be run as root (sudo)." >&2
    exit 1
  fi
}

require_root

log "apt update + base packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y --no-install-recommends \
  ca-certificates curl wget git unzip jq vim \
  openjdk-21-jdk maven \
  iproute2 iputils-ping dnsutils

log "Installing kubectl"
if ! command -v kubectl >/dev/null 2>&1; then
  KVER=$(curl -sL https://dl.k8s.io/release/stable.txt)
  curl -fsSL "https://dl.k8s.io/release/${KVER}/bin/linux/amd64/kubectl" -o /usr/local/bin/kubectl
  chmod +x /usr/local/bin/kubectl
fi
kubectl version --client=true --output=yaml | head -n 5

log "Installing Minikube"
if ! command -v minikube >/dev/null 2>&1; then
  curl -fsSL https://storage.googleapis.com/minikube/releases/latest/minikube-linux-amd64 -o /usr/local/bin/minikube
  chmod +x /usr/local/bin/minikube
fi
minikube version

log "Installing Linkerd CLI"
if ! command -v linkerd >/dev/null 2>&1; then
  curl -fsSL https://run.linkerd.io/install | HOME=/root sh
  install -m 0755 /root/.linkerd2/bin/linkerd /usr/local/bin/linkerd
fi
linkerd version --client

log "Verifying Docker"
if ! docker info >/dev/null 2>&1; then
  cat <<EOF >&2
Docker not reachable from inside this distro.
Open Docker Desktop on Windows -> Settings -> Resources -> WSL Integration,
toggle Ubuntu-24.04 ON, then re-run this script.
EOF
  exit 1
fi
docker version --format '{{.Server.Version}}'

log "Final sanity check"
java -version
mvn -v | head -n 1
docker version --format '{{.Server.Version}}'
minikube version | head -n 1
kubectl version --client=true --output=yaml | head -n 2
linkerd version --client

log "Bootstrap complete. You can now run: bash labotrack/runbook.sh"
