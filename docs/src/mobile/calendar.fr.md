# Agenda

**Agenda** (l'icône du calendrier) affiche les entrées **Plans** et **Journal** de votre serveur Nibras sur une grille journalière, et vous permet de les ajouter et de les déplacer du bout du doigt. Il se trouve du côté **Sur ce téléphone** du rail parce qu'il fonctionne hors ligne : les nouvelles entrées sont d'abord enregistrées sur le téléphone, puis envoyées au serveur dès qu'une connexion apparaît.

!!! note
    L'agenda affiche des données du serveur : il a donc besoin d'une [connexion à Nibras](sync.md). Sans elle, il n'affiche que les entrées que vous ajoutez sur le téléphone.

## Lire la grille { #reading-the-grid }

- La barre du haut affiche la date (ou la plage de dates) visible.
- **Précédent** / **Aujourd’hui** / **Suivant** font défiler les jours.
- **1 j** / **3 j** affichent un jour ou trois jours côte à côte.
- Les **Plans** sont dessinés en vert et les entrées **Journal** en bleu clair. Les occurrences futures d'un plan récurrent sont dessinées plus pâles.
- Une fine ligne en travers de la colonne d'aujourd'hui marque l'heure actuelle.
- Les heures affichées vont de l'**Heure de début du calendrier** à l'**Heure de fin du calendrier** définies dans [Paramètres](settings.md) → **Calendrier et widgets** (de 5 à 22 par défaut). Toute la plage tient à l'écran sans défilement.
- **Créneau** (15 min / 30 min / 60 min), sous la grille, définit la taille d'un créneau horaire, c'est-à-dire le pas sur lequel s'alignent les nouvelles entrées et les glissements.

### Mini-calendrier { #mini-calendar }

Lorsque **Mini-calendrier** est activé dans Paramètres → **Calendrier et widgets** (c'est le cas par défaut), une bande en haut affiche la semaine dernière et la semaine en cours, chaque ligne commençant par son numéro de semaine (**S41**). Un point marque les jours qui ont des entrées. Touchez un jour pour y aller.

Pour voir plus loin, touchez la petite flèche au bout de la deuxième ligne (**Afficher le mois entier**). La bande se déplie en un mois complet, avec des flèches pour passer au mois précédent ou suivant ; les jours hors du mois sont plus pâles. Touchez la flèche au bout de la ligne du titre du mois (**Revenir à deux semaines**) pour la replier.

## Ajouter une entrée { #add-an-entry }

Chacune de ces actions ouvre le formulaire **Nouvelle entrée** :

- Touchez un créneau vide : l'entrée commence là et dure un créneau.
- **Appuyez longuement et faites glisser** vers le bas de la grille pour couvrir une plage horaire plus longue.
- Touchez **+** (**Ajouter une entrée**) dans la barre du haut.

Dans le formulaire :

| Champ | |
|---|---|
| **Type** | **Plan** ou **Journal**. Le dernier type utilisé est présélectionné. |
| **Date** | Avec les flèches **Jour précédent** / **Jour suivant**. |
| **Début**, **Fin** | Les heures. |
| **Résumé** | Le titre. Le clavier s'ouvre ici. Le texte arabe est automatiquement aligné à droite. |
| **Description** | Détails facultatifs. |

Touchez **Enregistrer**. Vous verrez **Enregistré dans le calendrier**, ou **Enregistré — sera synchronisé une fois en ligne** si vous êtes hors ligne.

## Ajouter une semaine d'un coup { #add-a-week-at-once }

Pour un emploi du temps, saisir les entrées une à une est long. Touchez l'icône liste avec un plus dans la barre du haut (**Ajouter une semaine**) et tapez une entrée par ligne :

```
p lun 10:00 Analyse
p lun,mer 8:00-9:30 Physique
p mar-jeu 14 TP
j ven 2:30pm Réunion de groupe
```

- **`p` ou `j`** en début de ligne en fait un plan ou une entrée de journal. Sans eux, la ligne prend le **Type** choisi sous la zone de texte.
- **Jours** : un jour (`lun`), une liste (`lun,mer`) ou une plage (`lun-ven`). Les noms de jours français, anglais et arabes fonctionnent tous.
- **Heures** : `8`, `8:30` ou `2:30pm`, éventuellement avec une fin (`8:00-9:30`). Sans fin, une entrée dure une heure.
- Tout ce qui suit l'heure est le titre.

Sous la zone, chaque ligne montre ce qu'elle va donner (type, date, heures, titre), ou ce qui ne va pas, par exemple **Ligne 2 : pas d'heure (8, 8:30, 2:30pm…)**.

- **À partir du** est le premier jour à partir duquel les lignes sont placées : chaque jour de la semaine tombe sur sa première date à partir de celui-ci. Touchez la date pour la changer.
- **Répéter** : **Une fois (cette semaine)**, ou utilisez **+** pour répéter **Chaque semaine pendant N semaines, jusqu'au …** (jusqu'à 52), afin de saisir une seule fois l'emploi du temps d'un semestre. Seuls les plans se répètent : **Les entrées de journal sont ajoutées une fois ; seuls les plans se répètent.**

Touchez **Ajouter N** pour tout enregistrer. Comme les entrées simples, elles sont d'abord enregistrées sur le téléphone puis envoyées dès que le serveur est joignable.

## Modifier une entrée { #change-an-entry }

- **Touchez** une entrée pour l'ouvrir en modification (**Modifier l’entrée**). Le type ne peut plus être changé une fois l'entrée créée.
- **Faites glisser la fine bande** du bord supérieur ou inférieur pour changer l'heure de début ou de fin.
- **Appuyez longuement et faites glisser** l'entrée pour la déplacer à une autre heure du même jour.

Pendant le glissement, un aperçu montre où elle va atterrir. Rien ne change tant que vous n'avez pas relâché.

!!! note "Plans récurrents hors ligne"
    Une occurrence future d'un plan récurrent n'est pas encore une fiche sur le serveur. La modifier en crée une, ce qui ne peut pas se faire hors ligne. Vous verrez **Impossible de modifier une répétition hors ligne — réessayez une fois connecté**.
