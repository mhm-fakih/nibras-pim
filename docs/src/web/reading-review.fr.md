# Favoris, points clés et cartes mémoire

Trois pages vous aident à *relire* ce que vous avez rassemblé plutôt qu'à l'enrichir.

## Tous les favoris { #all-bookmarks }

**All bookmarks** (Tous les favoris ; barre latérale, ou **g b**) rassemble en une seule liste toutes les fiches
mises en favori de tous les modules, regroupées par module : départements, cours, planificateur, journal,
objectifs, tâches, ressources, notes et écrits. Cliquez sur l'une d'elles pour la lire dans le volet de droite ;
**Edit** (Modifier) vous amène à la fiche dans son propre module.

Les favoris comptent au-delà de cette page : ils déterminent ce qui apparaît sur le
[Kanban](tasks-kanban-sprints.md#kanban), ce qui peut rejoindre un [sprint](tasks-kanban-sprints.md#sprints),
ce qui entre dans la [table des matières des favoris](writings-fulltext.md#the-bookmarked-toc) et les
[flux RSS](rss.md), et ce que reçoit votre téléphone — voir [Synchronisation](../concepts/sync.md).

## Points clés { #highlights }

**Highlights** (Points clés) affiche les points essentiels de vos fiches, une fiche à la fois. Tout module doté
d'un champ de points clés l'alimente — notes, tâches, journaux, planificateurs, objectifs, écrits et ressources.
Chaque carte indique la fiche dont elle provient ; cliquez sur ce nom pour ouvrir la fiche.

La page est en direct : remplissez aujourd'hui les points clés d'une fiche et ils y figurent aujourd'hui même. Elle
est en lecture seule — pour modifier un point clé, modifiez la fiche, ou utilisez **Highlight** (Surligner) sur sa
[page de lecture](records.md#the-reading-page).

- **←** / **→** (ou **Page Up** / **Page Down**) permettent de passer d'une carte à l'autre ; **Home** et **End**
  sautent à la première et à la dernière. **↑** / **↓** font défiler une carte longue.
- Les boutons **−** et **+** (**Smaller text** / **Larger text**, Texte plus petit / plus grand) changent la taille
  de lecture ; votre choix est mémorisé.

## Cartes mémoire { #flash-cards }

**Flash cards** (Cartes mémoire) présente, un par un, des passages tirés de vos notes les plus longues, pour les
relire. Le paquet est partagé avec l'application mobile : une carte traitée à un endroit disparaît de l'autre.

Trois onglets trient le paquet : **Review** (À relire ; cartes non encore traitées), **Repeat** (À revoir) et
**Kept** (Conservées). Pour chaque carte, choisissez :

| Bouton | Touche | Signification |
|---|---|---|
| **Reviewed** (Relue) | **d** | Terminée — elle quitte le paquet. |
| **Repeat** (Revoir) | **r** | La montrer de nouveau plus tard (passe dans **Repeat**). |
| **Keep** (Conserver) | **k** | Mérite d'être gardée (passe dans **Kept**). |
| **Unset** (Annuler) | **u** | Remettre une carte « à revoir » ou « conservée » dans **Review**. |

Passez d'une carte à l'autre avec **←** / **→**. La note d'où provient un passage est liée au-dessus de celui-ci.
La taille du texte se règle comme sur la page Highlights.

!!! note "Remarque"
    L'onglet **Review** affiche un nombre limité de cartes non traitées à la fois ; d'autres apparaissent au fur et à
    mesure que vous avancez. **Repeat** et **Kept** affichent toujours tout.

## Vidéos { #videos }

**Videos** (groupe **Library** de la barre latérale) est la page Vidéos du téléphone sur le web,
disposée comme YouTube : chaque vidéo du dépôt 1 — le dossier que Syncthing porte au téléphone, donc
la même liste — de la plus récente à la plus ancienne, en cartes avec une image de la vidéo, sa
durée et une barre rouge montrant où vous en êtes dans ce navigateur. Sous chaque carte : le nom, le
dossier, la date d'ajout, la taille et le nombre de marque-pages. Les puces au-dessus filtrent par
dossier ; la zone de recherche filtre par nom.

Cliquez sur une carte (Ctrl-clic pour un nouvel onglet) pour la regarder : le lecteur, son nom et
ses détails, **Download** (télécharger), et ses **Bookmarks** (marque-pages) — les mêmes marques `.tmk`
que le lecteur du téléphone, donc celles faites d'un côté apparaissent de l'autre. Cliquez sur une
marque pour y sauter ; tapez ce qui s'y trouve (facultatif) puis **Bookmark here** (ou Entrée) pour
en ajouter une à l'instant courant. **Up next** (à suivre), sur le côté, liste le reste du même
dossier, puis les plus récentes ailleurs. Une vidéo reprend là où vous l'avez laissée. Les images et
durées sont faites sur le serveur à la première ouverture de la liste ; rechargez la page pour voir
celles qui manquaient.

## Coran { #quran }

**Quran** (Coran, code `A`, le premier des modules) est le module d'exégèse et de notes du Coran. Il reste
désactivé tant qu'il n'est pas activé dans [Settings → Modules](settings.md#modules) ; une fois activé, les fiches de
sourates (écrits de type *سورة*) et de versets (notes de type *آية*) dont il est tiré sont retirées des listes
Writings et Notes — choisir ce type dans le filtre de la liste les montre toujours.

La première fois, **Copy them into the Quran module** (les copier dans le module) copie les 114 sourates et leurs
versets. **⋮ → Copy again from the records** (recopier depuis les fiches) rafraîchit ensuite le texte et l'exégèse ;
les notes écrites sur cette page sont conservées.

- À gauche, les sourates dans l'ordre du **Mushaf** ou de la **Révélation**, avec leur nombre de versets (un point :
  certains ont des notes ou des notes liées). Tapez un nom ou un numéro pour en trouver une.
- Chaque verset affiche son **texte vocalisé** (la description courte de la fiche), son **exégèse** (*التفسير*, la
  description de la fiche), ses **notes** (*ملاحظات* ; une longue note s'affiche repliée) et ses **notes liées** :
  les notes liées à cette fiche de verset à l'ancienne (code d'entité `N` + identifiant de fiche), avec les notes
  qui leur sont liées à leur tour, imbriquées. Le titre d'une note liée l'ouvre dans Notes.
- Le crayon écrit ou modifie les notes d'un verset (Ctrl+Entrée enregistre) ; le signet le marque. Le signet à côté
  du nom de la sourate la met en favori (visible dans la liste). Une sourate peut avoir ses propres notes, au-dessus
  de son premier verset. Les notes ajoutées sur le téléphone s'ajoutent après une ligne datée.
- **Search** (Rechercher) parcourt les versets (sans tenir compte des voyelles), leur exégèse et leurs notes ; un
  résultat ouvre la sourate à ce verset.
- **Export** enregistre la sourate affichée en Markdown, HTML ou EPUB, avec son exégèse, ses notes et ses notes
  liées.

## Bibliothèque d'extraits { #excerpt-library }

**Excerpt library** (bibliothèque d'extraits ; barre latérale → **Library**) rassemble des échantillons de vos livres
numériques à lire et résumer : 2 à 3 pages prises au hasard dans le corps du fichier d'une ressource **ebk** — après les
premières pages, avant les notes et les index (entre environ 12 % et 85 % du livre).

- **Make more** (en faire d'autres) tire ce nombre de livres pas encore échantillonnés, au hasard, en arrière-plan. Un
  PDF ou un DjVu donne les pages elles-mêmes (**Pages**) et leur texte quand le fichier a une vraie couche de texte
  (**Text**) ; un EPUB donne l'équivalent de 2 à 3 pages de texte ; MOBI, AZW3 et CHM sont d'abord convertis. Un livre
  numérisé ne donne que les pages. Un livre sans fichier dans ses dossiers est noté et n'est plus essayé.
- Choisissez un échantillon, lisez-le, écrivez un **résumé d'une ligne** (Entrée enregistre) et des **détails**
  (Ctrl+Entrée enregistre). **Save & next** passe au suivant.
- **Promote to an Excerpt** (promouvoir en extrait) en fait un véritable extrait de son livre : le résumé comme titre,
  les détails comme description, le texte comme notes, les pages (« pp. 120–122 ») comme chapitres, et le PDF de
  l'échantillon dans son dossier. Chaque PDF d'échantillon est rangé dans le dépôt 1 sous
  `E/ebk/<id du livre>/` (nommé d'après ses pages, p. ex. `pp. 120-122.pdf`). **Set aside** (mettre de côté) en masque un ; **Another place** (un autre endroit)
  prend 2 à 3 pages ailleurs dans le même livre ; **Open the book** ouvre tout le fichier (un PDF à la page de
  l'échantillon).
- Les onglets montrent ce qui est **To summarize** (à résumer), **Summarized** (résumé), **Promoted** (promu) et
  **Set aside** (mis de côté).
