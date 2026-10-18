# Commandes

Une ligne qui commence par `/` est une commande. Pendant la saisie, la barre de commande suggère en gris la commande
correspondante — appuyez sur **Tab**, **→** ou **End** pour l'accepter.

## Référence des commandes { #command-reference }

| Commande | Aussi | Ce qu'elle fait |
|---|---|---|
| `/help` | `/h`, `/?` | Affiche le format d'une ligne de note et la liste des commandes |
| `/repos` | `/repo` | Actualise le tableau de bord des dossiers de notes |
| `/theme` | | Affiche le thème actuel |
| `/theme light` | `/theme l` | Passe au thème clair (mémorisé pour la prochaine fois) |
| `/theme dark` | `/theme d` | Passe au thème sombre (mémorisé pour la prochaine fois) |
| `/heartbeat` | | Vérifie si le serveur du dossier courant est joignable |
| `/login <user> <password>` | | Se connecte au serveur du dossier courant |
| `/tasks` | | Liste les 20 tâches mises à jour le plus récemment |
| `/goals` | | … objectifs |
| `/journals` | | … journaux |
| `/planners` | | … plannings |
| `/resources` | | … ressources |
| `/notes` | | … notes |
| `/writings` | | … écrits |
| `/quit` | `/exit`, `/q` | Ferme l'application |

Les commandes qui listent des enregistrements nécessitent d'abord une connexion — voir
[Commandes serveur](server.md).

## Préfixes de ligne { #line-prefixes }

| Commence par | Signification |
|---|---|
| `/` | Une commande (ci-dessus) |
| `+` | Enregistre la note **et** l'envoie au serveur — voir [Écrire des notes](writing-notes.md#send-a-note-to-the-server-the-prefix) |
| autre chose | Enregistre une note — voir [Écrire des notes](writing-notes.md) |

## Touches { #keys }

| Touche | Action |
|---|---|
| **Enter** | Enregistre la note ou exécute la commande |
| **↑** / **↓** | Parcourt les lignes saisies pendant cette session |
| **Tab**, **→**, **End** | Accepte la commande suggérée |
| **Ctrl+C**, **Ctrl+Q** | Ignorées exprès — tapez `/quit` pour quitter |

La barre de commande garde toujours le focus clavier : vous pouvez taper à tout moment.
