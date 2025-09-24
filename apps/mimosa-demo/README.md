# Mimosa pudica → EDR/IPS inline (Demo: Linux + Docker + Kubernetes)

This demo shows a reflex-style **inline block** and **endpoint kill** analogous to *Mimosa pudica* closing its leaves upon touch.

Two paths are provided:

1) **Docker Compose**: NGINX proxy with a simple IPS-like drop (regex) + Flask webapp + **Falco** (eBPF) that kills spawned shells.
2) **Kubernetes**: Same idea using Deployments/ConfigMaps + a **Falco DaemonSet** with an action script.

> Educational demo only — do **not** run on production nodes.

---

## Prereqs

- Linux host with Docker or containerd
- Docker Compose v2 (`docker compose`)
- Kubernetes cluster + `kubectl` (for the k8s path)
- Kernel that supports eBPF (Falco runs with `--modern-bpf`)

---

## 1) Docker path

### Start
```bash
cd docker
docker compose up --build -d
