# Recherche, filtres et rapports

Il existe quatre façons de trouver des enregistrements, de la plus rapide à la plus puissante :

1. Les **filtres de liste** du module dans lequel vous vous trouvez.
2. Les **raccourcis de rapports** et les **recherches enregistrées** dans la barre latérale.
3. La page **Search** (Recherche), sur tous les modules, vos notes locales et les fichiers du serveur.
4. La page **Reports** (Rapports), pour des tableaux aux colonnes choisies que vous pouvez imprimer.

## Filtrer la liste d'un module { #filtering-a-modules-list }

En haut de chaque liste de module :

| Contrôle | Ce qu'il fait |
|---|---|
| Zone **Search …** (Rechercher) | Trouve les enregistrements dont le texte contient ce que vous tapez. Ceux dont le titre commence par ce texte viennent en premier, puis ceux dont le titre le contient, puis les autres. Pendant qu'une recherche est active, chaque ligne montre *où* elle a trouvé une correspondance (par ex. **Description:** …) avec le terme en gras. |
| **Active only** (Actifs uniquement — Tasks, Goals) | Coché par défaut : n'affiche que les enregistrements dont le statut est marqué actif, et ceux sans statut. Le menu **Status** ne propose alors que les statuts actifs. Décochez-le pour voir aussi le travail terminé. |
| **Status**, **Type** (Statut, Type) | Afficher un seul statut ou type (pour les modules qui en ont). **Any** (Tous) efface le filtre. |
| **Context** (Contexte — Tasks) | Afficher les tâches d'un seul contexte. |
| **Priority** (Priorité) | Afficher une seule priorité, **P0**–**P5**. |
| ☆ (**Bookmarked only** — Favoris uniquement) | N'afficher que les enregistrements en favori. |
| **On day** (Ce jour-là) | Afficher les enregistrements appartenant à une date. Les journaux et les plans utilisent leur propre date ; les autres modules utilisent la date de création. |

Quand vous ouvrez le menu **Status**, **Type**, **Context** ou **Priority**, chaque option indique combien
d'enregistrements elle donnerait, compte tenu des autres filtres définis ; le nombre à côté de **Any** est le total. Une
option qui affiche 0 viderait la liste.

Les filtres de rapport et de jour actifs apparaissent sous forme de pastilles sous les filtres ; cliquez sur le × d'une
pastille pour la retirer. Le nombre d'enregistrements correspondants (**n items**) est affiché en bas, à côté de la
pagination, avec à côté le choix **Per page** (Par page) — 10, 25, 50, 100 ou 200 lignes, mémorisé par votre navigateur.

## Raccourcis de rapports dans la barre latérale { #report-shortcuts-in-the-sidebar }

Sous **‹Module› reports** (rapports du module), la barre latérale propose des filtres prêts à l'emploi pour le module
courant. Chacun indique combien d'enregistrements correspondent ; ceux qui sont vides sont grisés.

| Rapport | Affiche |
|---|---|
| **★ Bookmarked** (Favoris) | Les enregistrements en favori |
| **🚩 Urgent** (Urgent) | Les tâches urgentes |
| **No status** / **No type** (Sans statut / Sans type) | Les enregistrements auxquels il manque encore ce classement |
| **No department** / **No priority** (Sans département / Sans priorité) | Les enregistrements pas encore classés ou priorisés |
| **With tags** (Avec étiquettes) | Les enregistrements ayant au moins une étiquette |
| **Overdue** / **Due soon (7d)** (En retard / Échéance proche, 7 j) | Les enregistrements dont la date de fin est passée / tombe dans la semaine |
| **High priority (≥3)** (Priorité haute) | Priorité 3 et plus |
| **Recently created** / **Recently updated** (Créés / Modifiés récemment) | L'activité récente |

Seuls les rapports pertinents pour le module sont listés (par exemple, **Overdue** uniquement là où les
enregistrements ont une date de fin). Votre administrateur peut masquer cette section dans
[Settings → Features](settings.md#pages-and-features).

Le lien **RSS (bookmarked)** (RSS des favoris) sous les rapports est décrit dans [Flux RSS](rss.md).

## Recherches enregistrées { #saved-searches }

Sous les rapports, **Saved searches** (Recherches enregistrées) liste vos propres recherches pour le module courant.
Cliquez sur l'une d'elles pour l'exécuter. Cliquez sur l'engrenage (**Manage saved searches** — Gérer les recherches
enregistrées) pour en ajouter, en modifier ou en supprimer :

1. Cliquez sur **New report** (Nouveau rapport).
2. Donnez-lui un **Name** (Nom) et une **Priority** (Priorité, utilisée pour l'ordre).
3. Choisissez un **Query type** (Type de requête) :
    - **Search form** (Formulaire de recherche) — un ensemble de filtres de liste, écrits sous forme de paramètres,
      par ex. `bookmarked=true&highPriority=true`. L'exécuter filtre simplement la liste.
    - **HQL** — une requête sur les enregistrements de l'application, par ex. `from Task where bookmarked = true`.
      Les recherches enregistrées écrites pour l'ancienne application Nibras continuent de fonctionner : leurs
      requêtes sont adaptées aux enregistrements actuels au moment de l'exécution.
    - **SQL (native)** (SQL natif) — une requête brute sur la base de données.
4. Cliquez sur **Preview** (Aperçu) pour l'essayer, puis sur **Save** (Enregistrer).

Les requêtes qui renvoient des enregistrements les affichent sous forme de cartes dans le volet central — cliquez sur
l'une d'elles pour l'ouvrir, ou sur le × pour revenir à la liste. Les autres résultats (comptages, colonnes) s'ouvrent
dans une boîte de dialogue en tableau.

!!! warning "Le SQL est puissant"
    Une recherche enregistrée en SQL natif exécute exactement ce que vous écrivez. Une instruction qui modifie des
    données affiche « *n* row(s) affected » — elle les a réellement modifiées. Utilisez **Preview** avec prudence, et
    préférez **Search form** ou **HQL** lorsque vous voulez seulement consulter.

## Recherche globale { #global-search }

La page **Search** (Recherche) parcourt tous les modules à la fois, ainsi que votre
[dossier de notes locales](import-capture.md#local-notes) et les fichiers conservés sur le serveur. Ouvrez-la avec la
loupe (**Search everything**, Tout rechercher) de la dernière ligne de la barre latérale, ou appuyez sur **/** depuis
n'importe où.

Remplissez ce que vous savez et appuyez sur **Enter** dans n'importe quel champ, ou cliquez sur **Search** :

| Champ | Signification |
|---|---|
| **Words** (Mots) | Le texte à chercher. |
| **Priority** (Priorité) | Une priorité, **P1**–**P5**, ou **Any** (Toutes). |
| **Status code**, **Type code**, **Context code** (Code de statut, de type, de contexte) | Le code court d'un statut, d'un type ou d'un contexte — celui affiché à côté de son nom, par ex. `done`, `art`, `home`. |
| **Bookmarked only** (Favoris uniquement) | Uniquement les enregistrements en favori. |
| **Near-duplicates** (Quasi-doublons) | Chercher des enregistrements qui disent déjà la même chose — voir l'astuce ci-dessous. |

À côté du bouton **Search**, la page affiche la recherche sous forme d'une ligne de texte (par exemple
`linux p4 ?done *`). Cette même ligne passe dans l'adresse de la page : une recherche peut donc être mise en favori dans
le navigateur ou relancée.

Sous le formulaire, **Search in** (Chercher dans) choisit où le texte est cherché. **All** (Tout) est la valeur par
défaut ; cliquez sur une ou plusieurs des autres pastilles pour restreindre :

| Pastille | Cherche dans |
|---|---|
| **Title / summary** (Titre / résumé) | Les titres et résumés des enregistrements ; la première ligne, le nom de fichier et les étiquettes d'une note locale. |
| **Description / notes** | Les descriptions, notes et autres textes des enregistrements ; le reste d'une note locale. |
| **File names** (Noms de fichiers) | Les noms des fichiers et dossiers des dépôts et des dossiers compagnons des notes. |
| **File contents** (Contenu des fichiers) | Le texte lu dans ces fichiers (PDF, documents, texte…). |

Les résultats sont regroupés par module, avec les mots correspondants surlignés ; les correspondances de votre dossier de
notes apparaissent sous **Local notes** (Notes locales), et les fichiers sous **Files** (Fichiers). Cliquez sur un
résultat pour l'ouvrir.

### Fichiers { #files }

Le groupe **Files** liste les fichiers correspondants avec leur source, leur dossier et leur taille (**name match**
lorsque seul le nom correspond), ainsi qu'un extrait du texte autour de la correspondance.

- Restreignez la liste avec les pastilles de source (**Repo 1**, **Repo 2**, **Repo 3**, **Note files** — fichiers des
  notes) et le menu **Type** (**PDF**, **Documents**, **Spreadsheets** — tableurs, **Slides** — présentations,
  **Text**, **Audio**, **Video**, **Images**).
- La pastille à droite d'un fichier nomme l'enregistrement (ou la note) auquel il appartient — cliquez dessus pour
  ouvrir cet enregistrement.
- Les deux icônes **Open the file** (Ouvrir le fichier) et **Open its folder** (Ouvrir son dossier) les ouvrent sur
  l'ordinateur qui fait tourner le serveur.

Les dossiers parcourus, et les types de fichiers dont le contenu est lu, se règlent dans
[Settings → File search](settings.md#file-search). Seuls les mots libres sont cherchés dans les fichiers ; les codes et
les filtres s'appliquent aux enregistrements.

### Jetons de filtre { #filter-tokens }

La zone **Words** accepte aussi des **jetons de filtre**, mélangés au texte libre — les autres champs du formulaire en
écrivent simplement certains pour vous :

| Jeton | Signification | Exemple |
|---|---|---|
| `p4` | priorité 4 | `p4 linux` |
| `#code` | code de type | `#art budget` |
| `?code` | code de statut | `?done report` |
| `@code` | code de contexte | `@home` |
| `dCODE` | code de département | `dA` |
| `cCODE` | code de cours | `cALG` |
| `*` / `*-` | en favori / pas en favori | `* travel` |
| `!` / `!-` | urgent / non urgent | `! invoice` |
| `'xx` | langue (codes définis dans Settings) | `prayer 'ar` |

`d…` et `c…` ne sont traités comme des filtres que si le code existe vraiment ; les mots ordinaires commençant par ces
lettres sont donc toujours recherchés comme du texte.

!!! tip "Vérifiez les doublons avant d'ajouter"
    Cochez **Near-duplicates** (ou commencez les mots par `~`) pour trouver des enregistrements *semblables* plutôt que
    des correspondances exactes, par ex. `~ hand over the old laptop`. Chaque résultat affiche un pourcentage de
    similarité — utile avant de créer un enregistrement qui existe peut-être déjà. Les notes et les fichiers sont
    exclus d'une recherche de quasi-doublons.

!!! note "Aucun résultat du tout ?"
    Si la page Search ne trouve rien et affiche un avertissement indiquant que **the search index is empty**
    (l'index de recherche est vide), un administrateur doit cliquer une fois sur **Rebuild search index** (Reconstruire
    l'index de recherche) sur la page [Admin](administration.md#admin-page).

## Une journée à travers tous les modules { #a-day-across-all-modules }

Cliquez sur une date dans le mini-calendrier de la barre latérale. Si le module Journal est activé, cela ouvre le
journal de ce jour (voir [Calendrier](calendar.md#the-mini-calendar)). Si Journal est désactivé, le volet central
affiche à la place tous les enregistrements de ce jour dans l'ensemble des modules activés.

## La page Reports { #the-reports-page }

**Reports** (barre latérale) construit des tableaux que vous pouvez exécuter de nouveau et imprimer.

1. Cliquez sur **New report**.
2. Saisissez un **Report name** (Nom du rapport), choisissez un **Record type** (Type d'enregistrement) et le nombre de
   **Rows per page** (Lignes par page).
3. Sous **Tick what to show** (Cochez ce qu'il faut afficher), choisissez les colonnes ; ordonnez-les avec les flèches
   haut et bas sous **Order, left to right** (Ordre, de gauche à droite).
4. Sous **Which records** (Quels enregistrements), choisissez :
    - **Point and click** (Pointer-cliquer) — **Contains text** (Contient le texte), **Status**, **Type**, **Tag**
      (Étiquette), **Created from** / **Created to** (Créé du / au), plus n'importe lequel des préréglages de rapports
      de la barre latérale ; ou
    - **Query** (Requête) — écrivez vous-même une requête HQL, par ex. `from Book where type.code = 'art' order by id
      desc`. Le `order by` de la requête détermine le tri.
5. Choisissez **Sort by** (Trier par) et le sens, ajoutez des **Notes** facultatives, vérifiez l'aperçu et cliquez sur
   **Save**.

Dans la liste des rapports, utilisez **Run** (Exécuter) pour l'afficher dans l'application, **Printable view** (Vue
imprimable) pour ouvrir une page épurée noir sur blanc dans un nouvel onglet (puis cliquez sur **Print** — Imprimer),
**Edit** (Modifier) ou **Delete** (Supprimer).

!!! note
    Les valeurs apparaissent sous leur nom complet (un type de ressource s'affiche « Article », et non son code). Les
    très gros rapports sont tronqués à une taille sûre dans la vue imprimable, et la page l'indique.
