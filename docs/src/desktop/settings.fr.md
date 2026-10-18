# Paramètres

Ouvrez **Settings** (Paramètres) avec **Ctrl+,** ou le bouton en forme d'engrenage de la barre
d'outils. Cliquez sur **Save** (Enregistrer) pour appliquer vos modifications, ou sur **Cancel**
(Annuler) pour les abandonner.

La boîte de dialogue comporte trois parties : les paramètres globaux de l'application, le backend
Nibras par défaut, et les options d'étiquettes du dossier qui était actif à son ouverture.

## Paramètres globaux de l'application { #app-wide-settings }

### Dossiers de notes { #note-folders }

**Note folders (code:path per line; * = active)** (dossiers de notes, `code:path` par ligne ;
`*` = actif) — un dossier par ligne sous la forme `code:path`. Marquez le dossier actif avec `*`.
Exemple :

```
A*:~/Knotes
B:~/Work-notes
```

Avec plus d'un dossier, un sélecteur apparaît à côté du bouton d'envoi, et **Ctrl+1** … **Ctrl+9**
permettent de passer de l'un à l'autre. Voir
[Organiser les notes](organizing-notes.md#several-note-folders).

### Tous les paramètres globaux { #all-app-wide-settings }

| Paramètre | Ce qu'il fait | Par défaut |
|---|---|---|
| **Timestamp format** (format d'horodatage) | Le début des nouveaux noms de fichiers, sous forme de motif Python `strftime` | `%Y_%m_%d-%H%M%S` |
| **Open notes with** (ouvrir les notes avec) | Commande qui ouvre une note avec **Enter**, par exemple `xed`. Vide = application par défaut du système | vide |
| **Preview characters (head)** (caractères d'aperçu, début) | Caractères affichés depuis le début d'une note sur sa carte | 120 |
| **Counts shown from** (compteurs affichés à partir de) | Les notes au moins aussi longues affichent les compteurs de surlignages et de marques temporelles. 0 = toutes les cartes | 120 |
| **Editor pane starts in** (le panneau d'édition démarre en) | **Read (highlights, no editing)** (lecture : surlignages, pas d'édition) ou **Edit (type straight in)** (édition : saisie directe) | Read |
| **Preview words (head)** (mots d'aperçu, début) | Mots du début affichés sur la carte **Just added** (tout juste ajoutée) | 2 |
| **Preview words (tail)** (mots d'aperçu, fin) | Mots de la fin affichés sur la carte **Just added** | 2 |
| **Preview characters (tail)** (caractères d'aperçu, fin) | Caractères affichés depuis la fin d'une note longue. 0 = aucun | 40 |
| **Saving** → **Save on Enter (Shift+Enter for a newline)** (enregistrement → enregistrer avec Entrée, Shift+Entrée pour un saut de ligne) | Un simple **Enter** enregistre la note | désactivé |
| **Tags** → **List every #hashtag under the add field** (étiquettes → lister chaque #hashtag sous le champ d'ajout) | Désactivé, un total compact s'affiche à la place | activé |
| **Startup** → **Open filtered to today's notes** (démarrage → ouvrir filtré sur les notes du jour) | Démarrer avec aujourd'hui sélectionné dans le calendrier | activé |
| **Highlight colour** (couleur de surlignage) | Couleur derrière les passages surlignés | jaune |
| **Append separator** (séparateur d'ajout) | Inséré avant un commentaire ajouté rapidement. `$date` = heure actuelle, `\n` = nouvelle ligne | `\n---$date:\n` |
| **Append date format** (format de date d'ajout) | Format `strftime` pour `$date` | `%Y-%m-%d %H:%M` |
| **Kanban rows** (rangées kanban) | Nombre de rangées sur lesquelles se répartissent les colonnes kanban (1 à 3) | 2 |
| **Card max width (columns view)** (largeur maximale des cartes, vue colonnes) | Largeur maximale d'une carte dans la vue colonnes | 340 |

!!! warning "Modifier le format d'horodatage"
    Les autres applications knotes attendent le format par défaut `YYYY_MM_DD-HHMMSS`. Ne le
    modifiez que si toutes les applications qui lisent le dossier utilisent le même format.

## Backend Nibras — par défaut { #nibras-backend-default }

**Backend URL**, **Backend username** et **Backend password** (URL, nom d'utilisateur et mot de
passe du backend) — le serveur qu'utilisent, pour la synchronisation des codes, les dossiers qui
n'ont pas leur propre backend. URL vide = hors ligne. Voir
[Se connecter à un serveur Nibras](nibras-server.md).

## Options d'étiquettes du dossier actif { #tag-options-for-the-active-folder }

Elles sont enregistrées dans le dossier (dans `.knotes.json`) et ne s'appliquent qu'à lui. L'en-tête
nomme le dossier, par exemple **Tag options for folder “A”** (options d'étiquettes du dossier « A »).

| Paramètre | Ce qu'il fait |
|---|---|
| **Priority min** / **Priority max** (priorité min. / max.) | La plage des pastilles de priorité |
| **Default priority** (priorité par défaut) | La priorité avec laquelle démarre une nouvelle note |
| **Types (code:name per line; * = default)** (types, `code:name` par ligne ; `*` = par défaut) | Les types de ce dossier ; `*` marque celui par défaut |
| **Categories (code:name per line; * = default)** (catégories, `code:name` par ligne ; `*` = par défaut) | Les catégories de ce dossier ; `*` marque celle par défaut |
| **This folder's backend URL** / **username** / **password** (URL / nom d'utilisateur / mot de passe du backend de ce dossier) | Un serveur Nibras pour ce dossier uniquement. Vide = utiliser le backend par défaut |

Voir [Type, priorité et catégorie](tagging.md#define-your-own-codes).

## Paramètres de la barre d'outils { #toolbar-settings }

Ils changent si souvent qu'ils se trouvent dans la barre d'outils et sont mémorisés
automatiquement :

- Vue : **Kanban**, **Simple list** (liste simple), **Columns** (colonnes), **Minimal**
- Ordre de tri
- Thème clair / sombre (**Ctrl+T**)
- Panneau d'édition latéral ouvert ou fermé, et sa largeur (**Ctrl+E**)
- Barre latérale de filtres affichée ou masquée, sections repliées, et commutateur **Created** /
  **Updated** (créé / modifié) du calendrier (**Ctrl+B**)
- Ouverture automatique du dossier de fichiers après chaque enregistrement

## Le fichier de configuration { #the-config-file }

Tous les paramètres sont stockés dans `config.json` **à côté du programme** ; un dossier de
programme copié emporte donc ses paramètres avec lui. Fermez l'application avant de modifier le
fichier à la main.

### Uniquement dans le fichier de configuration { #config-file-only }

Quelques options n'ont pas de contrôle dans la boîte de dialogue :

| Clé | Ce qu'elle fait | Par défaut |
|---|---|---|
| `tag_bank_path` | Chemin vers votre `tag-bank.md`. Vide = à côté du programme. Faites-le pointer vers un dossier synchronisé pour partager le vocabulaire entre machines | vide |
| `card_file_chars` | Nombre de caractères de chaque nom de fichier joint affichés sur une carte | 12 |
| `editor_autosave_ms` | Pause (en millisecondes) avant que l'éditeur latéral n'enregistre | 900 |
