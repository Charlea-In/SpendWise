package com.example

import com.example.ai.GeminiDiagnosticLogger
import com.example.ai.GeminiErrorCategory
import com.example.ai.GeminiParserConfig
import com.example.ai.GeminiParsingException
import com.example.ai.GeminiTransport
import com.example.ai.GeminiExpenseParser
import com.example.model.ExpenseCategory
import com.example.model.PaymentMethod
import com.google.firebase.ai.type.InvalidAPIKeyException
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ServerException
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.UnknownException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.SocketException
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GeminiExpenseParserTest {

    private class RecordingLogger : GeminiDiagnosticLogger {
        val entries = mutableListOf<String>()
        override fun d(tag: String, message: String) {
            entries.add("D/$tag: $message")
        }

        override fun w(tag: String, message: String) {
            entries.add("W/$tag: $message")
        }

        override fun e(tag: String, message: String) {
            entries.add("E/$tag: $message")
        }
    }

    private inline fun <reified T : Throwable> newFirebaseException(
        message: String,
        cause: Throwable? = null
    ): T {
        return T::class.java
            .getConstructor(String::class.java, Throwable::class.java)
            .newInstance(message, cause)
    }

    private fun testConfig(
        textTimeoutMs: Long = 2_000L,
        audioTimeoutMs: Long = 4_000L,
        totalTextBudgetMs: Long = maxOf(10_000L, textTimeoutMs * 2),
        totalAudioBudgetMs: Long = maxOf(20_000L, audioTimeoutMs * 2),
        maxTransientRetries: Int = 2,
        maxRateLimitRetries: Int = 1,
        maxTimeoutRetries: Int = 1,
        initialBackoffMs: Long = 25L,
        rateLimitBackoffMs: Long = 50L
    ) = GeminiParserConfig(
        textTimeoutMs = textTimeoutMs,
        audioTimeoutMs = audioTimeoutMs,
        totalTextBudgetMs = totalTextBudgetMs,
        totalAudioBudgetMs = totalAudioBudgetMs,
        maxTransientRetries = maxTransientRetries,
        maxRateLimitRetries = maxRateLimitRetries,
        maxTimeoutRetries = maxTimeoutRetries,
        initialBackoffMs = initialBackoffMs,
        rateLimitBackoffMs = rateLimitBackoffMs,
        backoffMultiplier = 2.0,
        maxBackoffMs = 500L
    )

    @Test
    fun parsesShortSpanishTextExpenseAndUsesTextTimeout() = runBlocking {
        var observedTimeout = -1L
        val logger = RecordingLogger()
        val transport = object : GeminiTransport {
            override suspend fun generateTextExpenseJson(
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String {
                observedTimeout = timeoutMs
                assertTrue(prompt.contains("Café 3.50 en efectivo"))
                return """
                    {
                      "description": "Café",
                      "amountDollars": 3.50,
                      "category": "FOOD_DINING",
                      "accountName": "",
                      "paymentMethod": "CASH",
                      "note": "",
                      "confidence": "HIGH",
                      "clarificationHint": null,
                      "spokenTranscript": null
                    }
                """.trimIndent()
            }

            override suspend fun generateAudioExpenseJson(
                audioBytes: ByteArray,
                mimeType: String,
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String? = null
        }

        val parser = GeminiExpenseParser(
            config = testConfig(textTimeoutMs = 25_000L, audioTimeoutMs = 45_000L),
            transportOverride = transport,
            logger = logger
        )
        val result = parser.parseNaturalLanguageExpense("Café 3.50 en efectivo")
        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("Café", parsed.description)
        assertEquals(350L, parsed.amountCents)
        assertEquals(ExpenseCategory.FOOD_DINING, parsed.category)
        assertEquals(PaymentMethod.CASH, parsed.paymentMethod)
        assertTrue(parsed.isHighOrMediumConfidence)
        assertEquals(25_000L, observedTimeout)
        assertFalse(logger.entries.any { it.contains("Café") || it.contains("3.50") })
    }

    @Test
    fun parsesShortEnglishTextExpense() = runBlocking {
        val transport = object : GeminiTransport {
            override suspend fun generateTextExpenseJson(
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String {
                return """
                    {
                      "description": "Uber ride",
                      "amountDollars": 14.25,
                      "category": "TRANSPORTATION",
                      "accountName": "Personal",
                      "paymentMethod": "CARD",
                      "note": "",
                      "confidence": "HIGH",
                      "clarificationHint": null,
                      "spokenTranscript": null
                    }
                """.trimIndent()
            }

            override suspend fun generateAudioExpenseJson(
                audioBytes: ByteArray,
                mimeType: String,
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String? = null
        }

        val parser = GeminiExpenseParser(testConfig(), transportOverride = transport)
        val result = parser.parseNaturalLanguageExpense(
            userPrompt = "Uber ride $14.25 from Personal",
            existingAccounts = listOf("Personal", "Chequera")
        )
        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("Uber ride", parsed.description)
        assertEquals(1425L, parsed.amountCents)
        assertEquals(ExpenseCategory.TRANSPORTATION, parsed.category)
        assertEquals("Personal", parsed.accountName)
        assertEquals(PaymentMethod.CARD, parsed.paymentMethod)
    }

    @Test
    fun parsesLongerNaturalLanguageExpenseWithAccountHint() = runBlocking {
        var capturedPrompt = ""
        val longPrompt =
            "Ayer fui al Super Selectos y compré frutas, pan y leche por 42.80 dólares pagando con mi cuenta Chequera para la semana"
        val transport = object : GeminiTransport {
            override suspend fun generateTextExpenseJson(
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String {
                capturedPrompt = prompt
                return """
                    {
                      "description": "Super Selectos",
                      "amountDollars": 42.80,
                      "category": "GROCERIES",
                      "accountName": "Chequera",
                      "paymentMethod": "BANK",
                      "note": "Frutas, pan y leche para la semana",
                      "confidence": "HIGH",
                      "clarificationHint": null,
                      "spokenTranscript": null
                    }
                """.trimIndent()
            }

            override suspend fun generateAudioExpenseJson(
                audioBytes: ByteArray,
                mimeType: String,
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String? = null
        }

        val parser = GeminiExpenseParser(testConfig(), transportOverride = transport)
        val result = parser.parseNaturalLanguageExpense(longPrompt, listOf("Chequera", "Personal"))
        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals("Super Selectos", parsed.description)
        assertEquals(4280L, parsed.amountCents)
        assertEquals(ExpenseCategory.GROCERIES, parsed.category)
        assertEquals("Chequera", parsed.accountName)
        assertEquals(PaymentMethod.BANK, parsed.paymentMethod)
        assertTrue(capturedPrompt.contains("Chequera, Personal"))
    }

    @Test
    fun parsesShortVoiceRecordingWithAudioTimeoutConfig() = runBlocking {
        var observedAudioTimeout = -1L
        val shortClip = ByteArray(6_000) { 0x1A }
        val transport = object : GeminiTransport {
            override suspend fun generateTextExpenseJson(
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String? = null

            override suspend fun generateAudioExpenseJson(
                audioBytes: ByteArray,
                mimeType: String,
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String {
                observedAudioTimeout = timeoutMs
                assertEquals("audio/mp4", mimeType)
                assertEquals(6_000, audioBytes.size)
                return """
                    {
                      "description": "Churro - Local Store",
                      "amountDollars": 0.50,
                      "category": "FOOD_DINING",
                      "accountName": "Chequera",
                      "paymentMethod": "BANK",
                      "note": "",
                      "confidence": "HIGH",
                      "clarificationHint": null,
                      "spokenTranscript": "I bought a churro for 50 cents on Chequera"
                    }
                """.trimIndent()
            }
        }

        val parser = GeminiExpenseParser(
            config = testConfig(textTimeoutMs = 25_000L, audioTimeoutMs = 45_000L),
            transportOverride = transport
        )
        val result = parser.parseAudioExpense(shortClip, "audio/mp4", listOf("Chequera"))
        assertTrue(result.isSuccess)
        val parsed = result.getOrThrow()
        assertEquals(50L, parsed.amountCents)
        assertEquals("I bought a churro for 50 cents on Chequera", parsed.spokenTranscript)
        assertEquals(45_000L, observedAudioTimeout)
    }

    @Test
    fun parsesLongerVoiceRecordingAndRejectsOversizedAudio() = runBlocking {
        // ~55s mono AAC @ 32 kbps is ~220 KB, safely handled by audio timeout window
        val longClip = ByteArray(220_000) { 0x2F }
        val logger = RecordingLogger()
        val transport = object : GeminiTransport {
            override suspend fun generateTextExpenseJson(
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String? = null

            override suspend fun generateAudioExpenseJson(
                audioBytes: ByteArray,
                mimeType: String,
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String {
                assertEquals(220_000, audioBytes.size)
                return """
                    {
                      "description": "Farmacia San Nicolás",
                      "amountDollars": 28.75,
                      "category": "HEALTH",
                      "accountName": "Personal",
                      "paymentMethod": "CARD",
                      "note": "Receta médica",
                      "confidence": "HIGH",
                      "clarificationHint": null,
                      "spokenTranscript": "Gasté veintiocho con setenta y cinco en Farmacia San Nicolás con mi tarjeta personal"
                    }
                """.trimIndent()
            }
        }

        val parser = GeminiExpenseParser(testConfig(), transportOverride = transport, logger = logger)
        val okResult = parser.parseAudioExpense(longClip, "audio/mp4", listOf("Personal"))
        assertTrue(okResult.isSuccess)
        assertEquals(2875L, okResult.getOrThrow().amountCents)
        assertEquals(ExpenseCategory.HEALTH, okResult.getOrThrow().category)
        assertTrue(logger.entries.any { it.contains("audioBytes=220000") })

        val oversized = ByteArray(18 * 1024 * 1024 + 1)
        val tooBigResult = parser.parseAudioExpense(oversized)
        assertTrue(tooBigResult.isFailure)
        assertTrue(tooBigResult.exceptionOrNull()?.message?.contains("18 MB") == true)
    }

    @Test
    fun handles429QuotaExceededWithLimitedBackoffAndClearError() = runBlocking {
        var attempts = 0
        val sleepDurations = mutableListOf<Long>()
        val logger = RecordingLogger()
        val statusUpdates = mutableListOf<String>()

        val transport = object : GeminiTransport {
            override suspend fun generateTextExpenseJson(
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String {
                attempts++
                throw newFirebaseException<QuotaExceededException>(
                    "Quota exceeded: 429 RESOURCE_EXHAUSTED for gemini-3.8-flash"
                )
            }

            override suspend fun generateAudioExpenseJson(
                audioBytes: ByteArray,
                mimeType: String,
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String? = null
        }

        val parser = GeminiExpenseParser(
            config = testConfig(maxTransientRetries = 3, maxRateLimitRetries = 1, rateLimitBackoffMs = 300L),
            transportOverride = transport,
            logger = logger,
            sleepFn = { sleepDurations.add(it) }
        )

        val failure = parser.parseNaturalLanguageExpense(
            userPrompt = "Tacos 8.00",
            onStatusUpdate = { statusUpdates.add(it) }
        )

        assertTrue(failure.isFailure)
        // Must respect maxRateLimitRetries = 1 -> 2 total attempts, not maxTransientRetries = 3
        assertEquals(2, attempts)
        assertEquals(listOf(300L), sleepDurations)
        assertTrue(statusUpdates.single().contains("429"))

        val ex = failure.exceptionOrNull() as GeminiParsingException
        assertEquals(GeminiErrorCategory.RATE_LIMITED_429, ex.details.category)
        assertEquals(429, ex.details.httpStatus)
        assertTrue(ex.message.orEmpty().contains("429"))
        assertTrue(logger.entries.any { it.contains("httpStatus=429") && it.contains("RATE_LIMITED_429") })
    }

    @Test
    fun retries503And504WithExponentialBackoffAndNeverRetriesAppCheckOrAuth() = runBlocking {
        // Part A: 503 fails twice then succeeds on 3rd attempt with exponential backoff (100ms, 200ms)
        var attempts503 = 0
        val sleeps503 = mutableListOf<Long>()
        val recovering503Transport = object : GeminiTransport {
            override suspend fun generateTextExpenseJson(
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String {
                attempts503++
                if (attempts503 <= 2) {
                    throw newFirebaseException<ServerException>(
                        "HTTP 503 Service Unavailable: backend temporarily overloaded"
                    )
                }
                return """
                    {
                      "description": "Almuerzo",
                      "amountDollars": 9.00,
                      "category": "FOOD_DINING",
                      "accountName": "",
                      "paymentMethod": "CASH",
                      "note": "",
                      "confidence": "HIGH",
                      "clarificationHint": null,
                      "spokenTranscript": null
                    }
                """.trimIndent()
            }

            override suspend fun generateAudioExpenseJson(
                audioBytes: ByteArray,
                mimeType: String,
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String? = null
        }

        val parser503 = GeminiExpenseParser(
            config = testConfig(maxTransientRetries = 2, initialBackoffMs = 100L),
            transportOverride = recovering503Transport,
            sleepFn = { sleeps503.add(it) }
        )
        val recovered = parser503.parseNaturalLanguageExpense("Almuerzo 9 dolares")
        assertTrue(recovered.isSuccess)
        assertEquals(3, attempts503)
        assertEquals(listOf(100L, 200L), sleeps503)

        // Part B: 504 gateway timeout exhausts bounded retries cleanly with 504 category
        var attempts504 = 0
        val sleeps504 = mutableListOf<Long>()
        val persistent504Transport = object : GeminiTransport {
            override suspend fun generateTextExpenseJson(
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String {
                attempts504++
                throw newFirebaseException<ServerException>("HTTP 504 Gateway Timeout DEADLINE_EXCEEDED")
            }

            override suspend fun generateAudioExpenseJson(
                audioBytes: ByteArray,
                mimeType: String,
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String? = null
        }
        val parser504 = GeminiExpenseParser(
            config = testConfig(maxTransientRetries = 2, initialBackoffMs = 50L),
            transportOverride = persistent504Transport,
            sleepFn = { sleeps504.add(it) }
        )
        val failed504 = parser504.parseNaturalLanguageExpense("Coffee 4")
        assertTrue(failed504.isFailure)
        assertEquals(3, attempts504)
        assertEquals(listOf(50L, 100L), sleeps504)
        val ex504 = failed504.exceptionOrNull() as GeminiParsingException
        assertEquals(GeminiErrorCategory.GATEWAY_TIMEOUT_504, ex504.details.category)
        assertEquals(504, ex504.details.httpStatus)

        // Part C: App Check 403 and InvalidAPIKey / ServiceDisabled MUST NOT retry
        val nonRetryExceptions = listOf(
            newFirebaseException<ServerException>("HTTP 403 Firebase App Check token is invalid."),
            newFirebaseException<InvalidAPIKeyException>("API key not valid for AIzaSyFAKEKEYVALUE1234567890"),
            newFirebaseException<ServiceDisabledException>("Vertex AI in Firebase API SERVICE_DISABLED")
        )
        val logger = RecordingLogger()
        for (nonRetryable in nonRetryExceptions) {
            var count = 0
            val parserNoRetry = GeminiExpenseParser(
                config = testConfig(maxTransientRetries = 3),
                transportOverride = object : GeminiTransport {
                    override suspend fun generateTextExpenseJson(
                        prompt: String,
                        systemInstruction: String,
                        timeoutMs: Long
                    ): String {
                        count++
                        throw nonRetryable
                    }

                    override suspend fun generateAudioExpenseJson(
                        audioBytes: ByteArray,
                        mimeType: String,
                        prompt: String,
                        systemInstruction: String,
                        timeoutMs: Long
                    ): String? = null
                },
                logger = logger
            )
            val failRes = parserNoRetry.parseNaturalLanguageExpense("Pan 1.50")
            assertTrue(failRes.isFailure)
            assertEquals("Non-retryable error should stop after 1 attempt", 1, count)
        }
        assertFalse(
            "API keys must be redacted from logs",
            logger.entries.any { it.contains("AIzaSyFAKEKEYVALUE") }
        )
        assertTrue(logger.entries.any { it.contains("[REDACTED_KEY]") })
    }

    @Test
    fun handlesGenuineClientSideTimeoutAndPreservesCoroutineCancellation() = runBlocking {
        val logger = RecordingLogger()
        var timeoutAttempts = 0
        val hangingTransport = object : GeminiTransport {
            override suspend fun generateTextExpenseJson(
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String {
                timeoutAttempts++
                delay(5_000L)
                return "{}"
            }

            override suspend fun generateAudioExpenseJson(
                audioBytes: ByteArray,
                mimeType: String,
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String? = null
        }

        // Genuine client-side withTimeout triggers at 80ms while transport stalls 5,000ms
        val parser = GeminiExpenseParser(
            config = testConfig(
                textTimeoutMs = 80L,
                totalTextBudgetMs = 250L,
                maxTimeoutRetries = 1,
                initialBackoffMs = 10L
            ),
            transportOverride = hangingTransport,
            logger = logger
        )

        val timeoutResult = parser.parseNaturalLanguageExpense("Dinner 20")
        assertTrue(timeoutResult.isFailure)
        val ex = timeoutResult.exceptionOrNull() as GeminiParsingException
        assertEquals(GeminiErrorCategory.CLIENT_TIMEOUT, ex.details.category)
        assertEquals(408, ex.details.httpStatus)
        assertTrue(timeoutAttempts in 1..2)
        assertTrue(ex.message.orEmpty().contains("timed out"))
        assertTrue(logger.entries.any { it.contains("TimeoutCancellationException") })

        // Cooperative external CancellationException must propagate directly without retry
        var cancelAttempts = 0
        val cancelParser = GeminiExpenseParser(
            config = testConfig(textTimeoutMs = 5_000L, totalTextBudgetMs = 10_000L),
            transportOverride = object : GeminiTransport {
                override suspend fun generateTextExpenseJson(
                    prompt: String,
                    systemInstruction: String,
                    timeoutMs: Long
                ): String {
                    cancelAttempts++
                    delay(5_000L)
                    return "{}"
                }

                override suspend fun generateAudioExpenseJson(
                    audioBytes: ByteArray,
                    mimeType: String,
                    prompt: String,
                    systemInstruction: String,
                    timeoutMs: Long
                ): String? = null
            }
        )

        val job = async {
            cancelParser.parseNaturalLanguageExpense("Cancel test 10")
        }
        delay(30L)
        job.cancel(CancellationException("User left screen"))
        var caughtCancel = false
        try {
            job.await()
            fail("Expected CancellationException to propagate")
        } catch (_: CancellationException) {
            caughtCancel = true
        }
        assertTrue(caughtCancel)
        assertEquals(1, cancelAttempts)
    }

    @Test
    fun handlesNetworkInterruptionAndNeverLogsSensitiveFinancialData() = runBlocking {
        var attempts = 0
        val logger = RecordingLogger()
        val sensitivePrompt = "Secret merchant Super7 account Chequera card 7708 total 99.45"

        val transport = object : GeminiTransport {
            override suspend fun generateTextExpenseJson(
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String {
                attempts++
                if (attempts == 1) {
                    // Simulate how FirebaseAIException wraps OkHttp IOException inside UnknownException
                    throw newFirebaseException<UnknownException>(
                        "Something unexpected happened.",
                        UnknownHostException("Unable to resolve host \"firebasevertexai.googleapis.com\"")
                    )
                }
                throw IOException("Network interruption: socket reset", SocketException("Connection reset"))
            }

            override suspend fun generateAudioExpenseJson(
                audioBytes: ByteArray,
                mimeType: String,
                prompt: String,
                systemInstruction: String,
                timeoutMs: Long
            ): String? = null
        }

        val parser = GeminiExpenseParser(
            config = testConfig(maxTransientRetries = 2, initialBackoffMs = 10L),
            transportOverride = transport,
            logger = logger
        )

        val res = parser.parseNaturalLanguageExpense(sensitivePrompt, listOf("Chequera"))
        assertTrue(res.isFailure)
        assertEquals(3, attempts)

        val ex = res.exceptionOrNull() as GeminiParsingException
        assertEquals(GeminiErrorCategory.NETWORK_ERROR, ex.details.category)
        assertTrue(ex.message.orEmpty().contains("Network connection to Gemini was interrupted"))
        assertNotNull(ex.details.rootCauseClass)

        val allLogs = logger.entries.joinToString("\n")
        assertTrue(allLogs.contains("model=gemini-3.8-flash"))
        assertTrue(allLogs.contains("type=TEXT"))
        assertTrue(allLogs.contains("category=NETWORK_ERROR"))
        assertFalse("Prompt text must never appear in logs", allLogs.contains("Super7"))
        assertFalse("Account name must never appear in logs", allLogs.contains("Chequera"))
        assertFalse("Amount must never appear in logs", allLogs.contains("99.45"))
        assertFalse("Card digits must never appear in logs", allLogs.contains("7708"))
    }
}
