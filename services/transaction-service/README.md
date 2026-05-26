# transaction-service

![Build](https://github.com/theoduluard/banking-platform/actions/workflows/build.yml/badge.svg)
![Coverage](https://raw.githubusercontent.com/theoduluard/banking-platform/main/.github/badges/transaction-service/coverage.svg)

Service de gestion des virements de la plateforme Solaris Bank.

## Responsabilités

- Initiation des virements entre comptes (Saga Choreography via Kafka)
- Retour immédiat `202 Accepted` — la transaction passe en `PENDING`
- Écoute des résultats de débit/crédit pour finaliser (`COMPLETED`) ou annuler (`FAILED`)
- Compensation automatique en cas d'échec du crédit (rollback du débit)

## Saga Choreography

```
transaction-service          account-service
      │                            │
      ├─ DebitRequestedEvent ──────►│
      │                            ├─ débit compte source
      │◄─ DebitResultEvent ────────┤
      │                            │
      ├─ CreditRequestedEvent ─────►│
      │                            ├─ crédit compte destination
      │◄─ CreditResultEvent ───────┤
      │                            │
   COMPLETED / FAILED
```

## Topics Kafka

| Topic | Producteur | Consommateur |
|---|---|---|
| `account.debit.requested` | transaction-service | account-service |
| `account.debit.result` | account-service | transaction-service |
| `account.credit.requested` | transaction-service | account-service |
| `account.credit.result` | account-service | transaction-service |

## Endpoints

| Méthode | Route | Description | Auth |
|---|---|---|---|
| `POST` | `/api/v1/transactions/transfer` | Initier un virement | JWT |
| `GET` | `/api/v1/transactions` | Historique | JWT |
| `GET` | `/api/v1/transactions/{id}` | Détail | JWT |

## Lancer en local

```bash
cd ../../infrastructure && docker compose up -d postgres-transactions kafka
./mvnw spring-boot:run
```

## Tests

```bash
./mvnw test
```
