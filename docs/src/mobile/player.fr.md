# Lecteur multimédia

knotes intègre un lecteur audio et vidéo conçu pour venir à bout de longs enregistrements : cours, livres audio, mémos vocaux. Il se souvient de l'endroit où vous vous êtes arrêté dans chaque fichier, vous permet de poser des signets horodatés accompagnés chacun d'une note, et peut choisir pour vous ce qui sera lu ensuite.

## La médiathèque { #the-media-library }

Ouvrez **Médias** (l'icône du casque) depuis le rail. Cette page parcourt tous les fichiers audio et vidéo situés dans vos **Dossiers de la bibliothèque audio** (définis dans la visite de configuration ou dans les [Paramètres](settings.md) ; vous pouvez en ajouter plusieurs).

Trois onglets :

| Onglet | Comment il liste vos fichiers |
|---|---|
| **Bibliothèque** | Selon les étiquettes propres des fichiers : **Genres**, puis artiste, puis album. Les fichiers sans étiquettes sont rangés sous **Genre inconnu**, **Artiste inconnu** ou **Album inconnu**, jamais masqués. |
| **Dossiers** | L'arborescence des dossiers, un niveau à la fois. Chaque dossier donne un aperçu de ses premières pistes (**…et N autres**), avec **Signets récents ici** en haut. |
| **Avec signets** | Tous les fichiers qui ont des signets, du signet le plus récent au plus ancien, avec leur nombre de signets. Le moyen le plus rapide de reprendre une écoute inachevée. |

**Filtrer par nom…**, en haut, interroge toute la bibliothèque comme une seule liste à plat. La recherche porte sur les noms de fichiers, l'artiste, l'album et le genre. **Réanalyser la bibliothèque** (la flèche circulaire) relit les dossiers après l'ajout de fichiers.

Touchez n'importe quel fichier pour le lire. Il s'ouvre dans le lecteur et reprend là où vous vous étiez arrêté.

Les fichiers pris en charge comprennent l'audio MP3, M4A/M4B, AAC, Opus, OGG, WAV, FLAC et AMR, ainsi que la vidéo MP4, MKV, WebM, AVI, MOV et 3GP.

!!! tip "D'autres façons de lire"
    - Un audio ou une vidéo **joint à une note** est lu ici lorsque vous le touchez sur la carte de la note.
    - Depuis une autre application (un gestionnaire de fichiers, par exemple), choisissez **Ouvrir avec → knotes** sur un fichier audio ou vidéo.

## L'écran du lecteur { #the-player-screen }

De haut en bas :

- **Progression** : où vous en êtes dans le fichier, en pourcentage, et où vous en êtes dans le dossier (fichiers triés par nom).
- **Transcription** (uniquement si le fichier en a une). Voir [Sous-titres et transcriptions](#subtitles-and-transcripts).
- **Barre de progression** avec l'heure actuelle et la durée totale. Le temps s'écoule de gauche à droite dans toutes les langues.
- **Le nom du fichier**, et en dessous un petit lien rouge **Supprimer ce fichier**.
- **Note du signet (facultatif)** : saisissez ici un libellé pour le prochain signet.
- Le grand bouton **Ajouter un signet**.
- Les **boutons de lecture** :

    | Rangée du haut | | |
    |---|---|---|
    | **+10** avance | **Lecture** / **Pause** | **−10** recul |

    | Rangée du bas | | | |
    |---|---|---|---|
    | **Suivant** (⏭) | Liste **Dans ce dossier** | Bascule **Audio seul** | **−5** recul |

    La durée du saut **+10/−10** est réglable (voir [Réglages du lecteur](#player-settings)). **Audio seul** coupe l'image d'une vidéo, par exemple en conduisant, et affiche **audio** lorsqu'il est activé. Le bouton ⏭ porte une petite icône indiquant ce que fera **Lire ensuite**.

La lecture se poursuit en arrière-plan et écran éteint. Les commandes habituelles apparaissent dans la notification, sur l'écran de verrouillage et sur les casques Bluetooth. Retour vous ramène à l'application et laisse le [mini-lecteur](navigation.md#the-mini-player) en bas.

## Signets { #bookmarks }

Un signet est un moment du fichier, accompagné d'une ligne de texte facultative.

1. Pendant l'écoute, saisissez éventuellement un libellé dans **Note du signet (facultatif)**.
2. Touchez **Ajouter un signet**.

Le signet est placé quelques secondes **avant** le moment actuel (7 secondes par défaut), car c'est généralement la phrase que vous venez d'entendre qui vous intéresse. Le lecteur revient à ce point et confirme avec **✓ Signet −7 s**.

- **Avec les sous-titres activés**, un signet sans libellé saisi prend comme libellé la ligne de sous-titre en cours (**✓ Signet −7 s · réplique capturée**).
- **Touchez à nouveau rapidement pour augmenter sa priorité.** Un second appui dans les quelques secondes qui suivent (3 par défaut), toujours au même endroit, n'ajoute pas un autre signet. Il ajoute à la place une étiquette de priorité : `p2`, puis `p3` au troisième appui, et ainsi de suite (**✓ priorité ajoutée p2**).
- **Signet à la pause** : lorsque cette option est cochée dans les Réglages du lecteur, chaque pause (à l'écran, depuis le câble d'un casque ou en Bluetooth) pose un signet. Le bouton pause affiche un petit badge de signet tant qu'elle est active. Pratique si vous risquez de vous endormir en écoutant.

Ouvrez le panneau **Signets** (l'icône de signet, sous les commandes) pour voir les signets du fichier, du plus récent au plus ancien. Touchez-en un pour y aller, ou touchez son icône de corbeille pour le supprimer.

### Où sont stockés les signets { #where-bookmarks-are-stored }

Les signets sont enregistrés dans un petit fichier texte à côté du fichier multimédia, portant le même nom avec l'extension `.tmk` :

```
lecture.mp3
lecture.tmk
```

```
[12:04] p2 the key definition
[1:03:15] summary starts here
```

Comme il s'agit d'un simple fichier à côté de l'enregistrement, les signets l'accompagnent jusqu'à votre ordinateur et retour. Les lignes que l'application ne reconnaît pas sont conservées, jamais écrasées. Voir [Signets (.tmk)](../concepts/bookmarks-tmk.md).

## Les panneaux { #the-panels }

Une rangée d'icônes sous les commandes ouvre un panneau à la fois. Touchez à nouveau la même icône pour le fermer.

| Icône | Panneau |
|---|---|
| Signet | **Signets** : les signets de ce fichier (ouvert par défaut) |
| Dossier | **Dans ce dossier** : les autres fichiers du même dossier ; touchez-en un pour le lire |
| Dossier ouvert | **Dossiers récents** : les dossiers depuis lesquels vous avez lu ; touchez-en un pour reprendre son fichier le plus récent |
| Horloge | **Lus récemment** : les fichiers que vous avez lus récemment |
| Engrenage | **Réglages du lecteur** |

L'application se souvient du panneau que vous aviez ouvert.

## Réglages du lecteur { #player-settings }

| Réglage | Plage | Effet |
|---|---|---|
| **Saut N s** | 5 à 60 s (10 par défaut) | Durée des grands boutons de saut. |
| **Vitesse** | 0,75× à 1,25× | Vitesse de lecture. |
| **Signet −N s** | 0 à 20 s (7 par défaut) | Recul appliqué lors de la pose d'un signet. |
| **Prio. 2 appuis N s** | 1 à 10 s (3 par défaut) | Délai dans lequel un second appui doit suivre pour augmenter la priorité au lieu d'ajouter un signet. |
| **Volume** | −20 à +50 dB | Augmente ou baisse le volume de ce lecteur, pour les enregistrements faibles. |
| **Lire ensuite** | voir ci-dessous | Ce qui se passe à la fin d'un fichier, et ce que fait ⏭. |
| **Minuterie de veille** | **Non**, 15 min, 30 min, 45 min, 60 min | Met la lecture en pause au bout de ce délai. Affiche **pause dans …** pendant le décompte. Fonctionne écran éteint. |
| **Signet à la pause** | activé/désactivé | Chaque pause ajoute un signet. |

Chaque curseur dispose aussi de boutons − et + pour un réglage fin. Ces réglages sont conservés d'une session à l'autre.

### Lire ensuite { #play-next }

| Mode | Ce qui est lu ensuite |
|---|---|
| **Non** | Rien ne démarre tout seul à la fin d'un fichier. |
| **Même dossier** | Le fichier suivant du dossier est lu automatiquement. |
| **Ancien au hasard** | Un fichier pris n'importe où dans vos dossiers de bibliothèque, qui n'a jamais reçu de signet et n'a pas été touché depuis 90 jours. Si aucun fichier n'est encore aussi ancien, les plus anciens sont utilisés. |
| **Au hasard ici** | Le même genre de choix, mais uniquement dans le dossier de ce qui est en cours de lecture, sous-dossiers compris. Dans le dossier de pièces jointes d'une note, la recherche porte sur le dossier qui contient toutes les notes : elle parcourt ainsi les pièces jointes de toutes les notes. |
| **Plus récent ici** | Le même dossier que **Au hasard ici**, mais avec les fichiers sans signet les plus récents au lieu des plus anciens, pour rattraper ce qui vient d'arriver. |

S'il ne reste rien à choisir, le lecteur l'indique, par exemple **Plus rien ici — chaque fichier de ce dossier a un signet ou a déjà été lu.**

## Sous-titres et transcriptions { #subtitles-and-transcripts }

Le lecteur reconnaît deux types de fichiers texte compagnons placés à côté d'un fichier multimédia et portant le même nom :

- **Sous-titres** (`.srt`) : `lecture.en.srt`, `lecture.fr.srt` ou simplement `lecture.srt`. Une rangée **Sous-titres** affiche une puce par langue plus **Non**, et la ligne en cours s'affiche sous la barre de progression, pour l'audio comme pour la vidéo.
- **Transcriptions** (`.ai.txt`) : `lecture.en.ai.txt`, `lecture.ar.ai.txt` ou simplement `lecture.ai.txt`, par exemple des transcriptions automatiques faites sur votre ordinateur. Elles n'ont pas de minutage : tout le texte s'affiche donc dans une zone **Transcription** déroulante au-dessus des commandes, avec une puce par langue. Utilisez **Masquer la transcription** / **Afficher la transcription** pour la replier.

Les transcriptions sont en lecture seule sur le téléphone, mais vous pouvez en sélectionner et copier le texte. Une transcription peut être supprimée avec son bouton de suppression (**Supprimer la transcription …**).

## Supprimer un enregistrement { #deleting-a-recording }

Touchez **Supprimer ce fichier** sous le nom du fichier. La boîte de dialogue **Supprimer ce fichier ?** liste tout ce qui part avec lui : ses fichiers de transcription et ses signets. Il n'y a pas de corbeille, le fichier ne peut donc pas être récupéré. Après la suppression, le lecteur passe à la suite.
