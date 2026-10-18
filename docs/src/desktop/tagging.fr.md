# Type, priorité et catégorie

Chaque note porte trois étiquettes, et toutes trois figurent dans son nom de fichier :

```
2026_06_10-143025~W~p3~A.md
                  │  │  └── category  A
                  │  └───── priority  3
                  └──────── type      W
```

Le type et la catégorie sont des codes courts (généralement une lettre) ; la priorité est un
nombre. N'importe lequel de ces codes peut être vide. Voir
[Noms de fichiers et étiquettes](../concepts/filenames-and-tags.md) pour le schéma complet.

## Étiqueter une nouvelle note { #tag-a-new-note }

Le formulaire d'ajout comporte trois rangées de pastilles :

| Rangée | Ce qu'elle définit | Raccourci |
|---|---|---|
| **Type** | Le code de type de la note | **Ctrl+Shift+<letter>** |
| **Prio** (priorité) | La priorité (**P1**, **P2**, …) | **Ctrl+Shift+<digit>** |
| **Cat** (catégorie) | Le code de catégorie | **Ctrl+Shift+<letter>** |

Cliquez sur une pastille, ou appuyez sur son raccourci, avant d'enregistrer. Appuyer de nouveau sur
un raccourci de type ou de catégorie l'efface. Survolez une pastille pour voir son nom complet et
son raccourci. Votre choix est **persistant** — il reste sélectionné pour la note suivante.

Les raccourcis suivent la touche physique ; ils continuent donc de fonctionner pendant que vous
tapez en arabe.

!!! warning "Gardez des codes de type et de catégorie distincts"
    Les raccourcis de type et de catégorie partagent **Ctrl+Shift+<letter>**. Si la même lettre est
    à la fois un type et une catégorie, le raccourci choisit le type. Attribuez-leur des lettres
    différentes.

## Réétiqueter une note existante { #retag-an-existing-note }

1. Sélectionnez une carte sur le tableau (cliquez dessus ou utilisez les touches fléchées).
2. Appuyez sur **Ctrl+Shift+<letter>** pour définir son type ou sa catégorie, ou sur
   **Ctrl+Shift+<digit>** pour définir sa priorité.

Le fichier est renommé sur-le-champ ; son contenu, sa date de création et ses
[fichiers joints](attached-files.md) le suivent. La vue ne saute pas : vous pouvez donc parcourir
une colonne en réétiquetant carte après carte.

!!! tip "Réétiquetage en masse"
    Choisissez **Date created** (date de création) dans le menu de tri de la barre d'outils avant
    une grosse session de réétiquetage. Avec le tri par défaut **Last updated** (dernière
    modification), une note réétiquetée part à la fin puisque son fichier vient de changer.

Réétiqueter depuis le tableau *définit* toujours une valeur ; cela n'en efface jamais.

## Définir vos propres codes { #define-your-own-codes }

Les codes disponibles sont définis **par dossier de notes** — chaque dossier peut avoir son propre
vocabulaire. Ouvrez **Settings** (Paramètres, **Ctrl+,**) et faites défiler jusqu'à
**Tag options for folder “X”** (options d'étiquettes du dossier « X ») :

| Paramètre | Format |
|---|---|
| **Priority min** / **Priority max** (priorité min. / max.) | La plage des pastilles de priorité, par exemple de 1 à 5 |
| **Default priority** (priorité par défaut) | La priorité avec laquelle démarre une nouvelle note |
| **Types (code:name per line; * = default)** (types) | Un `code:name` par ligne |
| **Categories (code:name per line; * = default)** (catégories) | Un `code:name` par ligne |

Placez un `*` sur le code avec lequel les nouvelles notes doivent démarrer. Par exemple :

```
N*:Note
W:Work
H:Home
I:Ideas
T:Todo
```

Le `*` marque seulement la valeur par défaut ; il ne fait jamais partie du code ni du nom de
fichier. Si vous en étoilez plusieurs, le premier l'emporte.

Ces options sont enregistrées dans un fichier caché `.knotes.json` à l'intérieur du dossier ; elles
voyagent donc avec lui. La première fois qu'un dossier est utilisé, il est initialisé à partir de
vos paramètres actuels.

!!! note "Codes provenant d'un serveur Nibras"
    Si le dossier est connecté à un serveur Nibras, ses types et catégories sont remplacés par les
    codes du serveur à chaque lancement. Voir [Se connecter à un serveur Nibras](nibras-server.md).

## Étiquettes non reconnues { #tags-that-dont-match }

Les notes synchronisées depuis ailleurs peuvent porter des codes que ce dossier ne définit pas (par
exemple le type `Y`). Elles s'affichent quand même sur le tableau, et la barre latérale liste le
code inconnu dans sa facette en *italique ambré*. Pour les retrouver toutes, utilisez la section
**Bad taxonomy** (taxonomie invalide) de la barre latérale — voir
[Tableau, barre latérale et filtres](board-and-filters.md#clean-up-queues).
