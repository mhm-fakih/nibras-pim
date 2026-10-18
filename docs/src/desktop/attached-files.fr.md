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

1. Sélectionnez la carte de la note.
2. Appuyez sur **Ctrl+O**, ou cliquez sur **open folder** (ouvrir le dossier) sur la carte.
3. Le dossier compagnon est créé (s'il n'existe pas encore) et ouvert dans votre gestionnaire de
   fichiers. Déposez-y vos fichiers.

La ligne 📎 de la carte se met à jour au prochain rafraîchissement.

!!! tip "Sessions de saisie"
    Activez le bouton dossier de la barre d'outils (**Auto-open the note's files folder after each
    save** — ouvrir automatiquement le dossier de fichiers de la note après chaque enregistrement).
    Chaque note que vous enregistrez ouvre alors aussitôt son dossier compagnon, prêt à recevoir
    des fichiers.

## Ouvrir les fichiers joints { #open-attached-files }

La ligne 📎 d'une carte liste jusqu'à cinq noms de fichiers. Cliquez sur un nom pour ouvrir ce
fichier avec l'application par défaut de votre système. Cliquez sur les **…** finaux ou sur le
compteur pour ouvrir le dossier entier ; survolez-les pour voir tous les noms de fichiers.

## Ce qu'il advient des fichiers lorsque vous… { #what-happens-to-the-files-when-you }

| Action | Fichiers joints |
|---|---|
| Réétiquetez la note | Le dossier est renommé en même temps que la note |
| Fusionnez des notes | Tous les fichiers joints sont déplacés dans le dossier de la note conservée |
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
