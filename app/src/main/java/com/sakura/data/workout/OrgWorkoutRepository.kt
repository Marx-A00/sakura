package com.sakura.data.workout

import com.sakura.orgengine.OrgDateSection
import com.sakura.orgengine.OrgExerciseLog
import com.sakura.orgengine.OrgFile
import com.sakura.orgengine.OrgParser
import com.sakura.orgengine.OrgSetEntry
import com.sakura.orgengine.OrgWriter
import com.sakura.sync.SyncBackend
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Org-file-backed implementation of WorkoutRepository.
 *
 * Manages workout-log.org via SyncBackend using the Phase 3 per-set format:
 *   * <date>
 *   ** Workout
 *   :PROPERTIES:
 *   :split_day: monday-lift
 *   :volume: 12450
 *   :duration_min: 62
 *   :END:
 *   *** Exercise Name
 *   :PROPERTIES:
 *   :id: 1712661600000
 *   :exercise_type: barbell
 *   :END:
 *   **** Set 1
 *   :PROPERTIES:
 *   :reps: 5
 *   :weight: 80
 *   :unit: kg
 *   :is_pr: false
 *   :END:
 *
 * Thread safety: all file mutations (saveSession) are serialized through [fileMutex].
 * Read operations do not acquire the mutex.
 *
 * PR detection note: findPersonalBest reads history BEFORE the session is written to avoid
 * including the current session in the comparison. The caller (ViewModel) calls findPersonalBest
 * before calling saveSession.
 */
class OrgWorkoutRepository(
    private val syncBackend: SyncBackend,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : WorkoutRepository {

    private val fileMutex = Mutex()

    companion object {
        const val WORKOUT_LOG_FILE = "workout-log.org"
    }

    // =========================================================================
    // Mutations
    // =========================================================================

    override suspend fun saveSession(session: WorkoutSession): Result<Unit> {
        return fileMutex.withLock {
            withContext(ioDispatcher) {
                try {
                    val content = try { syncBackend.readFile(WORKOUT_LOG_FILE) } catch (e: Exception) { "" }
                    val orgFile = if (content.isBlank()) OrgFile(sections = emptyList())
                                  else OrgParser.parse(content, OrgParser.ParseMode.WORKOUT)

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
    // Reads
    // =========================================================================

    override suspend fun loadSession(date: LocalDate): WorkoutSession? {
        return try {
            val content = try { syncBackend.readFile(WORKOUT_LOG_FILE) } catch (e: Exception) { return null }
            if (content.isBlank()) return null
            val orgFile = OrgParser.parse(content, OrgParser.ParseMode.WORKOUT)
            orgFile.sections.find { it.date == date }?.toWorkoutSession()
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun loadHistory(): List<WorkoutSession> {
        return try {
            val content = try { syncBackend.readFile(WORKOUT_LOG_FILE) } catch (e: Exception) { return emptyList() }
            if (content.isBlank()) return emptyList()
            val orgFile = OrgParser.parse(content, OrgParser.ParseMode.WORKOUT)
            orgFile.sections.mapNotNull { it.toWorkoutSession() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun findPersonalBest(exerciseName: String): PersonalBest? {
        return try {
            val content = try { syncBackend.readFile(WORKOUT_LOG_FILE) } catch (e: Exception) { return null }
            if (content.isBlank()) return null
            val orgFile = OrgParser.parse(content, OrgParser.ParseMode.WORKOUT)

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

            // Return null if no history for this exercise (first time)
            if (!foundAny) null
            else PersonalBest(weight = bestWeight, reps = bestReps, holdSecs = bestHoldSecs)
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun loadLastSessionForSplitDay(splitDay: SplitDay): WorkoutSession? {
        return try {
            val content = try { syncBackend.readFile(WORKOUT_LOG_FILE) } catch (e: Exception) { return null }
            if (content.isBlank()) return null
            val orgFile = OrgParser.parse(content, OrgParser.ParseMode.WORKOUT)

            // Sections are stored newest-first; find the first matching split day
            orgFile.sections
                .firstOrNull { it.splitDay == splitDay.label }
                ?.toWorkoutSession()
        } catch (e: Exception) {
            null
        }
    }

    // =========================================================================
    // org file manipulation helpers
    // =========================================================================

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
            // Replace existing section for this date
            mutableSections[existingIndex] = newSection
        } else {
            // Insert in sorted position (newest first)
            val insertAt = mutableSections.indexOfFirst { it.date < newSection.date }.let {
                if (it < 0) mutableSections.size else it
            }
            mutableSections.add(insertAt, newSection)
        }
        return mutableSections
    }
}

// =============================================================================
// Conversion extension functions
// =============================================================================

/** WorkoutSession -> OrgDateSection */
private fun WorkoutSession.toOrgDateSection(): OrgDateSection = OrgDateSection(
    date = this.date,
    meals = emptyList(),
    exercises = emptyList(),
    exerciseLogs = this.exercises.map { it.toOrgExerciseLog() },
    splitDay = this.splitDay.label,
    volume = this.totalVolume.toInt(),
    durationMin = if (this.durationMin > 0) this.durationMin else null
)

/** ExerciseLog -> OrgExerciseLog */
private fun ExerciseLog.toOrgExerciseLog(): OrgExerciseLog = OrgExerciseLog(
    name = this.name,
    id = this.id,
    exerciseType = this.exerciseType.label,
    sets = this.sets.map { it.toOrgSetEntry() }
)

/** SetLog -> OrgSetEntry */
private fun SetLog.toOrgSetEntry(): OrgSetEntry = OrgSetEntry(
    setNumber = this.setNumber,
    reps = this.reps,
    weight = this.weight,
    unit = this.unit,
    holdSecs = this.holdSecs,
    rpe = this.rpe,
    isPr = this.isPr
)

/** OrgDateSection -> WorkoutSession (returns null if no exerciseLogs and no exercises) */
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
    val splitDayParsed = this.splitDay?.let { SplitDay.fromLabel(it) } ?: return null
    return WorkoutSession(
        date = this.date,
        splitDay = splitDayParsed,
        exercises = exerciseList,
        durationMin = this.durationMin ?: 0
    )
}

/** OrgExerciseLog -> ExerciseLog */
private fun OrgExerciseLog.toExerciseLog(): ExerciseLog = ExerciseLog(
    id = this.id,
    name = this.name,
    exerciseType = ExerciseType.fromLabel(this.exerciseType),
    sets = this.sets.map { it.toSetLog() }
)

/** OrgSetEntry -> SetLog */
private fun OrgSetEntry.toSetLog(): SetLog = SetLog(
    setNumber = this.setNumber,
    reps = this.reps,
    weight = this.weight,
    unit = this.unit,
    holdSecs = this.holdSecs,
    rpe = this.rpe,
    isPr = this.isPr
)
