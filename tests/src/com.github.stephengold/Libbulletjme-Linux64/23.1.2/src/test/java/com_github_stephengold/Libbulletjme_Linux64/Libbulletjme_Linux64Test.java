/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_github_stephengold.Libbulletjme_Linux64;

import com.jme3.bullet.PhysicsSpace;
import com.jme3.bullet.collision.shapes.SphereCollisionShape;
import com.jme3.bullet.objects.PhysicsRigidBody;
import com.jme3.bullet.util.NativeLibrary;
import com.jme3.math.Vector3f;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import com.jme3.bullet.PhysicsTickListener;
import static org.junit.jupiter.api.Assertions.*;

class Libbulletjme_Linux64Test {

    private static final String NATIVE_RESOURCE =
            "native/linux/x86_64/libbulletjme.so";

    @BeforeAll
    static void loadNativeLibrary() throws IOException {
        Path library = Files.createTempFile(
                "libbulletjme-", ".so");

        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(NATIVE_RESOURCE)) {

            assertNotNull(input,
                    "Native library resource missing: "
                            + NATIVE_RESOURCE);

            Files.copy(input, library,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        library.toFile().deleteOnExit();

        System.load(library.toAbsolutePath().toString());
    }

    @Test
    void nativeLibraryVersionMatches() {
        String version = NativeLibrary.versionNumber();

        assertEquals(NativeLibrary.expectedVersion, version);
    }

    @Test
    void nativePhysicsSimulationMovesRigidBody() {
        PhysicsSpace space = new PhysicsSpace(
                PhysicsSpace.BroadphaseType.DBVT);

        try {
            SphereCollisionShape shape =
                    new SphereCollisionShape(0.25f);

            PhysicsRigidBody ball =
                    new PhysicsRigidBody(shape, 1f);

            ball.setPhysicsLocation(
                    new Vector3f(0f, 2f, 0f));

            space.addCollisionObject(ball);

            float initialY =
                    ball.getPhysicsLocation(null).y;

            for (int i = 0; i < 40; i++) {
                space.update(0.02f, 0);
            }

            float finalY =
                    ball.getPhysicsLocation(null).y;

            assertTrue(
                    finalY < initialY - 0.5f,
                    "Gravity did not move the ball: "
                            + initialY + " -> " + finalY);

        } finally {
            space.destroy();
        }
    }

    @Test
void nativeTickCallbacksReachJava() {
    PhysicsSpace space = new PhysicsSpace(
            PhysicsSpace.BroadphaseType.DBVT);

    int[] before = {0};
    int[] after = {0};

    PhysicsTickListener listener = new PhysicsTickListener() {
        @Override
        public void prePhysicsTick(
                PhysicsSpace source, float timeStep) {
            if (source == space && timeStep > 0f) {
                before[0]++;
            }
        }

        @Override
        public void physicsTick(
                PhysicsSpace source, float timeStep) {
            if (source == space && timeStep > 0f) {
                after[0]++;
            }
        }
    };

    space.addTickListener(listener);

    try {
        for (int i = 0; i < 8; i++) {
            space.update(0.02f, 0);
        }

        assertTrue(before[0] > 0,
                "Native preTick callback not invoked");

        assertEquals(before[0], after[0],
                "Pre/post callbacks mismatch");

    } finally {
        space.removeTickListener(listener);
        space.destroy();
    }
}




@Test
void nativeCollisionCallbackThroughPairTest() {
    PhysicsSpace space = new PhysicsSpace(
            PhysicsSpace.BroadphaseType.DBVT);

    try {
        SphereCollisionShape shape =
                new SphereCollisionShape(1f);

        PhysicsRigidBody first =
                new PhysicsRigidBody(shape, 1f);

        PhysicsRigidBody second =
                new PhysicsRigidBody(shape, 1f);

        first.setPhysicsLocation(
                new Vector3f(0f, 0f, 0f));

        second.setPhysicsLocation(
                new Vector3f(1.5f, 0f, 0f));

        int[] callbackCount = {0};
        boolean[] correctObjects = {false};
        float[] minDistance = {Float.POSITIVE_INFINITY};

        int contacts = space.pairTest(first, second, event -> {
            callbackCount[0]++;

            boolean forward =
                    event.getObjectA() == first
                    && event.getObjectB() == second;

            boolean reverse =
                    event.getObjectA() == second
                    && event.getObjectB() == first;

            correctObjects[0] |= forward || reverse;

            minDistance[0] = Math.min(
                    minDistance[0],
                    event.getDistance1());
        });

        assertTrue(contacts > 0,
                "Bullet did not detect overlapping spheres");

        assertEquals(contacts, callbackCount[0],
                "JNI callback count mismatch");

        assertTrue(correctObjects[0],
                "JNI callback returned incorrect objects");

        assertTrue(minDistance[0] < 0f,
                "Expected negative contact distance");

        // Negative control: move spheres apart.
        second.setPhysicsLocation(
                new Vector3f(5f, 0f, 0f));

        int separatedContacts =
                space.pairTest(first, second, event ->
                        fail("Unexpected collision callback"));

        assertEquals(0, separatedContacts,
                "Separated spheres should not collide");

    } finally {
        space.destroy();
    }
}















}
