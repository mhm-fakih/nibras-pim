# Comptes, permissions et administration

Cette page s'adresse aux personnes qui gèrent les **utilisateurs** et le **vocabulaire** d'une installation Nibras :
les administrateurs et, pour la page Parameters, les éditeurs de paramètres. Si une page décrite ici n'apparaît pas
dans votre barre latérale, c'est que vous ne disposez pas de la capacité qu'elle requiert.

## Rôles et capacités { #roles-and-capabilities }

Chaque utilisateur a un seul **rôle**. Ce qu'un rôle peut faire est un ensemble de **capacités** :

| Capacité | Vous permet de |
|---|---|
| **Write content** (Écrire du contenu) | Créer, modifier, supprimer et importer des enregistrements ; utiliser Batch entry, Import files, Import notes, Image renamer, Static website, Maintenance et Trash. |
| **Write parameters** (Écrire les paramètres) | Modifier les statuts, les types et les contextes sur la page **Parameters** (Paramètres). |
| **Manage users** (Gérer les utilisateurs) | Utiliser les pages **Users** (Utilisateurs) et **Roles & permissions** (Rôles et permissions). |
| **Admin tools** (Outils d'administration) | Utiliser la page **Admin**. |
| **Settings** (Paramètres) | Utiliser la page **Settings**. |
| **Audit log** (Journal d'audit) | Voir la page **Audit log** et l'**History** (Historique) de chaque enregistrement. |

Toute personne connectée peut lire, rechercher et parcourir tous les modules ainsi que les pages de lecture (Today,
Calendar, Kanban, Dashboards, Insights, Sprints, Reports, Bookmarks, Highlights, Flash cards, Local notes).

Les quatre rôles sont configurés au départ comme suit :

| Rôle | Capacités par défaut |
|---|---|
| **Admin** (Administrateur) | Tout — toujours, et cela ne peut pas être réduit |
| **Data entry** (Saisie de données) | Write content |
| **Parameters data entry** (Saisie des paramètres) | Write parameters |
| **Read only** (Lecture seule) | Aucune — lecture uniquement |

### Modifier ce qu'un rôle peut faire { #changing-what-a-role-can-do }

Ouvrez **Roles & permissions** (barre latérale). La grille affiche les rôles sur le côté et les capacités en haut.
Cochez ou décochez une case — la modification est enregistrée et prend effet immédiatement. La ligne **Admin** est
verrouillée.

## Utilisateurs { #users }

Ouvrez **Users** pour voir le **Username** (Nom d'utilisateur), le **Name** (Nom), le **Role** (Rôle) et le **Status**
(Statut — **Active** / **Disabled**, Actif / Désactivé) de chacun.

- **Add user** (Ajouter un utilisateur) — saisissez un **Username**, un **Display name** (Nom affiché) facultatif, un
  **Role**, un **Password** (Mot de passe), et laissez **Active** activé. Cliquez sur **Save** (Enregistrer).
- **Edit** (Modifier, crayon) — modifiez n'importe lequel de ces éléments. Laissez **New password (leave blank to
  keep)** (Nouveau mot de passe, laisser vide pour conserver) vide pour conserver le mot de passe actuel.
- **Désactivez** quelqu'un en désactivant **Active** — la personne ne peut plus se connecter, mais son historique reste
  intact.
- **Delete** (Supprimer, corbeille) supprime le compte après confirmation.

!!! tip
    Donnez à chaque personne son propre compte. Le journal d'audit enregistre *qui* a modifié quoi, ce qui n'est utile
    que si les comptes ne sont pas partagés.

## Paramètres { #parameters }

**Parameters** est l'endroit où se trouve le vocabulaire partagé : les **statuts**, les **types** et les **contextes**
qui servent à classer les enregistrements, par module. (Les départements et les cours sont des modules à part entière ;
voir [Modules et enregistrements](records.md).)

Pour chaque liste :

- Tapez un nom dans **New value…** (Nouvelle valeur) et un **code** court, puis cliquez sur **Add value** (Ajouter la
  valeur). Les codes sont ce que vous tapez dans les [commandes](batch-commands.md) — `?done`, `#art`, `@home`.
- **Double-cliquez** sur une valeur (ou utilisez **Rename** — Renommer) pour la renommer.
- Cliquez sur l'échantillon de couleur (**Recolour** — Recolorer) pour choisir sa couleur. Les couleurs sont utilisées
  pour les pastilles et dans le [Calendrier](calendar.md).
- Cliquez sur le ★ (**Set as default for blank records** — Définir par défaut pour les enregistrements vides) pour faire
  d'une valeur la valeur par défaut : les nouveaux enregistrements laissés vides la reçoivent.
- **Remove this value** (Supprimer cette valeur) la supprime après confirmation ; les enregistrements existants
  continuent de l'afficher jusqu'à ce qu'ils soient modifiés.
- **Fill empty colours** (Remplir les couleurs vides) donne une couleur à toutes les valeurs non colorées en une seule
  fois.

## Page Admin { #admin-page }

**Admin** (titre **Administration**) donne une vue d'ensemble des données :

- Une carte par module avec son **nombre d'enregistrements** et, pour chaque champ de classement, combien des valeurs
  disponibles sont réellement **utilisées**.
- **Rebuild search index** (Reconstruire l'index de recherche) — reconstruit l'index utilisé par la
  [recherche globale](search.md#global-search). Lancez-le une fois sur une nouvelle installation, ou si les résultats
  de recherche semblent incomplets. La progression et une ligne de résultat sont affichées.
- **Synced to clients** (Synchronisé vers les clients) — exactement ce que les applications mobile, terminal et de
  bureau devraient proposer : les départements de priorité 2 et plus, et les types de chaque module activé dans leur
  ordre de synchronisation. Comparez avec un appareil pour repérer une copie périmée. Voir
  [Synchronisation](../concepts/sync.md).

## Journal d'audit { #audit-log }

**Audit log** liste chaque modification de chaque enregistrement : les insertions, chaque champ mis à jour (ancien →
nouveau) et les suppressions, avec la date et l'auteur. Affinez-le par **Module**, **Operation** (Opération —
**Insert**, **Update**, **Delete** : insertion, mise à jour, suppression), **Field** (Champ), et dates **From** / **To**
(Du / Au). Utilisez **Previous** / **Next** (Précédent / Suivant) pour parcourir les pages.

Le même historique pour un seul enregistrement apparaît dans son volet de détail, sous **History**.

## Corbeille { #trash }

**Trash** (Corbeille) liste les enregistrements qui ont été supprimés — ou absorbés par une fusion (**Merged → #id**,
Fusionné) — dans tous les modules. Cliquez sur l'icône de restauration pour en remettre un là où il était. Voir
[Supprimer et restaurer](records.md#deleting-and-restoring).

## Maintenance { #maintenance }

**Maintenance** montre où part l'espace de stockage et propose deux façons d'en récupérer. Les deux fonctionnent de la
même manière : un tableau classé, une case à cocher par ligne, **Select all** / **Clear** (Tout sélectionner /
Effacer), et un bouton qui indique ce qu'il va récupérer avant que vous ne cliquiez dessus.

=== "Dossiers du dépôt 1"

    **Heaviest repository 1 folders** (Dossiers les plus lourds du dépôt 1) liste les enregistrements ayant le plus de
    fichiers dans le dépôt de travail. Cochez-en quelques-uns et cliquez sur **Move n to repo 2 — reclaims …**
    (Déplacer n vers le dépôt 2 — récupère …) pour déplacer leurs fichiers vers le dépôt d'archives. Les couvertures et
    les fichiers de checkout restent en place. **Rescan** (Réanalyser) actualise la liste.

=== "Texte intégral des ressources"

    **Longest resource full text** (Textes intégraux de ressources les plus longs) liste les ressources dont le texte
    stocké est le plus volumineux. Cochez-en quelques-unes et cliquez sur **Move n to file — … out of the database**
    (Déplacer n vers un fichier — … hors de la base de données). Chaque texte est écrit dans un fichier du dossier de la
    ressource dans le dépôt 1, vérifié, puis seulement ensuite effacé de l'enregistrement. Le texte n'apparaît plus
    dans la recherche d'enregistrements, mais il reste sur le disque. Un fichier existant du même nom est mis de côté,
    jamais écrasé.
