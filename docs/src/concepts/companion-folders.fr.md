# Dossiers compagnons

Une note est un simple fichier texte, mais une capture s'accompagne souvent de *choses* : un PDF, une photo, un
enregistrement, un schéma. Elles vivent dans le **dossier compagnon** de la note — un dossier placé à côté de la note,
qui porte son nom et se termine par `.d`.

```text
notes/
├── 2026_06_10-143025~N~p2~B.md
└── 2026_06_10-143025~N~p2~B.d/
    ├── receipt.pdf
    ├── whiteboard.jpg
    ├── meeting.m4a
    └── meeting.tmk
```

La règle est simple : **le nom du fichier de la note sans `.md`, plus `.d`**. Aucun index ne relie les deux ;
le nom *est* le lien. Le téléphone, le bureau, le terminal et le serveur utilisent tous cette même règle.

## Créé seulement en cas de besoin { #created-only-when-needed }

Une note ordinaire n'a pas de dossier compagnon. Il n'apparaît que lorsque vous joignez quelque chose :

| Où | Comment |
|-------|-----|
| [Bureau](../desktop/index.md) | `Ctrl+O` (ou le bouton *ouvrir le dossier* d'une carte) crée le dossier et l'ouvre dans votre gestionnaire de fichiers — déposez-y les fichiers. Un interrupteur de la barre d'outils peut l'ouvrir automatiquement après chaque note enregistrée. |
| Bureau, régularisation d'un élément isolé | Un **dossier** déposé dans le dossier de notes devient une nouvelle note dont ce dossier est le compagnon. Tout **fichier non texte** (PDF, image, dessin…) devient une nouvelle note dont l'unique ligne est le nom du fichier, et le fichier est déplacé dans le dossier compagnon. |
| [Téléphone](../mobile/index.md), *Collecter des fichiers* | Rassemble captures d'écran, photos de l'appareil photo, téléchargements et enregistrements dans une nouvelle note (dans le dossier de collecte défini dans les Paramètres), avec tous les fichiers dans son dossier compagnon. |

## Ce que les applications en font { #what-the-apps-do-with-it }

- **Les cartes affichent les pièces jointes.** Les cartes du bureau affichent un compteur 📎 et les premiers noms de fichiers, chacun
  cliquable ; les cartes du téléphone listent les fichiers et les ouvrent — les médias dans le lecteur de l'application, tout
  le reste dans l'application que propose Android.
- **Le dossier suit la note.** Sur le bureau et dans le terminal, réétiqueter (ce qui renomme la
  note), supprimer en douceur et fusionner déplacent aussi le dossier compagnon. Une fusion regroupe les fichiers de chaque note
  absorbée dans le dossier de la note conservée, en renommant en cas de conflit.
- **Les fichiers sont protégés contre une suppression accidentelle.** Une note dont le dossier compagnon contient un fichier
  ne peut pas être supprimée — ni sur le bureau, ni sur le téléphone. Déplacez ou supprimez d'abord les fichiers, délibérément, pour que rien
  ne reste abandonné sous un nom que personne ne cherchera.
- **L'import les emporte vers le serveur.** Lorsqu'une note est
  [importée](capture-then-classify.md#import-from-the-folder), les fichiers sont copiés dans le dossier de pièces jointes du nouvel
  enregistrement, et le dossier compagnon est archivé dans `imported/` avec la note.
- **Les signets comptent.** Les [fichiers de signets](bookmarks-tmk.md) `.tmk` présents dans un dossier compagnon apparaissent
  sous forme de compteur de *marques temporelles* sur la carte du bureau.

!!! warning "Réétiquetez les notes avec pièces jointes sur le bureau"
    Le téléphone renomme une note lorsque vous changez ses étiquettes, mais ne renomme pas encore son dossier
    compagnon. Pour les notes qui ont des pièces jointes, changez les étiquettes dans l'application de bureau ou de terminal, qui
    déplacent le dossier en même temps — ou renommez le dossier `.d` à la main pour qu'il corresponde.

## Jamais confondu avec une note { #never-mistaken-for-a-note }

Les dossiers compagnons sont ignorés lors du listage des notes, et un dossier `.d` n'est considéré comme compagnon
que si un `.md` correspondant se trouve juste à côté. Sur le bureau, un dossier isolé qui se termine par hasard par
`.d` sans note correspondante est affiché à la place comme un élément *needs metadata* (métadonnées requises).
