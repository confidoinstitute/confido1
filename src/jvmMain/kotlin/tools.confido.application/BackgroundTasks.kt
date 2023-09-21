package tools.confido.application

import kotlinx.datetime.Clock
import tools.confido.question.Question
import tools.confido.question.QuestionState
import tools.confido.refs.deref
import tools.confido.state.serverState
import tools.confido.utils.forEachDeref


suspend fun checkAllQuestionSchedules() {
    val now = Clock.System.now()
    serverState.rooms.values.forEach { room->
        room.questions.forEachDeref { q->
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
            if (newQ != q) {
                println("UPDATING QUESTION")
                serverState.questionManager.replaceEntity(newQ)
            }
            if (sendReminder) {
                val author = q.author?.deref()
                if (author == null) {
                    println("Not sending, no author")
                } else {
                    println("SENDING REMINDER2")
                    if (author.email != null && author.email.isNotEmpty() && author.emailVerified)
                        globalMailer.sendScheduledResolutionMail(author.email, q)
                }
            }
        }
    }
}