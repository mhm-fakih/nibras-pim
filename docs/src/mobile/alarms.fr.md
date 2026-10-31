# Alarmes, rappels et carillon

Nibras Mobile peut sonner à un moment donné de cinq façons :

- le **minuteur rapide** en haut de la page Alarmes : un cadran, un bouton ;
- les **Alarmes** que vous réglez vous-même : une heure, un minuteur ou un décalage par rapport à une prière ;
- les **Rappels des plans** avant les événements de votre agenda Nibras ;
- un **carillon** qui marque le passage du temps, toutes les 15 minutes ou moins souvent ;
- l'**adhan**, traité dans [Heures de prière et adhan](prayer.md).

Tous continuent de fonctionner application fermée, et sont réarmés après un redémarrage du téléphone comme après une mise à jour de l'application.

## Minuteur rapide { #quick-timer }

Ouvrez **Alarmes** (l'icône du réveil) depuis le rail. Le haut de la page est le **Minuteur rapide** : un cadran de 60 minutes et un bouton rond à côté, pour les minuteurs du genre « dix minutes, à partir de maintenant ».

1. Faites glisser le doigt autour du cadran, dans le sens des aiguilles d'une montre depuis 12, comme sur un minuteur de cuisine, pour choisir **de 1 à 60 minutes**. Le centre affiche les minutes. Le cadran rouvre là où vous l'aviez laissé (10 minutes la première fois).
2. Touchez le bouton de lecture (**Démarrer le minuteur rapide**), ou le centre du cadran.
3. Pendant le décompte, l'anneau coloré se rétracte vers 12, le centre compte à rebours et, en dessous, **Fin à** indique l'heure à laquelle il sonnera.
4. Pour l'annuler, touchez le même bouton, devenu un bouton d'arrêt rouge (**Arrêter le minuteur rapide**), ou de nouveau le centre du cadran.

Le minuteur rapide sonne comme n'importe quelle alarme, avec une notification et un bouton **Arrêter**. Son son, son volume et le nombre de lectures se règlent une fois pour toutes dans les [Paramètres](settings.md#alerts) → **Alertes** → **Minuteur rapide** :

| Réglage | Par défaut | Signification |
|---|---|---|
| **Choisir un fichier son** | **Son d’alarme par défaut** | N'importe quel fichier audio du téléphone. **Son d’alarme par défaut** revient au son du téléphone. |
| **Volume N %** | 80 % | Le niveau sonore, en part du volume d'alarme maximal du téléphone. |
| **Joué N×** | 2× | Le nombre de fois où le son est joué (de 1 à 10). |

## Alarmes { #alarms }

Sous le minuteur rapide, l'en-tête affiche **Alarmes** et le nombre d'alarmes activées (**N actives**).

### Ajouter une alarme { #add-an-alarm }

Touchez le bouton **+** (**Ajouter une alarme**). Le formulaire **Nouvelle alarme** s'ouvre :

1. **Libellé** : facultatif, affiché dans la liste et dans la notification.
2. **Son** : **Choisir…** n'importe quel fichier audio du téléphone, ou **Par défaut** pour le **Son d’alarme par défaut**.
3. **Type** : choisissez **Heure**, **Minuteur** ou **Prière**. Les champs en dessous s'adaptent. Changer de type conserve ce que vous avez saisi pour les autres.
4. Réglez le moment (voir ci-dessous).
5. **Répétitions : N×** : le nombre de fois où le son est joué (de 1 à 20).
6. **Volume : N %** : le niveau sonore, pour cette alarme uniquement. Tout à gauche, le curseur affiche **Volume : minimum −5 dB** : le cran d'alarme le plus bas du téléphone, rendu encore un peu plus discret, pour une alarme qu'on doit à peine entendre.
7. **Enregistrer**.

#### Heure { #time }

Une **Heure** et une **Minute**, ainsi que les **Jours** de répétition. **Jours — aucun signifie une seule fois** : si aucun jour n'est choisi, elle sonne une fois à la prochaine occurrence de cette heure, comme une alarme de téléphone.

#### Minuteur { #countdown }

Choisissez une **Durée** (5, 10, 15, 20 ou 30 minutes, 1 h, 1 h 30 min ou 2 h), ou saisissez n'importe quel nombre dans **Ou en minutes**.

Un minuteur ne démarre pas du simple fait d'être enregistré. Lancez-le avec le bouton de lecture de sa carte. Il affiche **en cours** pendant le décompte et **non démarré** sinon. Si le téléphone était éteint au moment où il aurait dû sonner, il ne sonne pas en retard au rallumage.

#### Prière { #prayer }

Choisissez l'une des prières et un **Décalage — négatif signifie avant**, par exemple −30 pour une demi-heure avant le Fajr, ou **à l’heure** pour l'heure exacte. Vous pouvez aussi la limiter à certains **Jours**. L'alarme suit la prière chaque jour et utilise les réglages de la page [Heures de prière](prayer.md) : elle concorde donc toujours avec ce qu'affiche cette page.

### La liste { #the-list }

Chaque alarme est une carte qui se lit de haut en bas, une information par ligne :

- l'heure (ou la durée du minuteur, ou la prière et le décalage) à gauche, et votre libellé à droite ;
- **quand elle sonnera réellement la prochaine fois**, par exemple **sonne demain à 05:10**, ou **désactivée**. Si une alarme est activée mais n'a aucun moment où se déclencher (un minuteur non démarré, par exemple), elle indique **non programmée**, pour qu'elle ne puisse pas sembler armée alors qu'elle ne l'est pas ;
- ses jours (**une fois**, ou **Le** suivi des jours), ou pour un minuteur **en cours** / **non démarré** ;
- le fichier son qu'elle joue ;
- ses répétitions et son volume, par exemple **Répétitions : 2× · Volume : 60 %**.

La dernière ligne regroupe les actions :

- **Modifier** ;
- **Dupliquer** (l'icône de copie) : ouvre une copie de l'alarme dans le formulaire, pour en changer l'heure ou le son avant de l'enregistrer. **Annuler** ne laisse aucune copie. La copie d'un minuteur en cours démarre arrêtée ;
- un bouton lecture/arrêt (minuteurs uniquement) ;
- un bouton de suppression, qui demande d'abord **Supprimer cette alarme ?** ;
- un interrupteur pour l'activer ou la désactiver.

Lorsqu'une alarme sonne, une notification affiche son libellé avec un bouton **Arrêter**, et le son est joué le nombre de fois choisi. Pendant la lecture, le volume d'alarme du téléphone est réglé sur le niveau de cette alarme, puis remis comme avant : 50 % sonne donc pareil quel que soit le réglage du curseur d'alarme. Des alarmes dues au même moment sont jouées l'une après l'autre. Le son est joué par un service au premier plan, si bien qu'Android ne peut pas l'interrompre une fois l'alarme déclenchée.

!!! warning "Des alarmes qui sonnent en retard ?"
    Si la page affiche **Les alarmes exactes ne sont pas autorisées pour cette appli : elles peuvent sonner en retard.**, ouvrez les paramètres Android de Nibras Mobile et autorisez **Alarmes et rappels**.

Le [widget Compte à rebours des alarmes](widgets.md#alarms-countdown) indique le temps restant avant les alarmes restantes du jour.

## Rappels des plans { #plan-reminders }

Si vous êtes connecté à un [serveur Nibras](sync.md), Nibras Mobile vous prévient avant les événements **Plans** et **Journal** à venir qui ont une heure de début. Configurez-les dans les [Paramètres](settings.md#alerts) → **Alertes** → **Rappels des plans** :

| Réglage | Par défaut | Signification |
|---|---|---|
| **Délais (minutes)** | `60, 5` | Les minutes avant le début. Plusieurs valeurs sont possibles, chacune donnant sa propre alerte. `60, 5` signifie une heure avant et cinq minutes avant. |
| **Son de rappel** | Par défaut | **Choisir un son** pour en sélectionner un. |
| **Exiger l’arrêt** | Désactivé | Activé : une alarme plein écran dont le son tourne en boucle jusqu'à ce que vous touchiez **Ignorer**. Désactivé : le son est joué le nombre de fois indiqué ci-dessous, puis s'arrête. |
| **Rappeler même en mode silencieux ou vibreur** | Désactivé | Joue le son du rappel au volume de l'alarme : les modes silencieux et vibreur ne le coupent plus. |
| **Répéter le son (fois)** | 4 | Le nombre de fois où le son est joué lorsque **Exiger l’arrêt** est désactivé. |

La notification de rappel affiche le plan et son heure de début (**dans 5 min · 14:30**). Elle reste jusqu'à ce que vous agissiez :

- **Ignorer** efface ce rappel. Les rappels suivants pour le même événement arrivent quand même.
- **Arrêter les rappels** annule les rappels restants pour cette occurrence, pour le cas où vous y êtes déjà. Les rappels de la semaine suivante d'un plan récurrent ne sont pas concernés.

Si des tâches du serveur ont dépassé leur échéance, Nibras Mobile publie aussi une notification unique **N tâches en retard**.

## Le carillon { #the-chime }

Un carillon doux qui marque le passage du temps, pour le sentir passer sans rien regarder. Configurez-le dans les [Paramètres](settings.md#alerts) → **Alertes** → **État et carillon** :

- **Carillon** : activé ou désactivé (activé par défaut ; également proposé dans la visite de configuration sous le nom **Carillon tous les quarts d’heure**).
- **Carillon toutes les** : **15 min**, **30 min**, **1 h** ou **2 h**. L'intervalle se compte depuis l'heure de début : avec une heure de début à 8 et **2 h**, il sonne à 08:00, 10:00, 12:00, et ainsi de suite.
- **Choisir le fichier du carillon** : n'importe quel fichier audio. Si aucun n'est choisi, le son de notification par défaut du téléphone est utilisé.
- **Heure de début du carillon** et **Heure de fin du carillon** : il ne sonne que dans cette plage (de 8 à 22 par défaut, bornes comprises).

Le carillon est joué au volume des **notifications** de votre téléphone : il est donc aussi fort que vos autres notifications et respecte ce curseur.

## La notification d'état { #the-status-notification }

**Notification permanente** (Paramètres → **Alertes** → **État et carillon**, activée par défaut) maintient une notification discrète et continue qui affiche le nombre de notes du jour (**Aujourd’hui : N · Total : M**) et, lors de la synchronisation avec Nibras, si vous êtes en ligne et quand a eu lieu la dernière synchronisation.

Elle maintient aussi l'application active en arrière-plan, ce qui aide les rappels et le carillon à se déclencher à l'heure. Si vous la désactivez et que les alarmes commencent à arriver en retard, réactivez-la.
