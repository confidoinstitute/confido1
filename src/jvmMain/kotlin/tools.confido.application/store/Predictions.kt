package tools.confido.store

import com.mongodb.client.model.Filters.and
import com.mongodb.reactivestreams.client.ClientSession
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.plus
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineCollection
import tools.confido.question.Prediction
import tools.confido.question.Question
import tools.confido.refs.Ref
import tools.confido.refs.deref
import tools.confido.refs.ref
import tools.confido.state.appConfig
import tools.confido.state.serverState
import tools.confido.utils.generateId
import users.User

/**
 * Prediction storage, redesigned per PERSISTENCE_SYNC_REDESIGN.md §3.4:
 *
 * - [userPredictionLog] (collection `userPredHist`) is the ground truth — an append-only
 *   log of prediction events with a declared coalescing policy.
 * - [groupPredictionLog] (collection `groupPredHist`) is its derivative for the group
 *   aggregate.
 * - [latestPredictions] is a *materialized view* of the log — "latest prediction per
 *   (question, user)" — resident in memory and persisted into the existing `userPred`
 *   collection (same documents, same synthetic `"$qid:$uid"` ids) only so boot does not
 *   replay the full history.
 * - `groupPred`, `predictorCount` and `predictionCount` are incremental folds maintained
 *   from the head view's change stream (exposed via the serverState facade).
 *
 * Invariant (by construction, single writer): the head value for (q,u) equals the newest
 * `userPredHist` entry for (q,u); `groupPred[q]` matches the newest `groupPredHist` entry.
 */

/** "Latest prediction per (question, user)" — materialized view of [userPredictionLog]. */
object latestPredictions : ChangeSource<Prediction>() {
    internal val coll: CoroutineCollection<Prediction> = serverState.database.getCollection("userPred")

    private var byQuestion: PersistentMap<Ref<Question>, PersistentMap<Ref<User>, Prediction>> =
        persistentMapOf()

    val asMap: Map<Ref<Question>, Map<Ref<User>, Prediction>> get() = byQuestion
    fun get(question: Ref<Question>, user: Ref<User>): Prediction? = byQuestion[question]?.get(user)
    fun forQuestion(question: Ref<Question>): Map<Ref<User>, Prediction> =
        byQuestion[question] ?: emptyMap()

    internal suspend fun initialize() {
        coll.ensureUniqueIndex(Prediction::question, Prediction::user)
        coll.ensureIndex(Prediction::question)
        coll.ensureIndex(Prediction::user)
    }

    internal suspend fun load() {
        coll.find().toFlow().collect { pred ->
            val user = pred.user ?: return@collect
            byQuestion = byQuestion.put(pred.question,
                (byQuestion[pred.question] ?: persistentMapOf()).put(user, pred))
        }
        // Views are rebuilt wholesale by predictionData.load() afterwards; no per-entry
        // change emission during boot (matches the old isLoading-guarded load).
    }

    /** Deterministic rebuild procedure required of a materialized view: refold the log. */
    suspend fun rebuildFromLog() {
        byQuestion = persistentMapOf()
        userPredictionLog.coll.find().toFlow().collect { pred ->
            val user = pred.user ?: return@collect
            val cur = byQuestion[pred.question]?.get(user)
            if (cur == null || pred.ts >= cur.ts)
                byQuestion = byQuestion.put(pred.question,
                    (byQuestion[pred.question] ?: persistentMapOf()).put(user, pred))
        }
    }

    internal fun applyPut(pred: Prediction) {
        val user = pred.user!!
        val old = byQuestion[pred.question]?.get(user)
        byQuestion = byQuestion.put(pred.question,
            (byQuestion[pred.question] ?: persistentMapOf()).put(user, pred))
        emitChange(if (old == null) Change.Added(pred) else Change.Updated(old, pred))
    }

    internal fun applyClear(question: Ref<Question>) {
        val removed = byQuestion[question] ?: return
        byQuestion = byQuestion.remove(question)
        removed.values.forEach { emitChange(Change.Removed(it)) }
    }
}

/** The user prediction event log (`userPredHist`), ordered by `ts` within (question, user). */
object userPredictionLog {
    internal val coll: CoroutineCollection<Prediction> = serverState.database.getCollection("userPredHist")

    internal suspend fun initialize() {
        coll.ensureIndex(Prediction::question, Prediction::user, Prediction::ts)
        coll.ensureIndex(Prediction::question, Prediction::ts)
    }

    /** All entries for (question, user), ascending by ts. */
    fun query(question: Ref<Question>, user: Ref<User>) =
        coll.find(and(Prediction::question eq question, Prediction::user eq user))
            .sort(ascending(Prediction::ts)).toFlow()

    /** Newest entry with `ts <= ts`, or the head value when [ts] is null ("now"). */
    suspend fun at(question: Ref<Question>, user: Ref<User>, ts: Instant?): Prediction? =
        if (ts == null) latestPredictions.get(question, user)
        else coll.find(and(Prediction::question eq question, Prediction::user eq user,
                Prediction::ts lte ts.epochSeconds.toInt()))
            .sort(descending(Prediction::ts)).first()
}

/** The group prediction event log (`groupPredHist`); entries always have `user == null`. */
object groupPredictionLog {
    internal val coll: CoroutineCollection<Prediction> = serverState.database.getCollection("groupPredHist")

    internal suspend fun initialize() {
        coll.ensureIndex(Prediction::question, Prediction::ts)
    }

    /** All entries for a question, ascending by ts. */
    fun query(question: Ref<Question>) =
        coll.find(Prediction::question eq question).sort(ascending(Prediction::ts)).toFlow()

    /** Newest entry with `ts <= ts`, or the current group prediction when [ts] is null. */
    suspend fun at(question: Ref<Question>, ts: Instant?): Prediction? =
        if (ts == null) predictionData.groupPred[question]
        else coll.find(and(Prediction::question eq question,
                Prediction::ts lte ts.epochSeconds.toInt()))
            .sort(descending(Prediction::ts)).first()
}

/**
 * Orchestrates boot and owns the derived folds. Registered in [Stores]; the serverState
 * facade (`userPred`, `groupPred`, `predictorCount`, `predictionCount`) delegates here.
 */
object predictionData : ManagedStore {
    override val mongoCollections
        get() = listOf(latestPredictions.coll, userPredictionLog.coll, groupPredictionLog.coll)

    /** Group aggregate per question; absent key = no group prediction. */
    internal val groupPredMap = mutableMapOf<Ref<Question>, Prediction>()
    val groupPred: Map<Ref<Question>, Prediction> get() = groupPredMap
    val predictorCount = CountView<Ref<Question>>()
    val predictionCount = CountView<Ref<Question>>()

    init {
        // groupPred + predictorCount are folds over the head view's change stream: any
        // change to a question's head re-aggregates that question (bounded by its
        // predictor count, as the old recalcGroupPred was).
        latestPredictions.onChange { change -> recomputeGroup(change.entity.question) }
    }

    internal fun recomputeGroup(qref: Ref<Question>) {
        val question = qref.deref() ?: run {
            groupPredMap.remove(qref)
            predictorCount.set(qref, 0)
            return
        }
        val preds = latestPredictions.forQuestion(qref).values
        val gp = serverState.calculateGroupPred(question, preds)
        if (gp == null) groupPredMap.remove(qref) else groupPredMap[qref] = gp
        predictorCount.set(qref, preds.size)
    }

    override suspend fun initialize() {
        latestPredictions.initialize()
        userPredictionLog.initialize()
        groupPredictionLog.initialize()
    }

    override suspend fun load() {
        latestPredictions.load()
        latestPredictions.asMap.keys.forEach { recomputeGroup(it) }
        @Serializable
        data class Result(val question: Ref<Question>, val cnt: Int)
        userPredictionLog.coll.aggregate<Result>(
            listOf(group(Prediction::question, Result::question first Prediction::question, Result::cnt.sum(1)))
        ).toFlow().collect { predictionCount.set(it.question, it.cnt) }
    }
}

/**
 * Record a new prediction: upsert the head, append to the user log under the coalescing
 * policy, and append the recomputed group aggregate to the group log — one transaction.
 *
 * Coalescing policy (formerly inline in ServerState.addPrediction): if the previous log
 * entry for (question, user) is less than `appConfig.predictionCoalesceInterval` seconds
 * older, it is replaced instead of kept — unless the pair straddles the question's score
 * time, which must stay intact for scoring. If the newest group log entry was caused by
 * the replaced entry, it is replaced too (derivative coalescing).
 */
suspend fun addPrediction(pred: Prediction) {
    val q = pred.question.deref()
    require(q != null) { "Prediction for a nonexistent question" }
    requireNotNull(pred.user) { "User prediction must have a user" }
    val sched = q.effectiveSchedule
    transact {
        inTransaction { scope ->
            scope.stage(HeadPutOp(pred))
            val newPreds = latestPredictions.forQuestion(pred.question).toMutableMap()
                .apply { put(pred.user!!, pred) }
            val gp = serverState.calculateGroupPred(q, newPreds.values)
            gp?.let { check(it.user == null) }
            scope.stage(AppendOp(pred, sched.score, gp))
        }
        serverState.extensions.forEach { it.onPrediction(q, pred) }
    }
}

/** Delete all prediction data of a question (head + both logs) in the ambient transaction. */
suspend fun clearPredictions(question: Ref<Question>) = inTransaction { scope ->
    scope.stage(ClearPredictionsOp(question))
}

private class HeadPutOp(val pred: Prediction) : StagedWrite {
    override suspend fun flush(sess: ClientSession?) {
        val stored = pred.copy(id = "${pred.question.id}:${pred.user!!.id}")
        val filter = and(Prediction::question eq pred.question, Prediction::user eq pred.user)
        val options = com.mongodb.client.model.ReplaceOptions().upsert(true)
        if (sess == null) latestPredictions.coll.replaceOne(filter, stored, options)
        else latestPredictions.coll.replaceOne(sess, filter, stored, options)
    }
    override fun apply() {
        latestPredictions.applyPut(pred.copy(id = "${pred.question.id}:${pred.user!!.id}"))
    }
}

private class AppendOp(val pred: Prediction, val scoreTime: Instant?, val groupPred: Prediction?) : StagedWrite {
    private var coalesced = false

    override suspend fun flush(sess: ClientSession?) {
        // Reads are deliberately sessionless (as before the redesign): under a single
        // writer they see the pre-transaction committed state, which is what the
        // coalescing decision is defined against.
        val log = userPredictionLog.coll
        val myLast = log.find(and(Prediction::question eq pred.question, Prediction::user eq pred.user))
            .sort(descending(Prediction::ts)).first()

        var lastOther: Prediction? = null
        if (myLast != null && pred.ts - myLast.ts < appConfig.predictionCoalesceInterval &&
            !(scoreTime != null && myLast.ts <= scoreTime.epochSeconds && pred.ts > scoreTime.epochSeconds)
        ) {
            coalesced = true
            if (sess == null) log.deleteOneById(myLast.id) else log.deleteOneById(sess, myLast.id)
            lastOther = log.find(Prediction::question eq pred.question)
                .sort(descending(Prediction::ts)).first()
        }
        val entry = pred.copy(id = generateId())
        if (sess == null) log.insertOne(entry) else log.insertOne(sess, entry)

        groupPred?.let { gp ->
            val groupLog = groupPredictionLog.coll
            if (lastOther != null) {
                // If the newest group log entry was caused by the entry we just replaced,
                // replace it as well.
                val lastGroup = groupLog.find(Prediction::question eq pred.question)
                    .sort(descending(Prediction::ts)).first()
                if (lastGroup != null && lastGroup.ts >= lastOther.ts) {
                    if (sess == null) groupLog.deleteOneById(lastGroup.id)
                    else groupLog.deleteOneById(sess, lastGroup.id)
                }
            }
            val groupEntry = gp.copy(id = generateId())
            if (sess == null) groupLog.insertOne(groupEntry) else groupLog.insertOne(sess, groupEntry)
        }
    }

    override fun apply() {
        if (!coalesced) predictionData.predictionCount.increment(pred.question)
    }
}

private class ClearPredictionsOp(val question: Ref<Question>) : StagedWrite {
    override suspend fun flush(sess: ClientSession?) {
        val filter = Prediction::question eq question
        if (sess == null) {
            latestPredictions.coll.deleteMany(filter)
            userPredictionLog.coll.deleteMany(filter)
            groupPredictionLog.coll.deleteMany(filter)
        } else {
            latestPredictions.coll.deleteMany(sess, filter)
            userPredictionLog.coll.deleteMany(sess, filter)
            groupPredictionLog.coll.deleteMany(sess, filter)
        }
    }
    override fun apply() {
        latestPredictions.applyClear(question) // fold clears groupPred + predictorCount
        predictionData.predictionCount.set(question, 0)
    }
}

/** Register prediction stores and their cascade hooks. Called once from server startup. */
fun registerPredictionStores() {
    Stores.register(predictionData)
    // Cascade: deleting a question deletes its predictions (this was previously simply
    // missing — deleted questions orphaned their prediction data forever).
    tools.confido.store.questionStore.beforeEntityDelete { q -> clearPredictions(q.ref) }
}
