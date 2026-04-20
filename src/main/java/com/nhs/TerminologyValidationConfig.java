package com.nhs;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.validation.JpaValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.RemoteTerminologyServiceValidationSupport;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.ValueSetExpansionOptions;

import jakarta.annotation.PostConstruct;
import java.util.List;

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
            System.err.println("[TERM-VALIDATION] ONTO_SERVER_URL not set — skipping remote setup");
            return;
        }
        ontoServerUrl = ontoServerUrl.replaceAll("/$", "");
        System.out.println("[TERM-VALIDATION] Configuring remote terminology support → " + ontoServerUrl);

        // -----------------------------------------------------------------------
        // Step A: Remove the built-in InMemoryTerminologyServerValidationSupport
        // This is critical — without this, SNOMED codes get intercepted locally
        // and returned as "not found" before the remote server is ever consulted
        // -----------------------------------------------------------------------
        List<IValidationSupport> supports = jpaValidationSupportChain.getValidationSupports();
        supports.stream()
            .filter(s -> s instanceof InMemoryTerminologyServerValidationSupport)
            .findFirst()
            .ifPresent(found -> {
                jpaValidationSupportChain.removeValidationSupport(found);
                System.out.println("[TERM-VALIDATION] Removed built-in InMemoryTerminologyServerValidationSupport");
            });

        // -----------------------------------------------------------------------
        // Step B: Build a smart remote support that:
        //   - Only intercepts SNOMED / DMD / ICD code systems
        //   - Returns null for everything else (so the JPA chain handles it)
        //   - POSTs the resolved ValueSet body to Ontoserver (not just the URL)
        // -----------------------------------------------------------------------
        final String finalOntoServerUrl = ontoServerUrl;

        RemoteTerminologyServiceValidationSupport baseRemote =
            new RemoteTerminologyServiceValidationSupport(fhirContext, finalOntoServerUrl);
        baseRemote.addClientInterceptor(terminologyInterceptor);

        // Wrap it so it only activates for remote code systems
        IValidationSupport guardedRemote = new IValidationSupport() {

            private boolean isRemoteSystem(String system) {
                return system != null && (
                    system.startsWith("http://snomed.info/sct") ||
                    system.startsWith("https://dmd.nhs.uk")     ||
                    system.startsWith("http://read.info")        ||
                    system.startsWith("http://hl7.org/fhir/sid/icd")
                );
            }

            @Override
            public FhirContext getFhirContext() {
                return fhirContext;
            }

            @Override
            public String getName() {
                return "NHS Guarded Remote Terminology Support";
            }

            // Only claim to support code systems we own
            @Override
            public boolean isCodeSystemSupported(ValidationSupportContext ctx, String system) {
                if (!isRemoteSystem(system)) return false;
                return baseRemote.isCodeSystemSupported(ctx, system);
            }

            // Only claim to support ValueSets if the underlying system is remote
            @Override
            public boolean isValueSetSupported(ValidationSupportContext ctx, String valueSetUrl) {
                return false; // let JPA chain resolve ValueSets — we validate the codes inside them
            }

            // validateCode: only handle remote systems, return null otherwise (chain continues)
            @Override
            public CodeValidationResult validateCode(
                    ValidationSupportContext ctx,
                    ConceptValidationOptions options,
                    String system, String code, String display, String valueSetUrl) {
                if (!isRemoteSystem(system)) return null;
                System.out.printf("[REMOTE] validateCode system=%s code=%s%n", system, code);
                return baseRemote.validateCode(ctx, options, system, code, display, valueSetUrl);
            }

            // validateCodeInValueSet: only handle remote systems
            // HAPI passes the resolved ValueSet object here — so Ontoserver gets
            // the full ValueSet body, not just a URL it might not know
            @Override
            public CodeValidationResult validateCodeInValueSet(
                    ValidationSupportContext ctx,
                    ConceptValidationOptions options,
                    String system, String code, String display,
                    IBaseResource valueSet) {
                if (!isRemoteSystem(system)) return null;
                System.out.printf("[REMOTE] validateCodeInValueSet system=%s code=%s%n", system, code);
                return baseRemote.validateCodeInValueSet(ctx, options, system, code, display, valueSet);
            }

            // expandValueSet: only expand if the ValueSet contains remote systems
            @Override
            public ValueSetExpansionOutcome expandValueSet(
                    ValidationSupportContext ctx,
                    ValueSetExpansionOptions options,
                    IBaseResource valueSet) {
                if (valueSet instanceof org.hl7.fhir.r4.model.ValueSet vs) {
                    boolean hasRemoteSystem = vs.getCompose().getInclude().stream()
                        .anyMatch(i -> isRemoteSystem(i.getSystem()));
                    if (!hasRemoteSystem) return null; // let chain handle it
                }
                return baseRemote.expandValueSet(ctx, options, valueSet);
            }
        };

        // -----------------------------------------------------------------------
        // Step C: Add the guarded remote support at position 0
        // It returns null for non-remote systems so the JPA chain (position 1+)
        // naturally handles local ValueSets, CodeSystems, profiles etc.
        // -----------------------------------------------------------------------
        jpaValidationSupportChain.addValidationSupport(0, guardedRemote);
        System.out.println("[TERM-VALIDATION] Guarded remote support registered at position 0");
    }
}