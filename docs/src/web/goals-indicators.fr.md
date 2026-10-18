# Objectifs, habitudes et indicateurs

Les **objectifs** disent vers quoi vous tendez ; les **indicateurs** mesurent des choses dans le
temps — d'une habitude que vous consignez à la main à un nombre que le serveur calcule pour vous
chaque nuit.

## Objectifs { #goals }

Un objectif est une fiche comme les autres (voir [Modules et fiches](records.md)), avec un **Code**,
un **Status** (Statut), un **Type**, un **% complete** (% d'achèvement), et des dates **Start**
(Début), **End** (Fin) et **Completed** (Terminé).

Ce qui rend les objectifs particuliers :

- **Mettez en favori** les objectifs que vous poursuivez activement. Seuls les objectifs en favori
  sont proposés dans le sélecteur **Goal** (Objectif) d'une tâche, sur le tableau
  [Kanban](tasks-kanban-sprints.md#kanban) et dans la zone **Link to goal** (Lier à un objectif) du
  calendrier.
- Le volet de détail d'un objectif liste ses tâches sous **Related** (Liés), et comporte une zone
  **+ Add a task to this goal…** (+ Ajouter une tâche à cet objectif…) — saisissez un résumé et
  appuyez sur **Enter**.
- Cliquez sur le cercle **✓** pour marquer l'objectif comme terminé (cela retire aussi son favori).

## Suivi d'habitude sur un objectif { #habit-tracking-on-a-goal }

Un objectif peut porter une ou plusieurs **habitudes** — des indicateurs quotidiens qui lui sont liés.

1. Ouvrez l'objectif.
2. S'il n'a pas encore d'habitude, cliquez sur **Track this goal** (Suivre cet objectif). Cela ajoute
   un indicateur d'habitude quotidien lié à l'objectif.
3. Chaque jour, saisissez une **Value** (Valeur) (et éventuellement une **Date** et une **Note** —
   *What did you do today?*, « Qu'avez-vous fait aujourd'hui ? »), puis cliquez sur **Log**
   (Consigner). Utilisez **Log another** (Consigner une autre) pour d'autres entrées le même jour.

L'objectif affiche alors une carte thermique de vos jours, colorée selon la quantité consignée —
rouge pour aucune entrée, orange pour un peu, des verts pour davantage — avec un journal jour par jour
en dessous.

!!! tip "Afficher les habitudes sur un écran"
    Activez **Show habit heatmap on dashboards** (Afficher la carte thermique des habitudes sur les
    tableaux de bord) dans le formulaire de l'objectif, puis ajoutez une région **Goals habit
    heatmap** (Carte thermique des habitudes des objectifs) à un [tableau de bord](dashboards.md).

## Indicateurs { #indicators }

Ouvrez **Indicators** (Indicateurs) depuis la barre latérale (ou **g i**). La page comporte quatre
parties :

1. **KPI cards** (Cartes d'indicateurs clés) — des chiffres standard, visibles d'un coup d'œil, sur
   l'ensemble de vos fiches.
2. **Needs data** (Données requises) — les indicateurs surveillés qui n'ont pas encore de valeur pour
   la période en cours (jour, semaine…). Chaque carte affiche la dernière valeur et sa date ; saisissez
   une valeur (ou acceptez la **Proposed value** — Valeur proposée) et cliquez sur **Confirm**
   (Confirmer).
3. **Monitored indicators** (Indicateurs surveillés) — un graphique linéaire par indicateur. Les
   indicateurs qui partagent une même **Category** (Catégorie) sont tracés ensemble sur un seul
   graphique.
4. **All indicators** (Tous les indicateurs) — chaque indicateur défini ; ★ marque ceux qui sont
   surveillés. Cliquez sur l'un d'eux pour le modifier.

### Créer un indicateur { #creating-an-indicator }

Cliquez sur **New indicator** (Nouvel indicateur) et renseignez :

| Champ | Signification |
|---|---|
| **Name** (Nom), **Code**, **Description** | Ce qu'il mesure |
| **Frequency (daily / weekly)** (Fréquence : quotidienne / hebdomadaire) | La fréquence à laquelle une valeur est attendue |
| **Scale / unit** (Échelle / unité) | p. ex. *minutes*, *pages*, *kg* |
| **Category** | Un code court ; les indicateurs de même catégorie partagent un graphique |
| **Colour** (Couleur) | Couleur de la courbe (remplie automatiquement si vide) |
| **Actively monitored** (Surveillé activement) | Le suivre sur cette page et dans **Needs data** |
| **Goal** | Le lier à un objectif pour l'utiliser comme suivi d'habitude |

Un indicateur obtient ses valeurs de l'une de quatre façons, affichée sous forme de pastille sur
chaque carte :

| Genre | Origine de la valeur |
|---|---|
| **MANUAL** | Vous la saisissez. |
| **QUERY** | Une requête sur vos fiches, définie dans le champ **Query** (Requête). |
| **FILE** | Le nombre de fichiers dans un dossier du serveur (**Folder path** — Chemin du dossier, filtré par **Extensions**). |
| **SCRIPT** | Une petite commande shell sur le serveur qui affiche un nombre. |

Pour les indicateurs QUERY, FILE et SCRIPT, l'application **propose** une valeur et attend que vous la
confirmiez. Activez **Auto-collect** (Collecte automatique) pour que le serveur calcule et enregistre
la valeur de lui-même une fois par jour, à l'heure définie dans
[Settings → Indicator auto-collection](settings.md#indicator-auto-collection).

!!! note "Remarque"
    Les indicateurs de type requête, dossier et script s'exécutent sur le serveur ; ils sont donc
    généralement configurés par la personne qui s'en occupe.
