# Pages serveur

La bande **Depuis le serveur** du [rail](navigation.md) regroupe les pages qui affichent les fiches de votre serveur Nibras. Connectez-vous d'abord, comme décrit dans [Synchronisation Nibras](sync.md).

Chaque page affiche d'abord la copie enregistrée sur votre téléphone, puis s'actualise depuis le serveur quand c'est possible : elles fonctionnent donc toutes hors ligne. Pour les recharger toutes en une fois depuis le serveur, touchez **Actualiser les pages du serveur** (la flèche circulaire en bas de la bande).

## Pages de fiches : T, P, J, G, N, W, R { #record-pages-t-p-j-g-n-w-r }

Une page par type de fiche : **Tâches**, **Plans**, **Journal**, **Objectifs**, **Notes**, **Écrits** et **Ressources**. Le nombre à côté de chaque lettre dans le rail indique combien de fiches de ce type se trouvent sur le téléphone.

### La liste { #the-list }

- Le haut affiche l'état de la connexion (**En ligne**, **Hors ligne** ou **Connexion**), **Synchronisation…** pendant le travail, et un bouton **Actualiser**.
- **Rechercher…** réduit la liste au fil de la saisie.
- Les fiches marquées d'une étoile viennent en premier, sous **★ Favoris**. Les autres suivent.
- Sur certaines pages, les autres fiches sont réparties en groupes repliables : les Ressources par type de ressource, les Tâches par contexte, les Plans et le Journal par sous-type. **Tout déplier** / **Tout replier** les ouvre ou les ferme d'un coup.
- Sur les Ressources, les Notes et les Écrits, une fine barre sous chaque ligne indique approximativement la durée de lecture. Une ligne pleine correspond à une dizaine de pages. Elle devient orange au-delà de 15 pages et rouge au-delà de 20.
- De petites icônes indiquent qu'une fiche **Contient une note** ou **Contient des fichiers**.
- Les flèches haut et bas (**Aller en haut** / **Aller en bas**) permettent de parcourir rapidement une longue liste ou un long article.

Si vous êtes hors ligne et que rien n'a encore été enregistré pour un type, la page indique **Hors ligne — aucune fiche en cache pour ce type**.

### Ouvrir une fiche { #open-a-record }

Touchez une ligne pour la déplier. Une seule fiche est ouverte à la fois. Une fiche ouverte affiche :

- sa description complète,
- **Fichiers (N)** : ses pièces jointes. Touchez-en une pour l'ouvrir depuis votre **Dossier des fichiers** (défini dans les Paramètres). L'audio et la vidéo sont lus dans le [lecteur](player.md) de l'application,
- **Notes (N)** : les notes qui lui ont été ajoutées,
- **Ajouter une note à cette fiche…** avec un bouton **Envoyer la note**. La note est ajoutée à la fiche sur le serveur (**Note enregistrée**, ou enregistrée pour plus tard hors ligne),
- **Marquer comme non lu**, qui remet la fiche à l'état non lu. Les fiches que vous ouvrez sont marquées comme lues sur ce téléphone uniquement.

### Marquer une fiche comme terminée { #mark-a-record-complete }

Cochez la case au début d'une ligne. Vous verrez **Marqué comme terminé**, ou **Enregistré — sera marqué terminé une fois en ligne**.

### Surligner une ressource { #highlight-a-resource }

Sur une fiche **Ressources** qui possède son texte intégral, le texte s'affiche sous **Texte intégral — touchez une proposition pour la surligner**. Touchez une proposition pour la surligner (elle passe en gras), et le surlignage est envoyé au serveur. Touchez-la à nouveau pour le retirer. La taille du texte correspond au réglage **Taille du texte** dans les [Paramètres](settings.md).

### Favori : l'étoile { #bookmark-the-star }

L'étoile de chaque ligne fonctionne de l'une de deux manières, au choix dans les [Paramètres](settings.md) → **Bouton étoile** :

| Mode | Effet de l'étoile |
|---|---|
| **Favori sur cet appareil** (par défaut) | Ajoute la fiche à une sélection privée sur le téléphone. Rien n'est envoyé au serveur. Les fiches étoilées passent en haut de la liste et apparaissent dans le [widget Fiches en favori](widgets.md#bookmarked-records). |
| **Retirer des favoris dans Nibras** | Chaque fiche présente sur le téléphone est en favori sur le serveur (c'est pour cela qu'elle est là) : l'étoile est donc toujours pleine. La toucher retire la fiche de vos favoris sur le serveur, immédiatement si vous êtes en ligne, sinon lors de la synchronisation suivante. Jusque-là, la ligne reste affichée avec la mention **Retiré des favoris — quittera cette liste à la prochaine synchronisation. Touchez l’étoile pour annuler.** |

## Rechercher sur le serveur { #search-server }

**Rechercher sur le serveur** (le globe avec une loupe) interroge **le serveur lui-même**, y compris les fiches qui n'ont jamais été synchronisées sur le téléphone.

1. Saisissez votre recherche dans **Rechercher sur le serveur Nibras** et touchez **Rechercher**.
2. Affinez les résultats par type avec les puces (**Tout**, ou un code de type).
3. Touchez le drapeau d'un résultat (**Marquer pour synchroniser sur ce téléphone**) pour le mettre en favori sur le serveur. Il arrive alors sur le téléphone lors de la synchronisation suivante (**Marqué — sera synchronisé sur ce téléphone**).

La recherche nécessite une connexion. En cas d'échec, le message en donne la raison : identifiants incorrects (401), compte sans autorisation (403), serveur trop ancien pour proposer la recherche (404), ou serveur injoignable.

## Cartes mémoire { #flash-cards }

Des passages tirés de vos fiches les plus longues, affichés **un par un**, en plein écran. Faites-les défiler comme des images. Un passage trop long pour l'écran est réduit pour tenir, vous n'avez donc jamais besoin de faire défiler.

Trois onglets, chacun avec un compteur :

| Onglet | Contenu |
|---|---|
| **À revoir** | Les passages en attente d'une décision. Quarante sont affichés à la fois, et la page se remplit à mesure que vous avancez. |
| **À répéter** | Les passages que vous voulez revoir. |
| **Gardées** | Les passages que vous avez gardés. |

Sous chaque passage, choisissez **Garder**, **Répéter** ou **Revu**. **Annuler** défait une décision. Le compteur indique où vous en êtes et combien il en reste. Utilisez − et + pour **Texte plus petit** / **Texte plus grand**, et le bouton de copie (**Copier ce passage**) pour le copier.

Les décisions fonctionnent aussi hors ligne. La carte se déplace immédiatement, et le compteur indique l'âge de votre copie et le nombre de décisions en attente (**copie enregistrée, il y a 2 h · 3 décisions à synchroniser**).

## Passages surlignés { #highlights }

Les points clés consignés dans vos fiches (le champ de surlignages du serveur pour les notes, tâches, journal, plans, objectifs, écrits et ressources), **une fiche à la fois**. Balayez pour passer de l'une à l'autre. Chaque carte porte le nom de la fiche dont elle provient. Cette page est en lecture seule : elle affiche simplement ce que contiennent actuellement les fiches. **Copier ces surlignages** copie la carte ; − et + changent la taille du texte.

## Contacts { #contacts }

Les personnes que vous avez mises en favori sur le serveur, chacune suivie des tâches encore en cours avec elle, avec leurs échéances (**échéance …**). L'en-tête affiche **N en favori · M en cours**. Un contact sans rien en cours apparaît quand même, pour que vous voyiez que rien n'est en suspens. Si personne n'est en favori, la page indique **Aucun contact en favori. Ajoutez en favori les personnes avec qui vous travaillez.**
