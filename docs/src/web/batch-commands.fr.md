# Modification et saisie par lots

Nibras dispose d'un petit **langage de commandes** pour définir des champs en tapant plutôt qu'en
cliquant. Les mêmes jetons fonctionnent lors de la modification de nombreuses fiches à la fois
(**modification par lots**), lors de la création de nombreuses fiches à partir de lignes de texte
(**saisie par lots**), dans **Import files** (Importer des fichiers) et — en tant que filtres — dans la
[recherche globale](search.md#global-search).

## Les jetons { #the-tokens }

| Jeton | Définit | Exemple |
|---|---|---|
| `p3` | la priorité | `p2` |
| `#code` | le type (codes des types de *ce* module) | `#art` |
| `?code` | le statut | `?done` |
| `@code` | le contexte | `@home` |
| `dCODE` | le département | `dA` |
| `cCODE` | le cours | `cALG` |
| `=code` | le champ code propre à la fiche | `=alg` |
| `*` / `*-` | mettre en favori / retirer le favori | `*` |
| `!` / `!-` | urgent / non urgent | `!` |
| `(DATE` | la date de début | `(2026-07-17` |
| `)DATE` | la date de fin | `)+3` |
| `'xx` | la langue | `'en` |

Les codes sont les codes courts définis sur la page [Parameters](administration.md#parameters) et sur
les départements et les cours. Les codes sont résolus par rapport au module dans lequel vous
travaillez ; ainsi `#art` sur une ressource désigne un type de *ressource*.

### Écrire les dates { #writing-dates }

`DATE` accepte plusieurs formes ; l'heure vaut 00:00 par défaut et l'année est l'année en cours :

| Forme | Signification |
|---|---|
| `2026-07-17` | une date complète |
| `2026-07-17_1600` | une date complète à 16:00 |
| `295` | semaine ISO 29, jour 5 (vendredi ; 1 = lundi … 7 = dimanche) |
| `295_25` | la même chose, en 2025 |
| `295_1600` | semaine 29, vendredi, 16:00 |
| `-1`, `+3`, `0` | des jours relatifs à aujourd'hui |
| `-1_1600` | hier à 16:00 |

### Étiquettes de langue { #language-tags }

Une apostrophe initiale suivie de l'un des codes de langue définis dans
[Settings](settings.md#tasks-and-other-preferences) (par défaut `ar`, `en`, `fr`, `fa`, `de`) définit
la langue d'une fiche. Tout ce qui ne figure pas dans la liste reste du texte ordinaire, de sorte
que des mots comme *don't* ne sont jamais pris pour une étiquette. Voir
[Langues](../concepts/languages.md).

## Modification par lots { #batch-edit }

1. Dans la liste d'un module, cochez les cases des fiches à modifier (la case de l'en-tête
   sélectionne toute la page).
2. Une barre bleue apparaît : **n selected** (n sélectionnées). Tapez une commande, p. ex.
   `p3 ?done *-`.
3. Appuyez sur **Enter** ou cliquez sur **Apply** (Appliquer). **Clear** (Effacer) annule la
   sélection.

Un message confirme combien de fiches ont été mises à jour et quels champs ont changé. Chaque
modification est consignée dans le journal d'audit. En modification par lots, un jeton inconnu est
une erreur ; une faute de frappe ne modifie donc rien.

## Saisie par lots { #batch-entry }

**Batch entry** (Saisie par lots, barre latérale) crée une fiche par ligne.

1. Choisissez le **Default module** (Module par défaut).
2. Tapez ou collez des lignes. Les jetons définissent des champs ; les mots restants deviennent le
   titre. Une ligne peut commencer par un code de module pour remplacer celui par défaut :

    ```text
    t p3 ?done @home a task done at home
    n a quick note
    c dA =alg Algebra
    ```

3. Cliquez sur **Add n records** (Ajouter n fiches).

Les fiches créées s'affichent sous forme de cartes. Les lignes en échec restent dans la zone avec
leur erreur, pour que vous puissiez les corriger et relancer.

!!! tip "Astuce"
    En saisie par lots, un `cCODE` ou `dCODE` qui ne correspond à aucun cours ou département réel est
    conservé dans le titre, de sorte qu'un mot comme « car » n'est pas avalé.

Vous pouvez créer de cette façon des tâches, des journaux, des plans, des ressources, des notes, des
objectifs, des écrits et des cours (ceux qui sont activés).
