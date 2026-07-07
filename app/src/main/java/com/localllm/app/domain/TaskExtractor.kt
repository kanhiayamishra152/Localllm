package com.localllm.app.domain

import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class ExtractedTask(
    val title: String,
    val dueDate: Long? = null,
    val isRoutine: Boolean = false,
    val recurrence: String? = null,
    val priority: Int = 0
)

/**
 * Heuristic task/routine detector. Parses a chat message and extracts actionable
 * tasks, deadlines and routines WITHOUT invoking the LLM. The actual AI-driven
 * enrichment is deferred (see PRD section 4) and triggered later in the app.
 */
@Singleton
class TaskExtractor @Inject constructor() {

    private val triggers = listOf(
        "remind me to", "remind me", "add task", "add a task", "new task", "todo:",
        "to-do:", "to do:", "remember to", "i need to", "i have to", "i must",
        "schedule", "set a reminder", "set reminder", "don't forget to",
        "do not forget to", "make sure to", "plan to"
    )

    private val weekdays = mapOf(
        "monday" to Calendar.MONDAY, "tuesday" to Calendar.TUESDAY,
        "wednesday" to Calendar.WEDNESDAY, "thursday" to Calendar.THURSDAY,
        "friday" to Calendar.FRIDAY, "saturday" to Calendar.SATURDAY,
        "sunday" to Calendar.SUNDAY
    )

    fun extract(text: String): List<ExtractedTask> {
        val lower = text.lowercase(Locale.getDefault())
        if (!triggers.any { lower.contains(it) }) return emptyList()

        val isRoutine = lower.contains("every day") || lower.contains("everyday") ||
                lower.contains("daily") || lower.contains("each day") ||
                lower.contains("every week") || lower.contains("weekly") ||
                lower.contains("each week") || weekdays.any { w ->
                    lower.contains("every $w") || lower.contains("each $w")
                }

        val recurrence = when {
            lower.contains("daily") || lower.contains("every day") || lower.contains("everyday") ||
                    lower.contains("each day") -> "daily"
            lower.contains("weekly") || lower.contains("every week") || lower.contains("each week") -> "weekly"
            weekdays.any { w -> lower.contains("every $w") || lower.contains("each $w") } -> "weekly"
            else -> null
        }

        val dueDate = parseDeadline(lower)
        val title = buildTitle(text, lower) ?: return emptyList()

        val priority = when {
            lower.contains("urgent") || lower.contains("asap") || lower.contains("important") -> 2
            lower.contains("soon") || lower.contains("today") -> 1
            else -> 0
        }

        return listOf(
            ExtractedTask(
                title = title,
                dueDate = dueDate,
                isRoutine = isRoutine,
                recurrence = recurrence,
                priority = priority
            )
        )
    }

    private fun buildTitle(text: String, lower: String): String? {
        for (trigger in triggers) {
            val idx = lower.indexOf(trigger)
            if (idx >= 0) {
                var raw = text.substring(idx + trigger.length).trim()
                raw = raw.removePrefix("to ").removePrefix("a ").trim()
                // strip a trailing deadline clause for a clean title
                raw = raw.replace(Regex("\\b(by|on|at|every|each|tomorrow|today|tonight)\\b.*", RegexOption.IGNORECASE), "")
                    .trim()
                raw = raw.trimEnd('.', '!', '?', ',').trim()
                if (raw.isNotBlank()) return raw.replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }

    private fun parseDeadline(lower: String): Long? {
        val cal = Calendar.getInstance()
        val time = parseTime(lower)
        when {
            lower.contains("tomorrow") -> cal.add(Calendar.DAY_OF_YEAR, 1)
            lower.contains("tonight") -> { /* keep today, set evening */ }
            lower.contains("today") -> { /* keep today */ }
            lower.contains("next week") -> cal.add(Calendar.WEEK_OF_YEAR, 1)
            else -> {
                val wd = weekdays.entries.firstOrNull { lower.contains("${it.key}") }
                if (wd != null) {
                    val target = wd.value
                    val diff = (target - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
                    cal.add(Calendar.DAY_OF_YEAR, if (diff == 0) 7 else diff)
                } else {
                    val inDays = Regex("in (\\d+) days?").find(lower)
                    if (inDays != null) {
                        cal.add(Calendar.DAY_OF_YEAR, inDays.groupValues[1].toIntOrNull() ?: 0)
                    }
                }
            }
        }
        if (time != null) {
            cal.set(Calendar.HOUR_OF_DAY, time.first)
            cal.set(Calendar.MINUTE, time.second)
            cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        } else {
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun parseTime(lower: String): Pair<Int, Int>? {
        val at = Regex("at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(lower)
        if (at != null) {
            var h = at.groupValues[1].toIntOrNull() ?: return null
            val m = at.groupValues[2].toIntOrNull() ?: 0
            val mer = at.groupValues[3]
            if (mer == "pm" && h < 12) h += 12
            if (mer == "am" && h == 12) h = 0
            return h to m
        }
        val hm = Regex("(\\d{1,2}):(\\d{2})\\s*(am|pm)?").find(lower)
        if (hm != null) {
            var h = hm.groupValues[1].toIntOrNull() ?: return null
            val m = hm.groupValues[2].toIntOrNull() ?: 0
            val mer = hm.groupValues[3]
            if (mer == "pm" && h < 12) h += 12
            if (mer == "am" && h == 12) h = 0
            return h to m
        }
        return null
    }
}
