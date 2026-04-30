/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.crac;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class Core {
    private static final GlobalContext GLOBAL_CONTEXT = new GlobalContext();
    private static int checkpointRestoreCount;

    private Core() {
    }

    public static Context<Resource> getGlobalContext() {
        return GLOBAL_CONTEXT;
    }

    public static void checkpointRestore() throws CheckpointException, RestoreException {
        checkpointRestoreCount++;
    }

    public static int registeredResourceCount() {
        return GLOBAL_CONTEXT.registeredResourceCount();
    }

    public static int checkpointRestoreCount() {
        return checkpointRestoreCount;
    }

    public static boolean firstRegisteredResourceEquals(Object candidate) {
        return GLOBAL_CONTEXT.firstRegisteredResourceEquals(candidate);
    }

    public static void clear() {
        GLOBAL_CONTEXT.clear();
        checkpointRestoreCount = 0;
    }

    private static final class GlobalContext implements Context<Resource> {
        private final List<Resource> resources = new ArrayList<>();

        @Override
        public void register(Resource resource) {
            resources.add(Objects.requireNonNull(resource));
        }

        private int registeredResourceCount() {
            return resources.size();
        }

        private boolean firstRegisteredResourceEquals(Object candidate) {
            return !resources.isEmpty() && resources.get(0).equals(candidate);
        }

        private void clear() {
            resources.clear();
        }
    }
}
