# knotes desktop

**knotes desktop** est un petit outil de prise de notes pour Linux (et Windows), pensé d'abord pour
le clavier. Vous tapez une idée, choisissez un type, une priorité et une catégorie, appuyez sur
**Ctrl+Enter** — et la note est enregistrée sous forme de simple fichier Markdown dans le dossier de
votre choix. Pas de base de données, pas de compte, aucune dépendance.

L'astuce réside dans le nom du fichier : les étiquettes de chaque note sont écrites dans son nom,
par exemple

```
2026_06_10-143025~W~p3~A.md
```

si bien que le même dossier fonctionne tel quel avec l'[application mobile knotes](../mobile/index.md),
l'[application en terminal](../terminal/index.md) et le serveur Nibras. Synchronisez le dossier
entre vos machines avec n'importe quel outil de synchronisation de fichiers, et toutes les
applications voient les mêmes notes.

!!! tip "Vous découvrez les idées derrière knotes ?"
    Lisez d'abord [Notes en texte brut](../concepts/plain-text-notes.md) et
    [Noms de fichiers et étiquettes](../concepts/filenames-and-tags.md) — ces pages expliquent le
    schéma de nommage des fichiers sur lequel repose cette application.

## Ce que vous pouvez faire { #what-you-can-do }

- **Saisir rapidement** — un champ de texte en bas, des étiquettes persistantes, enregistrement
  d'une seule touche. Voir [Écrire des notes](writing-notes.md).
- **Étiqueter par type, priorité et catégorie** au clavier, et réétiqueter plusieurs notes à la
  suite. Voir [Type, priorité et catégorie](tagging.md).
- **Parcourir et filtrer** en vue kanban, liste ou colonnes, avec une carte thermique calendaire,
  un nuage d'étiquettes et des compteurs en direct. Voir
  [Tableau, barre latérale et filtres](board-and-filters.md).
- **Joindre des fichiers** à une note dans son propre dossier compagnon. Voir
  [Fichiers joints](attached-files.md).
- **Fusionner, supprimer et régulariser** les notes et les fichiers isolés en toute sécurité. Voir
  [Organiser les notes](organizing-notes.md).
- **Rester synchronisé avec les codes de module et de département** d'un serveur Nibras. Voir
  [Se connecter à un serveur Nibras](nibras-server.md).

## Installation { #install }

knotes desktop est un programme Python 3 qui utilise GTK 3. Il n'y a rien à compiler — il vous faut
seulement Python et les liaisons GTK (PyGObject).

=== "Debian / Ubuntu / Mint"

    ```bash
    sudo apt install python3-gi gir1.2-gtk-3.0
    ```

=== "Fedora"

    ```bash
    sudo dnf install python3-gobject gtk3
    ```

=== "Arch"

    ```bash
    sudo pacman -S python-gobject gtk3
    ```

=== "Windows"

    Windows ne fournit pas GTK, installez-le donc via [MSYS2](https://www.msys2.org/). Dans le
    shell **MSYS2 UCRT64** :

    ```bash
    pacman -S mingw-w64-ucrt-x86_64-python \
              mingw-w64-ucrt-x86_64-python-gobject \
              mingw-w64-ucrt-x86_64-gtk3 \
              mingw-w64-ucrt-x86_64-librsvg
    ```

    `librsvg` n'est nécessaire que pour l'icône de la fenêtre. (Installer `pygobject` avec `pip`
    fonctionne aussi, mais seulement si un environnement d'exécution GTK 3 est déjà dans votre
    `PATH` — MSYS2 est la voie la plus simple.)

Placez ensuite le dossier `desktop/` là où vous rangez habituellement vos programmes.

## Lancement { #run }

=== "Linux"

    ```bash
    cd desktop
    ./run.sh
    ```

    `run.sh` vérifie que GTK est disponible et vous indique quoi installer si ce n'est pas le cas.
    Vous pouvez aussi le lancer directement avec `python3 knotes.py`.

    **Entrée de menu et épinglage au panneau.** Exécutez `./install.sh` une fois. Il ajoute
    **knotes** au menu des applications ; lancez-le depuis ce menu, puis faites un clic droit sur
    son icône dans le panneau et choisissez **Pin** (Épingler). Relancez `install.sh` si vous
    déplacez le dossier.

=== "Windows"

    Double-cliquez sur `run.bat` (ou lancez-le depuis un shell). Il privilégie `pythonw.exe`, si
    bien qu'aucune fenêtre de console ne reste ouverte, et affiche une indication si Python ou GTK
    manque.

    Pour une entrée dans le menu Démarrer, faites un clic droit sur `run.bat` →
    **Create shortcut** (Créer un raccourci), donnez une icône au raccourci et déplacez-le dans
    `%APPDATA%\Microsoft\Windows\Start Menu\Programs`.

## Premier lancement { #first-run }

1. **Choisissez votre dossier de notes.** Par défaut, les notes vont dans `~/knotes`. Pour utiliser
   un autre dossier, appuyez sur **Ctrl+,** pour ouvrir **Settings** (Paramètres) et modifiez
   **Note folders** (Dossiers de notes), par exemple `A:~/Knotes` (sous Windows,
   `A:C:\Users\you\Knotes` — `~` y fonctionne aussi). Voir [Paramètres](settings.md#note-folders).
2. **Écrivez une note.** Tapez dans le champ en bas et appuyez sur **Ctrl+Enter**.
3. **Observez le résultat.** Une carte apparaît sur le tableau, et un fichier nommé par exemple
   `2026_06_10-143025~N~p2~F.md` apparaît dans votre dossier.

!!! note "Le tableau s'ouvre sur aujourd'hui"
    Par défaut, l'application démarre filtrée sur les notes **du jour** ; une nouvelle journée
    affiche donc **No notes entered today** (Aucune note saisie aujourd'hui). Cliquez sur
    **Clear all filters** (Effacer tous les filtres) en bas de la barre latérale pour tout voir,
    ou désactivez **Open filtered to today's notes** (Ouvrir filtré sur les notes du jour) dans
    les [Paramètres](settings.md).

Appuyez sur **F1** à tout moment pour afficher la liste des
[raccourcis clavier](keyboard-shortcuts.md).

## Où les données sont stockées { #where-things-are-stored }

| Quoi | Où |
|---|---|
| Vos notes | De simples fichiers `.md` dans chaque dossier de notes |
| Fichiers joints | Un dossier `<note name>.d/` à côté de chaque note — voir [Fichiers joints](attached-files.md) |
| Notes supprimées et fusionnées | Dossiers cachés `.deleted/` et `.merged/` dans le dossier de notes |
| Options d'étiquettes par dossier | Un fichier caché `.knotes.json` dans chaque dossier de notes |
| Paramètres de l'application | `config.json` à côté du programme |
| Vocabulaire de hashtags | `tag-bank.md` à côté du programme (ou un chemin de votre choix) |

## Différences sous Windows { #windows-differences }

Tout fonctionne de la même façon sous Windows — les noms de fichiers, les dossiers cachés de
gestion interne, le dossier de notes synchronisé. Seules quelques intégrations au système
diffèrent :

| | Linux | Windows |
|---|---|---|
| Ouvrir une note (**Enter**) | Votre éditeur configuré, sinon celui du système par défaut | Votre éditeur configuré, sinon l'application par défaut du fichier |
| Ouvrir le dossier de fichiers d'une note (**Ctrl+O**) | Nemo (dans un nouvel onglet), sinon votre gestionnaire de fichiers par défaut | L'Explorateur, une fenêtre à chaque fois |
| Épingler au panneau | Via `install.sh` | Épinglez le raccourci de `run.bat` |
