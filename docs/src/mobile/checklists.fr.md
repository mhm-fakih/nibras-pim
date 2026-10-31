# Listes de contrôle

**Listes de contrôle** (l'icône de liste cochée, juste sous l'agenda dans le rail) garde les petites choses que vous comptez faire chaque jour ou chaque semaine : recharger le téléphone, un point avant de dormir, un appel à la famille. Vous les cochez jour après jour, et chaque semaine est conservée dans un simple fichier Markdown. Elles sont volontairement séparées de vos tâches, pour que trente petites coches quotidiennes n'enterrent jamais le vrai travail.

## La page { #the-page }

- La barre du haut affiche la semaine (**Semaine 43**), ses dates, et **cette semaine** ou **pas commencée** (une semaine qui n'a pas encore de fichier). Les flèches mènent à la **Semaine précédente** et à la **Semaine suivante**.
- **Jour** / **Semaine** basculent entre les deux vues de la même semaine (voir plus bas).
- **Cette semaine** vous ramène quand vous regardez une autre semaine. Sur la semaine en cours, le même emplacement propose **Planifier la semaine prochaine**.
- Le crayon (**Modifier les listes**) et l'icône de note (**Modifier en texte**) servent à changer les éléments. Voir [Modifier les listes](#edit-the-lists).

Les éléments sont rangés en **groupes**. Une nouvelle liste commence avec **Début de journée**, **Mi-journée**, **Fin de journée**, **Études** et **Hebdomadaire**, tous vides. Renommez, ajoutez ou supprimez des groupes à votre guise.

### Jour : cocher { #day-ticking }

La vue **Jour** sert à cocher. Deux rangées de puces en haut choisissent le jour, du lundi au dimanche, chacune avec la part déjà faite (**75%**). Aujourd'hui est marqué d'un point, et vous pouvez choisir un autre jour pour compléter celui d'hier.

Touchez un élément pour faire tourner sa marque : fait (✓), puis fait en partie (◐), puis pas fait. Un élément fait en partie compte pour moitié. L'en-tête de chaque groupe affiche son pourcentage pour le jour.

Le groupe **Hebdomadaire** est différent : ses éléments ont une seule marque pour toute la semaine plutôt qu'une par jour.

### Semaine : faire le bilan { #week-looking-back }

La vue **Semaine** montre chaque élément face aux sept jours, avec le taux de chaque jour au-dessus de chaque groupe. Vous pouvez aussi cocher ici ; les jours à venir sont estompés.

## Modifier les listes { #edit-the-lists }

Touchez le crayon (**Modifier les listes**). La page passe en mode modification jusqu'à ce que vous le touchiez de nouveau (**Terminer**) :

- **Ajouter un élément** sous un groupe ajoute des éléments. La fenêtre reste ouverte pour le suivant, de sorte qu'une longue liste se saisit d'un trait ; touchez **Terminé** à la fin.
- Touchez un élément ou l'en-tête d'un groupe pour le renommer (**Renommer l’élément**, **Renommer le groupe**).
- La corbeille à côté d'un élément le supprime (**Supprimer l’élément**).
- **Ajouter un groupe**, en bas, ajoute un groupe. Seul un groupe vide peut être supprimé (**Supprimer le groupe**).

Les modifications s'appliquent à la semaine affichée. Les éléments sont repris les semaines suivantes.

### Modifier en texte { #edit-as-text }

Pour les changements plus importants (les cours d'un semestre collés d'un coup, un groupe réorganisé), touchez **Modifier en texte**. Toute la semaine s'ouvre comme un seul texte :

```
## 1 Début de journée
1.1 Vérifier la charge du téléphone
1.2 Relire le planning

## 5 Hebdomadaire [weekly]
5.1 Appeler la famille
```

Un élément par ligne, sous un titre `## Groupe`. Ajoutez `[weekly]` après le nom d'un groupe pour des éléments hebdomadaires. Collez, réordonnez ou supprimez librement, puis touchez **Enregistrer**.

### Codes { #codes }

Chaque groupe a un numéro et chaque élément un code (`1.2` est l'élément 2 du groupe 1). Le code apparaît en petit au-dessus du nom de chaque élément, et c'est à lui qu'appartiennent les coches : renommer un élément garde ses coches, et un même code désigne le même élément d'une semaine à l'autre. Les éléments sont listés dans l'ordre de leurs codes. Dans **Modifier en texte**, une ligne garde ses coches tant que son code ne change pas, et lui donner un autre code la déplace. Une ligne sans code est un nouvel élément du groupe situé au-dessus, numéroté à la suite.

## Les semaines { #weeks }

Chaque semaine va du lundi au dimanche et a sa propre liste. Une semaine à laquelle vous n'avez pas encore touché affiche les éléments de la dernière semaine précédente, sans marques ; son fichier n'est écrit qu'une fois que vous cochez ou modifiez quelque chose, si bien que regarder en avant ne laisse aucune trace. **Planifier la semaine prochaine** copie les éléments de cette semaine dans la suivante et l'ouvre en modification, pour ajuster la liste avant qu'elle commence.

## Rappels { #reminders }

Écrivez une heure devant le titre d'un groupe, par exemple renommez **Début de journée** en `07:30 Début de journée`. Chaque jour à 07:30, une notification discrète nomme alors le groupe et indique **C’est le moment de cocher ses éléments.** La toucher ouvre la page.

- Le rappel est **silencieux** : ni son ni vibration.
- Il est sauté les jours où chaque élément du groupe a déjà une marque.
- Seuls les groupes quotidiens ont des rappels, pas le groupe hebdomadaire.

L'heure fait partie du titre : elle suit donc le groupe dans les semaines suivantes, et une heure modifiée sur un ordinateur est reprise par le téléphone.

## Éléments en arabe { #arabic-items }

Un élément ou un groupe dont le texte commence par des lettres arabes s'écrit de droite à gauche et s'aligne à droite, quelle que soit la langue de l'application. Tout le reste se lit de gauche à droite, si bien qu'une liste peut mélanger les deux.

## Où sont les fichiers { #where-the-files-are }

Chaque semaine est un fichier Markdown nommé d'après elle, par exemple `2026-W43.md`, dans le **Dossier des listes**. Par défaut, c'est un dossier `checklists` à l'intérieur de votre dossier de notes, qui se synchronise donc avec vos notes. Changez-le dans [Paramètres](settings.md) → **Listes de contrôle**. Un fichier ressemble à ceci :

```
# Week 43 · 2026-10-19 – 2026-10-25

## 1 Start of day
| Item | Mon | Tue | Wed | Thu | Fri | Sat | Sun |
|---|---|---|---|---|---|---|---|
| 1.1 Check phone charge level | ✓ | ◐ |  |  |  |  |  |
```

Il s'affiche comme un tableau dans n'importe quel lecteur Markdown. Si vous le modifiez à la main, `x` compte aussi comme fait. Quand le fichier change en dehors de l'application (une synchronisation depuis votre ordinateur), la page le relit à votre retour.

L'interrupteur **Listes de contrôle** en haut du même onglet des paramètres désactive la fonction : la page quitte le rail et aucun rappel n'est programmé.
