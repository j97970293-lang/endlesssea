# Philosophie d'Endless Sea quant aux extensions

> English summary in `docs/en/12-extension-philosophy.md`.

## Libre arbitre total pour les auteurs d'extensions

1. **Le code d'une extension s'exécute tel quel.** L'application ne filtre, ne
   censure, ni ne réécrit jamais le contenu qu'une extension sert — c'est à vous
   de choisir vos sources. Une extension peut afficher des bannières
   publicitaires intégrées à son catalogue, ouvrir des liens externes,
   présenter un avertissement légal, de l'argot, du contenu de niche… c'est son
   droit le plus strict.
2. **Ce que l'app encadre pour la sécurité technique** (et non pour des raisons
   de contenu) :
   - l'extension doit demander ses permissions (`INTERNET`, `DOWNLOAD`,
     `FILES`, …) dans son manifeste ;
   - le trafic passe par le client HTTP de l'app (journalisation, politique
     Wi-Fi/cookies) ;
   - les mises à jour d'extension avec un signataire différent sont refusées.
3. **Marqueurs de contenu.** Les dépôts peuvent marquer une extension `nsfw:
   true`; l'application ne l'installe alors pas automatiquement (choix
   d'installation initial, réversible dans l'écran Extensions).
4. **Responsabilité.** Les extensions sont du code tiers : elles relèvent de
   leurs auteurs et ne sont ni approuvées ni garanties par l'équipe Endless
   Sea. Respectez les licences et les conditions des sites concernés.
5. **Réglages.** Si votre extension déclare des réglages via
   `EsExtension.settings()` (SWITCH / TEXT / PASSWORD / LIST), l'utilisateur
   les édite dans *Paramètres → Extensions — réglages par source*, puis l'app
   les injecte dans `ExtensionContext.settings` à chaque instanciation.

## API 1 — ce qui est garanti stable

- Constructeurs DTO inchangés (ajout uniquement de propriétés hors
  constructeur, comme `ExtensionContext.settings`).
- `apiVersion: 1` reste accepté ; `minAppVersion` continue de bloquer
  l'installation sur une app trop ancienne.
