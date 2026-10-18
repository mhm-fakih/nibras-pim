# Référence des paramètres

Ouvrez les **Paramètres** avec l'engrenage en bas du rail.

!!! warning "Touchez Enregistrer"
    Les modifications de cette page prennent effet lorsque vous touchez **Enregistrer** en haut à droite. **Retour** quitte sans enregistrer. La seule exception est la **Langue**, qui change dès que vous la touchez.

Les réglages des heures de prière se trouvent sur la page [Heures de prière](prayer.md) elle-même, et les réglages du lecteur dans le [lecteur](player.md#player-settings). Cette page couvre tout le reste, dans l'ordre d'apparition.

## Langue { #language }

**English**, **عربي** ou **Français**. S'applique immédiatement. En arabe, toute l'interface passe de droite à gauche. Vos notes et vos fichiers ne sont jamais traduits.

## Notes { #notes }

| Réglage | Par défaut | Signification |
|---|---|---|
| **Nom de l’application** | knotes | Le titre en haut de la page Notes. |
| **Code de type par défaut (nouvelles notes)** | premier type | La puce de type présélectionnée pour les nouvelles notes. Reste sélectionnée après chaque enregistrement. Vide signifie le premier type de la liste. |
| **Code de catégorie / service par défaut (nouvelles notes)** | première catégorie | La catégorie présélectionnée pour les nouvelles notes. Vide signifie la première catégorie. |
| **Priorité par défaut (nouvelles notes)** | 2 | La priorité présélectionnée pour les nouvelles notes. |
| **Priorité maximale (widgets)** | 4 | La priorité que listent les widgets **Priorité maximale** et **Tâches du jour**, et que compte le widget des indicateurs. Les tâches ajoutées depuis le widget reçoivent cette priorité. |
| **Dossier des notes** | `Notes` à la racine de votre stockage | L'emplacement de vos notes. Utilisez le bouton de dossier pour en choisir un. |
| **Dossier des réponses IA** | dossier des notes + `-ai` | Le dossier des [Réponses IA](ai-answers.md), en lecture seule. Vide utilise la valeur par défaut. |
| **Format d’horodatage** | `yyyy_MM_dd-HHmmss` | La façon d'écrire l'heure au début du nom de fichier d'une nouvelle note. **Maintenant :** affiche un exemple, ou **format non valide**. |
| **Lignes d’aperçu** | 3 | Le nombre de lignes de texte affichées sur chaque carte de note. |
| **Priorité min**, **Priorité max** | 1, 5 | La plage des priorités proposées. |

!!! tip "Les lettres de l'horodatage"
    Dans le format d'horodatage, `HH` désigne l'heure, `mm` les minutes et `MM` le **mois**. Les confondre donne des noms de fichiers étranges. Changer le format n'affecte que les nouvelles notes. Les noms de fichiers existants sont conservés.

## Serveur Nibras { #nibras-server }

Navigateur en lecture seule de votre base de données Nibras. Voir [Synchronisation Nibras](sync.md#connect) pour le détail de chaque champ.

| Réglage | Signification |
|---|---|
| **Protocole** | `http` ou `https` |
| **IP / hôte**, **Port**, **Contexte** | L'adresse du serveur. **URL complète** affiche le résultat. |
| **Nom d’utilisateur**, **Mot de passe** | Votre compte Nibras, utilisé pour se connecter lors de la synchronisation. |
| **Dossier des fichiers** | L'emplacement des pièces jointes des fiches sur le téléphone. |

### Bouton étoile { #star-button }

L'effet de l'étoile sur les fiches du serveur :

- **Favori sur cet appareil** : une sélection privée. Rien n'est envoyé au serveur. (Par défaut.)
- **Retirer des favoris dans Nibras** : toucher l'étoile retire la fiche de vos favoris sur le serveur. Voir [Pages serveur](server-pages.md#bookmark-the-star).

**Taille du texte N×** définit la taille du texte intégral des Ressources (1,5× par défaut).

## Médias { #media }

| Réglage | Signification |
|---|---|
| **Dossiers de la bibliothèque audio** | Les dossiers que parcourt la page [Médias](player.md). Ajoutez-en plusieurs avec **Ajouter un dossier**. |
| **Dossier de la galerie** | Le dossier qu'affiche la [Galerie](gallery.md). |
| **Heure de début du calendrier**, **Heure de fin du calendrier** | Les heures qu'affiche la grille de l'[Agenda](calendar.md) (5 et 22 par défaut). |

## Rappels des plans { #plan-reminders }

Des rappels avant les événements Plans/Journal à venir provenant du serveur. Voir [Rappels des plans](alarms.md#plan-reminders).

| Réglage | Par défaut |
|---|---|
| **Délais (minutes)** | `60, 5` |
| **Son de rappel** (**Choisir un son**) | Par défaut |
| **Exiger l’arrêt** | Désactivé |
| **Répéter le son (fois)** | 4 |

## Synchronisation en arrière-plan { #background-sync }

| Réglage | Par défaut |
|---|---|
| **Synchroniser avec Nibras** | Activé |
| **Vérifier toutes les (s)** | 30 |
| **Synchroniser toutes les (s)** | 300 |

Voir [Synchronisation en arrière-plan](sync.md#background-sync).

## État et carillon { #status-chime }

| Réglage | Par défaut | Signification |
|---|---|---|
| **Notification permanente** | Activé | Une notification continue avec le nombre de notes du jour. Elle maintient aussi l'application active pour que les alarmes sonnent à l'heure. |
| **Mini-calendrier** | Activé | La bande de deux semaines en haut de la page Agenda. |
| **Widget des notes récentes** | 3 derniers jours, 40 mots, taille du texte 15 sp | La période couverte par le [widget Notes récentes](widgets.md#recent-notes), le nombre de mots avant **Lire la suite**, et sa taille de texte. |
| **Les heures de prière sont masquées** | (affiché uniquement lorsque désactivé) | Fait revenir la page Heures de prière après qu'elle a été désactivée sur cette page. |
| **Collecte de fichiers** | Activé | La page Collecte et son exécution quotidienne. Désactivé, les deux disparaissent. |
| **Carillon toutes les 15 minutes** | Activé | Un carillon à :00, :15, :30 et :45. |
| **Choisir le fichier du carillon** | carillon intégré | N'importe quel fichier audio. |
| **Heure de début du carillon**, **Heure de fin du carillon** | 8, 22 | La plage horaire pendant laquelle le carillon sonne. |

## Collecte de fichiers { #file-collection }

Voir [Collecter des fichiers](collect.md).

| Réglage | Signification |
|---|---|
| **Dossiers sources** | Les dossiers dans lesquels rassembler les fichiers. Décochez un dossier pour l'ignorer sans le retirer. |
| **Dossier de destination (New)** | L'endroit où les collections sont écrites. Votre dossier de notes par défaut. |
| **Collecter automatiquement chaque jour** | Lance la collecte toute seule chaque jour, **À** l'heure que vous choisissez (15:00 par défaut). Les fichiers sont déplacés. |

## Types et catégories { #types-and-categories }

Les listes des types et catégories de notes, chaque entrée ayant un **Code** court et un **Nom**.

- **Types** : un type par note. Le code vient en premier dans le nom du fichier.
- **Catégories** : une catégorie par note. Le code vient en dernier dans le nom du fichier.

Utilisez **Ajouter un type** / **Ajouter une catégorie** pour en ajouter un et l'icône de corbeille (**Supprimer**) pour en supprimer un. Gardez des codes courts (une ou deux lettres). Les caractères `~ / \ : * ? " < > |` ne peuvent pas être utilisés.

Les valeurs par défaut sont les types `W` Work, `H` Home, `I` Ideas, `T` Todo et les catégories `P` Personal, `W` Work, `M` Misc.

!!! note "Lorsque vous êtes connecté à Nibras"
    Avec un serveur Nibras configuré, les deux listes sont remplacées par les modules et services du serveur au démarrage de l'application et chaque fois que vous touchez **Actualiser les notes + synchroniser**, afin que le téléphone et le serveur utilisent les mêmes codes. Les modifications faites ici peuvent alors être écrasées.

Supprimer un code ne modifie pas les notes existantes. Les notes qui l'utilisent encore apparaissent sous le filtre **Classement invalide** de la page Notes.

## Visite de configuration { #setup-tour }

**Afficher la visite de configuration** repasse sur les dossiers, en expliquant le rôle de chacun.
