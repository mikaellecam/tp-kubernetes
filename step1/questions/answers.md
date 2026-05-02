# Step 1A — Réponses aux 20 questions Kubernetes / Minikube

> Pour chaque question : la commande utilisée, l'extrait de sortie pertinent, et une capture d'écran de la console (`screenshots/qNN.png`).
>
> Convention de captures : prises depuis le terminal WSL2 Ubuntu après `minikube start --driver=docker --cpus=4 --memory=6g`.

---

## Gestion de Minikube

### (1) Vérifier que Minikube pointe correctement vers le moteur Docker

```bash
minikube profile list
minikube config get driver
docker info --format '{{.Name}} / {{.ServerVersion}}'
```

`Driver` doit valoir `docker`. La commande `minikube docker-env` documente comment réutiliser le daemon Docker interne du nœud Minikube.

![Q1](screenshots/q01.png)

### (2) Quels sont les addons actuellement installés ?

```bash
minikube addons list
```

Liste sous forme de tableau avec colonne `STATUS` (enabled / disabled).

![Q2](screenshots/q02.png)

### (3) Installer un addon intéressant et justifier

On active `metrics-server` :

```bash
minikube addons enable metrics-server
kubectl top nodes
kubectl top pods -A
```

**Justification** : `metrics-server` est l'agrégateur de métriques de ressources de référence dans Kubernetes. Il alimente `kubectl top`, le `HorizontalPodAutoscaler`, et fournit une vue rapide CPU/mémoire complémentaire à ce que Linkerd Viz observera plus tard sur les requêtes HTTP. Empreinte minime, valeur immédiate pour le diagnostic.

![Q3](screenshots/q03.png)

### (4) Lister les profils actifs avec leurs caractéristiques

```bash
minikube profile list
```

Colonnes : `Profile`, `VM Driver`, `Runtime`, `IP`, `Port`, `Version`, `Status`, `Nodes`.

![Q4](screenshots/q04.png)

### (5) Quels sont les profils en cours ?

```bash
minikube profile
```

Affiche le profil actif (par défaut `minikube`).

![Q5](screenshots/q05.png)

### (6) Créer un nouveau profil — qu'est-ce qu'un profil ?

```bash
minikube start -p demo --driver=docker --cpus=2 --memory=2g
minikube profile list
```

Un **profil** Minikube est une instance de cluster Kubernetes isolée : sa propre VM/conteneur, son propre kubeconfig context, ses propres addons, son propre stockage. On les utilise pour faire coexister plusieurs « clusters jouets » sur un même hôte (ex : un profil `dev`, un profil `mesh`, un profil `multi-node`). Le profil actif s'utilise via `minikube -p <nom> ...` ou `minikube profile <nom>`.

```bash
# Nettoyage à la fin de la démonstration
minikube delete -p demo
```

![Q6](screenshots/q06.png)

### (7) Afficher le statut de Minikube

```bash
minikube status
```

Sortie attendue : `host: Running`, `kubelet: Running`, `apiserver: Running`, `kubeconfig: Configured`.

![Q7](screenshots/q07.png)

### (8) Comment accéder au dashboard de Minikube ?

```bash
minikube dashboard --url
# ou simplement :
minikube dashboard
```

`--url` imprime l'URL sans ouvrir de navigateur (pratique en WSL où aucun browser n'est attaché).

![Q8](screenshots/q08.png)

### (9) Qu'est-ce que le Dashboard, que présente-t-il ?

C'est l'**UI web officielle de Kubernetes**. Il agrège dans une seule interface :

- la vue d'ensemble du cluster (nœuds, espaces de noms, événements) ;
- les workloads (Deployments, ReplicaSets, Pods, StatefulSets, DaemonSets, Jobs, CronJobs) ;
- les services et la configuration réseau (Services, Ingresses) ;
- la configuration et le stockage (ConfigMaps, Secrets, PersistentVolumes / PersistentVolumeClaims) ;
- les rôles et politiques RBAC ;
- les logs en direct des conteneurs et l'exécution de commandes (`exec`) dans un pod.

![Q9](screenshots/q09.png)

### (10) Lister les nœuds d'un profil

```bash
kubectl get nodes -o wide
# ou
minikube node list
```

![Q10](screenshots/q10.png)

### (11) Ajouter un nœud à un profil et le supprimer

```bash
minikube node add
minikube node list
minikube node delete m02   # m02 est le nom donné par défaut au second nœud
minikube node list
```

![Q11](screenshots/q11.png)

### (12) Consulter les logs de Minikube

```bash
minikube logs --file=mk.log     # vidange complète vers un fichier
minikube logs -n 200             # 200 dernières lignes seulement
```

![Q12](screenshots/q12.png)

---

## Gestion des Pods et Services sous Kubernetes

### (13) Lister les images en cours d'exécution

```bash
minikube image ls
# ou : ce qui est effectivement chargé dans des pods
kubectl get pods -A -o jsonpath='{..image}' | tr ' ' '\n' | sort -u
```

![Q13](screenshots/q13.png)

### (14) Lancer une image nginx en mode impératif

```bash
kubectl create deployment nginx --image=nginx
kubectl get deployment,pods -l app=nginx
```

![Q14](screenshots/q14.png)

### (15) Créer un Service en mode impératif pour exposer nginx

```bash
kubectl expose deployment nginx --port=80 --type=NodePort
kubectl get svc nginx
```

![Q15](screenshots/q15.png)

### (16) Visualiser les informations du Pod et du Service

```bash
NGINX_POD=$(kubectl get pods -l app=nginx -o jsonpath='{.items[0].metadata.name}')
kubectl describe pod "$NGINX_POD"
kubectl describe svc nginx
```

![Q16](screenshots/q16.png)

### (17) Obtenir l'URL du Service

```bash
minikube service nginx --url
```

![Q17](screenshots/q17.png)

### (18) Exécuter le service dans un browser

```bash
URL=$(minikube service nginx --url)
echo "$URL"
# Soit ouvrir l'URL dans un navigateur Windows :
explorer.exe "$URL"
# Soit en CLI :
curl -sI "$URL"
```

![Q18](screenshots/q18.png)

### (19) Lancer une commande bash dans le conteneur nginx

```bash
NGINX_POD=$(kubectl get pods -l app=nginx -o jsonpath='{.items[0].metadata.name}')
kubectl exec -it "$NGINX_POD" -- /bin/bash
# Remarque : l'image nginx officielle inclut bash. Pour des images plus minimales,
# utiliser : kubectl exec -it "$NGINX_POD" -- /bin/sh
```

![Q19](screenshots/q19.png)

### (20) Lister les logs du conteneur nginx

```bash
NGINX_POD=$(kubectl get pods -l app=nginx -o jsonpath='{.items[0].metadata.name}')
kubectl logs "$NGINX_POD"
kubectl logs -f "$NGINX_POD"   # suivre en continu
```

![Q20](screenshots/q20.png)

---

## Nettoyage

```bash
kubectl delete service nginx
kubectl delete deployment nginx
minikube stop
```
