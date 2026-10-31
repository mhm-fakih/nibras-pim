# Boîte de réception : importer depuis le téléphone et l'ordinateur

Nibras repose sur le principe **capturer maintenant, classer plus tard** : les applications mobile
et de bureau vous permettent de noter des choses rapidement — hors ligne, sous forme de notes en
texte brut — et c'est dans l'application web que ces captures deviennent de véritables
enregistrements. Voir [Capturer puis classer](../concepts/capture-then-classify.md) pour l'idée
qui sous-tend ce fonctionnement.

Il existe quatre voies d'entrée :

| Source | Comment elle arrive | Où vous la traitez |
|---|---|---|
| **Application mobile** — une note que vous *envoyez* | Directement sous forme d'enregistrement dans son module, déjà mis en favori | Dans le module lui-même (regardez sous **★ Bookmarked** (★ Favoris) ou **Recently created** (Créés récemment)) |
| **Application de bureau / dossier de notes** | Sous forme de fichiers Markdown dans le dossier de notes lu par le serveur | **Local notes** (Notes locales) et **Import notes** (Importer des notes) |
| **Outils de capture** — Telegram, favoris Firefox, vidéos en ligne | Sous forme de notes écrites dans ce même dossier de notes | **Local notes** et **Import notes** |
| **N'importe quels fichiers** | Déposés dans un dossier du serveur, nommés comme une commande | **Import files** (Importer des fichiers) |

## Depuis le téléphone { #from-the-phone }

Lorsque vous envoyez une note depuis l'application mobile, elle est créée sur le serveur comme un
enregistrement dans le module indiqué par son type (tâche, journal, plan, note, objectif, écrit ou
ressource), avec sa priorité et son département, et **mise en favori**. Rien n'est à importer :
ouvrez le module et passez-la en revue. Si le téléphone était hors ligne, il envoie sa file
d'attente au retour de la connexion. Voir [Synchronisation](../concepts/sync.md).

## Depuis les outils de capture { #from-the-capture-tools }

Trois petits outils en ligne de commande font entrer des contenus extérieurs dans le dossier de notes, sous forme de
notes ordinaires prêtes pour **Import notes** :

| Outil | Ce qu'il écrit |
|---|---|
| **Telegram** | Une note **N** par exécution, *Telegram ‹chat› export*, avec les photos, vidéos et fichiers des messages dans son dossier compagnon. |
| **Favoris Firefox** | Une note **R** par exécution, *Firefox bookmarks export*, les favoris regroupés par date d'ajout. |
| **Média** (via yt-dlp) | Une note **R** par exécution : une ligne de votre main, puis pour chaque vidéo son titre, sa durée, son site et son adresse, avec la vidéo, un MP3 et les sous-titres dans le dossier compagnon. |

Leur priorité et leur département viennent des valeurs par défaut du dossier de notes ; lorsque le dossier ne définit
aucun département, les notes reçoivent le département **X**, si bien qu'elles n'arrivent jamais sans département.
Donnez-leur leur vrai département au moment du classement ou de l'import. N'oubliez pas que les notes **R** ont besoin
d'un type de ressource — voir l'astuce sous [Importer des notes](#import-notes). Les outils eux-mêmes s'installent sur
l'ordinateur qui les exécute ; voir le dossier `capture/` des sources de Nibras.

## Notes locales { #local-notes }

**Local notes** affiche le dossier de notes Markdown en texte brut qu'écrit l'application de
bureau — le même dossier que celui depuis lequel **Import notes** importe. Comme le serveur lit
lui-même ce dossier, vous pouvez le parcourir depuis n'importe quel navigateur.

- **By day** (Par jour) regroupe les notes selon leur date d'écriture ; **By subject** (Par sujet)
  les regroupe par département et par cours, à l'aide des `#tags` de chaque note.
- **Filter these notes — text, tag or course…** (Filtrer ces notes — texte, tag ou cours…) réduit
  la liste.
- Cliquez sur une note pour l'ouvrir : vous pouvez modifier le texte et l'enregistrer dans le
  fichier avec **Save** (Enregistrer), ou utiliser **Import as ‹module›** (Importer comme ‹module›)
  pour la transformer en enregistrement.
- **Add note** (Ajouter une note) écrit une nouvelle note dans le dossier. Choisissez un **Type**
  (N — Note, T — Task, J — Journal, R — Resource, P — Plan, G — Goal, W — Writing) et une
  **Priority** (Priorité), mettez le titre sur la première ligne, et ajoutez un tag tel que
  `#A.subject` pour la classer dans un département. Cliquez sur **Write note** (Écrire la note).
- L'icône d'actualisation (**Re-read the folder** — Relire le dossier) prend en compte les
  modifications faites ailleurs.

L'import depuis cette page vous explique ce qui va se passer : un enregistrement est créé (sous le
cours de la note, s'il y en a un), les éventuels fichiers associés sont copiés dans le dossier de
l'enregistrement, et la note est déplacée dans le sous-dossier `imported/` pour disparaître de la
liste. Ensuite, **Open the record** (Ouvrir l'enregistrement) vous y conduit.

!!! note "Rangement nocturne"
    Chaque nuit, le serveur range le dossier de notes : une note restée vide depuis plus d'un jour est déplacée dans le
    dossier `.deleted` (la corbeille de l'application de bureau), sauf si son dossier compagnon contient des fichiers,
    et les dossiers compagnons vides sont supprimés.

!!! note "Comment les sujets sont reconnus"
    Le tag `#A.subject` d'une note est comparé au champ **Note tags** (Tags de note) de vos
    **Courses** (Cours) — d'abord le tag canonique, puis les éventuels alias. Attribuez leurs tags
    à vos cours et la vue **By subject** les nommera correctement. Voir
    [Noms de fichiers et tags](../concepts/filenames-and-tags.md).

## Importer des notes { #import-notes }

**Import notes** importe en masse les notes du dossier, carte par carte.

Le **nom de fichier** de chaque note porte ses métadonnées —
`date~TYPE~pPRIORITY~DEPT.md`, par exemple `2026_06_15-214006~N~p3~A.md` :

| Partie | Définit |
|---|---|
| date et heure | la date de création de l'enregistrement |
| `TYPE` | le module cible, par son code (`N` note, `T` tâche, …) |
| `p3` | la priorité |
| `A` | le département, par sa lettre |

La première ligne de la note devient le titre et le reste la description. Les fichiers du dossier
associé à la note sont copiés dans le dossier de l'enregistrement. Voir
[Noms de fichiers et tags](../concepts/filenames-and-tags.md) et
[Dossiers associés](../concepts/companion-folders.md).

Pour importer :

1. Vérifiez l'**Import folder** (Dossier d'import) en haut (un dossier sur le serveur ; cliquez sur
   **Save** si vous le modifiez).
2. Si vous le souhaitez, réduisez les cartes avec les filtres **Type** et **Priority**.
3. Lisez chaque carte : ses pastilles indiquent le module, la priorité, le département et l'heure
   tirés du nom de fichier. **Similar existing records** (Enregistrements existants similaires), à
   côté, affiche les enregistrements aux titres proches — cliquez sur l'un d'eux pour le vérifier
   avant d'importer un doublon.
4. Cliquez sur **Import** (Importer). La carte du nouvel enregistrement apparaît juste sous la
   note, et le fichier de la note est déplacé dans `imported/`.

Une carte qui indique **Missing type, priority, department — can't import** (Type, priorité,
département manquants — import impossible) ou **unrecognized filename** (nom de fichier non
reconnu) doit d'abord voir son nom de fichier corrigé — renommez-le dans l'application de bureau,
puis rechargez la page.

!!! tip "Les notes de ressource ont besoin d'un type de ressource"
    Les notes de type **R** deviennent des ressources, et une ressource a besoin d'un type avant de
    pouvoir être classée. Choisissez un **Default resource type (for R notes)** (Type de ressource
    par défaut, pour les notes R) en haut de la page, ou cliquez sur l'une des pastilles de type
    d'une carte pour le remplacer (cliquez à nouveau pour revenir au type par défaut).

### Trouver les doublons { #finding-duplicates }

Le panneau **Find duplicates already in the database** (Trouver les doublons déjà présents dans la
base) analyse un module entier à la recherche d'enregistrements saisis deux fois. Choisissez le
**Module** et une **Method** (Méthode), puis cliquez sur **Scan** (Analyser) :

| Méthode | Ce qu'elle fait |
|---|---|
| **Fast — title match (instant)** (Rapide — correspondance de titre, instantané) | Compare les titres. |
| **Cheap AI — embeddings, same language** (IA économique — embeddings, même langue) | Compare le sens, au sein d'une même langue (environ une minute). |
| **Smart AI — any language** (IA avancée — toute langue) | Évalue les paires candidates avec un modèle de langage, d'une langue à l'autre (quelques minutes). |

Les méthodes d'IA ne sont disponibles que si le serveur dispose d'un service d'IA local ; sinon
elles sont grisées. Chaque paire trouvée affiche un score de similarité et un bouton **Merge**
(Fusionner), qui ouvre la même [boîte de dialogue de fusion](records.md#merging-duplicates) que le
glisser-déposer dans une liste.

## Importer des fichiers { #import-files }

**Import files** transforme d'un coup des fichiers et des dossiers en enregistrements. Sa partie
supérieure est [Pending sync](writings-fulltext.md#pending-sync) (Synchronisation en attente) ; la
partie inférieure, **Import files / folders by name** (Importer des fichiers / dossiers par nom),
fonctionne ainsi :

1. Placez des fichiers ou des dossiers dans un **dossier de dépôt** sur le serveur et nommez chacun
   comme une [commande par lot](batch-commands.md) — par exemple `t ?done Final report.pdf`.
2. Saisissez le **Drop folder** (Dossier de dépôt, mémorisé pour la prochaine fois) et choisissez le
   **Default module** (Module par défaut) pour les noms qui ne commencent pas par un code de module.
3. Cliquez sur **Scan / preview** (Analyser / aperçu). Chaque élément indique ce qu'il va créer
   (**Create ‹module›: “titre”**), ce qu'il va lui arriver et le dossier où il ira — ou pourquoi il ne
   peut pas être importé. Ce dossier suit la disposition réelle du module dans le dépôt 1, par type et
   par centaines d'identifiants, comme réglé dans [Settings → Modules](settings.md#modules).
4. Cliquez sur **Import** pour un seul élément, ou sur **Import all** (Tout importer).

Un **fichier** est déplacé dans le dossier repository-1 du nouvel enregistrement et renommé selon
son titre ; un **dossier** devient le dossier de l'enregistrement. Les éléments importés restent sur
la page, avec une ligne ✓ indiquant le nouvel enregistrement et l'endroit où sont allés ses fichiers,
jusqu'à la prochaine analyse.

## Renommeur d'images { #image-renamer }

**Image renamer** (Renommeur d'images) vous aide à ranger un dossier d'images (captures d'écran,
numérisations, photos) sur le serveur avant de les classer.

1. Saisissez le **Directory path** (Chemin du dossier, un dossier sur le serveur) et cliquez sur
   **Load** (Charger).
2. Parcourez les images et donnez à chacune un **File name** (Nom de fichier) parlant —
   l'extension est conservée pour vous. Le renommage passe à l'image suivante.

Il est conçu pour le clavier :

| Touche | Action |
|---|---|
| **←** / **h** | Image précédente |
| **→** / **l** / **Space** | Image suivante |
| **r** / **F2** / **Enter** | Modifier le nom |
| **Enter** (pendant la modification) | Renommer et passer à l'image suivante |
| **Esc** | Annuler la modification |
| **d** / **Del** | Supprimer — sans confirmation |
| **u** | Annuler la dernière suppression ou le dernier renommage |

Les images supprimées sont mises de côté plutôt que détruites, ce qui rend **u** possible.
