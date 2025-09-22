# Biomimetisme
Support pour l'application de la détection propagation d'IOC via réseau mycorhizien

## Plan de l'application
myco-ioc/
├─ docker-compose.yml
├─ node/            # service "arbre"
│  ├─ Dockerfile
│  └─ src/index.ts
├─ controller/
│  ├─ Dockerfile
│  └─ src/index.ts
├─ traffic/
│  ├─ Dockerfile
│  └─ src/index.ts
├─ dashboard/       # Next.js
│  ├─ Dockerfile
│  └─ app/page.tsx  (ou pages/index.tsx si Next<13)
└─ natsbox/         # (optionnel) nats cli container

## Exemple d'usage 
Scale des nodes à la volée : docker compose up -d --scale node=7

