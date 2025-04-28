# HAPI FHIR JPA Server with Custom Validation Packages

## How to Use

1. Place your FHIR NPM packages (`.tgz` files) into the `./packages/` folder.

2. Edit `docker-compose.yml`:
   - Update the `HAPI_FHIR_VALIDATION_PACKAGES` environment variable to list your packages.
   - Example: `/packages/hl7.fhir.uk.core-1.0.0.tgz,/packages/hl7.fhir.r4.core-4.0.1.tgz`

3. Start the server:

```bash
docker-compose up -d