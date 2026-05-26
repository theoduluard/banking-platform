# account-service

![Build](https://github.com/theoduluard/banking-platform/actions/workflows/build.yml/badge.svg)
![Coverage](https://raw.githubusercontent.com/theoduluard/banking-platform/main/.github/badges/account-service/coverage.svg)

Service de gestion des comptes bancaires de la plateforme Solaris Bank.

## Responsabilités

- Création de compte (CHECKING ou SAVINGS)
- Génération automatique d'IBAN français (algorithme MOD-97)
- Consultation des comptes et soldes
- Blocage / activation de compte
- Débit et crédit des comptes (consommateur Kafka Saga)

## Stack

| Couche | Technologie |
|---|---|
| Framework | Spring Boot 4.x |
| Messaging | Apache Kafka (KRaft) |
| Persistance | Spring Data JPA + PostgreSQL |
| Build | Maven 3.9 |

## Endpoints

| Méthode | Route | Description | Auth |
|---|---|---|---|
| `POST` | `/api/v1/accounts` | Créer un compte | JWT |
| `GET` | `/api/v1/accounts` | Lister mes comptes | JWT |
| `GET` | `/api/v1/accounts/{id}` | Détail d'un compte | JWT |
| `PUT` | `/api/v1/accounts/{id}/status` | Changer le statut | JWT |

## Topics Kafka

| Topic | Rôle |
|---|---|
| `account.debit.requested` | Reçoit les demandes de débit (Saga) |
| `account.credit.requested` | Reçoit les demandes de crédit (Saga) |
| `account.debit.result` | Publie le résultat du débit |
| `account.credit.result` | Publie le résultat du crédit |

## Lancer en local

```bash
cd ../../infrastructure && docker compose up -d postgres-accounts kafka
./mvnw spring-boot:run
```

## Tests

```bash
./mvnw test
```
