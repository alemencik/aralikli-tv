package com.daylak.araliklitv

import org.junit.Assert.*
import org.junit.Test

class SchedulerTest {
    @Test fun wrongAnswerIsScheduledSoon() {
        val p=Scheduler.grade(Progress(),Rating.AGAIN,1_000_000L,12)
        assertEquals(17,p.dueAtTurn)
        assertFalse(Scheduler.due(p,1_000_001L,16))
        assertTrue(Scheduler.due(p,1_000_001L,17))
    }
    @Test fun easyAnswerIsDelayed() {
        val p=Scheduler.grade(Progress(),Rating.EASY,1_000_000L,0)
        assertTrue(p.intervalDays>=2)
        assertFalse(Scheduler.due(p,1_000_001L,500))
    }
    @Test fun repeatThenGraduate() {
        val first=Scheduler.grade(Progress(),Rating.GOOD,1_000_000L,0)
        assertEquals(2,first.learningStep)
        val next=Scheduler.grade(first,Rating.GOOD,2_000_000L,11)
        assertEquals(0,next.learningStep)
        assertEquals(1,next.intervalDays)
    }
}
