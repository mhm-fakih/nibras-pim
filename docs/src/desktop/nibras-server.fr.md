# Se connecter à un serveur Nibras

Nibras Notes fonctionne entièrement hors ligne. Le connecter à un serveur Nibras est facultatif,
et cela sert à deux choses : garder vos codes de **type** et de **catégorie** alignés sur ceux du
serveur, afin que les notes que vous saisissez maintenant puissent être importées plus tard au bon
endroit — et [importer une note](#import-a-note-into-the-server) dans le serveur sous forme
d'enregistrement quand vous le demandez.

| Sur le poste de travail | Provient, côté serveur, des |
|---|---|
| Types | Modules activés qui ont un code, dans l'ordre du serveur |
| Catégories | Départements mis en favoris |

C'est le flux « saisir maintenant, classer plus tard » — voir
[Saisir, puis classer](../concepts/capture-then-classify.md) et [Synchronisation](../concepts/sync.md).

!!! note "Rien n'est envoyé de lui-même"
    La synchronisation ne fait que *lire* des codes. Vos notes restent des fichiers dans votre
    dossier jusqu'à ce que vous en importiez une vous-même — d'ici, par la page d'import du serveur,
    le préfixe `+` de l'[application en terminal](../terminal/index.md), ou
    l'[application mobile](../mobile/index.md).

## Configuration { #set-it-up }

1. Ouvrez **Settings** (Paramètres, **Ctrl+,**).
2. Sous **Nibras backend — default** (backend Nibras — par défaut), renseignez :
    - **Backend URL** (URL du backend) — l'adresse de base du serveur, par exemple
      `https://nibras.example.com`
    - **Backend username** (nom d'utilisateur du backend)
    - **Backend password** (mot de passe du backend)
3. Cliquez sur **Save** (Enregistrer).

L'application se connecte et récupère les codes immédiatement. Les rangées de pastilles **Type** et
**Cat** sont remplacées par les codes du serveur, et ces codes sont enregistrés dans le
`.knotes.json` du dossier.

## Quand la synchronisation a lieu { #when-it-syncs }

- Au démarrage de l'application.
- Quand vous passez à un autre dossier de notes.
- Quand vous enregistrez les paramètres.

Chaque synchronisation s'exécute en arrière-plan et abandonne rapidement. Si le serveur est
injoignable, si l'URL est vide ou si la connexion échoue, rien ne change — vous gardez les codes
que vous avez déjà et continuez à écrire. Après une synchronisation réussie, les codes sont mis en
cache et restent donc disponibles hors ligne.

!!! warning "Les codes du serveur remplacent les vôtres"
    Tant qu'un dossier est connecté, ses **Types** et **Categories** (Catégories) proviennent du
    serveur à chaque synchronisation. Les modifications que vous apportez à ces listes dans les
    paramètres seront écrasées à la synchronisation suivante. Laissez l'URL vide si vous préférez
    gérer les codes à la main.

## Importer une note dans le serveur { #import-a-note-into-the-server }

Quand une note est prête à devenir un enregistrement sur le serveur :

1. Sélectionnez sa carte.
2. Appuyez sur **Ctrl+I**, ou cliquez sur le bouton d'import (la flèche) dans la ligne d'actions de
   la carte.
3. Confirmez **Import this note into the backend as a …?** (importer cette note dans le backend en
   tant que … ?) — la boîte de dialogue nomme le type d'enregistrement (tiré du code de type de la
   note), affiche le titre de la note et l'adresse du serveur.

Le serveur crée l'enregistrement, et le fichier de la note est déplacé dans l'archive `imported/` du
dossier : la carte quitte donc le tableau. La barre d'état le confirme avec **Imported →**, le module
et le numéro du nouvel enregistrement. Ses fichiers joints sont copiés vers l'enregistrement comme
pièces jointes.

- Un fichier isolé qui a encore besoin de métadonnées (**needs metadata**) ne peut pas être
  importé — le serveur choisit le type d'enregistrement d'après le code de type du nom de fichier ;
  réétiquetez-le d'abord.
- Sans URL de serveur, l'application affiche **No backend configured** (aucun backend configuré).
  Si l'import échoue, une boîte de dialogue donne la raison indiquée par le serveur.
- Le serveur lit le fichier directement dans le dossier ; il doit donc pouvoir voir ce même
  dossier — en pratique, un serveur qui tourne sur la même machine.

## Un serveur différent par dossier { #a-different-server-per-folder }

Chaque dossier de notes peut utiliser son propre serveur. Dans **Settings**, la section
**Tag options for folder “X”** (options d'étiquettes du dossier « X ») contient :

- **This folder's backend URL** (URL du backend de ce dossier)
- **This folder's backend username** (nom d'utilisateur du backend de ce dossier)
- **This folder's backend password** (mot de passe du backend de ce dossier)

Ces valeurs ne s'appliquent qu'au dossier nommé dans l'en-tête, et sont stockées dans le
`.knotes.json` de ce dossier. Les champs vides se rabattent sur le backend par défaut situé
au-dessus.

!!! warning "Les mots de passe sont stockés en clair"
    Le mot de passe du backend par défaut est enregistré dans `config.json` à côté du programme, et
    le mot de passe propre à un dossier dans le `.knotes.json` de ce dossier. Si vous synchronisez
    des dossiers de notes vers d'autres appareils, le mot de passe du dossier voyage avec eux.
    Utilisez un compte qui ne dispose que des accès nécessaires.
