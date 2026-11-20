package com.example.ai

import android.util.Log
import com.example.model.ExpenseCategory
import com.example.model.PaymentMethod
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.RequestOptions
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.pow
import kotlin.math.roundToLong

private const val TAG = "GeminiExpenseParser"

data class ParsedNaturalExpense(
    val description: String,
    val amountCents: Long,
    val category: ExpenseCategory,
    val accountName: String,
    val paymentMethod: PaymentMethod,
    val note: String,
    val isHighOrMediumConfidence: Boolean,
    val clarificationHint: String? = null,
    val spokenTranscript: String? = null
)

data class GeminiParserConfig(
    val modelName: String = DEFAULT_MODEL_NAME,
    val providerName: String = PROVIDER_GEMINI_DEVELOPER_API,
    val textTimeoutMs: Long = 45_000L,
    val audioTimeoutMs: Long = 60_000L,
    val totalTextBudgetMs: Long = 65_000L,
    val totalAudioBudgetMs: Long = 95_000L,
    val maxTransientRetries: Int = 2,
    val maxRateLimitRetries: Int = 1,
    val maxTimeoutRetries: Int = 0,
    val initialBackoffMs: Long = 1_000L,
    val rateLimitBackoffMs: Long = 2_500L,
    val backoffMultiplier: Double = 2.0,
    val maxBackoffMs: Long = 8_000L
) {
    companion object {
        const val DEFAULT_MODEL_NAME = "gemini-3.8-flash"
        const val PROVIDER_GEMINI_DEVELOPER_API = "googleAI"
        const val MAX_AUDIO_BYTES = 18 * 1024 * 1024
    }
}

interface GeminiDiagnosticLogger {
    fun d(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String)
}

object AndroidGeminiDiagnosticLogger : GeminiDiagnosticLogger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    override fun e(tag: String, message: String) {
        Log.e(tag, message)
    }
}

interface GeminiTransport {
    suspend fun generateTextExpenseJson(
        prompt: String,
        systemInstruction: String,
        timeoutMs: Long
    ): String?

    suspend fun generateAudioExpenseJson(
        audioBytes: ByteArray,
        mimeType: String,
        prompt: String,
        systemInstruction: String,
        timeoutMs: Long
    ): String?
}

private class FirebaseGeminiTransport(
    private val config: GeminiParserConfig,
    private val expenseSchema: Schema,
    private val systemInstructionText: String
) : GeminiTransport {

    private fun buildModel(attemptTimeoutMs: Long) =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = config.modelName,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = expenseSchema
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.LOW
                }
            },
            systemInstruction = content {
                text(systemInstructionText)
            },
            requestOptions = RequestOptions(attemptTimeoutMs)
        )

    private val textModel by lazy { buildModel(config.textTimeoutMs) }
    private val audioModel by lazy { buildModel(config.audioTimeoutMs) }

    override suspend fun generateTextExpenseJson(
        prompt: String,
        systemInstruction: String,
        timeoutMs: Long
    ): String? = withContext(Dispatchers.IO) {
        val model = if (timeoutMs == config.textTimeoutMs) textModel else buildModel(timeoutMs)
        val buffer = StringBuilder()
        model.generateContentStream(prompt).collect { chunk ->
            chunk.text?.let { buffer.append(it) }
        }
        buffer.toString().ifBlank { null }
    }

    override suspend fun generateAudioExpenseJson(
        audioBytes: ByteArray,
        mimeType: String,
        prompt: String,
        systemInstruction: String,
        timeoutMs: Long
    ): String? = withContext(Dispatchers.IO) {
        val model = if (timeoutMs == config.audioTimeoutMs) audioModel else buildModel(timeoutMs)
        val buffer = StringBuilder()
        model.generateContentStream(
            content {
                inlineData(audioBytes, mimeType)
                text(prompt)
            }
        ).collect { chunk ->
            chunk.text?.let { buffer.append(it) }
        }
        buffer.toString().ifBlank { null }
    }
}

class GeminiExpenseParser internal constructor(
    private val config: GeminiParserConfig,
    transportOverride: GeminiTransport?,
    private val logger: GeminiDiagnosticLogger = AndroidGeminiDiagnosticLogger,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val sleepFn: suspend (Long) -> Unit = { ms -> delay(ms) }
) {
    constructor(config: GeminiParserConfig = GeminiParserConfig()) : this(
        config = config,
        transportOverride = null,
        logger = AndroidGeminiDiagnosticLogger
    )

    private val expenseSchema: Schema = Schema.obj(
        properties = mapOf(
            "description" to Schema.string(
                description = "Concise merchant or item description (e.g., 'Churro - Local Store', 'Trader Joe\\'s')"
            ),
            "amountDollars" to Schema.double(
                description = "Expense amount in main currency units (e.g., 0.50 for '0.50 cents' or '50 cents', 14.50 for '14 dollars and 50 cents'). Return 0 if no amount is mentioned."
            ),
            "category" to Schema.enumeration(
                values = ExpenseCategory.entries.map { it.name },
                description = "Best matching expense category from the allowed list"
            ),
            "accountName" to Schema.string(
                description = "Exact custom account name mentioned by the user (in Title Case, or matching one of their existing accounts), or empty string if no account is mentioned",
                nullable = true
            ),
            "paymentMethod" to Schema.enumeration(
                values = PaymentMethod.entries.map { it.name },
                description = "Payment method inferred from text (use BANK for checking/chequera/bank transfer, CASH for cash, WALLET for digital wallet, CARD for card/default)"
            ),
            "note" to Schema.string(
                description = "Brief extra detail if mentioned (e.g., store or context), empty string if none",
                nullable = true
            ),
            "confidence" to Schema.enumeration(
                values = listOf("HIGH", "MEDIUM", "LOW"),
                description = "LOW if amount is missing/zero, speech is silent/unintelligible, or the request is not an expense; MEDIUM or HIGH otherwise"
            ),
            "clarificationHint" to Schema.string(
                description = "Short helpful question if confidence is LOW, null otherwise",
                nullable = true
            ),
            "spokenTranscript" to Schema.string(
                description = "Clean transcription of what the user spoke in audio input, or null for text input",
                nullable = true
            )
        ),
        optionalProperties = listOf("accountName", "note", "clarificationHint", "spokenTranscript")
    )

    private val transport: GeminiTransport by lazy {
        transportOverride ?: FirebaseGeminiTransport(config, expenseSchema, SYSTEM_INSTRUCTION_TEXT)
    }

    suspend fun parseNaturalLanguageExpense(
        userPrompt: String,
        existingAccounts: List<String> = emptyList(),
        onStatusUpdate: ((String) -> Unit)? = null
    ): Result<ParsedNaturalExpense> {
        val trimmed = userPrompt.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Please describe an expense first."))
        }
        val accountsHint = if (existingAccounts.isNotEmpty()) {
            "\nUser's custom accounts: ${existingAccounts.joinToString(", ")}"
        } else ""

        return executeParsingWithRetries(
            requestType = GeminiRequestType.TEXT,
            fallbackLabel = trimmed.take(40),
            attemptTimeoutMs = config.textTimeoutMs,
            totalBudgetMs = config.totalTextBudgetMs,
            payloadMetadata = "promptChars=${trimmed.length} accountHints=${existingAccounts.size}",
            onStatusUpdate = onStatusUpdate
        ) { effectiveTimeoutMs ->
            transport.generateTextExpenseJson(
                prompt = "$trimmed$accountsHint",
                systemInstruction = SYSTEM_INSTRUCTION_TEXT,
                timeoutMs = effectiveTimeoutMs
            )
        }
    }

    suspend fun parseAudioExpense(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4",
        existingAccounts: List<String> = emptyList(),
        onStatusUpdate: ((String) -> Unit)? = null
    ): Result<ParsedNaturalExpense> {
        if (audioBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Recorded audio was empty. Please try again."))
        }
        if (audioBytes.size > GeminiParserConfig.MAX_AUDIO_BYTES) {
            return Result.failure(IllegalArgumentException("Voice note exceeds 18 MB limit. Please record a shorter clip."))
        }
        val accountsHint = if (existingAccounts.isNotEmpty()) {
            " User's custom accounts: ${existingAccounts.joinToString(", ")}."
        } else ""

        val audioPrompt =
            "Listen to this spoken expense recording and extract description, amountDollars, category, accountName, paymentMethod, note, confidence, clarificationHint, and spokenTranscript.$accountsHint"

        return executeParsingWithRetries(
            requestType = GeminiRequestType.AUDIO,
            fallbackLabel = "Voice expense",
            attemptTimeoutMs = config.audioTimeoutMs,
            totalBudgetMs = config.totalAudioBudgetMs,
            payloadMetadata = "audioBytes=${audioBytes.size} mime=$mimeType accountHints=${existingAccounts.size}",
            onStatusUpdate = onStatusUpdate
        ) { effectiveTimeoutMs ->
            transport.generateAudioExpenseJson(
                audioBytes = audioBytes,
                mimeType = mimeType,
                prompt = audioPrompt,
                systemInstruction = SYSTEM_INSTRUCTION_TEXT,
                timeoutMs = effectiveTimeoutMs
            )
        }
    }

    private suspend fun executeParsingWithRetries(
        requestType: GeminiRequestType,
        fallbackLabel: String,
        attemptTimeoutMs: Long,
        totalBudgetMs: Long,
        payloadMetadata: String,
        onStatusUpdate: ((String) -> Unit)?,
        invokeTransport: suspend (effectiveTimeoutMs: Long) -> String?
    ): Result<ParsedNaturalExpense> {
        val overallStartMs = clockMs()
        val maxOverallAttempts = 1 + maxOf(
            config.maxTransientRetries,
            config.maxRateLimitRetries,
            config.maxTimeoutRetries
        ).coerceAtLeast(0)

        var attempt = 1
        while (true) {
            val elapsedBeforeAttempt = (clockMs() - overallStartMs).coerceAtLeast(0L)
            val remainingBudgetMs = totalBudgetMs - elapsedBeforeAttempt
            val effectiveTimeoutMs = minOf(attemptTimeoutMs, remainingBudgetMs).coerceAtLeast(1L)

            logger.d(
                TAG,
                "requestStart type=${requestType.label} model=${config.modelName} provider=${config.providerName} " +
                    "attempt=$attempt/$maxOverallAttempts timeoutMs=$effectiveTimeoutMs $payloadMetadata"
            )

            val attemptStartMs = clockMs()
            try {
                val rawJson = withTimeout(effectiveTimeoutMs) {
                    invokeTransport(effectiveTimeoutMs)
                }
                if (rawJson.isNullOrBlank()) {
                    throw JSONException("empty_response_body")
                }

                val parsed = parseStructuredJson(rawJson, fallbackLabel)
                val attemptDurationMs = (clockMs() - attemptStartMs).coerceAtLeast(0L)
                val totalDurationMs = (clockMs() - overallStartMs).coerceAtLeast(0L)
                logger.d(
                    TAG,
                    "requestSuccess type=${requestType.label} model=${config.modelName} provider=${config.providerName} " +
                        "attempts=$attempt attemptDurationMs=$attemptDurationMs totalDurationMs=$totalDurationMs " +
                        "confident=${parsed.isHighOrMediumConfidence} positiveAmount=${parsed.amountCents > 0L} " +
                        "category=${parsed.category.name} hasAccount=${parsed.accountName.isNotBlank()}"
                )
                return Result.success(parsed)
            } catch (t: Throwable) {
                // GeminiErrorClassifier rethrows cooperative (non-timeout) CancellationException immediately.
                val details = GeminiErrorClassifier.classify(
                    throwable = t,
                    requestType = requestType,
                    timeoutMs = effectiveTimeoutMs,
                    modelName = config.modelName
                )

                val attemptDurationMs = (clockMs() - attemptStartMs).coerceAtLeast(0L)
                val totalDurationMs = (clockMs() - overallStartMs).coerceAtLeast(0L)
                val allowedRetriesForError = maxRetriesFor(details.category)
                val retriesUsedSoFar = attempt - 1
                val backoffMs = computeBackoffMs(details.category, attempt)
                val budgetAfterBackoff = totalBudgetMs - (totalDurationMs + backoffMs)
                val minViableAttemptMs = minOf(attemptTimeoutMs, 250L)

                val willRetry = details.retryable &&
                    retriesUsedSoFar < allowedRetriesForError &&
                    budgetAfterBackoff >= minViableAttemptMs

                if (willRetry) {
                    logger.w(
                        TAG,
                        "requestFailure type=${requestType.label} model=${config.modelName} provider=${config.providerName} " +
                            "attempt=$attempt durationMs=$attemptDurationMs exception=${details.exceptionClass} " +
                            "rootCause=${details.rootCauseClass} httpStatus=${details.httpStatus ?: "none"} " +
                            "category=${details.category.name} willRetry=true backoffMs=$backoffMs " +
                            "detail=${details.sanitizedDiagnostic}"
                    )
                    onStatusUpdate?.invoke(buildRetryStatusText(details, attempt + 1, allowedRetriesForError + 1))
                    if (backoffMs > 0L) {
                        sleepFn(backoffMs)
                    }
                    attempt++
                } else {
                    logger.e(
                        TAG,
                        "requestFailure type=${requestType.label} model=${config.modelName} provider=${config.providerName} " +
                            "attempts=$attempt totalDurationMs=$totalDurationMs exception=${details.exceptionClass} " +
                            "rootCause=${details.rootCauseClass} httpStatus=${details.httpStatus ?: "none"} " +
                            "category=${details.category.name} willRetry=false " +
                            "detail=${details.sanitizedDiagnostic}"
                    )
                    return Result.failure(GeminiParsingException(details, t))
                }
            }
        }
    }

    private fun parseStructuredJson(jsonText: String, fallbackLabel: String): ParsedNaturalExpense {
        val json = JSONObject(jsonText)
        val description = json.optString("description", "").trim().ifBlank { fallbackLabel }
        val amountDollars = json.optDouble("amountDollars", 0.0)
        val amountCents = if (amountDollars.isNaN() || amountDollars <= 0.0) {
            0L
        } else {
            (amountDollars * 100.0).roundToLong()
        }
        val category = ExpenseCategory.fromKey(json.optString("category", "OTHER"))
        val rawAccount = json.optString("accountName", "").trim()
        val accountName = if (rawAccount.isBlank() || rawAccount.equals("null", ignoreCase = true)) {
            ""
        } else {
            rawAccount
        }
        val paymentMethod = PaymentMethod.fromKey(json.optString("paymentMethod", "CARD"))
        val rawNote = json.optString("note", "").trim()
        val note = if (rawNote.equals("null", ignoreCase = true)) "" else rawNote
        val confidence = json.optString("confidence", "MEDIUM").uppercase()
        val rawHint = json.optString("clarificationHint", "").trim()
        val hint = if (rawHint.isBlank() || rawHint.equals("null", ignoreCase = true)) null else rawHint
        val rawTranscript = json.optString("spokenTranscript", "").trim()
        val transcript = if (rawTranscript.isBlank() || rawTranscript.equals("null", ignoreCase = true)) null else rawTranscript

        val isConfident = confidence != "LOW" && amountCents > 0L
        return ParsedNaturalExpense(
            description = description,
            amountCents = amountCents,
            category = category,
            accountName = accountName,
            paymentMethod = paymentMethod,
            note = note,
            isHighOrMediumConfidence = isConfident,
            clarificationHint = hint,
            spokenTranscript = transcript
        )
    }

    private fun maxRetriesFor(category: GeminiErrorCategory): Int {
        return when (category) {
            GeminiErrorCategory.RATE_LIMITED_429 -> config.maxRateLimitRetries.coerceAtLeast(0)
            GeminiErrorCategory.CLIENT_TIMEOUT -> config.maxTimeoutRetries.coerceAtLeast(0)
            GeminiErrorCategory.SERVICE_UNAVAILABLE_503,
            GeminiErrorCategory.GATEWAY_TIMEOUT_504,
            GeminiErrorCategory.SERVER_ERROR_5XX,
            GeminiErrorCategory.NETWORK_ERROR -> config.maxTransientRetries.coerceAtLeast(0)
            GeminiErrorCategory.APP_CHECK_FAILURE,
            GeminiErrorCategory.AUTH_OR_CONFIG_ERROR,
            GeminiErrorCategory.UNSUPPORTED_LOCATION,
            GeminiErrorCategory.RESPONSE_INVALID_OR_BLOCKED,
            GeminiErrorCategory.UNKNOWN -> 0
        }
    }

    private fun computeBackoffMs(category: GeminiErrorCategory, failedAttempt: Int): Long {
        val base = when (category) {
            GeminiErrorCategory.RATE_LIMITED_429 -> config.rateLimitBackoffMs
            else -> config.initialBackoffMs
        }.coerceAtLeast(0L)
        if (base == 0L) return 0L
        val exponent = (failedAttempt - 1).coerceAtLeast(0)
        val rawDelay = base * config.backoffMultiplier.pow(exponent.toDouble())
        return rawDelay.roundToLong().coerceAtMost(config.maxBackoffMs.coerceAtLeast(base))
    }

    private fun buildRetryStatusText(
        details: GeminiErrorDetails,
        nextAttempt: Int,
        maxAttemptsForCategory: Int
    ): String {
        val reason = when (details.category) {
            GeminiErrorCategory.RATE_LIMITED_429 -> "Gemini rate limit (429)"
            GeminiErrorCategory.SERVICE_UNAVAILABLE_503 -> "Gemini service busy (503)"
            GeminiErrorCategory.GATEWAY_TIMEOUT_504 -> "Gemini gateway timeout (504)"
            GeminiErrorCategory.SERVER_ERROR_5XX -> "Temporary server error"
            GeminiErrorCategory.CLIENT_TIMEOUT -> "Connection timed out"
            GeminiErrorCategory.NETWORK_ERROR -> "Network hiccup"
            else -> "Temporary error"
        }
        return "$reason — retrying ($nextAttempt/$maxAttemptsForCategory)..."
    }

    private companion object {
        val SYSTEM_INSTRUCTION_TEXT = """
            You extract structured daily expense entries from casual natural-language text or voice recordings (English, Spanish, or Spanglish).
            Rules:
            - Common colloquial phrasing: '0.50 cents' or '50 cents' both mean 0.50 in dollars/main currency (amountDollars = 0.50).
            - If an account name is mentioned, match it to the user's custom accounts if provided, or preserve the spoken/written name in Title Case. If NO account is mentioned, return empty string "".
            - Match food/snacks like churro, coffee, tacos, lunch -> FOOD_DINING; supermarket items -> GROCERIES; bills -> UTILITIES; uber/gas/bus -> TRANSPORTATION; movies/games -> ENTERTAINMENT; rent/home -> HOUSING; pharmacy/gym -> HEALTH; clothes/electronics -> SHOPPING; unclear -> OTHER.
            - Only mark confidence as LOW if no valid positive amount can be found, the audio is silent/unintelligible, or the request is not an expense.
        """.trimIndent()
    }
}
