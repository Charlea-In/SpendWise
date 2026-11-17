package com.example.ai

import android.util.Log
import com.example.model.ExpenseCategory
import com.example.model.PaymentMethod
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
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

class GeminiExpenseParser {

    private val expenseSchema = Schema.obj(
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

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = expenseSchema
                temperature = 0.1f
            },
            systemInstruction = content {
                text(
                    """
                    You extract structured daily expense entries from casual natural-language text or voice recordings (English, Spanish, or Spanglish).
                    Rules:
                    - Common colloquial phrasing: '0.50 cents' or '50 cents' both mean 0.50 in dollars/main currency (amountDollars = 0.50).
                    - If an account name is mentioned, match it to the user's custom accounts if provided, or preserve the spoken/written name in Title Case. If NO account is mentioned, return empty string "".
                    - Match food/snacks like churro, coffee, tacos, lunch -> FOOD_DINING; supermarket items -> GROCERIES; bills -> UTILITIES; uber/gas/bus -> TRANSPORTATION; movies/games -> ENTERTAINMENT; rent/home -> HOUSING; pharmacy/gym -> HEALTH; clothes/electronics -> SHOPPING; unclear -> OTHER.
                    - Only mark confidence as LOW if no valid positive amount can be found, the audio is silent/unintelligible, or the request is not an expense.
                    """.trimIndent()
                )
            }
        )
    }

    suspend fun parseNaturalLanguageExpense(
        userPrompt: String,
        existingAccounts: List<String> = emptyList()
    ): Result<ParsedNaturalExpense> {
        val trimmed = userPrompt.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Please describe an expense first."))
        }
        val accountsHint = if (existingAccounts.isNotEmpty()) {
            "\nUser's custom accounts: ${existingAccounts.joinToString(", ")}"
        } else ""

        return executeParsing(fallbackLabel = trimmed.take(40)) {
            model.generateContent("$trimmed$accountsHint")
        }
    }

    suspend fun parseAudioExpense(
        audioBytes: ByteArray,
        mimeType: String = "audio/mp4",
        existingAccounts: List<String> = emptyList()
    ): Result<ParsedNaturalExpense> {
        if (audioBytes.isEmpty()) {
            return Result.failure(IllegalArgumentException("Recorded audio was empty. Please try again."))
        }
        val maxBytes = 18 * 1024 * 1024
        if (audioBytes.size > maxBytes) {
            return Result.failure(IllegalArgumentException("Voice note exceeds 18 MB limit. Please record a shorter clip."))
        }
        val accountsHint = if (existingAccounts.isNotEmpty()) {
            " User's custom accounts: ${existingAccounts.joinToString(", ")}."
        } else ""

        return executeParsing(fallbackLabel = "Voice expense") {
            model.generateContent(
                content {
                    inlineData(audioBytes, mimeType)
                    text(
                        "Listen to this spoken expense recording and extract description, amountDollars, category, accountName, paymentMethod, note, confidence, clarificationHint, and spokenTranscript.$accountsHint"
                    )
                }
            )
        }
    }

    private suspend fun executeParsing(
        fallbackLabel: String,
        call: suspend () -> com.google.firebase.ai.type.GenerateContentResponse
    ): Result<ParsedNaturalExpense> {
        return try {
            val response = withTimeout(60_000L) {
                call()
            }
            val jsonText = response.text
                ?: return Result.failure(IllegalStateException("Gemini returned an empty response."))

            val json = JSONObject(jsonText)
            val description = json.optString("description", "").trim().ifBlank { fallbackLabel }
            val amountDollars = json.optDouble("amountDollars", 0.0)
            val amountCents = if (amountDollars.isNaN() || amountDollars <= 0.0) 0L else (amountDollars * 100.0).roundToLong()
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

            Result.success(
                ParsedNaturalExpense(
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
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini parsing timed out", e)
            Result.failure(IllegalStateException("Gemini request timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini expense parsing failed", e)
            Result.failure(IllegalStateException(e.localizedMessage ?: "Failed to parse expense with Gemini."))
        }
    }
}
