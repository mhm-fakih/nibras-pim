# Galerie

**Galerie** (l'icône des photos) est une visionneuse d'images plein écran pour un dossier d'images et ses sous-dossiers. Vous pouvez aussi annoter une image (dessiner dessus, surligner, rogner et renommer), de sorte qu'une capture d'écran puisse être annotée avant de la joindre à une note ou de l'envoyer.

Le dossier est le **Dossier de la galerie** des [Paramètres](settings.md) (défini pendant la visite de configuration sous le nom **Dossier des images**). Par défaut, il s'agit du dossier `Pictures` de votre téléphone.

## Navigation { #browsing }

- Balayez vers la gauche ou la droite, ou utilisez les boutons ronds **Image précédente** / **Image suivante**, pour passer d'une image à l'autre.
- Les images s'affichent à l'endroit, quelle que soit l'orientation de l'appareil photo lors de la prise.

## Annoter une image { #marking-up-a-picture }

Une rangée de puces d'outils est posée sur l'image :

| Outil | Effet d'un glissement |
|---|---|
| **Afficher** | Aucun ; un balayage change de page. C'est l'outil par défaut. |
| **Stylo** | Trace des lignes à main levée. |
| **Surligner** | Trace une bande de surligneur sur l'image. Le glissement détermine la largeur de la bande ; sa hauteur est fixe, comme la pointe d'un surligneur. |
| **Rogner** | Définit la zone à conserver. |

Lorsqu'un outil est sélectionné, un glissement dessine au lieu de changer de page : utilisez donc les boutons **Image précédente** / **Image suivante** pour avancer. **Annuler la dernière annotation** supprime l'annotation la plus récente.

Le champ **Libellé (renomme le fichier)** renomme l'image : le libellé est ajouté à la fin du nom du fichier. Appliquer un nouveau libellé remplace le précédent au lieu de s'y ajouter.

!!! note "Pas de bouton Enregistrer"
    Vos annotations et votre libellé sont enregistrés automatiquement lorsque vous quittez l'image : en passant à la suivante ou à la précédente, en revenant à **Afficher** ou en quittant la Galerie. Si l'enregistrement échoue, l'application indique **Impossible d’enregistrer cette image — ses annotations sont toujours là.** et reste sur l'image pour que rien ne soit perdu.

!!! tip "L'original est conservé"
    La première fois qu'une image est annotée, l'original intact est copié dans un dossier caché `.scr-backup` à côté d'elle. Rien ne vide ce dossier automatiquement : videz-le donc de temps en temps, une fois satisfait de vos modifications.

Le rognage est toujours appliqué en dernier, quel que soit l'ordre dans lequel vous avez dessiné, afin que vos annotations restent là où vous les avez placées.

## Supprimer une image { #deleting-a-picture }

!!! warning
    Le bouton rouge **Supprimer la photo** dans le coin supprime l'image affichée **immédiatement, sans confirmation**. Cela permet de vider rapidement un dossier de captures d'écran : soyez donc prudent.
