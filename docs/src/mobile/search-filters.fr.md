# Retrouver des notes

La page Notes propose trois outils pour retrouver des éléments : la **recherche**, le **tri et regroupement**, et le **panneau de filtres**. Ils fonctionnent ensemble : les filtres s'appliquent par-dessus la recherche, et la liste affiche ce qui passe les deux.

## Recherche { #search }

Touchez la loupe (**Rechercher**) dans la barre du haut et saisissez votre texte dans **Rechercher dans les notes…**. La liste se réduit au fil de la saisie.

La recherche porte sur :

- le texte de chaque note (les marqueurs de surlignage sont ignorés, une expression correspond donc même à travers un surlignage), et
- les **codes** de type et de catégorie dans le nom du fichier, comme `W` ou `P`. Elle porte sur les codes courts, pas sur les noms complets affichés dans les Paramètres.

La recherche ne tient pas compte de la casse.

!!! note "Ouvrir la recherche efface les filtres"
    Lorsque vous ouvrez la recherche, les filtres sont effacés afin que vous cherchiez dans tout. Sinon, une note pourrait être masquée par un filtre oublié, et un résultat vide serait trompeur. Touchez le **×** pour fermer la recherche et effacer le texte.

## Trier et grouper { #sort-and-group }

Le bouton **Trier les notes** (l'icône à lignes) ouvre un menu en deux parties.

**Ordre**

| Option | Ordre |
|---|---|
| **Trier par date** | Les plus anciennes en haut, les plus récentes en bas, comme une discussion. La liste s'ouvre en bas. |
| **Trier par catégorie** | Par code de catégorie. Les notes sans catégorie viennent en dernier. |
| **Trier par priorité** | Par numéro de priorité. Les notes sans priorité viennent en dernier. |
| **Trier par type** | Par code de type. Les notes sans type viennent en dernier. |

**Regroupement**

| Option | Résultat |
|---|---|
| **Sans regroupement** | Une seule liste simple. |
| **Grouper par type** / **Grouper par priorité** / **Grouper par catégorie** | La liste est divisée en sections repliables comme **Sans type**, **Priorité 3** ou **Sans catégorie**, chacune avec un compteur. Touchez l'en-tête d'une section pour la replier ou la déplier. |

## Le panneau de filtres { #the-filter-panel }

Au-dessus de la liste se trouve une barre intitulée **Filtres**. Lorsque des filtres sont actifs, elle affiche **Filtres (N)**, où N est leur nombre, ainsi que *affichées/total* à droite, pour qu'une liste courte ne soit jamais prise pour des notes manquantes. Touchez la barre pour ouvrir ou fermer le panneau.

!!! tip "Il démarre sur aujourd'hui"
    Au démarrage de l'application, aujourd'hui est déjà sélectionné dans le calendrier : la liste affiche donc les notes du jour. Touchez **Tout effacer** pour tout voir.

### Calendrier { #calendar }

Un calendrier mensuel qui commence le lundi. Chaque jour affiche sa date et, entre parenthèses, le nombre de notes qui y tombent, avec une teinte plus foncée pour les jours les plus chargés.

- Touchez un jour pour n'afficher que les notes de ce jour. Touchez d'autres jours pour les ajouter ; touchez à nouveau un jour choisi pour le retirer.
- Les flèches **Mois précédent** / **Mois suivant** font défiler le calendrier ; **Aujourd’hui** y revient. Changer de mois ne modifie pas la sélection.
- **Création** / **Modification** choisit la date prise en compte :
    - **Création** est l'heure figurant dans le nom du fichier de la note, c'est-à-dire le moment où elle a été écrite. Elle ne change pas lorsque vous modifiez ou réétiquetez la note.
    - **Modification** est le moment où le fichier a été modifié pour la dernière fois.

### Priorité, Type, Catégorie { #priority-type-category }

Chacun affiche ses valeurs sous forme de puces avec un compteur entre parenthèses. Choisissez-en une ou plusieurs pour n'afficher que ces notes. Les compteurs indiquent toujours ce que vous *obtiendriez* en choisissant cette valeur avec les autres filtres tels qu'ils sont, de sorte qu'une option non choisie n'affiche pas 0.

### Étiquettes { #tags }

Un nuage des `#hashtags` écrits dans vos notes. Plus le texte est gros, plus l'étiquette est utilisée. Touchez une étiquette pour n'afficher que les notes qui la contiennent, et touchez-la à nouveau pour la retirer. Les étiquettes sont comparées sans tenir compte de la casse : `#Work` et `#work` sont donc la même étiquette.

### Noms non standard { #non-standard-names }

Pour les fichiers du dossier dont le nom ne suit pas le schéma des notes, comme un fichier qu'un autre appareil a déposé dans un dossier synchronisé.

| Option | Affiche |
|---|---|
| **Tout** | Tout |
| **Notes seulement** | Uniquement les notes correctement nommées |
| **Sans nom seulement** | Uniquement les fichiers non standard, ce qui fait une liste de fichiers à renommer |

### Classement invalide { #bad-taxonomy }

Pour les notes dont le nom est bien formé mais qui utilisent un type, une priorité ou une catégorie non définis dans vos [Paramètres](settings.md) (par exemple un code que vous avez supprimé depuis).

| Option | Affiche |
|---|---|
| **Tout** | Tout |
| **Valides seulement** | Les notes dont toutes les étiquettes sont définies |
| **Invalides seulement** | Les notes avec un type, une priorité ou une catégorie non définis |

**Tout effacer** désactive tous les filtres. Il conserve votre choix **Création** / **Modification**.

## La page Réponses IA { #the-ai-answers-page }

La page [Réponses IA](ai-answers.md) dispose de la même zone de recherche et du même panneau de filtres.
