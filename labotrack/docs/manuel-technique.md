# Manuel technique — LaboTrack

## Stack

| Couche | Choix |
|---|---|
| Langage | Java 21 (Eclipse Temurin) |
| Framework | Spring Boot 3.3.5 (`web`, `webflux` pour le client réactif, `data-jpa`, `actuator`, `thymeleaf` côté frontend, `validation`) |
| Build | Maven 3.9 (multi-stage Dockerfile) |
| BDD | PostgreSQL 16 (StatefulSet, deux bases : `samples`, `analysis`) ; H2 en profil `dev` pour le développement local |
| Conteneurs | Docker (multi-stage) + addon `registry` de Minikube |
| Orchestration | Kubernetes via Minikube (driver `docker`, 4 vCPU, 6 Go) |
| Service Mesh | Linkerd 2.x (CRDs + control plane + viz) |
| OS de référence | WSL2 Ubuntu 24.04 LTS sur Windows 11 |

## Arborescence du projet

```
tp-kubernetes/
├── README.md                        # description et liens
├── document_tp_kubernetes.md        # référence d'installation fournie
├── tp_kubernetes (Services Mesh) 2026.md  # énoncé
├── step1/
│   ├── questions/
│   │   └── answers.md               # réponses aux 20 questions Kubernetes
│   └── monservice/                  # service Spring Boot démonstrateur
│       ├── pom.xml
│       ├── src/...
│       ├── Dockerfile               # multi-stage
│       ├── Dockerfile.simple        # mono-stage (cas 1)
│       └── docker-compose.yml
└── labotrack/
    ├── docs/                        # ce manuel + manuel-utilisateur + architecture
    ├── services/
    │   ├── sample-api/
    │   ├── analysis-api/
    │   └── result-frontend/
    ├── manifests/                   # *.yaml Kubernetes + Linkerd
    └── runbook.sh                   # déploiement « one-shot »
```

## Endpoints

### sample-api (port 8080)
- `POST /samples` — body `{patient,examType,sampleType}` → `Sample` créé.
- `GET /samples` — liste tous les échantillons.
- `GET /samples/{id}` — récupère un échantillon ; utilisé par `analysis-api`.
- `PATCH /samples/{id}/status` — body `{status}` → met à jour le statut ; utilisé par `analysis-api`.
- Liveness/readiness : `/actuator/health/{liveness,readiness}`.

### analysis-api (port 8080)
- `POST /analyze/{id}` — orchestration : `GET sample-api/samples/{id}` → calcul → persistance → `PATCH sample-api/samples/{id}/status`.
- `GET /results/{sampleId}` — récupère le résultat d'analyse.
- `GET /results` — liste tous les résultats.
- Latence simulée configurable via `ANALYSIS_SIMULATED_LATENCY_MS` (300 ms par défaut sur le déploiement K8s, pour démontrer les retries Linkerd).

### result-frontend (port 8080, NodePort 30080)
- `GET /` — page Thymeleaf, liste les échantillons + résultats agrégés.
- `POST /samples` — formulaire HTML, proxie vers `sample-api`.
- `POST /analyze` — bouton, proxie vers `analysis-api`.

## Stratégie d'images Docker — multi-stage

Les trois services partagent le même squelette de Dockerfile. Exemple (`sample-api/Dockerfile`) :

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/sample-api.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

**Bénéfices** :
- L'image finale ne contient ni Maven ni JDK, seulement un JRE.
- Cache Maven via `dependency:go-offline` (rebuild rapide en cas de changement de code uniquement).
- Aucune dépendance hôte (Java/Maven non requis pour builder).

## Build & push — deux stratégies, exposées via la variable `STRATEGY` du runbook

### Stratégie `registry` (par défaut, conforme à l'énoncé)
```bash
minikube addons enable registry
kubectl -n kube-system port-forward svc/registry 5000:80 &
docker build -t localhost:5000/sample-api:1.0 labotrack/services/sample-api
docker push   localhost:5000/sample-api:1.0
# idem pour analysis-api et result-frontend
```
Les manifests référencent `localhost:5000/<svc>:1.0`. Minikube tire les images depuis son registry interne.

### Stratégie `in-cluster` (fallback)
```bash
eval $(minikube docker-env)
docker build -t sample-api:1.0 labotrack/services/sample-api
# idem pour les autres
eval $(minikube docker-env -u)
```
Aucun push ; les images sont construites directement dans le `docker` interne de Minikube. Le runbook substitue alors le tag dans les manifests.

## Manifests Kubernetes

| Fichier | Contenu |
|---|---|
| `00-namespace.yaml` | namespace `labotrack` annoté `linkerd.io/inject=enabled` |
| `10-postgres.yaml` | `Secret` + `ConfigMap` d'init (création des deux bases) + `Service` headless + `StatefulSet` (1 replica, PVC 1 Gi) |
| `20-sample-api.yaml` | `ServiceAccount` + `Deployment` (2 replicas) + `Service` ClusterIP, profil Spring `prod`, env JDBC depuis le secret |
| `30-analysis-api.yaml` | idem, 3 replicas, `SAMPLE_API_BASE_URL`, `ANALYSIS_SIMULATED_LATENCY_MS=300` |
| `40-result-frontend.yaml` | 1 replica, `Service` `NodePort 30080` |
| `60-linkerd-serviceprofile.yaml` | `ServiceProfile` pour `sample-api` et `analysis-api` (routes, `isRetryable`, `timeout`, `retryBudget`) |
| `70-linkerd-authz.yaml` | `Server` + `MeshTLSAuthentication` + `AuthorizationPolicy` ; expose le frontend en public via `NetworkAuthentication` |
| `99-rogue-pod.yaml` | pod « pirate » dans un autre namespace pour démontrer le rejet de l'AuthorizationPolicy |

## Service Mesh Linkerd

### Installation
```bash
linkerd install --crds | kubectl apply -f -
linkerd install        | kubectl apply -f -
linkerd check
linkerd viz install    | kubectl apply -f -
linkerd viz check
```

### Vérifications
```bash
linkerd check --proxy -n labotrack         # tous les sidecars sains
linkerd -n labotrack viz edges deploy      # mTLS = True sur chaque arête
linkerd -n labotrack viz stat deploy       # RPS, latences p50/p95/p99
linkerd -n labotrack viz tap deploy/analysis-api    # trace live des requêtes
linkerd -n labotrack viz top deploy/analysis-api    # top routes
```

### Démonstration des retries
La latence artificielle de 300 ms sur `analysis-api` combinée au timeout `8s` de la route `/analyze/{id}` ne déclenche pas de retry (timeout >> latence) ; en revanche les `GET` `/samples/{id}` et `/results/{id}` côté caller ont `isRetryable: true` et `timeout: 3s`. En provoquant des erreurs transitoires (ex. `kubectl -n labotrack delete pod -l app=sample-api` pendant un appel) on observe les retries dans `viz tap`.

### Démonstration zero-trust
```bash
kubectl apply -f labotrack/manifests/99-rogue-pod.yaml
kubectl -n rogue exec rogue -- curl -s -o /dev/null -w '%{http_code}\n' \
  http://sample-api.labotrack.svc.cluster.local:8080/samples
# Réponse attendue : 403
```

## Sécurité

- Aucun secret en clair dans les manifests : credentials Postgres dans un `Secret` Kubernetes consommé par les deux services.
- mTLS à 100 % en intra-mesh.
- Politique Zero-Trust par défaut : seuls les couples (caller, callee) listés dans `70-linkerd-authz.yaml` sont autorisés ; tout autre trafic est rejeté par le proxy avant même d'atteindre l'application.

## Limites connues / pistes d'amélioration

- Postgres single replica + PVC standard (pas de HA). Pour la prod : opérateur (CloudNativePG ou Zalando Postgres Operator).
- Pas de migration de schéma versionnée (Hibernate `ddl-auto=update`). Pour la prod : Flyway / Liquibase.
- Pas d'Ingress, pas de TLS public ; le frontend est exposé en `NodePort` pour simplifier la démo Minikube.
- Linkerd `viz` embarque déjà Prometheus/Grafana ; un kube-prometheus-stack standalone (option du TP) n'est pas livré faute de temps.
