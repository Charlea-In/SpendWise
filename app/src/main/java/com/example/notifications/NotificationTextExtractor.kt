package com.example.notifications

import android.app.Notification
import android.app.Person
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import java.util.Locale

data class NotificationExtractionMetadata(
    val hasTitle: Boolean = false,
    val titleLength: Int = 0,
    val hasConversationTitle: Boolean = false,
    val conversationTitleLength: Int = 0,
    val hasText: Boolean = false,
    val textLength: Int = 0,
    val hasBigText: Boolean = false,
    val bigTextLength: Int = 0,
    val hasSubText: Boolean = false,
    val subTextLength: Int = 0,
    val extraMessagesCount: Int = 0,
    val textLinesCount: Int = 0,
    val candidateCount: Int = 0
)

data class ExtractedNotificationMessage(
    val title: String,
    val body: String
)

data class NotificationExtractionResult(
    val title: String,
    val candidates: List<ExtractedNotificationMessage>,
    val metadata: NotificationExtractionMetadata
) {
    val candidateBodies: List<String>
        get() = candidates.map { it.body }
}

sealed class BankNotificationFilterDecision {
    data object Allow : BankNotificationFilterDecision()
    data class Ignore(val reason: String) : BankNotificationFilterDecision()
}

object BankNotificationFilter {
    private val systemPackages = setOf("android", "com.android.systemui")

    fun evaluatePreExtraction(
        packageName: String?,
        ownPackageName: String,
        hasNotification: Boolean,
        flags: Int,
        hasExtras: Boolean
    ): BankNotificationFilterDecision {
        val pkg = packageName?.trim().orEmpty()
        if (pkg.isBlank()) {
            return BankNotificationFilterDecision.Ignore("MISSING_PACKAGE")
        }
        if (pkg == ownPackageName) {
            return BankNotificationFilterDecision.Ignore("OWN_APP")
        }
        if (pkg in systemPackages) {
            return BankNotificationFilterDecision.Ignore("SYSTEM_PACKAGE")
        }
        if (!hasNotification) {
            return BankNotificationFilterDecision.Ignore("NULL_NOTIFICATION")
        }
        if ((flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            return BankNotificationFilterDecision.Ignore("GROUP_SUMMARY")
        }
        if ((flags and Notification.FLAG_ONGOING_EVENT) != 0) {
            return BankNotificationFilterDecision.Ignore("ONGOING_EVENT")
        }
        if (!hasExtras) {
            return BankNotificationFilterDecision.Ignore("NULL_EXTRAS")
        }
        return BankNotificationFilterDecision.Allow
    }

    fun evaluatePostExtraction(result: NotificationExtractionResult): BankNotificationFilterDecision {
        if (result.title.isBlank() && result.candidates.isEmpty()) {
            return BankNotificationFilterDecision.Ignore("EMPTY_CONTENT")
        }
        return BankNotificationFilterDecision.Allow
    }
}

object NotificationTextExtractor {

    private val summaryOnlyCountRegex = Regex(
        """^\d+\s+(?:new\s+)?(?:messages?|unread\s+messages?|mensajes(?:\s+nuevos)?)$""",
        RegexOption.IGNORE_CASE
    )

    fun extract(extras: Bundle?): NotificationExtractionResult {
        if (extras == null) {
            return NotificationExtractionResult(
                title = "",
                candidates = emptyList(),
                metadata = NotificationExtractionMetadata()
            )
        }

        val rawTitle = safeGetCharSequence(extras, Notification.EXTRA_TITLE)
        val conversationTitle = safeGetCharSequence(extras, Notification.EXTRA_CONVERSATION_TITLE)
        val bigText = safeGetCharSequence(extras, Notification.EXTRA_BIG_TEXT)
        val normalText = safeGetCharSequence(extras, Notification.EXTRA_TEXT)
        val subText = safeGetCharSequence(extras, Notification.EXTRA_SUB_TEXT)
        val messagingEntries = extractMessagingEntries(extras)
        val textLines = extractTextLines(extras)

        val resolvedTitle = rawTitle.ifBlank { conversationTitle }

        val candidates = mutableListOf<ExtractedNotificationMessage>()
        val seenNormalized = LinkedHashSet<String>()

        fun addCandidateIfUnique(candidateTitle: String, rawBody: String) {
            val cleanBody = rawBody.trim()
            if (cleanBody.isBlank()) return
            val normalized = normalizeForDedup(cleanBody)
            if (normalized in seenNormalized) return

            // Skip truncated EXTRA_TEXT previews (ending in "..." or "…") already covered by fuller text
            val strippedEllipsis = normalized.removeSuffix("...").removeSuffix("…").trim()
            if (strippedEllipsis.length >= 12 && seenNormalized.any { it.contains(strippedEllipsis) }) {
                return
            }

            // Skip generic summary counters ("2 new messages") if we already extracted real messages
            if (candidates.isNotEmpty() && summaryOnlyCountRegex.matches(cleanBody)) {
                return
            }

            seenNormalized.add(normalized)
            candidates.add(
                ExtractedNotificationMessage(
                    title = candidateTitle.ifBlank { resolvedTitle },
                    body = cleanBody
                )
            )
        }

        // 1. MessagingStyle messages (Google Messages and modern chat/SMS apps), newest first
        for (entry in messagingEntries) {
            val entryTitle = resolvedTitle.ifBlank { entry.sender }
            addCandidateIfUnique(entryTitle, entry.text)
        }

        // 2. Expanded BigTextStyle body (optionally preserving distinct subText context)
        if (bigText.isNotBlank()) {
            val combinedBigText = combinePrimaryWithSubText(bigText, subText)
            addCandidateIfUnique(resolvedTitle, combinedBigText)
        }

        // 3. Standard collapsed text (skip if duplicated/covered by EXTRA_MESSAGES or EXTRA_BIG_TEXT)
        if (normalText.isNotBlank()) {
            val combinedNormal = combinePrimaryWithSubText(normalText, subText)
            val normalNorm = normalizeForDedup(normalText)
            val alreadyCoveredWithoutSubText = normalNorm in seenNormalized ||
                seenNormalized.any {
                    val stripped = normalNorm.removeSuffix("...").removeSuffix("…").trim()
                    stripped.length >= 12 && it.contains(stripped)
                }
            if (!alreadyCoveredWithoutSubText) {
                addCandidateIfUnique(resolvedTitle, combinedNormal)
            }
        }

        // 4. InboxStyle lines, newest first
        for (line in textLines) {
            addCandidateIfUnique(resolvedTitle, line)
        }

        // 5. Fallback if notification only populated subText or title-only
        if (candidates.isEmpty() && subText.isNotBlank()) {
            addCandidateIfUnique(resolvedTitle, subText)
        }

        val metadata = NotificationExtractionMetadata(
            hasTitle = rawTitle.isNotBlank(),
            titleLength = rawTitle.length,
            hasConversationTitle = conversationTitle.isNotBlank(),
            conversationTitleLength = conversationTitle.length,
            hasText = normalText.isNotBlank(),
            textLength = normalText.length,
            hasBigText = bigText.isNotBlank(),
            bigTextLength = bigText.length,
            hasSubText = subText.isNotBlank(),
            subTextLength = subText.length,
            extraMessagesCount = messagingEntries.size,
            textLinesCount = textLines.size,
            candidateCount = candidates.size
        )

        return NotificationExtractionResult(
            title = resolvedTitle,
            candidates = candidates,
            metadata = metadata
        )
    }

    private fun combinePrimaryWithSubText(primary: String, subText: String): String {
        if (subText.isBlank()) return primary
        if (primary.contains(subText, ignoreCase = true)) return primary
        return "$primary • $subText"
    }

    private fun safeGetCharSequence(extras: Bundle, key: String): String {
        return try {
            extras.getCharSequence(key)?.toString()?.trim().orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private data class MessagingEntry(
        val text: String,
        val sender: String,
        val timestamp: Long,
        val index: Int
    )

    private fun extractMessagingEntries(extras: Bundle): List<MessagingEntry> {
        val rawArray: Array<out Parcelable>? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
            } else {
                @Suppress("DEPRECATION")
                extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            }
        } catch (_: Throwable) {
            null
        }
        if (rawArray.isNullOrEmpty()) return emptyList()

        val entries = mutableListOf<MessagingEntry>()
        rawArray.forEachIndexed { index, parcelable ->
            val bundle = parcelable as? Bundle ?: return@forEachIndexed
            val text = try {
                bundle.getCharSequence("text")?.toString()?.trim().orEmpty()
            } catch (_: Throwable) {
                ""
            }
            if (text.isBlank()) return@forEachIndexed

            val sender = extractSenderName(bundle)
            val timestamp = try {
                bundle.getLong("time", 0L)
            } catch (_: Throwable) {
                0L
            }
            entries.add(
                MessagingEntry(
                    text = text,
                    sender = sender,
                    timestamp = timestamp,
                    index = index
                )
            )
        }

        // Order newest first: if distinct positive timestamps exist, sort descending by time then reverse index;
        // otherwise preserve reverse chronological insertion order.
        val hasTimestamps = entries.any { it.timestamp > 0L }
        return if (hasTimestamps) {
            entries.sortedWith(
                compareByDescending<MessagingEntry> { it.timestamp }
                    .thenByDescending { it.index }
            )
        } else {
            entries.asReversed()
        }
    }

    private fun extractSenderName(bundle: Bundle): String {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val person = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bundle.getParcelable("sender_person", Person::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    bundle.getParcelable<Person>("sender_person")
                }
                val personName = person?.name?.toString()?.trim().orEmpty()
                if (personName.isNotBlank()) return personName
            }
        } catch (_: Throwable) {
            // Ignore malformed sender_person and fallback to legacy sender key
        }
        return try {
            bundle.getCharSequence("sender")?.toString()?.trim().orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun extractTextLines(extras: Bundle): List<String> {
        val lines = try {
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        } catch (_: Throwable) {
            null
        } ?: return emptyList()

        return lines
            .mapNotNull { it?.toString()?.trim()?.takeIf { line -> line.isNotBlank() } }
            .asReversed()
    }

    private fun normalizeForDedup(text: String): String {
        return text.trim().replace(Regex("""\s+"""), " ").lowercase(Locale.ROOT)
    }
}
