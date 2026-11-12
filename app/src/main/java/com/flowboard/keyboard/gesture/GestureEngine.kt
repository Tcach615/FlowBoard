package com.flowboard.keyboard.gesture

/**
 * Two-finger chord state machine.
 *
 * Rules, all of which are covered by unit tests:
 *  - Fingers are identified by pointer id and assigned to a role by the zone they land in,
 *    never by the order Android reports them.
 *  - A third or fourth finger is ignored instead of corrupting the pair.
 *  - A direction is captured as soon as it is reached and stays captured for the gesture,
 *    so fingers may move at different speeds.
 *  - The chord is committed once, when both directions are known and one finger lifts.
 *  - While a finger stays on screen after a commit nothing else is committed (sticky lock),
 *    which prevents duplicate insertions.
 *  - If the second direction never arrives inside [Config.syncWindowMs], the gesture is
 *    rejected rather than guessed.
 */
class GestureEngine(
    private var config: Config = Config(),
    private val listener: Listener
) {
    data class Config(
        /** How long a chord may stay half-finished before it is rejected, in milliseconds. */
        val syncWindowMs: Long = 240L
    )

    interface Listener {
        fun onSnapshot(snapshot: Snapshot)
        fun onChordCommitted(chord: Chord)
        fun onGestureRejected(reason: RejectionReason)
    }

    enum class Phase { IDLE, TRACKING, ARMED, COMMITTED, REJECTED }

    enum class RejectionReason { INCOMPLETE_DIRECTIONS, FINGERS_OUT_OF_SYNC, OUT_OF_ZONE, CANCELLED }

    data class Snapshot(
        val phase: Phase,
        val leftDirection: Direction?,
        val rightDirection: Direction?,
        val previewChord: Chord?,
        val activePointers: Int,
        val lastChord: Chord?,
        val lastRejection: RejectionReason?
    )

    private class Tracker(val id: Int, val role: FingerRole, var x: Float, var y: Float)

    private val trackers = LinkedHashMap<Int, Tracker>()
    private var capturedLeft: Direction? = null
    private var capturedRight: Direction? = null
    private var commitIssued = false
    private var locked = false
    private var windowDeadline: Long? = null
    private var lastChord: Chord? = null
    private var lastRejection: RejectionReason? = null
    private var phase: Phase = Phase.IDLE
    private var layout: ZoneLayout? = null

    val snapshot: Snapshot
        get() = Snapshot(
            phase = phase,
            leftDirection = activeDirection(FingerRole.LEFT),
            rightDirection = activeDirection(FingerRole.RIGHT),
            previewChord = previewChord(),
            activePointers = trackers.size,
            lastChord = lastChord,
            lastRejection = lastRejection
        )

    fun updateConfig(newConfig: Config) {
        config = newConfig
    }

    fun setLayout(newLayout: ZoneLayout) {
        layout = newLayout
        publish()
    }

    /** @return true when the pointer was accepted into the gesture. */
    fun onDown(id: Int, x: Float, y: Float, timeMs: Long): Boolean {
        val zoneLayout = layout ?: return false
        if (locked) return false
        if (trackers.containsKey(id)) return false
        if (trackers.size >= 2) return false

        windowDeadline?.let { if (timeMs > it) expireWindow(timeMs) }

        if (windowDeadline == null && trackers.isEmpty()) {
            resetGestureState()
        }

        val role = zoneLayout.roleOf(x)
        if (trackers.values.any { it.role == role }) return false

        trackers[id] = Tracker(id, role, x, y)
        zoneLayout.direction(role, x, y)?.let { capture(role, it) }
        updatePhase()
        publish()
        return true
    }

    fun onMove(id: Int, x: Float, y: Float, timeMs: Long) {
        val zoneLayout = layout ?: return
        if (locked) return
        val tracker = trackers[id] ?: return
        if (!zoneLayout.isInsideZone(tracker.role, x, y)) {
            reject(RejectionReason.OUT_OF_ZONE)
            return
        }
        tracker.x = x
        tracker.y = y
        zoneLayout.direction(tracker.role, x, y)?.let { capture(tracker.role, it) }
        updatePhase()
        publish()
    }

    fun onUp(id: Int, x: Float, y: Float, timeMs: Long) {
        val zoneLayout = layout ?: return
        val tracker = trackers.remove(id) ?: return
        if (zoneLayout.isInsideZone(tracker.role, x, y)) {
            zoneLayout.direction(tracker.role, x, y)?.let { capture(tracker.role, it) }
        }
        if (locked) {
            if (trackers.isEmpty()) finishGesture()
            publish()
            return
        }
        tryCommit(timeMs)
        if (trackers.isEmpty() && commitIssued) finishGesture()
        publish()
    }

    fun onCancel(id: Int, timeMs: Long) {
        trackers.remove(id)
        if (!commitIssued) {
            reject(RejectionReason.CANCELLED)
        } else if (trackers.isEmpty()) {
            finishGesture()
        }
        publish()
    }

    /** Drives the sync-window timeout; call from the view's frame callback. */
    fun onTick(timeMs: Long) {
        val deadline = windowDeadline ?: return
        if (timeMs < deadline) return
        expireWindow(timeMs)
    }

    fun reset() {
        finishGesture()
        lastRejection = null
        publish()
    }

    private fun tryCommit(now: Long) {
        if (commitIssued) return
        val left = capturedLeft
        val right = capturedRight
        if (left != null && right != null) {
            commitIssued = true
            locked = true
            lastChord = Chord(left, right)
            lastRejection = null
            phase = Phase.COMMITTED
            windowDeadline = now + config.syncWindowMs
            listener.onChordCommitted(Chord(left, right))
        } else if (windowDeadline == null) {
            // One finger is already up: give the other one the sync window to contribute.
            windowDeadline = now + config.syncWindowMs
        }
    }

    private fun expireWindow(now: Long) {
        val deadline = windowDeadline ?: return
        if (now < deadline) return
        windowDeadline = null
        if (!commitIssued) {
            val reason = if (capturedLeft == null && capturedRight == null) {
                RejectionReason.INCOMPLETE_DIRECTIONS
            } else {
                RejectionReason.FINGERS_OUT_OF_SYNC
            }
            lastRejection = reason
            locked = true
            phase = Phase.REJECTED
            listener.onGestureRejected(reason)
        }
        if (trackers.isEmpty()) finishGesture()
        publish()
    }

    private fun reject(reason: RejectionReason) {
        if (commitIssued) return
        locked = true
        phase = Phase.REJECTED
        lastRejection = reason
        listener.onGestureRejected(reason)
        publish()
    }

    private fun capture(role: FingerRole, direction: Direction) {
        if (role == FingerRole.LEFT) capturedLeft = direction else capturedRight = direction
    }

    private fun activeDirection(role: FingerRole): Direction? {
        val captured = if (role == FingerRole.LEFT) capturedLeft else capturedRight
        if (captured != null) return captured
        val tracker = trackers.values.firstOrNull { it.role == role } ?: return null
        return layout?.direction(role, tracker.x, tracker.y)
    }

    private fun previewChord(): Chord? {
        val left = activeDirection(FingerRole.LEFT) ?: return null
        val right = activeDirection(FingerRole.RIGHT) ?: return null
        return Chord(left, right)
    }

    private fun updatePhase() {
        phase = when {
            commitIssued -> Phase.COMMITTED
            capturedLeft != null && capturedRight != null -> Phase.ARMED
            trackers.isNotEmpty() -> Phase.TRACKING
            else -> Phase.IDLE
        }
    }

    private fun resetGestureState() {
        capturedLeft = null
        capturedRight = null
        commitIssued = false
        locked = false
        windowDeadline = null
        lastRejection = null
        phase = Phase.IDLE
    }

    private fun finishGesture() {
        trackers.clear()
        resetGestureState()
    }

    private fun publish() {
        listener.onSnapshot(snapshot)
    }
}
