package com.example.ai

import com.google.firebase.ai.type.APINotConfiguredException
import com.google.firebase.ai.type.ContentBlockedException
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.InvalidLocationException
import com.google.firebase.ai.type.PermissionMissingException
import com.google.firebase.ai.type.PromptBlockedException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.RequestTimeoutException
import com.google.firebase.ai.type.ResponseStoppedException
import com.google.firebase.ai.type.SerializationException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceConnectionHandshakeFailedException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnsupportedUserLocationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import org.json.JSONException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

enum class GeminiRequestType(val label: String) {
    TEXT("TEXT"),
    AUDIO("AUDIO")
}

enum class GeminiErrorCategory(val retryable: Boolean) {
    RATE_LIMITED_429(retryable = true),
    SERVICE_UNAVAILABLE_503(retryable = true),
    GATEWAY_TIMEOUT_504(retryable = true),
    SERVER_ERROR_5XX(retryable = true),
    CLIENT_TIMEOUT(retryable = true),
    NETWORK_ERROR(retryable = true),
    APP_CHECK_FAILURE(retryable = false),
    AUTH_OR_CONFIG_ERROR(retryable = false),
    UNSUPPORTED_LOCATION(retryable = false),
    RESPONSE_INVALID_OR_BLOCKED(retryable = false),
    UNKNOWN(retryable = false)
}

data class GeminiErrorDetails(
    val category: GeminiErrorCategory,
    val httpStatus: Int?,
    val exceptionClass: String,
    val rootCauseClass: String,
    val retryable: Boolean,
    val userMessage: String,
    val sanitizedDiagnostic: String
)

class GeminiParsingException(
    val details: GeminiErrorDetails,
    cause: Throwable? = null
) : IllegalStateException(details.userMessage, cause)

object GeminiErrorClassifier {

    private val HTTP_STATUS_REGEX = Regex(
        """(?i)(?:\bhttp\s*(?:status|code)?\b|\bstatus(?:\s*code)?\b|\bcode\b|\berror\b)?\s*[:=(#\[]?\b(400|401|403|404|408|413|429|500|502|503|504)\b"""
    )
    private val GOOGLE_API_KEY_REGEX = Regex("""AIza[0-9A-Za-z\-_]{10,}""")
    private val KEY_PARAM_REGEX = Regex("""(?i)([?&](?:key|api_key|token)=)[^&\s]+""")
    private val BEARER_TOKEN_REGEX = Regex("""(?i)(Bearer\s+)[A-Za-z0-9._\-]+""")

    fun classify(
        throwable: Throwable,
        requestType: GeminiRequestType,
        timeoutMs: Long,
        modelName: String
    ): GeminiErrorDetails {
        val chain = collectCauseChain(throwable)

        // Cooperative coroutine cancellation (non-timeout) must always propagate immediately.
        val externalCancel = chain.firstOrNull {
            it is CancellationException && it !is TimeoutCancellationException
        }
        if (externalCancel != null) {
            throw externalCancel
        }

        val exceptionClass = throwable::class.java.simpleName.ifBlank { throwable::class.java.name }
        val rootCause = chain.lastOrNull() ?: throwable
        val rootCauseClass = rootCause::class.java.simpleName.ifBlank { rootCause::class.java.name }
        val combinedMessage = chain.mapNotNull { it.message?.takeIf(String::isNotBlank) }.joinToString(" | ")
        val lower = combinedMessage.lowercase()
        val parsedHttpStatus = extractHttpStatus(chain, combinedMessage)

        val timeoutDisplay = formatDurationLabel(timeoutMs)

        // 1. HTTP 504 / Gateway timeout (check before generic timeout matching)
        if (
            parsedHttpStatus == 504 ||
            lower.contains("gateway timeout") ||
            lower.contains("deadline_exceeded") ||
            lower.contains("deadline exceeded")
        ) {
            return GeminiErrorDetails(
                category = GeminiErrorCategory.GATEWAY_TIMEOUT_504,
                httpStatus = parsedHttpStatus ?: 504,
                exceptionClass = exceptionClass,
                rootCauseClass = rootCauseClass,
                retryable = true,
                userMessage = "Gemini gateway timed out (504). Please try again in a moment.",
                sanitizedDiagnostic = sanitizeForLog(combinedMessage)
            )
        }

        // 2. HTTP 429 / Quota / Free-tier rate limits
        if (
            chain.any { it is QuotaExceededException } ||
            parsedHttpStatus == 429 ||
            lower.contains("resource_exhausted") ||
            lower.contains("rate limit") ||
            lower.contains("too many requests") ||
            lower.contains("quota")
        ) {
            return GeminiErrorDetails(
                category = GeminiErrorCategory.RATE_LIMITED_429,
                httpStatus = parsedHttpStatus ?: 429,
                exceptionClass = exceptionClass,
                rootCauseClass = rootCauseClass,
                retryable = true,
                userMessage = "Gemini free-tier rate limit or quota was reached (429). Wait a few seconds and try again.",
                sanitizedDiagnostic = sanitizeForLog(combinedMessage)
            )
        }

        // 3. HTTP 503 / Service Unavailable
        if (
            parsedHttpStatus == 503 ||
            lower.contains("service unavailable") ||
            lower.contains("overloaded") ||
            lower.contains("temporarily unavailable") ||
            (lower.contains("unavailable") && chain.any { it is ServerException })
        ) {
            return GeminiErrorDetails(
                category = GeminiErrorCategory.SERVICE_UNAVAILABLE_503,
                httpStatus = parsedHttpStatus ?: 503,
                exceptionClass = exceptionClass,
                rootCauseClass = rootCauseClass,
                retryable = true,
                userMessage = "Gemini service is temporarily busy or unavailable (503). Please try again shortly.",
                sanitizedDiagnostic = sanitizeForLog(combinedMessage)
            )
        }

        // 4. Other transient 5xx server failures (500 / 502)
        if (
            parsedHttpStatus == 500 ||
            parsedHttpStatus == 502 ||
            lower.contains("bad gateway") ||
            lower.contains("internal error encountered")
        ) {
            val code = parsedHttpStatus ?: 500
            return GeminiErrorDetails(
                category = GeminiErrorCategory.SERVER_ERROR_5XX,
                httpStatus = code,
                exceptionClass = exceptionClass,
                rootCauseClass = rootCauseClass,
                retryable = true,
                userMessage = "Gemini encountered a temporary server error ($code). Please try again.",
                sanitizedDiagnostic = sanitizeForLog(combinedMessage)
            )
        }

        // 5. Firebase App Check failures (403 / attestation / token rejection) — never retryable
        if (
            lower.contains("app check") ||
            lower.contains("appcheck") ||
            lower.contains("app_check") ||
            lower.contains("attestation") ||
            (parsedHttpStatus == 403 && lower.contains("token"))
        ) {
            val code = parsedHttpStatus ?: 403
            return GeminiErrorDetails(
                category = GeminiErrorCategory.APP_CHECK_FAILURE,
                httpStatus = code,
                exceptionClass = exceptionClass,
                rootCauseClass = rootCauseClass,
                retryable = false,
                userMessage = "Gemini request was rejected by Firebase App Check ($code). Launch from AI Studio with a debug token or verify App Check configuration in Firebase Console.",
                sanitizedDiagnostic = sanitizeForLog(combinedMessage)
            )
        }

        // 6. Authentication / API enablement / Configuration errors — never retryable
        if (
            chain.any {
                it is InvalidAPIKeyException ||
                    it is ServiceDisabledException ||
                    it is APINotConfiguredException ||
                    it is PermissionMissingException ||
                    it is InvalidLocationException ||
                    it is ServiceConnectionHandshakeFailedException
            } ||
            parsedHttpStatus == 401 ||
            parsedHttpStatus == 403 ||
            parsedHttpStatus == 404 ||
            lower.contains("api key not valid") ||
            lower.contains("genai config not found") ||
            lower.contains("service_disabled") ||
            lower.contains("permission_denied") ||
            lower.contains("unauthenticated") ||
            lower.contains("url not found")
        ) {
            val resolvedCode = parsedHttpStatus ?: when {
                chain.any { it is InvalidAPIKeyException } -> 401
                chain.any { it is PermissionMissingException || it is ServiceDisabledException || it is APINotConfiguredException } -> 403
                else -> null
            }
            val configMsg = when {
                chain.any { it is InvalidAPIKeyException } || resolvedCode == 401 || lower.contains("api key not valid") ->
                    "Gemini API authentication failed (401). Verify Firebase configuration (google-services.json) and API key restrictions."
                chain.any { it is ServiceDisabledException || it is APINotConfiguredException } ||
                    lower.contains("service_disabled") ||
                    lower.contains("genai config not found") ->
                    "Firebase AI Logic (Gemini Developer API) is not enabled or configured for this Firebase project."
                resolvedCode == 404 || lower.contains("url not found") ->
                    "Configured Gemini model ($modelName) or endpoint was not found (404)."
                else ->
                    "Gemini access was denied (${resolvedCode ?: 403}). Verify Firebase AI Logic permissions and App Check setup."
            }
            return GeminiErrorDetails(
                category = GeminiErrorCategory.AUTH_OR_CONFIG_ERROR,
                httpStatus = resolvedCode,
                exceptionClass = exceptionClass,
                rootCauseClass = rootCauseClass,
                retryable = false,
                userMessage = configMsg,
                sanitizedDiagnostic = sanitizeForLog(combinedMessage)
            )
        }

        // 7. Unsupported region/location — never retryable
        if (
            chain.any { it is UnsupportedUserLocationException } ||
            lower.contains("user location is not supported")
        ) {
            return GeminiErrorDetails(
                category = GeminiErrorCategory.UNSUPPORTED_LOCATION,
                httpStatus = parsedHttpStatus ?: 400,
                exceptionClass = exceptionClass,
                rootCauseClass = rootCauseClass,
                retryable = false,
                userMessage = "Gemini Developer API is not supported in your current region/location.",
                sanitizedDiagnostic = sanitizeForLog(combinedMessage)
            )
        }

        // 8. Client / Socket / SDK timeout (after checking 504)
        if (
            chain.any {
                it is TimeoutCancellationException ||
                    it is RequestTimeoutException ||
                    it is SocketTimeoutException
            } ||
            parsedHttpStatus == 408 ||
            lower.contains("timed out") ||
            lower.contains("timeout")
        ) {
            val msg = when (requestType) {
                GeminiRequestType.AUDIO ->
                    "Voice parsing timed out after $timeoutDisplay. Try a shorter recording or check your internet connection."
                GeminiRequestType.TEXT ->
                    "Gemini request timed out after $timeoutDisplay. Check your connection and try again."
            }
            return GeminiErrorDetails(
                category = GeminiErrorCategory.CLIENT_TIMEOUT,
                httpStatus = parsedHttpStatus ?: 408,
                exceptionClass = exceptionClass,
                rootCauseClass = rootCauseClass,
                retryable = true,
                userMessage = msg,
                sanitizedDiagnostic = sanitizeForLog(combinedMessage)
            )
        }

        // 9. Network interruption / DNS / Socket failure (including OkHttp IOException wrapped in UnknownException)
        if (
            chain.any {
                it is UnknownHostException ||
                    it is ConnectException ||
                    it is NoRouteToHostException ||
                    it is SSLException ||
                    it is SocketException ||
                    it is IOException
            } ||
            lower.contains("unable to resolve host") ||
            lower.contains("failed to connect") ||
            lower.contains("network is unreachable") ||
            lower.contains("connection reset") ||
            lower.contains("connection refused") ||
            lower.contains("connection aborted") ||
            lower.contains("broken pipe") ||
            lower.contains("unexpected end of stream") ||
            lower.contains("network")
        ) {
            return GeminiErrorDetails(
                category = GeminiErrorCategory.NETWORK_ERROR,
                httpStatus = parsedHttpStatus,
                exceptionClass = exceptionClass,
                rootCauseClass = rootCauseClass,
                retryable = true,
                userMessage = "Network connection to Gemini was interrupted. Check internet access and try again.",
                sanitizedDiagnostic = sanitizeForLog(combinedMessage)
            )
        }

        // 10. Safety block or invalid structured output — never retryable
        if (
            chain.any {
                it is PromptBlockedException ||
                    it is ContentBlockedException ||
                    it is ResponseStoppedException ||
                    it is SerializationException ||
                    it is JSONException
            }
        ) {
            val safeDiag = if (chain.any { it is JSONException }) {
                "json_parse_error"
            } else {
                sanitizeForLog(combinedMessage)
            }
            return GeminiErrorDetails(
                category = GeminiErrorCategory.RESPONSE_INVALID_OR_BLOCKED,
                httpStatus = parsedHttpStatus,
                exceptionClass = exceptionClass,
                rootCauseClass = rootCauseClass,
                retryable = false,
                userMessage = "Gemini could not turn that into an expense entry. Try stating the item and amount clearly.",
                sanitizedDiagnostic = safeDiag
            )
        }

        // 11. HTTP 400 or other unrecognized non-retryable failures
        val fallbackMessage = if (parsedHttpStatus == 400) {
            "Gemini rejected the request format (400). Please rephrase your expense and try again."
        } else {
            "Gemini could not process the expense right now. Please try again."
        }
        return GeminiErrorDetails(
            category = GeminiErrorCategory.UNKNOWN,
            httpStatus = parsedHttpStatus,
            exceptionClass = exceptionClass,
            rootCauseClass = rootCauseClass,
            retryable = false,
            userMessage = fallbackMessage,
            sanitizedDiagnostic = sanitizeForLog(combinedMessage)
        )
    }

    fun sanitizeForLog(raw: String): String {
        if (raw.isBlank()) return "none"
        return raw
            .replace(GOOGLE_API_KEY_REGEX, "[REDACTED_KEY]")
            .replace(KEY_PARAM_REGEX, "$1[REDACTED]")
            .replace(BEARER_TOKEN_REGEX, "$1[REDACTED_TOKEN]")
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .take(140)
    }

    private fun collectCauseChain(throwable: Throwable): List<Throwable> {
        val result = ArrayList<Throwable>(4)
        var current: Throwable? = throwable
        var depth = 0
        while (current != null && depth < 10 && !result.contains(current)) {
            result.add(current)
            current = current.cause
            depth++
        }
        return result
    }

    private fun extractHttpStatus(chain: List<Throwable>, combinedMessage: String): Int? {
        val match = HTTP_STATUS_REGEX.find(combinedMessage)
        if (match != null) {
            return match.groupValues.getOrNull(1)?.toIntOrNull()
        }
        return when {
            chain.any { it is QuotaExceededException } -> 429
            chain.any { it is RequestTimeoutException } -> 408
            chain.any { it is PermissionMissingException } -> 403
            chain.any { it is InvalidAPIKeyException } -> 401
            else -> null
        }
    }

    private fun formatDurationLabel(timeoutMs: Long): String {
        return if (timeoutMs >= 1_000L && timeoutMs % 1_000L == 0L) {
            "${timeoutMs / 1_000L}s"
        } else if (timeoutMs >= 1_000L) {
            String.format("%.1fs", timeoutMs / 1_000.0)
        } else {
            "${timeoutMs}ms"
        }
    }
}
