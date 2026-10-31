# Fichiers joints

Toute note peut avoir des fichiers joints — un PDF, des photos, un enregistrement, un schéma. Ils se
trouvent dans le **dossier compagnon** de la note : un dossier placé à côté de la note, portant le
même nom suivi de `.d`.

```
~/Knotes/
├── 2026_06_10-143025~N~p2~F.md
└── 2026_06_10-143025~N~p2~F.d/
    ├── receipt.pdf
    └── talk.mp3
```

Comme il s'agit d'un simple dossier, vous y ajoutez des fichiers avec votre gestionnaire de fichiers
habituel, et n'importe quel outil de synchronisation les emporte avec lui. Voir
[Dossiers compagnons](../concepts/companion-folders.md) pour l'idée qui sous-tend ce principe.

## Joindre des fichiers à une note { #attach-files-to-a-note }

Depuis l'application :

1. Sélectionnez la carte de la note, et ouvrez le panneau latéral (**Ctrl+E**) s'il est fermé.
2. Dans le panneau **Files** (fichiers) sous la note, cliquez sur **add files…** (ajouter des
   fichiers) et choisissez les fichiers — ou faites-les glisser depuis votre gestionnaire de
   fichiers et déposez-les sur le panneau.

Les fichiers sont copiés dans le dossier compagnon, qui est créé dès l'arrivée du premier fichier.

Depuis votre gestionnaire de fichiers :

1. Sélectionnez la carte de la note.
2. Appuyez sur **Ctrl+O**, ou cliquez sur **open folder** (ouvrir le dossier) sur la carte.
3. Le dossier compagnon est créé (s'il n'existe pas encore) et ouvert dans votre gestionnaire de
   fichiers. Déposez-y vos fichiers.

Pour ranger des éléments de votre dossier de téléchargements dans une **nouvelle** note, cochez-les
dans la [liste de réception](writing-notes.md#the-inbox-file-downloads-into-a-note) avant
d'enregistrer la note.

!!! tip "Sessions de saisie"
    Activez le bouton dossier de la barre d'outils (**Auto-open the note's files folder after each
    save** — ouvrir automatiquement le dossier de fichiers de la note après chaque enregistrement).
    Chaque note que vous enregistrez ouvre alors aussitôt son dossier compagnon, prêt à recevoir
    des fichiers.

## Le panneau des fichiers { #the-files-panel }

Le panneau **Files** se trouve sous la note dans le panneau latéral (**Ctrl+E**) et liste le dossier
compagnon de la note sélectionnée — les dossiers d'abord, puis les fichiers avec leur taille.
Cliquez sur une ligne pour l'ouvrir avec l'application par défaut de votre système. **open folder**
ouvre le dossier entier dans votre gestionnaire de fichiers (**Ctrl+O**). Une note sans fichiers
affiche **No files yet — add some, or drop them here** (pas encore de fichiers — ajoutez-en ou
déposez-les ici) ; consulter une note ne crée jamais de dossier vide.

Sur le tableau, la ligne d'actions de la carte compte plutôt les fichiers, par exemple `· 3 files`.

## Retrouver une note par ses fichiers { #find-a-note-by-its-files }

La [recherche](board-and-filters.md#search) porte aussi sur les noms des fichiers et dossiers que
contient le dossier compagnon de chaque note, à n'importe quelle profondeur — tapez une partie du
nom d'un PDF ou d'un enregistrement pour trouver la note à laquelle il appartient.

## Ce qu'il advient des fichiers lorsque vous… { #what-happens-to-the-files-when-you }

| Action | Fichiers joints |
|---|---|
| Réétiquetez la note | Le dossier est renommé en même temps que la note |
| Fusionnez des notes | Tous les fichiers joints sont déplacés dans le dossier de la note conservée, et les noms de fichiers de chaque partie sont listés sous son texte |
| Supprimez la note | Impossible tant que le dossier contient encore des fichiers — videz-le d'abord |

!!! warning "Supprimer une note qui a des fichiers"
    knotes refuse de supprimer une note qui a encore des fichiers joints, afin qu'ils ne finissent
    pas oubliés dans un dossier caché. Le bouton de suppression est désactivé sur ces cartes, et
    **Ctrl+D** explique pourquoi. Déplacez ou supprimez d'abord les fichiers (**Ctrl+O** ouvre le
    dossier), puis supprimez la note.

## Enregistrements et marques temporelles { #recordings-and-time-marks }

Si un dossier compagnon contient des fichiers de signets `.tmk` (écrits par le
[lecteur audio](../audio-player/index.md) ou par le téléphone), les notes longues affichent sur
leur carte un compteur de **time marks** (marques temporelles). Voir
[Signets (.tmk)](../concepts/bookmarks-tmk.md).
