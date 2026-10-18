# Nibras

**Nibras** est un écosystème de connaissances personnel qui vit dans vos propres fichiers, et non dans le
cloud de quelqu'un d'autre. Les notes sont des fichiers Markdown en texte brut, rangés dans un seul dossier. Cinq applications lisent et écrivent dans ce même dossier,
chacune adaptée à un moment différent de la journée — et un serveur facultatif transforme les captures de votre choix
en enregistrements structurés (tâches, entrées de journal, objectifs, écrits, ressources…).

- **Un seul dossier de texte brut.** Chaque note est un fichier `.md` dont le *nom* porte les étiquettes. N'importe quel
  éditeur peut l'ouvrir ; n'importe quel outil de synchronisation de fichiers peut le copier.
- **Cinq clients, un seul format.** Téléphone, navigateur, bureau, terminal et lecteur audio s'accordent tous sur
  les mêmes noms de fichiers et les mêmes fichiers annexes, octet pour octet.
- **Fonctionne hors ligne.** Capturer, étiqueter, rechercher et poser des signets ne nécessitent jamais de réseau.
  Tout ce qui est destiné au serveur attend dans une file d'attente jusqu'à ce qu'il soit joignable.
- **Le serveur est facultatif.** Sans lui, vous disposez d'un système de notes rapide et portable. Avec lui, vous obtenez en plus
  des enregistrements, des rapports, un calendrier et un vocabulaire partagé de types et de catégories.

## Les applications { #the-apps }

| Application | Fonctionne sur | À quoi elle sert |
|-----|---------|----------------|
| [knotes pour Android](mobile/index.md) | Téléphone | Capture rapide, le lecteur multimédia avec signets, les widgets, les rappels et une vue hors ligne de vos enregistrements du serveur. |
| [Application web Nibras](web/index.md) | Navigateur + serveur | Le système d'enregistrements complet : tâches, journaux, objectifs, écrits, ressources, rapports — plus l'importation des notes depuis le dossier. |
| [knotes pour le bureau](desktop/index.md) | Linux / Windows (GTK) | Un tableau piloté au clavier pour capturer, trier, fusionner des notes et y joindre des fichiers. |
| [knotes pour le terminal](terminal/index.md) | N'importe quel terminal | Saisie de notes en une ligne et rapports rapides du serveur, sans quitter le shell. |
| [Lecteur audio](audio-player/index.md) | Linux (GTK) | Un lecteur audio et vidéo de bureau dont les signets sont partagés avec le téléphone. |

<div class="grid cards" markdown>

-   **Vous débutez ?**

    ---

    Lisez d'abord les [Concepts](concepts/index.md) — dix minutes pour comprendre comment le dossier, les noms de fichiers et
    la synchronisation s'articulent, et chaque application devient évidente.

-   **Vous voulez simplement prendre des notes ?**

    ---

    Installez [knotes pour Android](mobile/index.md) ou [knotes pour le bureau](desktop/index.md), indiquez-lui
    un dossier et commencez à écrire. Pas de compte, pas de serveur.

-   **Plusieurs appareils ?**

    ---

    Partagez le dossier entre eux avec un outil de synchronisation de fichiers — voir [Synchronisation](concepts/sync.md).

-   **Vous voulez des enregistrements et des rapports ?**

    ---

    Mettez en place l'[application web](web/index.md), puis laissez vos applications de capture
    [classer maintenant, importer plus tard](concepts/capture-then-classify.md).

</div>

## Par où commencer { #where-to-start }

1. [Notes en texte brut](concepts/plain-text-notes.md) — pourquoi tout est un fichier.
2. [Noms de fichiers et étiquettes](concepts/filenames-and-tags.md) — la convention unique que partagent toutes les applications.
3. [Capturer maintenant, classer plus tard](concepts/capture-then-classify.md) — comment une capture rapide devient un enregistrement.
4. Choisissez ensuite votre application dans le tableau ci-dessus.
