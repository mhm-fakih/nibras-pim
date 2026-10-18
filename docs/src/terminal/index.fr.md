# knotes terminal

**knotes terminal** est une application de prise de notes qui fonctionne dans votre terminal. Elle ressemble à une
messagerie : un journal de ce qui s'est passé en haut, une barre de commande d'une ligne en bas. Tapez une note et
appuyez sur **Enter** — elle est enregistrée sous forme de fichier texte brut, avec les mêmes noms de fichiers que
[knotes desktop](../desktop/index.md) et l'[application mobile](../mobile/index.md), si bien que toutes peuvent
partager un même dossier.

Elle peut aussi communiquer avec un serveur Nibras : vous connecter, lister vos tâches, objectifs ou journaux les plus
récents, et envoyer une note directement au serveur sous forme d'enregistrement.

!!! tip "Comment les notes sont stockées"
    Les notes sont de simples fichiers Markdown dont le nom porte les étiquettes. Voir
    [Notes en texte brut](../concepts/plain-text-notes.md) et
    [Noms de fichiers et étiquettes](../concepts/filenames-and-tags.md).

## Ce que vous trouverez ici { #what-youll-find-here }

- [Écrire des notes](writing-notes.md) — le format d'une ligne de note, les valeurs par défaut persistantes et le préfixe `+`.
- [Commandes](commands.md) — chaque `/commande` et chaque touche.
- [Commandes serveur](server.md) — se connecter à un serveur Nibras et en lister les enregistrements.
- [Configuration](configuration.md) — dossiers de notes, serveurs, valeurs par défaut et variables d'environnement.

## Installer et lancer { #install-and-run }

Il vous faut Python 3. L'application utilise quelques paquets Python (Textual pour l'interface, httpx pour le
serveur, plus deux aides facultatives pour l'arabe) ; `run.sh` les installe pour vous dans un environnement privé.

L'application terminal réutilise le code de stockage des notes de knotes desktop : gardez donc le dossier `terminal/`
à côté du dossier `desktop/`.

=== "Linux / macOS"

    ```bash
    cd terminal
    ./run.sh
    ```

    Le premier lancement crée un dossier `.venv` et installe les paquets ; les lancements suivants démarrent
    immédiatement.

=== "À la main"

    `run.sh` est un script shell. Ailleurs, effectuez vous-même les mêmes étapes :

    ```bash
    cd terminal
    python3 -m venv .venv
    .venv/bin/pip install -r requirements.txt
    .venv/bin/python knotes_tui.py
    ```

    (Sous Windows, les programmes du venv se trouvent dans `.venv\Scripts\` au lieu de `.venv/bin/`.)

!!! note "La lancer seule"
    Si vous copiez `terminal/` quelque part sans `desktop/`, copiez aussi `desktop/notes.py` à côté de
    `knotes_tui.py`. Sans ce fichier, l'application s'arrête avec un message qui le signale.

## Premier lancement { #first-run }

Au premier démarrage, l'application crée `config.json` à côté du programme. Si knotes desktop est installé à côté,
ses dossiers de notes et ses étiquettes par défaut sont recopiés, et vous reprenez là où l'application de bureau
s'était arrêtée. Sinon, vous obtenez un seul dossier, **A**, dans `~/knotes`.

L'écran affiche :

- **Dashboard** (tableau de bord, en haut) — vos dossiers de notes : code, chemin, serveur et nombre de notes. **★**
  marque le dossier courant.
- **Log** (journal, au milieu) — les notes enregistrées, la sortie des commandes et les messages.
- **Command bar** (barre de commande, en bas) — là où vous tapez.
- **Status bar** (barre d'état, en dessous) — le serveur courant avec un point **vert** quand il est joignable et
  **rouge** quand il ne l'est pas, le dossier courant et les étiquettes par défaut, depuis combien de temps
  l'application tourne, et combien de notes vous avez enregistrées pendant cette session.

Essayez : tapez `hello from the terminal` et appuyez sur **Enter**. Le journal confirme `✓ saved` avec le nom du
fichier.

Pour quitter, tapez `/quit`. (**Ctrl+C** et **Ctrl+Q** sont volontairement ignorés, afin qu'une touche pressée par
mégarde ne ferme pas l'application.)

## Texte arabe { #arabic-text }

Les terminaux modernes mettent en forme et ordonnent correctement l'arabe d'eux-mêmes ; l'application affiche donc
le texte tel quel. Si le vôtre affiche des lettres arabes détachées ou à l'envers, lancez l'application avec
`NIBRAS_RESHAPE=1` :

```bash
NIBRAS_RESHAPE=1 ./run.sh
```

Voir [Langues](../concepts/languages.md).
