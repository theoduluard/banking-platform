# auth-service

![Build](https://github.com/theoduluard/banking-platform/actions/workflows/build.yml/badge.svg)
![Coverage](https://raw.githubusercontent.com/theoduluard/banking-platform/main/.github/badges/auth-service/coverage.svg)

Service d'authentification de la plateforme Solaris Bank.

## Responsabilités

- Inscription des utilisateurs avec validation du mot de passe (maj, min, chiffre, caractère spécial)
- Login avec génération de JWT (access token 24h + refresh token 7j)
- Hashage BCrypt des mots de passe
- Gestion des erreurs de validation avec messages explicites

## Stack

| Couche | Technologie |
|---|---|
| Framework | Spring Boot 4.x |
| Sécurité | Spring Security + JJWT 0.12.6 |
| Persistance | Spring Data JPA + PostgreSQL |
| Build | Maven 3.9 |

## Endpoints

| Méthode | Route | Description | Auth |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Créer un compte | Non |
| `POST` | `/api/v1/auth/login` | Se connecter | Non |

## Lancer en local

```bash
# Démarrer la base de données
cd ../../infrastructure && docker compose up -d postgres-auth

# Lancer le service
./mvnw spring-boot:run
```

## Tests

```bash
./mvnw test
```

Le rapport de couverture JaCoCo est généré dans `target/site/jacoco/index.html`.
