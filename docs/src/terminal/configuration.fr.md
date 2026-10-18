# Configuration

Tous les réglages se trouvent dans `config.json`, **à côté du programme**. Il est créé au premier lancement —
copié depuis les réglages de knotes desktop s'ils existent — et l'application y réécrit lorsque vous changez de
dossier ou de thème. Modifiez-le avec n'importe quel éditeur de texte pendant que l'application est fermée.

## Exemple { #example }

```json
{
  "preview_chars": 60,
  "default_repo": "P",
  "default_type": "N",
  "default_priority": 2,
  "default_subject": "Z",
  "theme": "solarized-light",
  "repos": {
    "P": { "path": "~/Knotes", "backend": "https://nibras.example.com" },
    "W": { "path": "~/Work-notes", "backend": "https://work.example.com" }
  }
}
```

## Réglages { #settings }

| Clé | Ce qu'elle fait | Par défaut |
|---|---|---|
| `repos` | Vos dossiers de notes. Chacun a un code d'une lettre, un `path` et son propre `backend` (adresse du serveur) | un dossier **A** dans `~/knotes` |
| `default_repo` | Code du dossier courant. Mis à jour automatiquement quand vous nommez un dossier dans une ligne de note | le premier dossier |
| `default_type` | Type des notes sans code d'étiquette | `N` |
| `default_priority` | Priorité des notes sans code d'étiquette | `2` |
| `default_subject` | Catégorie des notes sans code d'étiquette | `Z` |
| `theme` | `solarized-light` ou `solarized-dark` — ou utilisez `/theme` | `solarized-light` |
| `preview_chars` | Longueur des aperçus de notes | `60` |

Les codes de dossier doivent être d'une seule lettre — c'est ce qu'une ligne de note peut désigner
(voir [Écrire des notes](writing-notes.md)).

!!! tip "Un dossier, plusieurs applications"
    Faites pointer le `path` d'un dossier vers le même dossier que celui de knotes desktop ou de votre téléphone.
    Toutes lisent et écrivent les mêmes noms de fichiers, de sorte que les notes apparaissent partout. Voir
    [Synchronisation](../concepts/sync.md).

## Variables d'environnement { #environment-variables }

| Variable | Effet |
|---|---|
| `NIBRAS_URL` | Utilise cette adresse de serveur pour **tous** les dossiers pendant ce lancement, p. ex. `NIBRAS_URL=https://test.example.com ./run.sh`. Pratique pour les tests |
| `NIBRAS_RESHAPE=1` | Met en forme et réordonne le texte arabe dans l'application, pour les terminaux qui ne le font pas |

!!! warning "Attention"
    Si vous changez de dossier ou de thème pendant que `NIBRAS_URL` est défini, l'application enregistre sa
    configuration — et avec elle l'adresse de serveur substituée, pour chaque dossier. Vérifiez `config.json`
    ensuite.

## Autres fichiers { #other-files }

| Fichier | Rôle |
|---|---|
| `~/.config/knotes/tokens.json` | Vos connexions, une par serveur. Supprimez-le pour vous déconnecter partout |
| `.venv/` | L'environnement Python privé que crée `run.sh`. Supprimez-le pour réinstaller les paquets |
