# Listes de contrôle

**Listes de contrôle** (l'icône de liste cochée, juste sous l'agenda dans le rail) garde les petites choses que vous comptez faire chaque jour ou chaque semaine : recharger le téléphone, un point avant de dormir, un appel à la famille. Vous les cochez jour après jour, et pouvez ajouter une note à n'importe quelle coche. Avec un serveur Nibras configuré, les listes se synchronisent avec le web, où vous pouvez aussi les cocher et les annoter. Elles sont volontairement séparées de vos tâches, pour que trente petites coches quotidiennes n'enterrent jamais le vrai travail.

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

### Notes { #notes }

**Appuyez longuement** sur la marque d'un élément (dans l'une ou l'autre vue) pour ouvrir ce jour : choisissez directement la marque (**Fait**, **Fait en partie**, **Pas fait**) et écrivez une **Note**, ce qui a été fait ou pourquoi pas, puis touchez **Enregistrer**. Un jour qui a une note affiche un petit point dans le coin de sa marque, et en vue Jour la première ligne de la note apparaît sous le nom de l'élément.

### Semaine : faire le bilan { #week-looking-back }

La vue **Semaine** montre chaque élément face aux sept jours, avec le taux de chaque jour au-dessus de chaque groupe. Vous pouvez aussi cocher ici ; les jours à venir sont estompés.

## Modifier les listes { #edit-the-lists }

Touchez le crayon (**Modifier les listes**). La page passe en mode modification jusqu'à ce que vous le touchiez de nouveau (**Terminer**) :

- **Ajouter un élément** sous un groupe ajoute des éléments. La fenêtre reste ouverte pour le suivant, de sorte qu'une longue liste se saisit d'un trait ; touchez **Terminé** à la fin.
- Touchez un élément ou l'en-tête d'un groupe pour le renommer (**Renommer l’élément**, **Renommer le groupe**).
- La corbeille à côté d'un élément le supprime (**Supprimer l’élément**).
- **Ajouter un groupe**, en bas, ajoute un groupe. Seul un groupe vide peut être supprimé (**Supprimer le groupe**).

Un élément ou un groupe ajouté pendant qu'une semaine est affichée commence cette semaine-là et continue les suivantes. Un élément supprimé pendant qu'une semaine est affichée quitte la liste à partir de cette semaine : les semaines précédentes le gardent, avec leurs coches. Rajouter un élément sous le même code ramène le même élément, anciennes coches comprises. Un nom vaut pour toutes les semaines : renommer dans une semaine renomme partout.

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

Chaque semaine va du lundi au dimanche et affiche chaque élément qui est sur la liste cette semaine-là. Les éléments passent d'une semaine à l'autre d'eux-mêmes, sans coches. Une semaine où rien n'est encore coché ni noté est marquée **pas commencée**. **Planifier la semaine prochaine** ouvre la semaine suivante en modification, pour ajuster la liste avant qu'elle commence.

## Rappels { #reminders }

Écrivez une heure devant le titre d'un groupe, par exemple renommez **Début de journée** en `07:30 Début de journée`. Chaque jour à 07:30, une notification discrète nomme alors le groupe et indique **C’est le moment de cocher ses éléments.** La toucher ouvre la page.

- Le rappel est **silencieux** : ni son ni vibration.
- Il est sauté les jours où chaque élément du groupe a déjà une marque.
- Seuls les groupes quotidiens ont des rappels, pas le groupe hebdomadaire.

L'heure fait partie du titre : elle suit donc le groupe dans les semaines suivantes, et une heure modifiée sur le web est reprise par le téléphone à la synchronisation suivante.

## Éléments en arabe { #arabic-items }

Un élément ou un groupe dont le texte commence par des lettres arabes s'écrit de droite à gauche et s'aligne à droite, quelle que soit la langue de l'application. Tout le reste se lit de gauche à droite, si bien qu'une liste peut mélanger les deux.

## Où sont conservées les listes { #where-the-lists-are-kept }

Les listes sont conservées dans l'application, pas dans des fichiers. Chaque groupe, élément et coche du jour (avec sa note) est un enregistrement, les mêmes que le serveur Nibras garde dans sa base de données.

### Synchronisation avec le web { #syncing-with-the-web }

Avec un serveur Nibras configuré et **Synchroniser avec Nibras** activé (voir [Synchronisation Nibras](sync.md)), le téléphone et le web échangent leurs modifications : quelques secondes après une coche ou une modification, à l'ouverture de la page, et à chaque synchronisation. La page Listes de contrôle du web permet de cocher, d'écrire des notes, d'ajouter et de renommer éléments et groupes, et de les retirer de la liste ; ces modifications arrivent sur le téléphone.

Si la même coche (ou le même élément, ou groupe) a été modifiée des deux côtés entre deux synchronisations, un côté l'emporte. Par défaut, c'est le téléphone ; cela se change sur le web, dans le menu **⋮** de la page **Checklists** : **The web's change stands** (le changement du web l'emporte). Sans serveur, les listes restent simplement sur le téléphone.

### Les anciens fichiers de semaine { #the-old-week-files }

Les versions précédentes gardaient chaque semaine dans un fichier Markdown (`2026-W43.md`) du **Dossier des listes**. À sa première ouverture, cette version lit ces fichiers : éléments, groupes et coches gardent leurs codes et leurs dates, et un élément disparu de la dernière semaine s'arrête là où il figurait pour la dernière fois. Les fichiers restent en place, intacts, et ne sont plus écrits ; le serveur Nibras lit sa propre copie de la même façon, si bien que les deux aboutissent aux mêmes enregistrements.

L'interrupteur **Listes de contrôle** en haut de [Paramètres](settings.md) → **Listes de contrôle** (où se règle aussi le Dossier des listes) désactive la fonction : la page quitte le rail et aucun rappel n'est programmé.
