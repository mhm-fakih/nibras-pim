# Lecture

Le volet **PLAYER** (lecteur) regroupe tout ce qui concerne le fichier en cours de lecture.

## Ouvrir quelque chose à lire { #open-something-to-play }

- Cliquez sur une piste de la liste de lecture (voir [Bibliothèque](library.md)).
- Appuyez sur **Ctrl+O** (ou sur le bouton d'ouverture de la barre de titre) et choisissez un fichier.
- Collez le chemin d'un fichier dans **Go to path…** (aller au chemin, **Ctrl+L**) et appuyez sur **Enter** (Entrée).
- Lancez le lecteur avec un fichier : `./run.sh ~/Music/talk.mp3`.
- Cliquez sur un fichier sous **RECENTLY BOOKMARKED** (marqués récemment).

Lorsque vous ouvrez un fichier isolément (pas depuis la liste de lecture), les autres fichiers multimédias de son dossier deviennent
la file d'attente, dans l'ordre des noms.

## Commandes { #controls }

De haut en bas :

| Commande | Ce qu'elle fait |
|---|---|
| Zone vidéo | Affichée uniquement pour les fichiers comportant de la vidéo |
| Champ **Title** (titre) et bouton corbeille | Le titre du fichier — modifiez-le et appuyez sur **Enter** pour l'enregistrer comme tag (voir [Édition des tags](tag-editing.md)). Survolez-le pour voir le nom du fichier. Le bouton corbeille place le fichier dans la corbeille |
| Pastilles Subtitles / Transcript (sous-titres / transcription) | Affichées lorsque le fichier en possède — voir [Sous-titres et transcriptions](subtitles-and-transcripts.md) |
| Barre de recherche avec forme d'onde | Cliquez ou faites glisser pour vous déplacer ; les marque-pages apparaissent sous forme de traits |
| Temps | Écoulé, restant et total |
| Transport | **Previous track** (piste précédente), retour, **Play / Pause** (lecture / pause), avance, **Next track** (piste suivante) |
| Champ de marque-page et **＋ Bookmark** (ajouter un marque-page) | Voir [Marque-pages](bookmarks.md) |
| **Speed** (vitesse) | De 0.75× à 1.25×, sans modifier la hauteur du son |
| **Sleep** (minuterie) | Met en pause après une durée choisie (uniquement si activé dans les paramètres) |
| **Vol** | De 0 à 3× — au-delà du repère central, les enregistrements faibles sont amplifiés |

Les larges boutons retour et avance sautent de la durée **Skip forward / back** (saut avant / arrière ; 10 secondes par défaut ;
modifiable dans les [Paramètres](settings.md)).

## La barre de recherche avec forme d'onde { #the-waveform-seek-bar }

La barre de recherche est dessinée par-dessus le profil sonore de l'enregistrement : les passages forts apparaissent comme de hautes barres, les passages calmes
restent plats, et la partie déjà lue est teintée. C'est un moyen rapide de repérer où quelqu'un commence à
parler, ou de sauter un long silence.

La forme d'onde est calculée en arrière-plan après l'ouverture d'un fichier, la lecture ne l'attend donc jamais.
Pour les très gros fichiers, elle est ignorée — le lecteur vous le signale — car elle nécessite de lire tout le
fichier. Augmentez ou supprimez la limite avec **Waveform limit (MB)** (limite de forme d'onde, en Mo) dans les [Paramètres](settings.md).

## Reprendre là où vous vous étiez arrêté { #resume-where-you-left-off }

Le lecteur mémorise votre position dans chaque fichier écouté plus de quelques secondes.
Rouvrez le fichier et la lecture reprend à cet endroit. Au démarrage suivant, le dernier fichier lu est
rouvert, en pause.

## Fin du fichier { #end-of-file }

Lorsqu'un fichier se termine, le lecteur **s'arrête**. Il n'enchaîne pas sur l'enregistrement suivant — appuyez sur
**Next track** ou cliquez sur le suivant dans la liste de lecture quand vous êtes prêt.

## Minuterie de mise en veille { #sleep-timer }

La minuterie est masquée tant que vous n'activez pas **Sleep timer** (minuterie de mise en veille) dans les [Paramètres](settings.md). Un
menu **Sleep** apparaît alors à côté du volume : choisissez une durée (15, 30, 45 ou 60 minutes par défaut) et
la lecture se met en pause une fois le temps écoulé. Choisissez **Off** (désactivé) pour annuler.

## Placer un fichier dans la corbeille { #move-a-file-to-the-trash }

Appuyez sur **Ctrl+Delete** ou sur le bouton corbeille à côté du titre, puis confirmez avec **Move to Trash** (placer dans la corbeille).
Le fichier et ses marque-pages `.tmk` vont dans la corbeille de votre bureau (vous pouvez donc les restaurer), et le
lecteur passe au fichier suivant de la file d'attente.

## Clair et sombre { #light-and-dark }

**Ctrl+T** ou le bouton lune de la barre de titre bascule entre le thème clair (par défaut) et le thème sombre.
