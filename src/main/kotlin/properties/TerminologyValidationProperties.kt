@ConfigurationProperties(prefix = "terminology")
data class TerminologyValidationProperties(
    var url: String?,
    var authorization: Authorization?
) {
    data class Authorization(
        var tokenUrl: String,
        var clientId: String,
        var clientSecret: String
    )
}
