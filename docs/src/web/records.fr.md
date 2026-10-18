# Modules et enregistrements

Tout ce que vous conservez dans l'application web est un **enregistrement** dans un **module**. Chaque module est son
propre type d'enregistrement avec ses propres champs, mais tous partagent la même liste, le même volet de détail et le
même formulaire : dès que vous en connaissez un, vous les connaissez tous.

## Les modules { #the-modules }

| Module | À quoi il sert | Champs notables |
|---|---|---|
| **Tasks** (Tâches) | Les choses à faire | Summary, Description, Status, **Goal**, % complete, Planned duration (min), Start, End, Urgent, Order #, **Repeats**, **People** |
| **Journal** (Journal) | Ce qui s'est passé | Summary, Description, Type, Start, End, Energy |
| **Planner** (Planificateur) | Ce que vous prévoyez, à un moment donné | Summary, **Plan**, **Reality**, Type, Status, Start, End, % complete, **Repeats** |
| **Resources** (Ressources) | Livres, articles, vidéos… | Type, Title, Author, Publisher, Edition, ISBN, Short description, Description, Full text, Highlights, Comments, URL, Status, Year, Pages, Read, Read on, % complete |
| **Notes** (Notes) | Idées et notes de lecture | Summary, Short description, Content, Reaction, Type, Status, **Resource** (le livre dont parle la note), Pages |
| **Goals** (Objectifs) | Les résultats que vous visez | Summary, Description, Code, Status, Type, % complete, Start, End, Completed, **Show habit heatmap on dashboards** |
| **Writings** (Écrits) | Les textes plus longs que vous rédigez | Summary, Body, Short description, Slug, Source, Status, Type, Born on |
| **Contacts** (Contacts) | Les personnes | Name, Code, Job title, Telephones, Email, Address, About |
| **Departments** (Départements) | Les grands domaines de la vie ou du travail | Name, Code, Summary |
| **Courses** (Cours) | Les sujets à l'intérieur d'un département | Title, Code, Department, **Note tags** |
| **Excerpts** (Extraits) | Des passages d'un livre | Summary, Book, Chapter(s), Chapter #, Title, Content |

La plupart des modules de contenu comportent aussi **Highlights** (surlignages), **Notes**, **Department**
(département), **Course** (cours), **Priority** (priorité), **Bookmarked** (favori) et **Tags** (étiquettes).

Votre administrateur décide quels modules sont activés (ainsi que leurs libellés et leur ordre) dans
[Settings → Modules](settings.md#modules) ; votre barre latérale peut donc en afficher moins.

!!! note "Les départements et les cours classent tout"
    Le **Department** et le **Course** d'un enregistrement indiquent où il se range. Lorsque vous choisissez un
    **Course** dans un formulaire, son département est rempli pour vous. Les départements et les cours sont des modules
    à part entière : vous pouvez les mettre en favori, leur joindre des fichiers et des notes, et les rechercher comme
    n'importe quel autre enregistrement.

## Parcourir un module { #browsing-a-module }

Cliquez sur un module dans la barre latérale. Le volet central liste ses enregistrements, du plus récent au plus ancien,
25 par page. Chaque ligne affiche un ★ s'il est en favori, le titre, la date de création, et des pastilles pour le code,
le statut, le type, la ressource liée et jusqu'à trois étiquettes. Une vignette de couverture apparaît lorsque
l'enregistrement possède une image de couverture.

Utilisez la zone de recherche et les filtres en haut de la liste pour l'affiner — voir
[Recherche, filtres et rapports](search.md).

## Créer un enregistrement { #creating-a-record }

1. Cliquez sur **New** (Nouveau) en haut de la liste (ou appuyez sur **n**).
2. Remplissez le formulaire. Les zones de texte marquées **(Markdown)** acceptent le [Markdown](#formatting-text).
3. Cliquez sur **Save** (Enregistrer) — ou sur **Cancel** (Annuler) pour abandonner le brouillon.

Le nouvel enregistrement s'ouvre dans le volet de détail, surmonté d'une carte **✓ Saved** (Enregistré).

!!! tip "Des accès plus rapides"
    - La barre **Quick note** (Note rapide) en bas du volet de détail crée une note en une seule étape : tapez, puis
      **Ctrl+Enter** ou **Add** (Ajouter). Si la première ligne est courte, elle devient le titre et le reste le
      contenu. Cliquez sur l'en-tête de la barre pour la replier.
    - La [saisie par lot](batch-commands.md#batch-entry) crée de nombreux enregistrements, un par ligne.
    - Sur **Resources**, l'icône de lien à côté de **New** (**New resource from a URL** — Nouvelle ressource depuis
      une URL) vous permet de coller une adresse web et de cliquer sur **Fetch** (Récupérer) : le serveur lit la page
      et ouvre un formulaire pré-rempli — titre, auteur, éditeur, description, année et texte de l'article. Vérifiez
      les champs, puis **Save** ; l'image principale de la page est enregistrée comme couverture. Le panneau reste
      ouvert pour que vous puissiez récupérer plusieurs liens à la suite.

## Le volet de détail { #the-detail-pane }

Sélectionnez un enregistrement pour l'afficher à droite. En haut :

| Contrôle | Ce qu'il fait |
|---|---|
| ☆ / ★ | Mettre en **favori** (**Bookmark**) ou retirer le favori. Les favoris alimentent le Kanban, les sprints, la table des matières des favoris, les flux RSS et ce que reçoit votre téléphone. |
| 🚩 (tâches) | Marquer ou retirer **Urgent**. |
| ◯ / ✓ (tâches, objectifs) | Marquer comme **terminé** (**complete**) ou rouvrir. Terminer un enregistrement en favori retire aussi son favori. |
| **Track** (Suivre) (tâches) | Démarrer une session de suivi du temps — voir [Suivi du temps](tasks-kanban-sprints.md#time-tracking). |
| **+ Subtask** (Sous-tâche) (tâches avec un objectif) | Créer une tâche de suite sous le même objectif. |
| **Edit** (Modifier) | Ouvrir le formulaire (ou appuyer sur **e**). |
| ⤢ | Ouvrir l'enregistrement dans la [page de lecture](#the-reading-page) complète, dans un nouvel onglet. |
| **Repo1**, **Repo2**, **Repo3** | Ouvrir le dossier de l'enregistrement dans chaque dépôt de fichiers — voir [Fichiers](#files-and-folders). Le badge indique le nombre de fichiers. |
| **Delete** (Supprimer) | Déplacer l'enregistrement vers **Trash** (Corbeille), après confirmation. |

Sous les boutons, des pastilles affichent l'**ID** de l'enregistrement, la règle de répétition, le statut, le type, le
contexte, le département, le cours, la ressource liée (cliquez pour l'ouvrir) et la date de création (**Created**).

Plus bas, vous trouverez :

- **Tags** (Étiquettes) — cliquez sur **+ add tag…** (ajouter une étiquette) pour associer une étiquette existante ou en
  taper une nouvelle ; cliquez sur le × d'une étiquette pour la retirer.
- **People** (Personnes) — sur les tâches (lorsque Contacts est activé) : **+ add person…** (ajouter une personne).
- **Les champs**, rendus en texte mis en forme. Les listes de cases à cocher (`- [ ] item`) peuvent être cochées
  directement ici.
- **Append to Description… / Append to Notes…** (Ajouter à la description / aux notes) — une zone sous ces champs qui
  ajoute du texte à la fin sans ouvrir le formulaire. Appuyez sur **Ctrl+Enter** pour ajouter.
- **Related** (Liés) — les enregistrements liés dans d'autres modules (les tâches d'un objectif, les journaux de suivi
  du temps d'une tâche, les notes d'une ressource…). Cliquez sur l'un d'eux pour l'ouvrir.
- **Edit full text in your editor** (Modifier le texte intégral dans votre éditeur) — voir
  [Écrits et fichiers de checkout](writings-fulltext.md).
- **Files** (Fichiers) — voir ci-dessous.
- **History** (Historique) — chaque modification de cet enregistrement, champ par champ (visible si vous avez accès au
  journal d'audit).

Sur un **contact**, une liste **Open tasks** (Tâches ouvertes) montre ce qui reste ouvert avec cette personne. Sur une
**ressource**, **Add a note about this resource** (Ajouter une note sur cette ressource) crée une véritable note liée à
la ressource, avec le cours pré-rempli à partir de celui de la ressource.

### Mettre en forme le texte { #formatting-text }

Les champs de texte long s'écrivent en Markdown et s'affichent mis en forme. La page [Help](keyboard-shortcuts.md)
(Aide) contient un aide-mémoire. Le texte entouré de `==double égal==` est affiché surligné. Le texte en arabe et dans
d'autres écritures de droite à gauche est aligné automatiquement d'après ses premières lettres — voir
[Langues](../concepts/languages.md).

## Modification, modifications rapides et favoris { #editing-quick-edits-and-bookmarks }

- **Edit** (ou **e**) ouvre le formulaire complet. Enregistrez ou annulez comme d'habitude.
- Avec un enregistrement sélectionné, vous pouvez changer un champ au clavier sans passer par le formulaire : **p**
  priorité, **#** type, **?** statut, **@** contexte, **d** département, **c** cours, **\*** basculer le favori. Un petit
  sélecteur apparaît ; choisissez une valeur et elle est appliquée aussitôt. Voir
  [Raccourcis clavier](keyboard-shortcuts.md#quick-edit).
- Pour modifier de nombreux enregistrements à la fois, cochez-les dans la liste et tapez une commande — voir
  [Modification par lot](batch-commands.md#batch-edit).

## Tâches et plans récurrents { #repeating-tasks-and-plans }

Les entrées **Tasks** et **Planner** ont un interrupteur **Repeats** (Répétition) dans le formulaire. Choisissez
**Daily** (Quotidien), **Weekly** (Hebdomadaire), **Monthly** (Mensuel), **Yearly** (Annuel) avec un intervalle
**Every N** (Tous les N), ou **Custom (cron)** (Personnalisé).

=== "Tâches"

    Une tâche récurrente n'existe qu'une occurrence à la fois. Lorsque vous la terminez, la suivante est créée.
    Activez **Fixed schedule — repeats on the date even if not completed** (Calendrier fixe — se répète à la date même
    si elle n'est pas terminée) pour que l'occurrence suivante apparaisse à sa date quoi qu'il arrive.

=== "Planificateur"

    Un plan récurrent est une série : l'enregistrement contient la règle et le calendrier dessine chaque occurrence.
    Choisissez quand elle se termine (**Ends**) — **Never** (Jamais), **On date** (À une date) ou
    **After N occurrences** (Après N occurrences). Une occurrence ne devient un enregistrement à part entière que
    lorsque vous l'ouvrez, la déplacez ou l'annotez. Voir [Calendrier](calendar.md#repeating-plans).

## Fusionner des doublons { #merging-duplicates }

Dans Tasks, Journal, Planner, Resources, Notes, Goals et Writings, vous pouvez fusionner deux enregistrements :
**faites glisser une ligne de la liste sur une autre**. Une boîte de dialogue **Merge** (Fusionner) explique ce qui va
se passer — les champs de texte sont joints, les champs vides de l'enregistrement conservé sont remplis à partir de
l'autre, les étiquettes sont combinées, les enregistrements liés suivent l'enregistrement conservé, et les fichiers
sont déplacés dans un dossier `<id>-files`. Utilisez **Swap** (Inverser) pour choisir quel enregistrement est conservé,
puis **Merge**. L'enregistrement absorbé part à la corbeille.

La page [Import notes](import-capture.md#finding-duplicates) peut aussi analyser tout un module à la recherche de
doublons probables.

## Supprimer et restaurer { #deleting-and-restoring }

**Delete** ne détruit jamais un enregistrement directement : il est déplacé vers **Trash** (barre latérale →
**Trash**), qui liste les enregistrements supprimés et fusionnés de tous les modules. Cliquez sur l'icône de
restauration pour remettre un enregistrement là où il était.

## Fichiers et dossiers { #files-and-folders }

Chaque enregistrement peut avoir des fichiers conservés dans jusqu'à trois **dépôts** — des dossiers sur le serveur :

| Dépôt | Signification |
|---|---|
| **Repo 1 · sandbox** | La copie de travail, portable. Les fichiers de checkout et les couvertures se trouvent ici. |
| **Repo 2 · main** | Les archives. |
| **Repo 3 · dismissed** | Ce qui a été mis de côté. |

Dans la section **Files** du volet de détail, choisissez **Upload to** (Téléverser vers), cliquez sur **Upload**
(Téléverser) et choisissez un fichier. Cliquez sur un nom de fichier pour le télécharger ; l'icône de corbeille le
supprime. Un fichier nommé `cover.jpg` (ou `.png`, `.webp`…) est affiché comme couverture de l'enregistrement.

!!! note "Boutons d'ouverture de dossier"
    **Repo1 / Repo2 / Repo3** (et les touches **f 1/2/3**) ouvrent le dossier dans un gestionnaire de fichiers *sur
    l'ordinateur qui exécute le serveur*. Ils sont utiles lorsque le serveur tourne sur votre propre ordinateur ; depuis
    un autre appareil, utilisez plutôt **Upload** et les liens de fichiers. Les boutons Repo 2 et Repo 3 peuvent être
    masqués dans [Settings → Features](settings.md#pages-and-features).

## La page de lecture { #the-reading-page }

Le bouton ⤢ ouvre un enregistrement dans son propre onglet, étroit et sans distraction. De là :

- **Edit** vous renvoie à la fenêtre principale avec le formulaire ouvert.
- **Highlight** (Surligner), lorsque l'enregistrement a un corps de texte, vous permet de cliquer sur des phrases pour
  les surligner. Chaque clic marque le texte et l'ajoute aux surlignages de l'enregistrement ; cliquez de nouveau pour
  le retirer. Choisissez la granularité **Sentence** (Phrase) ou **Clause** (Proposition) — le choix est mémorisé pour
  chaque enregistrement. Cliquez sur **Done highlighting** (Surlignage terminé) une fois fini.

Les entrées du calendrier s'ouvrent aussi dans cette page de lecture.
