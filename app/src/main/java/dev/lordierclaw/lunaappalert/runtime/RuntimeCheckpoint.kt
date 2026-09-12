package dev.lordierclaw.lunaappalert.runtime

import dev.lordierclaw.lunaappalert.core.*
import org.json.JSONObject

data class RuntimeCheckpoint(val bootCount: Int, val wallMillis: Long, val evaluation: EvaluationState,
    val session: SessionCheckpoint?) {
    fun encode(): String = JSONObject().apply {
        put("boot",bootCount); put("wall",wallMillis)
        put("date",evaluation.currentDate)
        put("daily", JSONObject(evaluation.dailyFired))
        put("sessionId",evaluation.sessionId ?: JSONObject.NULL)
        put("occurrences",JSONObject(evaluation.sessionOccurrences))
        session?.let { put("session",JSONObject().apply {
            put("package",it.packageName); put("id",it.sessionId)
            put("start",it.startedAtElapsedMillis); put("last",it.lastTick)
        }) }
    }.toString()

    companion object {
        fun decode(json: String?): RuntimeCheckpoint? = runCatching {
            if (json == null) return null
            val root = JSONObject(json)
            val daily = root.optJSONObject("daily") ?: JSONObject()
            val occurrences = root.optJSONObject("occurrences") ?: JSONObject()
            val session = root.optJSONObject("session")?.let {
                SessionCheckpoint(it.getString("package"),it.getString("id"),it.getLong("start"),it.getLong("last"))
            }
            RuntimeCheckpoint(root.getInt("boot"),root.getLong("wall"),EvaluationState(
                root.optString("date"), daily.keys().asSequence().associateWith { daily.getBoolean(it) },
                root.optString("sessionId").takeUnless { it.isBlank() || it == "null" },
                occurrences.keys().asSequence().associateWith { occurrences.getInt(it) }), session)
        }.getOrNull()
    }
}
