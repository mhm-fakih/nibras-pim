# Tableaux de bord et analyses

## Tableaux de bord { #dashboards }

Un **dashboard** (tableau de bord) est une page que vous composez à partir de **regions** (zones) toutes
prêtes et que vous laissez ouverte — sur un écran mural, une télévision ou un moniteur inutilisé.

### Créer un tableau de bord { #building-a-dashboard }

1. Ouvrez **Dashboards** (Tableaux de bord) depuis la barre latérale et cliquez sur **New dashboard** (Nouveau tableau de bord).
2. Saisissez un **Dashboard name** (Nom du tableau de bord), le nombre de **Columns** (Colonnes) de la grille et
   **Auto-refresh (min)** (Actualisation automatique, en minutes ; 0 la désactive).
3. Cliquez sur **Add region** (Ajouter une zone) pour chaque bloc souhaité, puis choisissez son **Component** (Composant) :

    | Composant | Affiche | Paramètres |
    |---|---|---|
    | **Quote of the day** (Citation du jour) | La note mise en favori la plus récente d'un type donné | **Note type code** (Code du type de note), par ex. `quote` |
    | **Calendar (filtered by type)** (Calendrier filtré par type) | Une semaine d'entrées du journal et du planificateur | **Event type name** (Nom du type d'événement ; vide pour tous), **Start hour** (Heure de début), **End hour** (Heure de fin) |
    | **Goals habit heatmap** (Carte de chaleur des habitudes) | Les cartes de chaleur des objectifs marqués *Show habit heatmap on dashboards* | — |
    | **Kanban board** (Tableau Kanban) | Le [Kanban](tasks-kanban-sprints.md#kanban) | — |
    | **File content** (Contenu de fichier) | Le texte d'un fichier situé dans l'un des dépôts | **Repo** (Dépôt, 1–3), **Relative path** (Chemin relatif) |
    | **Quick tasks (quick-tasks.md, open ones)** (Tâches rapides) | Le fichier `quick-tasks.md` de l'application de bureau (à la racine du dossier de notes), sans les tâches cochées | — |

4. Indiquez sur combien de **Columns** de la grille s'étend chaque zone, et utilisez les flèches
   **Move up** / **Move down** (Monter / Descendre). Chaque zone affiche un **Preview** (Aperçu) en direct.
5. Cliquez sur **Save** (Enregistrer).

Dans la liste des tableaux de bord, utilisez l'icône en forme d'œil pour **View** (Afficher) un tableau de bord, le crayon pour
**Edit** (Modifier) et la corbeille pour **Delete** (Supprimer).

!!! tip "Faire correspondre les types du calendrier"
    Le champ **Event type name** de la zone calendrier correspond au *nom* du type, à la fois dans le Journal et dans
    le Planificateur. Vous pouvez donc définir le même nom (par exemple « family ») dans les deux sur la page
    [Parameters](administration.md#parameters) — chacun peut avoir sa propre couleur.

### L'afficher sur un écran { #showing-it-on-a-screen }

**View** ouvre le tableau de bord seul, sans la barre latérale ni le reste de l'interface, dans un nouvel onglet.
Il actualise ses données toutes les deux ou trois minutes et se recharge entièrement selon l'intervalle
**Auto-refresh**, ce qui lui permet de tourner sans surveillance pendant des jours.

!!! warning "Les liens d'affichage des tableaux de bord ne demandent pas de connexion"
    La page d'affichage est conçue pour une télévision sur laquelle personne n'est connecté : **toute personne
    qui peut atteindre le serveur et connaît le lien peut voir le contenu de ce tableau de bord**. N'y placez que
    des zones que vous acceptez de montrer ainsi, et ne diffusez pas largement son lien.

## Les listes de contrôle de la semaine { #the-weekly-checklists }

**Checklists (week)** (Listes de contrôle de la semaine ; barre latérale → **Library**) affiche les listes de contrôle
quotidiennes et hebdomadaires que vous tenez sur le téléphone, une semaine à la fois, sous forme de page murale en
lecture seule. Elle s'ouvre dans un nouvel onglet, sans rien autour, et fait tenir toute la semaine à l'écran — le
texte rapetisse plutôt que la page ne défile. Les listes sont conservées dans la base de données, avec laquelle le
téléphone se synchronise (voir [Cocher depuis le web](#ticking-on-the-web)) ; une modification faite sur le téléphone
apparaît ici dès qu'il s'est synchronisé.

- Chaque groupe d'éléments forme un bloc avec son propre score. Les groupes quotidiens ont une colonne par jour ; les
  groupes hebdomadaires n'ont qu'une colonne **Week** (Semaine).
- Une marque vaut ✓ pour fait, ◐ pour fait en partie, ou une case vide. Les jours à venir sont estompés, la colonne
  d'aujourd'hui est ombrée, et la ligne sous la souris est surlignée pour que vous puissiez la suivre d'un bout à
  l'autre.
- L'en-tête affiche le numéro et les dates de la semaine, la part des éléments quotidiens faits chaque jour, et les
  flèches **Previous week** / **Next week** (Semaine précédente / suivante).
- Les éléments en arabe s'affichent de droite à gauche.

La page s'entretient toute seule : lorsque la modification faite sur le téléphone arrive sur le serveur, elle relit la
semaine en une quinzaine de secondes, et elle se recharge entièrement toutes les quelques minutes (4 sauf
modification). Cliquez sur le petit bouton de rechargement de l'en-tête pour choisir un autre intervalle, ou **Never
reload** (Ne jamais recharger) ; le choix est enregistré pour tous les écrans. Ajouter `?reload=N` à l'adresse de la
page fixe l'intervalle (en minutes) pour cet écran seulement.

!!! note
    Contrairement au lien **View** d'un tableau de bord, cette page demande une connexion. Connectez-vous une fois sur
    le navigateur de l'écran : une page ouverte renouvelle d'elle-même sa connexion, elle reste donc connectée.

### Cocher depuis le web { #ticking-on-the-web }

**Checklists** (Listes de contrôle ; barre latérale → **Library**, juste au-dessus de la page murale) affiche les
mêmes listes dans l'application, pour cocher et annoter :

- **Cliquez** sur une case pour changer sa marque : fait (✓), puis fait en partie (◐), puis vide — comme sur le
  téléphone.
- **Clic droit** sur une case pour choisir sa marque et écrire une **note** pour ce jour. Une case annotée porte un
  petit coin jaune ; survolez-la pour lire la note.
- **Cliquez sur un élément** pour voir son historique : chaque jour où il a été coché ou annoté, le plus récent en
  premier.
- **Edit lists** (Modifier les listes) ajoute des groupes et des éléments, les renomme, fait passer un groupe de
  quotidien à **Weekly** (hebdomadaire) et retire des groupes ou des éléments de la liste. Les changements valent à
  partir de la semaine affichée : un élément ajouté en semaine 46 commence cette semaine-là, et un élément retiré en
  semaine 46 apparaît pour la dernière fois en semaine 45. Rien n'est supprimé — l'historique reste, et **Not on this
  week's list** (Absents de la liste de cette semaine), en bas, propose **Reopen** (Rétablir). Une heure devant le nom
  d'un groupe (`07:30 Start of day`) règle le rappel du téléphone.
- Les flèches passent d'une semaine à l'autre ; **This week** (Cette semaine) y ramène. L'icône de fenêtre ouvre la
  page murale.
- L'icône d'**imprimante** imprime des **listes papier** : une feuille A4 qui reprend une seule fois les groupes et
  éléments de la semaine, chacun avec **trois cases, une par jour**, à cocher au stylo sur trois jours, et un cadre de
  notes par jour en bas. Choisissez le premier jour (aujourd'hui par défaut) et appuyez sur **Print** (Imprimer) ; la
  boîte d'impression du navigateur s'ouvre sur l'imprimante actuelle (choisissez-y *Enregistrer au format PDF* pour un
  fichier). Un élément hebdomadaire a une seule case pour les trois jours. Une longue liste se répartit sur deux colonnes,
  et le texte rapetisse jusqu'à ce que tout tienne sur une page.

**Synchronisation avec le téléphone.** Le téléphone garde sa propre copie et échange les changements avec le serveur
à chaque synchronisation ; il fonctionne donc aussi hors ligne. Si le même jour du même élément a été modifié à la
fois sur le téléphone et sur le web entre deux synchronisations, c'est le changement du téléphone qui l'emporte par
défaut. Pour faire l'inverse, ouvrez le menu **⋮** de la page Checklists et choisissez **The web's change stands**
(Le changement du web l'emporte).

Les fichiers de semaine que le téléphone écrivait (dans le dossier `checklists` du dossier de notes) ont été lus une
fois, à la première ouverture de la page ; ils restent en place. **⋮ → Import the week files again** (Réimporter les
fichiers de semaine) les relit en n'ajoutant que ce qui manque à la base.

## Analyses { #insights }

**Insights** (Analyses) vous aide à garder le rythme dans la relecture de votre travail récent. Choisissez une
**period** (période, de *Last week* à *Last year* ; par défaut les 3 derniers mois) et une fenêtre
**reviewed in** (relu dans, de *Last day* à *Last month* ; par défaut la dernière semaine). Votre navigateur
mémorise les deux.

La page affiche :

- **Where your records live** (Où se trouvent vos fiches) — un graphique en anneau des fiches créées pendant la période, par module.
- **Needs review first** (À relire en priorité) — pour chaque module, la part des fiches de la période que vous avez
  modifiées pendant la fenêtre de relecture, de la plus faible à la plus élevée.
- **Activity by type** (Activité par type) — une carte par module avec des barres hebdomadaires des fiches **Created** (Créées) et
  **Updated** (Mises à jour), ainsi que les fiches en attente de relecture. Cliquez sur une fiche pour l'ouvrir.

« Relu » signifie simplement *modifié* pendant la fenêtre de relecture — ouvrez une fiche, améliorez-la,
enregistrez-la.
