# Sauvegarde, dépannage et FAQ

## Sauvegarder { #backing-up }

### Vos notes sont déjà des fichiers { #your-notes-are-already-files }

Tout ce que vous écrivez se trouve dans des fichiers ordinaires, dans des dossiers que vous avez choisis :

| Quoi | Où |
|---|---|
| Notes | Un fichier `.md` chacune, dans votre **Dossier des notes** |
| Pièces jointes des notes et collections | Le dossier `.d` à côté de chaque note |
| Surlignages dans les notes | Dans le fichier de la note lui-même (`==…==`) |
| Signets des médias | Un fichier `.tmk` à côté de chaque fichier audio ou vidéo |
| Images annotées | L'image elle-même ; les originaux dans un dossier caché `.scr-backup` à côté |

Pour les sauvegarder, copiez les dossiers sur un ordinateur ou tenez-les à jour avec un outil de synchronisation de dossiers comme Syncthing. Désinstaller knotes ne les supprime pas.

### Ce qui est stocké dans l'application { #what-lives-inside-the-app }

Certaines choses sont stockées par l'application plutôt que dans vos dossiers :

- vos réglages, y compris les chemins des dossiers, les types et catégories, et les informations du serveur,
- les alarmes et les réglages des heures de prière,
- les réglages du lecteur et la dernière position dans chaque fichier multimédia,
- l'état propre au téléphone pour les fiches du serveur : lu/non lu et la sélection privée par étoile,
- la copie enregistrée des fiches du serveur, et les modifications encore en attente d'envoi.

Tout cela est perdu si vous désinstallez l'application ou effacez ses données. Avant l'une ou l'autre de ces opérations, laissez les modifications en attente se synchroniser avec le serveur et notez vos réglages. Tout ce qui se trouve sur le serveur est téléchargé à nouveau lorsque vous vous reconnectez.

## Dépannage { #troubleshooting }

### La page Notes est vide { #the-notes-page-is-empty }

- La liste démarre sur **aujourd'hui**. Si vous n'avez rien écrit aujourd'hui, elle est vide. Ouvrez la barre **Filtres** et touchez **Tout effacer**.
- Vérifiez **Paramètres → Dossier des notes**. Un chemin qui semble correct mais n'existe pas (une faute de frappe, `notes` au lieu de `Notes`, une carte mémoire non insérée) donne une application vide, sans message d'erreur. La visite de configuration (**Paramètres → Afficher la visite de configuration**) indique si chaque dossier existe et ce qu'il contient.
- Assurez-vous que l'accès aux fichiers est toujours accordé (voir ci-dessous).

### « Impossible d’écrire la note. Vérifiez que l’accès aux fichiers est toujours accordé. » { #could-not-write-the-note-check-that-file-access-is-still-granted }

knotes a besoin de l'**Accès à tous les fichiers**. Ouvrez les paramètres Android → Applications → knotes → Autorisations (ou **Accès spécial des applications → Accès à tous les fichiers**) et activez-le. S'il est désactivé au démarrage de l'application, celle-ci affiche à nouveau **Autoriser l’accès aux fichiers**.

### Les alarmes, les rappels ou le carillon arrivent en retard, ou pas du tout { #alarms-reminders-or-the-chime-come-late-or-not-at-all }

- Si la page Alarmes indique **Les alarmes exactes ne sont pas autorisées pour cette appli**, autorisez **Alarmes et rappels** pour knotes dans les paramètres Android.
- Autorisez les notifications pour knotes. Sans elles, Android masque les rappels, le carillon et la notification de l'adhan.
- Laissez **Notification permanente** activée (Paramètres → **État et carillon**). Elle maintient l'application active pour que les alarmes se déclenchent à l'heure.
- Certains téléphones arrêtent les applications en arrière-plan pour économiser la batterie. Si les alarmes arrivent toujours en retard, excluez knotes de l'optimisation de la batterie dans les paramètres Android.

### L'adhan n'a pas retenti { #the-athan-didnt-sound }

Sur la page [Heures de prière](prayer.md), ouvrez **Adhan** et vérifiez que :

- **Alertes activées** est bien activé. Sinon, la page affiche **Les alertes sont désactivées**.
- La prière est cochée sous **Appeler l’adhan à**.
- Son volume sous **Volume pour chacune** n'est pas à 0, et le volume des **alarmes** du téléphone n'est pas à zéro. L'adhan peut être plus doux que le volume d'alarme, jamais plus fort.
- Touchez **Écouter** pour tester le son.

### Le téléphone n'a pas été mis en silence pour la prière { #the-phone-wasnt-silenced-for-prayer }

**Silence → Passer en silence à ces horaires** nécessite l'accès « Ne pas déranger ». Si le panneau affiche **Android ne permet pas à une appli de modifier la sonnerie sans l’accès « Ne pas déranger ».**, touchez **Autoriser l’accès**. Vérifiez aussi que la prière est cochée sous **À ces horaires**.

### Les heures de prière ne correspondent pas à celles de ma mosquée { #prayer-times-dont-match-my-mosque }

Parcourez **Méthode** : définissez **Votre position**, puis l'**École** (Asr), puis la **Méthode de calcul**. Affinez le reste avec **Horaires → Ajuster chaque horaire**. Une différence d'une minute ou deux sur chaque horaire est normale d'une méthode à l'autre.

### « Impossible de supprimer cette note » { #cant-delete-this-note }

La note a des fichiers dans son dossier de pièces jointes. Ouvrez-le avec **Ouvrir le dossier des fichiers**, déplacez ou supprimez les fichiers, puis supprimez la note. Voir [Supprimer une note](notes.md#delete-a-note).

### Les pages serveur sont vides ou pas à jour { #server-pages-are-empty-or-out-of-date }

- Vérifiez l'état de la connexion en haut d'une page de fiches (**En ligne** / **Hors ligne**).
- **Échec de la connexion — vérifiez le nom d’utilisateur et le mot de passe dans les paramètres** : corrigez-les dans **Paramètres → Serveur Nibras**.
- Seules les fiches **en favori** arrivent sur le téléphone. Pour en obtenir une autre, utilisez [Rechercher sur le serveur](server-pages.md#search-server) et marquez-la.
- Touchez **Actualiser les pages du serveur** en bas du rail pour tout recharger.

### La recherche sur le serveur affiche une erreur { #search-server-shows-an-error }

| Le message mentionne | Signification |
|---|---|
| **401** | Le nom d'utilisateur ou le mot de passe est incorrect. |
| **403** | Vous êtes connecté, mais le compte n'est pas autorisé à effectuer des recherches. |
| **404** | Le serveur exécute une ancienne version sans recherche. Mettez le serveur à jour. |
| **Impossible de joindre** | Vérifiez l'adresse dans les Paramètres et que le serveur est démarré. |

### La page Réponses IA indique que le dossier est absent { #the-ai-answers-page-says-the-folder-is-missing }

Le dossier n'est pas encore arrivé sur le téléphone, ou il se trouve ailleurs. Vérifiez votre outil de synchronisation de dossiers, ou faites pointer **Paramètres → Dossier des réponses IA** vers son emplacement réel.

### « Plus rien ici » dans le lecteur { #nothing-left-here-in-the-player }

**Lire ensuite** n'a trouvé aucun fichier à lire : tout ce qui se trouve dans ce dossier a déjà un signet ou a déjà été lu. Choisissez un autre mode, ou ouvrez un fichier depuis la page Médias.

## FAQ { #faq }

**knotes a-t-elle besoin d'une connexion Internet ?**
Non. Les notes, le lecteur, la galerie, la collecte, les heures de prière, les alarmes et les widgets fonctionnent tous hors ligne. Seules les pages serveur et la synchronisation ont besoin de votre serveur Nibras, et elles affichent la dernière copie synchronisée lorsqu'il est injoignable.

**Puis-je utiliser un dossier de notes que j'ai déjà ?**
Oui. Faites pointer le **Dossier des notes** dessus. Les fichiers `.md` et `.txt` simples apparaissent immédiatement, et deviennent des notes entièrement étiquetées dès que vous leur donnez un type, une priorité et une catégorie.

**Puis-je modifier mes notes sur un ordinateur ?**
Oui. Ce sont des fichiers texte ordinaires. Tenez le dossier à jour avec un outil de synchronisation, et les mêmes notes s'ouvrent dans l'application de bureau Nibras ou dans n'importe quel éditeur de texte. Voir [Notes en texte brut](../concepts/plain-text-notes.md).

**Quelque chose quitte-t-il mon téléphone ?**
Uniquement ce que vous envoyez à votre propre serveur Nibras, si vous en avez configuré un. La position utilisée pour les heures de prière n'est lue que lorsque vous touchez **Utiliser ma position**, et rien n'est consulté en ligne.

**Pourquoi les chiffres restent-ils 0–9 en arabe ?**
C'est voulu. Les heures, les noms de fichiers et les fichiers de signets sont partagés avec d'autres outils, et garder les chiffres occidentaux les rend lisibles partout.

**Pourquoi n'y a-t-il pas de corbeille ?**
Une note, une image ou un enregistrement supprimé est un fichier supprimé. Si vous synchronisez vos dossiers avec un ordinateur, votre outil de synchronisation conserve peut-être d'anciennes versions. Consultez ses réglages.

**Puis-je désactiver les fonctions que je n'utilise pas ?**
**Heures de prière** (sur sa propre page, sous **Adhan**) et **Collecte de fichiers** (dans les Paramètres) peuvent être désactivées, et elles disparaissent alors du rail. Les pages serveur restent simplement vides si aucun serveur n'est configuré.
