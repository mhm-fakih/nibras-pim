# Pages serveur

La bande **Depuis le serveur** du [rail](navigation.md) regroupe les pages qui affichent les fiches de votre serveur Nibras. Connectez-vous d'abord, comme décrit dans [Synchronisation Nibras](sync.md).

La bande n'apparaît que si **Utiliser un serveur Nibras** est activé dans [Paramètres](settings.md) → **Serveur** (c'est le cas par défaut). Désactivé, l'application ne se connecte nulle part et toutes les pages ci-dessous quittent le rail.

Chaque page affiche d'abord la copie enregistrée sur votre téléphone, puis s'actualise depuis le serveur quand c'est possible : elles fonctionnent donc toutes hors ligne. Pour tout remettre à jour d'un coup, touchez **Actualiser les pages du serveur** (la flèche circulaire en bas de la bande). Cela lance une synchronisation complète : les fiches de tous les types, les cartes mémoire, les surlignages et les contacts, ainsi que les types, départements et dispositions de dossiers du serveur.

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
- **Fichiers (N)** : ses pièces jointes. Touchez-en une pour l'ouvrir ; l'audio et la vidéo sont lus dans le [lecteur](player.md) de l'application,
- **Notes (N)** : les notes qui lui ont été ajoutées,
- **Ajouter une note à cette fiche…** avec un bouton **Envoyer la note**. La note est ajoutée à la fiche sur le serveur (**Note enregistrée**, ou enregistrée pour plus tard hors ligne),
- **Marquer comme non lu**, qui remet la fiche à l'état non lu. Les fiches que vous ouvrez sont marquées comme lues sur ce téléphone uniquement.

!!! note "Les fichiers viennent du dossier du téléphone"
    Les fichiers listés sont ceux qui se trouvent réellement dans le dossier de la fiche sur ce téléphone, sous le **Dossier des fichiers** défini dans Paramètres → **Serveur** (tenu à jour avec le serveur par un outil de synchronisation de dossiers comme Syncthing). Le dossier est retrouvé selon la disposition du serveur : le code du module, puis, si ce module est configuré ainsi sur le serveur, le type de la fiche et une tranche de centaines (id ÷ 100), puis l'id de la fiche. Par exemple `T/1234` pour une tâche, ou `R/<type>/12/1234` pour une ressource. L'application lit la disposition de chaque module sur le serveur en même temps que les types et les départements. Un fichier que l'outil de synchronisation n'a pas encore amené sur le téléphone n'est pas listé, et une fiche dont le dossier est absent n'affiche aucun fichier.

### Marquer une fiche comme terminée { #mark-a-record-complete }

Cochez la case au début d'une ligne. Vous verrez **Marqué comme terminé**, ou **Enregistré — sera marqué terminé une fois en ligne**.

### Surligner une ressource { #highlight-a-resource }

Sur une fiche **Ressources** qui possède son texte intégral, le texte s'affiche sous **Texte intégral — touchez une proposition pour la surligner**. Touchez une proposition pour la surligner (elle passe en gras), et le surlignage est envoyé au serveur. Touchez-la à nouveau pour le retirer. La taille du texte correspond au réglage **Taille du texte** dans [Paramètres](settings.md) → **Serveur**.

### Favori : l'étoile { #bookmark-the-star }

L'étoile de chaque ligne fonctionne de l'une de deux manières, au choix dans [Paramètres](settings.md) → **Serveur** → **Bouton étoile** :

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

## Coran { #quran }

**A** ouvre la bande du serveur lorsque le module Coran du serveur est activé (voir le
[Coran](../web/reading-review.md#quran) du web) ; le nombre est celui de ses sourates.

- Les sourates dans l'ordre du **Mushaf** ou de la **Révélation** ; tapez un nom ou un numéro pour en trouver une,
  et **Favoris** pour n'afficher que les sourates en favori ou contenant des versets en favori (★) — ✎ compte les
  versets annotés.
- Le signet au bout d'une ligne, ou à côté du nom d'une sourate ouverte, met la **sourate** en favori.
- Une sourate ouverte affiche chaque verset vocalisé, son **exégèse**, ses **notes** (une longue note repliée) et les
  **notes liées** à lui sur le serveur, imbriquées.
- Sur un verset, le signet marque **le verset**, et le bouton de note **ajoute une note**. Elle est ajoutée aux notes
  du verset sur le serveur, après une ligne datée — les notes existantes ne sont jamais remplacées.
- Tout fonctionne hors ligne. Chaque sourate ouverte est enregistrée sur le téléphone (le bouton nuage enregistre les
  114 d'un coup) ; les favoris et notes faits hors ligne s'affichent aussitôt (une note comme *En attente d'envoi*)
  et partent vers le serveur dès qu'il est joignable. Chaque note part avec sa propre référence : envoyée deux fois
  après une réponse perdue, elle n'est ajoutée qu'une fois.

## Bibliothèque d'extraits { #excerpt-library }

L'icône de livre après les passages surlignés ouvre la [Bibliothèque d'extraits](../web/reading-review.md#excerpt-library)
du web : 2 à 3 pages prises au hasard dans vos livres numériques, à lire et à résumer. La page a
besoin du serveur ; rien n'est gardé sur le téléphone.

- **La liste** : filtrez par *À résumer*, *Résumés*, *Promus*, *Mis de côté* ou *Tous*, et cherchez
  dans le titre, l'auteur, le résumé et le texte. Chaque ligne montre le livre, son résumé (ou le
  début du texte) et l'endroit des pages dans le livre.
- **En faire plus** : choisissez 10, 20 ou 50 et le serveur fait autant de nouveaux échantillons en
  arrière-plan, à partir de livres pris au hasard. La progression s'affiche sous le bouton. Aucune
  IA n'intervient : les pages sont simplement découpées dans les fichiers des livres.
- **Un échantillon** : écrivez un **Résumé en une ligne** (la touche Terminé du clavier
  l'enregistre) et des **Détails** facultatifs, puis **Enregistrer** ou **Enregistrer et suivant**.
  Lisez-le sous **Texte** (quand le livre a un vrai texte ; les paragraphes arabes sont alignés à
  droite) ou **Pages** (les pages en images). **Ouvrir le livre** ouvre le fichier entier ; **Un
  autre endroit** prend 2 à 3 autres pages du même livre ; **Mettre de côté** le retire de la liste
  (**Reprendre** le ramène) ; **Promouvoir en Extrait** en fait un Extrait de son livre une fois un
  résumé enregistré.
- **Lire les pages** : **Lire en plein écran** ouvre le PDF de l'échantillon dans le lecteur PDF de
  l'application (zoom, stylo, surligneur, enregistrés dans le fichier) ; **Ouvrir avec…** le confie
  à une autre application (PocketBook, un navigateur…) ; toucher une page sous **Pages** l'ouvre aussi
  en plein écran. Le PDF est téléchargé une fois dans `Nibras/excerpt-library/<id du livre>/`, et vos
  annotations restent avec lui ; elles ne sont pas renvoyées au serveur.
- **Couvertures** : chaque échantillon montre la couverture de son livre — la sienne, sinon une
  faite à partir de sa première page (ou la couverture contenue dans un EPUB/MOBI).
