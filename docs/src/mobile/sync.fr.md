# Synchronisation Nibras

knotes fonctionne entièrement de manière autonome. Si vous faites aussi tourner un **serveur Nibras**, vous y connecter ajoute vos tâches, plans, journal, objectifs, écrits et ressources sur le téléphone. Vous pouvez les lire et y travailler hors ligne, et vos modifications repartent vers le serveur au retour de la connexion. Voir [Fonctionnement de la synchronisation](../concepts/sync.md) pour une vue d'ensemble.

!!! note "Les notes de votre téléphone restent des fichiers"
    La connexion n'envoie **pas** les notes de votre téléphone. Elles restent des fichiers dans votre dossier de notes. Pour les synchroniser avec un ordinateur, utilisez un outil de synchronisation de dossiers comme Syncthing. Une note donnée peut être envoyée volontairement au serveur avec [Envoyer vers Nibras](#push-a-note-to-nibras).

## Se connecter { #connect }

Vous pouvez saisir l'adresse du serveur dans la visite de configuration (**Se connecter à Nibras → Adresse du serveur**), ou dans les [Paramètres](settings.md) → **Serveur Nibras** :

| Champ | Quoi saisir |
|---|---|
| **Protocole** | `http` ou `https`, selon ce qu'utilise votre serveur. |
| **IP / hôte** | L'adresse du serveur sur votre réseau, ou son nom d'hôte. |
| **Port** | Le port du serveur. |
| **Contexte** | Le chemin sous lequel l'application est servie, le cas échéant. Sinon, laissez **(aucun)**. |
| **Nom d’utilisateur**, **Mot de passe** | Votre compte Nibras. Utilisé pour se connecter lors de la synchronisation. |
| **Dossier des fichiers** | L'emplacement des pièces jointes des fiches sur le téléphone, par exemple un dossier `Nibras` tenu à jour par un outil de synchronisation de dossiers. L'application ouvre les fichiers joints depuis cet emplacement ; elle ne les télécharge pas. |

**URL complète**, sous les champs, affiche l'adresse ainsi construite pour que vous puissiez la vérifier. Touchez **Enregistrer** en haut des Paramètres.

## Ce qui descend { #what-comes-down }

Le téléphone reçoit les fiches que vous avez **mises en favori** sur le serveur, pour chacun des sept types. Elles sont enregistrées sur le téléphone, de sorte que les [pages serveur](server-pages.md) s'ouvrent instantanément et fonctionnent hors ligne. Lorsque vous êtes hors ligne, elles affichent ce qui a été synchronisé en dernier.

Pour faire descendre une fiche que vous n'avez pas mise en favori, trouvez-la avec [Rechercher sur le serveur](server-pages.md#search-server) et marquez-la.

knotes récupère aussi sa liste de **types** et de **catégories** de notes depuis le serveur, au démarrage de l'application et lorsque vous touchez **Actualiser les notes + synchroniser**, afin que le téléphone classe les notes sous les mêmes codes que le serveur.

## Ce qui monte { #what-goes-up }

Les modifications que vous faites sur le téléphone y sont d'abord enregistrées, puis envoyées dès que le serveur est joignable :

- les notes que vous ajoutez à une fiche,
- les surlignages que vous faites dans une ressource,
- les fiches que vous marquez comme terminées,
- les retraits des favoris (lorsque l'étoile est réglée sur **Retirer des favoris dans Nibras**),
- les entrées d'agenda que vous ajoutez ou déplacez,
- les notes que vous [envoyez vers Nibras](#push-a-note-to-nibras),
- les décisions sur les cartes mémoire.

Hors ligne, vous verrez des messages comme **Enregistré — sera synchronisé une fois en ligne**. Tout ce qui attend est envoyé automatiquement dès le retour du réseau. Vous n'avez rien à faire.

## Synchronisation en arrière-plan { #background-sync }

Dans les [Paramètres](settings.md) → **Synchronisation en arrière-plan** :

| Réglage | Par défaut | Signification |
|---|---|---|
| **Synchroniser avec Nibras** | Activé | Interroge régulièrement le serveur et récupère les fiches récentes lorsque vous êtes en ligne. |
| **Vérifier toutes les (s)** | 30 | Une vérification légère en ligne / hors ligne. |
| **Synchroniser toutes les (s)** | 300 | Une synchronisation complète : récupération des fiches et envoi de ce qui attend. Ne s'exécute qu'en ligne. |

L'état actuel apparaît sur la page Notes sous le titre (**synchronisé il y a 2 min**, **hors ligne · dernière synchro il y a 1 h**) et dans la [notification d'état](alarms.md#the-status-notification).

Pour synchroniser **tout de suite**, touchez **Actualiser les notes + synchroniser** sur la page Notes, ou touchez le titre d'un widget serveur. Lorsqu'une synchronisation que vous avez lancée se termine, vous verrez **Synchronisé**, **Hors ligne — synchronisation impossible**, **Échec de la connexion — vérifiez le nom d’utilisateur et le mot de passe dans les paramètres** ou **Échec de la synchronisation**.

## Envoyer une note vers Nibras { #push-a-note-to-nibras }

N'importe quelle note de votre téléphone peut être créée comme fiche sur le serveur :

1. Sur la carte de la note, touchez **Plus** (⋮) → **Envoyer vers Nibras**.
2. La fiche est créée avec le type, la priorité et la catégorie de la note tirés de son nom de fichier, et elle est enregistrée en favori sur le serveur : elle redescend donc lors de la synchronisation suivante.

Pendant qu'elle attend d'être envoyée, l'élément de menu indique **En attente — sera synchronisée** et la carte affiche une icône de nuage d'envoi (**En attente pour Nibras**). Une fois arrivée, l'icône devient un nuage coché (**Envoyée à Nibras**) et le menu propose **Renvoyer vers Nibras**. L'envoi fonctionne hors ligne : la note attend et est envoyée au retour du réseau.
