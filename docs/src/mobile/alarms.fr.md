# Alarmes, rappels et carillon

knotes peut sonner à un moment donné de quatre façons :

- les **Alarmes** que vous réglez vous-même : une heure, un minuteur ou un décalage par rapport à une prière ;
- les **Rappels des plans** avant les événements de votre agenda Nibras ;
- un **Carillon toutes les 15 minutes** qui marque le passage du temps ;
- l'**adhan**, traité dans [Heures de prière et adhan](prayer.md).

Tous continuent de fonctionner application fermée et sont réarmés après un redémarrage du téléphone.

## Alarmes { #alarms }

Ouvrez **Alarmes** (l'icône du réveil) depuis le rail. L'en-tête affiche **Alarmes** et le nombre d'alarmes activées (**N actives**).

### Ajouter une alarme { #add-an-alarm }

Touchez le bouton **+** (**Ajouter une alarme**). Le formulaire **Nouvelle alarme** s'ouvre :

1. **Libellé** : facultatif, affiché dans la liste et dans la notification.
2. **Type** : choisissez **Heure**, **Minuteur** ou **Prière**. Les champs en dessous s'adaptent. Changer de type conserve ce que vous avez saisi pour les autres.
3. Réglez le moment (voir ci-dessous).
4. **Son** : **Choisir…** n'importe quel fichier audio du téléphone, ou **Par défaut** pour le **Son d’alarme par défaut**.
5. **Répétitions : N×** : le nombre de fois où le son est joué (de 1 à 20).
6. **Volume : N %** : le niveau sonore, pour cette alarme uniquement.
7. Enregistrez.

#### Heure { #time }

Une **Heure** et une **Minute**, ainsi que les **Jours** de répétition. **Jours — aucun signifie une seule fois** : si aucun jour n'est choisi, elle sonne une fois à la prochaine occurrence de cette heure, comme une alarme de téléphone.

#### Minuteur { #countdown }

Choisissez une **Durée** (5, 10, 15, 20 ou 30 minutes, 1 h, 1 h 30 min ou 2 h), ou saisissez n'importe quel nombre dans **Ou en minutes**.

Un minuteur ne démarre pas du simple fait d'être enregistré. Lancez-le avec le bouton de lecture de sa ligne. Il affiche **en cours** pendant le décompte et **non démarré** sinon. Si le téléphone était éteint au moment où il aurait dû sonner, il ne sonne pas en retard au rallumage.

#### Prière { #prayer }

Choisissez l'une des prières et un **Décalage — négatif signifie avant**, par exemple −30 pour une demi-heure avant le Fajr, ou **à l’heure** pour l'heure exacte. Vous pouvez aussi la limiter à certains **Jours**. L'alarme suit la prière chaque jour et utilise les réglages de la page [Heures de prière](prayer.md) : elle concorde donc toujours avec ce qu'affiche cette page.

### La liste { #the-list }

Chaque ligne indique ce qu'est l'alarme et **quand elle sonnera réellement la prochaine fois**, par exemple **sonne demain à 05:10**. Si une alarme est activée mais n'a aucun moment où se déclencher (un minuteur non démarré, par exemple), elle indique **non programmée**, pour qu'elle ne puisse pas sembler armée alors qu'elle ne l'est pas.

Chaque ligne comporte :

- un interrupteur pour l'activer ou la désactiver,
- un bouton lecture/arrêt (minuteurs uniquement),
- un bouton de suppression, qui demande d'abord **Supprimer cette alarme ?**,
- **Modifier**.

Lorsqu'une alarme sonne, une notification affiche son libellé, et le son est joué le nombre de fois choisi, au volume de cette alarme, sur le canal des alarmes.

!!! warning "Des alarmes qui sonnent en retard ?"
    Si la page affiche **Les alarmes exactes ne sont pas autorisées pour cette appli : elles peuvent sonner en retard.**, ouvrez les paramètres Android de knotes et autorisez **Alarmes et rappels**.

Le [widget Compte à rebours des alarmes](widgets.md#alarms-countdown) indique le temps restant avant les alarmes restantes du jour.

## Rappels des plans { #plan-reminders }

Si vous êtes connecté à un [serveur Nibras](sync.md), knotes vous prévient avant les événements **Plans** et **Journal** à venir qui ont une heure de début. Configurez-les dans les [Paramètres](settings.md) → **Rappels des plans** :

| Réglage | Par défaut | Signification |
|---|---|---|
| **Délais (minutes)** | `60, 5` | Les minutes avant le début. Plusieurs valeurs sont possibles, chacune donnant sa propre alerte. `60, 5` signifie une heure avant et cinq minutes avant. |
| **Son de rappel** | Par défaut | **Choisir un son** pour en sélectionner un. |
| **Exiger l’arrêt** | Désactivé | Activé : une alarme plein écran dont le son tourne en boucle jusqu'à ce que vous touchiez **Ignorer**. Désactivé : le son est joué le nombre de fois indiqué ci-dessous, puis s'arrête. |
| **Répéter le son (fois)** | 4 | Le nombre de fois où le son est joué lorsque **Exiger l’arrêt** est désactivé. |

La notification de rappel affiche le plan et son heure de début (**dans 5 min · 14:30**). Elle reste jusqu'à ce que vous agissiez :

- **Ignorer** efface ce rappel. Les rappels suivants pour le même événement arrivent quand même.
- **Arrêter les rappels** annule les rappels restants pour cette occurrence, pour le cas où vous y êtes déjà. Les rappels de la semaine suivante d'un plan récurrent ne sont pas concernés.

Si des tâches du serveur ont dépassé leur échéance, knotes publie aussi une notification unique **N tâches en retard**.

## Le carillon toutes les 15 minutes { #the-15-minute-chime }

Un carillon doux à **:00, :15, :30 et :45**, pour sentir le temps passer sans rien regarder. Configurez-le dans les [Paramètres](settings.md) → **État et carillon** :

- **Carillon toutes les 15 minutes** : activé ou désactivé (activé par défaut ; également proposé dans la visite de configuration sous le nom **Carillon tous les quarts d’heure**).
- **Heure de début du carillon** et **Heure de fin du carillon** : il ne sonne que dans cette plage (de 8 à 22 par défaut).
- **Choisir le fichier du carillon** : n'importe quel fichier audio. Si aucun n'est choisi, le son de notification par défaut du téléphone est utilisé.

Le carillon est joué au volume des **notifications** de votre téléphone : il est donc aussi fort que vos autres notifications et respecte ce curseur.

## La notification d'état { #the-status-notification }

**Notification permanente** (dans **État et carillon**, activée par défaut) maintient une notification discrète et continue qui affiche le nombre de notes du jour (**Aujourd’hui : N · Total : M**) et, lors de la synchronisation avec Nibras, si vous êtes en ligne et quand a eu lieu la dernière synchronisation.

Elle maintient aussi l'application active en arrière-plan, ce qui aide les rappels et le carillon à se déclencher à l'heure. Si vous la désactivez et que les alarmes commencent à arriver en retard, réactivez-la.
