/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PortableRemoteObject {

    private static final List<Remote> EXPORTED_OBJECTS = new ArrayList<Remote>();
    private static final List<Remote> UNEXPORTED_OBJECTS = new ArrayList<Remote>();

    private PortableRemoteObject() {
    }

    public static void exportObject(Remote object) throws RemoteException {
        EXPORTED_OBJECTS.add(object);
    }

    public static void unexportObject(Remote object) throws RemoteException {
        UNEXPORTED_OBJECTS.add(object);
    }

    public static void reset() {
        EXPORTED_OBJECTS.clear();
        UNEXPORTED_OBJECTS.clear();
    }

    public static List<Remote> exportedObjects() {
        return Collections.unmodifiableList(EXPORTED_OBJECTS);
    }

    public static List<Remote> unexportedObjects() {
        return Collections.unmodifiableList(UNEXPORTED_OBJECTS);
    }
}
