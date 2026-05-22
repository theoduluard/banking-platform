# Solaris Bank — Banking Platform

Plateforme bancaire complète construite sur une architecture microservices.
Projet d'apprentissage couvrant Spring Boot, Spring Cloud Gateway, JPA, JWT, Kafka et React.

---

## Table des matières

- [Architecture](#architecture)
- [Services](#services)
- [Prérequis](#prérequis)
- [Lancer le projet](#lancer-le-projet)
- [API Reference](#api-reference)
- [Structure du projet](#structure-du-projet)
- [Documentation](#documentation)

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                     React UI                        │
└─────────────────────────┬───────────────────────────┘
                          │ :8080
┌─────────────────────────▼───────────────────────────┐
│                    API Gateway                      │
│         Validation JWT · Routing · X-User-Id        │
└──────────┬──────────────────────────┬───────────────┘
           │                          │
    /auth/**│                         │/accounts/**
           │                          │
┌──────────▼──────────┐   ┌───────────▼─────────────┐
│    auth-service     │   │    account-service      │
│       :8081         │   │        :8082            │
│                     │   │                         │
│  Register · Login   │   │  Comptes · IBAN · Solde │
│  JWT · BCrypt       │   │  Transactions (à venir) │
└──────────┬──────────┘   └───────────┬─────────────┘
           │                          │
┌──────────▼──────────┐   ┌───────────▼─────────────┐
│   postgres-auth     │   │   postgres-accounts     │
│      :5433          │   │        :5434            │
└─────────────────────┘   └─────────────────────────┘
```

### Principes d'architecture

- **Un service = une responsabilité** — chaque service gère son propre domaine métier
- **Une base de données par service** — pas de base partagée entre services
- **Sécurité centralisée** — le gateway valide le JWT et injecte `X-User-Id`, les services font confiance au gateway
- **Stateless** — aucune session serveur, authentification par JWT uniquement

---

## Services

### API Gateway — `:8080`
Point d'entrée unique de la plateforme.
- Valide le JWT sur toutes les routes protégées
- Injecte le header `X-User-Id` pour les services en aval
- Route les requêtes vers le bon microservice
- Routes publiques : `/api/v1/auth/login`, `/api/v1/auth/register`

### auth-service — `:8081`
Gestion des utilisateurs et de l'authentification.
- Inscription avec validation du mot de passe (maj, min, chiffre, caractère spécial)
- Login avec génération de JWT (access token 24h + refresh token 7j)
- Hashage BCrypt des mots de passe
- Gestion des erreurs de validation avec messages explicites

### account-service — `:8082`
Gestion des comptes bancaires.
- Création de compte (CHECKING ou SAVINGS)
- Génération automatique d'IBAN français (algorithme MOD-97)
- Consultation des comptes et soldes
- Blocage / activation de compte

### transaction-service — `:8083` *(à venir)*
Virements et historique des transactions.
- Virements entre comptes avec pattern Saga
- Détection de fraude via Kafka
- Historique paginé avec export CSV

### fraud-detection-service — `:8084` *(à venir)*
Analyse des transactions en temps réel.
- Scoring de risque
- Règles métier configurables
- Alertes et résolution manuelle

### report-service — `:8085` *(à venir)*
Génération de relevés et exports.
- Relevés PDF mensuels
- Exports CSV
- Spring Batch pour la génération en masse

---

## Prérequis

| Outil | Version minimale |
|---|---|
| Java | 21 |
| Maven | 3.9+ |
| Docker Desktop | 4.x |
| IntelliJ IDEA | 2024+ (recommandé) |

---

## Lancer le projet

### 1. Démarrer les bases de données

```bash
cd infrastructure
docker compose up -d
```

Vérifie que les containers tournent :
```bash
docker ps
# postgres-auth      → :5433
# postgres-accounts  → :5434
```

### 2. Lancer les services

Chaque service se lance dans un terminal séparé :

```bash
# Terminal 1 — auth-service
cd services/auth-service
./mvnw spring-boot:run

# Terminal 2 — account-service
cd services/account-service
./mvnw spring-boot:run

# Terminal 3 — API Gateway (lancer en dernier)
cd services/api-gateway
./mvnw spring-boot:run
```

### 3. Vérifier que tout tourne

```bash
# auth-service
curl http://localhost:8081/actuator/health

# account-service
curl http://localhost:8082/actuator/health

# gateway
curl http://localhost:8080/actuator/health
```

---

## API Reference

Toutes les requêtes passent par le gateway sur le port **8080**.

### Auth

#### S'inscrire
```bash
POST /api/v1/auth/register
Content-Type: application/json

{
  "firstname": "Jean",
  "lastname": "Dupont",
  "email": "jean@solaris.com",
  "password": "Pass@123"
}
```
Réponse `201 Created` :
```json
{
  "message": "Account created successfully",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### Se connecter
```bash
POST /api/v1/auth/login
Content-Type: application/json

{
  "email": "jean@solaris.com",
  "password": "Pass@123"
}
```
Réponse `200 OK` :
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "jean@solaris.com",
  "firstname": "Jean",
  "lastname": "Dupont",
  "role": "CLIENT"
}
```

### Accounts

> Toutes les routes accounts nécessitent le header `Authorization: Bearer <token>`

#### Créer un compte
```bash
POST /api/v1/accounts
Authorization: Bearer <token>
Content-Type: application/json

{
  "type": "CHECKING"
}
```
Réponse `201 Created` :
```json
{
  "id": "uuid...",
  "iban": "FR7630006000010000000000197",
  "type": "CHECKING",
  "balance": 0,
  "currency": "EUR",
  "status": "ACTIVE",
  "createdAt": "2026-05-22T18:00:00"
}
```

#### Lister mes comptes
```bash
GET /api/v1/accounts
Authorization: Bearer <token>
```

#### Détail d'un compte
```bash
GET /api/v1/accounts/{id}
Authorization: Bearer <token>
```

#### Changer le statut d'un compte
```bash
PUT /api/v1/accounts/{id}/status?status=BLOCKED
Authorization: Bearer <token>
```

### Codes d'erreur

| Code | Signification |
|---|---|
| `400` | Validation échouée (champ manquant, format invalide) |
| `401` | Token JWT absent, expiré ou invalide |
| `403` | Accès refusé (ressource appartenant à un autre utilisateur) |
| `404` | Ressource introuvable |
| `409` | Conflit (email déjà utilisé, IBAN déjà existant) |
| `500` | Erreur serveur inattendue |

---

## Structure du projet

```
banking-platform/
├── services/
│   ├── api-gateway/          # Spring Cloud Gateway MVC — port 8080
│   ├── auth-service/         # Authentification JWT — port 8081
│   ├── account-service/      # Comptes bancaires — port 8082
│   ├── transaction-service/  # (à venir) — port 8083
│   ├── fraud-detection-service/ # (à venir) — port 8084
│   └── report-service/       # (à venir) — port 8085
│
├── frontend/
│   └── banking-ui/           # React + Vite (à venir)
│
├── infrastructure/
│   └── docker-compose.yml    # PostgreSQL, Kafka, Redis (à venir)
│
└── docs/
    ├── memo-auth-service.md      # Cours — auth-service
    ├── memo-account-service.md   # Cours — account-service
    └── memo-api-gateway.md       # Cours — API Gateway
```

---

## Documentation

Les fichiers dans `docs/` sont des mémos pédagogiques qui expliquent les concepts, annotations et décisions d'architecture de chaque service.

| Fichier | Contenu |
|---|---|
| [memo-auth-service.md](docs/memo-auth-service.md) | Spring Security, JWT, BCrypt, validation, gestion des erreurs |
| [memo-account-service.md](docs/memo-account-service.md) | JPA, BigDecimal, IBAN/MOD-97, DTOs, Repository |
| [memo-api-gateway.md](docs/memo-api-gateway.md) | Gateway, RestClient, OncePerRequestFilter, Transfer-Encoding |

---

## Stack technique

| Couche | Technologie |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.x |
| Gateway | Spring Cloud Gateway MVC 2025.x |
| Sécurité | Spring Security + JWT (JJWT 0.12.6) |
| Persistance | Spring Data JPA + Hibernate |
| Base de données | PostgreSQL 16 |
| Messaging | Apache Kafka *(à venir)* |
| Conteneurisation | Docker Compose |
| Frontend | React + Vite *(à venir)* |
| Build | Maven 3.9 |
