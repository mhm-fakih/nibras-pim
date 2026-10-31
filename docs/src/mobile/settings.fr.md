# Référence des paramètres

Ouvrez les **Paramètres** avec l'engrenage en bas du rail.

Les paramètres sont répartis en onglets, un par fonction, affichés sous forme de puces en haut : **Général**, **Serveur**, **Heures de prière**, **Médias**, **Collecte de fichiers**, **Listes de contrôle**, **Alertes** et **Calendrier et widgets**. Chaque onglet retient où vous en étiez dans le défilement.

Les onglets d'une fonction facultative commencent par un interrupteur qui active ou désactive toute la fonction. Désactivée, ses pages quittent le rail et rien ne tourne en arrière-plan pour elle ; le reste des réglages de l'onglet est masqué jusqu'à ce que vous la réactiviez.

!!! warning "Touchez Enregistrer"
    Les modifications prennent effet lorsque vous touchez **Enregistrer** en haut à droite. Un seul **Enregistrer** vaut pour tous les onglets. **Retour** quitte sans enregistrer. Les exceptions sont la **Langue** et la **Couleur du thème**, qui s'appliquent dès que vous les touchez.

Les réglages des heures de prière se trouvent sur la page [Heures de prière](prayer.md) elle-même, et les réglages du lecteur dans le [lecteur](player.md#player-settings). Cette page couvre tout le reste, onglet par onglet.

## Général { #general }

### Langue { #language }

**English**, **عربي** ou **Français**. S'applique immédiatement. En arabe, toute l'interface passe de droite à gauche. Vos notes et vos fichiers ne sont jamais traduits.

### Couleur du thème { #theme-colour }

La couleur de base de l'application. Touchez-en une pour l'appliquer aussitôt :

- **Marine**, la couleur par défaut et celle des icônes de l'application, ou l'une des sept autres pastilles (sarcelle, vert, violet, bordeaux, orange, brun, graphite).
- Le bouton fond d'écran (**Couleurs du fond d'écran**) suit la palette du téléphone, telle qu'Android 12 et versions ultérieures la tirent de votre fond d'écran.
- **Autre couleur (hex)** : saisissez n'importe quelle couleur à six chiffres, comme `2E6B3A`. Elle s'applique dès le sixième chiffre.

Des variantes claire et sombre sont tirées de la couleur choisie, et suivent le mode sombre du téléphone.

### Notes { #notes }

| Réglage | Par défaut | Signification |
|---|---|---|
| **Nom de l’application** | Nibras Mobile | Le titre en haut de la page Notes. Vide affiche le nom propre de l'application dans la langue en cours. |
| **Code de type par défaut (nouvelles notes)** | premier type | La puce de type présélectionnée pour les nouvelles notes. Reste sélectionnée après chaque enregistrement. Vide signifie le premier type de la liste. |
| **Code de catégorie / service par défaut (nouvelles notes)** | première catégorie | La catégorie présélectionnée pour les nouvelles notes. Vide signifie la première catégorie. |
| **Priorité par défaut (nouvelles notes)** | 2 | La priorité présélectionnée pour les nouvelles notes. |
| **Priorité maximale (widgets)** | 4 | La priorité que listent les widgets **Priorité maximale** et **Tâches du jour**, et que compte le widget des indicateurs. Les tâches ajoutées depuis le widget reçoivent cette priorité. |
| **Dossier des notes** | `Notes` à la racine de votre stockage | L'emplacement de vos notes. Utilisez le bouton de dossier pour en choisir un. |
| **Dossier des réponses IA** | dossier des notes + `-ai` | Le dossier des [Réponses IA](ai-answers.md), en lecture seule. Vide utilise la valeur par défaut. |
| **Format d’horodatage** | `yyyy_MM_dd-HHmmss` | La façon d'écrire l'heure au début du nom de fichier d'une nouvelle note. **Maintenant :** affiche un exemple, ou **format non valide**. |
| **Lignes d’aperçu** | 3 | Le nombre de lignes de texte affichées sur chaque carte de note. |
| **Priorité min**, **Priorité max** | 1, 5 | La plage des priorités proposées. |

!!! tip "Les lettres de l'horodatage"
    Dans le format d'horodatage, `HH` désigne l'heure, `mm` les minutes et `MM` le **mois**. Les confondre donne des noms de fichiers étranges. Changer le format n'affecte que les nouvelles notes. Les noms de fichiers existants sont conservés.

### Types et catégories { #types-and-categories }

Les listes des types et catégories de notes, chaque entrée ayant un **Code** court et un **Nom**.

- **Types** : un type par note. Le code vient en premier dans le nom du fichier.
- **Catégories** : une catégorie par note. Le code vient en dernier dans le nom du fichier.

Utilisez **Ajouter un type** / **Ajouter une catégorie** pour en ajouter un et l'icône de corbeille (**Supprimer**) pour en supprimer un. Gardez des codes courts (une ou deux lettres). Les caractères `~ / \ : * ? " < > |` ne peuvent pas être utilisés.

Les valeurs par défaut sont les types `W` Work, `H` Home, `I` Ideas, `T` Todo et les catégories `P` Personal, `W` Work, `M` Misc.

!!! note "Lorsque vous êtes connecté à Nibras"
    Avec un serveur Nibras configuré, les deux listes sont remplacées par les modules et services du serveur au démarrage de l'application et chaque fois que vous touchez **Actualiser les notes + synchroniser**, afin que le téléphone et le serveur utilisent les mêmes codes. Les modifications faites ici peuvent alors être écrasées.

Supprimer un code ne modifie pas les notes existantes. Les notes qui l'utilisent encore apparaissent sous le filtre **Classement invalide** de la page Notes.

### Visite de configuration { #setup-tour }

**Afficher la visite de configuration** repasse sur les dossiers, en expliquant le rôle de chacun.

### Sauvegarde { #backup }

**Exporter les réglages** enregistre tous les réglages de ce téléphone dans un fichier JSON, à l'endroit de votre choix ; **Importer les réglages** recharge un tel fichier, pour une réinstallation ou un deuxième téléphone. Les réglages à l'écran sont d'abord enregistrés : ce que vous voyez est donc ce qui part dans le fichier. Le mot de passe du serveur n'est pas inclus. L'import redémarre l'application (**N réglages importés — redémarrage**).

## Serveur { #server }

**Utiliser un serveur Nibras** (activé par défaut) : désactivé, aucune connexion n'est établie, et la bande **Depuis le serveur** quitte le rail.

### Serveur Nibras { #nibras-server }

Navigateur en lecture seule de votre base de données Nibras. Voir [Synchronisation Nibras](sync.md#connect) pour le détail de chaque champ.

| Réglage | Signification |
|---|---|
| **Protocole** | `http` ou `https` |
| **IP / hôte**, **Port**, **Contexte** | L'adresse du serveur. **URL complète** affiche le résultat. |
| **Nom d’utilisateur**, **Mot de passe** | Votre compte Nibras, utilisé pour se connecter lors de la synchronisation. |
| **Dossier des fichiers** | L'emplacement des pièces jointes des fiches sur le téléphone (`Nibras` à la racine de votre stockage par défaut). |

### Bouton étoile { #star-button }

L'effet de l'étoile sur les fiches du serveur :

- **Favori sur cet appareil** : une sélection privée. Rien n'est envoyé au serveur. (Par défaut.)
- **Retirer des favoris dans Nibras** : toucher l'étoile retire la fiche de vos favoris sur le serveur. Voir [Pages serveur](server-pages.md#bookmark-the-star).

**Taille du texte N×** définit la taille du texte intégral des Ressources (1,5× par défaut).

### Synchronisation en arrière-plan { #background-sync }

| Réglage | Par défaut |
|---|---|
| **Synchroniser avec Nibras** | Activé |
| **Vérifier toutes les (s)** | 30 |
| **Synchroniser toutes les (s)** | 300 |

Voir [Synchronisation en arrière-plan](sync.md#background-sync).

## Heures de prière { #prayer-times }

Un seul interrupteur, **Heures de prière** (activé par défaut) : la page de prière, l'adhan, la mise en silence et le widget. Désactivé, la page quitte le rail et rien n'est programmé. Le lieu, la méthode et les sons se règlent sur la page [Heures de prière](prayer.md) elle-même. C'est aussi par cet interrupteur que la page revient après avoir été désactivée depuis la page de prière.

## Médias { #media }

| Réglage | Par défaut | Signification |
|---|---|---|
| **Pages du lecteur multimédia** | Activé | Les pages **Médias** (bibliothèque audio) et **Vidéos**. Désactivé : elles quittent le rail ; les fichiers ouverts depuis une note se lisent toujours. |
| **Dossiers de la bibliothèque audio** | | Les dossiers que parcourt la page [Médias](player.md). Ajoutez-en plusieurs avec **Ajouter un dossier**. |
| **Dossier des vidéos** | `Nibras` à la racine de votre stockage | Le dossier qu'affiche la page [Vidéos](player.md#videos), sous-dossiers compris. |
| **Galerie** | Activé | La page [Galerie](gallery.md). Désactivé : elle quitte le rail. |
| **Dossier de la galerie** | `Pictures` | Le dossier qu'affiche la Galerie. |

## Collecte de fichiers { #file-collection }

Voir [Collecter des fichiers](collect.md).

| Réglage | Signification |
|---|---|
| **Collecte de fichiers** | La page Collecte et son exécution quotidienne (activé par défaut). Désactivé, les deux disparaissent, quel que soit le réglage automatique ci-dessous. |
| **Dossiers sources** | Les dossiers dans lesquels rassembler les fichiers. Décochez un dossier pour l'ignorer sans le retirer. |
| **Dossier de destination (New)** | L'endroit où les collections sont écrites. Votre dossier de notes par défaut. |
| **Collecter automatiquement chaque jour** | Lance la collecte toute seule chaque jour, **À** l'heure que vous choisissez (15:00 par défaut). Les fichiers sont déplacés. Désactivé par défaut. |

## Listes de contrôle { #checklists }

Voir [Listes de contrôle](checklists.md).

| Réglage | Par défaut | Signification |
|---|---|---|
| **Listes de contrôle** | Activé | Des listes quotidiennes et hebdomadaires, cochées par jour, un fichier par semaine. Désactivé : la page quitte le rail. |
| **Dossier des listes** | dossier des notes + `/checklists` | Un fichier `.md` par semaine. Vide utilise la valeur par défaut. |

## Alertes { #alerts }

### Rappels des plans { #plan-reminders }

Des rappels avant les événements Plans/Journal à venir provenant du serveur. Voir [Rappels des plans](alarms.md#plan-reminders).

| Réglage | Par défaut |
|---|---|
| **Délais (minutes)** | `60, 5` |
| **Son de rappel** (**Choisir un son**) | Par défaut |
| **Exiger l’arrêt** | Désactivé |
| **Rappeler même en mode silencieux ou vibreur** | Désactivé |
| **Répéter le son (fois)** | 4 |

### État et carillon { #status-chime }

| Réglage | Par défaut | Signification |
|---|---|---|
| **Notification permanente** | Activé | Une notification continue avec le nombre de notes du jour. Elle maintient aussi l'application active pour que les alarmes sonnent à l'heure. Voir [La notification d'état](alarms.md#the-status-notification). |
| **Carillon** | Activé | Joue un son à l'intervalle choisi, dans la plage ci-dessous. |
| **Carillon toutes les** | 15 min | **15 min**, **30 min**, **1 h** ou **2 h**, comptées depuis l'heure de début. |
| **Choisir le fichier du carillon** | son de notification du téléphone | N'importe quel fichier audio. |
| **Heure de début du carillon**, **Heure de fin du carillon** | 8, 22 | La plage horaire pendant laquelle le carillon sonne. |

Voir [Le carillon](alarms.md#the-chime).

### Minuteur rapide { #quick-timer }

Ce que joue le [minuteur rapide](alarms.md#quick-timer) en haut de la page Alarmes quand il se termine.

| Réglage | Par défaut |
|---|---|
| **Choisir un fichier son** | **Son d’alarme par défaut** |
| **Volume N %** | 80 % |
| **Joué N×** | 2× (de 1 à 10) |

## Calendrier et widgets { #calendar-widgets }

| Réglage | Par défaut | Signification |
|---|---|---|
| **Mini-calendrier** | Activé | La barre de la semaine en haut de la page [Agenda](calendar.md). |
| **Heure de début du calendrier**, **Heure de fin du calendrier** | 5, 22 | Les heures qu'affiche la grille de l'Agenda. |

### Widget des notes récentes { #recent-notes-widget }

La période couverte par le [widget Notes récentes](widgets.md#recent-notes) (de **aujourd’hui seulement** à 30 jours, 3 par défaut), le nombre de mots avant **Lire la suite** (de 10 à 200, 40 par défaut), et sa **Taille du texte N sp** (de 10 à 28, 15 par défaut).

### Widget tableau des tâches { #tasks-board-widget }

La **Taille du texte N sp** du [widget Tableau des tâches](widgets.md#tasks-board) (de 10 à 24, 13 par défaut) et son nombre de **colonnes** (de 1 à 4, 2 par défaut).

Les widgets se redessinent quand vous touchez **Enregistrer** : une nouvelle taille s'affiche donc aussitôt.
