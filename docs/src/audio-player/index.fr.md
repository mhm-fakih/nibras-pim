# Lecteur audio

Le **lecteur audio** est un lecteur de bureau pour les enregistrements audio et vidéo — cours, conférences, mémos
vocaux, podcasts. Son point fort est le **marque-page** : une seule touche marque l'instant que vous venez d'entendre, et
les marques sont enregistrées dans un petit fichier texte `.tmk` à côté de l'enregistrement, dans le même format que celui
de l'[application mobile](../mobile/index.md). Synchronisez le dossier, et les marque-pages créés sur le téléphone apparaissent
ici, et inversement.

Autour de cela, il propose une bibliothèque par dossiers, une barre de recherche avec forme d'onde, des sous-titres, des transcriptions
modifiables et l'édition des tags.

## La fenêtre en un coup d'œil { #the-window-at-a-glance }

```
┌───────────────┬──────────────────────────────────────────────────┐
│ FOLDERS       │  Genres │ Artists │ Albums                         │
│ Go to path…   ├─────────────────┬──────────────────┬─────────────┤
│ ⚙ root folder │  PLAYLIST       │  PLAYER          │ TRANSCRIPT  │
│               │                 │  title · waveform│ (when the   │
├───────────────┤                 │  transport       │  file has   │
│ RECENTLY      │  Tag → …        │  BOOKMARKS       │  one)       │
│ BOOKMARKED    │                 │  Speed · Vol     │             │
└───────────────┴─────────────────┴──────────────────┴─────────────┘
  status bar
```

- **FOLDERS** (dossiers) — parcourez votre dossier d'enregistrements ; double-cliquez sur un dossier pour le charger. Voir
  [Bibliothèque](library.md).
- **Genres / Artists / Albums** (genres / artistes / albums) et **PLAYLIST** (liste de lecture) — affinez la sélection et choisissez une piste.
- **PLAYER** (lecteur) — vidéo, barre de recherche avec forme d'onde, commandes et liste des marque-pages. Voir
  [Lecture](playback.md) et [Marque-pages](bookmarks.md).
- **TRANSCRIPT** (transcription) — une transcription modifiable, lorsque le fichier en possède une. Voir
  [Sous-titres et transcriptions](subtitles-and-transcripts.md).
- **RECENTLY BOOKMARKED** (marqués récemment) — les fichiers que vous avez marqués dernièrement ; cliquez sur l'un d'eux pour le lire.

## Installation { #install }

Le lecteur est un programme Python 3 qui utilise GTK 3 pour la fenêtre et GStreamer pour la lecture. `mutagen`
est facultatif — il n'est nécessaire que pour **écrire** les tags.

=== "Debian / Ubuntu / Mint"

    ```bash
    sudo apt install python3-gi gir1.2-gtk-3.0 gir1.2-gst-1.0 gir1.2-gstpbutils-1.0 \
         python3-mutagen gstreamer1.0-plugins-good gstreamer1.0-plugins-bad \
         gstreamer1.0-plugins-ugly gstreamer1.0-gtk3
    ```

=== "Fedora"

    ```bash
    sudo dnf install python3-gobject gtk3 gstreamer1 gstreamer1-plugins-base python3-mutagen \
         gstreamer1-plugins-good gstreamer1-plugins-bad-free gstreamer1-plugins-ugly-free
    ```

=== "Arch"

    ```bash
    sudo pacman -S python-gobject gtk3 gstreamer gst-plugins-base python-mutagen \
         gst-plugins-good gst-plugins-bad gst-plugins-ugly
    ```

Les formats réellement lisibles dépendent des plugins GStreamer installés ; les paquets ci-dessus couvrent
tous les formats listés dans [Bibliothèque](library.md#supported-formats).

!!! note "Linux uniquement"
    Le lecteur audio est conçu pour les bureaux Linux. Il n'existe pas de lanceur Windows.

## Lancement { #run }

```bash
cd audio-player
./run.sh                    # checks the requirements, then starts
./run.sh ~/Music/talk.mp3   # open a file and start playing it
```

`run.sh` vous indique quels paquets installer s'il manque quelque chose.

**Entrée de menu.** Exécutez `./install.sh` une fois. Il ajoute **Audio Player** (lecteur audio) à votre menu d'applications ; lancez-le
depuis là, puis faites un clic droit sur son icône dans le panneau et choisissez **Pin** (épingler). Relancez `install.sh` si vous déplacez
le dossier.

## Premier lancement { #first-run }

1. Appuyez sur **Ctrl+,** pour ouvrir **Settings** (paramètres), et réglez **Folder-tree root** (racine de l'arborescence) sur le dossier qui contient vos
   enregistrements (utilisez **Browse…** (parcourir) pour le choisir). Restez ciblé — votre dossier d'enregistrements, pas tout votre
   dossier personnel.
2. Dans **FOLDERS**, double-cliquez sur un dossier. Ses fichiers multimédias (sous-dossiers compris) sont chargés dans la
   liste de lecture.
3. Cliquez sur une piste pour la lire.
4. Pendant la lecture, appuyez sur **B** pour marquer l'instant que vous venez d'entendre.

Le lecteur s'ouvre en plein écran et mémorise sa disposition, son thème, le dernier dossier et le dernier fichier. La fois suivante, il
ouvre ce fichier en pause, à la position où vous vous étiez arrêté.

## Où sont stockées les données { #where-things-are-stored }

| Quoi | Où |
|---|---|
| Marque-pages | `<recording>.tmk` à côté de chaque enregistrement — voir [Marque-pages](bookmarks.md) |
| Sous-titres | `<recording>.<lang>.srt` à côté de l'enregistrement |
| Transcriptions | `<recording>.<lang>.ai.txt` à côté de l'enregistrement |
| Paramètres, positions de reprise, fichiers récents | `config.json` à côté du programme |

Le lecteur n'utilise pas le réseau.
