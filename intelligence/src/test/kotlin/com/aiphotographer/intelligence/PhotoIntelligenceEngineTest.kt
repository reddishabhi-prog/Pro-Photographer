package com.aiphotographer.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Each test below is a real photographic situation with an answer a working
 * photographer would agree with. These are the engine's acceptance criteria.
 */
class PhotoIntelligenceEngineTest {

    private fun codes(guidance: Guidance): Set<String> =
        guidance.hints.map { it.code }.toSet()

    // -----------------------------------------------------------------
    @Test
    fun `backlit indoor portrait is caught and corrected`() {
        // Subject stood in front of a bright window. Auto-exposure metered for
        // the window, so the face is dark. Also centred, with too much
        // headroom - the classic phone snapshot.
        val subjectRows = 2..5
        val subjectCols = 2..3
        val input = FrameInput(
            people = listOf(Fixtures.person(0.32f, 0.26f, 0.68f, 1.0f, Fixtures.UPPER_BODY)),
            faces = listOf(Fixtures.face(0.40f, 0.26f, 0.60f, 0.46f)),
            labels = listOf(SceneLabel("person", 0.9f), SceneLabel("window", 0.5f)),
            luma = LumaStats(
                mean = 150f, p5 = 35f, p95 = 240f,
                clippedShadows = 0.04f, clippedHighlights = 0.06f,
                regions = Fixtures.grid { r, c ->
                    if (r in subjectRows && c in subjectCols) 55f else 215f
                },
            ),
            color = ColorStats(150f, 150f, 152f),
            camera = Fixtures.camera(iso = 100, shutterDenominator = 500),
            tilt = DeviceTilt(rollDeg = 0.5f),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertEquals(SceneType.PORTRAIT, g.scene)
        assertEquals(BodyFraming.HALF, g.framing)
        assertEquals(LightingType.BACKLIT, g.lighting.type)
        assertEquals(LightDirection.BEHIND, g.lighting.direction)
        assertTrue(
            "should raise exposure to save the face, got ${g.lighting.evCompensation}",
            g.lighting.evCompensation > 0.3f,
        )
        assertTrue("user must be told about the backlight", "BACKLIT" in codes(g))
        assertTrue(
            "headroom should be flagged",
            CompositionAnalyzer.TOO_MUCH_HEADROOM in g.composition.issues,
        )
        assertFalse("not a shot worth taking yet", g.readyToShoot)
        assertNotNull("a portrait should get a pose suggestion", g.poseId)
    }

    // -----------------------------------------------------------------
    @Test
    fun `well composed golden hour portrait is left alone`() {
        // Subject on the left third, eyes high, warm low sun from the side.
        // The engine's job here is to NOT interfere.
        val input = FrameInput(
            people = listOf(Fixtures.person(0.22f, 0.10f, 0.54f, 0.74f, Fixtures.UPPER_BODY)),
            faces = listOf(Fixtures.face(0.30f, 0.10f, 0.46f, 0.26f, eulerY = 5f)),
            labels = listOf(SceneLabel("person", 0.95f), SceneLabel("sunset", 0.5f)),
            luma = LumaStats(
                mean = 132f, p5 = 42f, p95 = 205f,
                clippedShadows = 0.01f, clippedHighlights = 0.02f,
                regions = Fixtures.grid { _, c -> if (c < 3) 150f else 120f },
            ),
            color = ColorStats(165f, 150f, 135f),
            camera = Fixtures.camera(iso = 100, shutterDenominator = 250),
            tilt = DeviceTilt(rollDeg = 1.0f),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertEquals(LightingType.GOLDEN, g.lighting.type)
        assertEquals(
            "must not neutralise warm light back to grey",
            WhiteBalanceMode.DAYLIGHT, g.settings.whiteBalance,
        )
        assertTrue("composition is textbook, got ${g.composition.score}", g.composition.score >= 85)
        assertTrue("should tell the user to shoot, got ${g.overallScore}", g.readyToShoot)
        assertTrue(
            "nothing is wrong, so nothing should be urgent",
            g.hints.none { it.priority == HintPriority.CRITICAL },
        )
    }

    // -----------------------------------------------------------------
    @Test
    fun `night scene uses a slow shutter and high ISO`() {
        val input = FrameInput(
            labels = listOf(SceneLabel("street", 0.6f), SceneLabel("light", 0.5f)),
            luma = LumaStats(
                mean = 38f, p5 = 4f, p95 = 190f,
                clippedShadows = 0.22f, clippedHighlights = 0.04f,
                regions = Fixtures.flatGrid(38f),
            ),
            color = ColorStats(120f, 100f, 95f),
            camera = Fixtures.camera(iso = 3200, shutterDenominator = 15),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertEquals(SceneType.NIGHT, g.scene)
        assertEquals(LightingType.LOW_LIGHT, g.lighting.type)
        assertTrue("must warn about the dark", "LOW_LIGHT" in codes(g))
        assertTrue(
            "needs a slow shutter, got 1/${g.settings.shutterDenominator}",
            g.settings.shutterDenominator <= 60,
        )
        assertTrue("needs high ISO, got ${g.settings.iso}", g.settings.iso >= 1600)
    }

    // -----------------------------------------------------------------
    @Test
    fun `food is shot from directly above`() {
        val input = FrameInput(
            labels = listOf(
                SceneLabel("food", 0.85f),
                SceneLabel("plate", 0.7f),
                SceneLabel("dish", 0.6f),
            ),
            luma = LumaStats(
                mean = 145f, p5 = 50f, p95 = 210f,
                clippedShadows = 0.01f, clippedHighlights = 0.02f,
                regions = Fixtures.flatGrid(145f),
            ),
            color = ColorStats(150f, 145f, 140f),
            camera = Fixtures.camera(iso = 200, shutterDenominator = 60),
            // Phone held flat, pointing down at the table.
            tilt = DeviceTilt(pitchDeg = -60f),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertEquals(SceneType.FOOD, g.scene)
        assertEquals(ShootingAngle.HIGH, g.angle)
        assertTrue("should suggest a flat-lay", "ANGLE_HIGH_FOOD" in codes(g))
    }

    // -----------------------------------------------------------------
    @Test
    fun `full body shot with cut off feet and a tilted horizon is rejected`() {
        val input = FrameInput(
            people = listOf(Fixtures.person(0.30f, 0.05f, 0.70f, 1.0f, Fixtures.FULL_BODY)),
            faces = listOf(Fixtures.face(0.44f, 0.05f, 0.56f, 0.17f)),
            labels = listOf(SceneLabel("person", 0.9f)),
            luma = LumaStats(
                mean = 130f, p5 = 40f, p95 = 200f,
                clippedShadows = 0.02f, clippedHighlights = 0.02f,
                regions = Fixtures.flatGrid(130f),
            ),
            color = Fixtures.neutralColor(140f),
            camera = Fixtures.camera(iso = 100, shutterDenominator = 250),
            tilt = DeviceTilt(rollDeg = 12f),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertEquals(SceneType.FULL_BODY, g.scene)
        assertEquals(BodyFraming.FULL, g.framing)
        assertTrue("cropped limbs must be caught", CompositionAnalyzer.LIMBS_CROPPED in codes(g))
        assertTrue("tilt must be caught", CompositionAnalyzer.HORIZON_TILTED in codes(g))
        assertFalse(g.readyToShoot)
        assertEquals(
            "low angle lengthens a standing body",
            ShootingAngle.SLIGHTLY_LOW, g.angle,
        )
    }

    // -----------------------------------------------------------------
    @Test
    fun `group of four gets group advice and a group pose`() {
        val input = FrameInput(
            people = listOf(
                Fixtures.person(0.05f, 0.30f, 0.28f, 0.95f, Fixtures.UPPER_BODY),
                Fixtures.person(0.28f, 0.28f, 0.50f, 0.95f, Fixtures.UPPER_BODY),
                Fixtures.person(0.50f, 0.29f, 0.72f, 0.95f, Fixtures.UPPER_BODY),
                Fixtures.person(0.72f, 0.31f, 0.95f, 0.95f, Fixtures.UPPER_BODY),
            ),
            faces = listOf(
                Fixtures.face(0.12f, 0.30f, 0.22f, 0.42f),
                Fixtures.face(0.34f, 0.28f, 0.44f, 0.40f),
                Fixtures.face(0.56f, 0.29f, 0.66f, 0.41f),
                Fixtures.face(0.79f, 0.31f, 0.89f, 0.43f),
            ),
            labels = listOf(SceneLabel("person", 0.95f), SceneLabel("people", 0.9f)),
            luma = LumaStats(
                mean = 140f, p5 = 50f, p95 = 205f,
                clippedShadows = 0.01f, clippedHighlights = 0.02f,
                regions = Fixtures.flatGrid(140f),
            ),
            color = Fixtures.neutralColor(145f),
            camera = Fixtures.camera(iso = 200, shutterDenominator = 125),
            tilt = DeviceTilt(rollDeg = 1f),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertEquals(SceneType.GROUP, g.scene)
        assertTrue(
            "expected a group pose, got ${g.poseId}",
            g.poseId in setOf("group_stagger", "group_close_in"),
        )
        assertTrue("GROUP_TIGHTEN" in codes(g))
    }

    // -----------------------------------------------------------------
    @Test
    fun `harsh midday sun is detected and exposure is protected`() {
        val input = FrameInput(
            people = listOf(Fixtures.person(0.30f, 0.12f, 0.66f, 0.90f, Fixtures.UPPER_BODY)),
            faces = listOf(Fixtures.face(0.40f, 0.12f, 0.58f, 0.32f)),
            labels = listOf(SceneLabel("person", 0.9f), SceneLabel("sun", 0.5f)),
            luma = LumaStats(
                mean = 168f, p5 = 25f, p95 = 252f,
                clippedShadows = 0.05f, clippedHighlights = 0.16f,
                regions = Fixtures.grid { r, _ -> if (r < 3) 230f else 120f },
            ),
            color = Fixtures.neutralColor(150f),
            camera = Fixtures.camera(iso = 50, shutterDenominator = 2500),
            tilt = DeviceTilt(rollDeg = 1f),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertEquals(LightingType.HARSH, g.lighting.type)
        assertTrue(
            "should pull exposure down to save highlights, got ${g.lighting.evCompensation}",
            g.lighting.evCompensation < 0f,
        )
        assertTrue("should suggest shade", "HARSH" in codes(g))
    }

    // -----------------------------------------------------------------
    @Test
    fun `wide dynamic range landscape turns on HDR and widens the frame`() {
        val input = FrameInput(
            labels = listOf(
                SceneLabel("mountain", 0.9f),
                SceneLabel("sky", 0.85f),
                SceneLabel("cloud", 0.6f),
            ),
            luma = LumaStats(
                mean = 120f, p5 = 12f, p95 = 246f,
                clippedShadows = 0.07f, clippedHighlights = 0.06f,
                regions = Fixtures.grid { r, _ -> if (r < 2) 225f else 70f },
            ),
            color = ColorStats(140f, 145f, 155f),
            camera = Fixtures.camera(iso = 50, shutterDenominator = 1000),
            tilt = DeviceTilt(rollDeg = 0.5f),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertEquals(SceneType.LANDSCAPE, g.scene)
        assertTrue("sky and shadow both clipping: HDR belongs on", g.settings.useHdr)
        assertEquals("16:9", g.settings.aspectRatio)
    }

    // -----------------------------------------------------------------
    @Test
    fun `moving subject is frozen and HDR is suppressed`() {
        val input = FrameInput(
            people = listOf(Fixtures.person(0.30f, 0.10f, 0.66f, 0.95f, Fixtures.FULL_BODY)),
            faces = listOf(Fixtures.face(0.42f, 0.10f, 0.56f, 0.26f)),
            labels = listOf(SceneLabel("person", 0.9f)),
            luma = LumaStats(
                mean = 140f, p5 = 45f, p95 = 210f,
                clippedShadows = 0.01f, clippedHighlights = 0.02f,
                regions = Fixtures.flatGrid(140f),
            ),
            color = Fixtures.neutralColor(145f),
            camera = Fixtures.camera(iso = 50, shutterDenominator = 2000),
            subjectMotion = 0.75f,
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertTrue(
            "must freeze motion, got 1/${g.settings.shutterDenominator}",
            g.settings.shutterDenominator >= 500,
        )
        assertFalse("HDR on a moving subject would ghost", g.settings.useHdr)
    }

    // -----------------------------------------------------------------
    @Test
    fun `crooked document is called out`() {
        val input = FrameInput(
            labels = listOf(
                SceneLabel("document", 0.9f),
                SceneLabel("text", 0.8f),
                SceneLabel("paper", 0.7f),
            ),
            luma = LumaStats(
                mean = 200f, p5 = 120f, p95 = 250f,
                clippedShadows = 0f, clippedHighlights = 0.01f,
                regions = Fixtures.flatGrid(200f),
            ),
            color = ColorStats(205f, 203f, 200f),
            camera = Fixtures.camera(iso = 100, shutterDenominator = 100),
            tilt = DeviceTilt(rollDeg = 5f),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertEquals(SceneType.DOCUMENT, g.scene)
        assertTrue(CompositionAnalyzer.DOCUMENT_NOT_SQUARE in codes(g))
    }

    // -----------------------------------------------------------------
    @Test
    fun `subject looking out of frame is given looking room`() {
        // Subject sits on the right of frame and is turned further right, so
        // their gaze runs straight out of the picture.
        val input = FrameInput(
            people = listOf(Fixtures.person(0.58f, 0.12f, 0.94f, 0.80f, Fixtures.UPPER_BODY)),
            faces = listOf(Fixtures.face(0.68f, 0.12f, 0.86f, 0.32f, eulerY = -30f)),
            labels = listOf(SceneLabel("person", 0.9f)),
            luma = LumaStats(
                mean = 140f, p5 = 45f, p95 = 205f,
                clippedShadows = 0.01f, clippedHighlights = 0.02f,
                regions = Fixtures.flatGrid(140f),
            ),
            color = Fixtures.neutralColor(145f),
            camera = Fixtures.camera(iso = 200, shutterDenominator = 125),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertTrue(
            CompositionAnalyzer.NO_LOOKING_ROOM in g.composition.issues,
        )
        assertTrue(
            "target should move left so the gaze has room, got ${g.composition.targetCenterX}",
            g.composition.targetCenterX < 0.58f,
        )
    }

    // -----------------------------------------------------------------
    @Test
    fun `a terrible frame never floods the viewfinder with advice`() {
        // Dark, backlit, badly tilted, tiny centred subject. Everything is
        // wrong, but the user still has to be able to see through the screen.
        val input = FrameInput(
            people = listOf(Fixtures.person(0.46f, 0.40f, 0.56f, 0.62f, Fixtures.UPPER_BODY)),
            faces = listOf(Fixtures.face(0.48f, 0.40f, 0.54f, 0.48f)),
            labels = listOf(SceneLabel("person", 0.6f)),
            luma = LumaStats(
                mean = 90f, p5 = 5f, p95 = 250f,
                clippedShadows = 0.18f, clippedHighlights = 0.14f,
                regions = Fixtures.grid { r, c -> if (r in 2..3 && c == 3) 40f else 220f },
            ),
            color = ColorStats(130f, 130f, 140f),
            camera = Fixtures.camera(iso = 2000, shutterDenominator = 30),
            tilt = DeviceTilt(rollDeg = 15f),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertTrue("at most ${HintBuilder.MAX_HINTS} hints, got ${g.hints.size}",
            g.hints.size <= HintBuilder.MAX_HINTS)
        assertEquals(
            "the most urgent problem must come first",
            g.hints.map { it.priority.weight }.sortedDescending(),
            g.hints.map { it.priority.weight },
        )
        assertTrue(
            "light and tilt are real problems here, not polish",
            g.hints.first().priority >= HintPriority.IMPORTANT,
        )
        assertTrue("should score badly, got ${g.overallScore}", g.overallScore < 60)
        assertFalse(g.readyToShoot)
    }

    // -----------------------------------------------------------------
    @Test
    fun `the same frame always gives the same answer`() {
        // A viewfinder that changes its mind frame to frame is unusable, so
        // the engine must be completely deterministic.
        val input = FrameInput(
            people = listOf(Fixtures.person(0.30f, 0.20f, 0.70f, 0.95f, Fixtures.UPPER_BODY)),
            faces = listOf(Fixtures.face(0.42f, 0.20f, 0.58f, 0.38f)),
            labels = listOf(SceneLabel("person", 0.9f)),
            luma = LumaStats(
                mean = 140f, p5 = 40f, p95 = 210f,
                clippedShadows = 0.02f, clippedHighlights = 0.03f,
                regions = Fixtures.flatGrid(140f),
            ),
            color = Fixtures.neutralColor(),
            camera = Fixtures.camera(iso = 200, shutterDenominator = 125),
            tilt = DeviceTilt(rollDeg = 4f),
        )

        val first = PhotoIntelligenceEngine.analyse(input)
        val second = PhotoIntelligenceEngine.analyse(input)

        assertEquals(first.scene, second.scene)
        assertEquals(first.overallScore, second.overallScore)
        assertEquals(first.hints.map { it.code }, second.hints.map { it.code })
        assertEquals(first.settings, second.settings)
    }

    // -----------------------------------------------------------------
    @Test
    fun `an empty frame does not crash the engine`() {
        // The very first frames after the camera opens can carry nothing at all.
        val input = FrameInput(
            luma = LumaStats(
                mean = 0f, p5 = 0f, p95 = 0f,
                clippedShadows = 1f, clippedHighlights = 0f,
                regions = Fixtures.flatGrid(0f),
            ),
            color = ColorStats(0f, 0f, 0f),
            camera = CameraState(iso = 0, exposureTimeNs = 0, aperture = 0f, focalLengthMm = 0f),
        )

        val g = PhotoIntelligenceEngine.analyse(input)

        assertNotNull(g)
        assertTrue(g.overallScore in 0..100)
        assertTrue(g.settings.iso in ExposureMath.MIN_ISO..ExposureMath.MAX_ISO)
        assertTrue(g.settings.shutterDenominator > 0)
    }
}
