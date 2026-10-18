# Noms de fichiers et étiquettes

Chaque note Nibras porte ses métadonnées **dans son nom de fichier**. Cette convention unique est partagée,
octet pour octet, par le téléphone, le bureau, le terminal, l'application web et le serveur.

## Le schéma { #the-scheme }

```text
<timestamp>~<type>~p<priority>~<category>.md
```

```text
2026_06_10-143025~T~p2~A.md
│                 │  │  └─ category  A
│                 │  └──── priority  2
│                 └─────── type      T
└───────────────────────── timestamp 2026-06-10 14:30:25
```

| Champ | Format | Remarques |
|-------|--------|-------|
| horodatage | `yyyy_MM_dd-HHmmss` | Le moment où la note a été créée. Ne change jamais ensuite ; c'est donc l'identité stable de la note et sa « date de création ». |
| type | un code court, généralement une lettre | *De quel genre de chose* il s'agit — p. ex. `T` tâche, `N` note, `J` journal. Peut être vide. |
| priorité | `p` + un nombre | Son importance. Le préfixe `p` permet aux applications de repérer ce champ sans ambiguïté. |
| catégorie | un code court, généralement une lettre | *Le domaine* auquel elle appartient (un département, un sujet, un projet). Peut être vide. |

`~` sépare les champs. Le type et la catégorie peuvent tous deux être vides ; ceci est donc aussi une note valide :

```text
2026_06_11-090512~~p3~.md
```

### Collisions dans la même seconde { #same-second-collisions }

Si deux notes devaient recevoir le même nom, l'application ajoute `-2`, `-3`, … à l'horodatage :

```text
2026_06_10-143025~N~p3~B.md
2026_06_10-143025-2~N~p3~B.md
```

Ce suffixe est ignoré lors de la lecture de la date de création.

### Les codes viennent de votre vocabulaire { #codes-come-from-your-vocabulary }

Les lettres signifient ce que votre vocabulaire leur fait signifier :

- **Sans serveur**, chaque application tient sa propre liste : le bureau la stocke par dossier dans
  `.knotes.json`, le téléphone dans ses paramètres. Modifiez-les librement.
- **Avec un serveur**, les applications de capture téléchargent le vocabulaire : les types viennent des
  **modules** du serveur et les catégories de ses **départements mis en signet**. Ainsi, les lettres d'une note correspondent
  exactement à un type d'enregistrement et à un département lors de l'import. Voir [Synchronisation](sync.md#vocabulary-download).

Codes de type courants du serveur :

| Code | Module | | Code | Module |
|------|--------|-|------|--------|
| `T` | Tâches | | `G` | Objectifs |
| `N` | Notes | | `W` | Écrits |
| `J` | Journaux | | `R` | Ressources |
| `P` | Planificateurs | | | |

## Réétiqueter, c'est renommer { #retagging-is-renaming }

Changer le type, la priorité ou la catégorie d'une note revient simplement à **renommer le fichier**. L'horodatage reste ;
la note garde donc sa place dans le temps. C'est ce qui rend
[capturer maintenant, classer plus tard](capture-then-classify.md) si peu coûteux.

!!! note "Création ou modification"
    Les applications lisent la « création » dans l'**horodatage du nom de fichier** et la « modification » dans la date de
    modification du fichier lui-même. Réétiqueter ou modifier une note met à jour sa date de modification, mais jamais sa
    date de création — et un outil de synchronisation qui réécrit les dates de modification ne peut pas non plus brouiller votre
    chronologie.

## Quels fichiers comptent comme notes { #which-files-count-as-notes }

La règle utilisée pour *compter* les notes est partout la même :

- uniquement les fichiers `.md`,
- dont le nom correspond au schéma ci-dessus,
- directement dans le dossier (jamais dans des sous-dossiers).

Tout le reste est soit ignoré (fichiers cachés, dossiers d'intendance, dossiers compagnons), soit affiché comme
élément **needs metadata** (métadonnées requises) que vous devez régulariser.

!!! warning "Conservez le format d'horodatage par défaut"
    Le bureau et le téléphone vous permettent de changer le motif d'horodatage, mais la règle de comptage partagée
    attend `yyyy_MM_dd-HHmmss`. Les notes nommées selon un autre motif ne seront pas reconnues comme des notes
    canoniques par les autres applications ni par le serveur.

## Anciens formats { #legacy-formats }

| Format | D'où il vient | Comment il est traité |
|--------|--------------------|-------------------|
| `<timestamp>~p<priority>~<subjects>.txt` (plusieurs sujets reliés par `-`) | Premières versions du téléphone | Le téléphone les liste et les lit toujours, et les réécrit selon le schéma `.md` actuel la prochaine fois que vous en modifiez une. Les autres applications les affichent comme *needs metadata*. |
| Tout `.md` ou `.txt` au nom libre | Déposé par un autre programme | Téléphone : listé sans étiquettes, géré une fois modifié. Bureau : *needs metadata* ; le régulariser le renomme selon le schéma, garde son nom d'origine comme première ligne, et le date d'après la date de modification du fichier. |
| Tout autre fichier ou dossier | Téléchargements, exports, numérisations | Bureau : le régulariser crée une nouvelle note et déplace l'élément dans le [dossier compagnon](companion-folders.md) de cette note. |

## Comment chaque application lit le nom { #how-each-app-reads-the-name }

| Application | Comportement |
|-----|-----------|
| [Téléphone](../mobile/index.md) | Lit toutes les notes canoniques ainsi que les anciens `.txt`. Panneau de filtres avec facettes type / priorité / catégorie, plus deux files de nettoyage : **Noms non standard** et **Classement invalide** (un nom bien formé utilisant un code que votre vocabulaire ne définit pas). |
| [Bureau](../desktop/index.md) | Tableau et kanban regroupés selon les trois étiquettes. `Ctrl+Shift+<letter>` réétiquette la note sélectionnée ; `Ctrl+Shift+<digit>` définit sa priorité. Les deux mêmes files de nettoyage figurent dans la barre latérale. |
| [Terminal](../terminal/index.md) | Utilise le code de stockage du bureau. Une ligne telle que `t3d buy milk` enregistre une note de type `t`, de priorité `3`, de catégorie `d`. |
| [Application web](../web/index.md) | La page **Local notes** (Notes locales) lit le dossier de notes sur le disque du serveur. Lorsque vous y écrivez une note, sa lettre de catégorie est tirée du premier hashtag `#X.something` de la note. |
| Import serveur | Exige un type d'une lettre correspondant à un module et une catégorie non vide ; prend la date dans l'horodatage et la priorité dans le jeton `p`. |

!!! tip "Ordre des étiquettes à l'écran"
    Le bureau affiche les étiquettes d'une note dans l'ordre **type · priorité · catégorie** (p. ex. `T 2 A`) — le même
    ordre que dans le nom de fichier — et les garde de gauche à droite, même sur une note arabe.
