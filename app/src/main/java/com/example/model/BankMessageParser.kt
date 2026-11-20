package com.example.model

import java.util.Locale
import kotlin.math.roundToLong

data class ParsedBankMessage(
    val merchant: String,
    val amountCents: Long,
    val paymentMethod: PaymentMethod,
    val cardLastFour: String,
    val suggestedCategory: ExpenseCategory,
    val confidence: CategorizationConfidence,
    val fingerprint: String,
    val sourceLabel: String
)

object BankMessageParser {

    // OTP / verification / login / promo messages that must NEVER be treated as charges
    private val ignoredKeywords = listOf(
        "verification code", "verify your", "security code", "one-time password", "one time password",
        "passcode", "2fa", "two-factor", "login attempt", "sign-in code", "authentication code",
        "do not share", "never share", "expires in", "otp ", " otp",
        "codigo de verificacion", "código de verificación", "clave dinamica", "clave dinámica",
        "no la compartas", "no compartas", "token de seguridad", "nip temporal",
        "declined", "rejected", "failed", "insufficient funds", "canceled", "cancelled",
        "rechazada", "declinada", "fondos insuficientes", "cancelada",
        "refund", "reimbursement", "reembolso", "devolucion", "devolución", "cashback credited",
        "promo", "off your next", "discount code", "apply now", "pre-approved", "preapproved"
    )

    // Strong transaction / charge indicators (English + Spanish)
    private val chargeActionKeywords = listOf(
        "charged", "charge of", "charge for", "purchase", "purchased", "spent", "paid",
        "payment", "debited", "debit of", "withdrawn", "withdrawal", "transaction",
        "pos ", "card used", "card ending", "authorized", "approved", "autopay", "auto-pay",
        "bought", "bill paid", "sent to", "transfer to", "wire transfer", "ach ",
        "compra", "compraste", "cargo", "cargado", "pago", "pagaste", "consumo",
        "retiro", "retiraste", "debito", "débito", "aprobada", "aprobado", "autorizada",
        "autorizado", "transferencia", "tarjeta terminacion", "tarjeta terminación", "cta ",
        "gasto", "cobro", "cobrado", "suscripcion", "suscripción", "domiciliacion", "domiciliación"
    )

    private val bankMethodKeywords = listOf(
        "transfer", "transferencia", "spei", "ach", "wire", "checking", "chequera",
        "savings", "cuenta ", "direct debit", "bill pay", "zelle", "clabe", "iban"
    )

    private val cardMethodKeywords = listOf(
        "card", "tarjeta", "visa", "mastercard", "amex", "american express",
        "credit", "credito", "crédito", "debit", "debito", "débito", "apple pay",
        "google pay", "google wallet", "tap to pay", "pos", "ending in", "terminacion",
        "terminación", "****"
    )

    private val currencyBeforeAmountRegex = Regex(
        """(?:US\$|MX\$|CA\$|AU\$|USD|MXN|EUR|GBP|CAD|AUD|COP|ARS|CLP|PEN|BRL|\$|€|£|¥|₹|₱|₩)\s*([0-9]{1,3}(?:[,\s][0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val amountBeforeCurrencyRegex = Regex(
        """\b([0-9]{1,3}(?:[,\s][0-9]{3})*(?:\.[0-9]{1,2})?|[0-9]+(?:[.,][0-9]{1,2})?)\s*(?:USD|MXN|EUR|GBP|CAD|AUD|COP|ARS|CLP|PEN|BRL|dollars|pesos|euros|bucks)\b""",
        RegexOption.IGNORE_CASE
    )

    private val contextualAmountRegex = Regex(
        """(?:of|for|por|de|monto[:\s]*|amount[:\s]*)\s*([0-9]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val lastFourRegexes = listOf(
        Regex("""(?:\*{2,}|x{2,}|•{2,})\s*([0-9]{4})(?![0-9\-:])\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:ending(?:\s+in)?|terminaci[oó]n(?:\s+en)?|final(?:es)?|tarjeta|card|acct|cuenta)\s*(?:#|no\.?|num\.?)?\s*[:\-]?\s*\*{0,4}([0-9]{4})(?![0-9\-:])\b""", RegexOption.IGNORE_CASE),
        Regex("""(?:cuenta|tarjeta|card|acct)\s+(?:[A-Za-z0-9._-]{2,20}\s+)\*{0,4}([0-9]{4})(?![0-9\-:])\b""", RegexOption.IGNORE_CASE)
    )

    private val merchantPatterns = listOf(
        // "at STARBUCKS STORE #123 on..." / "at Amazon.com."
        Regex("""\b(?:at|@)\s+([A-Za-z0-9ÁÉÍÓÚÑáéíóúñ&'.*\-_/ ]{2,45}?)(?=\s+(?:on|for|using|with|via|card|from|ending|date|ref|el|con|por|para|en\s+fecha|available|balance|saldo)\b|[,.;!\n\(]|$)""", RegexOption.IGNORE_CASE),
        // "en OXXO GAS" / "en AMAZON MX" / "en Mercado Libre el ..."
        Regex("""\ben\s+(?:comercio\s+|establecimiento\s+)?([A-Za-z0-9ÁÉÍÓÚÑáéíóúñ&'.*\-_/ ]{2,45}?)(?=\s+(?:el|con|por|para|fecha|tarjeta|terminaci[oó]n|saldo|autorizacion|autorizaci[oó]n|ref)\b|[,.;!\n\(]|$)""", RegexOption.IGNORE_CASE),
        // "to Spotify USA" / "a Netflix"
        Regex("""\b(?:to|toward|para|a)\s+([A-Za-z0-9ÁÉÍÓÚÑáéíóúñ&'.*\-_/ ]{2,42}?)(?=\s+(?:of|by|on|for|from|using|with|via|card|account|cuenta|el|con|por|ref)\b|[,.;!\n\(]|$)""", RegexOption.IGNORE_CASE),
        // "Merchant: Trader Joe's" / "Comercio: Uber Eats"
        Regex("""\b(?:merchant|comercio|establecimiento|beneficiario|payee)\s*[:\-]\s*([A-Za-z0-9ÁÉÍÓÚÑáéíóúñ&'.*\-_/ ]{2,45}?)(?=[,.;!\n\(]|$)""", RegexOption.IGNORE_CASE)
    )

    private val genericTitlePrefixes = setOf(
        "messages", "google messages", "samsung messages", "sms", "text message",
        "bank alert", "transaction alert", "card alert", "payment alert", "spend alert",
        "alerta bancaria", "notificacion", "notificación", "aviso", "cargo", "compra"
    )

    fun parse(
        title: String,
        body: String,
        sourceAppLabel: String = "",
        learnedHistory: Map<String, ExpenseCategory> = emptyMap(),
        nowMillis: Long = System.currentTimeMillis()
    ): ParsedBankMessage? {
        val cleanTitle = title.trim()
        val cleanBody = body.trim()
        val combined = listOf(cleanTitle, cleanBody).filter { it.isNotBlank() }.joinToString(" • ")
        if (combined.length < 6) return null

        val lower = combined.lowercase(Locale.ROOT)

        // 1. Immediately filter OTPs, 2FA codes, declined transactions, refunds, promos
        if (ignoredKeywords.any { lower.contains(it) }) {
            return null
        }

        // Skip obvious pure OTP digit patterns like "is 482910" without charge keywords
        val hasChargeSignal = chargeActionKeywords.any { lower.contains(it) }
        val hasCurrencySymbol = Regex("""[\$€£¥₹₱₩]|(?:USD|MXN|EUR|GBP|CAD)(?=\b|\s|[0-9])""", RegexOption.IGNORE_CASE)
            .containsMatchIn(combined)

        // Balance-only alerts (e.g., "Your balance is $1,240.00") without any charge keyword
        val isBalanceOnly = (lower.contains("balance is") || lower.contains("available balance") ||
            lower.contains("saldo actual") || lower.contains("saldo disponible")) && !hasChargeSignal
        if (isBalanceOnly) return null

        // Require either a charge keyword + amount OR currency + merchant/card pattern
        if (!hasChargeSignal && !hasCurrencySymbol) return null
        if (!hasChargeSignal) {
            // If only currency symbol is present, still require some financial context ("at ", "en ", "card", "tarjeta")
            val hasContext = lower.contains(" at ") || lower.contains(" en ") ||
                cardMethodKeywords.any { lower.contains(it) } ||
                bankMethodKeywords.any { lower.contains(it) }
            if (!hasContext) return null
        }

        // 2. Extract amount (ignoring numbers right next to card last-4 masks)
        val amountCents = extractAmountCents(combined) ?: return null
        if (amountCents <= 0L) return null

        // 3. Extract card last-4 digits
        val cardLastFour = extractLastFour(combined)

        // 4. Extract merchant name cleanly
        val merchant = extractMerchant(cleanTitle, cleanBody, sourceAppLabel, cardLastFour)

        // 5. Payment method (CARD vs BANK)
        val paymentMethod = inferPaymentMethod(lower, cardLastFour)

        // 6. Categorize merchant using SpendWise's categorizer
        val catResult = ExpenseCategorizer.analyze(
            description = merchant,
            amountCents = amountCents,
            learnedHistory = learnedHistory
        )
        val suggestedCategory = catResult.suggestedCategory
            ?: catResult.candidates.firstOrNull()
            ?: ExpenseCategory.OTHER

        // 7. Deduplication fingerprint bucketed into 5-minute windows
        val bucket5Min = nowMillis / 300_000L
        val normalizedMerchant = merchant.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "").take(16)
        val fingerprint = "$amountCents|$normalizedMerchant|$cardLastFour|$bucket5Min"

        val resolvedSource = when {
            sourceAppLabel.isNotBlank() -> sourceAppLabel
            cleanTitle.isNotBlank() -> cleanTitle.take(28)
            else -> "Bank Alert"
        }

        return ParsedBankMessage(
            merchant = merchant,
            amountCents = amountCents,
            paymentMethod = paymentMethod,
            cardLastFour = cardLastFour,
            suggestedCategory = suggestedCategory,
            confidence = catResult.confidence,
            fingerprint = fingerprint,
            sourceLabel = resolvedSource
        )
    }

    private fun extractAmountCents(text: String): Long? {
        // Mask card numbers & last-4 digits so we never treat "card 4821" as $4821.00
        val sanitized = text
            .replace(Regex("""(?:\*{2,}|x{2,}|•{2,})\s*[0-9]{4}\b""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""(?:ending(?:\s+in)?|terminaci[oó]n(?:\s+en)?|tarjeta|card)\s*(?:#|no\.?)?\s*[0-9]{4}\b""", RegexOption.IGNORE_CASE), " ")

        val match = currencyBeforeAmountRegex.find(sanitized)
            ?: amountBeforeCurrencyRegex.find(sanitized)
            ?: contextualAmountRegex.find(sanitized)
            ?: return null

        val rawNum = match.groupValues.getOrNull(1)?.trim() ?: return null
        return parseFlexibleAmountToCents(rawNum)
    }

    internal fun parseFlexibleAmountToCents(raw: String): Long? {
        val trimmed = raw.replace(" ", "")
        if (trimmed.isBlank()) return null

        val normalized = when {
            // Both comma and dot -> decide which is decimal separator by last occurrence
            trimmed.contains(',') && trimmed.contains('.') -> {
                if (trimmed.lastIndexOf(',') > trimmed.lastIndexOf('.')) {
                    // European: 1.250,50 -> 1250.50
                    trimmed.replace(".", "").replace(",", ".")
                } else {
                    // US/MX: 1,250.50 -> 1250.50
                    trimmed.replace(",", "")
                }
            }
            trimmed.contains(',') -> {
                val afterComma = trimmed.substringAfterLast(',')
                if (afterComma.length <= 2) {
                    // Decimal comma: 14,50 -> 14.50
                    trimmed.replace(",", ".")
                } else {
                    // Thousands comma: 1,250 -> 1250
                    trimmed.replace(",", "")
                }
            }
            else -> trimmed
        }

        val value = normalized.toDoubleOrNull() ?: return null
        if (value <= 0.0 || value > 1_000_000.0) return null
        return (value * 100.0).roundToLong()
    }

    private fun extractLastFour(text: String): String {
        for (regex in lastFourRegexes) {
            val match = regex.find(text)
            val digits = match?.groupValues?.getOrNull(1)
            if (!digits.isNullOrBlank() && digits.length == 4) {
                return digits
            }
        }
        return ""
    }

    private fun extractMerchant(
        title: String,
        body: String,
        sourceAppLabel: String,
        cardLastFour: String
    ): String {
        val searchSpace = if (body.isNotBlank()) body else title
        for (regex in merchantPatterns) {
            val match = regex.find(searchSpace) ?: continue
            val candidate = cleanMerchantCandidate(match.groupValues[1], cardLastFour)
            if (candidate.length >= 2 && !isGenericMerchantNoise(candidate)) {
                return candidate
            }
        }

        // Fallback: try title patterns too if body didn't have a clear preposition
        if (title.isNotBlank() && body.isNotBlank()) {
            for (regex in merchantPatterns) {
                val match = regex.find(title) ?: continue
                val candidate = cleanMerchantCandidate(match.groupValues[1], cardLastFour)
                if (candidate.length >= 2 && !isGenericMerchantNoise(candidate)) {
                    return candidate
                }
            }
        }

        // Second fallback: check if title itself is a meaningful bank/merchant label
        val cleanedTitle = cleanMerchantCandidate(title, cardLastFour)
        if (cleanedTitle.length >= 2 &&
            cleanedTitle.lowercase(Locale.ROOT) !in genericTitlePrefixes &&
            !cleanedTitle.contains("$") &&
            !cleanedTitle.matches(Regex("^[0-9+\\-()\\s]+$"))
        ) {
            return cleanedTitle.take(36)
        }

        return if (sourceAppLabel.isNotBlank() && sourceAppLabel.lowercase(Locale.ROOT) !in genericTitlePrefixes) {
            "$sourceAppLabel Charge"
        } else {
            "Bank Card Charge"
        }
    }

    private fun cleanMerchantCandidate(raw: String, cardLastFour: String): String {
        var cleaned = raw.trim()
        // Strip trailing currency/amount fragments or card digits
        cleaned = cleaned
            .replace(Regex("""\s+(?:US\$|MX\$|USD|MXN|EUR|\$|€|£)[0-9.,]+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(?:ending|terminaci[oó]n|card|tarjeta|\*{2,}).*$""", RegexOption.IGNORE_CASE), "")
        if (cardLastFour.isNotBlank()) {
            cleaned = cleaned.replace(cardLastFour, "")
        }
        cleaned = cleaned
            .trim(' ', '-', ':', '.', ',', '*', '#', '"', '\'')
            .replace(Regex("""\s{2,}"""), " ")

        return if (cleaned.length > 38) cleaned.take(38).trim() else cleaned
    }

    private fun isGenericMerchantNoise(candidate: String): Boolean {
        val lower = candidate.lowercase(Locale.ROOT)
        return lower in setOf(
            "your card", "tu tarjeta", "tu cuenta", "your account",
            "su tarjeta", "su cuenta", "the store", "la tienda",
            "merchant", "comercio", "card", "tarjeta", "account", "cuenta"
        ) || lower.all { it.isDigit() || it == '*' }
    }

    private fun inferPaymentMethod(lowerText: String, cardLastFour: String): PaymentMethod {
        val hasBank = bankMethodKeywords.any { lowerText.contains(it) }
        val hasCard = cardMethodKeywords.any { lowerText.contains(it) } || cardLastFour.isNotBlank()
        return when {
            hasCard -> PaymentMethod.CARD
            hasBank -> PaymentMethod.BANK
            else -> PaymentMethod.CARD
        }
    }
}
