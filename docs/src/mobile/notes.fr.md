# Écrire et modifier des notes

La page **Notes** est l'écran d'accueil de l'application. Elle liste les notes de votre dossier de notes et propose, en bas, une zone pour écrire la suivante.

!!! tip "Pourquoi la liste est-elle presque vide ?"
    La liste s'ouvre en affichant **uniquement les notes du jour**. Le téléphone sert surtout à capturer : « ce que j'ai écrit aujourd'hui » est donc la vue par défaut. L'en-tête **Filtres** au-dessus de la liste indique le filtre actif. Touchez-le et utilisez **Tout effacer**, ou choisissez d'autres jours dans le calendrier, pour en voir davantage. Voir [Retrouver des notes](search-filters.md).

## La barre du haut { #the-top-bar }

- Le nom et la version de l'application, et en dessous **N notes · M aujourd’hui** : le nombre de notes dans le dossier, et combien ont été écrites aujourd'hui.
- Lorsque la synchronisation en arrière-plan est activée, une troisième ligne indique l'état de la synchronisation, par exemple **synchronisé il y a 5 min** ou **hors ligne · dernière synchro hier**.
- **Trier les notes** (l'icône à lignes) choisit l'ordre et le regroupement. Voir [Retrouver des notes](search-filters.md).
- **Rechercher** (la loupe) ouvre une zone de recherche.

Si vous êtes connecté à un serveur Nibras et que des plans approchent, un panneau repliable **Projets à venir (N)** apparaît au-dessus de la liste avec les cinq prochains.

## Écrire une note { #write-a-note }

1. En bas de l'écran, touchez **Nouvelle note — touchez pour développer** si le formulaire est replié.
2. Saisissez votre texte dans **Écrivez une note rapide…**.
3. Vérifiez les trois rangées de puces au-dessus de la zone :
    - **Type** : le genre de note (par exemple Travail, Idées, À faire).
    - **Prio** : la priorité, un nombre (de 1 à 5 par défaut).
    - **Cat.** : la catégorie.
4. Touchez la flèche d'envoi (**Enregistrer la note**).

La note est enregistrée immédiatement sous forme de nouveau fichier. Les puces conservent leurs valeurs pour la note suivante, car celle-ci est généralement du même genre. Une note a toujours un type et une catégorie : toucher une puce la sélectionne, mais ne la désélectionne jamais.

Le type, la priorité et la catégorie par défaut des nouvelles notes se définissent dans les [Paramètres](settings.md). Touchez la flèche de l'en-tête **Nouvelle note** pour replier le formulaire pendant que vous lisez. L'application se souvient s'il est replié.

!!! note "Les brouillons sont en sécurité"
    Ce que vous tapez est conservé comme brouillon au fil de la saisie. Si vous fermez l'application, la balayez ou si le téléphone redémarre, le texte est de retour dans la zone la fois suivante.

### Comment la note est nommée { #how-the-note-is-named }

Chaque note est un fichier nommé d'après le moment où vous l'avez enregistrée, suivi de son type, de sa priorité et de sa catégorie :

```
2026_06_10-143025~W~p3~P.md
```

Cela correspond au 10 juin 2026 à 14:30:25, type `W`, priorité 3, catégorie `P`. Si deux notes sont enregistrées dans la même seconde, la seconde reçoit `-2` après l'heure. Le format de l'heure peut être modifié dans les Paramètres (**Format d’horodatage**). Le schéma complet est expliqué dans [Noms de fichiers et étiquettes](../concepts/filenames-and-tags.md).

## Les cartes de notes { #note-cards }

Chaque note de la liste est une carte qui affiche :

- Les premières lignes de la note (3 par défaut, voir **Lignes d’aperçu** dans les Paramètres). Les lignes en arabe sont automatiquement alignées à droite, ligne par ligne.
- Les **fichiers joints**, s'il y en a (voir plus bas).
- Des badges pour le type, la priorité et la catégorie, ainsi que l'heure.
- Une icône de nuage si la note a été envoyée à Nibras : un nuage d'envoi pendant l'attente (**En attente pour Nibras**), un nuage coché une fois arrivée (**Envoyée à Nibras**).
- **Modifier**, une icône de suppression et **Plus** (⋮).

Touchez une carte pour la **lire**.

### Le menu Plus (⋮) { #the-more-menu }

| Élément | Effet |
|---|---|
| **Envoyer vers Nibras** | Crée cette note comme fiche sur votre serveur Nibras. Affiche **En attente — sera synchronisée** pendant l'attente, puis **Renvoyer vers Nibras** une fois envoyée. Voir [Synchronisation Nibras](sync.md#push-a-note-to-nibras). |
| **Ouvrir le dossier des fichiers (N)** | Ouvre le dossier des pièces jointes de la note dans un gestionnaire de fichiers. N'apparaît que s'il y a des pièces jointes. |
| **Modifier dans l’éditeur ci-dessous** | Charge la note dans la zone du bas (l'en-tête indique **Modification de la note**), pour modifier son texte et ses étiquettes sans quitter la liste. Touchez **Annuler** pour arrêter la modification. |
| **Supprimer** | Supprime la note, après confirmation. |

## Lire et modifier une note { #read-and-edit-a-note }

Toucher une carte l'ouvre en plein écran. Deux puces en haut permettent de basculer entre **Lecture** et **Modifier**.

**Lecture**

- Le Markdown est affiché mis en forme : titres, gras, listes, etc.
- **Touchez une phrase pour la surligner.** Touchez à nouveau pour retirer le surlignage. Le surlignage est écrit dans la note elle-même sous la forme `==…==`, il apparaît donc aussi sur l'ordinateur et sur le serveur. L'en-tête compte le nombre de phrases surlignées.

**Modifier**

- Tout l'écran devient l'éditeur. Vos modifications sont enregistrées quand vous faites une pause dans la saisie, puis à nouveau quand vous quittez (Retour, passage à **Lecture** ou ouverture d'une autre note). Il n'y a pas de bouton Enregistrer.

**Étiquettes en bas**

Dans les deux modes, les puces **Type**, **Prio** et **Cat.** restent épinglées sous le texte. En changer une renomme le fichier de la note en conséquence. La note conserve son heure d'origine.

## Pièces jointes { #attachments }

Une note peut avoir des fichiers joints. Ils se trouvent dans un **dossier compagnon** à côté de la note, portant le même nom suivi de `.d` :

```
2026_06_10-143025~W~p3~P.md
2026_06_10-143025~W~p3~P.d/
    photo.jpg
    recording.m4a
```

La carte liste jusqu'à cinq noms de fichiers joints. Touchez-en un pour l'ouvrir : l'audio et la vidéo sont lus dans le [lecteur](player.md) de l'application (leur icône est teintée), et tout le reste s'ouvre dans l'application qui le prend en charge. S'il y en a davantage, une ligne **… +N autres** ouvre le dossier entier. Les pièces jointes sont généralement créées par [Collecter des fichiers](collect.md) ou par la synchronisation depuis votre ordinateur. Voir [Dossiers compagnons](../concepts/companion-folders.md).

## Supprimer une note { #delete-a-note }

Touchez l'icône de suppression sur la carte, ou **Supprimer** dans le menu ⋮. La question **Supprimer cette note ?** vous est posée avant toute action.

!!! warning "Les notes avec pièces jointes ne peuvent pas être supprimées ici"
    Si le dossier compagnon de la note contient encore des fichiers, la boîte de dialogue indique **Impossible de supprimer cette note** et les liste. Supprimer la note laisserait ces fichiers sans rien qui les nomme. Videz d'abord le dossier (**Ouvrir le dossier des fichiers**), puis supprimez la note.

Une note supprimée est un fichier supprimé. Il n'y a pas de corbeille.

## Les fichiers qui ne sont pas encore des notes { #files-that-arent-notes-yet }

Tout autre élément de votre dossier de notes apparaît quand même dans la liste :

- Les fichiers `.md` ou `.txt` simples provenant d'autres applications s'affichent sans type, priorité ni catégorie. Donnez-leur des étiquettes et ils deviennent des notes ordinaires.
- Les fichiers dont le nom ne suit pas le schéma des notes sont listés par nom de fichier, pour que vous puissiez les repérer et les traiter. Le filtre **Noms non standard** les retrouve tous.
- Les anciennes notes `.txt` à deux champs des versions précédentes sont toujours lues, et sont converties au format `.md` actuel la prochaine fois que vous les modifiez.

## Les boutons flottants { #the-floating-buttons }

En bas à droite de la liste :

- **Actualiser les notes + synchroniser** (la flèche circulaire) relit le dossier, actualise les listes de types et de catégories depuis le serveur et lance immédiatement une synchronisation complète.
- Sur les longues listes, **Aller en haut** et **Aller en bas** apparaissent également. Lorsque la liste est groupée, un bouton permet de **Réduire tous les groupes** / **Développer tous les groupes**.
