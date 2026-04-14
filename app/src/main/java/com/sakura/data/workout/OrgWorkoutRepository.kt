package com.sakura.data.workout

import com.sakura.orgengine.OrgDateSection
import com.sakura.orgengine.OrgExerciseLog
import com.sakura.orgengine.OrgFile
import com.sakura.orgengine.OrgParser
import com.sakura.orgengine.OrgSetEntry
import com.sakura.orgengine.OrgWriter
import com.sakura.preferences.AppPreferencesRepository
import com.sakura.sync.SyncBackend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Org-file-backed implementation of WorkoutRepository.
 *
 * Manages workout-log.org via SyncBackend using the Phase 3 per-set format.
 * Day-based UX: each addSet call writes immediately to the org file (no draft/finish lifecycle).
 * This mirrors the OrgFoodRepository.addEntry pattern exactly.
 *
 * Thread safety: all file mutations are serialized through [fileMutex].
 * Read operations do not acquire the mutex.
 *
 * User exercise persistence: stored as JSON in DataStore via AppPreferencesRepository.
 * Format is a simple JSON array of serializable objects — no separate org file.
 *
 * Org file format:
 *   * <2026-04-09 Thu>            <- date heading (level 1)
 *   ** Workout                    <- session heading (level 2)
 *   :PROPERTIES:
 *   :split_day: monday-lift
 *   :volume: 12450
 *   :duration_min: 62
 *   :complete: true
 *   :END:
 *   *** Exercise Name             <- exercise heading (level 3)
 *   :PROPERTIES:
 *   :id: 1712661600000
 *   :exercise_type: barbell
 *   :category: weighted
 *   :END:
 *   **** Set 1                    <- set heading (level 4)
 *   :PROPERTIES:
 *   :reps: 5
 *   :weight: 80
 *   :unit: kg
 *   :is_pr: false
 *   :END:
 */
class OrgWorkoutRepository(
    private val syncBackend: SyncBackend,
    private val prefsRepo: AppPreferencesRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : WorkoutRepository {

    private val fileMutex = Mutex()

    companion object {
        const val WORKOUT_LOG_FILE = "workout-log.org"
    }

    // =========================================================================
    // Atomic session operations (legacy / batch)
    // =========================================================================

    override suspend fun saveSession(session: WorkoutSession): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = readFile()
                    val orgFile = parseFile(content)
                    val newSection = session.toOrgDateSection()
                    val updatedSections = upsertSection(orgFile.sections, newSection)
                    val updatedFile = orgFile.copy(sections = updatedSections)
                    syncBackend.writeFile(WORKOUT_LOG_FILE, OrgWriter.write(updatedFile))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    // =========================================================================
    // Read operations
    // =========================================================================

    override suspend fun loadSession(date: LocalDate): WorkoutSession? {
        return try {
            val content = readFile()
            if (content.isBlank()) return null
            val orgFile = parseFile(content)
            orgFile.sections.find { it.date == date }?.toWorkoutSession()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun loadHistory(): List<WorkoutSession> {
        return try {
            val content = readFile()
            if (content.isBlank()) return emptyList()
            val orgFile = parseFile(content)
            orgFile.sections.mapNotNull { it.toWorkoutSession() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun findPersonalBest(exerciseName: String): PersonalBest? {
        return try {
            val content = readFile()
            if (content.isBlank()) return null
            val orgFile = parseFile(content)

            val normalizedTarget = exerciseName.trim().lowercase()
            var bestWeight = 0.0
            var bestReps = 0
            var bestHoldSecs = 0
            var foundAny = false

            for (section in orgFile.sections) {
                for (exercise in section.exerciseLogs) {
                    if (exercise.name.trim().lowercase() != normalizedTarget) continue
                    foundAny = true
                    for (set in exercise.sets) {
                        if (set.unit != "bw") bestWeight = maxOf(bestWeight, set.weight)
                        bestReps = maxOf(bestReps, set.reps)
                        bestHoldSecs = maxOf(bestHoldSecs, set.holdSecs)
                    }
                }
            }

            if (!foundAny) null
            else PersonalBest(weight = bestWeight, reps = bestReps, holdSecs = bestHoldSecs)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun loadLastSessionForSplitDay(splitDay: SplitDay): WorkoutSession? {
        return try {
            val content = readFile()
            if (content.isBlank()) return null
            val orgFile = parseFile(content)
            orgFile.sections
                .firstOrNull { it.splitDay == splitDay.label }
                ?.toWorkoutSession()
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================================
    // Incremental write methods (day-based UX)
    // =========================================================================

    override suspend fun addExercise(date: LocalDate, exercise: ExerciseLog, splitDay: String?): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = readFile()
                    val orgFile = parseFile(content)
                    val orgExercise = exercise.toOrgExerciseLog()
                    val updatedSections = upsertExercise(orgFile.sections, date, orgExercise, splitDay)
                    syncBackend.writeFile(WORKOUT_LOG_FILE, OrgWriter.write(orgFile.copy(sections = updatedSections)))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun removeExercise(date: LocalDate, exerciseId: Long): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = readFile()
                    if (content.isBlank()) return@withContext Result.success(Unit)
                    val orgFile = parseFile(content)
                    val updatedSections = deleteExercise(orgFile.sections, date, exerciseId)
                    syncBackend.writeFile(WORKOUT_LOG_FILE, OrgWriter.write(orgFile.copy(sections = updatedSections)))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun addSet(date: LocalDate, exerciseId: Long, set: SetLog): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = readFile()
                    val orgFile = parseFile(content)
                    val orgSet = set.toOrgSetEntry()
                    val updatedSections = appendSet(orgFile.sections, date, exerciseId, orgSet)
                    syncBackend.writeFile(WORKOUT_LOG_FILE, OrgWriter.write(orgFile.copy(sections = updatedSections)))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun removeSet(date: LocalDate, exerciseId: Long, setNumber: Int): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = readFile()
                    if (content.isBlank()) return@withContext Result.success(Unit)
                    val orgFile = parseFile(content)
                    val updatedSections = deleteSet(orgFile.sections, date, exerciseId, setNumber)
                    syncBackend.writeFile(WORKOUT_LOG_FILE, OrgWriter.write(orgFile.copy(sections = updatedSections)))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun markComplete(date: LocalDate, complete: Boolean): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = readFile()
                    val orgFile = parseFile(content)
                    val updatedSections = setComplete(orgFile.sections, date, complete)
                    syncBackend.writeFile(WORKOUT_LOG_FILE, OrgWriter.write(orgFile.copy(sections = updatedSections)))
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override suspend fun loadPreviousSetsForExercise(exerciseName: String): List<SetLog> {
        return try {
            val content = readFile()
            if (content.isBlank()) return emptyList()
            val orgFile = parseFile(content)
            val normalized = exerciseName.trim().lowercase()
            // Sections are newest-first; find the most recent occurrence
            for (section in orgFile.sections) {
                val log = section.exerciseLogs.firstOrNull { it.name.trim().lowercase() == normalized }
                if (log != null) return log.sets.map { it.toSetLog() }
            }
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // =========================================================================
    // User exercise library persistence (DataStore JSON)
    // =========================================================================

    override suspend fun saveUserExercises(exercises: List<LibraryExercise>) {
        try {
            val serializable = exercises.map { it.toSerializable() }
            val json = Json.encodeToString(serializable)
            prefsRepo.saveUserExercisesJson(json)
        } catch (_: Exception) {
            // Best effort — if serialization fails, don't crash
        }
    }

    override suspend fun loadUserExercises(): List<LibraryExercise> {
        return try {
            val json = prefsRepo.userExercisesJson.first()
            if (json.isBlank()) return emptyList()
            val serializable = Json.decodeFromString<List<SerializableLibraryExercise>>(json)
            serializable.map { it.toLibraryExercise() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // =========================================================================
    // org file helpers
    // =========================================================================

    private suspend fun readFile(): String {
        return try { syncBackend.readFile(WORKOUT_LOG_FILE) } catch (e: Exception) { "" }
    }

    private fun parseFile(content: String): OrgFile {
        return if (content.isBlank()) OrgFile(sections = emptyList())
               else OrgParser.parse(content, OrgParser.ParseMode.WORKOUT)
    }

    /**
     * Insert or replace the section for the given date.
     * Sections are maintained sorted by date descending (newest first).
     */
    private fun upsertSection(
        sections: List<OrgDateSection>,
        newSection: OrgDateSection
    ): List<OrgDateSection> {
        val mutableSections = sections.toMutableList()
        val existingIndex = mutableSections.indexOfFirst { it.date == newSection.date }
        if (existingIndex >= 0) {
            mutableSections[existingIndex] = newSection
        } else {
            val insertAt = mutableSections.indexOfFirst { it.date < newSection.date }.let {
                if (it < 0) mutableSections.size else it
            }
            mutableSections.add(insertAt, newSection)
        }
        return mutableSections
    }

    /**
     * Add or append an exercise to a date's workout.
     * Creates the date section if it doesn't exist.
     */
    private fun upsertExercise(
        sections: List<OrgDateSection>,
        date: LocalDate,
        exercise: OrgExerciseLog,
        splitDay: String? = null
    ): List<OrgDateSection> {
        val mutableSections = sections.toMutableList()
        val sectionIndex = mutableSections.indexOfFirst { it.date == date }
        if (sectionIndex < 0) {
            // Create a new date section with this exercise
            val newSection = OrgDateSection(
                date = date,
                meals = emptyList(),
                exercises = emptyList(),
                exerciseLogs = listOf(exercise),
                splitDay = splitDay
            )
            val insertAt = mutableSections.indexOfFirst { it.date < date }.let {
                if (it < 0) mutableSections.size else it
            }
            mutableSections.add(insertAt, newSection)
        } else {
            val section = mutableSections[sectionIndex]
            // Replace if same id exists, otherwise append
            val existingIdx = section.exerciseLogs.indexOfFirst { it.id == exercise.id }
            val updatedLogs = if (existingIdx >= 0) {
                section.exerciseLogs.toMutableList().also { it[existingIdx] = exercise }
            } else {
                section.exerciseLogs + exercise
            }
            mutableSections[sectionIndex] = section.copy(
                exerciseLogs = updatedLogs,
                splitDay = section.splitDay ?: splitDay
            )
        }
        return mutableSections
    }

    /**
     * Remove an exercise by id from a date's section.
     */
    private fun deleteExercise(
        sections: List<OrgDateSection>,
        date: LocalDate,
        exerciseId: Long
    ): List<OrgDateSection> {
        return sections.map { section ->
            if (section.date != date) return@map section
            val updatedLogs = section.exerciseLogs.filter { it.id != exerciseId }
            section.copy(exerciseLogs = updatedLogs)
        }
    }

    /**
     * Append a set to an existing exercise (by id) in a date's section.
     * Throws if the date section or exercise is not found.
     */
    private fun appendSet(
        sections: List<OrgDateSection>,
        date: LocalDate,
        exerciseId: Long,
        set: OrgSetEntry
    ): List<OrgDateSection> {
        return sections.map { section ->
            if (section.date != date) return@map section
            val exerciseIndex = section.exerciseLogs.indexOfFirst { it.id == exerciseId }
            if (exerciseIndex < 0) {
                throw NoSuchElementException("Exercise $exerciseId not found on $date")
            }
            val exercise = section.exerciseLogs[exerciseIndex]
            // Assign set number = next after existing sets
            val numberedSet = set.copy(setNumber = exercise.sets.size + 1)
            val updatedExercise = exercise.copy(sets = exercise.sets + numberedSet)
            val updatedLogs = section.exerciseLogs.toMutableList()
            updatedLogs[exerciseIndex] = updatedExercise
            section.copy(exerciseLogs = updatedLogs)
        }
    }

    /**
     * Remove a set by set number from an exercise, then renumber remaining sets.
     */
    private fun deleteSet(
        sections: List<OrgDateSection>,
        date: LocalDate,
        exerciseId: Long,
        setNumber: Int
    ): List<OrgDateSection> {
        return sections.map { section ->
            if (section.date != date) return@map section
            val exerciseIndex = section.exerciseLogs.indexOfFirst { it.id == exerciseId }
            if (exerciseIndex < 0) return@map section
            val exercise = section.exerciseLogs[exerciseIndex]
            val filteredSets = exercise.sets.filter { it.setNumber != setNumber }
            // Renumber remaining sets to be 1-indexed
            val renumberedSets = filteredSets.mapIndexed { idx, s -> s.copy(setNumber = idx + 1) }
            val updatedExercise = exercise.copy(sets = renumberedSets)
            val updatedLogs = section.exerciseLogs.toMutableList()
            updatedLogs[exerciseIndex] = updatedExercise
            section.copy(exerciseLogs = updatedLogs)
        }
    }

    /**
     * Set the complete flag on a date's workout section.
     * Creates an empty section if none exists (no-op effectively).
     */
    private fun setComplete(
        sections: List<OrgDateSection>,
        date: LocalDate,
        complete: Boolean
    ): List<OrgDateSection> {
        val mutableSections = sections.toMutableList()
        val sectionIndex = mutableSections.indexOfFirst { it.date == date }
        if (sectionIndex >= 0) {
            mutableSections[sectionIndex] = mutableSections[sectionIndex].copy(complete = complete)
        }
        // If no section exists for this date, nothing to mark complete — no-op
        return mutableSections
    }
}

// =============================================================================
// User exercise serialization (JSON via kotlinx.serialization)
// =============================================================================

@Serializable
private data class SerializableLibraryExercise(
    val name: String,
    val categoryLabel: String,
    val muscleGroups: List<String> = emptyList(),
    val isBuiltIn: Boolean = false,
    val restSecs: Int? = null
)

private fun LibraryExercise.toSerializable(): SerializableLibraryExercise =
    SerializableLibraryExercise(
        name = this.name,
        categoryLabel = this.category.label,
        muscleGroups = this.muscleGroups,
        isBuiltIn = this.isBuiltIn,
        restSecs = this.restSecs
    )

private fun SerializableLibraryExercise.toLibraryExercise(): LibraryExercise =
    LibraryExercise(
        name = this.name,
        category = ExerciseCategory.fromLabel(this.categoryLabel),
        muscleGroups = this.muscleGroups,
        isBuiltIn = this.isBuiltIn,
        restSecs = this.restSecs
    )

// =============================================================================
// Conversion extension functions
// =============================================================================

/** WorkoutSession -> OrgDateSection */
private fun WorkoutSession.toOrgDateSection(): OrgDateSection = OrgDateSection(
    date = this.date,
    meals = emptyList(),
    exercises = emptyList(),
    exerciseLogs = this.exercises.map { it.toOrgExerciseLog() },
    splitDay = this.splitDay?.label,  // null for freestyle days
    volume = this.totalVolume.toInt(),
    durationMin = if (this.durationMin > 0) this.durationMin else null,
    complete = this.isComplete
)

/** ExerciseLog -> OrgExerciseLog */
private fun ExerciseLog.toOrgExerciseLog(): OrgExerciseLog = OrgExerciseLog(
    name = this.name,
    id = this.id,
    exerciseType = this.exerciseType.label,
    sets = this.sets.map { it.toOrgSetEntry() },
    category = this.category.label
)

/** SetLog -> OrgSetEntry */
private fun SetLog.toOrgSetEntry(): OrgSetEntry = OrgSetEntry(
    setNumber = this.setNumber,
    reps = this.reps,
    weight = this.weight,
    unit = this.unit,
    holdSecs = this.holdSecs,
    rpe = this.rpe,
    isPr = this.isPr,
    durationMin = this.durationMin,
    distanceKm = this.distanceKm
)

/**
 * OrgDateSection -> WorkoutSession.
 * Returns null only when there are no exercises AND no exercise logs (truly empty section).
 * splitDay is nullable — freestyle days without a split are valid sessions.
 */
private fun OrgDateSection.toWorkoutSession(): WorkoutSession? {
    // Prefer exerciseLogs (Phase 3 format); fall back to legacy exercises (backward compat)
    if (this.exerciseLogs.isEmpty() && this.exercises.isEmpty()) return null
    val exerciseList = if (this.exerciseLogs.isNotEmpty()) {
        this.exerciseLogs.map { it.toExerciseLog() }
    } else {
        // Convert legacy flat exercises to ExerciseLog with synthesized single set
        this.exercises.mapIndexed { index, entry ->
            ExerciseLog(
                id = System.currentTimeMillis() + index,
                name = entry.name,
                exerciseType = ExerciseType.BARBELL,
                sets = listOf(
                    SetLog(
                        setNumber = 1,
                        reps = entry.reps,
                        weight = entry.weight,
                        unit = entry.unit
                    )
                )
            )
        }
    }
    // splitDay is nullable — null = freestyle day (valid)
    val splitDayParsed = this.splitDay?.let { SplitDay.fromLabel(it) }
    return WorkoutSession(
        date = this.date,
        splitDay = splitDayParsed,
        templateName = splitDayParsed?.displayName,
        exercises = exerciseList,
        durationMin = this.durationMin ?: 0,
        isComplete = this.complete
    )
}

/** OrgExerciseLog -> ExerciseLog */
private fun OrgExerciseLog.toExerciseLog(): ExerciseLog {
    val exerciseType = ExerciseType.fromLabel(this.exerciseType)
    val category = this.category?.let { ExerciseCategory.fromLabel(it) } ?: exerciseType.toCategory()
    return ExerciseLog(
        id = this.id,
        name = this.name,
        exerciseType = exerciseType,
        category = category,
        sets = this.sets.map { it.toSetLog() }
    )
}

/** OrgSetEntry -> SetLog */
private fun OrgSetEntry.toSetLog(): SetLog = SetLog(
    setNumber = this.setNumber,
    reps = this.reps,
    weight = this.weight,
    unit = this.unit,
    holdSecs = this.holdSecs,
    rpe = this.rpe,
    isPr = this.isPr,
    durationMin = this.durationMin,
    distanceKm = this.distanceKm
)
