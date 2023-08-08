/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jgit.org_eclipse_jgit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgEclipseJGitTest {

    private static final Logger logger = LoggerFactory.getLogger("OrgEclipseJGitTest");

    private static final String TMP_DIR = System.getProperty("java.io.tmpdir");

    private static final String TEST_FILE_NAME = "Test.txt";

    private static final String GIT_DIR_NAME = ".git";

    private File remoteRepositoryDir;

    private Repository remoteRepository;

    private Git remoteGit;

    @BeforeAll
    void beforeAll() throws Exception {
        remoteRepositoryDir = new File(TMP_DIR, "test_jgit_remote_repo_" + System.currentTimeMillis());
        remoteRepository = new FileRepository(new File(remoteRepositoryDir, GIT_DIR_NAME));
        remoteRepository.create();
        logger.info("Created remote repository: {}", remoteRepositoryDir);
        remoteGit = new Git(remoteRepository);
        writeFile(remoteRepository, TEST_FILE_NAME, "Test Message");
        remoteGit.add().addFilepattern(TEST_FILE_NAME).call();
        RevCommit revCommit = remoteGit.commit().setMessage("Initial commit").call();
        logger.info("Committed '{}' file to remote repository: {}", TEST_FILE_NAME, revCommit.getName());
    }

    @AfterAll
    void afterAll() {
        if (remoteRepository != null) {
            remoteRepository.close();
        }
        deleteDir(remoteRepositoryDir);
    }

    @Test
    void test() throws Exception {
        File localRepositoryDir = new File(TMP_DIR, "test_jgit_local_repo_" + System.currentTimeMillis());
        try {
            Git localGit = Git.cloneRepository()
                    .setDirectory(localRepositoryDir)
                    .setURI("file://" + remoteGit.getRepository().getWorkTree().getPath())
                    .call();
            logger.info("Created local repository: {}", localRepositoryDir);

            File[] localFiles = localRepositoryDir.listFiles();
            if (localFiles != null) {
                logger.info("Found files in local repository: {}", Arrays.toString(localFiles));
                assertThat(localFiles)
                        .hasSize(2)
                        .extracting(File::getName)
                        .contains(TEST_FILE_NAME, GIT_DIR_NAME);
            } else {
                fail("Files not found in local repository");
            }

            localGit.getRepository().close();
        } finally {
            deleteDir(localRepositoryDir);
        }
    }

    public void writeFile(Repository repository, String name, String data) throws IOException {
        File parentFile = repository.getWorkTree();
        File file = new File(parentFile, name);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(data);
        }
    }

    private void deleteDir(File file) {
        if (file != null) {
            File[] childFiles = file.listFiles();
            if (childFiles != null) {
                for (File childFile : childFiles) {
                    deleteDir(childFile);
                }
            }
            boolean deleted = file.delete();
            if (!deleted) {
                logger.warn("File '{}' not deleted", file);
            }
        }
    }

}
