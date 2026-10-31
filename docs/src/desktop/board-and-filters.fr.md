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
| **Week calendar** (Calendrier de la semaine) | Vos notes de journal (**J**) et de planification (**P**) disposées sur une semaine, dans leurs créneaux horaires. Voir [Calendrier de la semaine](#week-calendar). |
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
  d'aperçu, début et fin — dans les paramètres). Les passages surlignés sont colorés. Chaque ligne
  prend sa propre direction : les lignes en arabe se lisent et s'alignent à droite, les lignes en
  anglais à gauche, même au sein d'une seule note.
- Les `#hashtags` de la note, une seule fois, sous forme de mots teintés après la dernière ligne.
- Une **ligne d'actions** : **open folder** (ouvrir le dossier), **open note** (ouvrir la note),
  copier le nom de fichier, copier le chemin complet, importer dans le serveur Nibras, et
  supprimer. Survolez un bouton pour voir son raccourci.
- Des compteurs sur les notes longues : mots, surlignages et — si la note a des enregistrements —
  marques temporelles issues des [fichiers de signets `.tmk`](../concepts/bookmarks-tmk.md), plus
  le nombre de [fichiers joints](attached-files.md), par exemple
  `1,204 words · 2 highlights · 4 time marks · 3 files`.
- Deux dates à droite de la ligne d'actions : celle de **création** de la note (tirée de son nom de
  fichier) et celle de sa **dernière modification**. La seconde passe à l'orange après 3 jours et
  au rouge après 7 — une note à laquelle vous revenez sans cesse.

## La barre de titre { #the-title-bar }

La barre de titre affiche la version de l'application et le nombre de notes du dossier :
`Nibras Notes v1.3.2.b57 · 412 notes · 3 to settle`.

- Seules les **vraies notes** sont comptées — le même nombre que celui de
  l'[application terminal](../terminal/index.md). Les fichiers isolés en attente d'étiquettes sont
  comptés à part, sous la forme **· N to settle** (N à régulariser), qui disparaît à zéro (voir
  [Files de nettoyage](#clean-up-queues)).
- Quand une recherche ou un filtre restreint le tableau, elle affiche les deux nombres :
  `12 / 412 notes`.
- Quand des cartes sont marquées, elle ajoute un rappel tel que
  `⛓ 3 marked — Ctrl+Shift+M to merge, Esc to clear`.

## Recherche { #search }

Appuyez sur **Ctrl+F** (ou sur le bouton loupe), tapez, puis appuyez sur **Entrée**. Le tableau se
restreint aux notes dont :

- le **texte** contient ce que vous avez tapé,
- le **code de type ou de catégorie** le contient,
- le **nom de fichier** le contient — collez un nom complet pour trouver une note précise, ou tapez
  un fragment du bloc d'étiquettes comme `N~p2` pour lister toutes les notes étiquetées ainsi, ou
- les [**fichiers joints**](attached-files.md) comprennent un fichier ou un dossier dont le nom le
  contient, à n'importe quelle profondeur.

La correspondance porte sur le texte brut et ignore la casse. La recherche porte sur tout le
dossier : appuyer sur **Entrée** met de côté vos filtres de la barre latérale, qui reviennent quand
la recherche se termine. Videz le champ (ou appuyez sur **Esc**) pour terminer la recherche et
retrouver le tableau tel qu'il était.

## Ce qui filtre le tableau { #whats-filtering-the-board }

Dès qu'une recherche ou un filtre restreint le tableau, une ligne au-dessus des cartes l'indique —
par exemple `4 of 8 │ Search "n~p2" │ Type N · Note │ Priority P2`. Son bouton **clear** (effacer)
vide le champ de recherche et revient à la vue de lancement (aujourd'hui et hier) ; cliquez à
nouveau pour voir tout le dossier.

Un tableau vide indique toujours pourquoi : **No notes entered today or yesterday** (aucune note
saisie aujourd'hui ni hier — la vue de lancement), **No notes entered today** (aucune note saisie
aujourd'hui), **No note's text, file name or attached file name contains “…”** (aucun texte, nom de
fichier ou nom de fichier joint ne contient « … »), **No notes match the filters** (aucune note ne
correspond aux filtres) ou **No notes in this folder yet** (pas encore de note dans ce dossier).

## La barre latérale de filtres { #the-filter-sidebar }

Appuyez sur **Ctrl+B** pour afficher ou masquer la barre latérale. Masquée, elle se réduit à un fin
rail **FILTERS** (filtres) qui indique toujours combien de filtres sont actifs. Faites glisser son
bord droit pour l'élargir ou la rétrécir ; la largeur est mémorisée.

Chaque option indique **combien de notes elle regroupe**. Les compteurs sont *à facettes* : chaque
nombre vous dit combien de notes vous verriez si vous choisissiez aussi cette option, en tenant
compte de vos autres filtres et du champ de recherche.

### Sections { #sections }

De haut en bas :

| Section | Ce qu'elle fait |
|---|---|
| **Calendar** (Calendrier) | Une vue mensuelle — chaque jour qui a des notes affiche son numéro en gras. Cliquez sur un jour pour filtrer sur celui-ci ; **Shift+clic** sur un autre jour pour sélectionner une plage. Les jours choisis sont remplis de la couleur d'accent. |
| **Type** / **Priority** / **Category** (Type / Priorité / Catégorie) | Côte à côte : le type au-dessus de la priorité à gauche, les catégories à droite (les noms arabes se lisent de droite à gauche). Toutes les options définies par le dossier, plus les codes inconnus réellement utilisés par les notes (en *italique ambré*). Choisissez-en une ou plusieurs. |
| **Non-standard** (Non standard) | **Include** / **Hide** / **Only** (Inclure / Masquer / Uniquement) — les fichiers et dossiers isolés qui ne sont pas encore de vraies notes. |
| **Bad taxonomy** (Taxonomie invalide) | **Include** / **Hide** / **Only** — les notes dont les codes ne sont pas définis par le dossier. |
| **Tags** (Étiquettes) | Un nuage des `#hashtags` utilisés dans le texte des notes, dimensionnés selon leur fréquence. Cliquez sur l'un d'eux pour n'afficher que les notes qui le portent (une étiquette à la fois). Masquée quand aucune note n'a de hashtag. |

Le calendrier possède un commutateur **Created** / **Updated** (Créé / Modifié). **Created**
utilise la date figurant dans le nom de fichier de la note, si bien qu'un réétiquetage ne déplace
jamais une note dans le temps. **Updated** utilise la date de dernière modification du fichier —
la vue « quand ai-je repris ceci ». Survolez un jour pour voir les deux nombres. Sous le mois se
trouvent **Today** (Aujourd'hui — revenir au mois en cours) et **Clear days** (Effacer les jours —
retour à aujourd'hui et hier ; cliquez à nouveau pour tous les jours).

**Clear all filters** (Effacer tous les filtres) en bas de la barre latérale revient à la vue de
lancement ; cliquez à nouveau pour voir tout le dossier. Les sections se replient individuellement
et la disposition est mémorisée.

!!! note "La vue de lancement"
    L'application s'ouvre sur **aujourd'hui et hier**, avec les éléments non standard masqués —
    désactivez ces comportements avec **Open filtered to today's and yesterday's notes** (ouvrir
    filtré sur les notes d'aujourd'hui et d'hier) et **Hide non-standard items by default**
    (masquer les éléments non standard par défaut) dans les [paramètres](settings.md). Les autres
    sélections ne sont pas conservées d'un lancement à l'autre, afin que vous ne commenciez jamais
    la journée avec un filtre périmé. Au cours d'une session, **chaque dossier de notes garde ses
    propres filtres** : en changeant de dossier puis en y revenant, vous les retrouvez, et un
    dossier que vous n'avez pas encore visité s'ouvre sur la vue de lancement.

### Files de nettoyage { #clean-up-queues }

Deux sections sont là pour vous aider à faire le ménage :

- **Non-standard → Only** liste les fichiers et dossiers du dossier de notes dont le nom n'est pas
  au format `<timestamp>~<type>~p<n>~<category>.md` — des éléments que vous y avez déposés et qui
  attendent encore des étiquettes. Ils sont toujours classés sous *aujourd'hui* dans le calendrier
  tant que vous ne les avez pas régularisés. Voir
  [Régulariser les fichiers isolés](organizing-notes.md#settle-loose-files). (Ils sont masqués par
  défaut : choisissez **Include** ou **Only** pour les voir.)
- **Bad taxonomy → Only** liste les notes dont le nom est bien formé mais qui utilisent un code non
  défini par le dossier. Sélectionnez-en une et réétiquetez-la avec **Ctrl+Shift+<code>**.

La barre de titre affiche le retard accumulé, par exemple `412 notes · 3 to settle`, quels que
soient les filtres. Les fichiers ou dossiers qui ont leur place dans le dossier de notes sans être
des notes — un script, le dossier d'un site web — peuvent être retirés de ce compte une fois pour
toutes avec **Note folders: leave out (one path per line)** (dossiers de notes : exclure, un chemin
par ligne) dans les [paramètres](settings.md).

## Calendrier de la semaine { #week-calendar }

Le bouton calendrier du groupe de vues affiche une semaine comme un agenda de bureau : sept colonnes
de jours, lundi en premier, en créneaux d'une demi-heure. Chaque note de **journal (J)** et de
**planification (P)** du dossier se place dans le créneau où tombe l'heure de son nom de fichier —
y compris les notes écrites depuis le formulaire d'ajout, sur le téléphone ou à la main. Chaque
entrée affiche son heure et les deux premières lignes de son titre ; survolez-la pour lire toute la
note.

| Pour… | Faites ceci |
|---|---|
| Changer de semaine | **‹** / **›** ou **Ctrl+PgUp** / **Ctrl+PgDn** ; **Today** revient à la semaine en cours |
| Ajouter une entrée | Cliquez sur un créneau vide. Le formulaire **New calendar entry** (nouvelle entrée de calendrier) s'ouvre avec ce jour et cette heure (tous deux modifiables), le type **Journal** pour un créneau passé ou **Planner** pour un créneau futur, la priorité, la catégorie et le texte. **Ctrl+Enter** enregistre. |
| Déplacer une entrée | Faites-la glisser vers un autre créneau |
| Modifier une entrée | Double-cliquez dessus pour changer sa date, son heure, son type, ses étiquettes ou son texte |
| La lire ou la modifier dans le panneau | Cliquez dessus (les touches fléchées parcourent les entrées comme des cartes) |

La note est enregistrée avec l'heure du créneau au lieu de « maintenant », si bien que le journal
d'hier tombe bien sur hier. Déplacer une entrée ne fait que renommer le fichier — l'heure est dans
son nom — et ses fichiers joints la suivent. Les entrées n'ont pas d'heure de fin. Le champ de
recherche restreint la semaine ; les filtres de la barre latérale ne s'y appliquent pas.

## Bandeau de hashtags { #hashtag-strip }

Sous le champ de texte, un bandeau d'une ligne liste chaque hashtag du dossier avec son compteur.
Désactivez **List every #hashtag under the add field** (Lister chaque #hashtag sous le champ
d'ajout) dans les paramètres pour n'afficher qu'un total compact.

## Rafraîchissement { #refresh }

Le tableau se met à jour de lui-même quand des fichiers changent sur le disque — par exemple quand
un outil de synchronisation apporte des notes d'une autre machine. **Ctrl+R** (ou le bouton de
rafraîchissement) relit le dossier à la demande.
