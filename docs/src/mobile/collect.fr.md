# Collecter des fichiers

Captures d'écran, photos de l'appareil photo, téléchargements et enregistrements vocaux s'accumulent dans les dossiers où écrivent les autres applications. **Collecter des fichiers** les rassemble dans une seule note, avec les fichiers joints et renommés pour se trier par date. Une capture d'écran isolée devient quelque chose que vous pouvez titrer, étiqueter et retrouver.

Ouvrez cette page depuis le rail (l'icône de dossier avec flèche, la dernière de **Sur ce téléphone**).

## Fonctionnement { #how-it-works }

1. À l'ouverture de la page, elle parcourt vos dossiers sources (**Analyse des dossiers source…**) et liste ce qu'elle a trouvé, chaque fichier avec son nouveau nom.
2. Le bandeau indique où iront les fichiers : **Ils seront déplacés dans un nouveau sous-dossier horodaté de … et renommés avec l’horodatage standard.**
3. Touchez **Collecter N fichiers**. Les fichiers sont déplacés et le résultat s'affiche (**N fichiers sur N déplacés**).
4. Touchez **Analyser encore** (ou la flèche **Analyser à nouveau** en haut) pour relancer l'analyse.

!!! warning "Les fichiers sont déplacés, pas copiés"
    Les fichiers collectés sont retirés de leurs dossiers d'origine. Ils se trouvent désormais dans la collection.

## Ce que vous obtenez { #what-you-get }

Chaque collection est une [note avec pièces jointes](notes.md#attachments) ordinaire :

```
2026_06_11-095700~W~p2~P.md        the note: "Collected 3 files" and a list
2026_06_11-095700~W~p2~P.d/        the files
    2024_01_15-123045_Screenshot_Chrome.png
    2026_06_11-095700_notes.pdf
    …
```

- La note utilise vos **type, priorité et catégorie** par défaut pour les nouvelles notes, comme une note rapide. Vous pouvez la réétiqueter comme n'importe quelle autre note.
- Chaque fichier est renommé de sorte qu'un **horodatage ouvre son nom**. Si le nom d'origine contient une date et une heure reconnaissables (c'est le cas de la plupart des noms produits par l'appareil photo, les captures d'écran et les enregistreurs), cette heure est utilisée. Sinon, c'est la date de dernière modification du fichier qui est utilisée, et la liste l'indique par **date de modif.**

Par exemple, `Screenshot_2024-01-15-12-30-45_Chrome.png` devient `2024_01_15-123045_Screenshot_Chrome.png`.

!!! tip "Placez les collections dans votre dossier de notes"
    Par défaut, les collections vont dans votre dossier de notes : elles apparaissent ainsi sur la page Notes et votre ordinateur les lit comme des notes. Si vous choisissez une autre destination, la visite de configuration vous avertit : *Les notes collectées n’apparaîtront pas sur l’écran d’accueil.*

## Choisir les dossiers { #choosing-the-folders }

Dans les [Paramètres](settings.md), sous **Collecte de fichiers** :

- **Dossiers sources** : les dossiers à parcourir. Par défaut, ce sont les emplacements habituels des captures d'écran, des photos de l'appareil photo, des images, des téléchargements et des enregistrements. Ajoutez les vôtres avec **Ajouter un dossier**. Décochez un dossier pour l'ignorer tout en le gardant dans la liste.
- **Dossier de destination (New)** : l'endroit où les collections sont écrites.

## Collecter automatiquement chaque jour { #collect-automatically-every-day }

Activez **Collecter automatiquement chaque jour** et choisissez une heure (**À 15:00** par défaut). La même collecte s'exécute alors toute seule chaque jour. Vous recevez une notification (**N fichiers collectés**) uniquement lorsque quelque chose a réellement été collecté.

## Désactiver la fonction { #switching-the-feature-off }

L'interrupteur **Collecte de fichiers** dans les Paramètres retire la page Collecte du rail et arrête l'exécution quotidienne, quel que soit le réglage automatique.
