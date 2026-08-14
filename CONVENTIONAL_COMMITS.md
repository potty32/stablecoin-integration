# Conventional Commits — Stablecoin Platform

Format: `<type>(<scope>): <description>`

## Types

| Type       | Beschreibung                                     |
|------------|--------------------------------------------------|
| `feat`     | Neue Funktion                                    |
| `fix`      | Bugfix                                           |
| `docs`     | Nur Dokumentationsänderungen                     |
| `chore`    | Build, Abhängigkeiten, Konfiguration             |
| `refactor` | Umstrukturierung ohne Funktionsänderung          |
| `test`     | Tests hinzufügen oder anpassen                   |
| `ci`       | CI/CD-Pipeline-Änderungen                        |
| `perf`     | Performance-Verbesserungen                       |

## Scopes

| Scope      | Bereich                          |
|------------|----------------------------------|
| `backend`  | Spring Boot Backend              |
| `frontend` | Angular Frontend                 |
| `db`       | Flyway Migrationen               |
| `ci`       | GitHub Actions / Railway         |
| `docs`     | Projektdokumentation             |

## Beispiele

```
feat(backend): add idempotency check in B2bTransferService
fix(backend): correct revenue calculation when gas cost exceeds spread
feat(frontend): implement transfer-form component with wallet validation
chore(db): add V2 migration for address_book index
test(backend): add compliance block scenario for high-risk addresses
ci: add OWASP dependency-check to Maven build
refactor(backend): extract outbox event routing to dedicated handler
perf(backend): add partial index on outbox_message(status) for PENDING
```

## Breaking Changes

Für Breaking Changes ein `!` nach dem Type oder einen `BREAKING CHANGE:` Footer:

```
feat(backend)!: change transaction status enum to include COMPLIANCE_CHECK

BREAKING CHANGE: clients must handle the new COMPLIANCE_CHECK status
```
