# Paramètres

**Settings** (Paramètres — barre latérale, ou **g s**) configure l'application web pour tous les utilisateurs de cette
installation. Il vous faut la capacité **Settings** pour l'ouvrir — en général, seuls les administrateurs l'ont. La
plupart des modifications sont enregistrées dès que vous basculez un interrupteur, appuyez sur **Enter** ou quittez un
champ ; les sections dotées d'un bouton **Save** (Enregistrer) sont enregistrées lorsque vous cliquez dessus.

!!! note "Certaines préférences appartiennent à votre navigateur"
    Quelques éléments sont mémorisés par navigateur plutôt que dans Settings : la largeur de la barre latérale et de la
    liste, le repli de la barre latérale, le nombre d'enregistrements **Per page** (Par page), la taille du
    mini-calendrier, la vue de la page Calendar, l'état replié de la barre **Quick note** (Note rapide), les tailles de
    texte des pages de lecture, les périodes d'Insights — et le fait que *vous* receviez ou non les notifications de
    bureau.

## Application { #application }

**App name** (Nom de l'application) est le nom affiché en haut de la barre latérale et dans l'onglet du navigateur
(par défaut *Nibras*).

## Pages et fonctionnalités { #pages-and-features }

**Pages** affiche ou masque les pages d'outils dans la barre latérale : Calendar, Kanban, Dashboards, All bookmarks,
Indicators, Insights, Sprints, Reports, Maintenance, Parameters, Admin, Batch entry, Import files, Import notes, Image
renamer, Static website et Audit log. Settings et Help sont toujours disponibles. Masquer une page la retire seulement
de la barre latérale.

**Features** (Fonctionnalités) désactive des parties de l'interface qui ne sont pas des pages :

| Fonctionnalité | Ce qu'elle contrôle |
|---|---|
| **RSS feeds** (Flux RSS) | Le lien **RSS (bookmarked)** dans la barre latérale. |
| **Standard reports per module** (Rapports standard par module) | Les raccourcis de rapports intégrés dans la barre latérale (vos recherches enregistrées restent). |
| **Release codename** (Nom de code de la version) | Le surnom de la version à côté du numéro de version. |
| **Resource detail fields** (Champs de détail des ressources) | Full text, Highlights, Comments, URL et Read on, sur les ressources. Les masquer ne modifie aucune donnée. |
| **Repository 2** / **Repository 3** (Dépôt 2 / 3) | Les boutons **Repo2** / **Repo3** sur les enregistrements. |
| **New resource from a URL** (Nouvelle ressource depuis une URL) | Le bouton de lien à côté de **New** sur Resources. |

## Notifications { #notifications }

- **Enable desktop notifications (overdue tasks/planners, missing journal entry)** (Activer les notifications de
  bureau : tâches/plans en retard, entrée de journal manquante) — active les notifications de votre navigateur pour
  cette application. La première fois, le navigateur demande l'autorisation. S'il indique **Notifications are blocked
  for this site** (Les notifications sont bloquées pour ce site), autorisez-les dans les paramètres du site du
  navigateur.
- **Journal reminder hour** (Heure du rappel de journal) — après cette heure (format 24 heures, 17 par défaut), vous
  recevez un rappel une fois par jour si vous n'avez pas écrit d'entrée de journal. Cliquez sur **Save**.

Lorsque les notifications sont activées, l'application vérifie toutes les quelques minutes s'il existe des tâches et
des plans **en retard** ou **arrivant à échéance dans la semaine**, et vous relance toutes les 15 minutes lorsqu'aucun
[minuteur](tasks-kanban-sprints.md#time-tracking) ne tourne.

!!! note
    Les notifications ne fonctionnent que lorsque l'application web est ouverte dans un onglet du navigateur.
    L'interrupteur est stocké dans votre navigateur : chaque personne (et chaque navigateur) choisit donc séparément.

## Collecte automatique des indicateurs { #indicator-auto-collection }

**Run time** (Heure d'exécution) est le moment où les indicateurs dont **Auto-collect** (Collecte automatique) est
activé calculent et enregistrent leur valeur chaque jour (23:50 par défaut). Voir
[Objectifs, habitudes et indicateurs](goals-indicators.md#creating-an-indicator).

## Ajout à la description { #append-to-description }

Le **Prefix** (Préfixe) et le **Suffix** (Suffixe) entourent le texte ajouté avec la zone **Append to…** (Ajouter à…)
sous la description ou les notes d'un enregistrement. Écrivez `\n` pour un saut de ligne ; le préfixe par défaut
commence un nouveau paragraphe.

## Modules { #modules }

Le tableau **Modules** liste tous les types d'enregistrements. Pour chacun :

| Colonne | Signification |
|---|---|
| **Module** | Sa clé interne (fixe). |
| **Label** (Libellé) | Le nom affiché dans la barre latérale et dans les listes. |
| **Code** | Un code court de 4 lettres au plus (par ex. `T`) utilisé dans les commandes, les noms de fichiers et dans les applications. Doit être unique. |
| **Color** (Couleur) | La couleur d'accent du module, affichée comme une barre sur les lignes de liste. |
| **Order** (Ordre) | La position dans la barre latérale. |
| **Sync order** (Ordre de synchronisation) | L'ordre dans lequel les paramètres du module sont transmis aux applications mobile et de bureau. |
| **Enabled** (Activé) | Afficher ou masquer le module partout. |
| **Bucket /100**, **Bucket by type** (Regroupement par centaine, par type) | La façon dont les dossiers d'enregistrements du module sont organisés dans les dépôts (regroupés par centaines d'ID, et/ou par type). |

Modifiez **Label**, **Code**, **Order** ou **Sync order**, puis appuyez sur **Enter** ou cliquez sur le bouton **Save**
de la ligne. Les interrupteurs et les couleurs sont enregistrés immédiatement.

!!! warning "Désactiver un module"
    Un module désactivé disparaît de la barre latérale, de la page Today et des listes de types des applications.
    Ses enregistrements ne sont pas supprimés — réactivez-le et ils reviennent. Lorsque **Journal** est désactivé,
    cliquer sur un jour dans le mini-calendrier ne crée plus de journal du jour.

Les statuts, les types et les contextes ne se règlent pas ici mais sur la page
[Parameters](administration.md#parameters).

## Dépôts { #repositories }

Les dossiers du serveur où se trouvent les fichiers des enregistrements :

- **Repo 1 — sandbox / portable**, **Repo 2 — main**, **Repo 3 — dismissed**.
- **Checkout folder — bookmark symlinks** (Dossier de checkout — liens symboliques des favoris) — l'endroit où vont
  les liens vers les dossiers des enregistrements en favori.
- **File-explorer command** (Commande de l'explorateur de fichiers) — le programme que lancent les boutons **Repo** sur
  le bureau du serveur.

Cliquez sur **Save**. Ce sont des chemins sur la machine serveur ; ils sont donc normalement définis une seule fois par
la personne qui l'a installé.

## Notes locales { #local-notes }

**Notes folder** (Dossier de notes) est le dossier de notes Markdown affiché dans
[Local notes](import-capture.md#local-notes) — un chemin sur le serveur ; laissez-le vide pour la valeur par défaut.
L'indication sous le champ précise combien de notes ont été trouvées, ou que le dossier n'existe pas.
**Tag bank file (fallback only)** (Fichier de banque d'étiquettes, solution de repli uniquement) n'est nécessaire que si
vos cours ne portent pas encore de **Note tags**.

## Export { #export }

- **Open the file after "Export to file"** (Ouvrir le fichier après « Export to file ») — ouvrir le fichier de checkout
  sur le bureau du serveur après un export manuel.
- **Open the file after a new calendar-day journal is auto-exported** (Ouvrir le fichier après l'export automatique
  d'un nouveau journal du jour) — la même chose, lorsqu'un clic sur un jour crée un nouveau journal du jour.

## Lors de la mise en favori { #on-bookmarking }

Deux actions facultatives lorsqu'un enregistrement est mis en favori (toutes deux sur **Off** — désactivées — par
défaut) :

- **Create a symbolic link in the checkout folder** (Créer un lien symbolique dans le dossier de checkout) — un lien
  vers le dossier de l'enregistrement dans le dépôt 1, nommé d'après son code de module et son ID (par ex. `W18`), avec
  son texte de checkout écrit à côté.
- **Open the record's checkout text** (Ouvrir le texte de checkout de l'enregistrement) — écrire le fichier texte de
  l'enregistrement et l'ouvrir.

Retirer un favori retire toujours son lien.

## Tâches et autres préférences { #tasks-and-other-preferences }

| Paramètre | Signification |
|---|---|
| **Auto-stop a timer after (minutes)** (Arrêter automatiquement un minuteur après, en minutes) | Durée maximale d'une session de suivi du temps (60 par défaut). |
| **Language codes** (Codes de langue) | Codes séparés par des virgules, utilisables comme étiquettes `'xx` dans les commandes et la recherche (par défaut `ar,en,fr,fa,de`). Voir [Langues](../concepts/languages.md). |
| **Today page: records per column** (Page Today : enregistrements par colonne) | Le nombre d'enregistrements créés et modifiés que chaque module affiche sur Today (5 par défaut, 50 au maximum). |
| **Status on completion** (Statut à l'achèvement) | Le statut que prend une tâche lorsque sa case d'achèvement est cochée — sur l'enregistrement, lors d'une revue de sprint ou depuis le téléphone. **Leave unchanged** (Laisser inchangé) conserve le statut tel quel. |
