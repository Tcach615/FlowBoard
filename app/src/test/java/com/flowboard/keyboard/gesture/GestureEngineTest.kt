package com.flowboard.keyboard.gesture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureEngineTest {

    private class Recorder : GestureEngine.Listener {
        val chords = mutableListOf<Chord>()
        val rejections = mutableListOf<GestureEngine.RejectionReason>()
        var snapshot: GestureEngine.Snapshot? = null

        override fun onSnapshot(snapshot: GestureEngine.Snapshot) {
            this.snapshot = snapshot
        }

        override fun onChordCommitted(chord: Chord) {
            chords.add(chord)
        }

        override fun onGestureRejected(reason: GestureEngine.RejectionReason) {
            rejections.add(reason)
        }
    }

    private val layout = ZoneLayout(
        splitX = 540f,
        leftCenterX = 270f,
        leftCenterY = 600f,
        leftRadius = 200f,
        rightCenterX = 810f,
        rightCenterY = 600f,
        rightRadius = 200f
    )

    private fun engine(recorder: Recorder, syncWindowMs: Long = 240L): GestureEngine {
        val engine = GestureEngine(GestureEngine.Config(syncWindowMs = syncWindowMs), recorder)
        engine.setLayout(layout)
        return engine
    }

    private fun point(role: FingerRole, direction: Direction): Pair<Float, Float> {
        val centerX = if (role == FingerRole.LEFT) 270f else 810f
        return (centerX + direction.dx * 120f) to (600f + direction.dy * 120f)
    }

    private fun playChord(
        engine: GestureEngine,
        chord: Chord,
        leftId: Int = 1,
        rightId: Int = 2
    ) {
        val (lx, ly) = point(FingerRole.LEFT, chord.left)
        val (rx, ry) = point(FingerRole.RIGHT, chord.right)
        engine.onDown(leftId, 270f, 600f, 0)
        engine.onDown(rightId, 810f, 600f, 5)
        engine.onMove(leftId, lx, ly, 20)
        engine.onMove(rightId, rx, ry, 25)
        engine.onUp(leftId, lx, ly, 40)
        engine.onUp(rightId, rx, ry, 45)
    }

    @Test
    fun `all sixteen chords are recognized`() {
        for (chord in Chord.ALL) {
            val recorder = Recorder()
            val engine = engine(recorder)
            playChord(engine, chord)
            assertEquals(listOf(chord), recorder.chords)
        }
    }

    @Test
    fun `roles are taken from zones not from pointer order`() {
        val recorder = Recorder()
        val engine = engine(recorder)
        val (lx, ly) = point(FingerRole.LEFT, Direction.NW)
        val (rx, ry) = point(FingerRole.RIGHT, Direction.SE)

        engine.onDown(7, 810f, 600f, 0) // right zone first
        engine.onDown(4, 270f, 600f, 10)
        engine.onMove(7, rx, ry, 20)
        engine.onMove(4, lx, ly, 25)
        engine.onUp(7, rx, ry, 300)

        assertEquals(listOf(Chord(Direction.NW, Direction.SE)), recorder.chords)
    }

    @Test
    fun `fingers may contribute within the sync window`() {
        val recorder = Recorder()
        val engine = engine(recorder)
        val (lx, ly) = point(FingerRole.LEFT, Direction.NW)
        val (rx, ry) = point(FingerRole.RIGHT, Direction.SE)

        engine.onDown(1, 270f, 600f, 0)
        engine.onMove(1, lx, ly, 50)
        engine.onUp(1, lx, ly, 80)
        assertTrue(recorder.chords.isEmpty())

        engine.onDown(2, 810f, 600f, 150)
        engine.onMove(2, rx, ry, 180)
        engine.onUp(2, rx, ry, 200)

        assertEquals(listOf(Chord(Direction.NW, Direction.SE)), recorder.chords)
    }

    @Test
    fun `half a chord is rejected once the window expires`() {
        val recorder = Recorder()
        val engine = engine(recorder)
        val (lx, ly) = point(FingerRole.LEFT, Direction.NW)

        engine.onDown(1, 270f, 600f, 0)
        engine.onMove(1, lx, ly, 30)
        engine.onUp(1, lx, ly, 60)
        engine.onTick(400)

        assertTrue(recorder.chords.isEmpty())
        assertEquals(listOf(GestureEngine.RejectionReason.FINGERS_OUT_OF_SYNC), recorder.rejections)
    }

    @Test
    fun `a chord with no directions is incomplete`() {
        val recorder = Recorder()
        val engine = engine(recorder)

        engine.onDown(1, 270f, 600f, 0)
        engine.onDown(2, 810f, 600f, 10)
        engine.onUp(1, 270f, 600f, 50)
        engine.onUp(2, 810f, 600f, 60)
        engine.onTick(400)

        assertTrue(recorder.chords.isEmpty())
        assertEquals(listOf(GestureEngine.RejectionReason.INCOMPLETE_DIRECTIONS), recorder.rejections)
    }

    @Test
    fun `a committed chord is not repeated while a finger stays down`() {
        val recorder = Recorder()
        val engine = engine(recorder)
        val (lx, ly) = point(FingerRole.LEFT, Direction.NW)
        val (rx, ry) = point(FingerRole.RIGHT, Direction.NE)

        engine.onDown(1, 270f, 600f, 0)
        engine.onDown(2, 810f, 600f, 5)
        engine.onMove(1, lx, ly, 20)
        engine.onMove(2, rx, ry, 25)
        engine.onUp(1, lx, ly, 40)
        engine.onMove(2, 700f, 700f, 60) // ignored: already committed
        engine.onUp(2, 700f, 700f, 80)

        assertEquals(1, recorder.chords.size)
        assertEquals(GestureEngine.Phase.IDLE, recorder.snapshot?.phase)
    }

    @Test
    fun `consecutive chords are independent`() {
        val recorder = Recorder()
        val engine = engine(recorder)

        playChord(engine, Chord(Direction.NW, Direction.NW))
        playChord(engine, Chord(Direction.SE, Direction.SE))
        playChord(engine, Chord(Direction.NE, Direction.SW))

        assertEquals(
            listOf(
                Chord(Direction.NW, Direction.NW),
                Chord(Direction.SE, Direction.SE),
                Chord(Direction.NE, Direction.SW)
            ),
            recorder.chords
        )
    }

    @Test
    fun `third finger is ignored`() {
        val recorder = Recorder()
        val engine = engine(recorder)

        assertTrue(engine.onDown(1, 270f, 600f, 0))
        assertTrue(engine.onDown(2, 810f, 600f, 10))
        assertFalse(engine.onDown(3, 900f, 620f, 20))

        assertEquals(2, recorder.snapshot?.activePointers)
    }

    @Test
    fun `leaving the zone rejects the gesture`() {
        val recorder = Recorder()
        val engine = engine(recorder)

        engine.onDown(1, 270f, 600f, 0)
        engine.onMove(1, 270f, 1600f, 20)

        assertEquals(listOf(GestureEngine.RejectionReason.OUT_OF_ZONE), recorder.rejections)
    }

    @Test
    fun `cancelled pointer rejects the gesture`() {
        val recorder = Recorder()
        val engine = engine(recorder)

        engine.onDown(1, 270f, 600f, 0)
        engine.onCancel(1, 30)

        assertEquals(listOf(GestureEngine.RejectionReason.CANCELLED), recorder.rejections)
    }

    @Test
    fun `snapshot previews the chord while both fingers are down`() {
        val recorder = Recorder()
        val engine = engine(recorder)
        val (lx, ly) = point(FingerRole.LEFT, Direction.SW)
        val (rx, ry) = point(FingerRole.RIGHT, Direction.NE)

        engine.onDown(1, 270f, 600f, 0)
        engine.onDown(2, 810f, 600f, 5)
        engine.onMove(1, lx, ly, 20)
        engine.onMove(2, rx, ry, 25)

        val snapshot = recorder.snapshot
        assertEquals(GestureEngine.Phase.ARMED, snapshot?.phase)
        assertEquals(Chord(Direction.SW, Direction.NE), snapshot?.previewChord)
        assertNull(recorder.chords.firstOrNull())
    }
}
