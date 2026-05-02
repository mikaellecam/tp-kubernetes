# TP Kubernetes — Service Mesh (LaboTrack)

> Module Architecture — INSA — H. Tondeur 2026.
> Réalisé sur Windows 11 + WSL2 Ubuntu 24.04 + Docker Desktop.

### Groupe: 

- Mikael LE CAM
- Antonin RIQUART
- Gregoire LEGRAND
- Jonathan ISAMBOURG

## Contenu du dépôt

```
tp-kubernetes/
├── README.md                              # vous êtes ici
├── document_tp_kubernetes.md              # référence d'installation fournie par l'enseignant
├── tp_kubernetes (Services Mesh) 2026.md  # énoncé du TP
├── bootstrap-wsl.sh                       # script d'amorçage WSL2 Ubuntu (JDK, Maven, Minikube, kubectl, Linkerd)
├── step1/
│   ├── questions/
│   │   ├── answers.md                     # réponses aux 20 questions Kubernetes
│   │   └── screenshots/                   # captures d'écran q01..q20
│   └── monservice/                        # Spring Boot REST + Dockerfile + Dockerfile multi-stage + docker-compose
└── labotrack/
    ├── docs/
    │   ├── architecture.md                # diagramme + cycle de vie + choix de design
    │   ├── manuel-technique.md            # stack, build, manifests, mesh
    │   └── manuel-utilisateur.md          # « how-to » + dépannage
    ├── services/
    │   ├── sample-api/                    # Spring Boot + JPA, POST /samples, GET /samples/{id}
    │   ├── analysis-api/                  # Spring Boot + WebClient, POST /analyze/{id}
    │   └── result-frontend/               # Spring Boot + Thymeleaf, page agrégée
    ├── manifests/                         # *.yaml Kubernetes + Linkerd (ServiceProfile, AuthorizationPolicy)
    └── runbook.sh                         # déploiement « one-shot » de bout en bout
```

## Démarrage rapide

### Sur Windows hôte
1. Démarrer **Docker Desktop**.
2. `wsl --install -d Ubuntu-24.04 --no-launch` (si pas déjà fait), puis ouvrir Ubuntu une première fois pour créer l'utilisateur.
3. Docker Desktop → **Settings → Resources → WSL Integration** → activer `Ubuntu-24.04`.

### Dans WSL2 Ubuntu
```bash
cd /mnt/c/Users/mikae/Documents/Dev/INSA/Architecture/tp-kubernetes
sudo bash bootstrap-wsl.sh         # JDK 21, Maven, Minikube, kubectl, Linkerd
bash labotrack/runbook.sh           # déploie tout sur Minikube
```

À la fin, le runbook imprime l'URL du frontend (`http://192.168.49.2:30080`).

## Étape 1 — Manipulations

- **Étape 1A** : `step1/questions/answers.md` répond aux 20 questions Kubernetes (commande + capture pour chaque).
- **Étape 1B** : `step1/monservice/` héberge le service Spring Boot démonstrateur avec :
  - `Dockerfile.simple` (cas 1 : compile en local puis copie le fat-jar).
  - `Dockerfile` (cas 2 : multi-stage, compilation et packaging à l'intérieur du build Docker).
  - `docker-compose.yml` qui utilise `Dockerfile.simple`.

## Étape 2 — LaboTrack

Le dossier `labotrack/` implémente le « Mini-Système de Suivi des Analyses de Laboratoire » sous forme de 3 microservices Spring Boot maillés par Linkerd. Tous les détails techniques se trouvent dans `labotrack/docs/manuel-technique.md` et `labotrack/docs/architecture.md`.