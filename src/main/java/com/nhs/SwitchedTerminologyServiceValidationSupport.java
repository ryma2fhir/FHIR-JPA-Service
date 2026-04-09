package com.nhs;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.context.support.ValueSetExpansionOptions;
import org.hl7.fhir.instance.model.api.IBaseResource;

import java.util.function.Predicate;

/**
 * Routes terminology validation to either a remote server or local in-memory support
 * based on the code system URL. SNOMED, DMD, and ICD codes go to Ontoserver;
 * everything else uses the local in-memory support.
 *
 * Mirrors SwitchedTerminologyServiceValidationSupport from the NHS FHIR-Validation project.
 */
public class SwitchedTerminologyServiceValidationSupport implements IValidationSupport {

    private final FhirContext fhirContext;
    private final IValidationSupport localSupport;
    private final IValidationSupport remoteSupport;
    private final Predicate<String> useRemotePredicate;

    public SwitchedTerminologyServiceValidationSupport(
            FhirContext fhirContext,
            IValidationSupport localSupport,
            IValidationSupport remoteSupport,
            Predicate<String> useRemotePredicate) {
        this.fhirContext = fhirContext;
        this.localSupport = localSupport;
        this.remoteSupport = remoteSupport;
        this.useRemotePredicate = useRemotePredicate;
    }

    private IValidationSupport selectSupport(String system) {
        if (system != null && useRemotePredicate.test(system)) {
            return remoteSupport;
        }
        return localSupport;
    }

    @Override
    public FhirContext getFhirContext() {
        return fhirContext;
    }

    @Override
    public String getName() {
        return "NHS Switched Terminology Validation Support";
    }

    @Override
    public boolean isCodeSystemSupported(ValidationSupportContext context, String system) {
        return selectSupport(system).isCodeSystemSupported(context, system);
    }

    @Override
    public boolean isValueSetSupported(ValidationSupportContext context, String valueSetUrl) {
        return remoteSupport.isValueSetSupported(context, valueSetUrl)
                || localSupport.isValueSetSupported(context, valueSetUrl);
    }

    @Override
    public IValidationSupport.CodeValidationResult validateCode(
            ValidationSupportContext context,
            ConceptValidationOptions options,
            String system,
            String code,
            String display,
            String valueSetUrl) {
        return selectSupport(system).validateCode(context, options, system, code, display, valueSetUrl);
    }

    @Override
    public IValidationSupport.CodeValidationResult validateCodeInValueSet(
            ValidationSupportContext context,
            ConceptValidationOptions options,
            String system,
            String code,
            String display,
            IBaseResource valueSet) {
        return selectSupport(system).validateCodeInValueSet(context, options, system, code, display, valueSet);
    }

    @Override
    public IValidationSupport.ValueSetExpansionOutcome expandValueSet(
            ValidationSupportContext context,
            ValueSetExpansionOptions options,
            IBaseResource valueSet) {
        IValidationSupport.ValueSetExpansionOutcome result = remoteSupport.expandValueSet(context, options, valueSet);
        if (result == null) {
            result = localSupport.expandValueSet(context, options, valueSet);
        }
        return result;
    }
}