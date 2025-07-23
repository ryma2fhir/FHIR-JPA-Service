class AccessTokenInterceptor(
    private val authorizedClientManager: OAuth2AuthorizedClientManager
) : IClientInterceptor {

    override fun interceptRequest(theRequest: IHttpRequest) {
        val clientRequest = OAuth2AuthorizeRequest.withClientRegistrationId("terminology")
            .principal("system")  // can be any string, used for tracking
            .build()

        val authorizedClient = authorizedClientManager.authorize(clientRequest)
            ?: throw IllegalStateException("Failed to authorize client")

        val token = authorizedClient.accessToken.tokenValue
        theRequest.addHeader("Authorization", "Bearer $token")
    }

    override fun interceptResponse(theResponse: IHttpResponse) {
        // No-op
    }
}
