package com.sakura.data.workout

enum class SplitDay(val label: String, val displayName: String) {
    MONDAY_LIFT("monday-lift", "Monday — Lift (Heavy Compounds)"),
    TUESDAY_CALISTHENICS("tuesday-calisthenics", "Tuesday — Calisthenics"),
    THURSDAY_LIFT("thursday-lift", "Thursday — Lift (Moderate Volume)"),
    FRIDAY_CALISTHENICS("friday-calisthenics", "Friday — Calisthenics");

    companion object {
        fun fromLabel(label: String): SplitDay? =
            entries.firstOrNull { it.label == label }

        /**
         * Determine the next split day based on the last completed session.
         * Uses a fixed weekly cycle: Mon(0), Tue(1), Wed(rest), Thu(3), Fri(4), Sat(rest), Sun(off).
         * Returns null if lastSplitDay is null (no history).
         */
        fun nextAfter(lastSplitDay: SplitDay?): SplitDay? {
            if (lastSplitDay == null) return null  // no history -> caller shows all 4 days as selectable cards
            return when (lastSplitDay) {
                MONDAY_LIFT -> TUESDAY_CALISTHENICS
                TUESDAY_CALISTHENICS -> THURSDAY_LIFT
                THURSDAY_LIFT -> FRIDAY_CALISTHENICS
                FRIDAY_CALISTHENICS -> MONDAY_LIFT
            }
        }
    }
}
