# Calendrier

Nibras propose deux calendriers : le petit, dans la barre latérale de chaque module, pour accéder
rapidement à un jour, et la page complète **Calendar** (Calendrier), pour planifier votre semaine.

## Le mini-calendrier { #the-mini-calendar }

Dans toute vue de module, la barre latérale affiche un calendrier compact sous la zone de recherche.

- Par défaut, il affiche **Last 2 weeks** (2 dernières semaines : la semaine dernière et celle-ci,
  en commençant le lundi). Cliquez sur la flèche de dépliage pour voir **the full month** (le mois
  entier) avec les numéros de semaine ISO ; votre choix est mémorisé.
- Un petit **point** sous une date signifie que des fiches du module courant ont été créées ce jour-là.
- Une date en **gras** possède une entrée de journal.
- Le jour courant est entouré.

**Cliquer sur une date** ouvre le **journal récapitulatif** de ce jour dans le module Journal — et le
crée s'il n'existe pas encore, de sorte qu'il y en a toujours exactement un par jour pour écrire. Si
le module Journal est désactivé, cliquer sur une date ne crée rien ; le volet central liste alors
toutes les fiches de ce jour dans vos modules activés.

!!! tip "Astuce"
    Pour filtrer la liste courante sur un seul jour sans ouvrir de journal, utilisez plutôt le
    filtre **On day** (Le jour) en haut de la liste.

## La page Calendrier { #the-calendar-page }

Ouvrez **Calendar** depuis la barre latérale (ou appuyez sur **g c**). Elle affiche une semaine (ou un
jour) d'entrées **Journal** et **Planner** (Planificateur), ainsi que vos **sprints**, sur une grille
horaire.

### Se déplacer { #moving-around }

- **‹** et **›** reculent ou avancent d'une semaine (ou d'un jour) ; **Today** (Aujourd'hui) y revient.
- L'en-tête affiche la plage de dates et le numéro de **semaine ISO**.
- Le calendrier mensuel à droite permet d'aller à n'importe quelle date.
- Une ligne rouge indique l'heure actuelle.

### Ajuster l'affichage { #adjusting-the-view }

| Contrôle | Choix |
|---|---|
| **Week** (Semaine) / **Day** (Jour) | Sept jours à partir du lundi, ou un seul jour |
| **Slot** (Créneau) | 15, 30 ou 60 minutes par ligne |
| **From** (De) / **To** (À) | La première et la dernière heure affichées (par défaut 07:00–21:00) |

Ces réglages sont mémorisés par votre navigateur.

### Apparence des entrées { #how-entries-look }

Chaque entrée est colorée selon son **type** (les couleurs se définissent sur la page
[Parameters](administration.md#parameters)). La bordure distingue les modules : les entrées du
**planificateur** ont une bordure en tirets, celles du **journal** une bordure pleine, et les
**sprints** une bordure double. Survolez une entrée pour voir son heure et sa description ; les plans
récurrents sont marqués **⟳**.

### Ajouter une entrée { #adding-an-entry }

1. Cliquez sur un créneau — ou appuyez et faites glisser vers le bas sur plusieurs créneaux pour
   choisir une plage horaire.
2. Dans **New calendar entry** (Nouvelle entrée de calendrier), choisissez le **Module** (**Planner**
   ou **Journal**) et éventuellement un **Type**. Le dernier module utilisé est mémorisé.
3. Saisissez un **Summary** (Résumé) et une **Description** facultative ; ajustez **Start** (Début)
   et **End** (Fin) si nécessaire.
4. Facultativement, utilisez **Link to goal** (Lier à un objectif) ou **Link to task** (Lier à une
   tâche) (ceux qui sont mis en favori sont proposés).
5. Cliquez sur **Create** (Créer).

Les entrées créées ici sont automatiquement mises en favori.

### Modifier une entrée { #changing-an-entry }

- **Cliquez** sur une entrée pour l'ouvrir dans la page de lecture dans un nouvel onglet (un sprint
  ouvre la page [Sprints](tasks-kanban-sprints.md#sprints)). Utilisez-y **Edit** (Modifier) pour la
  changer.
- **Faites glisser le bord supérieur ou inférieur** d'une entrée pour changer son heure de début ou
  de fin.

Les utilisateurs en lecture seule peuvent consulter, mais pas ajouter, redimensionner ni supprimer.

## Plans récurrents { #repeating-plans }

Une entrée du planificateur dont l'interrupteur **Repeats** (Se répète) est activé est une *série* :
le calendrier dessine chaque occurrence à la même heure de la journée sans les enregistrer une à une.
Une occurrence ne devient une fiche à part entière que lorsque vous l'ouvrez, la déplacez ou
l'annotez — ainsi, une année d'habitude quotidienne ne coûte rien tant que vous n'y touchez pas.

Pour supprimer une seule occurrence, survolez-la et cliquez sur le **×** dans son coin (**Remove just
this occurrence** — Supprimer uniquement cette occurrence). Pour modifier ou terminer toute la série,
modifiez la fiche d'origine du planificateur — voir
[Tâches et plans récurrents](records.md#repeating-tasks-and-plans).

## Calendriers ailleurs { #calendars-elsewhere }

- Un [tableau de bord](dashboards.md) peut inclure une région **Calendar (filtered by type)**
  (Calendrier filtré par type) — par exemple uniquement les événements « famille » — à afficher sur
  un écran inutilisé.
- Les entrées du planificateur dont la date de fin est passée, ou proche, apparaissent dans les
  rapports **Overdue** (En retard) et **Due soon (7d)** (Échéance proche, 7 j) du module Planner, et
  dans les [notifications du bureau](settings.md#notifications) si vous les activez.
