# api-gateway

![Build](https://github.com/theoduluard/banking-platform/actions/workflows/build.yml/badge.svg)
![Coverage](https://raw.githubusercontent.com/theoduluard/banking-platform/main/.github/badges/api-gateway/coverage.svg)

Point d'entrée unique de la plateforme Solaris Bank.

## Responsabilités

- Valide le JWT sur toutes les routes protégées
- Injecte le header `X-User-Id` pour les services en aval
- Route les requêtes vers le bon microservice
- Routes publiques : `/api/v1/auth/login`, `/api/v1/auth/register`

## Stack

| Couche | Technologie |
|---|---|
| Framework | Spring Cloud Gateway MVC 2025.x |
| Sécurité | JJWT 0.12.6 (validation JWT) |
| Build | Maven 3.9 |

## Routing

| Pattern | Service cible | Authentification |
|---|---|---|
| `/api/v1/auth/**` | auth-service:8081 | Non |
| `/api/v1/accounts/**` | account-service:8082 | JWT requis |
| `/api/v1/transactions/**` | transaction-service:8083 | JWT requis |

## Lancer en local

```bash
# Le gateway doit être lancé en dernier
./mvnw spring-boot:run
```

## Tests

```bash
./mvnw test
```
