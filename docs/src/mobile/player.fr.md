# Lecteur multimédia

Nibras Mobile intègre un lecteur audio et vidéo conçu pour venir à bout de longs enregistrements : cours, livres audio, mémos vocaux. Il se souvient de l'endroit où vous vous êtes arrêté dans chaque fichier, vous permet de poser des signets horodatés accompagnés chacun d'une note, et peut choisir pour vous ce qui sera lu ensuite.

## La médiathèque { #the-media-library }

Ouvrez **Médias** (l'icône du casque) depuis le rail. Cette page parcourt tous les fichiers audio et vidéo situés dans vos **Dossiers de la bibliothèque audio** (définis dans la visite de configuration ou dans [Paramètres](settings.md) → **Médias** ; vous pouvez en ajouter plusieurs).

Trois onglets :

| Onglet | Comment il liste vos fichiers |
|---|---|
| **Bibliothèque** | Selon les étiquettes propres des fichiers : **Genres**, puis artiste, puis album. Les fichiers sans étiquettes sont rangés sous **Genre inconnu**, **Artiste inconnu** ou **Album inconnu**, jamais masqués. |
| **Dossiers** | L'arborescence des dossiers, un niveau à la fois. Chaque dossier donne un aperçu de ses premières pistes (**…et N autres**), avec **Signets récents ici** en haut. |
| **Avec signets** | Tous les fichiers qui ont des signets, du signet le plus récent au plus ancien, avec leur nombre de signets. Le moyen le plus rapide de reprendre une écoute inachevée. |

**Filtrer par nom…**, en haut, interroge toute la bibliothèque comme une seule liste à plat. La recherche porte sur les noms de fichiers, l'artiste, l'album et le genre. **Réanalyser la bibliothèque** (la flèche circulaire) relit les dossiers après l'ajout de fichiers. Le dernier fichier lu est mis en évidence dans chaque liste.

Touchez n'importe quel fichier pour le lire. Il s'ouvre dans le lecteur et reprend là où vous vous étiez arrêté.

Les fichiers pris en charge comprennent l'audio MP3, M4A/M4B, AAC, Opus, OGG, WAV, FLAC et AMR, ainsi que la vidéo MP4, MKV, WebM, AVI, MOV et 3GP.

!!! tip "D'autres façons de lire"
    - L'icône **Lecteur** de l'écran d'accueil, installée à côté de celle de Nibras Mobile, ouvre directement le lecteur sur la piste chargée ou la dernière lue.
    - Un audio ou une vidéo **joint à une note** est lu ici lorsque vous le touchez sur la carte de la note.
    - Depuis une autre application (un gestionnaire de fichiers, par exemple), choisissez **Ouvrir avec → Nibras Mobile** sur un fichier audio ou vidéo.

**Pages du lecteur multimédia**, dans [Paramètres](settings.md) → **Médias**, désactive les pages **Médias** et **Vidéos**. Elles quittent le rail, mais les fichiers ouverts depuis une note ou depuis une autre application sont toujours lus.

## Vidéos { #videos }

**Vidéos** (l'icône de film, juste sous Médias dans le rail) affiche toutes les vidéos du **Dossier des vidéos** et de ses sous-dossiers, des plus récentes aux plus anciennes, sous forme de cartes. Chaque carte montre une image de la vidéo avec sa durée, une barre en bas indiquant jusqu'où vous l'avez regardée, et en dessous le dossier, depuis quand le fichier est arrivé, sa taille et son nombre de signets.

- Quand les vidéos sont réparties dans plusieurs dossiers, une rangée de puces restreint la page à un seul dossier. **Toutes** les affiche de nouveau.
- **Relire le dossier** (la flèche circulaire) regarde à nouveau après l'arrivée de nouveaux fichiers.
- Touchez une carte pour la lire dans le lecteur, image comprise. Signets, transcriptions et reprise fonctionnent comme pour tout autre fichier.

Le **Dossier des vidéos** se règle dans [Paramètres](settings.md) → **Médias**. Par défaut, c'est un dossier `Nibras` à la racine du stockage.

## L'écran du lecteur { #the-player-screen }

Le lecteur occupe tout l'écran. La barre du haut indique la part du fichier déjà lue, en pourcentage, puis une barre et un compteur (**3/12**) pour votre avancée dans le dossier (fichiers triés par nom), et une étoile. En dessous se trouve une rangée d'[onglets](#the-tabs). Le premier, **Lecteur**, réunit les commandes. De haut en bas :

- **La vidéo**, pour les fichiers vidéo. **Agrandir la vidéo** lui donne presque tout l'écran en gardant dessous la barre de progression et les boutons de lecture et de saut. **Réduire la vidéo** ramène toutes les commandes.
- **Transcription** (uniquement si le fichier en a une). Voir [Sous-titres et transcriptions](#subtitles-and-transcripts).
- **Barre de progression** avec l'heure actuelle et la durée totale. Les signets y sont marqués. Le temps s'écoule de gauche à droite dans toutes les langues.
- **Le nom du fichier** et le dossier d'où il est lu. En dessous, quatre petites actions : une corbeille rouge (**Supprimer ce fichier**), **Dossier** (ouvre le dossier dans votre gestionnaire de fichiers), une icône de partage (**Partager** envoie le fichier à une autre application) et **Favori** (voir [Fichiers favoris](#starred-files)).
- **Note du signet (facultatif)** : saisissez ici un libellé pour le prochain signet.
- Le grand bouton **Ajouter un signet**, à côté du nom.
- Les **boutons de lecture** :

    | Rangée du haut | | |
    |---|---|---|
    | **+10** avance | **Lecture** / **Pause** | **−10** recul |

    | Rangée du bas | | | |
    |---|---|---|---|
    | **Suivant** (⏭) | Liste **Dans ce dossier** | Bascule **Audio seul** | **−5** recul |

    La durée du saut **+10/−10** est réglable (voir [Réglages du lecteur](#player-settings)). **Audio seul** coupe l'image d'une vidéo, par exemple en conduisant, et affiche **audio** lorsqu'il est activé. Le bouton ⏭ porte une petite icône indiquant ce que fera **Lire ensuite**.

- **Sous-titres**, quand le fichier en a.
- **Signets (N)** : les signets de ce fichier. Voir [Signets](#bookmarks).

La lecture se poursuit en arrière-plan et écran éteint. Les commandes habituelles apparaissent dans la notification, sur l'écran de verrouillage et sur les casques Bluetooth. Retour vous ramène à l'application et laisse le [mini-lecteur](navigation.md#the-mini-player) en bas.

### Avant le fichier suivant { #before-the-next-file-starts }

Quand un fichier se termine et que **Lire ensuite** s'apprête à en lancer un autre, un bandeau annonce **Fichier suivant dans 5 s**, avec un bouton **Arrêter**. Il apparaît sur tous les onglets et dans le mini-lecteur. Touchez **Arrêter** pour rester sur le fichier terminé. Ouvrir un fichier, ou appuyer vous-même sur lecture ou ⏭, l'annule aussi.

## Signets { #bookmarks }

Un signet est un moment du fichier, accompagné d'une ligne de texte facultative.

1. Pendant l'écoute, saisissez éventuellement un libellé dans **Note du signet (facultatif)**.
2. Touchez **Ajouter un signet**.

Le signet est placé quelques secondes **avant** le moment actuel (7 secondes par défaut), car c'est généralement la phrase que vous venez d'entendre qui vous intéresse. Le lecteur revient à ce point et confirme avec **✓ Signet −7 s**.

- **Avec les sous-titres activés**, un signet sans libellé saisi prend comme libellé la ligne de sous-titre en cours (**✓ Signet −7 s · réplique capturée**).
- **Touchez à nouveau rapidement pour augmenter sa priorité.** Un second appui dans les quelques secondes qui suivent (3 par défaut), toujours au même endroit, n'ajoute pas d'autre signet et ne recule pas une seconde fois. Il ajoute à la place une étiquette de priorité : `p2`, puis `p3` au troisième appui, et ainsi de suite (**✓ priorité ajoutée p2**).
- **Signet à la pause** : lorsque cette option est active, chaque pause (à l'écran, depuis le câble d'un casque ou en Bluetooth) pose un signet. Le bouton pause affiche un petit badge de signet tant qu'elle est active. Pratique si vous risquez de vous endormir en écoutant. Par défaut, elle suit votre connexion Bluetooth : voir [Réglages du lecteur](#player-settings).

Les signets du fichier sont listés dans l'onglet **Lecteur**, sous les commandes, sous le titre **Signets (N)**, du point le plus avancé dans le fichier au plus proche du début. Touchez-en un pour y aller, ou touchez son icône de corbeille pour le supprimer.

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

## Fichiers favoris { #starred-files }

Un signet marque un moment ; un **favori** marque le fichier entier, pour le retrouver vite. Touchez **Favori** sous le nom du fichier, ou l'étoile de la barre du haut, pour mettre en favori le fichier en cours. Touchez-la de nouveau (**Retirer des favoris**) pour l'enlever.

Les favoris sont listés dans l'onglet **Favoris**, du plus récent au plus ancien. Touchez-en un pour le lire, ou touchez son étoile pour le retirer. Un favori déplacé ailleurs est masqué jusqu'à son retour, et supprimer un fichier depuis le lecteur retire aussi son étoile.

## Les onglets { #the-tabs }

Chaque onglet est une icône, la plupart avec un compteur. L'application se souvient de l'onglet que vous aviez ouvert.

| Icône | Onglet |
|---|---|
| Lecture | **Lecteur** : les commandes et les signets de ce fichier |
| Étoile | **Favoris** : vos [fichiers favoris](#starred-files) |
| Dossier | **Dans ce dossier** : les autres fichiers du même dossier |
| Dossier ouvert | **Dossiers récents** : les dossiers depuis lesquels vous avez lu ; touchez-en un pour reprendre son fichier le plus récent |
| Horloge | **Lus récemment** : les fichiers que vous avez lus récemment |
| Engrenage | **Réglages du lecteur** |

Ouvrir un fichier depuis l'une de ces listes le lance et vous ramène à l'onglet **Lecteur**.

## Réglages du lecteur { #player-settings }

| Réglage | Plage | Effet |
|---|---|---|
| **Saut N s** | 5 à 60 s (10 par défaut) | Durée des grands boutons de saut. |
| **Vitesse** | 0,75× à 1,25× | Vitesse de lecture. |
| **Signet −N s** | 0 à 20 s (7 par défaut) | Recul appliqué lors de la pose d'un signet. |
| **Prio. 2 appuis N s** | 1 à 10 s (3 par défaut) | Délai dans lequel un second appui doit suivre pour augmenter la priorité au lieu d'ajouter un signet. |
| **Volume** | −20 à +50 dB | Augmente ou baisse le volume de ce lecteur, pour les enregistrements faibles. |
| **Fichiers récents conservés** | 10 à 200 (50 par défaut) | Combien de fichiers **Lus récemment** garde en mémoire. |
| **Lire ensuite** | voir ci-dessous | Ce qui se passe à la fin d'un fichier, et ce que fait ⏭. |
| **Lire la suite à la fin d'une vidéo** | activé/désactivé (désactivé par défaut) | Désactivé : une vidéo terminée reste en pause au début, quel que soit le mode **Lire ensuite**. ⏭ lit toujours la suivante. |
| **Minuterie de veille** | **Non**, 15 min, 30 min, 45 min, 60 min | Met la lecture en pause au bout de ce délai. Affiche **pause dans …** pendant le décompte. Fonctionne écran éteint. |
| **Minuteur de nuit** | activé/désactivé (activé par défaut) | Une lecture lancée la nuit se met en pause d'elle-même. Voir ci-dessous. |
| **Signet à la pause** | activé/désactivé | Chaque pause ajoute un signet. |
| **Suivre le Bluetooth** | activé/désactivé (activé par défaut) | **Signet à la pause** s'active de lui-même tant qu'un audio Bluetooth (casque, voiture, enceinte) est connecté, et se désactive sinon. Tant que cette option est active, la case du dessus montre ce que la connexion a décidé et ne se coche pas à la main. |

Chaque curseur dispose aussi de boutons − et + pour un réglage fin. Ces réglages sont conservés d'une session à l'autre.

### Minuteur de nuit { #night-sleep-timer }

Avec **Minuteur de nuit** activé, tout ce que vous lancez entre **De** et **Jusqu'à** (21:00 et 05:00 par défaut) se met en pause après **Minuteur** minutes (20 par défaut, de 5 à 90). Pendant ce temps, la minuterie de veille affiche **minuteur de nuit : pause dans …**. Désactiver le minuteur de nuit le laisse désactivé jusqu'à la fin de cette nuit.

### Lire ensuite { #play-next }

| Mode | Ce qui est lu ensuite |
|---|---|
| **Non** | Le fichier suivant du dossier est préparé, en pause. Rien ne démarre tout seul. |
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

Touchez la corbeille rouge (**Supprimer ce fichier**) sous le nom du fichier. La boîte de dialogue **Supprimer ce fichier ?** liste tout ce qui part avec lui : ses fichiers de transcription et ses signets. Il n'y a pas de corbeille, le fichier ne peut donc pas être récupéré. Après la suppression, le lecteur passe à la suite.
