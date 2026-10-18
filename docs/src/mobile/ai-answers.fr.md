# Réponses IA

**Réponses IA** (l'icône du robot, juste sous Notes dans le rail) est une seconde page de notes, en **lecture seule**. Elle affiche un dossier de notes distinct qui arrive sur le téléphone depuis ailleurs, généralement des réponses préparées sur votre ordinateur et transférées par un outil de synchronisation de dossiers comme Syncthing.

## Emplacement du dossier { #where-the-folder-is }

Par défaut, l'application cherche un dossier portant le nom de votre dossier de notes suivi de `-ai`. Si votre dossier de notes est `/storage/emulated/0/Notes`, le dossier des réponses IA est `/storage/emulated/0/Notes-ai`.

Pour utiliser un autre dossier, définissez **Dossier des réponses IA** dans les [Paramètres](settings.md). Laissez le champ vide pour utiliser la valeur par défaut.

L'application ne crée jamais ce dossier elle-même. S'il n'existe pas encore, la page indique **Pas encore de dossier de réponses IA sur ce téléphone.** et affiche le chemin attendu. Si le dossier existe mais ne contient rien, elle indique **Le dossier existe mais il est vide.**

## Ce que vous pouvez faire { #what-you-can-do }

- **Parcourir** les réponses. Les cartes ressemblent à celles de la page Notes, et les fichiers joints s'ouvrent ou se lisent de la même manière.
- Les **rechercher** avec **Rechercher dans ces réponses**, et les affiner avec le même [panneau de filtres](search-filters.md#the-filter-panel) que la page Notes.
- En **lire** une en la touchant. L'en-tête indique **Réponse IA**. Le Markdown est mis en forme.

Ce que vous ne pouvez pas faire ici, c'est écrire : pas de zone pour de nouvelles notes, pas de **Modifier**, pas de suppression, pas d'envoi vers Nibras ni de modification des étiquettes. Le dossier est alimenté par la synchronisation, et toute modification faite ici serait écrasée.

!!! note "Dates"
    Chaque réponse est datée par l'heure figurant dans son nom de fichier, c'est-à-dire le moment où elle a été écrite, et non par le moment où elle est arrivée sur votre téléphone. Les outils de synchronisation modifient la date de modification d'un fichier lorsqu'ils le copient, ce qui daterait sinon chaque réponse du moment de son arrivée.

Si la page affiche **Aucune réponse ne correspond.**, les réponses sont bien là, mais votre recherche ou vos filtres les masquent toutes. Effacez-les pour tout voir.
