# Écrire des notes

Le bas de la fenêtre est occupé par le **formulaire d'ajout** : trois rangées de pastilles
d'étiquettes (**Type**, **Prio** — priorité, **Cat** — catégorie), un champ de texte, un sélecteur
de dossier et un bouton d'envoi. Tout ce que vous y tapez devient un nouveau fichier de note.

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
en haut du panneau :

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
