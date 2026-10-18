# Édition des tags

Le lecteur peut écrire les tags les plus courants — titre, artiste, album et genre — directement dans vos
fichiers multimédias, de sorte que tout autre lecteur les affiche aussi.

!!! note "Nécessite mutagen"
    L'écriture des tags nécessite le paquet Python `mutagen` (`python3-mutagen` sur Debian/Ubuntu,
    `python-mutagen` sur Arch). Sans lui, tout le reste fonctionne, le bouton **Apply** (appliquer) est désactivé,
    et les modifications de titre signalent qu'elles n'ont pas pu être enregistrées.

## Renommer le titre du fichier en cours de lecture { #rename-the-playing-files-title }

1. Cliquez dans le champ **Title** (titre) en haut du lecteur.
2. Saisissez le nouveau titre.
3. Appuyez sur **Enter** (Entrée).

La ligne de message affiche `✓ Title saved` et la liste de lecture se met à jour. Le nom du fichier lui-même ne
change pas — survolez le champ pour le voir.

## Taguer un dossier entier { #tag-a-whole-folder }

La barre **Tag →** (taguer) sous la liste de lecture écrit les mêmes **Genre**, **Artist** (artiste) et **Album** dans chaque
fichier actuellement listé dans la liste de lecture.

1. Chargez un dossier (voir [Bibliothèque](library.md)) et restreignez la liste de lecture aux fichiers voulus —
   **Untagged only** (sans tags uniquement) est souvent le bon point de départ.
2. Remplissez n'importe lesquels des champs **Genre**, **Artist** et **Album**. Les champs vides sont laissés tels quels.
3. Cliquez sur **Apply** et confirmez (`Write these tags to N file(s)?`).

Le dossier est ensuite relu, de sorte que le navigateur Genres / Artists / Albums reflète les nouveaux tags.

## Quels formats peuvent être tagués { #which-formats-can-be-tagged }

| Format | Tags écrits en tant que |
|---|---|
| MP3 | ID3v2 (titre, artiste, album, genre) |
| FLAC, Ogg, Opus | Commentaires Vorbis |
| M4A / MP4 | Tags MP4 standard |

Les fichiers qui ne peuvent pas être tagués — certains formats vidéo et moins courants — sont ignorés et comptés dans le
message de résultat (par ex. `Tagged 12 file(s), 2 unsupported`). Rien d'autre n'est modifié.
