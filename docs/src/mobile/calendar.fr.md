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
- Les heures affichées vont de l'**Heure de début du calendrier** à l'**Heure de fin du calendrier** définies dans les [Paramètres](settings.md) (de 5 à 22 par défaut). Toute la plage tient à l'écran sans défilement.
- **Créneau** (15 min / 30 min / 60 min), sous la grille, définit la taille d'un créneau horaire, c'est-à-dire le pas sur lequel s'alignent les nouvelles entrées et les glissements.

### Mini-calendrier { #mini-calendar }

Lorsque **Mini-calendrier** est activé dans les Paramètres (c'est le cas par défaut), une bande en haut affiche la semaine dernière et la semaine en cours, chaque ligne commençant par son numéro de semaine (**S41**). Un point marque les jours qui ont des entrées. Touchez un jour pour y aller.

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

## Modifier une entrée { #change-an-entry }

- **Touchez** une entrée pour l'ouvrir en modification (**Modifier l’entrée**). Le type ne peut plus être changé une fois l'entrée créée.
- **Faites glisser la fine bande** du bord supérieur ou inférieur pour changer l'heure de début ou de fin.
- **Appuyez longuement et faites glisser** l'entrée pour la déplacer à une autre heure du même jour.

Pendant le glissement, un aperçu montre où elle va atterrir. Rien ne change tant que vous n'avez pas relâché.

!!! note "Plans récurrents hors ligne"
    Une occurrence future d'un plan récurrent n'est pas encore une fiche sur le serveur. La modifier en crée une, ce qui ne peut pas se faire hors ligne. Vous verrez **Impossible de modifier une répétition hors ligne — réessayez une fois connecté**.
