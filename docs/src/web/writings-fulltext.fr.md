# Écrits, fichiers d'extraction et publication

L'application web est idéale pour organiser, mais pour les longs textes vous préférerez peut-être
votre propre éditeur. Nibras vous permet d'**extraire** (check out) le texte d'une fiche dans un
fichier, de le modifier n'importe où, puis de rapatrier les modifications — en toute sécurité, avec
une revue mot à mot. Cette page présente aussi la **table des matières** générale de vos fiches en
favori et le générateur de **Static website** (site web statique).

## Écrits { #writings }

**Writings** (Écrits) est le module des textes longs : essais, articles, chapitres. En plus de
**Summary** (Résumé) et **Body** (Corps), un écrit possède une **Short description** (Description
courte), un **Slug** (un nom adapté au web), une **Source**, un **Status** (Statut), un **Type** et une
date **Born on** (Né le). Tout ce qui est décrit dans [Modules et fiches](records.md) s'applique.

## Modifier le texte intégral dans votre éditeur { #edit-full-text-in-your-editor }

Le volet de détail de chaque fiche comporte une section **Edit full text in your editor** (Modifier
le texte intégral dans votre éditeur).

1. Cliquez sur **Export to file** (Exporter vers un fichier). Les champs de texte longs de la fiche
   sont écrits dans un seul **fichier d'extraction** dans le dossier de la fiche du dépôt 1, nommé
   d'après le code du module et l'ID (par exemple `W18.md` pour l'écrit 18). L'application affiche
   l'emplacement du fichier — et, si votre administrateur l'a activé, l'ouvre.
2. Modifiez le fichier dans n'importe quel éditeur et enregistrez-le.
3. De retour dans l'application, cliquez sur **Review & sync** (Revoir et synchroniser). Une boîte de
   dialogue montre chaque champ modifié, avec les ajouts en vert et les suppressions barrées en rouge.
4. Cliquez sur **Apply** (Appliquer) pour écrire les modifications dans la fiche, ou sur **Cancel**
   (Annuler) pour la laisser telle quelle.

Les modifications appliquées sont enregistrées comme toute autre modification ; elles apparaissent
donc dans l'**History** (Historique) de la fiche.

Pour les ressources, l'extraction comprend le **Full text** (Texte intégral) et les **Highlights**
(Surlignages) ; pour les autres modules, le titre ou le résumé, la description et les notes.

!!! tip "La mise en favori peut extraire pour vous"
    Dans [Settings → On bookmarking](settings.md#on-bookmarking), un administrateur peut faire en
    sorte que la mise en favori d'une fiche écrive automatiquement son texte d'extraction, et
    éventuellement crée un lien vers son dossier dans un **checkout folder** (dossier d'extraction).

## Synchronisation en attente { #pending-sync }

Inutile d'ouvrir chaque fiche pour rapatrier les modifications. **Import files → Pending sync —
edited record files** (Importer des fichiers → Synchronisation en attente — fichiers de fiches
modifiés) liste chaque fiche dont le fichier d'extraction diffère de la base de données, avec les
différences affichées champ par champ.

- **Sync** (Synchroniser) applique une fiche.
- **Sync all** (Tout synchroniser) applique toutes les fiches listées.
- **Rescan** (Réanalyser) vérifie à nouveau.

Quand tout correspond, vous verrez **Nothing to sync — all exported files match the database.**
(Rien à synchroniser — tous les fichiers exportés correspondent à la base de données.)

## La table des matières des favoris { #the-bookmarked-toc }

Sur le même panneau, **Bookmarked TOC** (Table des matières des favoris) extrait **toutes les fiches
en favori** — tâches, plans, objectifs, journaux, notes, écrits et ressources — et reconstruit un
fichier d'index général, `TOC.md`, à la racine du dépôt 1. Il est organisé par département → cours →
fiche, prêt à être ouvert comme projet dans une application d'écriture Markdown. Les modifications
que vous apportez aux fichiers extraits reviennent via **Pending sync**.

!!! note "Rien n'est écrasé"
    Si un fichier d'extraction existe déjà avec un contenu *différent* (peut-être des modifications
    non synchronisées), il n'est jamais remplacé : l'ancienne copie est conservée à côté avec un
    horodatage dans son nom, et l'application la signale comme conflit pour que vous puissiez
    comparer et fusionner à la main.

## Site web statique { #static-website }

**Static website** (barre latérale) publie les fiches d'une recherche enregistrée sous la forme d'un
site web autonome sur disque, prêt à être téléversé chez n'importe quel hébergeur.

1. Créez d'abord une recherche enregistrée qui sélectionne ce qu'il faut publier — par exemple une
   recherche enregistrée **Search form** (Formulaire de recherche) ou **HQL** sur les Écrits qui
   retient les fiches étiquetées `published` (voir
   [Recherches enregistrées](search.md#saved-searches)).
2. Sur la page Static website, définissez l'**Output folder** (Dossier de sortie — un dossier sur le
   serveur ; mémorisé pour la prochaine fois).
3. Cliquez sur **Generate** (Générer) sur la recherche enregistrée que vous voulez publier.

Le site comporte un index de cartes (description courte et vignette de couverture) et une page
complète par fiche. Chaque image — couvertures, pièces jointes intégrées et images externes — est
copiée dans un dossier `media/`, de sorte que le site n'a aucune dépendance extérieure. Le résultat
vous indique combien de pages et de fichiers média ont été écrits et où ouvrir l'index.
