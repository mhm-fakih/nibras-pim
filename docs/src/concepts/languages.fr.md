# Langues

Nibras est conçu pour un usage bilingue et de droite à gauche. Il y a deux questions distinctes :
**dans quelle langue parlent les applications** (leurs menus et messages) et **comment elles disposent le texte que vous
écrivez** (dans n'importe quelle langue).

## Langues de l'interface { #interface-languages }

| Application | Interface |
|-----|-----------|
| [knotes pour Android](../mobile/index.md) | **Anglais, arabe (عربي) ou français (Français)** — au choix |
| [Application web](../web/index.md), [bureau](../desktop/index.md), [terminal](../terminal/index.md), [lecteur audio](../audio-player/index.md) | Anglais |

Sur le téléphone, le sélecteur de langue est la première page de la visite de configuration et la première ligne des
Paramètres. Chaque langue est écrite dans sa propre écriture, pour que vous trouviez la vôtre quelle que soit la langue
actuellement affichée. Le changement est immédiat. Tant que vous n'avez pas choisi, l'application suit la langue du
téléphone lorsqu'il s'agit de l'une des trois.

Sur Android 13 et versions ultérieures, le choix correspond à la langue par application du système ; il apparaît donc aussi (et peut être
modifié) dans les réglages système *Langues des applications*, et les widgets et notifications le suivent également.

### Arabe : de droite à gauche, avec chiffres latins { #arabic-right-to-left-with-latin-digits }

- **Toute l'interface est inversée.** La navigation, les listes et les commandes sont disposées de droite à gauche.
- **Les chiffres restent latins** (`12:30`, et non `١٢:٣٠`). Beaucoup de nombres formatés par l'application finissent dans des données —
  horodatages des signets, noms de fichiers, valeurs envoyées au serveur — et y changer la forme des chiffres
  corromprait des fichiers que d'autres outils et appareils lisent. Les chiffres latins sont aussi ceux qu'utilisent au quotidien de nombreux
  lecteurs arabophones.

### Ce qui est traduit et ce qui ne l'est pas { #what-is-and-isnt-translated }

| Traduit | Jamais traduit |
|------------|------------------|
| Menus, boutons, paramètres, boîtes de dialogue, messages | **Vos notes et fichiers** — leur texte et leurs noms |
| Libellés des widgets et textes des notifications | Vos noms de types et de catégories (votre propre vocabulaire, ou celui du serveur) |
| Formulations de dates comme les noms des mois de l'hégire | Les enregistrements et leur contenu sur le serveur |
| | Libellés des signets et transcriptions |

!!! note "Remarque"
    Le widget des heures de prière est toujours en arabe et de droite à gauche, quelle que soit la langue de l'interface —
    il utilise les noms arabes courts des prières, avec des chiffres latins.

## Texte arabe dans vos notes { #arabic-text-in-your-notes }

Quelle que soit la langue de l'interface, **chaque note se dispose selon son propre contenu** :

- Une ligne ou un paragraphe est de droite à gauche lorsque sa **première lettre forte** est arabe, et
  de gauche à droite sinon. Ainsi une note arabe s'aligne automatiquement à droite, une note anglaise
  à gauche, et un seul mot arabe cité dans une phrase anglaise ne renverse pas toute la
  ligne.
- Les notes mixtes fonctionnent ligne par ligne : un titre anglais au-dessus d'un paragraphe arabe est correct dans les deux parties.
- Les titres et les noms de fichiers (par exemple les noms arabes de pistes et de dossiers dans le lecteur du téléphone)
  s'alignent à droite selon la même règle.
- Les `#hashtags` arabes sont reconnus comme les autres.

Application par application :

| Application | Texte arabe |
|-----|-------------|
| Téléphone | Les cartes, le lecteur et l'éditeur s'alignent ligne par ligne, automatiquement. |
| Bureau | Chaque paragraphe choisit sa propre direction ; l'éditeur latéral passe à un volet de droite à gauche pour une note arabe, de sorte que les lignes vides et les nombres commencent du côté droit. Les étiquettes restent de gauche à droite (`T 2 A`). Un badge indique la disposition de clavier active (`EN` / `ع`). |
| Application web | Les champs de texte et le texte affiché détectent automatiquement la direction. |
| Lecteur audio | Les transcriptions arabes sont alignées à droite. |
| Terminal | Compte sur le terminal pour mettre en forme et ordonner l'arabe (la plupart des terminaux modernes le font). Si le vôtre ne le fait pas, définissez `NIBRAS_RESHAPE=1` avant de le lancer. La ligne de saisie elle-même reste alignée à gauche. |

## Étiqueter la langue d'un enregistrement { #tagging-a-records-language }

L'application web comprend une petite convention pour la langue du *contenu* des enregistrements : un mot formé
d'une apostrophe et d'un code de langue — `'en`, `'ar`, `'fr` — isolé.

- Dans la **recherche**, il filtre : `prayer 'ar` trouve les enregistrements arabes correspondant à « prayer ».
- À l'**import**, une telle étiquette dans la première ligne d'une note définit la langue du nouvel enregistrement et est retirée
  du titre.

Seuls les codes listés dans les paramètres de l'application web (*Language codes* (codes de langue), par défaut `ar,en,fr,fa,de`) sont
reconnus ; une apostrophe ordinaire dans votre texte n'est donc jamais prise pour l'un d'eux.
