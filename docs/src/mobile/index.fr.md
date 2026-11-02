# Nibras Mobile pour Android

**Nibras Mobile** (نبراس محمول en arabe, autrefois appelée knotes) est l'application mobile de l'écosystème de gestion des connaissances personnelles Nibras. C'est avant tout un outil de prise de notes rapide : un seul écran avec vos notes et une zone pour saisir la suivante. Autour de cela, elle ajoute ce dont vous avez besoin sur un téléphone au fil de la journée : un lecteur multimédia qui se souvient de l'endroit où vous vous êtes arrêté, les heures de prière avec l'adhan, des alarmes et un minuteur rapide, des listes de contrôle quotidiennes, un agenda, des widgets pour l'écran d'accueil et une connexion facultative à un serveur Nibras.

## Ce qui la distingue { #what-makes-it-different }

- **Vos notes sont des fichiers texte brut.** Chaque note est un fichier Markdown (`.md`) dans un dossier de votre choix. Aucune base de données de l'application ne les enferme. Les mêmes fichiers s'ouvrent sur votre ordinateur, se synchronisent avec n'importe quel outil de synchronisation de dossiers et survivront à l'application. Voir [Notes en texte brut](../concepts/plain-text-notes.md).
- **Le nom du fichier porte les métadonnées.** La date d'écriture de la note, son type, sa priorité et sa catégorie figurent tous dans le nom du fichier, si bien que n'importe quel outil peut les trier et les filtrer. Voir [Noms de fichiers et étiquettes](../concepts/filenames-and-tags.md).
- **Capturer maintenant, classer plus tard.** Enregistrer une note demande un seul appui. Vous pouvez ranger les étiquettes quand bon vous semble. Voir [Capturer, puis classer](../concepts/capture-then-classify.md).
- **Fonctionne hors ligne.** Tout ce qui se trouve dans la partie **Sur ce téléphone** de l'application fonctionne sans aucun réseau. La partie serveur est facultative et affiche, hors ligne, ce qui a été synchronisé en dernier.
- **Trois langues.** Anglais (English), arabe (عربي) et français. En arabe, toute l'interface passe de droite à gauche.

## Installation { #install }

Nibras Mobile nécessite Android 6.0 ou une version plus récente. Il existe trois façons de l'installer, qui donnent toutes le même fichier, signé avec la même clé :

- **Depuis un client F-Droid** (F-Droid, Droid-ify, Neo Store…). Ajoutez le dépôt du projet dans *Paramètres → Dépôts* du client, ou ouvrez ce lien sur le téléphone :
  `https://mhm-fakih.github.io/nibras-fdroid/repo?fingerprint=4E6533C6F4783BD04CF7A9278CF5963A5E7DE77D486EA32FBD3AA4E5CCEDC2F3`
  Les mises à jour arrivent ensuite comme pour n'importe quelle autre application.
- **Avec [Obtainium](https://obtainium.imranr.dev)**, qui installe et met à jour directement depuis les versions GitHub du projet. Chaque version contient un `-release.apk` et un `-debug.apk` ; choisissez celui en **release**.
- **Directement** : téléchargez le fichier `knotes-…-release.apk` depuis la [dernière version](https://github.com/mhm-fakih/nibras-pim/releases/latest), ouvrez-le, autorisez votre navigateur ou votre gestionnaire de fichiers à installer des applications inconnues si Android le demande, puis touchez **Installer**.

Une fois l'installation faite, vous avez **deux icônes** sur votre téléphone : **Nibras Mobile**, qui ouvre l'application, et **Lecteur**, qui ouvre directement le [lecteur multimédia](player.md) sur la dernière piste écoutée.

!!! note "Mise à jour"
    Une mise à jour s'installe par-dessus l'ancienne version. Vos notes sont des fichiers dans votre propre dossier : une mise à jour n'y touche donc jamais. Vos réglages sont conservés, et les alarmes, les rappels et l'adhan sont reprogrammés d'eux-mêmes après la mise à jour. Pour transférer vos réglages sur un autre téléphone, utilisez **Paramètres → Général → Sauvegarde** (voir [Sauvegarde](troubleshooting.md#backing-up)).

## Premier lancement { #first-run }

### 1. Autoriser l'accès aux fichiers { #1-grant-file-access }

Sur le premier écran, l'application explique qu'elle stocke vos notes sous forme de fichiers texte brut et demande l'accès à ceux-ci. Touchez **Autoriser l’accès aux fichiers**. Sur Android 11 et versions ultérieures, cela ouvre la page système **Accès à tous les fichiers** ; activez-la pour Nibras Mobile, puis revenez en arrière.

L'accès complet aux fichiers est nécessaire, car vos notes, vos médias et vos images se trouvent dans des dossiers ordinaires que vous choisissez, et non à l'intérieur de l'application.

### 2. La visite de configuration { #2-the-setup-tour }

Lors d'une nouvelle installation, une courte visite vous présente les dossiers que lit l'application et les parties de l'application que vous voulez utiliser. Le haut de chaque page affiche **Étape N sur 10**. **Suivant** avance, **Retour** revient en arrière, et **Passer la configuration** est présent sur chaque page si vous préférez d'abord explorer. Rien n'est définitif dans cette visite : chaque réponse peut être modifiée plus tard dans les [Paramètres](settings.md).

| Page | Ce que vous choisissez |
|---|---|
| **Language · اللغة · Langue** | **English**, **عربي** ou **Français**. L'application change de langue immédiatement. Vos notes et vos fichiers ne sont jamais traduits. |
| **Bienvenue dans Nibras Mobile** | Une courte présentation. |
| **Où vivent vos notes** | Le **Dossier des notes**. L'écran d'accueil écrit et liste les notes ici. Si vous conservez déjà des notes dans un dossier (synchronisé depuis un ordinateur, par exemple), indiquez-le ici et elles apparaissent aussitôt. |
| **Rassembler des fichiers en notes** | Un interrupteur **Collecte de fichiers**, et l'emplacement où vont les captures d'écran, photos et enregistrements collectés (**Les notes collectées vont dans**). Voir [Collecter des fichiers](collect.md). |
| **Votre médiathèque** | Un interrupteur **Pages du lecteur multimédia**, et le **Dossier des médias** pour l'audio et la vidéo. Voir [Lecteur multimédia](player.md). |
| **Images** | Un interrupteur **Galerie**, et le **Dossier des images** qu'elle affiche. Voir [Galerie](gallery.md). |
| **Heures de prière** | Un interrupteur **Heures de prière**. Le lieu, la méthode et les sons se règlent sur la page [Heures de prière](prayer.md) elle-même. |
| **Se connecter à Nibras** | Un interrupteur **Utiliser un serveur Nibras**, l'**Adresse du serveur**, le **Nom d’utilisateur** et le **Mot de passe**, et **Synchroniser avec Nibras en arrière-plan**. Laissez l'adresse vide pour passer. Voir [Synchronisation Nibras](sync.md). |
| **Rappels et carillon** | Les interrupteurs **Carillon** et **Garder une notification d’état**, ainsi que **Autoriser les notifications**. |
| **Tout est prêt** | Un récapitulatif de chaque dossier que l'application lira et des parties **Activé** ou **Désactivé**. Touchez **Commencer avec Nibras Mobile**. |

Une partie désactivée quitte le [rail](navigation.md) et ne fait rien en arrière-plan. Chacune a le même interrupteur en haut de son onglet dans les Paramètres, pour la réactiver plus tard.

À côté de chaque champ de dossier, la visite vérifie ce qui s'y trouve réellement : **Dossier trouvé, et il contient des fichiers.**, **Dossier trouvé — il est vide pour l’instant.**, **Ce dossier n’existe pas encore.** (touchez **Créer** pour le créer) ou **Ce chemin est un fichier, pas un dossier.** Touchez **Choisir** pour sélectionner un dossier au lieu de saisir un chemin.

!!! tip "Un bon choix par défaut"
    Un dossier nommé `Notes` à la racine du stockage de votre téléphone est un bon point de départ. Si vous comptez synchroniser avec un ordinateur, faites pointer votre outil de synchronisation sur ce même dossier.

!!! note "Mise à niveau depuis une ancienne version"
    Si l'application était déjà configurée sur ce téléphone, la visite ne s'affiche pas. Vous pouvez l'ouvrir à tout moment avec **Afficher la visite de configuration** dans **Paramètres → Général**.

### 3. Autoriser les notifications { #3-allow-notifications }

Sur Android 13 et versions ultérieures, l'application demande une seule fois l'autorisation d'afficher des notifications. Acceptez. Sans elle, Android n'affichera ni les rappels, ni le carillon, ni la notification de l'adhan, ni les commandes du lecteur.

## Changer de langue plus tard { #changing-the-language-later }

La liste des langues est le premier élément de [Paramètres](settings.md) → **Général**. Chaque langue est écrite dans sa propre écriture, vous pouvez donc trouver la vôtre quelle que soit la langue affichée à l'écran. Sur Android 13 et versions ultérieures, vous pouvez aussi la changer depuis la page système **Langues des applications**. Plus de détails dans [Langues](../concepts/languages.md).

En arabe, les notifications s'affichent elles aussi de droite à gauche, même sur un téléphone dont la langue est l'anglais. Les nombres conservent les chiffres occidentaux (0–9), afin que les heures, les noms de fichiers et les signets restent lisibles par les autres outils.

## Pour aller plus loin { #where-to-go-next }

- [Se déplacer : le rail de gauche](navigation.md)
- [Écrire et modifier des notes](notes.md)
- [Retrouver des notes](search-filters.md)
- [Lecteur multimédia](player.md)
- [Heures de prière et adhan](prayer.md)
- [Alarmes, minuteur rapide et carillon](alarms.md)
- [Listes de contrôle quotidiennes et hebdomadaires](checklists.md)
- [Widgets de l'écran d'accueil](widgets.md)
- [Référence des paramètres](settings.md)
- [Sauvegarde, dépannage et FAQ](troubleshooting.md)
