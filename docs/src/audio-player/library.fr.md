# Bibliothèque

Les parties gauche et supérieure de la fenêtre servent à trouver quoi écouter : une arborescence de dossiers, une barre d'emplacement,
un navigateur à trois colonnes et la liste de lecture.

## Parcourir les dossiers { #browse-folders }

L'arborescence **FOLDERS** (dossiers) part de votre **dossier racine** — réglez-le dans les [Paramètres](settings.md) ou avec le
bouton ⚙ au-dessus de l'arborescence. Si le dossier n'existe pas, l'arborescence revient à votre dossier personnel.

- Seuls les dossiers contenant des médias quelque part à l'intérieur sont listés, de sorte que les dossiers de notes ou de
  documents ne vous gênent pas.
- Déplier un dossier ne charge jamais rien ; **double-cliquez** dessus pour le charger dans la bibliothèque.
- Le chargement lit le dossier **et tous ses sous-dossiers**. La barre d'état en bas affiche un indicateur
  pendant le travail, puis le nombre de fichiers trouvés.

Le bouton dossier de la barre de titre place le clavier dans l'arborescence des dossiers.

## Aller à un chemin { #go-to-a-path }

Le champ **Go to path…** (aller au chemin) au-dessus de l'arborescence (**Ctrl+L**) accepte un chemin saisi ou collé. Appuyez sur **Enter** (Entrée)
ou cliquez sur la flèche :

| Vous saisissez | Ce qui se passe |
|---|---|
| Un dossier | Il est sélectionné dans l'arborescence et chargé |
| Un fichier multimédia | Son dossier est chargé et la lecture du fichier commence |
| Tout autre fichier (par exemple un `.srt`) | Son dossier est chargé |
| Quelque chose qui n'existe pas | Le champ devient rouge et le lecteur le signale |

Les chemins peuvent être entre guillemets, commencer par `~`, contenir des `$VARIABLES` ou être des liens `file://`. Pendant la saisie,
les sous-dossiers correspondants sont proposés en complétion.

## Affiner : genres, artistes, albums { #narrow-down-genres-artists-albums }

Les trois colonnes au-dessus de la liste de lecture fonctionnent comme une bibliothèque musicale classique : choisissez un genre pour restreindre les
artistes, un artiste pour restreindre les albums, et la liste de lecture suit. Les tags sont lus dans les fichiers
eux-mêmes au chargement du dossier.

**Untagged only** (sans tags uniquement, en haut à droite) n'affiche que les fichiers sans genre, artiste ni album — pratique pour
[taguer un dossier d'un seul coup](tag-editing.md#tag-a-whole-folder).

## La liste de lecture { #the-playlist }

| Colonne | Affiche |
|---|---|
| **#** | Numéro de piste |
| **Title** (titre) | Le tag de titre, ou le nom du fichier |
| **Artist** (artiste), **Album** | Depuis les tags du fichier |
| **Length** (durée) | Durée |
| **Bkm** | Nombre de [marque-pages](bookmarks.md) du fichier |

- **Cliquez** sur une piste pour la lire. La liste de lecture telle qu'elle est affichée devient la file d'attente pour
  **Previous track** (piste précédente) / **Next track** (piste suivante).
- **Enter** lit la ligne sélectionnée.
- Cliquez sur un en-tête de colonne pour trier ; faites glisser les en-têtes pour les réorganiser. L'ordre est mémorisé.
- La piste en cours de lecture est affichée en gras et reste visible.

## Marqués récemment { #recently-bookmarked }

Sous l'arborescence des dossiers, **RECENTLY BOOKMARKED** (marqués récemment) liste les fichiers auxquels vous avez ajouté des marque-pages le plus récemment
(les plus récents en premier). Cliquez sur l'un d'eux pour le lire.

## Formats pris en charge { #supported-formats }

`.mp3` `.m4a` `.m4b` `.aac` `.ogg` `.oga` `.opus` `.flac` `.wav` `.wma` `.mka` `.webm` `.mp4`
`.m4v` `.mkv` `.mov` `.avi`

La lecture de chacun dépend des plugins GStreamer installés (voir
[Installation](index.md#install)).
