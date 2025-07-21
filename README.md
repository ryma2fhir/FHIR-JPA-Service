# HAPI FHIR JPA Server with Custom Validation Packages

## How to Use

1. Ensure docker is installed using `docker --version`

2. Within the parent directory run
```bash
docker run -p 8080:8080 -v $(pwd)/FHIR-JPA-Service:/configs -e "--spring.config.location=file:///configs/hapi.application.yaml" hapiproject/hapi:latest
```

3. go to:
- http://localhost:8080 for the landing page
- http://localhost:8080/fhir for swagger

4. to add packages to hapi see `fhir.implementationguides` within [hapi.application.yaml](https://github.com/ryma2fhir/FHIR-JPA-Service/blob/main/hapi.application.yaml)

## Details
The docker container [hapiproject/hapi:latest](https://hub.docker.com/r/hapiproject/hapi) is the offical HL7 latest release of the [hapi-fhir-jpaserver-starter](https://github.com/hapifhir/hapi-fhir-jpaserver-starter).
The hapi.application.yaml is a copy of [application.yaml](https://github.com/hapifhir/hapi-fhir-jpaserver-starter/blob/master/src/main/resources/application.yaml) which has been modified to include the neccessary packages

`docker run` runs the docker container
`-p 8080:8080` Map port 8080 inside the container to port 8080 on your host (if 8090:8080, 8090 would be inside the container)
`$(pwd)/FHIR-JPA-Service:/configs` mount the local folder FHIR-JPA-Service at path /configs
`e "--spring.config.location=file:///configs/hapi.application.yaml"` set environment variable to use the custom config file hapi.application.yaml within the newly mounted local folder configs
`hapiproject/hapi:latest` use the latest hapi project docker image


