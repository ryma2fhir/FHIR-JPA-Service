# HAPI FHIR JPA Server with Custom Validation Packages

## How to Use

1. Place your FHIR NPM packages (`.tgz` files) into the `./packages/` folder.

2. make addPackages bash script executable. This adds all the packages in /packages to the JAP server.
- chmod +x addPackages.sh

3. Start the server:

```bash
docker-compose up -d
```
4. Go to http://localhost:8080/ or http://localhost:8080/fhir/

5. Stop the server
```bash
docker-compose down
```