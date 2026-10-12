# Open Source Pulse — Backend

[![CI](https://github.com/EvanZig/open-source-pulse-be/actions/workflows/ci.yml/badge.svg)](https://github.com/EvanZig/open-source-pulse-be/actions/workflows/ci.yml)
[![Java 21](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-4.x-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](https://redis.io/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

> A high-performance, automated backend service designed to discover, curate, and filter beginner-friendly open-source GitHub issues (`good first issue`, `help wanted`) across popular repositories.

## API Endpoints

| Method | Endpoint | Description | Auth / Access |
|---|---|---|---|
| `GET` | `/api/v1/issues` | Search & filter open issues (language, label, keyword, pagination) | Public |
| `GET` | `/api/v1/issues/{id}` | Fetch a single issue by internal database ID | Public |
| `GET` | `/actuator/health` | Application & PostgreSQL database health probe | Public |
| `GET` | `/swagger-ui.html` | Interactive Swagger API documentation & test console | Public |
| `GET` | `/v3/api-docs` | OpenAPI 3.0 JSON specification | Public |

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

