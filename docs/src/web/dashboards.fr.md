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
