# Recherche, filtres et rapports

Il existe quatre façons de trouver des enregistrements, de la plus rapide à la plus puissante :

1. Les **filtres de liste** du module dans lequel vous vous trouvez.
2. Les **raccourcis de rapports** et les **recherches enregistrées** dans la barre latérale.
3. La **recherche globale**, sur tous les modules.
4. La page **Reports** (Rapports), pour des tableaux aux colonnes choisies que vous pouvez imprimer.

## Filtrer la liste d'un module { #filtering-a-modules-list }

En haut de chaque liste de module :

| Contrôle | Ce qu'il fait |
|---|---|
| Zone **Search …** (Rechercher) | Trouve les enregistrements dont le texte contient ce que vous tapez. Pendant qu'une recherche est active, chaque ligne montre *où* elle a trouvé une correspondance (par ex. **Description:** …) avec le terme en gras. |
| **Status**, **Type** (Statut, Type) | Afficher un seul statut ou type (pour les modules qui en ont). **Any** (Tous) efface le filtre. |
| **Priority** (Priorité) | Afficher une seule priorité, **P0**–**P5**. |
| ☆ (**Bookmarked only** — Favoris uniquement) | N'afficher que les enregistrements en favori. |
| **On day** (Ce jour-là) | Afficher les enregistrements appartenant à une date. Les journaux et les plans utilisent leur propre date ; les autres modules utilisent la date de création. |
| **Per page** (Par page) | 10, 25, 50, 100 ou 200 lignes — mémorisé par votre navigateur. |

Les filtres de rapport et de jour actifs apparaissent sous forme de pastilles sous les filtres ; cliquez sur le × d'une
pastille pour la retirer. Le nombre total d'enregistrements correspondants est affiché en bas, à côté de la pagination.

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

Tapez dans **Search everything…** (Tout rechercher) en haut de la barre latérale (ou appuyez sur **/**), puis
**Enter**. La page **Search** (Recherche) affiche les correspondances de tous les modules, regroupées par module, avec
les mots correspondants surlignés. Les correspondances de votre [dossier de notes locales](import-capture.md#local-notes)
apparaissent sous **Local notes** (Notes locales). Cliquez sur un résultat pour l'ouvrir.

Vous pouvez mélanger du texte libre et des **jetons de filtre** :

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
    Commencez une recherche par `~` pour trouver des **quasi-doublons** au lieu de correspondances exactes, par ex.
    `~ hand over the old laptop`. Chaque résultat affiche un pourcentage de similarité — utile avant de créer un
    enregistrement qui existe peut-être déjà.

!!! note "Aucun résultat du tout ?"
    Si la recherche globale ne trouve rien et affiche un avertissement indiquant que **the search index is empty**
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
