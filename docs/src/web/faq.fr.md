# FAQ et dépannage

## Connexion { #signing-in }

??? question "Le message *Invalid username or password* (nom d'utilisateur ou mot de passe invalide) s'affiche."
    Vérifiez l'orthographe et les majuscules. Si vous avez oublié votre mot de passe, demandez à un
    administrateur d'en définir un nouveau sur la page [Users](administration.md#users)
    (Utilisateurs).

??? question "Le message *Can't reach the server — the backend may be down* (impossible de joindre le serveur) s'affiche."
    L'application web ne parvient pas du tout à communiquer avec le serveur — votre mot de passe
    n'a même pas été vérifié. Réessayez dans une minute ; si le problème persiste, prévenez votre
    administrateur.

??? question "J'ai été déconnecté tout seul."
    Les sessions expirent au bout d'un certain nombre d'heures (12 par défaut). Reconnectez-vous ;
    vous reviendrez sur la page où vous étiez.

??? question "Une boîte *Change your password* (changer votre mot de passe) ne disparaît pas."
    Votre administrateur exige un nouveau mot de passe avant que vous puissiez continuer.
    Remplissez les trois champs et cliquez sur **Change password** (Changer le mot de passe).

## Éléments manquants { #missing-things }

??? question "Je ne vois pas le bouton **New** (Nouveau), ni **Edit** (Modifier) / **Delete** (Supprimer)."
    Votre rôle permet de lire mais pas d'écrire. Demandez à un administrateur la capacité **Write
    content** (Écrire du contenu) — voir [Rôles et capacités](administration.md#roles-and-capabilities).

??? question "Une page indique *You don't have permission to view this page* (vous n'avez pas la permission de voir cette page)."
    Cette page requiert une capacité que votre rôle ne possède pas.

??? question "Un module ou une page d'outil manque dans la barre latérale."
    Il est peut-être désactivé dans [Settings → Modules](settings.md#modules) ou
    [Settings → Pages](settings.md#pages-and-features), ou masqué par vos permissions. Si la barre
    latérale manque de place, les pages d'outils sont affichées sous forme d'icônes — survolez-les
    pour voir leur nom.

??? question "J'ai supprimé un enregistrement par erreur."
    Ouvrez **Trash** (Corbeille) et cliquez sur l'icône de restauration à côté de l'enregistrement.
    Les enregistrements fusionnés s'y trouvent aussi.

## Recherche { #search }

??? question "La recherche globale ne trouve absolument rien."
    Si la page Search avertit que **the search index is empty** (l'index de recherche est vide), un
    administrateur doit cliquer une fois sur **Rebuild search index** (Reconstruire l'index de
    recherche) sur la page [Admin](administration.md#admin-page).

??? question "Rechercher un mot commençant par *d* ou *c* donne un comportement étrange."
    Dans la recherche globale, `dX` et `cX` sont interprétés comme des filtres de département et de
    cours lorsqu'un département ou un cours portant ce code existe. Ajoutez un autre mot, ou
    cherchez plutôt dans la liste du module.

??? question "Comment voir tout ce que j'ai fait un jour donné ?"
    Utilisez le filtre **On day** (Le jour) dans la liste d'un module, ou désactivez le module
    Journal et cliquez sur le jour dans le mini-calendrier pour voir cette journée dans tous les
    modules. Lorsque Journal est activé, cliquer sur un jour ouvre plutôt le journal de ce jour.

## Calendrier et journaux { #calendar-and-journals }

??? question "Cliquer sur une date du mini-calendrier a créé un journal."
    C'est voulu : chaque jour possède un journal de synthèse, créé la première fois que vous
    cliquez sur ce jour. Pour simplement filtrer par date, utilisez **On day** au-dessus de la
    liste.

??? question "Je ne peux pas ajouter ni redimensionner des entrées du calendrier."
    Les utilisateurs en lecture seule peuvent consulter le calendrier mais pas le modifier.

## Fichiers et dossiers { #files-and-folders }

??? question "Les boutons **Repo1/Repo2/Repo3** ne font rien."
    Ils ouvrent un gestionnaire de fichiers *sur l'ordinateur qui exécute le serveur*. Si le
    serveur est ailleurs, rien de visible ne se passe sur votre écran. Utilisez plutôt **Upload**
    (Téléverser) et les liens de fichiers de la section **Files** (Fichiers) de l'enregistrement.

??? question "Les pages d'import demandent un chemin de dossier. Quel dossier ?"
    Un dossier sur le disque du serveur, pas sur votre propre ordinateur. Votre administrateur sait
    où aboutissent les notes capturées et les dossiers de dépôt.

## Notifications { #notifications }

??? question "J'ai activé les notifications mais je n'en reçois jamais."
    Vérifiez que le navigateur autorise les notifications pour ce site, et qu'un onglet de
    l'application web est ouvert — l'application ne peut vous notifier que pendant qu'elle
    fonctionne. Le réglage est propre à chaque navigateur : activez-le dans chacun de ceux que vous
    utilisez.

## Partage et confidentialité { #sharing-and-privacy }

??? question "Puis-je afficher un tableau de bord sur une télévision sans m'y connecter ?"
    Oui — c'est à cela que sert le lien **View** (Afficher) d'un tableau de bord. Mais cela signifie
    que toute personne qui possède le lien et peut joindre le serveur peut le voir. Il en va de
    même pour les [flux RSS](rss.md). Voir
    [Tableaux de bord](dashboards.md#showing-it-on-a-screen).

## Affichage { #display }

??? question "Le texte arabe est aligné dans le mauvais sens."
    Le sens est déterminé à partir du texte lui-même, par ses premières lettres. Si une ligne
    commence par des lettres latines ou des chiffres, elle est traitée comme allant de gauche à
    droite. Voir [Langues](../concepts/languages.md).

??? question "Ma disposition (largeur des colonnes, taille de page…) a été réinitialisée."
    Ces préférences sont stockées dans votre navigateur. Un autre navigateur, une fenêtre de
    navigation privée ou l'effacement des données du site repartent de zéro.

??? question "Existe-t-il une version plus récente ?"
    Cliquez sur **check for updates** (rechercher des mises à jour) dans la barre d'état, ou sur
    **Check for updates** sur la page **Help & shortcuts** (Aide et raccourcis).
