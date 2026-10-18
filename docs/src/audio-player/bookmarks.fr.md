# Marque-pages

Un marque-page signale un instant dans un enregistrement, avec une étiquette facultative. Le lecteur audio est conçu pour
les créer rapidement — pendant l'écoute, sans s'arrêter.

## Ajouter un marque-page { #add-a-bookmark }

Appuyez sur **B** (ou **Ctrl+B**, qui fonctionne même pendant la saisie), ou cliquez sur **＋ Bookmark** (ajouter un marque-page).

Le marque-page est placé quelques secondes **en arrière** — à l'instant que vous venez d'entendre, pas à celui où vous avez
réagi — et la lecture y revient pour que vous l'entendiez à nouveau. La ligne de message le confirme, par exemple
`✓ Bookmarked 12:04 (−5s)`. Réglez le recul avec **Bookmark rewind (seconds)** (recul du marque-page, en secondes) dans les
[Paramètres](settings.md).

### Avec une étiquette { #with-a-label }

1. Appuyez sur **N** pour aller au champ **Optional note for the next bookmark…** (note facultative pour le prochain marque-page).
2. Saisissez une étiquette.
3. Appuyez sur **Enter** (Entrée) (ou **Ctrl+B**) pour ajouter le marque-page avec cette étiquette.

Si vous ne saisissez pas d'étiquette et qu'une piste de sous-titres est activée, le marque-page reçoit comme étiquette la
ligne de sous-titre de cet instant.

## Utiliser vos marque-pages { #use-your-bookmarks }

La liste **BOOKMARKS** (marque-pages) sous les commandes affiche tous les marque-pages du fichier en cours, par ordre chronologique.
Les marque-pages apparaissent aussi sous forme de traits sur la [barre de recherche avec forme d'onde](playback.md#the-waveform-seek-bar).

| Pour… | Faites ceci |
|---|---|
| Aller à un marque-page | Cliquez sur son heure (ou n'importe où sur la ligne en dehors de l'étiquette) |
| Modifier une étiquette | Cliquez sur l'étiquette (ou sur **no label** (sans étiquette)), saisissez, puis appuyez sur **Enter** ou cliquez ailleurs pour enregistrer |
| Supprimer un marque-page | Cliquez sur son bouton corbeille |

La colonne **Bkm** de la liste de lecture indique combien de marque-pages possède chaque fichier, et les fichiers que vous marquez
apparaissent sous **RECENTLY BOOKMARKED** (marqués récemment).

## Le fichier .tmk { #the-tmk-file }

Les marque-pages sont enregistrés immédiatement dans un fichier texte brut à côté de l'enregistrement, portant le même nom avec
l'extension `.tmk` :

```
lecture.mp3
lecture.tmk
```

Un marque-page par ligne — l'heure entre crochets, puis l'étiquette :

```
[1:09]
[12:04] key point on refraction
[1:03:20] auto
```

- Les heures sont au format `M:SS`, ou `H:MM:SS` au-delà d'une heure.
- Les étiquettes écrites par d'autres applications (par exemple une priorité `p2` ou le mot `auto` venant du téléphone) sont
  conservées telles quelles.
- La suppression du dernier marque-page supprime le fichier `.tmk`.

Comme il s'agit de texte brut, vous pouvez lire ou modifier un `.tmk` dans n'importe quel éditeur, et il se synchronise comme tout autre
fichier. Le téléphone écrit le même format, de sorte que les marque-pages créés d'un côté apparaissent des deux. Voir
[Marque-pages (.tmk)](../concepts/bookmarks-tmk.md).

!!! tip "Les marque-pages dans vos notes"
    Si un enregistrement se trouve dans le [dossier compagnon](../concepts/companion-folders.md) d'une note, knotes
    desktop affiche le nombre de marque-pages comme **time marks** (repères temporels) sur la carte de la note.
