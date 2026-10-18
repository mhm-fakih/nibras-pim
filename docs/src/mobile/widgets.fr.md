# Widgets de l'écran d'accueil

knotes est fournie avec dix widgets pour l'écran d'accueil. Pour en ajouter un, appuyez longuement sur un emplacement vide de votre écran d'accueil, choisissez **Widgets**, trouvez **knotes** et faites glisser le widget voulu à sa place. La plupart peuvent être redimensionnés.

| Widget (nom dans le sélecteur) | Affiche | Nécessite un serveur Nibras ? |
|---|---|---|
| [Notes récentes](#recent-notes) | Vos notes récentes, à lire | Non |
| [Tâches du jour](#todays-tasks) | Les tâches prioritaires du jour, avec une barre d'ajout | Non |
| [Journal du jour](#todays-journal) | Les entrées de journal du jour, avec une barre d'ajout | Non |
| [Priorité maximale](#top-priority) | Toutes les notes à votre priorité maximale | Non |
| [Indicateurs Nibras Mobile](#nibras-mobile-kpis) | Des compteurs en un coup d'œil | En partie |
| [Plans à venir](#upcoming-plans) | Vos prochains événements planifiés | Oui |
| [Fiches en favori](#bookmarked-records) | Les fiches que vous avez étoilées | Oui |
| [Heures de prière](#prayer-times) | Date hégirienne, compte à rebours, horaires du jour | Non |
| [Compte à rebours des alarmes](#alarms-countdown) | Le temps restant avant les alarmes du jour | Non |
| [État du téléphone](#phone-status) | Les niveaux de volume | Non |

!!! tip "Touchez le titre pour actualiser"
    Un widget ne se met à jour que lorsque quelque chose le redessine. Sur la plupart des widgets, **toucher le titre** le redessine immédiatement. Sur les widgets qui affichent des données du serveur (**Indicateurs**, **Plans à venir**, **Fiches en favori**), cela récupère aussi d'abord des données fraîches depuis le serveur. Toucher ailleurs sur un widget ouvre généralement l'application.

## Notes récentes { #recent-notes }

Un fil en lecture seule de ce que vous avez écrit récemment, les plus anciennes en haut et les plus récentes en bas, comme une discussion que l'on fait défiler. Le titre indique **Notes du jour** ou **N derniers jours**.

Les notes longues sont coupées après un certain nombre de mots, avec **Lire la suite**. **Touchez une note** pour la déplier sur place, et touchez à nouveau pour la replier. C'est la seule commande ; il n'y a ni filtres ni boutons.

Configurez-le dans les [Paramètres](settings.md) → **Widget des notes récentes** :

| Réglage | Plage | Par défaut |
|---|---|---|
| Période couverte (**aujourd’hui seulement** / **N derniers jours**) | 1 à 30 jours | 3 jours |
| **N mots, puis Lire la suite** | 10 à 200 mots | 40 |
| **Taille du texte N sp** | 10 à 28 sp | 15 sp |

Le widget se redessine lorsque vous touchez **Enregistrer** dans les Paramètres : une nouvelle taille s'affiche donc immédiatement.

## Tâches du jour { #todays-tasks }

Les tâches écrites **aujourd'hui** à votre **Priorité maximale** (avec un titre comme **Tâches p4 du jour**). Il se vide pendant la nuit : c'est donc la liste du travail du jour, pas une archive.

Touchez **+  Ajouter une tâche…** en haut pour en saisir une nouvelle directement depuis l'écran d'accueil. Voir [Capture rapide](capture.md#add-a-task-or-journal-entry-from-the-home-screen).

## Journal du jour { #todays-journal }

Les entrées de journal du jour, toutes priorités confondues. Comme le widget des tâches, il repart de zéro chaque jour. Touchez **+  Écrire le journal du jour…** pour ajouter une entrée.

## Priorité maximale { #top-priority }

Toutes les notes de votre dossier à votre **Priorité maximale** (priorité 4, sauf si vous modifiez **Priorité maximale (widgets)** dans les Paramètres), de tout type et de toute date. S'il n'y en a aucune, il indique **Aucune note de priorité 4**. Toucher le titre le redessine. Ce sont des notes de votre téléphone : aucune synchronisation n'est nécessaire.

## Indicateurs Nibras Mobile { #nibras-mobile-kpis }

Un petit widget de compteurs, avec un libellé abrégé à côté de chaque nombre pour tenir dans une case étroite :

| Affichage | Signification |
|---|---|
| `5 nt. auj. / 1204` | Notes écrites aujourd'hui / notes au total |
| `862 fich.` | Fichiers dans votre dossier de notes, pièces jointes comprises |
| `12 fich. auj.` | Combien de ces fichiers datent d'aujourd'hui |
| `7.7 Go libres` | Espace de stockage libre sur le téléphone, en Go |
| `9 p4` | Notes à votre priorité maximale |
| `3 plans` | Plans à venir (depuis le serveur) |

Certains nombres changent de couleur pour vous avertir : trop de notes à priorité maximale, ou trop peu de plans à venir.

## Plans à venir { #upcoming-plans }

Vos prochains événements de l'agenda Nibras, indiqués par jour et heure (**Dem. 09:00** signifie demain à 09:00). Faites défiler pour en voir davantage. Touchez le titre pour récupérer des plans frais depuis le serveur. Touchez une ligne pour ouvrir l'application.

## Fiches en favori { #bookmarked-records }

Les Plans, Objectifs et Tâches que vous avez étoilés dans les [pages serveur](server-pages.md#bookmark-the-star) de knotes (fiches en favori sur ce téléphone), dans une liste déroulante. Touchez le titre pour synchroniser et redessiner.

## Heures de prière { #prayer-times }

Une large bande avec la date hégirienne, un compte à rebours jusqu'au prochain horaire et une colonne par prière que vous avez choisi d'afficher, la prochaine étant mise en évidence. Elle est en arabe et de droite à gauche, écrite assez grand pour être lue à l'autre bout d'une pièce.

Le compte à rebours n'apparaît qu'à l'approche de la prochaine prière. Le seuil correspond au réglage **Compte à rebours du widget** de la page [Heures de prière](prayer.md#reminders-and-countdown) (1 heure par défaut). Touchez la date pour redessiner ; touchez ailleurs pour ouvrir la page Heures de prière.

## Compte à rebours des alarmes { #alarms-countdown }

Le temps restant avant chacune des [alarmes](alarms.md) restantes du jour. Il se met à jour tout seul chaque minute tant qu'il est sur votre écran d'accueil. Touchez-le pour ouvrir la page Alarmes.

## État du téléphone { #phone-status }

Intitulé **Volumes** : vos niveaux de volume **Sonnerie**, **Notifications**, **Alarme** et **Musique**, sous forme de barres. Ils se mettent à jour lorsque le volume ou le mode de sonnerie change. Touchez le titre pour actualiser. Touchez ailleurs pour ouvrir les réglages de volume du téléphone.
