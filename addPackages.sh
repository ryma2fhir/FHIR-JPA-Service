#!/bin/bash
set -e

# Find all .tgz files inside /packages
PACKAGE_LIST=$(find /packages -type f -name "*.tgz" | paste -sd "," -)

# Export HAPI_FHIR_VALIDATION_PACKAGES dynamically
export HAPI_FHIR_VALIDATION_PACKAGES=$PACKAGE_LIST

echo "Detected FHIR packages: $HAPI_FHIR_VALIDATION_PACKAGES"

# Now start the original HAPI server
exec /docker-addPackages.sh