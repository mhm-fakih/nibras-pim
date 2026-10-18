# Tâches, Kanban et sprints

C'est dans les tâches que le travail se fait. Cette page présente les fonctions propres aux
tâches, le tableau **Kanban** qui affiche les tâches sous leurs objectifs, le **suivi du temps**, et
les **Sprints** pour s'engager sur un bloc de travail.

## Les tâches en bref { #tasks-in-brief }

En plus des champs communs, une tâche possède :

| Champ | Utilité |
|---|---|
| **Status** (Statut) | Où elle en est (votre propre liste de statuts — voir [Paramètres](administration.md#parameters)). |
| **Goal** (Objectif) | L'objectif en favori qu'elle sert. |
| **% complete** (% achevé) | L'avancement. |
| **Planned duration (min)** (Durée prévue, en min) | Votre estimation en minutes ; les sprints additionnent ces valeurs. |
| **Start** (Début), **End** (Fin) | Des dates. Une tâche dont la date de fin est dépassée apparaît dans le rapport **Overdue** (En retard). |
| **Urgent** | Un indicateur distinct de la priorité. |
| **Order #** (N° d'ordre) | La position parmi les tâches d'un objectif. |
| **Repeats** (Répétition) | Voir [Tâches répétitives](records.md#repeating-tasks-and-plans). |
| **People** (Personnes) | Les contacts concernés par la tâche (lorsque Contacts est activé). |

### Terminer une tâche { #completing-a-task }

Cliquez sur le cercle **✓** dans le volet de détail (**Click to mark complete** — Cliquer pour
marquer comme terminée). Cela enregistre la date d'achèvement et retire la tâche des favoris. Si
votre administrateur a défini un **Status on completion** (Statut à l'achèvement) dans les
[Paramètres](settings.md#tasks-and-other-preferences), la tâche passe aussi à ce statut. Cliquez à
nouveau pour la rouvrir.

### Relier des tâches à des objectifs { #linking-tasks-to-goals }

Choisissez un objectif dans le sélecteur **Goal** du formulaire de tâche (seuls les objectifs en
favori sont proposés). Vous pouvez aussi ajouter des tâches directement depuis un objectif : ouvrez
l'objectif, tapez dans **+ Add a task to this goal…** (+ Ajouter une tâche à cet objectif…), puis
appuyez sur **Enter**.

Une tâche qui a un objectif obtient un bouton **+ Subtask** (+ Sous-tâche). Il demande un
**Summary** (Résumé) et crée une nouvelle tâche sur le même objectif, placée juste après sa tâche
parente.

## Suivi du temps { #time-tracking }

Dans le volet de détail d'une tâche :

1. Cliquez sur **Track** (Chronométrer) pour démarrer une session. Une horloge en cours s'affiche.
2. Si vous le souhaitez, tapez un **Summary (optional)** (Résumé, facultatif) de ce que vous avez
   fait.
3. Cliquez sur **Stop** (Arrêter), ou appuyez sur **Enter** dans la zone de résumé.

L'arrêt crée une entrée de **Journal** liée à la tâche, couvrant la session — elle apparaît dans
les enregistrements **Related** (Liés) de la tâche et dans le calendrier. Démarrer un chronomètre
sur une autre tâche arrête et enregistre d'abord celui en cours.

!!! note "Chronomètres oubliés"
    Une session s'arrête d'elle-même au bout d'une durée maximale (60 minutes, sauf modification
    dans les [Paramètres](settings.md#tasks-and-other-preferences)), et est enregistrée comme se
    terminant à cette limite. Avec les [notifications du bureau](settings.md#notifications)
    activées, l'application vous le rappelle aussi toutes les 15 minutes lorsqu'aucun chronomètre
    ne tourne.

## Kanban { #kanban }

**Kanban** (barre latérale, ou **g k**) affiche les **objectifs en favori et leurs tâches**, avec
une colonne par **département**.

- Chaque colonne liste ses **Goals** (Objectifs), les tâches ouvertes de chaque objectif étant
  imbriquées sous la carte de l'objectif.
- **Tasks — no goal** (Tâches — sans objectif) liste les tâches de ce département qui ne sont
  rattachées à aucun objectif affiché.
- Les tâches apparaissent si elles sont en favori *ou* si elles appartiennent à un objectif en
  favori.
- Les tâches terminées restent 7 jours, barrées, puis disparaissent.
- Dans une liste, les tâches ouvertes viennent en premier, puis par priorité et de la plus récente
  à la plus ancienne.

Cliquez sur une carte pour aller à son module. Si le tableau est vide, mettez quelques objectifs ou
tâches en favori.

!!! tip "Gardez les tâches rattachées aux objectifs"
    Le tableau est conçu pour faire ressortir les tâches sans objectif. Relier une tâche à un
    objectif la déplace sous la carte de cet objectif.

## Sprints { #sprints }

Un **sprint** est un bloc daté de travail auquel vous vous engagez — disons *mardi matin* — puisé
dans vos tâches en favori. Ouvrez **Sprints** depuis la barre latérale.

### Créer un sprint { #creating-a-sprint }

1. Cliquez sur **New sprint** (Nouveau sprint).
2. Donnez-lui un **Name** (Nom, facultatif), un **Start** et un **End**.
3. Cliquez sur **Create** (Créer).

Le sprint apparaît aussi sous forme de bloc dans le [Calendrier](calendar.md).

### S'engager sur des tâches { #committing-tasks }

Le panneau de droite, **Bookmarked tasks** (Tâches en favori), liste les tâches en favori non
terminées qui ne sont pas encore dans le sprint, les plus souvent reportées en premier. Cochez
celles que vous voulez et cliquez sur **Commit n (time)** (Engager n (durée)).

Le tableau du sprint affiche alors :

- **Planned** (Prévu) — la durée prévue totale des tâches engagées.
- **Window** (Fenêtre) — la durée du sprint. Si le plan est plus long que la fenêtre, un
  avertissement le signale.
- **n/m done** (n/m terminées) et une barre de progression.
- Une note listant les tâches **counted at the default** (comptées à la valeur par défaut) parce
  qu'elles n'ont pas de durée prévue.
- **slipped n×** (reportée n fois) sur les tâches engagées dans des sprints précédents et non
  terminées.

Chaque tâche engagée a un bouton **Track** pour le suivi du temps et un × pour **Remove from this
sprint** (Retirer de ce sprint).

### Faire le bilan { #reviewing }

Cochez la case d'une tâche quand elle est terminée — cocher *constitue* le bilan, et cela termine
la tâche. Une fois le sprint fini, cliquez sur **Close review** (Clore le bilan) pour le verrouiller
(**Reviewed** — Bilan fait). **Re-open** (Rouvrir) le déverrouille.

!!! warning "Attention"
    L'icône de corbeille (**Delete this sprint** — Supprimer ce sprint) supprime le sprint
    immédiatement, sans demander de confirmation.
