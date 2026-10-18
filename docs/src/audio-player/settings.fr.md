# Paramètres

Ouvrez **Settings** (paramètres) avec **Ctrl+,** ou le bouton engrenage de la barre de titre. Cliquez sur **Save** (enregistrer) pour appliquer.

| Paramètre | Ce qu'il fait | Par défaut |
|---|---|---|
| **Folder-tree root** (racine de l'arborescence) | Le dossier dont part l'arborescence **FOLDERS** (dossiers). Saisissez ou collez un chemin (`~` est accepté), ou utilisez **Browse…** (parcourir). Restez ciblé — votre dossier d'enregistrements, pas tout votre dossier personnel | une valeur intégrée, sinon votre dossier personnel |
| **Skip forward / back (seconds)** (saut avant / arrière, en secondes) | De combien sautent les larges boutons retour/avance, **←**/**→** et **Ctrl+←**/**Ctrl+→** | 10 |
| **Bookmark rewind (seconds)** (recul du marque-page, en secondes) | De combien en arrière un nouveau marque-page est placé (et la lecture reculée) | 5 |
| **Waveform limit (MB)** (limite de forme d'onde, en Mo) | Les fichiers plus gros que cette taille n'ont pas de forme d'onde. 0 = pas de limite | 10 |
| **Sleep timer** → **Show the sleep timer** (minuterie → afficher la minuterie) | Affiche le menu **Sleep** (minuterie) à côté du volume | désactivé |
| **Sleep presets (minutes)** (durées prédéfinies de la minuterie) | Durées proposées par la minuterie, séparées par des virgules | `15, 30, 45, 60` |

Vous pouvez aussi changer le dossier racine de l'arborescence avec le bouton ⚙ au-dessus de celle-ci.

## Mémorisé automatiquement { #remembered-automatically }

Inutile d'enregistrer ces éléments — le lecteur les conserve pour vous :

- Thème clair ou sombre (**Ctrl+T**)
- Vitesse de lecture et volume
- Taille de la fenêtre et tailles de tous les volets
- Ordre des colonnes de la liste de lecture
- Le dernier dossier chargé et le dernier fichier lu
- Votre position dans chaque fichier (pour la reprise)
- Les fichiers lus récemment et marqués récemment

## Le fichier de configuration { #the-config-file }

Tout est stocké dans `config.json` **à côté du programme**. Fermez le lecteur avant de le modifier
à la main. Le supprimer rétablit les valeurs par défaut du lecteur — vos marque-pages ne sont pas affectés, car
ils se trouvent dans les fichiers `.tmk` à côté de vos enregistrements.
