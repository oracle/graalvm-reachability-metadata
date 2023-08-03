/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jgit.org_eclipse_jgit;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.internal.storage.file.FileRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrgEclipseJGitTest {

    private static final String TMP_DIR = System.getProperty("java.io.tmpdir");

    private Repository remoteRepository;

    @BeforeAll
    void beforeAll() throws Exception {
        remoteRepository = createFileRepository("remote");
    }

    @AfterAll
    void afterAll() {
        if (remoteRepository != null) {
            remoteRepository.close();
        }
    }

    @Test
    void test() throws Exception {
        try (Repository localRepository = createFileRepository("local")) {
            StoredConfig config = localRepository.getConfig();
            RemoteConfig remoteConfig = new RemoteConfig(config, "test");
            remoteConfig.addURI(new URIish(remoteRepository.getDirectory().toURI().toURL()));
            remoteConfig.update(config);
            config.save();

            Git git = new Git(localRepository);
            RevCommit commit = git.commit().setMessage("initial commit").call();
            git.push().setRemote("test").setRefSpecs(new RefSpec("refs/heads/master:refs/heads/x")).call();

            ObjectId remoteCommitId = remoteRepository.resolve(commit.getId().getName() + "^{commit}");
            assertThat(remoteCommitId).isEqualTo(commit.getId());
        }
    }

    private FileRepository createFileRepository(String suffix) throws Exception {
        File gitDir = new File(TMP_DIR, "test_jgit_" + suffix + "_repo_" + System.currentTimeMillis() + "/.git");
        FileRepository fileRepository = new FileRepository(gitDir);
        fileRepository.create();
        return fileRepository;
    }

}
