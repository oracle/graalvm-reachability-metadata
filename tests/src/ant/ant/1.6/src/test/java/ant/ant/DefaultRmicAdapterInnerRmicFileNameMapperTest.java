/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.rmi.Remote;
import org.apache.tools.ant.taskdefs.Rmic;
import org.apache.tools.ant.taskdefs.rmic.DefaultRmicAdapter;
import org.apache.tools.ant.util.FileNameMapper;
import org.junit.jupiter.api.Test;

public class DefaultRmicAdapterInnerRmicFileNameMapperTest {
    @Test
    void mapsIiopInterfaceClassThroughConfiguredClassLoader() {
        FileNameMapper mapper = newIiopMapper();

        String[] mappedNames = mapper.mapFileName(classFileName(ExampleRemote.class));

        assertThat(mappedNames).containsExactly(
                packageDirectory() + "_DefaultRmicAdapterInnerRmicFileNameMapperTest$ExampleRemote_Stub.class");
    }

    @Test
    void mapsIiopImplementationClassThroughConfiguredClassLoader() {
        FileNameMapper mapper = newIiopMapper();

        String[] mappedNames = mapper.mapFileName(classFileName(ExampleRemoteService.class));

        assertThat(mappedNames).containsExactly(
                packageDirectory() + "_DefaultRmicAdapterInnerRmicFileNameMapperTest$ExampleRemoteService_Tie.class",
                packageDirectory() + "_DefaultRmicAdapterInnerRmicFileNameMapperTest$ExampleRemote_Stub.class");
    }

    private static FileNameMapper newIiopMapper() {
        Rmic rmic = new LoaderBackedRmic();
        rmic.setIiop(true);

        DefaultRmicAdapter adapter = new MappingOnlyRmicAdapter();
        adapter.setRmic(rmic);
        return adapter.getMapper();
    }

    private static String classFileName(Class<?> type) {
        return type.getName().replace('.', File.separatorChar) + ".class";
    }

    private static String packageDirectory() {
        return "ant" + File.separator + "ant" + File.separator;
    }

    public interface ExampleRemote extends Remote {
    }

    public static class ExampleRemoteService implements ExampleRemote {
    }

    private static final class LoaderBackedRmic extends Rmic {
        @Override
        public ClassLoader getLoader() {
            return DefaultRmicAdapterInnerRmicFileNameMapperTest.class.getClassLoader();
        }
    }

    private static final class MappingOnlyRmicAdapter extends DefaultRmicAdapter {
        @Override
        public boolean execute() {
            throw new UnsupportedOperationException("This test only exercises file-name mapping.");
        }
    }
}
