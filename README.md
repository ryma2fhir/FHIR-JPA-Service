# HAPI FHIR JPA Server with Custom Validation Packages

## How to Use

1. Ensure docker is installed using `docker --version`

2. Within the parent directory build the maven package and run the docker container
```bash
mvn package
docker compose -up
```
3. go to:
- http://localhost:8080 for the landing page
- http://localhost:8080/fhir for swagger

4. to add packages to hapi see `fhir.implementationguides` within [hapi.application.yaml](https://github.com/ryma2fhir/FHIR-JPA-Service/blob/main/hapi.application.yaml)

## Details
The docker container [hapiproject/hapi:vx.x.x](https://hub.docker.com/r/hapiproject/hapi) is the offical HL7 release of the [hapi-fhir-jpaserver-starter](https://github.com/hapifhir/hapi-fhir-jpaserver-starter).
The hapi.application.yaml is a copy of [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml) which has been modified to include the neccessary packages

--- 

# HAPI FHIR Terminology Interceptor

This project adds authenticated terminology support to the [HAPI FHIR JPA Server](https://hapifhir.io/) Docker image, enabling it to proxy terminology operations (`$expand`, `$validate-code`, `$lookup`, `$translate`) to the [NHS Ontology Server](https://ontology.nhs.uk/) (Ontoserver) using OAuth2 client credentials.

Out of the box, HAPI FHIR cannot expand SNOMED CT ValueSets because it does not hold the terminology content locally. This interceptor forwards those requests to a remote terminology server that does, injecting a Bearer token automatically.

---

## How It Works

```
Client → HAPI FHIR → TerminologyOperationInterceptor → NHS Ontology Server
                              ↑
                    TerminologyInterceptor
                    (fetches & caches OAuth2 token)
```

1. A request hits HAPI FHIR for a terminology operation (e.g. `POST /fhir/ValueSet/$expand`)
2. `TerminologyOperationInterceptor` (a Spring servlet filter) catches it before HAPI processes it
3. It calls `TerminologyInterceptor.getBearerToken()` which fetches a token from the NHS auth server using OAuth2 Client Credentials — or returns the cached token if it's still valid
4. The original request is forwarded to the NHS Ontology Server with the token in the `Authorization` header
5. The response is streamed directly back to the client

HAPI's local database is never consulted for these operations.

---

## Project Structure

```
src/main/java/com/nhs/
├── TerminologyInterceptor.java           # OAuth2 token management
├── TerminologyOperationInterceptor.java  # Servlet filter — proxies terminology requests
└── TerminologyFilterConfig.java          # Registers the filter with Spring Boot
```

### TerminologyInterceptor
Implements HAPI's `IClientInterceptor`. Manages the OAuth2 token lifecycle:
- Fetches a token from `ONTO_AUTH_URL` using `ONTO_CLIENT_ID` and `ONTO_CLIENT_SECRET`
- Caches the token in memory and refreshes it 60 seconds before expiry
- Thread-safe via double-checked locking
- Also registered with HAPI's `remote_terminology` client for validation operations

### TerminologyOperationInterceptor
A Spring `OncePerRequestFilter` that runs at the servlet level, before HAPI touches the request:
- Checks if the request URI contains a terminology operation (`$expand`, `$validate-code`, `$lookup`, `$translate`)
- Strips the `/fhir` prefix and forwards the request to `ONTO_SERVER_URL`
- Injects the Bearer token from `TerminologyInterceptor`
- Streams the remote response back to the caller unchanged

### TerminologyFilterConfig
A Spring `@Configuration` class that explicitly registers `TerminologyOperationInterceptor` as a servlet filter scoped to `/fhir/*`. This is required because the JAR is loaded dynamically by HAPI's class loader, so Spring's component scan alone is not sufficient.

---

## Prerequisites

- Docker and Docker Compose
- Java 17+ and Maven (for building)
- Request a system-to-system account
 (from [The NHS England terminology server](https://digital.nhs.uk/services/terminology-server#how-to-access-this-service))

---

## Configuration

All secrets are stored in a `.env` file in the project root. **Never commit this file to version control.**

```
ONTO_AUTH_URL=https://ontology.nhs.uk/authorisation/auth/realms/nhs-digital-terminology/protocol/openid-connect/token
ONTO_CLIENT_ID=your-client-id
ONTO_CLIENT_SECRET=your-client-secret
ONTO_SERVER_URL=https://ontology.nhs.uk/production1/fhir
```

| Variable | Description |
|---|---|
| `ONTO_AUTH_URL` | OAuth2 token endpoint on the NHS auth server |
| `ONTO_CLIENT_ID` | Your OAuth2 client ID |
| `ONTO_CLIENT_SECRET` | Your OAuth2 client secret |
| `ONTO_SERVER_URL` | Base URL of the NHS Ontology Server FHIR endpoint |

---

## Build & Deploy

### 1. Build the JAR

```bash
mvn package
```

This produces `target/term-interceptor-1.0.jar`.

### 2. Start the server

```bash
docker compose up -d
```

Docker Compose will:
- Pull the HAPI FHIR image if not already present
- Mount the JAR into `/app/extra-classes/` where HAPI's class loader picks it up
- Mount `hapi.application.yaml` as the server config
- Inject all environment variables from `.env`

### 3. Verify it's working

```bash
docker compose logs fhir | grep -i "proxy\|token\|interceptor"
```

On the first terminology request you should see:
```
[CONFIG] Terminology proxy filter registered for /fhir/*
[PROXY] Intercepted POST /fhir/ValueSet/$expand — forwarding to https://ontology.nhs.uk/production1/fhir
[INTERCEPTOR] Refreshing OAuth2 token...
[INTERCEPTOR] Token refreshed. Expires in 300 s.
```

### 4. Test

```bash
curl -X POST "http://localhost:8080/fhir/ValueSet/\$expand" \
  -H "Content-Type: application/fhir+json" \
  -d '{
    "resourceType": "Parameters",
    "parameter": [{
      "name": "url",
      "valueUri": "http://snomed.info/sct?fhir_vs=isa/73211009"
    }]
  }'
```

---

## Updating HAPI FHIR

When a new HAPI FHIR version is released:

1. Check the available Docker tags:
```bash
curl -s "https://registry.hub.docker.com/v2/repositories/hapiproject/hapi/tags?page_size=10" \
  | python3 -m json.tool | grep '"name"'
```

2. Update the image tag in `docker-compose.yml`:
```yaml
image: "hapiproject/hapi:v9.x.x-1"   # not the -tomcat variant
```

3. Update the HAPI version in `pom.xml`:
```xml
<version>9.x.x</version>
```

4. Rebuild and redeploy:
```bash
mvn package
docker compose down
docker compose up -d
```

No Java code changes should be required for routine version bumps.

---

## Troubleshooting

**Container won't start**
```bash
docker compose logs fhir | tail -50
```

**Env vars not reaching the container**
- Ensure `.env` is in the same directory as `docker-compose.yml`
- No spaces around `=` in `.env` (use `KEY=value` not `KEY = value`)
- Verify with: `docker inspect fhir-server | grep -A 20 '"Env"'`

**401 from auth server**
- Double-check `ONTO_AUTH_URL` is the full token endpoint URL
- Verify `ONTO_CLIENT_ID` and `ONTO_CLIENT_SECRET` are correct

**ValueSet too large error**
- This comes from Ontoserver, not HAPI — the interceptor is working correctly
- Use a more specific SNOMED concept or add `count` parameter to limit results

**Rebuilding after code changes**
```bash
rm -rf target
mvn package
docker compose down
docker compose up -d
```
