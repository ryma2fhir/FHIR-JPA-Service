@Configuration
class TerminologySupportConfig(
    private val terminologyValidationProperties: TerminologyValidationProperties
) {

    private val logger = KotlinLogging.logger {}

    @Bean
    open fun remoteTerminologyServiceValidationSupport(
        @Qualifier("R4") fhirContext: FhirContext,
        optionalAuthorizedClientManager: Optional<OAuth2AuthorizedClientManager>
    ): RemoteTerminologyServiceValidationSupport {
        logger.info { "Using remote terminology server at ${terminologyValidationProperties.url}" }

        val validationSupport = RemoteTerminologyServiceValidationSupport(fhirContext)
        validationSupport.setBaseUrl(terminologyValidationProperties.url)

        if (optionalAuthorizedClientManager.isPresent) {
            val accessTokenInterceptor = AccessTokenInterceptor(optionalAuthorizedClientManager.get())
            validationSupport.addClientInterceptor(accessTokenInterceptor)
        }

        return validationSupport
    }
}
