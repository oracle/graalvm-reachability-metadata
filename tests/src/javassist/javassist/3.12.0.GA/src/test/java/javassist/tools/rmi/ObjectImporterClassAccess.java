/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist.tools.rmi;

public final class ObjectImporterClassAccess {
    private ObjectImporterClassAccess() {
    }

    public static Class<?> loadImporterClassByGeneratedAccessor() {
        return ObjectImporter.class$("javassist.tools.rmi.ObjectImporter");
    }
}
