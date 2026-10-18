# Tableau, barre latérale et filtres

Le centre de la fenêtre est occupé par le **tableau** : une carte par note. Le bord gauche accueille
la **barre latérale de filtres**, et la barre d'outils en haut permet de changer de vue et d'ordre de
tri.

## Vues { #views }

Les boutons de vue de la barre d'outils déterminent la disposition du tableau. Votre choix est
mémorisé.

| Vue | Ce que vous voyez |
|---|---|
| **Kanban** | Une colonne par catégorie, regroupée par type à l'intérieur de chaque colonne. Cliquez sur l'en-tête d'un type pour le replier. Seules les catégories qui ont des notes obtiennent une colonne. Les colonnes sont réparties sur 1 à 3 rangées (**Kanban rows** — rangées kanban — dans les paramètres). |
| **Simple list** (Liste simple) | Toutes les notes dans une seule colonne, la plus ancienne en haut, la plus récente en bas. |
| **Columns** (Colonnes) | La liste, répartie sur autant de colonnes que la fenêtre peut en contenir. La largeur des cartes est limitée par **Card max width** (largeur maximale des cartes) dans les paramètres. |
| **Minimal** | Tableau masqué — seulement la dernière note et le champ de texte (**Ctrl+L**). Voir [Écrire des notes](writing-notes.md#minimal-side-note-mode). |

Déplacez-vous entre les cartes avec les **touches fléchées** : ↑↓ dans une colonne, ←→ d'une
colonne à l'autre.

## Ordre de tri { #sort-order }

Le menu de tri de la barre d'outils ordonne les cartes :

| Option | Ordre |
|---|---|
| **Last updated** (Dernière modification) | Selon la dernière modification du fichier, la plus récente en dernier (par défaut) |
| **Date created** (Date de création) | Selon l'horodatage du nom de fichier, la plus récente en dernier — stable pendant que vous réétiquetez |
| **Category** / **Type** (Catégorie / Type) | Dans l'ordre défini par le dossier |
| **Priority** (Priorité) | La plus haute en premier |
| **Note length** (Longueur de la note) | La plus longue en premier — pratique pour repérer les notes devenues trop volumineuses |

## Lire une carte { #reading-a-card }

Chaque carte affiche :

- Le **type**, la **priorité** et la **catégorie** dans une petite marge à gauche.
- Le texte de la note, avec la **première ligne en gras**. Les notes longues montrent leur début et
  leur fin (réglez les quantités avec **Preview characters (head)** et **(tail)** — caractères
  d'aperçu, début et fin — dans les paramètres). Les passages surlignés sont colorés.
- Les éventuels `#hashtags`, et une ligne 📎 listant jusqu'à cinq
  [fichiers joints](attached-files.md) — cliquez sur un nom pour l'ouvrir.
- Une **ligne d'actions** : **open folder** (ouvrir le dossier), **open note** (ouvrir la note),
  copier le nom de fichier, copier le chemin complet, et supprimer.
- Des compteurs sur les notes longues : mots, surlignages et — si la note a des enregistrements —
  marques temporelles issues des [fichiers de signets `.tmk`](../concepts/bookmarks-tmk.md).
- Deux dates : celle de **création** de la note (tirée de son nom de fichier) et celle de sa
  **dernière modification**. La seconde passe à l'orange après 3 jours et au rouge après 7 — une
  note à laquelle vous revenez sans cesse.

## Recherche { #search }

Appuyez sur **Ctrl+F** (ou sur le bouton loupe) et tapez. Le tableau se restreint aux notes dont le
texte — ou le code de type ou de catégorie — contient ce que vous avez tapé. **Esc** ferme la
recherche.

## La barre latérale de filtres { #the-filter-sidebar }

Appuyez sur **Ctrl+B** pour afficher ou masquer la barre latérale. Masquée, elle se réduit à un fin
rail **FILTERS** (filtres) qui indique toujours combien de filtres sont actifs.

Chaque option indique **combien de notes elle regroupe**. Les compteurs sont *à facettes* : chaque
nombre vous dit combien de notes vous verriez si vous choisissiez aussi cette option, en tenant
compte de vos autres filtres et du champ de recherche.

### Sections { #sections }

| Section | Ce qu'elle fait |
|---|---|
| **Tags** (Étiquettes) | Un nuage des `#hashtags` utilisés dans le texte des notes, dimensionnés selon leur fréquence. Cliquez sur l'un d'eux pour n'afficher que les notes qui le portent (une étiquette à la fois). Masquée quand aucune note n'a de hashtag. |
| **Calendar** (Calendrier) | Une carte thermique mensuelle — chaque jour est teinté selon le nombre de notes qu'il contient. Cliquez sur un jour pour filtrer sur celui-ci ; **Shift+clic** sur un autre jour pour sélectionner une plage. |
| **Priority** / **Type** / **Category** (Priorité / Type / Catégorie) | Toutes les options définies par le dossier, plus les codes inconnus réellement utilisés par les notes (en *italique ambré*). Choisissez-en une ou plusieurs. |
| **Non-standard** (Non standard) | **Include** / **Hide** / **Only** (Inclure / Masquer / Uniquement) — les fichiers et dossiers isolés qui ne sont pas encore de vraies notes. |
| **Bad taxonomy** (Taxonomie invalide) | **Include** / **Hide** / **Only** — les notes dont les codes ne sont pas définis par le dossier. |

Le calendrier possède un commutateur **Created** / **Updated** (Créé / Modifié). **Created**
utilise la date figurant dans le nom de fichier de la note, si bien qu'un réétiquetage ne déplace
jamais une note dans le temps. **Updated** utilise la date de dernière modification du fichier —
la vue « quand ai-je repris ceci ». Sous le mois se trouvent **Today** (Aujourd'hui — revenir au
mois en cours) et **Clear days** (Effacer les jours).

**Clear all filters** (Effacer tous les filtres) en bas de la barre latérale réinitialise tout. Les
sections se replient individuellement et la disposition est mémorisée — mais pas vos sélections,
afin que vous ne commenciez jamais la journée avec un filtre périmé.

!!! note "Ouverture sur aujourd'hui"
    Le seul filtre appliqué au lancement est *aujourd'hui* dans le calendrier (désactivez-le avec
    **Open filtered to today's notes** — ouvrir filtré sur les notes du jour — dans les
    paramètres). Un tableau vide indique toujours pourquoi — par exemple
    **No notes entered today** (Aucune note saisie aujourd'hui), **No notes match the search**
    (Aucune note ne correspond à la recherche) ou **No notes match the filters** (Aucune note ne
    correspond aux filtres).

### Files de nettoyage { #clean-up-queues }

Deux sections sont là pour vous aider à faire le ménage :

- **Non-standard → Only** liste les fichiers et dossiers du dossier de notes dont le nom n'est pas
  au format `<timestamp>~<type>~p<n>~<category>.md` — des éléments que vous y avez déposés et qui
  attendent encore des étiquettes. Ils sont toujours classés sous *aujourd'hui* dans le calendrier
  tant que vous ne les avez pas régularisés. Voir
  [Régulariser les fichiers isolés](organizing-notes.md#settle-loose-files).
- **Bad taxonomy → Only** liste les notes dont le nom est bien formé mais qui utilisent un code non
  défini par le dossier. Sélectionnez-en une et réétiquetez-la avec **Ctrl+Shift+<code>**.

La barre de titre affiche aussi le retard accumulé, par exemple `412 notes · 3 to settle`.

## Bandeau de hashtags { #hashtag-strip }

Sous le champ de texte, un bandeau d'une ligne liste chaque hashtag du dossier avec son compteur.
Désactivez **List every #hashtag under the add field** (Lister chaque #hashtag sous le champ
d'ajout) dans les paramètres pour n'afficher qu'un total compact.

## Rafraîchissement { #refresh }

Le tableau se met à jour de lui-même quand des fichiers changent sur le disque — par exemple quand
un outil de synchronisation apporte des notes d'une autre machine. **Ctrl+R** (ou le bouton de
rafraîchissement) relit le dossier à la demande.
