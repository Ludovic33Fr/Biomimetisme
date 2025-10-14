# Release Notes Automation avec n8n

Cette automatisation n8n permet de publier automatiquement des release notes sur Confluence et d'envoyer des notifications Teams lors de la publication d'une nouvelle version.

## Fonctionnalités

### Déclencheurs
- **GitHub Tag** : Déclenché lors de la création d'un tag `vX.Y.Z`
- **Jira Transition** : Déclenché lors de la transition d'une version vers "Released"

### Workflow
1. **Agrégation des données** : Collecte des issues Jira "Done" et des commits GitHub
2. **Traitement LLM** : OpenAI regroupe par thème, filtre le bruit, crée 3 versions (user-facing, internal, support)
3. **Traduction** : Traduction automatique FR/EN
4. **Publication** : 
   - Page Confluence
   - Pull Request Markdown pour le site de documentation
   - Brouillon d'email
5. **Notification Teams** : Message avec encarts et checklist post-release

### KPIs Trackés
- **Couverture** : Tickets présents vs total
- **Temps de traitement** : Tag → Release notes
- **Taux d'ouverture** : Email de notification

## Structure du projet

```
apps/release-automation/
├── README.md
├── docker-compose.yml
├── n8n/
│   ├── workflows/
│   │   ├── release-notes-workflow.json
│   │   └── kpi-tracking-workflow.json
│   ├── credentials/
│   │   ├── github.json
│   │   ├── jira.json
│   │   ├── openai.json
│   │   ├── confluence.json
│   │   ├── teams.json
│   │   └── smtp.json
│   └── custom-nodes/
│       ├── confluence-publisher.js
│       ├── teams-notifier.js
│       └── kpi-tracker.js
├── templates/
│   ├── release-notes-template.md
│   ├── confluence-template.html
│   └── teams-template.json
└── scripts/
    ├── setup.sh
    └── test-workflow.js
```

## Installation

1. **Prérequis**
   ```bash
   docker-compose up -d
   ```

2. **Configuration des credentials**
   - Copier les fichiers de credentials dans `n8n/credentials/`
   - Configurer les tokens API nécessaires

3. **Import des workflows**
   - Importer `release-notes-workflow.json` dans n8n
   - Importer `kpi-tracking-workflow.json` pour le monitoring

## Configuration

### Variables d'environnement requises
- `GITHUB_TOKEN` : Token GitHub avec accès aux repos
- `JIRA_TOKEN` : Token Jira avec accès aux projets
- `OPENAI_API_KEY` : Clé API OpenAI
- `CONFLUENCE_TOKEN` : Token Confluence
- `TEAMS_WEBHOOK_URL` : URL webhook Teams
- `SMTP_CONFIG` : Configuration SMTP pour les emails

### Paramètres configurables
- **Seuils de couverture** : Pourcentage minimum de tickets couverts
- **Templates** : Personnalisation des templates de release notes
- **Filtres** : Exclusion de certains types de commits/issues
- **Langues** : Langues de traduction supportées

## Utilisation

### Déclenchement automatique
Le workflow se déclenche automatiquement lors de :
- Création d'un tag GitHub `v1.2.3`
- Transition d'une version Jira vers "Released"

### Déclenchement manuel
```bash
curl -X POST http://localhost:5678/webhook/release-trigger \
  -H "Content-Type: application/json" \
  -d '{"version": "v1.2.3", "repository": "owner/repo"}'
```

## Monitoring

### Dashboard KPIs
- Accès via http://localhost:3000/kpis
- Métriques en temps réel
- Historique des performances

### Logs
```bash
docker-compose logs -f n8n
```

## Personnalisation

### Templates
Modifier les templates dans `templates/` pour adapter le format des release notes.

### Nœuds personnalisés
Les nœuds personnalisés dans `custom-nodes/` peuvent être modifiés pour des besoins spécifiques.

## Dépannage

### Problèmes courants
1. **Credentials manquants** : Vérifier la configuration des tokens
2. **Rate limiting** : Ajuster les délais entre les appels API
3. **Templates invalides** : Valider la syntaxe des templates

### Support
- Logs détaillés dans n8n
- Métriques de performance dans le dashboard KPIs
