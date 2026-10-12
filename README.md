# Open Source Pulse — Backend

[![CI](https://github.com/EvanZig/open-source-pulse-be/actions/workflows/ci.yml/badge.svg)](https://github.com/EvanZig/open-source-pulse-be/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> A high-performance, automated backend service designed to discover, curate, and filter beginner-friendly open-source GitHub issues (`good first issue`, `help wanted`) across popular repositories.

---

## Architecture Overview

```
                      ┌─────────────────────────────────┐
                      │    GitHub REST API (v2022-11-28)│
                      └────────────────┬────────────────┘
                                       │  @HttpExchange / Declarative Client
                                       ▼  (Rate-limit aware, 7s backoff)
                      ┌─────────────────────────────────┐
                      │      GitHubSyncService          │
                      │  (Scheduled Ingestion Pipeline) │
                      └────────────────┬────────────────┘
                                       │  In-Memory Deduplication (LinkedHashMap)
                                       │  Batch Lookup (findAllByGithubIdIn)
                                       ▼
                      ┌─────────────────────────────────┐
                      │        IssueService             │
                      │  (Batch Upsert & Stale Pruning) │
                      └───────┬─────────────────┬───────┘
                              │                 │
           Write / Invalidate │                 │ Read (Cached)
                              ▼                 ▼
             ┌─────────────────────┐       ┌──────────────────────┐
             │   PostgreSQL 16     │       │   Redis 7 (Cache)    │
             │ - GIN Index (text[])│       │ - Normalized Keys    │
             │ - Flyway Migrations │       │ - Paged Response TTL │
             └─────────────────────┘       └──────────────────────┘
                              ▲
                              │ @EntityGraph (N+1 Prevention)
                              │ Composable JpaSpecification
                      ┌───────┴─────────────────┐
                      │     IssueController     │
                      │ (REST API /api/v1/issues│
                      └───────────────▲─────────┘
                                      │
            ┌─────────────────────────┴────────────────────────┐
            │                                                  │
┌───────────────────────┐                          ┌───────────────────────┐
│  React / Vite / Web   │                          │  Swagger / OpenAPI UI │
│   (CORS Configured)   │                          │   (/swagger-ui.html)  │
└───────────────────────┘                          └───────────────────────┘
```

---

## Senior Engineering Highlights

* **Java 21 Virtual Threads:** Enabled via `spring.threads.virtual.enabled: true` for lightweight, non-blocking concurrent request execution.
* **Declarative HTTP Client (`@HttpExchange`):** Type-safe HTTP interface via `RestClient` and `HttpServiceProxyFactory`, avoiding legacy `RestTemplate` boilerplate.
* **Dynamic Search Engine (JPA Specifications):** Composable, type-safe queries using the Criteria API. Filters by language, labels, and keywords with zero string concatenation or SQL injection risks.
* **N+1 Query Elimination (`@EntityGraph`):** Solves the classic ORM N+1 problem by joining parent `GitHubRepository` entities in a single database round-trip.
* **PostgreSQL Native Array & GIN Indexing:** Stores labels in native `text[]` columns with GIN index acceleration (`idx_issues_labels`), queried via `array_position()`.
* **Zero-Downtime Database Migrations (Flyway):** Automated schema migrations with strict repeatable versioning (`V1`, `V2`).
* **Type-Safe Configuration (`@ConfigurationProperties`):** Replaced scattered `@Value` magic strings with validated, immutable Java `record`s (`CorsProperties`).
* **Contract-First Documentation (OpenAPI 3 / Swagger):** Real-time interactive documentation available at `/swagger-ui.html`.
* **Resilient Ingestion:** In-memory deduplication using `LinkedHashMap` prevents unique key violations during live search ranking shifts.

---

## API Endpoints

| Method | Endpoint | Description | Auth / Access |
|---|---|---|---|
| `GET` | `/api/v1/issues` | Search & filter open issues (language, label, keyword, pagination) | Public |
| `GET` | `/api/v1/issues/{id}` | Fetch a single issue by internal database ID | Public |
| `GET` | `/actuator/health` | Application & PostgreSQL database health probe | Public |
| `GET` | `/swagger-ui.html` | Interactive Swagger API documentation & test console | Public |
| `GET` | `/v3/api-docs` | OpenAPI 3.0 JSON specification | Public |

### Sample Request & Response

```bash
GET /api/v1/issues?language=TypeScript&label=good%20first%20issue&page=0&size=20
```

```json
{
  "content": [
    {
      "id": 42,
      "githubId": 1827491823,
      "number": 128,
      "title": "Fix parser edge-case in JSX transformer",
      "htmlUrl": "https://github.com/facebook/react/issues/128",
      "state": "open",
      "bodySnippet": "Steps to reproduce: ...",
      "labels": ["good first issue", "compiler"],
      "commentsCount": 3,
      "githubCreatedAt": "2026-10-10T12:00:00Z",
      "repository": {
        "id": 5,
        "owner": "facebook",
        "name": "react",
        "htmlUrl": "https://github.com/facebook/react",
        "stars": 225000,
        "language": "JavaScript"
      }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

---

## Automated Testing Suite

The project enforces strict testing discipline using the **Testing Pyramid**:

```text
       / \         Full @SpringBootTest (End-to-End Context)
      /   \        Web Slice: @WebMvcTest (HTTP Routing, JSON & Status Codes)
     /_____\       Pure Unit Tests: Mockito (Domain & Service Logic, ~20ms execution)
```

* **Unit Testing (`IssueServiceTest`):** 100% Mockito-driven, running in milliseconds without Spring container overhead. Tests batch upserts, duplicate prevention, and exception throwing.
* **Web Slice Testing (`IssueControllerTest`):** Tests routing, parameter binding, default pagination, and error mapping (`ResourceNotFoundException` $\rightarrow$ `404 NOT FOUND`) using `MockMvc` and `@MockitoBean`.
* **Continuous Integration:** Automated on every push and pull request via GitHub Actions with real, isolated PostgreSQL and Redis service containers.

### Running Tests Locally

```bash
./mvnw test -Dtest=IssueServiceTest,IssueControllerTest
```

---

## Getting Started

### Prerequisites

* **JDK 21** or later
* **Docker** (for local PostgreSQL & Redis)

### 1. Start Infrastructure

```bash
# PostgreSQL
docker run -d --name oss-pulse-postgres \
  -e POSTGRES_DB=osspulse \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 postgres:16-alpine

# Redis
docker run -d --name oss-pulse-redis \
  -p 6379:6379 redis:7-alpine
```

### 2. Configure Environment (Optional)

```bash
# Optional: Set GitHub token to increase API rate limit from 10 to 30 req/min
export GITHUB_API_TOKEN="ghp_your_token_here"

# Optional: Override CORS allowed origins for your frontend
export CORS_ALLOWED_ORIGINS="http://localhost:3000,http://localhost:5173"
```

### 3. Run Application

```bash
./mvnw spring-boot:run
```

The application will start on **`http://localhost:8080`**.
Open **`http://localhost:8080/swagger-ui.html`** to browse and test the endpoints interactively.

---

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

