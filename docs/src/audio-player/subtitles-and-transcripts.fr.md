# Sous-titres et transcriptions

Le lecteur prend en compte deux types de fichiers texte placés à côté d'un enregistrement et portant le même nom :

| Type | Nom de fichier | Horodaté ? | Affichage |
|---|---|---|---|
| Sous-titres | `talk.en.srt`, `talk.srt` | Oui | Une ligne à la fois, synchronisée avec la lecture |
| Transcriptions | `talk.ar.ai.txt`, `talk.en.ai.txt`, `talk.ai.txt` | Non | Le texte entier dans le panneau latéral **TRANSCRIPT** (transcription) |

Lorsqu'un fichier possède l'un ou l'autre, une rangée de pastilles apparaît sous son titre.

## Sous-titres { #subtitles }

Les sous-titres sont des fichiers `.srt` standard. La partie entre le nom de l'enregistrement et `.srt` indique la
langue : `talk.en.srt` donne une pastille **en**. Un simple `talk.srt` donne une pastille **sub**.

1. Ouvrez l'enregistrement. Les pastilles **Subtitles:** (sous-titres) apparaissent, avec **Off** (désactivé) sélectionné.
2. Cliquez sur la pastille d'une langue.

La ligne en cours s'affiche par-dessus la vidéo ou — pour l'audio — juste au-dessus de la barre de recherche. Cliquez sur **Off** pour
masquer les sous-titres.

!!! tip "Astuce"
    Avec les sous-titres activés, un marque-page ajouté sans saisir d'étiquette reçoit comme étiquette la ligne en
    cours. Voir [Marque-pages](bookmarks.md#with-a-label).

## Transcriptions { #transcripts }

Une transcription est un fichier texte brut UTF-8 se terminant par `.ai.txt` — par exemple la sortie d'un outil de
reconnaissance vocale. Elle ne comporte pas d'horodatage, le lecteur affiche donc le texte entier dans le panneau **TRANSCRIPT** à
droite du lecteur, comme des paroles de chanson.

- Les pastilles **Transcript:** (transcription) indiquent chaque langue trouvée (`talk.ar.ai.txt` → **ar** ; un simple
  `talk.ai.txt` → **txt**).
- Le panneau **s'ouvre de lui-même** lorsqu'un fichier possède une transcription. Si vous avez choisi une langue pour un
  fichier précédent, cette langue est de nouveau choisie lorsqu'elle est disponible.
- Les transcriptions en arabe sont affichées de droite à gauche. Les autres langues suivent le sens de leur texte.
  Voir [Langues](../concepts/languages.md).

### Corriger une transcription { #correct-a-transcript }

Le panneau est modifiable — corrigez ce que l'outil de transcription a mal compris. Vos modifications sont réenregistrées dans le
fichier `.ai.txt` peu après que vous arrêtez de taper, et toujours avant de changer de langue, d'ouvrir un autre
fichier ou de quitter. Appuyez sur **Ctrl+S** pour enregistrer immédiatement. La ligne de message confirme chaque enregistrement.

### Commencer une nouvelle transcription { #start-a-new-transcript }

Pour chacune des langues **ar** et **en** que le fichier ne possède pas encore, un bouton **+ ar** / **+ en** apparaît.
Cliquez sur l'un d'eux pour créer un `talk.ar.ai.txt` (ou `.en.ai.txt`) vide et commencez à taper dans le panneau. Le
bouton sur lequel vous appuyez détermine la langue ; le fichier garde ce nom.

### Supprimer une transcription { #delete-a-transcript }

Chaque pastille de transcription possède son propre petit bouton corbeille. Un clic place ce fichier dans la corbeille de votre bureau
**immédiatement, sans confirmation** — restaurez-le depuis la corbeille si vous changez d'avis.

!!! note "D'où viennent les transcriptions"
    Le lecteur se contente de lire, modifier et créer ces fichiers — il ne transcrit pas lui-même l'audio. Tout
    outil qui écrit `<recording>.<lang>.ai.txt` à côté de l'enregistrement convient.
