# knotes pour Android

**knotes** est l'application mobile de l'écosystème de gestion des connaissances personnelles Nibras. C'est avant tout un outil de prise de notes rapide : un seul écran avec vos notes et une zone pour saisir la suivante. Autour de cela, elle ajoute ce dont vous avez besoin sur un téléphone au fil de la journée : un lecteur multimédia qui se souvient de l'endroit où vous vous êtes arrêté, les heures de prière avec l'adhan, des alarmes, un agenda, des widgets pour l'écran d'accueil et une connexion facultative à un serveur Nibras.

## Ce qui la distingue { #what-makes-it-different }

- **Vos notes sont des fichiers texte brut.** Chaque note est un fichier Markdown (`.md`) dans un dossier de votre choix. Aucune base de données de l'application ne les enferme. Les mêmes fichiers s'ouvrent sur votre ordinateur, se synchronisent avec n'importe quel outil de synchronisation de dossiers et survivront à l'application. Voir [Notes en texte brut](../concepts/plain-text-notes.md).
- **Le nom du fichier porte les métadonnées.** La date d'écriture de la note, son type, sa priorité et sa catégorie figurent tous dans le nom du fichier, si bien que n'importe quel outil peut les trier et les filtrer. Voir [Noms de fichiers et étiquettes](../concepts/filenames-and-tags.md).
- **Capturer maintenant, classer plus tard.** Enregistrer une note demande un seul appui. Vous pouvez ranger les étiquettes quand bon vous semble. Voir [Capturer, puis classer](../concepts/capture-then-classify.md).
- **Fonctionne hors ligne.** Tout ce qui se trouve dans la partie **Sur ce téléphone** de l'application fonctionne sans aucun réseau. La partie serveur est facultative et affiche, hors ligne, ce qui a été synchronisé en dernier.
- **Trois langues.** Anglais (English), arabe (عربي) et français. En arabe, toute l'interface passe de droite à gauche.

## Installation { #install }

knotes est distribuée sous forme de paquet d'application Android (`.apk`). Elle nécessite Android 6.0 ou une version plus récente.

1. Téléchargez le fichier `.apk` sur votre téléphone.
2. Ouvrez-le. Si Android le demande, autorisez votre navigateur ou votre gestionnaire de fichiers à installer des applications inconnues.
3. Touchez **Installer**, puis **Ouvrir**.

!!! note "Mise à jour"
    Pour mettre à jour, installez le nouveau `.apk` par-dessus l'ancien. Vos notes sont des fichiers dans votre propre dossier : une mise à jour n'y touche donc jamais. Vos réglages sont également conservés.

## Premier lancement { #first-run }

### 1. Autoriser l'accès aux fichiers { #1-grant-file-access }

Sur le premier écran, knotes explique qu'elle stocke vos notes sous forme de fichiers texte brut et demande l'accès à ceux-ci. Touchez **Autoriser l’accès aux fichiers**. Sur Android 11 et versions ultérieures, cela ouvre la page système **Accès à tous les fichiers** ; activez-la pour knotes, puis revenez en arrière.

L'accès complet aux fichiers est nécessaire, car vos notes, vos médias et vos images se trouvent dans des dossiers ordinaires que vous choisissez, et non à l'intérieur de l'application.

### 2. La visite de configuration { #2-the-setup-tour }

Lors d'une nouvelle installation, une courte visite vous présente les dossiers que lit l'application. Le haut de chaque page affiche **Étape N sur 9**. **Suivant** avance, **Retour** revient en arrière, et **Passer la configuration** est présent sur chaque page si vous préférez d'abord explorer. Rien n'est définitif dans cette visite : chaque réponse peut être modifiée plus tard dans les [Paramètres](settings.md).

| Page | Ce que vous choisissez |
|---|---|
| **Language · اللغة · Langue** | **English**, **عربي** ou **Français**. L'application change de langue immédiatement. Vos notes et vos fichiers ne sont jamais traduits. |
| **Bienvenue dans knotes** | Une courte présentation. |
| **Où vivent vos notes** | Le **Dossier des notes**. L'écran d'accueil écrit et liste les notes ici. Si vous conservez déjà des notes dans un dossier (synchronisé depuis un ordinateur, par exemple), indiquez-le ici et elles apparaissent aussitôt. |
| **Rassembler des fichiers en notes** | L'emplacement où vont les captures d'écran, photos et enregistrements collectés (**Les notes collectées vont dans**). Voir [Collecter des fichiers](collect.md). |
| **Votre médiathèque** | Le **Dossier des médias** pour l'audio et la vidéo. Voir [Lecteur multimédia](player.md). |
| **Images** | Le **Dossier des images** affiché par la Galerie. Voir [Galerie](gallery.md). |
| **Se connecter à Nibras** | L'**Adresse du serveur** Nibras, facultative. Laissez-la vide pour passer. Voir [Synchronisation Nibras](sync.md). |
| **Rappels et carillon** | Les interrupteurs **Carillon tous les quarts d’heure**, **Garder une notification d’état**, **Synchroniser avec Nibras en arrière-plan**, ainsi que **Autoriser les notifications**. |
| **Tout est prêt** | Un récapitulatif de chaque dossier que l'application lira. Touchez **Commencer avec knotes**. |

À côté de chaque champ de dossier, la visite vérifie ce qui s'y trouve réellement : **Dossier trouvé, et il contient des fichiers.**, **Dossier trouvé — il est vide pour l’instant.**, **Ce dossier n’existe pas encore.** (touchez **Créer** pour le créer) ou **Ce chemin est un fichier, pas un dossier.** Touchez **Choisir** pour sélectionner un dossier au lieu de saisir un chemin.

!!! tip "Un bon choix par défaut"
    Un dossier nommé `Notes` à la racine du stockage de votre téléphone est un bon point de départ. Si vous comptez synchroniser avec un ordinateur, faites pointer votre outil de synchronisation sur ce même dossier.

!!! note "Mise à niveau depuis une ancienne version"
    Si knotes était déjà configurée sur ce téléphone, la visite ne s'affiche pas. Vous pouvez l'ouvrir à tout moment depuis le bas des Paramètres avec **Afficher la visite de configuration**.

### 3. Autoriser les notifications { #3-allow-notifications }

Sur Android 13 et versions ultérieures, knotes demande une seule fois l'autorisation d'afficher des notifications. Acceptez. Sans elle, Android n'affichera ni les rappels, ni le carillon, ni la notification de l'adhan, ni les commandes du lecteur.

## Changer de langue plus tard { #changing-the-language-later }

La liste des langues est le premier élément des [Paramètres](settings.md). Chaque langue est écrite dans sa propre écriture, vous pouvez donc trouver la vôtre quelle que soit la langue affichée à l'écran. Sur Android 13 et versions ultérieures, vous pouvez aussi la changer depuis la page système **Langues des applications**. Plus de détails dans [Langues](../concepts/languages.md).

En arabe, les nombres conservent les chiffres occidentaux (0–9), afin que les heures, les noms de fichiers et les signets restent lisibles par les autres outils.

## Pour aller plus loin { #where-to-go-next }

- [Se déplacer : le rail de gauche](navigation.md)
- [Écrire et modifier des notes](notes.md)
- [Retrouver des notes](search-filters.md)
- [Lecteur multimédia](player.md)
- [Heures de prière et adhan](prayer.md)
- [Widgets de l'écran d'accueil](widgets.md)
- [Référence des paramètres](settings.md)
- [Sauvegarde, dépannage et FAQ](troubleshooting.md)
