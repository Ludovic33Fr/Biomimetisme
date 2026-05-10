# myco-ioc — tests

Smoke test d'intégration : vérifie que le flux nominal **détection → quorum → ioc.share → activeIOCs** fonctionne sur une stack docker-compose courante.

## Lancement

Prérequis : la stack tourne (`docker compose up -d --scale node=4` depuis `apps/myco-ioc/`).

```bash
cd apps/myco-ioc/tests
npm install
npm test
```

À lancer depuis WSL (ou tout host qui voit `nats://localhost:4222` et `http://localhost:3000`).

## Ce qui est testé

1. Le controller répond sur `GET /api/state` et expose au moins un node (timeout 10 s, le temps des `nodes.hello`).
2. Un burst de 25 événements `traffic.http` (path `/wp-login.php`, status 401) sur la même IP source vers un node existant déclenche un IOC partagé visible dans `state.activeIOCs` en **moins de 5 secondes**.

## Variables d'env

- `NATS_URL` — défaut `nats://localhost:4222`
- `CONTROLLER_URL` — défaut `http://localhost:3000`
