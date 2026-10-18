# Notes en texte brut

Dans Nibras, une note est **un simple fichier texte UTF-8 portant l'extension `.md`**, placé dans un dossier que vous
choisissez. Aucune base de données cachée ne détient le vrai contenu : le fichier *est* la note.

```text
notes/
├── 2026_06_10-143025~T~p2~A.md
├── 2026_06_10-151200~N~p3~B.md
└── 2026_06_11-090512~~p3~.md
```

## Pourquoi du texte brut { #why-plain-text }

- **Il vous appartient.** Ouvrez n'importe quelle note dans n'importe quel éditeur, cherchez-y avec grep, sauvegardez-la, versionnez-la avec git. Rien
  ne casse si toutes les applications Nibras disparaissent demain.
- **Il se synchronise avec n'importe quoi.** Un dossier de petits fichiers texte, c'est exactement ce que les outils de synchronisation
  de fichiers savent bien gérer — voir [Synchronisation](sync.md).
- **Toutes les applications peuvent le partager.** Les applications pour téléphone, bureau et terminal lisent et écrivent toutes dans le même dossier ;
  une note capturée sur l'une apparaît donc sur les autres sans aucune étape de conversion.
- **Il reste rapide.** Les applications parcourent directement le dossier ; aucun index ne risque de se désynchroniser
  de ce qui se trouve sur le disque.

## À quoi ressemble une note { #what-a-note-looks-like }

Le corps est du texte libre. Le Markdown est bienvenu mais pas obligatoire. Quelques conventions sont comprises par
les applications :

| Dans le texte | Signification |
|-------------|---------|
| Première ligne | Considérée comme le titre de la note : affichée en gras sur les cartes, et utilisée comme titre de l'enregistrement lors de l'import. |
| `#tag` | Un hashtag libre, recueilli dans le nuage d'étiquettes des panneaux de filtres (les hashtags arabes fonctionnent aussi). |
| `==marked text==` | Un surlignage — la même notation qu'Obsidian et GitHub. Les lecteurs du bureau et du téléphone l'affichent en couleur. |

Toutes les informations *structurées* — quand la note a été écrite, de quel genre de chose il s'agit, son
importance, le domaine de vie auquel elle appartient — se trouvent dans le **nom du fichier**, pas dans le texte.
C'est décrit dans [Noms de fichiers et étiquettes](filenames-and-tags.md).

## Ce que contient le dossier en plus des notes { #what-lives-in-the-folder-besides-notes }

| Élément | Ce que c'est |
|------|------------|
| `<note name>.d/` | Le [dossier compagnon](companion-folders.md) de pièces jointes de la note. |
| `.deleted/`, `.merged/`, `.imported/`, `imported/` | Dossiers d'intendance : notes supprimées en douceur, notes absorbées par une fusion, notes déjà importées sur le serveur. Jamais affichés comme des notes. |
| `.knotes.json` | Les réglages par dossier de l'application de bureau (sa liste de types et de catégories). |
| Autres fichiers cachés (p. ex. le `.stfolder` propre à un outil de synchronisation) | Ignorés. |
| Tout le reste | Affiché comme **needs metadata** (métadonnées requises) — un fichier ou dossier qui attend d'être transformé en véritable note. |

!!! info "Rien n'est effacé par surprise"
    Sur le bureau, supprimer une note la déplace dans `.deleted/` et fusionner déplace les notes absorbées dans
    `.merged/` ; les deux opérations peuvent donc être annulées à la main. Une note qui a encore des fichiers joints ne peut
    pas du tout être supprimée tant que vous ne vous êtes pas occupé de ces fichiers — sur n'importe quel appareil.

## Pour aller plus loin { #next }

- [Noms de fichiers et étiquettes](filenames-and-tags.md)
- [Dossiers compagnons](companion-folders.md)
