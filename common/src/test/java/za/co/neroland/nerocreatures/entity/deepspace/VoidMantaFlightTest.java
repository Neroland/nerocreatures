package za.co.neroland.nerocreatures.entity.deepspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VoidMantaFlightTest {

    @Test
    void fliesFreelyOnlyInReducedGravityOrItsOwnHabitat() {
        assertFalse(VoidMantaFlight.flightAllowed(VoidMantaFlight.VANILLA_GRAVITY, false, false),
                "normal gravity on Earth: glide only");
        assertTrue(VoidMantaFlight.flightAllowed(0.03D, false, false), "low gravity anywhere");
        assertTrue(VoidMantaFlight.flightAllowed(0.0D, false, false), "zero gravity anywhere");
        assertTrue(VoidMantaFlight.flightAllowed(VoidMantaFlight.FREE_FLIGHT_MAX_GRAVITY, false, false));
        assertFalse(VoidMantaFlight.flightAllowed(0.07D, false, false), "barely reduced is not enough");
        assertTrue(VoidMantaFlight.flightAllowed(VoidMantaFlight.VANILLA_GRAVITY, true, true),
                "its deep-space habitat on an off-Earth dimension");
        assertFalse(VoidMantaFlight.flightAllowed(VoidMantaFlight.VANILLA_GRAVITY, false, true),
                "a void-biome overworld (superflat, skyblock) does not count");
        assertFalse(VoidMantaFlight.flightAllowed(VoidMantaFlight.VANILLA_GRAVITY, true, false),
                "another off-Earth biome at normal gravity glides");
        assertFalse(VoidMantaFlight.flightAllowed(Double.NaN, false, false));
    }

    @Test
    void glideFloorCapsTheSinkButNeverAddsLift() {
        assertEquals(-VoidMantaFlight.GLIDE_SINK, VoidMantaFlight.glideVertical(-3.0D), 1.0E-9);
        assertEquals(-0.05D, VoidMantaFlight.glideVertical(-0.05D), 1.0E-9);
        assertEquals(0.4D, VoidMantaFlight.glideVertical(0.4D), 1.0E-9, "knock-ups are left alone");
        assertEquals(0.0D, VoidMantaFlight.glideVertical(Double.NaN), 1.0E-9);
    }

    @Test
    void glidingBlinkIsHorizontalAndFlyingBlinkFollowsTheLook() {
        double[] glide = VoidMantaFlight.direction(0.0F, -60.0F, false);
        assertEquals(0.0D, glide[1], 1.0E-9, "never gains height while gliding");
        assertEquals(1.0D, glide[2], 1.0E-9, "yaw 0 faces +z");
        double[] up = VoidMantaFlight.direction(90.0F, -90.0F, true);
        assertEquals(1.0D, up[1], 1.0E-9, "pitch -90 looks straight up");
        double[] any = VoidMantaFlight.direction(37.0F, 21.0F, true);
        assertEquals(1.0D, Math.sqrt(any[0] * any[0] + any[1] * any[1] + any[2] * any[2]), 1.0E-9);
    }

    @Test
    void blinkStopsShortOfBlocksAndIsCapped() {
        assertEquals(VoidMantaFlight.BLINK_DISTANCE,
                VoidMantaFlight.blinkDistance(Double.POSITIVE_INFINITY, VoidMantaFlight.BLINK_DISTANCE), 1.0E-9);
        assertEquals(VoidMantaFlight.BLINK_DISTANCE, VoidMantaFlight.blinkDistance(1000.0D, 1000.0D), 1.0E-9,
                "the cap holds whatever the inputs");
        assertEquals(5.0D - VoidMantaFlight.BLOCK_CLEARANCE,
                VoidMantaFlight.blinkDistance(5.0D, VoidMantaFlight.BLINK_DISTANCE), 1.0E-9,
                "stops short of the wall");
        assertEquals(0.0D, VoidMantaFlight.blinkDistance(2.0D, VoidMantaFlight.BLINK_DISTANCE), 1.0E-9,
                "a wall right ahead fizzles the blink rather than clipping into it");
        assertEquals(0.0D, VoidMantaFlight.blinkDistance(Double.NaN, VoidMantaFlight.BLINK_DISTANCE), 1.0E-9);
    }

    @Test
    void blinkNeverEntersUnloadedChunks() {
        assertEquals(VoidMantaFlight.BLINK_DISTANCE, VoidMantaFlight.loadedReach(1.0D, along -> true), 1.0E-9);
        // Loaded up to 5.5 blocks along the path; with a 1-block probe the last safe sample is 4.
        double reach = VoidMantaFlight.loadedReach(1.0D, along -> along <= 5.5D);
        assertEquals(4.0D, reach, 1.0E-9);
        assertEquals(4.0D, VoidMantaFlight.blinkDistance(Double.POSITIVE_INFINITY, reach), 1.0E-9);
        assertEquals(0.0D, VoidMantaFlight.loadedReach(1.0D, along -> false), 1.0E-9);
        assertEquals(0.0D, VoidMantaFlight.blinkDistance(Double.POSITIVE_INFINITY,
                VoidMantaFlight.loadedReach(1.0D, along -> along < 1.5D)), 1.0E-9, "too short: fizzles");
        // A gap in the middle stops the blink before the gap, even if the far end is loaded again.
        assertEquals(2.0D, VoidMantaFlight.loadedReach(0.0D, along -> along != 3.0D), 1.0E-9);
    }

    @Test
    void oneBlinkAtATime() {
        assertTrue(VoidMantaFlight.mayArm(0, false));
        assertFalse(VoidMantaFlight.mayArm(VoidMantaFlight.BLINK_DELAY_TICKS, false), "one is already armed");
        assertFalse(VoidMantaFlight.mayArm(0, true), "the previous pose is still playing");
        assertTrue(VoidMantaFlight.BLINK_DELAY_TICKS < VoidMantaFlight.BLINK_POSE_TICKS,
                "the jump lands inside the pose");
    }
}
