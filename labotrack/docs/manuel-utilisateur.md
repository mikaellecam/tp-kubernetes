# Manuel utilisateur — LaboTrack

## Pré-requis

- Windows 11 avec Docker Desktop installé et démarré, WSL2 Ubuntu 24.04 (cf. `Phase 0` dans `runbook.sh` ou les instructions ci-dessous).
- Outils dans WSL2 : `docker`, `minikube`, `kubectl`, `linkerd`. Un script d'amorçage est fourni : `./bootstrap-wsl.sh`.

## Démarrage en une commande

Depuis WSL2 Ubuntu, dans le dossier du repo :

```bash
bash labotrack/runbook.sh
```

Le runbook :

1. démarre Minikube (4 vCPU, 6 Go) ;
2. active l'addon `registry` ;
3. installe Linkerd (CRDs, control plane, viz) ;
4. construit et pousse les 3 images Docker multi-stage ;
5. applique tous les manifests Kubernetes (namespace, Postgres, services, ServiceProfile, AuthorizationPolicy) ;
6. attend que les rollouts soient prêts ;
7. affiche l'URL du frontend.

À la fin, le terminal imprime quelque chose comme :
```
Frontend URL: http://192.168.49.2:30080
```

## Utilisation de l'application

1. Ouvrir l'URL du frontend dans un navigateur.
2. Dans la section **Register a sample**, saisir un patient (ex. `Dupont`), un type d'examen (`glycemia`) et un type d'échantillon (`blood`), puis valider. La ligne apparaît dans le tableau avec le statut `REGISTERED`.
3. Cliquer sur **Analyze** dans la dernière colonne. `analysis-api` est appelée, qui à son tour interroge `sample-api`, génère un résultat aléatoire de glycémie, le persiste, et bascule le statut à `VALIDATED`.
4. La page se recharge ; la ligne montre maintenant la valeur en `g/L`, l'interprétation (`low` / `normal` / `high`) et le statut `VALIDATED`.

## Tests rapides via curl

```bash
FRONTEND=$(minikube service result-frontend -n labotrack --url)
SAMPLE=$(kubectl -n labotrack run curl --rm -i --image=curlimages/curl --restart=Never -- \
  curl -s -X POST -H 'Content-Type: application/json' \
  -d '{"patient":"Dupont","examType":"glycemia","sampleType":"blood"}' \
  http://sample-api.labotrack.svc.cluster.local:8080/samples)
echo "Sample created: $SAMPLE"
```

## Observabilité

- Dashboard Linkerd : `linkerd viz dashboard &` puis ouvrir l'URL imprimée.
- Métriques en CLI :
  - `linkerd -n labotrack viz stat deploy` — RPS et latences p50/p95/p99 par déploiement.
  - `linkerd -n labotrack viz edges deploy` — confirme le mTLS sur chaque arête (`SECURED`).
  - `linkerd -n labotrack viz tap deploy/analysis-api` — flux live des requêtes ; idéal pendant qu'on clique « Analyze ».
  - `linkerd -n labotrack viz top deploy/analysis-api` — agrégation par route.

## Démonstration zero-trust (optionnel)

```bash
kubectl apply -f labotrack/manifests/99-rogue-pod.yaml
kubectl -n rogue exec rogue -- curl -s -o /dev/null -w '%{http_code}\n' \
  http://sample-api.labotrack.svc.cluster.local:8080/samples
# attendu : 403
kubectl delete -f labotrack/manifests/99-rogue-pod.yaml
```

## Arrêt

```bash
kubectl delete namespace labotrack
linkerd viz uninstall | kubectl delete -f -
linkerd uninstall     | kubectl delete -f -
minikube stop
```

## Dépannage

| Symptôme | Cause probable | Action |
|---|---|---|
| `ImagePullBackOff` sur les pods | Le port-forward du registry est tombé | Re-exécuter `kubectl -n kube-system port-forward svc/registry 5000:80 &` ou relancer le runbook avec `STRATEGY=in-cluster` |
| `analysis-api` `503` ou timeout | `sample-api` pas prêt | `kubectl -n labotrack get pods` ; attendre `2/2 Running` |
| `linkerd check --proxy` échoue | Sidecar pas injecté | Vérifier l'annotation du namespace, puis `kubectl -n labotrack rollout restart deploy` |
| Pod « rogue » reçoit `200` au lieu de `403` | `AuthorizationPolicy` non appliquée | `kubectl get authorizationpolicy -n labotrack` ; ré-appliquer `70-linkerd-authz.yaml` |
