package com.daylak.araliklitv

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * MVP's transparent learning-step scheduler. NOT FSRS: future versions can replace
 * this behind the same repository contract without changing question IDs.
 * A missed or guessed question is due after 5 other answers OR 5 real minutes, whichever first.
 */
enum class Rating { AGAIN, GUESSED, HARD, GOOD, EASY }

object Scheduler {
    const val DAY_MS = 86_400_000L
    private const val MINUTE_MS = 60_000L

    fun grade(previous: Progress, rating: Rating, now: Long, turn: Int): Progress {
        val attempts = previous.attempts + 1
        val correctCount = previous.correct + if (rating != Rating.AGAIN) 1 else 0
        val base = previous.copy(attempts = attempts, correct = correctCount)
        if (rating == Rating.AGAIN || rating == Rating.GUESSED) return base.copy(
            repetitions = 0,
            intervalDays = 0,
            ease = max(1.3, previous.ease - 0.20),
            dueAt = now + 5 * MINUTE_MS,
            learningStep = 1,
            dueAtTurn = turn + 6
        )
        if (rating == Rating.HARD && previous.repetitions == 0) return base.copy(
            repetitions = 0, intervalDays = 0,
            ease = max(1.3, previous.ease - 0.10),
            dueAt = now + 5 * MINUTE_MS,
            learningStep = 1, dueAtTurn = turn + 6
        )
        if (rating == Rating.GOOD && previous.repetitions == 0) return base.copy(
            repetitions = 1, intervalDays = 0,
            dueAt = now + 10 * MINUTE_MS,
            learningStep = 2, dueAtTurn = turn + 11
        )
        val step = when {
            previous.repetitions <= 1 -> 1
            previous.repetitions == 2 -> 3
            previous.repetitions == 3 -> 7
            previous.repetitions == 4 -> 15
            previous.repetitions == 5 -> 30
            else -> max(31, (previous.intervalDays * previous.ease).roundToInt())
        }
        val interval = when (rating) {
            Rating.HARD -> max(1, (step * 0.6).roundToInt())
            Rating.GOOD -> step
            Rating.EASY -> max(2, (step * 1.8).roundToInt())
            Rating.AGAIN, Rating.GUESSED -> error("already handled")
        }
        val easeDelta = when (rating) { Rating.HARD -> -0.1; Rating.GOOD -> 0.0; Rating.EASY -> 0.05; Rating.AGAIN, Rating.GUESSED -> 0.0 }
        return base.copy(
            repetitions = previous.repetitions + 1,
            intervalDays = interval,
            ease = (previous.ease + easeDelta).coerceIn(1.3,3.0),
            dueAt = now + interval.toLong() * DAY_MS,
            learningStep = 0,
            dueAtTurn = 0
        )
    }

    /** A timeout is unanswered, not a knowledge error. Repeat it early but track separately. */
    fun timeout(previous: Progress, now: Long, turn: Int): Progress =
        grade(previous, Rating.AGAIN, now, turn).copy(timeouts = previous.timeouts + 1)

    fun due(p: Progress, now: Long, turn: Int): Boolean =
        p.attempts > 0 && if (p.learningStep > 0) {
            turn >= p.dueAtTurn || now >= p.dueAt
        } else now >= p.dueAt

    fun pick(questions: List<Question>, states: Map<String, Progress>, now: Long, turn: Int): Question? {
        val scorable = questions.filter { it.isScorable }
        val waiting = scorable.filter { q -> states[q.id]?.let { due(it, now, turn) } ?: false }
        if (waiting.isNotEmpty()) return waiting.minWithOrNull(
            compareBy<Question>({ states[it.id]?.dueAt ?: Long.MAX_VALUE }, { it.id })
        )
        return scorable.firstOrNull { (states[it.id]?.attempts ?: 0) == 0 }
    }

    fun dueCount(questions: List<Question>, states: Map<String, Progress>, now: Long, turn: Int): Int =
        questions.count { q -> q.isScorable && (states[q.id]?.let { due(it, now, turn) } ?: false) }
}
