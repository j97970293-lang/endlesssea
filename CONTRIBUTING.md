# Contributing to Endless Sea / Contribuer à Endless Sea

🇬🇧 English first, 🇫🇷 French below.

---

## 🇬🇧 Contributing

Thank you for your interest! Endless Sea is a community project under **GPL-3.0**.

### Ground rules

1. **No pirated sources in the main repository.** Providers targeting copyright-infringing websites will be rejected. Demo/official extensions must use legal, redistributable sources only.
2. **The extension contract is sacred.** Changes to `extensions-api` must remain backward-compatible (or bump `apiVersion` and document the migration in `docs/en/05-extension-api.md`).
3. **Modularity first.** The player, download engine and extension loader must stay replaceable. Don't leak UI code into engine modules, don't leak engine code into `:app`.
4. **Android 8.0 (API 26) is the floor.** Every feature must work on API 26 or degrade gracefully.

### How to contribute

1. Fork, create a branch (`feat/…`, `fix/…`, `docs/…`).
2. Follow Kotlin official code style (`kotlin.code.style=official`).
3. Add/extend tests — see `docs/en/12-testing-strategy.md`.
4. Run `./gradlew testDebugUnitTest` before opening the PR.
5. One PR = one feature/fix. Describe *what* and *why*.

### Writing an extension

Read [`docs/en/05-extension-api.md`](docs/en/05-extension-api.md), then copy `demo-extensions/archive-org` as a starting point. Extensions live in **their own repository** — the main repo never hardcodes sources.

---

## 🇫🇷 Contribuer

Merci de votre intérêt ! Endless Sea est un projet communautaire sous **GPL-3.0**.

### Règles de base

1. **Aucune source pirate dans le dépôt principal.** Les providers ciblant des sites illégaux seront refusés. Les extensions officielles/démo n'utilisent que des sources légales et redistribuables.
2. **Le contrat d'extension est sacré.** Toute modification de `extensions-api` doit rester rétrocompatible (sinon, incrémenter `apiVersion` et documenter la migration).
3. **La modularité d'abord.** Lecteur, moteur de téléchargement et chargeur d'extensions doivent rester remplaçables sans réécrire l'application.
4. **Android 8.0 (API 26) est le plancher.** Chaque fonctionnalité marche sur API 26 ou se dégrade proprement.

### Étapes

1. Forkez, créez une branche (`feat/…`, `fix/…`, `docs/…`).
2. Style Kotlin officiel.
3. Ajoutez des tests (voir `docs/en/12-testing-strategy.md`).
4. `./gradlew testDebugUnitTest` avant la PR.
5. Une PR = une fonctionnalité/correction.

### Écrire une extension

Lisez [`docs/en/05-extension-api.md`](docs/en/05-extension-api.md), partez de `demo-extensions/archive-org`. Les extensions vivent dans **leur propre dépôt** — rien n'est codé en dur dans l'application.
