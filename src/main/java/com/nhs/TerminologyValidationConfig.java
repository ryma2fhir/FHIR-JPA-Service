package com.nhs;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.validation.JpaValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.RemoteTerminologyServiceValidationSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.function.Predicate;

@Configuration
public class TerminologyValidationConfig {

    @Autowired
    private TerminologyInterceptor terminologyInterceptor;

    @Autowired
    private FhirContext fhirContext;

    @Autowired
    private JpaValidationSupportChain jpaValidationSupportChain;

    @PostConstruct
    public void registerRemoteTerminologySupport() {
        String ontoServerUrl = System.getenv("ONTO_SERVER_URL");
        if (ontoServerUrl == null || ontoServerUrl.isBlank()) {
            System.err.println("[TERM-VALIDATION] ONTO_SERVER_URL not set — skipping");
            return;
        }

        System.out.println("[TERM-VALIDATION] Setting up SwitchedTerminologyServiceValidationSupport → " + ontoServerUrl);

        // Remote support — calls Ontoserver with OAuth2 token for SNOMED/DMD/ICD
        RemoteTerminologyServiceValidationSupport remoteTermSvc =
                new RemoteTerminologyServiceValidationSupport(fhirContext, ontoServerUrl);
        remoteTermSvc.addClientInterceptor(terminologyInterceptor);

        // Local in-memory support for non-SNOMED code systems
        InMemoryTerminologyServerValidationSupport localSupport =
                new InMemoryTerminologyServerValidationSupport(fhirContext);

        // Route SNOMED, DMD and ICD codes to Ontoserver; everything else stays local
        Predicate<String> useRemote = system ->
                system != null && (
                        system.startsWith("http://snomed.info/sct") ||
                        system.startsWith("https://dmd.nhs.uk") ||
                        system.startsWith("http://read.info") ||
                        system.startsWith("http://hl7.org/fhir/sid/icd")
                );

        SwitchedTerminologyServiceValidationSupport switched =
                new SwitchedTerminologyServiceValidationSupport(
                        fhirContext, localSupport, remoteTermSvc, useRemote);

        // Add at position 0 so it takes precedence over HAPI's built-in
        // InMemoryTerminologyServerValidationSupport which would otherwise
        // intercept SNOMED codes and return "not-present" before we get a chance
        jpaValidationSupportChain.addValidationSupport(0, switched);

        System.out.println("[TERM-VALIDATION] SwitchedTerminologyServiceValidationSupport registered at position 0");
    }
}