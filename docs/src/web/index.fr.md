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
(Tâches) si vous êtes venu directement sur la page de connexion.

!!! note "Première connexion"
    Si votre administrateur vous a demandé de choisir votre propre mot de passe, une boîte de
    dialogue **Change your password** (Changer votre mot de passe) apparaît immédiatement. Saisissez
    le **Current password** (Mot de passe actuel), puis le **New password** (Nouveau mot de passe)
    deux fois, et cliquez sur **Change password** (Changer le mot de passe). Vous ne pouvez pas
    utiliser l'application tant que ce n'est pas fait.

Pour vous déconnecter, cliquez sur l'icône **Sign out** (Se déconnecter) à côté de votre nom, en bas
de la barre latérale gauche. Votre session se termine aussi d'elle-même au bout de quelques heures
(12 par défaut) ; il suffit de vous reconnecter.

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
| **Barre latérale gauche** | Le nom et la version de l'application, la zone de recherche globale, un mini-calendrier, des raccourcis vers les rapports et les recherches enregistrées du module courant, les boutons des modules, les pages d'outils, ainsi que votre nom avec **Sign out**. |
| **Volet central (liste)** | Les enregistrements du module courant, avec une zone de recherche, des filtres, la pagination et le bouton **New** (Nouveau). |
| **Volet droit (détail)** | L'enregistrement sélectionné — ou le formulaire lorsque vous en créez ou en modifiez un. Une barre **Quick note** (Note rapide) se trouve en bas. |

Une fine **barre d'état** court en bas de l'écran, avec les informations de version, un lien
**check for updates** (rechercher des mises à jour) et un lien **Help** (Aide).

Vous pouvez réorganiser la disposition à votre convenance ; chacun de ces réglages est mémorisé par
votre navigateur :

- **Redimensionnez** la barre latérale ou la liste en faisant glisser son bord droit.
- **Repliez** la barre latérale avec le chevron **‹** de son en-tête ; cliquez sur la fine barre
  restante pour la faire réapparaître.
- Lorsque la barre latérale manque de place, les boutons d'outils se réduisent à des icônes ;
  survolez une icône pour voir son nom.

!!! tip "Sur un téléphone ou une fenêtre étroite"
    La barre latérale devient un tiroir derrière un bouton de menu **☰** dans le coin supérieur
    gauche, et la vue du module affiche soit la liste, soit l'enregistrement — jamais les deux.
    Utilisez **Back to list** (Retour à la liste) pour revenir.

## S'orienter { #finding-your-way }

=== "Modules"

    Les **boutons de module** de la barre latérale (Tasks, Journal, Planner, Resources, Notes,
    Goals, Writings, Departments, Courses, …) ouvrent chaque type d'enregistrement. Les modules
    affichés, leur ordre et leur code court (affiché à côté du nom, p. ex. `T`) se règlent dans
    [Settings → Modules](settings.md#modules).

=== "Pages d'outils"

    Sous les modules se trouvent les **pages d'outils** : Today, Calendar, Kanban, Dashboards,
    Insights, Sprints, Reports, All bookmarks, Flash cards, Highlights, Local notes et — selon vos
    permissions — Parameters, Admin, Batch entry, Import files, Import notes, Image renamer, Static
    website, Maintenance, Audit log, Trash, Settings, Users, Roles & permissions, et **Help &
    shortcuts** (Aide et raccourcis). Un administrateur peut masquer les pages que vous n'utilisez
    pas (voir [Settings → Pages](settings.md#pages-and-features)).

=== "Clavier"

    Appuyez sur **?** pour afficher l'aide des raccourcis, sur **/** pour aller dans la recherche
    globale, et sur **g** suivi d'une lettre pour aller à un module — par exemple **g t** pour
    Tasks. Voir [Raccourcis clavier](keyboard-shortcuts.md).

=== "Recherche"

    Tapez dans **Search everything…** (Tout rechercher…) en haut de la barre latérale et appuyez
    sur **Enter** pour chercher dans tous les modules (et dans votre dossier de notes locales) à la
    fois. Voir [Recherche, filtres et rapports](search.md).

Cliquer sur le bouton de la page où vous vous trouvez déjà actualise ses données.

## La page Today { #the-today-page }

La page d'accueil de l'application, **Today** (Aujourd'hui), affiche une carte par module principal
(Tasks, Journal, Planner, Resources, Notes, Writings, Goals) avec deux courtes listes :

- **Created** (Créés) — les enregistrements les plus récents, avec l'heure (aujourd'hui) ou la date
  de leur création.
- **Modified** (Modifiés) — les enregistrements modifiés après leur première rédaction.

Cliquez sur un titre pour ouvrir l'enregistrement dans son module. Le nombre d'enregistrements par
liste se règle dans [Settings → Tasks](settings.md#tasks-and-other-preferences) (**Today page:
records per column** — Page Today : enregistrements par colonne).

## Ce que vous pouvez faire dépend de votre rôle { #what-you-can-do-depends-on-your-role }

Votre rôle est affiché sous votre nom dans la barre latérale. La plupart des personnes sont
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
