# Step 1B — monservice (Spring Boot demo)

Tiny REST service demonstrating fat-jar packaging + Docker (single-stage and multi-stage).

## Endpoints

- `GET /monservice/echo/{nom}` → `{"message":"echo: {nom}"}`
- `POST /monservice/hello` with body `{"nom":"value"}` → `{"message":"Hello value"}`

## Local run (non-containerized)

```bash
mvn clean package
java -jar target/monservice.jar
# in another terminal:
curl http://localhost:8080/monservice/echo/Mikael
curl -X POST -H 'Content-Type: application/json' -d '{"nom":"Mikael"}' http://localhost:8080/monservice/hello
```

## Cas 1 — single-stage Dockerfile + docker-compose

The fat-jar must already exist in `target/`. Then:

```bash
mvn clean package
docker compose up --build -d
curl http://localhost:8080/monservice/echo/Mikael
docker compose down
```

`Dockerfile.simple` copies the locally-built fat-jar into a slim JRE image.

## Cas 2 — multi-stage Dockerfile

No host JDK/Maven required. Sources are compiled inside the build stage; only the resulting fat-jar is copied to the final JRE image.

```bash
docker build -t monservice:multistage -f Dockerfile .
docker run -d --rm -p 8080:8080 --name monservice monservice:multistage
curl http://localhost:8080/monservice/echo/Mikael
docker stop monservice
```

## Why multi-stage?

- **Smaller final image**: only a JRE + the fat-jar (no Maven, no JDK, no source code).
- **Reproducible**: the build environment is pinned in the first stage and identical across machines.
- **No host dependencies**: someone cloning the repo only needs Docker; they don't need Java or Maven installed.
- **Single command**: `docker build` does dependency resolution, compilation, packaging and image production in one shot.
