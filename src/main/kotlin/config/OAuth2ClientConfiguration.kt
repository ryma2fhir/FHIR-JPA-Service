@Configuration
open class OAuth2ClientConfiguration(
    private val terminologyValidationProperties: TerminologyValidationProperties
) {

    companion object {
        const val REGISTRATION_ID = "terminology"
    }

    @Bean
    open fun clientRegistration(): ClientRegistration {
        val auth = terminologyValidationProperties.authorization
            ?: throw IllegalArgumentException("Missing authorization block in application.yaml")

        return ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientId(auth.clientId)
            .clientSecret(auth.clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
            .tokenUri(auth.tokenUrl)
            .build()
    }

    @Bean
    open fun clientRegistrationRepository(): ClientRegistrationRepository =
        InMemoryClientRegistrationRepository(clientRegistration())

    @Bean
    open fun authorizedClientService(
        clientRegistrationRepository: ClientRegistrationRepository
    ): OAuth2AuthorizedClientService =
        InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository)

    @Bean
    open fun authorizedClientManager(
        clientRegistrationRepository: ClientRegistrationRepository,
        authorizedClientService: OAuth2AuthorizedClientService
    ): OAuth2AuthorizedClientManager {
        val provider = AuthorizedClientProviderBuilder.builder()
            .clientCredentials()
            .build()
        val manager = DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, authorizedClientService)
        manager.setAuthorizedClientProvider(provider)
        return manager
    }
}
