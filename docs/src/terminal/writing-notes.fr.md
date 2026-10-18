# Écrire des notes

Tout ce que vous tapez qui ne commence ni par `/` ni par `+` est enregistré comme note quand vous appuyez sur
**Enter**.

## La ligne de note { #the-note-line }

Une ligne de note peut commencer par une lettre de dossier facultative et un code d'étiquette facultatif de trois
caractères :

```
[<folder>] [<type><priority><category>] <text>
```

| Partie | Ressemble à | Signification |
|---|---|---|
| Dossier | une lettre, p. ex. `s` | Le dossier de notes configuré dans lequel enregistrer |
| Code d'étiquette | lettre + chiffre + lettre, p. ex. `t3d` | Type **T**, priorité **3**, catégorie **D** |
| Texte | tout le reste | La note elle-même |

Les lettres ne tiennent pas compte de la casse ; les codes sont enregistrés en majuscules. Par exemple :

| Vous tapez | Enregistré comme |
|---|---|
| `s t3d buy new filters` | Dossier **S**, type **T**, priorité **3**, catégorie **D**, texte « buy new filters » |
| `t3d buy new filters` | Dossier courant, type **T** / priorité **3** / catégorie **D** |
| `just a thought` | Dossier courant, étiquettes par défaut, texte « just a thought » |
| `b x3 note` | `b` n'est pas un dossier et `x3` n'est pas un code d'étiquette complet : **toute la ligne** est donc le texte, avec le dossier courant et les étiquettes par défaut |

La lettre de dossier n'est prise en compte que si elle est suivie d'un code d'étiquette valide. Si la ligne ne
commence pas par un code d'étiquette valide, rien n'est retiré — toute la ligne est conservée comme note, de sorte
que rien de ce que vous tapez n'est jamais perdu.

!!! note "Remarque"
    La priorité du code d'étiquette est un seul chiffre, de 0 à 9.

## Valeurs par défaut persistantes { #sticky-defaults }

L'application se souvient de ce que vous avez utilisé en dernier :

- **Dossier** — nommer un dossier en fait le dossier courant. Il le reste pour les notes suivantes et est mémorisé
  d'un redémarrage à l'autre. La barre d'état l'affiche.
- **Étiquettes** — un code d'étiquette devient la valeur par défaut des notes simples suivantes pendant cette
  session.

Vous pouvez donc définir le contexte une fois, puis simplement taper :

```
w t3d first point about the plan
second point
third point
```

Les trois notes vont dans le dossier **W** avec le type **T**, la priorité **3** et la catégorie **D**.

Chaque dossier peut avoir son propre serveur : changer de dossier change donc aussi le serveur que vérifie la barre
d'état. Voir [Configuration](configuration.md).

## Envoyer une note au serveur : le préfixe `+` { #send-a-note-to-the-server-the-prefix }

Commencez une ligne par `+` pour enregistrer la note **et** créer un enregistrement correspondant sur le serveur
Nibras :

```
+ s t3d call the supplier about the delivery
```

Ce qui se passe :

1. La note est toujours d'abord enregistrée dans le dossier, elle n'est donc jamais perdue.
2. Si vous êtes connecté, l'application demande au serveur du dossier d'importer ce fichier. Le serveur crée
   l'enregistrement et déplace le fichier dans le sous-dossier `imported/` du dossier. Le journal affiche
   `✓ pushed`, avec le module et le numéro d'enregistrement.
3. Si vous n'êtes pas encore connecté, la note est **mise en file d'attente** et envoyée automatiquement après
   `/login`.

!!! warning "Avant d'utiliser `+`"
    - Le serveur lit le fichier directement dans le dossier : le dossier de notes doit donc être accessible au
      serveur (généralement le dossier d'import du serveur).
    - La file d'attente n'existe que tant que l'application tourne. Si vous quittez avant de vous connecter, les
      notes en attente restent enregistrées dans le dossier mais ne sont pas envoyées — importez-les plus tard
      depuis le serveur.

Voir [Commandes serveur](server.md) pour la connexion, et
[Capturer, puis classer](../concepts/capture-then-classify.md) pour l'idée derrière l'envoi différé des notes.

## Touches pratiques pendant la saisie { #handy-keys-while-typing }

| Touche | Action |
|---|---|
| **Enter** | Enregistre la note (ou exécute la commande) |
| **↑** / **↓** | Rappelle les lignes précédentes de cette session |
| **Tab**, **→** ou **End** | Accepte la suggestion de commande en gris |
