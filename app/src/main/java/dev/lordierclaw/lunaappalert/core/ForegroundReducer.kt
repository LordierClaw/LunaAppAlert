package dev.lordierclaw.lunaappalert.core

/** Android UsageEvents are mapped to these events at the platform boundary. */
enum class ForegroundEventKind { RESUMED, PAUSED, SCREEN_OFF, LOCKED, SHUTDOWN, IGNORED }

data class ForegroundEvent(
    val kind: ForegroundEventKind,
    val packageName: String? = null,
    val wallMillis: Long,
)

data class ForegroundChange(val packageName: String?, val wallMillis: Long)

/**
 * Retains only the foreground owner and two cursors, never an event history.
 * The adapter may deduplicate a bounded overlapping query before calling accept.
 */
class ForegroundReducer(
    private val selfPackageName: String,
    private val homePackages: Set<String> = emptySet(),
    private val ignoredPackages: Set<String> = emptySet(),
    private val pauseEndsUsage: Boolean = false,
) {
    var currentPackageName: String? = null
        private set
    var lastBoundaryMillis: Long = 0L
        private set
    var lastPauseMillis: Long = 0L
        private set
    private var lastEventMillis = Long.MIN_VALUE

    /** A pre-midnight foreground seed lets current-day replay include its first interval. */
    fun reset(seedPackageName: String? = null, seedBoundaryMillis: Long = 0L, seedPauseMillis: Long = 0L) {
        currentPackageName = seedPackageName?.takeUnless {
            it.isBlank() || it == selfPackageName || it in homePackages || it in ignoredPackages
        }
        lastBoundaryMillis = seedBoundaryMillis
        lastPauseMillis = seedPauseMillis
        lastEventMillis = seedBoundaryMillis
    }

    fun accept(event: ForegroundEvent): ForegroundChange? {
        if (event.kind == ForegroundEventKind.IGNORED || event.wallMillis < lastEventMillis) return null
        val next = when (event.kind) {
            ForegroundEventKind.RESUMED -> {
                val pkg = event.packageName?.takeIf { it.isNotBlank() } ?: return null
                when {
                    pkg == selfPackageName || pkg in homePackages -> null
                    pkg in ignoredPackages -> return null
                    else -> pkg
                }
            }
            ForegroundEventKind.PAUSED -> {
                if (event.packageName != currentPackageName || currentPackageName == null) return null
                lastPauseMillis = maxOf(lastPauseMillis, event.wallMillis)
                // Continuous sessions survive same-package Activity transitions. Daily
                // accounting follows actual resumed intervals, including pre-28 devices
                // that have no historical screen-off or keyguard UsageEvents.
                if (!pauseEndsUsage) return null
                null
            }
            ForegroundEventKind.IGNORED -> return null
            ForegroundEventKind.SCREEN_OFF, ForegroundEventKind.LOCKED,
            ForegroundEventKind.SHUTDOWN -> null
        }
        lastEventMillis = event.wallMillis
        if (next == currentPackageName) return null
        currentPackageName = next
        lastBoundaryMillis = event.wallMillis
        return ForegroundChange(next, event.wallMillis)
    }
}
