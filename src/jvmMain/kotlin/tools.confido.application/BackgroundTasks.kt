package tools.confido.application

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import rooms.Room
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.application.sessions.TransientData
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.state.serverState
import tools.confido.utils.forEachDeref


suspend fun checkAllQuestionSchedules() {
    val now = Clock.System.now()
    var changed = false
    serverState.rooms.values.forEach { room->
        room.questions.forEachDeref { q->
            // Isolate each question: one failure must not abort the rest of the sweep.
            try {
                if (checkQuestionSchedule(room, q, now)) changed = true
            } catch (e: Throwable) {
                backgroundLog.error("Scheduled update of question ${q.id} failed", e)
            }
        }
    }
    // Without this, scheduled state changes stay invisible to connected clients
    // until some unrelated mutation happens to trigger a refresh.
}

/** Applies any schedule transitions of [q] that are due at [now]. Returns whether it was modified. */
private suspend fun checkQuestionSchedule(room: Room, q: Question, now: Instant): Boolean {
    val sched = q.schedule ?: room.defaultSchedule
    var stat = q.scheduleStatus
    var newQ = q
    var sendReminder = false
    if (sched.open != null && now >= sched.open && !stat.openDone) {
        stat = stat.copy(openDone = true)
        newQ = newQ.withState(QuestionState.OPEN).copy(scheduleStatus = stat)
        println("SCHED OPEN ${q.id}")
    }
    if (sched.close != null && now >= sched.close && !stat.closeDone) {
        stat = stat.copy(closeDone = true)
        newQ = newQ.withState(QuestionState.CLOSED).copy(scheduleStatus = stat)
        println("SCHED CLOSE ${q.id}")
    }
    if (sched.resolve != null && now >= sched.resolve && !stat.resolveDone) {
        stat = stat.copy(resolveDone = true)
        newQ = newQ.copy(scheduleStatus = stat)
        println("SCHED RESOLVE ${q.id}")
        if (q.resolved) {
            println("RESOLVE DIRECT")
            newQ = newQ.withState(QuestionState.RESOLVED)
        } else {
            println("RESOLVE SEND REMINDER")
            sendReminder = true
        }
    }
    val changed = newQ != q
    if (changed) {
        println("UPDATING QUESTION")
        tools.confido.store.questionStore.modify(newQ.ref) { newQ }
    }
    if (sendReminder) {
        val author = q.author?.deref()
        if (author == null) {
            println("Not sending, no author")
        } else {
            println("SENDING REMINDER2")
            // Caught here rather than by the caller so that an SMTP failure cannot
            // discard the fact that the question itself was updated above.
            try {
                if (author.email != null && author.email.isNotEmpty() && author.emailVerified)
                    globalMailer.sendScheduledResolutionMail(author.email, q)
            } catch (e: Throwable) {
                backgroundLog.error("Sending resolution reminder for question ${q.id} failed", e)
            }
        }
    }
    return changed
}
