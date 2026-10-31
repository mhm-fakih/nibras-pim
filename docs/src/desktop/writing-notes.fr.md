# Écrire des notes

Le bas de la fenêtre est occupé par le **formulaire d'ajout** : trois rangées de pastilles
d'étiquettes (**Type**, **Prio** — priorité, **Cat** — catégorie), un champ de texte, un sélecteur
de dossier et un bouton d'envoi. À gauche du champ de texte, un petit bouton flèche bascule entre
le formulaire complet et le [formulaire court](#the-short-form). Tout ce que vous y tapez devient un
nouveau fichier de note.

## Enregistrer une note { #save-a-note }

1. Cliquez dans le champ de texte (il prend aussi le focus chaque fois que la fenêtre le prend ;
   vous pouvez donc simplement coller).
2. Tapez votre note. La première ligne sert de titre — les cartes l'affichent en gras.
3. Choisissez un type, une priorité et une catégorie avec les pastilles ou leurs raccourcis (voir
   [Type, priorité et catégorie](tagging.md)).
4. Appuyez sur **Ctrl+Enter**, ou cliquez sur le bouton d'envoi.

La note est enregistrée dans le dossier actif, le champ se vide, et le bandeau **Just added** (tout
juste ajoutée) au-dessus du formulaire affiche les étiquettes de la nouvelle note et un court
aperçu de ses mots. Votre choix d'étiquettes reste sélectionné pour la note suivante.

!!! tip "Enregistrer avec une simple touche Entrée"
    Vous préférez une saisie façon messagerie ? Activez **Save on Enter** (enregistrer avec Entrée)
    dans les [Paramètres](settings.md). **Enter** enregistre alors et **Shift+Enter** insère une
    nouvelle ligne.

Rien n'est perdu en quittant : **Ctrl+Q** enregistre d'abord un brouillon non envoyé sous forme de
note.

## Le formulaire court { #the-short-form }

Quand vous voulez simplement noter l'idée et la ranger plus tard, appuyez sur **Ctrl+N** (ou cliquez
sur le bouton flèche à gauche du champ de texte). Les rangées de pastilles disparaissent, et chaque
note est enregistrée en **N · P2 · X** — type N, priorité 2, catégorie X — prête à être réétiquetée
plus tard. Si le dossier ne définit pas l'un de ces codes, la note reçoit à la place le choix actuel
des pastilles. Survolez le bouton flèche pour voir exactement quelles étiquettes le formulaire court
utilisera.

Appuyez à nouveau sur **Ctrl+N** pour faire revenir les pastilles. L'application retient le
formulaire que vous avez utilisé en dernier.

## La boîte de réception : ranger les téléchargements dans une note { #the-inbox-file-downloads-into-a-note }

Les téléchargements, captures d'écran et autres fichiers atterrissent souvent d'abord dans un même
dossier — par défaut `~/new`. Le formulaire d'ajout liste les fichiers et dossiers de ce dossier
sous le champ de texte, dans une ligne dépliable telle que **5 to settle in ~/new** (5 à
régulariser dans ~/new), les plus récents en premier. Elle se met à jour d'elle-même à mesure que
des fichiers arrivent.

1. Dépliez la ligne et cochez les éléments qui vont avec la note que vous écrivez. La ligne les
   compte : **· 2 go into the new note** (2 iront dans la nouvelle note).
2. Écrivez la note (ou laissez le texte vide) et enregistrez-la.

Les éléments cochés sont **déplacés** dans le [dossier compagnon](attached-files.md) de la nouvelle
note : la boîte de réception se vide à mesure que vous écrivez des notes pour ce qu'elle contient.
Si vous avez enregistré sans texte, le texte de la note est le nom des éléments qu'elle a reçus.

Choisissez le dossier avec **Inbox folder** (dossier de réception) dans les
[Paramètres](settings.md) ; laissez-le vide pour désactiver la liste. Les dossiers qui se trouvent
dans la boîte de réception sans en être des éléments vont dans **Inbox: leave out (one path per
line)** (réception : exclure, un chemin par ligne). Vos dossiers de notes sont toujours exclus,
même lorsqu'ils se trouvent dans la boîte de réception.

## Texte arabe et mixte { #arabic-and-mixed-text }

Chaque ligne choisit sa propre direction ; l'arabe et l'anglais peuvent donc cohabiter dans une même
note et se lire correctement tous les deux. Un badge à gauche du champ de texte indique la
disposition de clavier active — **EN** ou **ع** — et se met à jour quand vous en changez. Tous les
raccourcis `Ctrl` fonctionnent quelle que soit la disposition active. Voir aussi
[Langues](../concepts/languages.md).

## Hashtags et banque d'étiquettes { #hashtags-and-the-tag-bank }

Écrivez des `#hashtags` n'importe où dans le texte d'une note. Ils apparaissent sur la carte, dans
le nuage d'étiquettes de la barre latérale et dans le bandeau de hashtags d'une ligne sous le champ
d'ajout.

Taper `#` (dans le champ d'ajout ou dans l'éditeur latéral) ouvre une liste de suggestions tirées
de votre **banque d'étiquettes** :

| Touche | Action |
|---|---|
| **↑** / **↓** | Parcourir les suggestions |
| **Enter** ou **Tab** | Insérer l'étiquette surlignée |
| **Esc** | Fermer la liste |

La banque d'étiquettes est un simple fichier, `tag-bank.md`, créé à côté du programme la première
fois qu'il est nécessaire. Une étiquette par ligne — l'orthographe préférée d'abord, puis les
éventuels alias séparés par des virgules. Les lignes commençant par `#` sont des commentaires.

```
# my tags
work, job, office
reading, books
```

Choisir un alias insère l'orthographe préférée : `#job` devient ainsi `#work`. La correspondance
est une simple recherche de sous-chaîne, insensible à la casse, sur les deux orthographes. Les
modifications du fichier sont prises en compte immédiatement — sans redémarrage. Pour garder un
même vocabulaire sur plusieurs machines, faites pointer **tag_bank_path** dans `config.json` vers
une copie située dans votre dossier synchronisé (voir [Paramètres](settings.md#config-file-only)).

!!! note
    Les étiquettes peuvent contenir des points et des tirets (`#project.alpha`, `#to-read`). Un `#`
    au milieu d'un mot (`C#`) ou suivi d'une espace (un titre Markdown) ne commence pas une
    étiquette.

## Lire et modifier dans le panneau latéral { #read-and-edit-in-the-side-pane }

Appuyez sur **Ctrl+E** (ou sur le bouton crayon de la barre d'outils) pour ouvrir le **panneau
d'édition latéral** à droite. Il affiche toujours la note sélectionnée sur le tableau — cliquez ou
utilisez les flèches pour passer à une autre carte. Faites glisser son bord gauche pour le
redimensionner.

Le panneau a deux modes, que l'on bascule avec les boutons **read** (lecture) et **edit** (édition)
en haut du panneau. Dans les deux, le Markdown tel que `**bold**`, `*italic*`, `` `code` ``,
`# headings`, les liens et les `#hashtags` est mis en forme sur place, les caractères de balisage
étant estompés plutôt que masqués.

=== "read (lecture)"

    La note mise en page pour la lecture. Les marqueurs de surlignage sont masqués et les passages
    surlignés sont colorés. **Cliquez sur une phrase pour la surligner** ; cliquez sur une phrase
    surlignée pour retirer le surlignage. Une phrase s'étend entre des signes de ponctuation
    (`. , ; : ! ?` et leurs équivalents arabes) ou des fins de ligne.

    Les surlignages sont stockés dans le fichier sous la forme `==like this==`, la même notation
    qu'utilisent Obsidian et d'autres outils Markdown ; ils survivent donc à la synchronisation et
    s'affichent aussi sur le téléphone.

=== "edit (édition)"

    Le texte brut, marqueurs compris. Les modifications sont **enregistrées automatiquement** peu
    après que vous avez cessé de taper, et toujours avant que vous ne réétiquetiez, supprimiez,
    fusionniez, changiez de dossier, fermiez le panneau ou quittiez l'application. La ligne d'état
    sous le panneau indique **Editing…** (modification en cours), **Saved** (enregistré) avec
    l'heure, ou une erreur.

    **Esc** ou **Ctrl+Enter** enregistre et rend le clavier au tableau.

Le panneau ne sert pas à créer des notes — les nouvelles notes viennent toujours du formulaire
d'ajout. Les fichiers isolés qui ne sont pas encore de vraies notes (voir
[Organiser les notes](organizing-notes.md#settle-loose-files)) sont affichés en lecture seule.

Choisissez le mode de démarrage du panneau avec **Editor pane starts in** (le panneau d'édition
démarre en), et la couleur de surlignage avec **Highlight colour** (couleur de surlignage), tous
deux dans les [Paramètres](settings.md).

Sous la note, le panneau **Files** (fichiers) liste les fichiers joints de la note — voir
[Fichiers joints](attached-files.md#the-files-panel).

## Notes rapides et tâches rapides { #quick-notes-and-quick-tasks }

Certaines choses ne méritent pas une note du tout — un numéro de téléphone, un article à acheter.
Pour celles-là, deux listes se trouvent sous le panneau latéral : **Quick notes** (notes rapides) à
gauche et **Quick tasks** (tâches rapides), avec une case à cocher par ligne, à droite.

- Appuyez sur **Ctrl+J** pour aller dans le champ des notes rapides, ou sur **Ctrl+K** pour celui
  des tâches rapides. Le panneau latéral et les listes s'ouvrent s'ils étaient fermés.
- Tapez une ligne et appuyez sur **Enter**. Le champ garde le clavier, vous pouvez donc en ajouter
  plusieurs à la suite. **Esc** revient au tableau.
- Cochez une tâche quand elle est faite — elle s'estompe au lieu d'être barrée, pour que l'arabe
  reste lisible. Le compteur de l'en-tête indique ce qui reste. Le **×** d'une ligne la supprime.
- Le bouton liste en haut du panneau latéral affiche ou masque les deux listes ; faites glisser le
  séparateur au-dessus d'elles pour les agrandir.

Les listes sont deux simples fichiers Markdown à la racine du dossier de notes, `quick-notes.md` et
`quick-tasks.md` — une liste à puces et une liste de tâches. Elles n'apparaissent jamais sur le
tableau, n'ont jamais besoin d'être régularisées, et se synchronisent et se modifient partout comme
n'importe quel fichier. Les modifications faites ailleurs apparaissent d'elles-mêmes. Chaque dossier
de notes a sa propre paire, si bien que changer de dossier change aussi les listes.

## Ajouter rapidement un commentaire { #quick-append-a-comment }

Pour ajouter une ligne à une note existante sans l'ouvrir :

1. Sélectionnez la carte (cliquez dessus ou utilisez les touches fléchées).
2. Appuyez sur **Space**. Un champ apparaît sous la carte.
3. Tapez votre commentaire et appuyez sur **Enter** (ou sur **Esc** pour annuler).

Le commentaire est ajouté à la fin de la note sous un séparateur daté. Par défaut, cela ressemble
à :

```
---2026-07-22 10:30:
your comment
```

Modifiez-le avec **Append separator** (séparateur d'ajout) et **Append date format** (format de
date d'ajout) dans les [Paramètres](settings.md).

## Ouvrir une note dans un autre éditeur { #open-a-note-in-another-editor }

Sélectionnez une carte et appuyez sur **Enter** (ou double-cliquez dessus, ou cliquez sur
**open note** — ouvrir la note — sur la carte). La note s'ouvre dans l'éditeur défini sous
**Open notes with** (ouvrir les notes avec) dans les paramètres — par exemple `xed` — ou dans
l'application par défaut de votre système pour les fichiers `.md`.

## Mode note latérale minimal { #minimal-side-note-mode }

Appuyez sur **Ctrl+L** (ou sur le dernier bouton du groupe de vues de la barre d'outils) pour une
toute petite fenêtre de note : le tableau et les rangées de pastilles disparaissent, ne laissant
que la carte **Just added**, un résumé d'une ligne des étiquettes sélectionnées, et le champ de
texte. Changez les étiquettes avec leurs raccourcis.

En mode minimal, appuyez sur **F12** pour sélectionner la carte tout juste ajoutée — puis
**Ctrl+Shift+<code>** la réétiquette ou **Ctrl+D** la supprime. **F12** à nouveau vous ramène au
champ de texte. **Ctrl+L** rétablit votre vue précédente.
