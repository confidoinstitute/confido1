package extensions.calibration_training

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.litote.kmongo.*
import org.litote.kmongo.coroutine.CoroutineCollection
import tools.confido.application.routes.*
import tools.confido.calibration.CalibrationBin
import tools.confido.extensions.ServerExtension
import tools.confido.refs.Ref
import tools.confido.refs.ref
import tools.confido.state.serverState
import tools.confido.utils.generateId
import tools.confido.utils.unixNow
import users.User
import java.io.File

/**
 * Server extension for calibration training.
 */
object CalibrationTrainingSE : ServerExtension, CalibrationTrainingExtension() {
    
    // In-memory storage for questions loaded from TOML
    private var trainingQuestions: List<TrainingQuestion> = emptyList()
    
    // MongoDB collection for user responses
    private lateinit var responseCollection: CoroutineCollection<TrainingResponse>
    
    override suspend fun serverInit() {
        // Initialize MongoDB collection
        responseCollection = serverState.database.getCollection<TrainingResponse>("trainingResponses")
        
        // Create indexes
        responseCollection.ensureIndex(TrainingResponse::user, TrainingResponse::questionId)
        responseCollection.ensureIndex(TrainingResponse::user)
        
        // Load questions from TOML file
        loadQuestions()
    }
    
    /**
     * Load questions from CSV/TSV file.
     * Format: Question, Answer (Yes/No/Skip), Comment, Category (optional)
     */
    private fun loadQuestions() {
        val defaultPath = "data/calibration_training.tsv"
        val customPath = System.getenv("CONFIDO_TRAINING_QUESTIONS_FILE")
        val filePath = customPath ?: defaultPath
        
        val file = File(filePath)
        if (!file.exists()) {
            println("Warning: Training questions file not found at $filePath")
            trainingQuestions = emptyList()
            return
        }
        
        try {
            val content = file.readText()
            val isTsv = detectTsvFormat(filePath, content)
            trainingQuestions = parseCsvQuestions(content, isTsv).filter { !it.skip }
            println("Loaded ${trainingQuestions.size} training questions from $filePath")
        } catch (e: Exception) {
            println("Error loading training questions: ${e.message}")
            trainingQuestions = emptyList()
        }
    }
    
    /**
     * Detect whether the file is TSV or CSV format.
     * First checks file extension, then analyzes content.
     */
    private fun detectTsvFormat(filePath: String, content: String): Boolean {
        // Check extension first
        if (filePath.endsWith(".tsv", ignoreCase = true)) return true
        if (filePath.endsWith(".csv", ignoreCase = true)) return false
        
        // Analyze first non-empty line
        val firstLine = content.lines().firstOrNull { it.isNotBlank() } ?: return false
        val tabCount = firstLine.count { it == '\t' }
        val commaCount = firstLine.count { it == ',' }
        
        return tabCount >= commaCount && tabCount >= 2
    }
    
    /**
     * Generate a unique ID from question text.
     * Format: slugified-question-text-hashsuffix
     */
    private fun generateQuestionId(text: String): String {
        val slug = text.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .take(40)
        val hash = text.hashCode().toUInt().toString(16).takeLast(6).padStart(6, '0')
        return "$slug-$hash"
    }
    
    /**
     * Parse CSV/TSV content into TrainingQuestion list.
     * Columns: Question, Answer, Comment, Category (optional)
     * Answer values: Yes/True/1 = true, No/False/0 = false, Skip/empty = skip
     */
    private fun parseCsvQuestions(content: String, isTsv: Boolean): List<TrainingQuestion> {
        val delimiter = if (isTsv) '\t' else ','
        val lines = content.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        
        val questions = mutableListOf<TrainingQuestion>()
        var startIndex = 0
        
        // Auto-detect header row
        val firstLine = lines.first()
        val firstFields = parseCsvLine(firstLine, delimiter)
        if (firstFields.isNotEmpty() && 
            firstFields[0].equals("question", ignoreCase = true)) {
            startIndex = 1
        }
        
        for (i in startIndex until lines.size) {
            val fields = parseCsvLine(lines[i], delimiter)
            if (fields.isEmpty()) continue
            
            val questionText = fields.getOrNull(0)?.trim() ?: continue
            if (questionText.isEmpty()) continue
            
            val answerStr = fields.getOrNull(1)?.trim()?.lowercase() ?: ""
            val comment = fields.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
            val category = fields.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }
            
            val (answer, skip) = when (answerStr) {
                "yes", "true", "1" -> true to false
                "no", "false", "0" -> false to false
                "skip", "" -> false to true
                else -> false to true  // Unknown values treated as skip
            }
            
            questions.add(TrainingQuestion(
                id = generateQuestionId(questionText),
                text = questionText,
                answer = answer,
                comment = comment,
                category = category,
                skip = skip,
            ))
        }
        
        return questions
    }
    
    /**
     * Parse a single CSV/TSV line, handling quoted fields.
     */
    private fun parseCsvLine(line: String, delimiter: Char): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && !inQuotes -> {
                    inQuotes = true
                }
                c == '"' && inQuotes -> {
                    // Check for escaped quote
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        current.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                }
                c == delimiter && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> {
                    current.append(c)
                }
            }
            i++
        }
        fields.add(current.toString())
        
        return fields
    }
    
    /**
     * Get IDs of questions the user has already answered.
     */
    private suspend fun getAnsweredQuestionIds(user: Ref<User>): Set<String> {
        return responseCollection.find(TrainingResponse::user eq user)
            .toList()
            .map { it.questionId }
            .toSet()
    }
    
    /**
     * Get a random unanswered question for the user.
     */
    private suspend fun getNextQuestion(user: Ref<User>): TrainingQuestion? {
        val answered = getAnsweredQuestionIds(user)
        val unanswered = trainingQuestions.filter { it.id !in answered }
        return unanswered.randomOrNull()
    }
    
    /**
     * Get user's responses for calibration calculation.
     */
    private suspend fun getUserResponses(user: Ref<User>): List<TrainingResponse> {
        return responseCollection.find(TrainingResponse::user eq user).toList()
    }
    
    /**
     * Calculate bucket stats from responses.
     */
    private fun calculateBucketStats(responses: List<TrainingResponse>): Map<String, BucketStat> {
        val buckets = mutableMapOf<String, MutableList<Pair<Double, Boolean>>>()
        
        for (response in responses) {
            val question = trainingQuestions.find { it.id == response.questionId } ?: continue
            
            // Normalize: confidence is always >= 0.5
            val confidence = if (response.yesProb >= 0.5) response.yesProb else 1 - response.yesProb
            val predictedYes = response.yesProb >= 0.5
            val wasCorrect = predictedYes == question.answer
            
            // Find the bucket
            val bucket = CalibrationBin.entries.find { confidence in it.range }
            val bucketName = bucket?.let { formatBucketName(it) } ?: "50% - 55%"
            
            buckets.getOrPut(bucketName) { mutableListOf() }.add(confidence to wasCorrect)
        }
        
        return buckets.mapValues { (_, entries) ->
            val correct = entries.count { it.second }
            BucketStat(correct, entries.size)
        }
    }
    
    private fun formatBucketName(bin: CalibrationBin): String {
        val start = (bin.range.start * 100).toInt()
        val end = (bin.range.endInclusive * 100).toInt()
        return "$start% - $end%"
    }
    
    /**
     * Get bucket feedback for a specific prediction.
     */
    private fun getBucketFeedback(
        prediction: Double,
        responses: List<TrainingResponse>,
        questions: List<TrainingQuestion>
    ): BucketFeedback {
        // Normalize confidence
        val confidence = if (prediction >= 0.5) prediction else 1 - prediction
        
        // Find the bucket
        val bucket = CalibrationBin.entries.find { confidence in it.range } ?: CalibrationBin.BIN_52
        val bucketName = formatBucketName(bucket)
        val targetAccuracy = (bucket.range.start + bucket.range.endInclusive) / 2
        
        // Count correct/total for this bucket
        var correct = 0
        var total = 0
        
        for (response in responses) {
            val question = questions.find { it.id == response.questionId } ?: continue
            val respConfidence = if (response.yesProb >= 0.5) response.yesProb else 1 - response.yesProb
            
            if (respConfidence in bucket.range) {
                total++
                val predictedYes = response.yesProb >= 0.5
                if (predictedYes == question.answer) {
                    correct++
                }
            }
        }
        
        return BucketFeedback(bucketName, correct, total, targetAccuracy)
    }
    
    override fun initRoutes(r: Routing) {
        r.apply {
            // Get next question
            getST("/api/training/next") {
                call.withUser {
                    val question = getNextQuestion(user.ref)
                    val answered = getAnsweredQuestionIds(user.ref).size
                    
                    call.respond(NextQuestionResponse(
                        question = question,
                        progress = TrainingProgress(answered, trainingQuestions.size)
                    ))
                }
            }
            
            // Submit answer
            postST("/api/training/submit") {
                call.withUser {
                    val request = call.receive<SubmitAnswerRequest>()
                    
                    // Find the question
                    val question = trainingQuestions.find { it.id == request.questionId }
                        ?: return@withUser call.respond(HttpStatusCode.NotFound, "Question not found")
                    
                    // Check if already answered
                    val existing = responseCollection.findOne(
                        and(
                            TrainingResponse::user eq user.ref,
                            TrainingResponse::questionId eq request.questionId
                        )
                    )
                    if (existing != null) {
                        return@withUser call.respond(HttpStatusCode.Conflict, "Question already answered")
                    }
                    
                    // Store the response
                    val response = TrainingResponse(
                        id = generateId(),
                        user = user.ref,
                        questionId = request.questionId,
                        yesProb = request.yesProb,
                        timestamp = unixNow().toLong(),
                    )
                    responseCollection.insertOne(response)
                    
                    // Get all user responses including the new one
                    val allResponses = getUserResponses(user.ref)
                    
                    // Calculate feedback
                    val predictedYes = request.yesProb >= 0.5
                    val wasCorrectSide = predictedYes == question.answer
                    val bucketFeedback = getBucketFeedback(request.yesProb, allResponses, trainingQuestions)
                    
                    call.respond(AnswerFeedbackResponse(
                        question = question,
                        userPrediction = request.yesProb,
                        wasCorrectSide = wasCorrectSide,
                        bucketFeedback = bucketFeedback,
                    ))
                }
            }
            
            // Get training stats
            getST("/api/training/stats") {
                call.withUser {
                    val responses = getUserResponses(user.ref)
                    val bucketStats = calculateBucketStats(responses)
                    
                    call.respond(TrainingStats(
                        totalAnswered = responses.size,
                        bucketStats = bucketStats,
                    ))
                }
            }
            
            // Reset progress
            postST("/api/training/reset") {
                call.withUser {
                    responseCollection.deleteMany(TrainingResponse::user eq user.ref)
                    call.respond(HttpStatusCode.OK)
                }
            }
            
            // Get all questions (for debugging/admin)
            getST("/api/training/questions") {
                call.withUser {
                    // Only allow admins
                    if (serverState.users[user.id]!!.type != users.UserType.ADMIN) {
                        return@withUser call.respond(HttpStatusCode.Forbidden, "Admin only")
                    }
                    call.respond(trainingQuestions)
                }
            }
        }
    }
}
