# Organiser les notes

knotes n'efface jamais rien de lui-même. La suppression et la fusion déplacent les fichiers dans des
dossiers cachés à l'intérieur de votre dossier de notes ; une erreur peut donc toujours être
réparée avec un gestionnaire de fichiers.

## Supprimer une note { #delete-a-note }

1. Sélectionnez la carte.
2. Appuyez sur **Ctrl+D**, ou cliquez sur le bouton corbeille de la carte.
3. La confirmation affiche les étiquettes et les premières lignes de la note. Appuyez sur
   **Enter** (ou cliquez sur **Yes** — Oui) pour confirmer.

La note est déplacée dans un dossier caché `.deleted/` à l'intérieur du dossier de notes. Pour la
restaurer, ressortez le fichier de ce dossier.

!!! note
    Une note qui a encore des [fichiers joints](attached-files.md) ne peut pas être supprimée.
    Videz d'abord son dossier compagnon.

## Fusionner des notes { #merge-notes }

La fusion réunit plusieurs notes en une seule — utile quand une idée a été saisie en plusieurs
morceaux.

1. Sélectionnez la note qui doit être **conservée** et appuyez sur **Ctrl+M** (ou faites un
   **Ctrl+clic** dessus). La première carte marquée est celle qui est conservée ; elle garde son
   nom de fichier et ses étiquettes.
2. Marquez les autres notes de la même façon, dans l'ordre où vous voulez qu'elles soient ajoutées.
   La barre de titre indique combien sont marquées.
3. Appuyez sur **Ctrl+Shift+M** et confirmez.

Le texte de chaque note absorbée est ajouté à la note conservée sous une ligne `---` qui indique le
fichier d'origine, de sorte que ses anciennes étiquettes et son horodatage ne sont pas perdus. Les
notes absorbées sont déplacées dans un dossier caché `.merged/`, et leurs fichiers joints dans le
dossier compagnon de la note conservée.

**Esc** efface les marques sans fusionner.

## Régulariser les fichiers isolés { #settle-loose-files }

Vous pouvez déposer n'importe quoi dans un dossier de notes — un fichier texte, un PDF, un dossier
entier. Tant qu'il n'a pas un vrai nom de note, knotes l'affiche sous forme de carte rouge
**needs metadata** (métadonnées requises), le classe sous *aujourd'hui* dans le calendrier et le
compte dans la barre de titre (`· 3 to settle`).

Pour en régulariser un, sélectionnez sa carte et appuyez sur n'importe quel raccourci d'étiquette —
**Ctrl+Shift+<letter>** pour un type ou une catégorie, ou **Ctrl+Shift+<digit>** pour une
priorité. Le résultat dépend de la nature de l'élément :

| Élément | Devient |
|---|---|
| Un dossier | Une nouvelle note portant le nom du dossier, ce dossier devenant son [dossier compagnon](attached-files.md) |
| Un fichier `.md` ou `.txt` | Une vraie note ; son ancien nom de fichier devient la première ligne du texte |
| Tout autre fichier (PDF, image, schéma, …) | Une nouvelle note dont le texte est le nom du fichier, avec le fichier joint |

Les fichiers régularisés conservent leur propre date (celle de la dernière modification du
fichier) ; ils se placent donc à leur véritable position dans le temps.

!!! tip "Trouver tout ce qui doit être régularisé"
    Dans la barre latérale, réglez **Non-standard** (Non standard) sur **Only** (Uniquement). Voir
    [Tableau, barre latérale et filtres](board-and-filters.md#clean-up-queues).

## Plusieurs dossiers de notes { #several-note-folders }

Vous pouvez tenir des dossiers séparés — par exemple personnel et travail — et passer de l'un à
l'autre.

1. Ouvrez **Settings** (Paramètres, **Ctrl+,**).
2. Dans **Note folders** (Dossiers de notes), indiquez un dossier par ligne sous la forme
   `code:path`, et marquez le dossier actif avec `*` :

    ```
    A*:~/Knotes
    B:~/Work-notes
    ```

3. Cliquez sur **Save** (Enregistrer).

Un sélecteur de dossier apparaît alors à côté du bouton d'envoi. Changez de dossier avec lui, ou
avec **Ctrl+1** … **Ctrl+9** pour les neuf premiers dossiers. Chaque dossier garde ses
[propres options d'étiquettes](tagging.md#define-your-own-codes) et peut même communiquer avec son
[propre serveur Nibras](nibras-server.md).

## Fichiers et dossiers ignorés par knotes { #files-and-folders-knotes-ignores }

Ceux-ci n'apparaissent jamais sur le tableau :

- Les fichiers et dossiers cachés (noms commençant par `.`), dont `.deleted`, `.merged`,
  `.imported` et `.knotes.json`.
- Les dossiers de gestion interne nommés `deleted`, `merged`, `imported`, `trash` ou `trashed`,
  quelle que soit la casse.
- Les dossiers compagnons `.d/` qui appartiennent à une note.
- `notes.sqlite` (et ses fichiers `-wal`, `-shm`, `-journal`) ainsi que `claude.md`.
