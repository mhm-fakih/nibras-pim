# L'application web Nibras

L'application web est la partie navigateur de Nibras : l'endroit où vos tâches, journaux, plans,
ressources, notes, objectifs et écrits vivent sous forme d'**enregistrements** sur un serveur,
prêts à être recherchés, reliés, planifiés et revus. Les applications mobile et de bureau
capturent les choses rapidement — souvent hors ligne — et l'application web est l'endroit où ces
captures arrivent, sont classées et travaillées.

Si vous découvrez la manière dont Nibras envisage les notes, les enregistrements et les noms de
fichiers, lisez d'abord les [Concepts](../concepts/index.md) ; ce guide porte sur l'utilisation de
l'application web elle-même.

## Ouvrir l'application et se connecter { #opening-the-app-and-signing-in }

Ouvrez l'application web à l'adresse que vous a donnée votre administrateur. Une carte **Sign in**
(Se connecter) s'affiche.

1. Saisissez votre **Username** (Nom d'utilisateur) et votre **Password** (Mot de passe).
2. Cliquez sur **Sign in**.

Une fois connecté, vous arrivez sur la page que vous tentiez d'atteindre, ou sur **Tasks**
(Tâches) si vous êtes venu directement sur la page de connexion. L'adresse de l'application seule
(sans rien après) ouvre le tableau [Kanban](tasks-kanban-sprints.md#kanban).

!!! note "Première connexion"
    Si votre administrateur vous a demandé de choisir votre propre mot de passe, une boîte de
    dialogue **Change your password** (Changer votre mot de passe) apparaît immédiatement. Saisissez
    le **Current password** (Mot de passe actuel), puis le **New password** (Nouveau mot de passe)
    deux fois, et cliquez sur **Change password** (Changer le mot de passe). Vous ne pouvez pas
    utiliser l'application tant que ce n'est pas fait.

Pour vous déconnecter, cliquez sur l'icône **Sign out** (Se déconnecter) à côté de votre nom, en bas
de la barre latérale gauche.

Tant qu'une page de l'application est ouverte, elle renouvelle d'elle-même votre connexion : un
onglet laissé ouvert — une liste de contrôle sur une télévision, par exemple — reste connecté aussi
longtemps qu'il est ouvert. Un navigateur fermé ou en veille reste connecté pendant la durée de
session qu'un administrateur fixe dans [Settings → Session](settings.md#session) (12 heures sauf
modification) ; au-delà, il suffit de vous reconnecter.

!!! warning "Problèmes de connexion"
    - **Invalid username or password.** (Nom d'utilisateur ou mot de passe invalide.) — vérifiez
      l'orthographe ; les noms d'utilisateur et les mots de passe sont sensibles à la casse.
    - **Can't reach the server — the backend may be down.** (Impossible de joindre le serveur — le
      backend est peut-être arrêté.) — l'application elle-même ne répond pas. Ce n'est pas un
      problème de mot de passe ; contactez votre administrateur.

## La disposition : trois volets { #the-layout-three-panes }

Chaque module (Tâches, Notes, Objectifs, …) utilise la même disposition de type Outlook :

| Volet | Ce qu'il contient |
|---|---|
| **Barre latérale gauche** | Le logo, le nom et la version de l'application, un mini-calendrier, des raccourcis vers les rapports et les recherches enregistrées du module courant, les boutons des modules (deux par ligne, chacun avec son nombre d'enregistrements), les pages d'outils réunies en groupes nommés, et une dernière ligne avec votre nom, le bouton **Search everything** (Tout rechercher) et **Sign out**. |
| **Volet central (liste)** | Les enregistrements du module courant, avec une zone de recherche, des filtres, la pagination et le bouton **New** (Nouveau). |
| **Volet droit (détail)** | L'enregistrement sélectionné — ou le formulaire lorsque vous en créez ou en modifiez un. Une barre **Quick note** (Note rapide) se trouve en bas. |

Une fine **barre d'état** court en bas de l'écran, avec le serveur, la base de données et la
version, un lien **check for updates** (rechercher des mises à jour) et un lien **Help** (Aide).
**check for updates** compare votre version à la dernière version publiée sur GitHub (voir la
[FAQ](faq.md#display)).

Vous pouvez réorganiser la disposition à votre convenance ; chacun de ces réglages est mémorisé par
votre navigateur :

- **Redimensionnez** la barre latérale ou la liste en faisant glisser son bord droit.
- **Repliez** la barre latérale avec le petit bouton rond **‹** au milieu de son bord droit
  (**Collapse sidebar**, Replier la barre latérale) ; cliquez sur la fine barre restante pour la
  faire réapparaître.
- Lorsque la fenêtre est basse, les boutons des modules défilent dans leur propre zone, de sorte que
  les rapports et les groupes d'outils restent visibles.

!!! tip "Une deuxième fenêtre"
    Cliquez sur le **logo** en haut de la barre latérale (**Open in a new tab**, Ouvrir dans un
    nouvel onglet) pour rouvrir l'application dans un nouvel onglet — déjà connecté — et travailler
    sur deux choses côte à côte.

!!! tip "Sur un téléphone ou une fenêtre étroite"
    La barre latérale devient un tiroir derrière un bouton de menu **☰** dans le coin supérieur
    gauche, et la vue du module affiche soit la liste, soit l'enregistrement — jamais les deux.
    Utilisez **Back to list** (Retour à la liste) pour revenir.

## S'orienter { #finding-your-way }

=== "Modules"

    Les **boutons de module** de la barre latérale (Tasks, Journal, Planner, Resources, Notes,
    Goals, Writings, Departments, Courses, …) ouvrent chaque type d'enregistrement. Ils sont
    disposés deux par ligne, et le nombre entre parenthèses après chaque nom indique combien
    d'enregistrements contient le module. Les modules affichés, leur ordre et leur code court
    (affiché à droite du bouton, p. ex. `T`) se règlent dans
    [Settings → Modules](settings.md#modules).

=== "Pages d'outils"

    Sous les modules, les **pages d'outils** sont réunies en cinq groupes. Cliquez sur un groupe
    pour ouvrir le menu de ses pages à côté de la barre latérale ; le groupe qui contient la page
    où vous êtes est mis en évidence et indique le nom de cette page.

    | Groupe | Pages |
    |---|---|
    | **Views** (Vues) | Today, Calendar, Kanban, Dashboards, Insights, Sprints, Reports |
    | **Library** (Bibliothèque) | All bookmarks, Flash cards, Highlights, Local notes, Checklists (week) |
    | **Import & export** (Import et export) | Batch entry, Import files, Import notes, Image renamer, Static website |
    | **Admin** (Administration) | Parameters, Admin, Maintenance, Audit log, Trash, Settings, Users, Roles & permissions |
    | **Help & shortcuts** (Aide et raccourcis) | Ouvre directement la page d'aide |

    Vous ne voyez que les pages que vos permissions autorisent, et un administrateur peut masquer
    celles que vous n'utilisez pas (voir [Settings → Pages](settings.md#pages-and-features)) ; un
    groupe resté vide disparaît. **Checklists (week)** (Listes de contrôle de la semaine) s'ouvre
    dans un nouvel onglet — voir [Tableaux de bord](dashboards.md#the-weekly-checklists).

=== "Clavier"

    Appuyez sur **?** pour afficher l'aide des raccourcis, sur **/** pour ouvrir la page de
    recherche, et sur **g** suivi d'une lettre pour aller à un module — par exemple **g t** pour
    Tasks. Voir [Raccourcis clavier](keyboard-shortcuts.md).

=== "Recherche"

    Cliquez sur la loupe (**Search everything**, Tout rechercher) de la dernière ligne de la barre
    latérale, ou appuyez sur **/**, pour ouvrir la page **Search** (Recherche) : un seul formulaire
    qui cherche à la fois dans tous les modules, dans votre dossier de notes locales et dans les
    fichiers des dépôts. Voir [Recherche, filtres et rapports](search.md#global-search).

Cliquer sur le bouton de la page où vous vous trouvez déjà actualise ses données.

## La page Today { #the-today-page }

**Today** (Aujourd'hui), dans le groupe **Views**, affiche une carte par module principal
(Tasks, Journal, Planner, Resources, Notes, Writings, Goals) avec deux courtes listes :

- **Created** (Créés) — les enregistrements les plus récents, avec l'heure (aujourd'hui) ou la date
  de leur création.
- **Modified** (Modifiés) — les enregistrements modifiés après leur première rédaction.

Cliquez sur un titre pour ouvrir l'enregistrement dans son module. Le nombre d'enregistrements par
liste se règle dans [Settings → Tasks](settings.md#tasks-and-other-preferences) (**Today page:
records per column** — Page Today : enregistrements par colonne).

## Ce que vous pouvez faire dépend de votre rôle { #what-you-can-do-depends-on-your-role }

Votre rôle est affiché entre parenthèses après votre nom, en bas de la barre latérale. La plupart des personnes sont
**Data entry** (Saisie : créer et modifier des enregistrements) ou **Read only** (Lecture seule :
consulter, rechercher et lire, mais sans **New**, **Edit** (Modifier), **Delete** (Supprimer) ni
note rapide). Les administrateurs peuvent accorder ou retirer chaque capacité — voir
[Comptes et permissions](administration.md). Si vous ouvrez une page que vous n'avez pas le droit
d'utiliser, vous verrez **You don't have permission to view this page.** (Vous n'avez pas la
permission de voir cette page.)

## Pour aller plus loin { #where-to-go-next }

- [Modules et enregistrements](records.md) — créer, modifier et organiser les enregistrements.
- [Recherche, filtres et rapports](search.md) — retrouver les choses.
- [Calendrier](calendar.md), [Tâches, Kanban et sprints](tasks-kanban-sprints.md),
  [Objectifs, habitudes et indicateurs](goals-indicators.md).
- [Boîte de réception : importer depuis le téléphone et l'ordinateur](import-capture.md).
- [FAQ et dépannage](faq.md).
