package com.example.model

enum class CategorizationConfidence {
    HIGH,
    MEDIUM,
    UNSURE
}

data class CategorizationResult(
    val suggestedCategory: ExpenseCategory?,
    val confidence: CategorizationConfidence,
    val candidates: List<ExpenseCategory>,
    val reason: String
)

object ExpenseCategorizer {

    private data class KeywordRule(
        val category: ExpenseCategory,
        val strongKeywords: Set<String>,
        val softKeywords: Set<String>
    )

    private val rules = listOf(
        KeywordRule(
            category = ExpenseCategory.GROCERIES,
            strongKeywords = setOf(
                "trader joe", "whole foods", "kroger", "safeway", "aldi", "lidl",
                "publix", "wegmans", "sprouts", "heb", "h-e-b", "supermarket",
                "grocery", "groceries", "farmer market", "farmers market", "produce",
                "bakery", "butcher", "milk", "eggs", "bread", "vegetables", "fruit",
                "costco", "sam's club", "sams club", "fresh market", "pantry"
            ),
            softKeywords = setOf(
                "market", "mart", "foodmart", "organic", "deli"
            )
        ),
        KeywordRule(
            category = ExpenseCategory.FOOD_DINING,
            strongKeywords = setOf(
                "starbucks", "peet's", "peets", "blue bottle", "dunkin", "cafe",
                "coffee", "espresso", "latte", "boba", "tea house", "restaurant",
                "bistro", "diner", "chipotle", "sweetgreen", "panera", "mcdonald",
                "burger", "pizza", "sushi", "ramen", "taco", "thai", "pho",
                "doordash", "ubereats", "uber eats", "grubhub", "postmates",
                "breakfast", "lunch", "dinner", "brunch", "snack", "ice cream",
                "bar", "pub", "brewery", "takeout", "delivery food"
            ),
            softKeywords = setOf(
                "tea", "cake", "sandwich", "salad", "noodle", "kitchen", "grill",
                "eat", "meal"
            )
        ),
        KeywordRule(
            category = ExpenseCategory.HOUSING,
            strongKeywords = setOf(
                "rent", "mortgage", "lease", "landlord", "hoa", "apartment",
                "condo fee", "property tax", "home insurance", "renters insurance",
                "maintenance", "plumber", "electrician", "repair", "ikea", "home depot",
                "lowe's", "lowes", "furniture", "mattress"
            ),
            softKeywords = setOf(
                "housing", "home", "apt", "room", "deposit", "cleaning"
            )
        ),
        KeywordRule(
            category = ExpenseCategory.UTILITIES,
            strongKeywords = setOf(
                "electric", "electricity", "pg&e", "pge", "coned", "power bill",
                "water bill", "sewer", "trash", "waste", "natural gas", "heating",
                "internet", "wifi", "wi-fi", "fiber", "comcast", "xfinity",
                "spectrum", "att", "at&t", "verizon", "t-mobile", "tmobile",
                "phone bill", "mobile plan", "cell bill", "utility", "utilities"
            ),
            softKeywords = setOf(
                "power", "water", "bill", "telecom", "broadband", "cable"
            )
        ),
        KeywordRule(
            category = ExpenseCategory.TRANSPORTATION,
            strongKeywords = setOf(
                "uber", "lyft", "taxi", "cab", "metro", "subway", "transit",
                "bus fare", "train", "amtrak", "bart", "mta", "caltrain",
                "gas station", "shell", "chevron", "exxon", "mobil", "bp ",
                "fuel", "gasoline", "ev charge", "supercharger", "parking",
                "garage", "toll", "fastrak", "ezpass", "car wash", "oil change",
                "auto repair", "tire", "flight", "airline", "delta", "united",
                "southwest", "scooter", "lime", "bird", "citibike"
            ),
            softKeywords = setOf(
                "gas", "bus", "rail", "ride", "car", "auto", "fare", "commute"
            )
        ),
        KeywordRule(
            category = ExpenseCategory.ENTERTAINMENT,
            strongKeywords = setOf(
                "netflix", "spotify", "hulu", "disney+", "disney plus", "hbo",
                "max ", "youtube", "apple music", "cinema", "amc", "regal",
                "movie", "theater", "theatre", "concert", "ticketmaster",
                "stubhub", "museum", "zoo", "bowling", "arcade", "steam",
                "playstation", "xbox", "nintendo", "twitch", "audible",
                "kindle", "book", "bookstore", "barnes", "festival", "club"
            ),
            softKeywords = setOf(
                "game", "show", "ticket", "music", "stream", "subscription",
                "fun", "party"
            )
        ),
        KeywordRule(
            category = ExpenseCategory.SHOPPING,
            strongKeywords = setOf(
                "amazon", "amzn", "ebay", "etsy", "best buy", "apple store",
                "uniqlo", "zara", "h&m", "nike", "adidas", "nordstrom",
                "macys", "macy's", "sephora", "ulta", "clothing", "shoes",
                "jacket", "shirt", "pants", "electronics", "headphones",
                "laptop", "charger", "gift", "present", "stationery"
            ),
            softKeywords = setOf(
                "shop", "store", "order", "apparel", "bag", "watch", "buy"
            )
        ),
        KeywordRule(
            category = ExpenseCategory.HEALTH,
            strongKeywords = setOf(
                "cvs", "walgreens", "rite aid", "pharmacy", "prescription",
                "medicine", "doctor", "dentist", "dental", "optometrist",
                "clinic", "hospital", "urgent care", "therapy", "therapist",
                "gym", "equinox", "planet fitness", "yoga", "pilates",
                "vitamin", "supplement", "co-pay", "copay", "medical", "lab"
            ),
            softKeywords = setOf(
                "health", "fitness", "care", "rx", "wellness", "massage"
            )
        )
    )

    private val ambiguousMerchants = mapOf(
        "target" to listOf(ExpenseCategory.SHOPPING, ExpenseCategory.GROCERIES),
        "walmart" to listOf(ExpenseCategory.GROCERIES, ExpenseCategory.SHOPPING),
        "apple" to listOf(ExpenseCategory.SHOPPING, ExpenseCategory.ENTERTAINMENT),
        "amazon" to listOf(ExpenseCategory.SHOPPING, ExpenseCategory.ENTERTAINMENT),
        "square" to listOf(ExpenseCategory.FOOD_DINING, ExpenseCategory.SHOPPING),
        "venmo" to listOf(ExpenseCategory.FOOD_DINING, ExpenseCategory.OTHER),
        "zelle" to listOf(ExpenseCategory.HOUSING, ExpenseCategory.OTHER),
        "paypal" to listOf(ExpenseCategory.SHOPPING, ExpenseCategory.OTHER)
    )

    private val genericWords = setOf(
        "misc", "miscellaneous", "other", "item", "stuff", "payment", "pay",
        "purchase", "expense", "charge", "cash", "atm", "withdrawal",
        "transfer", "card", "errand", "fee", "unknown"
    )

    fun analyze(
        description: String,
        amountCents: Long,
        learnedHistory: Map<String, ExpenseCategory> = emptyMap()
    ): CategorizationResult {
        val cleaned = description.trim().lowercase()

        if (cleaned.isBlank()) {
            val candidates = amountBasedCandidates(amountCents)
            return CategorizationResult(
                suggestedCategory = null,
                confidence = CategorizationConfidence.UNSURE,
                candidates = candidates,
                reason = "Enter a description so SpendWise can auto-categorize, or pick a category below."
            )
        }

        // 1. Check exact or normalized match in user's learned history
        val learnedMatch = learnedHistory[cleaned]
            ?: learnedHistory.entries.firstOrNull { (pastDesc, _) ->
                pastDesc.length >= 4 && (cleaned.contains(pastDesc) || pastDesc.contains(cleaned))
            }?.value

        if (learnedMatch != null) {
            return CategorizationResult(
                suggestedCategory = learnedMatch,
                confidence = CategorizationConfidence.HIGH,
                candidates = listOf(learnedMatch),
                reason = "Matched your past entries for \"$description\""
            )
        }

        // 2. Check explicitly ambiguous merchants unless clarified by another strong keyword
        val matchedAmbiguous = ambiguousMerchants.entries.firstOrNull { (merchant, _) ->
            cleaned == merchant || cleaned.split(" ", "-", "_").contains(merchant)
        }
        val strongMatches = mutableMapOf<ExpenseCategory, Int>()
        val softMatches = mutableMapOf<ExpenseCategory, Int>()

        for (rule in rules) {
            val strongHits = rule.strongKeywords.count { kw -> cleaned.contains(kw) }
            val softHits = rule.softKeywords.count { kw ->
                cleaned.split(" ", ",", "-", "/", "&").any { token -> token == kw } ||
                    (kw.length >= 4 && cleaned.contains(kw))
            }
            if (strongHits > 0) strongMatches[rule.category] = strongHits
            if (softHits > 0) softMatches[rule.category] = softHits
        }

        if (matchedAmbiguous != null && strongMatches.size <= 1) {
            val topCandidates = matchedAmbiguous.value
            // Amount heuristic can refine hint, but multi-category stores remain UNSURE to ask user
            val amountHint = when {
                matchedAmbiguous.key == "target" || matchedAmbiguous.key == "walmart" ->
                    if (amountCents in 1L..35_00L) "small basket could be Groceries or Shopping"
                    else "could be Shopping or Groceries"
                else -> "multi-purpose merchant"
            }
            return CategorizationResult(
                suggestedCategory = null,
                confidence = CategorizationConfidence.UNSURE,
                candidates = topCandidates,
                reason = "\"${description.trim()}\" is ambiguous ($amountHint) — please choose a category."
            )
        }

        // 3. Evaluate strong keyword matches
        if (strongMatches.isNotEmpty()) {
            val sorted = strongMatches.entries.sortedByDescending { it.value }
            val top = sorted.first()
            if (sorted.size > 1 && sorted[0].value == sorted[1].value) {
                return CategorizationResult(
                    suggestedCategory = null,
                    confidence = CategorizationConfidence.UNSURE,
                    candidates = sorted.take(3).map { it.key },
                    reason = "Multiple categories match \"${description.trim()}\" — please confirm."
                )
            }
            // Amount sanity check for Housing vs Utilities
            if (top.key == ExpenseCategory.HOUSING && amountCents in 1L..25_00L && cleaned.contains("home")) {
                return CategorizationResult(
                    suggestedCategory = null,
                    confidence = CategorizationConfidence.UNSURE,
                    candidates = listOf(ExpenseCategory.SHOPPING, ExpenseCategory.HOUSING),
                    reason = "Small amount for Housing — confirm whether this is Shopping or Housing."
                )
            }
            return CategorizationResult(
                suggestedCategory = top.key,
                confidence = CategorizationConfidence.HIGH,
                candidates = listOf(top.key),
                reason = "Auto-matched description to ${top.key.displayName}"
            )
        }

        // 4. Evaluate soft keyword matches + amount heuristics
        if (softMatches.isNotEmpty()) {
            val sorted = softMatches.entries.sortedByDescending { it.value }
            val topCategory = sorted.first().key
            if (sorted.size == 1 && amountConfirmsCategory(topCategory, amountCents)) {
                return CategorizationResult(
                    suggestedCategory = topCategory,
                    confidence = CategorizationConfidence.MEDIUM,
                    candidates = listOf(topCategory) + amountBasedCandidates(amountCents).filter { it != topCategory }.take(2),
                    reason = "Suggested ${topCategory.displayName} from description & amount"
                )
            }
            val merged = (sorted.map { it.key } + amountBasedCandidates(amountCents)).distinct().take(3)
            return CategorizationResult(
                suggestedCategory = null,
                confidence = CategorizationConfidence.UNSURE,
                candidates = merged,
                reason = "Not fully sure about \"${description.trim()}\" — pick the best fit below."
            )
        }

        // 5. Generic or unrecognized description -> prompt the user with smart amount-based candidates
        val isGeneric = genericWords.any { cleaned == it || cleaned.contains("$it ") || cleaned.contains(" $it") }
        val candidates = amountBasedCandidates(amountCents)
        val reason = if (isGeneric) {
            "\"${description.trim()}\" is too general — please select a category."
        } else {
            "Unfamiliar merchant \"${description.trim()}\" — choose a category so SpendWise remembers it next time."
        }
        return CategorizationResult(
            suggestedCategory = null,
            confidence = CategorizationConfidence.UNSURE,
            candidates = candidates,
            reason = reason
        )
    }

    private fun amountConfirmsCategory(category: ExpenseCategory, amountCents: Long): Boolean {
        if (amountCents <= 0L) return true
        return when (category) {
            ExpenseCategory.HOUSING -> amountCents >= 150_00L
            ExpenseCategory.UTILITIES -> amountCents in 15_00L..450_00L
            ExpenseCategory.FOOD_DINING -> amountCents <= 120_00L
            ExpenseCategory.TRANSPORTATION -> amountCents <= 250_00L
            else -> true
        }
    }

    private fun amountBasedCandidates(amountCents: Long): List<ExpenseCategory> {
        return when {
            amountCents >= 500_00L -> listOf(
                ExpenseCategory.HOUSING,
                ExpenseCategory.SHOPPING,
                ExpenseCategory.UTILITIES
            )
            amountCents in 45_00L..250_00L -> listOf(
                ExpenseCategory.GROCERIES,
                ExpenseCategory.UTILITIES,
                ExpenseCategory.SHOPPING
            )
            amountCents in 1L..18_00L -> listOf(
                ExpenseCategory.FOOD_DINING,
                ExpenseCategory.TRANSPORTATION,
                ExpenseCategory.ENTERTAINMENT
            )
            else -> listOf(
                ExpenseCategory.FOOD_DINING,
                ExpenseCategory.GROCERIES,
                ExpenseCategory.SHOPPING
            )
        }
    }
}
