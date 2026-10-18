# Heures de prière et adhan

**Heures de prière** (l'icône de la mosquée, la première de **Sur ce téléphone**) affiche les heures de prière du jour et la date hégirienne, et peut appeler l'adhan, vous prévenir à l'avance et mettre votre téléphone en silence pendant la prière. Tout est calculé sur le téléphone : cela fonctionne donc hors ligne, et rien concernant votre position ne quitte l'appareil.

## La page { #the-page }

- **La date hégirienne**, puis la date du jour et le nom de votre lieu.
- **La suivante** : la prochaine prière, son heure et un compte à rebours en direct (**dans 1 h 12 min**, puis **dans 4 min 30 s** à l'approche).
- **Les horaires** : Fajr, Chourouq (lever du soleil), Dhohr, Asr, Maghrib et Icha, chacun avec son nom arabe. Le prochain est mis en évidence, et une icône de haut-parleur (**Adhan activé**) signale les horaires où l'adhan sera appelé. Si vous avez décalé un horaire, l'ajustement est affiché à côté.

Sous les horaires se trouve une rangée de cinq puces. Chacune ouvre un panneau de réglages en dessous ; touchez-la à nouveau pour le fermer. **Chaque modification prend effet immédiatement ; il n'y a pas de bouton Enregistrer.**

| Puce | Panneau |
|---|---|
| **Horaires** | Les horaires à afficher, et les ajustements de chacun |
| **Méthode** | Le début de l'Asr, la méthode de calcul, votre position, les règles pour le Grand Nord |
| **Adhan** | Interrupteur général, son et volumes de l'adhan, rappels, compte à rebours du widget, bips avant le Chourouq |
| **Silence** | La mise en silence du téléphone aux heures de prière |
| **Hégire** | L'ajustement de la date hégirienne |

## D'abord : définir votre position { #first-set-your-location }

Les horaires dépendent de l'endroit où vous êtes. Ouvrez **Méthode → Votre position** :

- Touchez **Utiliser ma position** pour remplir la **Latitude** et la **Longitude** à partir de la dernière position connue du téléphone. Android demande l'autorisation de localisation la première fois. Seules les coordonnées sont lues, et seulement lorsque vous appuyez sur le bouton. Rien n'est consulté en ligne.
- Ou saisissez vous-même les coordonnées.
- **Nom du lieu (affiché en haut)** n'est qu'un libellé pour l'en-tête de la page.

## Horaires { #times }

- **Horaires à afficher** : décochez ceux dont vous ne voulez pas. Les horaires masqués sont aussi exclus des rappels et du widget.
- **Ajuster chaque horaire** : ajoutez ou retirez des minutes (de −60 à +60) avec les boutons − et +, et **réinitialiser** pour revenir en arrière. C'est ainsi que vous vous alignez sur la mosquée où vous priez réellement. Aucune méthode de calcul ne concorde avec toutes les mosquées.

## Méthode { #method }

- **École — début de l’Asr** : **Standard (chaféite / jaʿfarite)** ou **Hanafite**. Les deux diffèrent d'environ une heure en été.
- **Méthode de calcul** : de combien le soleil doit être sous l'horizon pour le Fajr et l'Icha. Chaque option affiche ses angles :
    - **Ligue islamique mondiale** (par défaut)
    - **Société islamique d’Amérique du Nord**
    - **Autorité générale égyptienne d’arpentage**
    - **Umm al-Qura, La Mecque**
    - **Université des sciences islamiques, Karachi**
    - **Institut de géophysique, Téhéran**
    - **Chiite duodécimain (Institut Leva, Qom)**

    Les méthodes chiite duodécimaine et de Téhéran retardent aussi le Maghrib jusqu'à ce que le soleil soit à 4° sous l'horizon, au lieu du coucher du soleil.
- **Nuits du Grand Nord** : au-delà d'environ 48° de latitude, il existe des nuits d'été où le Fajr ou l'Icha n'ont jamais lieu. Laissez ce réglage sur **Aucune (laisser indéfini)**, sauf si un horaire apparaît vide. Sinon, choisissez **Milieu de la nuit**, **Un septième de la nuit** ou **Selon l’angle**.

## Adhan { #athan }

### Interrupteurs { #switches }

- **Alertes activées** : l'interrupteur général. Désactivé, la page affiche toujours les horaires, mais rien ne sonne et aucune alarme n'est programmée. La page avertit alors : **Les alertes sont désactivées — les horaires restent affichés, mais rien ne sonnera.**
- **Horaires de prière dans l’appli** : désactivé, il retire toute la page du rail et ne programme rien. Pour la faire revenir, utilisez l'interrupteur **Les heures de prière sont masquées** dans les [Paramètres](settings.md).

### L'adhan { #the-athan }

- **Appeler l’adhan à** : choisissez les prières qui ont droit à l'adhan (Dhohr et Maghrib par défaut). Le Chourouq est le lever du soleil, pas une prière : il ne figure donc jamais dans cette liste.
- **Le son** : un **Adhan intégré (Mohammad Kazem-Zadeh)** est fourni, de sorte que l'adhan fonctionne dès le premier jour sans fichier à chercher. Pour utiliser un autre enregistrement, touchez **Choisir un fichier d’adhan** et sélectionnez n'importe quel fichier audio. **Effacer** revient à l'adhan intégré. **Écouter** le joue tout de suite ; **Arrêter** l'arrête.
- L'adhan est joué sur le canal sonore des **alarmes** : il reste donc audible lorsque le téléphone est mis en silence pour la prière.
- **Volume pour chacune** : un volume distinct par prière (80 % par défaut). Il peut rendre un adhan plus doux que le volume d'alarme du téléphone, jamais plus fort ; 0 le coupe. Les boutons **Écouter :** permettent de tester chacun.

Lorsque l'adhan est joué, une notification apparaît avec un bouton **Arrêter**.

### Rappels et compte à rebours { #reminders-and-countdown }

- **Me rappeler avant chaque horaire** : une notification discrète peu avant chaque horaire que vous avez choisi d'afficher (**10 min avant** par défaut, réglable de 1 à 60 minutes). Activé par défaut.
- **Compte à rebours du widget** : le [widget de prière](widgets.md#prayer-times) n'affiche son compte à rebours qu'une fois le prochain horaire aussi proche (1 heure par défaut, de 15 minutes jusqu'à **toujours affiché**).

### Bips avant le Chourouq { #beeps-before-shurooq }

Le lever du soleil met fin au temps du Fajr : il peut donc avoir son propre compte à rebours. Activez **Bips avant le Chourouq** et choisissez combien de temps avant le lever du soleil (10 min par défaut) et combien de bips (3 par défaut). Les bips retentissent sur le canal des alarmes, assez fort pour réveiller quelqu'un.

**Son du bip** : la **Tonalité intégrée** (aucun fichier requis), l'une des **Sonneries** du téléphone, ou **Un fichier**. **Écouter N** les joue tout de suite.

## Silence { #silence }

**Mettre le téléphone en silence → Passer en silence à ces horaires** met la sonnerie en silencieux à l'heure de la prière et la remet ensuite exactement comme elle était.

- **Pendant N min** : la durée du silence (15 par défaut, de 5 à 60).
- **À ces horaires** : les prières concernées (Dhohr et Maghrib par défaut). Ce choix est indépendant de l'adhan : vouloir le calme pendant la prière et vouloir entendre l'appel sont deux choix différents.

!!! note "Accès « Ne pas déranger »"
    Android ne permet à une application de modifier la sonnerie que si vous lui accordez l'accès « Ne pas déranger ». S'il manque, le panneau l'indique. Touchez **Autoriser l’accès** et activez knotes dans la liste.

## Hégire { #hijri }

La date hégirienne est calculée de manière arithmétique : elle n'a donc besoin d'aucun réseau. Le mois réel commence avec l'observation du croissant de lune, ce qui peut tomber un jour plus tôt ou plus tard.

- **Décaler de** : avancez ou reculez la date d'un jour pour la faire correspondre à ce qui a été annoncé là où vous vivez.
- **Le jour commence au Maghrib** : le jour musulman commence au coucher du soleil, donc après le Maghrib, la date affichée est déjà celle du lendemain. Activé par défaut.

## Voir aussi { #related }

- [Alarmes liées aux prières](alarms.md#prayer) : une alarme un nombre donné de minutes avant ou après n'importe quelle prière.
- Le [widget Heures de prière](widgets.md#prayer-times) sur votre écran d'accueil.

!!! tip "Position par défaut"
    Tant que vous n'avez pas défini la vôtre, la page utilise une position par défaut. Définissez la vôtre sous **Méthode → Votre position** avant de vous fier aux horaires.
