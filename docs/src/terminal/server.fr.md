# Commandes serveur

Lorsqu'un dossier de notes est relié à un serveur Nibras, l'application terminal peut vérifier le serveur, vous
connecter et lister vos enregistrements les plus récents. Chaque dossier a son propre serveur (voir
[Configuration](configuration.md)), et ces commandes utilisent toujours le serveur du dossier **courant** — celui
qu'affiche la barre d'état.

## Le serveur est-il en marche ? { #is-the-server-up }

La barre d'état vérifie le serveur courant toutes les quelques secondes. Un **● vert** à côté de son adresse
signifie qu'il est joignable ; **rouge** signifie qu'il ne l'est pas. Pour vérifier à la demande :

```
/heartbeat
```

Le journal répond `up` ou `unreachable`.

## Se connecter { #log-in }

```
/login <username> <password>
```

En cas de succès, le journal affiche `logged in as …` avec votre nom d'utilisateur et votre rôle. La connexion est
mémorisée — par serveur — d'un redémarrage à l'autre jusqu'à son expiration, si bien que vous avez rarement besoin
de la répéter. Elle est stockée dans `~/.config/knotes/tokens.json`, lisible uniquement par votre utilisateur.

La connexion envoie aussi les notes mises en file d'attente avec le préfixe `+` pendant que vous étiez déconnecté.

!!! warning "Attention"
    Le mot de passe que vous tapez est visible dans la barre de commande et conservé dans l'historique ↑/↓ de la
    session jusqu'à ce que vous quittiez. Connectez-vous quand personne ne regarde votre écran.

Si la connexion expire, la commande suivante indique `session expired — /login again`.

## Lister les enregistrements récents { #list-recent-records }

Chacune de ces commandes liste les **20 enregistrements mis à jour le plus récemment** d'un type donné :

| Commande | Liste |
|---|---|
| `/tasks` | Tâches |
| `/goals` | Objectifs |
| `/journals` | Journaux |
| `/planners` | Plannings |
| `/resources` | Ressources |
| `/notes` | Notes |
| `/writings` | Écrits |

La sortie commence par le nombre total, puis une ligne par enregistrement — son numéro, son titre et, s'il en a un,
son statut :

```
tasks — 214 total, showing 20
  #812  Renew the domain  In progress
  #809  Draft the quarterly summary  Done
```

Si vous n'êtes pas connecté, l'application vous demande d'abord de faire `/login`.

Voir [Synchronisation](../concepts/sync.md) pour comprendre comment les applications et le serveur s'articulent.
