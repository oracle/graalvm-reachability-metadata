/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_scm.maven_scm_api;

import java.io.File;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.maven.scm.ChangeFile;
import org.apache.maven.scm.ChangeSet;
import org.apache.maven.scm.CommandParameter;
import org.apache.maven.scm.CommandParameters;
import org.apache.maven.scm.ScmBranch;
import org.apache.maven.scm.ScmBranchParameters;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmFileStatus;
import org.apache.maven.scm.ScmRequest;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.ScmRevision;
import org.apache.maven.scm.ScmTag;
import org.apache.maven.scm.ScmTagParameters;
import org.apache.maven.scm.ScmVersion;
import org.apache.maven.scm.command.add.AddScmResult;
import org.apache.maven.scm.command.blame.BlameLine;
import org.apache.maven.scm.command.blame.BlameScmRequest;
import org.apache.maven.scm.command.blame.BlameScmResult;
import org.apache.maven.scm.command.branch.BranchScmResult;
import org.apache.maven.scm.command.changelog.ChangeLogScmRequest;
import org.apache.maven.scm.command.changelog.ChangeLogScmResult;
import org.apache.maven.scm.command.changelog.ChangeLogSet;
import org.apache.maven.scm.command.checkin.CheckInScmResult;
import org.apache.maven.scm.command.checkout.CheckOutScmResult;
import org.apache.maven.scm.command.diff.DiffScmResult;
import org.apache.maven.scm.command.edit.EditScmResult;
import org.apache.maven.scm.command.export.ExportScmResult;
import org.apache.maven.scm.command.export.ExportScmResultWithRevision;
import org.apache.maven.scm.command.info.InfoItem;
import org.apache.maven.scm.command.info.InfoScmResult;
import org.apache.maven.scm.command.list.ListScmResult;
import org.apache.maven.scm.command.login.LoginScmResult;
import org.apache.maven.scm.command.mkdir.MkdirScmResult;
import org.apache.maven.scm.command.remoteinfo.RemoteInfoScmResult;
import org.apache.maven.scm.command.remove.RemoveScmResult;
import org.apache.maven.scm.command.status.StatusScmResult;
import org.apache.maven.scm.command.tag.TagScmResult;
import org.apache.maven.scm.command.unedit.UnEditScmResult;
import org.apache.maven.scm.command.update.UpdateScmResult;
import org.apache.maven.scm.command.update.UpdateScmResultWithRevision;
import org.apache.maven.scm.log.ScmLogDispatcher;
import org.apache.maven.scm.log.ScmLogger;
import org.apache.maven.scm.manager.BasicScmManager;
import org.apache.maven.scm.manager.NoSuchScmProviderException;
import org.apache.maven.scm.provider.AbstractScmProvider;
import org.apache.maven.scm.provider.ScmProviderRepository;
import org.apache.maven.scm.provider.ScmProviderRepositoryWithHost;
import org.apache.maven.scm.provider.ScmUrlUtils;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.scm.util.AbstractConsumer;
import org.apache.maven.scm.util.FilenameUtils;
import org.apache.maven.scm.util.ThreadSafeDateFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class Maven_scm_apiTest {
    @TempDir
    File temporaryDirectory;

    @Test
    void scmUrlUtilitiesParseAndValidateProviderUrls() {
        String colonDelimitedUrl = "scm:git:https://example.test/repository.git";
        String pipeDelimitedUrl = "scm:svn|https://example.test/svn/project";

        assertThat(ScmUrlUtils.isValid(colonDelimitedUrl)).isTrue();
        assertThat(ScmUrlUtils.getDelimiter(colonDelimitedUrl)).isEqualTo(":");
        assertThat(ScmUrlUtils.getProvider(colonDelimitedUrl)).isEqualTo("git");
        assertThat(ScmUrlUtils.getProviderSpecificPart(colonDelimitedUrl))
                .isEqualTo("https://example.test/repository.git");

        assertThat(ScmUrlUtils.isValid(pipeDelimitedUrl)).isTrue();
        assertThat(ScmUrlUtils.getDelimiter(pipeDelimitedUrl)).isEqualTo("|");
        assertThat(ScmUrlUtils.getProvider(pipeDelimitedUrl)).isEqualTo("svn");
        assertThat(ScmUrlUtils.getProviderSpecificPart(pipeDelimitedUrl))
                .isEqualTo("https://example.test/svn/project");

        assertThat(ScmUrlUtils.validate(null)).containsExactly("The scm url cannot be null.");
        assertThat(ScmUrlUtils.validate("git:https://example.test/repository.git"))
                .containsExactly("The scm url must start with 'scm:'.");
        assertThat(ScmUrlUtils.validate("scm:git"))
                .containsExactly("The scm url does not contain a valid delimiter.");
        assertThat(ScmUrlUtils.isValid("scm:git")).isFalse();
    }

    @Test
    void commandParametersAndRequestsExposeTypedCommandState() throws Exception {
        CommandParameters parameters = new CommandParameters();
        Date startDate = new Date(1_700_000_000_000L);
        ScmRevision revision = new ScmRevision("abc123");
        ScmTagParameters tagParameters = new ScmTagParameters("Create release tag");
        ScmBranchParameters branchParameters = new ScmBranchParameters("Create release branch");

        tagParameters.setRemoteTagging(true);
        tagParameters.setScmRevision("tag-source-revision");
        branchParameters.setRemoteBranching(true);
        branchParameters.setScmRevision("branch-source-revision");

        parameters.setString(CommandParameter.MESSAGE, "Commit message");
        parameters.setInt(CommandParameter.LIMIT, 25);
        parameters.setDate(CommandParameter.START_DATE, startDate);
        parameters.setString(CommandParameter.RECURSIVE, "true");
        parameters.setScmVersion(CommandParameter.SCM_VERSION, revision);
        parameters.setScmTagParameters(CommandParameter.SCM_TAG_PARAMETERS, tagParameters);
        parameters.setScmBranchParameters(CommandParameter.SCM_BRANCH_PARAMETERS, branchParameters);

        assertThat(parameters.getString(CommandParameter.MESSAGE)).isEqualTo("Commit message");
        assertThat(parameters.getString(CommandParameter.BRANCH_NAME, "main")).isEqualTo("main");
        assertThat(parameters.getInt(CommandParameter.LIMIT)).isEqualTo(25);
        assertThat(parameters.getInt(CommandParameter.NUM_DAYS, 7)).isEqualTo(7);
        assertThat(parameters.getDate(CommandParameter.START_DATE)).isSameAs(startDate);
        assertThat(parameters.getBoolean(CommandParameter.RECURSIVE)).isTrue();
        assertThat(parameters.getBoolean(CommandParameter.BINARY, false)).isFalse();
        assertThat(parameters.getScmVersion(CommandParameter.SCM_VERSION)).isSameAs(revision);
        assertThat(parameters.getScmTagParameters(CommandParameter.SCM_TAG_PARAMETERS)).isSameAs(tagParameters);
        assertThat(parameters.getScmBranchParameters(CommandParameter.SCM_BRANCH_PARAMETERS))
                .isSameAs(branchParameters);
        assertThat(tagParameters.toString()).contains("Create release tag", "tag-source-revision");
        assertThat(branchParameters.toString()).contains("Create release branch", "branch-source-revision");

        parameters.remove(CommandParameter.MESSAGE);
        assertThat(parameters.getString(CommandParameter.MESSAGE, "fallback")).isEqualTo("fallback");
        parameters.setString(CommandParameter.MESSAGE, "Updated commit message");
        assertThat(parameters.getString(CommandParameter.MESSAGE)).isEqualTo("Updated commit message");

        ScmRepository repository = new ScmRepository("record", new RecordingRepository("memory://repository"));
        ScmFileSet fileSet = new ScmFileSet(temporaryDirectory, new File("pom.xml"));
        ScmRequest request = new ScmRequest(repository, fileSet);
        assertThat(request.getScmRepository()).isSameAs(repository);
        assertThat(request.getScmFileSet()).isSameAs(fileSet);

        ChangeLogScmRequest changeLogRequest = new ChangeLogScmRequest(repository, fileSet);
        Date endDate = new Date(startDate.getTime() + 60_000L);
        changeLogRequest.setDateRange(startDate, endDate);
        changeLogRequest.setNumDays(14);
        changeLogRequest.setScmBranch(new ScmBranch("release"));
        changeLogRequest.setStartRevision(new ScmRevision("1"));
        changeLogRequest.setEndRevision(new ScmRevision("2"));
        changeLogRequest.setDatePattern("yyyy-MM-dd");
        changeLogRequest.setLimit(10);

        assertThat(changeLogRequest.getStartDate()).isSameAs(startDate);
        assertThat(changeLogRequest.getEndDate()).isSameAs(endDate);
        assertThat(changeLogRequest.getCommandParameters().getInt(CommandParameter.NUM_DAYS)).isEqualTo(14);
        assertThat(changeLogRequest.getScmBranch().getName()).isEqualTo("release");
        assertThat(changeLogRequest.getStartRevision().getName()).isEqualTo("1");
        assertThat(changeLogRequest.getEndRevision().getName()).isEqualTo("2");
        assertThat(changeLogRequest.getDatePattern()).isEqualTo("yyyy-MM-dd");
        assertThat(changeLogRequest.getLimit()).isEqualTo(10);

        BlameScmRequest blameRequest = new BlameScmRequest(repository, fileSet);
        blameRequest.setFilename("src/main/java/App.java");
        blameRequest.setIgnoreWhitespace(true);
        assertThat(blameRequest.getFilename()).isEqualTo("src/main/java/App.java");
        assertThat(blameRequest.isIgnoreWhitespace()).isTrue();
    }

    @Test
    void fileSetsVersionsChangesAndUtilitiesModelScmState() throws Exception {
        File sourceFile = new File("src/main/java/App.java");
        File testFile = new File("src/test/java/AppTest.java");
        ScmFileSet explicitFileSet = new ScmFileSet(temporaryDirectory, List.of(sourceFile, testFile));

        assertThat(explicitFileSet.getBasedir()).isSameAs(temporaryDirectory);
        assertThat(explicitFileSet.getFiles()).containsExactly(sourceFile, testFile);
        assertThat(explicitFileSet.getFileList()).containsExactly(sourceFile, testFile);
        assertThat(explicitFileSet.toString()).contains("basedir =", "files =");

        File nested = new File(temporaryDirectory, "src/main/java/App.java");
        assertThat(nested.getParentFile().mkdirs()).isTrue();
        assertThat(nested.createNewFile()).isTrue();
        ScmFileSet scannedFileSet = new ScmFileSet(temporaryDirectory, "src/**/*.java");
        assertThat(scannedFileSet.getIncludes()).isEqualTo("src/**/*.java");
        assertThat(scannedFileSet.getFileList()).extracting(File::getName).contains("App.java");

        ScmFile added = new ScmFile("src/main/java/App.java", ScmFileStatus.ADDED);
        ScmFile modified = new ScmFile("src/test/java/AppTest.java", ScmFileStatus.MODIFIED);
        assertThat(added.getPath()).isEqualTo("src/main/java/App.java");
        assertThat(added.getStatus()).isSameAs(ScmFileStatus.ADDED);
        assertThat(added).isEqualTo(new ScmFile("src/main/java/App.java", ScmFileStatus.ADDED));
        assertThat(added.compareTo(modified)).isGreaterThan(0);
        assertThat(added.toString()).contains("src/main/java/App.java", "added");

        assertThat(ScmFileStatus.ADDED.isDiff()).isTrue();
        assertThat(ScmFileStatus.UNKNOWN.isStatus()).isTrue();
        assertThat(ScmFileStatus.UPDATED.isUpdate()).isTrue();
        assertThat(ScmFileStatus.TAGGED.isTransaction()).isTrue();
        assertThat(ScmFileStatus.RENAMED.isDiff()).isFalse();

        ScmVersion branch = new ScmBranch("main");
        ScmVersion tag = new ScmTag("v1.0.0");
        ScmVersion revision = new ScmRevision("abc123");
        assertThat(branch.getType()).isEqualTo("Branch");
        assertThat(tag.getType()).isEqualTo("Tag");
        assertThat(revision.getType()).isEqualTo("Revision");
        assertThat(revision.toString()).contains("abc123");

        ChangeFile changeFile = new ChangeFile("src/main/java/App.java", "42");
        changeFile.setAction(ScmFileStatus.MODIFIED);
        changeFile.setOriginalName("src/main/java/OldApp.java");
        changeFile.setOriginalRevision("41");
        assertThat(changeFile.getName()).isEqualTo("src/main/java/App.java");
        assertThat(changeFile.getRevision()).isEqualTo("42");
        assertThat(changeFile.getAction()).isSameAs(ScmFileStatus.MODIFIED);
        assertThat(changeFile.toString()).contains("src/main/java/App.java", "42");

        ChangeSet changeSet = new ChangeSet();
        changeSet.setAuthor("Jane Developer");
        changeSet.setComment("Fix <encoding> & keep CDATA terminator ]]> harmless");
        changeSet.setDate(new Date(0L));
        changeSet.setRevision("42");
        changeSet.setParentRevision("41");
        changeSet.addMergedRevision("40");
        changeSet.addFile(changeFile);

        assertThat(changeSet.containsFilename("src/main/java/App.java")).isTrue();
        assertThat(changeSet.containsFilename("missing.txt")).isFalse();
        assertThat(changeSet.getDateFormatted()).isNotBlank();
        assertThat(changeSet.getTimeFormatted()).isNotBlank();
        assertThat(changeSet.getMergedRevisions()).containsExactly("40");
        assertThat(ChangeSet.escapeValue("<tag attr=\"value\">Tom & Jerry's</tag>"))
                .isEqualTo("&lt;tag attr=&quot;value&quot;&gt;Tom &amp; Jerry&apos;s&lt;/tag&gt;");

        String xml = changeSet.toXML();
        assertThat(xml)
                .contains("<author><![CDATA[Jane Developer]]></author>")
                .contains("<revision>42</revision>")
                .contains("<parent>41</parent>")
                .contains("<merge>40</merge>")
                .contains("<name>src/main/java/App.java</name>")
                .contains("Fix <encoding> & keep CDATA terminator ] ] > harmless")
                .doesNotContain("]]> harmless");

        assertThat(FilenameUtils.normalizeFilename("src\\main//java\\App.java"))
                .isEqualTo("src/main/java/App.java");
        ThreadSafeDateFormat dateFormat = new ThreadSafeDateFormat("yyyy-MM-dd");
        assertThat(dateFormat.format(new Date(0L))).matches("19[67][09]-.*|1970-01-01");
        assertThat(dateFormat.parse("2024-01-02", new ParsePosition(0))).isNotNull();
    }

    @Test
    void scmResultsExposeCommandMetadataAndTypedPayloads() {
        List<ScmFile> files = List.of(
                new ScmFile("pom.xml", ScmFileStatus.MODIFIED),
                new ScmFile("src/main/java/App.java", ScmFileStatus.ADDED));
        List<ChangeSet> changes = List.of(new ChangeSet(new Date(0L), "alice", "Initial import", List.of()));
        ScmResult success = new ScmResult("scm command", "provider message", "command output", true);
        ScmResult failure = new ScmResult("bad command", "provider failure", "error output", false);

        assertThat(success.isSuccess()).isTrue();
        assertThat(success.getCommandLine()).isEqualTo("scm command");
        assertThat(success.getProviderMessage()).isEqualTo("provider message");
        assertThat(success.getCommandOutput()).isEqualTo("command output");
        assertThat(new ScmResult(success).isSuccess()).isTrue();
        assertThat(failure.isSuccess()).isFalse();

        assertThat(new AddScmResult(files, success).getAddedFiles()).isSameAs(files);
        assertThat(new BranchScmResult(files, success).getBranchedFiles()).isSameAs(files);
        assertThat(new CheckInScmResult("checkin", files, "rev-2").getScmRevision()).isEqualTo("rev-2");
        assertThat(new CheckInScmResult(files, success).getCheckedInFiles()).isSameAs(files);
        CheckOutScmResult checkOut = new CheckOutScmResult("checkout", "rev-3", files, "project");
        assertThat(checkOut.getCheckedOutFiles()).isSameAs(files);
        assertThat(checkOut.getRelativePathProjectDirectory()).isEqualTo("project");
        assertThat(checkOut.getRevision()).isEqualTo("rev-3");
        assertThat(new EditScmResult(files, success).getEditFiles()).isSameAs(files);
        assertThat(new ExportScmResult("export", files).getExportedFiles()).isSameAs(files);
        assertThat(new ExportScmResultWithRevision("export", files, "rev-4").getRevision()).isEqualTo("rev-4");
        assertThat(new ListScmResult(files, success).getFiles()).isSameAs(files);
        assertThat(new LoginScmResult("login", "ok", "", true).isSuccess()).isTrue();
        assertThat(new MkdirScmResult("mkdir", files).getCreatedDirs()).isSameAs(files);
        assertThat(new MkdirScmResult("mkdir", "rev-5").getRevision()).isEqualTo("rev-5");
        assertThat(new RemoveScmResult(files, success).getRemovedFiles()).isSameAs(files);
        assertThat(new StatusScmResult(files, success).getChangedFiles()).isSameAs(files);
        assertThat(new TagScmResult(files, success).getTaggedFiles()).isSameAs(files);
        assertThat(new UnEditScmResult(files, success).getUnEditFiles()).isSameAs(files);

        UpdateScmResult update = new UpdateScmResult(files, changes, success);
        assertThat(update.getUpdatedFiles()).isSameAs(files);
        assertThat(update.getChanges()).isSameAs(changes);
        update.setChanges(List.of());
        assertThat(update.getChanges()).isEmpty();
        assertThat(new UpdateScmResultWithRevision(files, changes, "rev-6", success).getRevision()).isEqualTo("rev-6");

        Map<String, CharSequence> differences = new LinkedHashMap<>();
        differences.put("pom.xml", "diff --git a/pom.xml b/pom.xml");
        DiffScmResult diff = new DiffScmResult(files, differences, "patch text", success);
        assertThat(diff.getChangedFiles()).isSameAs(files);
        assertThat(diff.getDifferences()).isSameAs(differences);
        assertThat(diff.getPatch()).isEqualTo("patch text");

        BlameLine blameLine = new BlameLine(new Date(0L), "rev-1", "author", "committer");
        blameLine.setRevision("rev-2");
        blameLine.setAuthor("other author");
        blameLine.setCommitter("other committer");
        blameLine.setDate(new Date(1L));
        assertThat(blameLine.getRevision()).isEqualTo("rev-2");
        assertThat(blameLine.getAuthor()).isEqualTo("other author");
        assertThat(blameLine.getCommitter()).isEqualTo("other committer");
        assertThat(blameLine.getDate()).isEqualTo(new Date(1L));
        assertThat(new BlameScmResult(List.of(blameLine), success).getLines()).containsExactly(blameLine);

        InfoItem infoItem = new InfoItem();
        infoItem.setPath("pom.xml");
        infoItem.setURL("https://example.test/repository/pom.xml");
        infoItem.setRepositoryRoot("https://example.test/repository");
        infoItem.setRepositoryUUID("uuid");
        infoItem.setRevision("7");
        infoItem.setNodeKind("file");
        infoItem.setSchedule("normal");
        infoItem.setLastChangedAuthor("alice");
        infoItem.setLastChangedRevision("6");
        infoItem.setLastChangedDate("2024-01-02");
        assertThat(infoItem.getPath()).isEqualTo("pom.xml");
        assertThat(infoItem.getURL()).isEqualTo("https://example.test/repository/pom.xml");
        assertThat(infoItem.getRepositoryRoot()).isEqualTo("https://example.test/repository");
        assertThat(infoItem.getRepositoryUUID()).isEqualTo("uuid");
        assertThat(infoItem.getRevision()).isEqualTo("7");
        assertThat(infoItem.getNodeKind()).isEqualTo("file");
        assertThat(infoItem.getSchedule()).isEqualTo("normal");
        assertThat(infoItem.getLastChangedAuthor()).isEqualTo("alice");
        assertThat(infoItem.getLastChangedRevision()).isEqualTo("6");
        assertThat(infoItem.getLastChangedDate()).isEqualTo("2024-01-02");
        assertThat(new InfoScmResult(List.of(infoItem), success).getInfoItems()).containsExactly(infoItem);

        Map<String, String> branches = new LinkedHashMap<>();
        branches.put("main", "rev-main");
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("v1.0.0", "rev-tag");
        RemoteInfoScmResult remoteInfo = new RemoteInfoScmResult("remote-info", branches, tags);
        assertThat(remoteInfo.getBranches()).isSameAs(branches);
        assertThat(remoteInfo.getTags()).isSameAs(tags);
        remoteInfo.setBranches(Map.of("release", "rev-release"));
        remoteInfo.setTags(Map.of("v1.1.0", "rev-v1.1.0"));
        assertThat(remoteInfo.getBranches()).containsEntry("release", "rev-release");
        assertThat(remoteInfo.getTags()).containsEntry("v1.1.0", "rev-v1.1.0");
        assertThat(remoteInfo.toString()).contains("release", "v1.1.0");

        ChangeLogSet changeLogSet = new ChangeLogSet(changes, new Date(0L), new Date(1L));
        changeLogSet.setStartVersion(new ScmRevision("1"));
        changeLogSet.setEndVersion(new ScmRevision("2"));
        assertThat(changeLogSet.getChangeSets()).isSameAs(changes);
        assertThat(changeLogSet.getStartVersion().getName()).isEqualTo("1");
        assertThat(changeLogSet.getEndVersion().getName()).isEqualTo("2");
        assertThat(changeLogSet.toXML()).contains("<changeset", "<author><![CDATA[Initial import]]></author>");
        assertThat(new ChangeLogScmResult(changeLogSet, success).getChangeLog()).isSameAs(changeLogSet);
    }

    @Test
    void managerDelegatesToRegisteredProviderAndProviderStoresCommandParameters() throws Exception {
        RecordingProvider provider = new RecordingProvider();
        BasicScmManager manager = new BasicScmManager();
        manager.setScmProvider("record", provider);
        manager.setScmProviderImplementation("alias", "record");

        assertThat(manager.getProviderByType("record")).isSameAs(provider);
        assertThat(manager.getProviderByType("alias")).isSameAs(provider);
        assertThat(manager.getProviderByUrl("scm:record:https://example.test/repository")).isSameAs(provider);
        assertThatExceptionOfType(NoSuchScmProviderException.class)
                .isThrownBy(() -> manager.getProviderByType("missing"));

        ScmRepository repository = manager.makeScmRepository("scm:record:https://example.test/repository/../module");
        assertThat(repository.getProvider()).isEqualTo("record");
        assertThat(repository.getProviderRepository()).isInstanceOf(RecordingRepository.class);
        assertThat(provider.lastProviderSpecificUrl).doesNotContain("../").endsWith("/module");
        assertThat(provider.lastDelimiter).isEqualTo(':');
        assertThat(manager.validateScmRepository("scm:record|ssh://example.test/repository")).isEmpty();
        assertThat(manager.validateScmRepository("scm:missing:https://example.test/repository"))
                .containsExactly("No such provider installed 'missing'.");

        ScmRepository repositoryFromDirectory = manager.makeProviderScmRepository("record", temporaryDirectory);
        assertThat(repositoryFromDirectory.getProvider()).isEqualTo("record");
        assertThat(provider.lastDirectory).isSameAs(temporaryDirectory);

        ScmFileSet fileSet = new ScmFileSet(temporaryDirectory, new File("pom.xml"));
        manager.add(repository, fileSet, "Add project descriptor");
        assertThat(provider.lastOperation).isEqualTo("add");
        assertThat(provider.lastParameters.getString(CommandParameter.MESSAGE)).isEqualTo("Add project descriptor");

        manager.branch(repository, fileSet, "release-1", "Prepare release branch");
        assertThat(provider.lastOperation).isEqualTo("branch");
        assertThat(provider.lastParameters.getString(CommandParameter.BRANCH_NAME)).isEqualTo("release-1");
        assertThat(provider.lastParameters.getScmBranchParameters(CommandParameter.SCM_BRANCH_PARAMETERS).getMessage())
                .isEqualTo("Prepare release branch");

        manager.tag(repository, fileSet, "v1.0.0", "Tag release");
        assertThat(provider.lastOperation).isEqualTo("tag");
        assertThat(provider.lastParameters.getString(CommandParameter.TAG_NAME)).isEqualTo("v1.0.0");
        assertThat(provider.lastParameters.getScmTagParameters(CommandParameter.SCM_TAG_PARAMETERS).getMessage())
                .isEqualTo("Tag release");

        manager.checkIn(repository, fileSet, new ScmRevision("base"), "Commit release metadata");
        assertThat(provider.lastOperation).isEqualTo("checkin");
        assertThat(provider.lastParameters.getString(CommandParameter.MESSAGE)).isEqualTo("Commit release metadata");
        assertThat(provider.lastParameters.getScmVersion(CommandParameter.SCM_VERSION).getName()).isEqualTo("base");

        ChangeLogScmRequest changeLogRequest = new ChangeLogScmRequest(repository, fileSet);
        changeLogRequest.setStartRevision(new ScmRevision("1"));
        changeLogRequest.setEndRevision(new ScmRevision("2"));
        manager.changeLog(changeLogRequest);
        assertThat(provider.lastOperation).isEqualTo("changelog");
        assertThat(provider.lastParameters.getScmVersion(CommandParameter.START_SCM_VERSION).getName()).isEqualTo("1");
        assertThat(provider.lastParameters.getScmVersion(CommandParameter.END_SCM_VERSION).getName()).isEqualTo("2");

        manager.update(repository, fileSet, new ScmRevision("3"), "yyyy-MM-dd");
        assertThat(provider.lastOperation).isEqualTo("update");
        assertThat(provider.lastParameters.getScmVersion(CommandParameter.SCM_VERSION).getName()).isEqualTo("3");
        assertThat(provider.lastParameters.getString(CommandParameter.CHANGELOG_DATE_PATTERN)).isEqualTo("yyyy-MM-dd");

        BlameScmRequest blameRequest = new BlameScmRequest(repository, fileSet);
        blameRequest.setFilename("pom.xml");
        manager.blame(blameRequest);
        assertThat(provider.lastOperation).isEqualTo("blame");
        assertThat(provider.lastParameters.getString(CommandParameter.FILE)).isEqualTo("pom.xml");
    }

    @Test
    void consumersParseProviderDatesWithConfiguredPatternsAndWarnOnInvalidInput() {
        TestScmLogger logger = new TestScmLogger(false, false, true, false);
        DateParsingConsumer consumer = new DateParsingConsumer(logger);
        consumer.consumeLine("provider output");
        assertThat(consumer.lastConsumedLine).isEqualTo("provider output");

        Date parsedWithUserPattern = consumer.parseWithPatterns("2024/01/02", "yyyy/MM/dd", "yyyy-MM-dd");
        assertThat(formatDate(parsedWithUserPattern, "yyyy-MM-dd")).isEqualTo("2024-01-02");

        Date parsedWithLocale = consumer.parseWithLocale("02 Jan 2024", null, "dd MMM yyyy", Locale.ENGLISH);
        assertThat(formatDate(parsedWithLocale, "yyyy-MM-dd")).isEqualTo("2024-01-02");

        assertThat(consumer.parseWithLocale("not-a-date", null, "yyyy-MM-dd", Locale.ENGLISH)).isNull();
        assertThat(logger.events).hasSize(1);
        assertThat(logger.events.get(0))
                .startsWith("warn:skip ParseException:")
                .contains("not-a-date", "yyyy-MM-dd", Locale.ENGLISH.toString());
    }

    @Test
    void logDispatcherAggregatesListenersAndRoutesInfoMessagesToEnabledLoggers() {
        ScmLogDispatcher dispatcher = new ScmLogDispatcher();
        TestScmLogger mutedLogger = new TestScmLogger(false, false, false, false);
        TestScmLogger infoLogger = new TestScmLogger(false, true, false, false);
        TestScmLogger warnLogger = new TestScmLogger(false, false, true, false);

        assertThat(dispatcher.isDebugEnabled()).isFalse();
        assertThat(dispatcher.isInfoEnabled()).isFalse();
        assertThat(dispatcher.isWarnEnabled()).isFalse();
        assertThat(dispatcher.isErrorEnabled()).isFalse();

        dispatcher.addListener(mutedLogger);
        dispatcher.addListener(infoLogger);
        dispatcher.addListener(warnLogger);

        assertThat(dispatcher.isDebugEnabled()).isFalse();
        assertThat(dispatcher.isInfoEnabled()).isTrue();
        assertThat(dispatcher.isWarnEnabled()).isTrue();
        assertThat(dispatcher.isErrorEnabled()).isFalse();

        IllegalStateException failure = new IllegalStateException("repository unavailable");
        dispatcher.info("checking repository");
        dispatcher.info("checking working copy", failure);
        dispatcher.info(failure);

        assertThat(infoLogger.events).containsExactly(
                "info:checking repository",
                "info:checking working copy:repository unavailable",
                "info:repository unavailable");
        assertThat(mutedLogger.events).isEmpty();
        assertThat(warnLogger.events).isEmpty();
    }

    @Test
    void providerRepositoriesRetainConnectionConfiguration() {
        RecordingRepository repository = new RecordingRepository("https://example.test/repository");
        RecordingRepository child = new RecordingRepository("https://example.test/repository/module");
        repository.setUser("user");
        repository.setPassword("secret");
        repository.setPushChanges(false);
        repository.setWorkItem("TASK-123");
        repository.setPersistCheckout(true);
        repository.setHost("example.test");
        repository.setPort(8443);
        repository.setPrivateKey("private-key");
        repository.setPassphrase("passphrase");
        child.parent = repository;

        assertThat(repository.getUser()).isEqualTo("user");
        assertThat(repository.getPassword()).isEqualTo("secret");
        assertThat(repository.isPushChanges()).isFalse();
        assertThat(repository.getWorkItem()).isEqualTo("TASK-123");
        assertThat(repository.isPersistCheckout()).isTrue();
        assertThat(repository.getHost()).isEqualTo("example.test");
        assertThat(repository.getPort()).isEqualTo(8443);
        assertThat(repository.getPrivateKey()).isEqualTo("private-key");
        assertThat(repository.getPassphrase()).isEqualTo("passphrase");
        assertThat(child.getParent()).isSameAs(repository);
        assertThat(new ScmRepository("record", repository).toString()).contains("record");
    }

    private static String formatDate(Date date, String pattern) {
        return new SimpleDateFormat(pattern, Locale.ENGLISH).format(date);
    }

    private static final class DateParsingConsumer extends AbstractConsumer {
        private String lastConsumedLine;

        private DateParsingConsumer(ScmLogger logger) {
            super(logger);
        }

        @Override
        public void consumeLine(String line) {
            lastConsumedLine = line;
        }

        private Date parseWithPatterns(String date, String userPattern, String defaultPattern) {
            return parseDate(date, userPattern, defaultPattern);
        }

        private Date parseWithLocale(String date, String userPattern, String defaultPattern, Locale locale) {
            return parseDate(date, userPattern, defaultPattern, locale);
        }
    }

    private static final class TestScmLogger implements ScmLogger {
        private final boolean debugEnabled;
        private final boolean infoEnabled;
        private final boolean warnEnabled;
        private final boolean errorEnabled;
        private final List<String> events = new ArrayList<>();

        private TestScmLogger(
                boolean debugEnabled,
                boolean infoEnabled,
                boolean warnEnabled,
                boolean errorEnabled) {
            this.debugEnabled = debugEnabled;
            this.infoEnabled = infoEnabled;
            this.warnEnabled = warnEnabled;
            this.errorEnabled = errorEnabled;
        }

        @Override
        public boolean isDebugEnabled() {
            return debugEnabled;
        }

        @Override
        public void debug(String content) {
            record("debug", content);
        }

        @Override
        public void debug(String content, Throwable error) {
            record("debug", content, error);
        }

        @Override
        public void debug(Throwable error) {
            record("debug", error);
        }

        @Override
        public boolean isInfoEnabled() {
            return infoEnabled;
        }

        @Override
        public void info(String content) {
            record("info", content);
        }

        @Override
        public void info(String content, Throwable error) {
            record("info", content, error);
        }

        @Override
        public void info(Throwable error) {
            record("info", error);
        }

        @Override
        public boolean isWarnEnabled() {
            return warnEnabled;
        }

        @Override
        public void warn(String content) {
            record("warn", content);
        }

        @Override
        public void warn(String content, Throwable error) {
            record("warn", content, error);
        }

        @Override
        public void warn(Throwable error) {
            record("warn", error);
        }

        @Override
        public boolean isErrorEnabled() {
            return errorEnabled;
        }

        @Override
        public void error(String content) {
            record("error", content);
        }

        @Override
        public void error(String content, Throwable error) {
            record("error", content, error);
        }

        @Override
        public void error(Throwable error) {
            record("error", error);
        }

        private void record(String level, String content) {
            events.add(level + ":" + content);
        }

        private void record(String level, String content, Throwable error) {
            events.add(level + ":" + content + ":" + error.getMessage());
        }

        private void record(String level, Throwable error) {
            events.add(level + ":" + error.getMessage());
        }
    }

    private static final class RecordingRepository extends ScmProviderRepositoryWithHost {
        private final String location;
        private ScmProviderRepository parent;

        private RecordingRepository(String location) {
            this.location = location;
        }

        @Override
        public ScmProviderRepository getParent() {
            return parent;
        }

        @Override
        public String toString() {
            return location;
        }
    }

    private static final class RecordingProvider extends AbstractScmProvider {
        private String lastOperation;
        private String lastProviderSpecificUrl;
        private char lastDelimiter;
        private File lastDirectory;
        private CommandParameters lastParameters;

        @Override
        public String getScmType() {
            return "record";
        }

        @Override
        public ScmProviderRepository makeProviderScmRepository(String scmSpecificUrl, char delimiter) {
            lastProviderSpecificUrl = scmSpecificUrl;
            lastDelimiter = delimiter;
            return new RecordingRepository(scmSpecificUrl);
        }

        @Override
        public ScmProviderRepository makeProviderScmRepository(File path) {
            lastDirectory = path;
            return new RecordingRepository(path.getAbsolutePath());
        }

        @Override
        public List<String> validateScmUrl(String scmSpecificUrl, char delimiter) {
            lastProviderSpecificUrl = scmSpecificUrl;
            lastDelimiter = delimiter;
            List<String> messages = new ArrayList<>();
            if (scmSpecificUrl == null || scmSpecificUrl.isEmpty()) {
                messages.add("Provider-specific URL is required.");
            }
            return messages;
        }

        @Override
        public AddScmResult add(ScmProviderRepository repository, ScmFileSet fileSet, CommandParameters parameters) {
            record("add", parameters);
            return new AddScmResult("add", filesFrom(fileSet));
        }

        @Override
        protected BranchScmResult branch(
                ScmProviderRepository repository,
                ScmFileSet fileSet,
                CommandParameters parameters) {
            record("branch", parameters);
            return new BranchScmResult("branch", filesFrom(fileSet));
        }

        @Override
        protected ChangeLogScmResult changelog(
                ScmProviderRepository repository,
                ScmFileSet fileSet,
                CommandParameters parameters) {
            record("changelog", parameters);
            return new ChangeLogScmResult("changelog", new ChangeLogSet(List.of(), new Date(0L), new Date(0L)));
        }

        @Override
        protected CheckInScmResult checkin(
                ScmProviderRepository repository,
                ScmFileSet fileSet,
                CommandParameters parameters) {
            record("checkin", parameters);
            return new CheckInScmResult("checkin", filesFrom(fileSet));
        }

        @Override
        protected UpdateScmResult update(
                ScmProviderRepository repository,
                ScmFileSet fileSet,
                CommandParameters parameters) {
            record("update", parameters);
            return new UpdateScmResult("update", filesFrom(fileSet));
        }

        @Override
        protected TagScmResult tag(ScmProviderRepository repository, ScmFileSet fileSet, CommandParameters parameters) {
            record("tag", parameters);
            return new TagScmResult("tag", filesFrom(fileSet));
        }

        @Override
        protected BlameScmResult blame(
                ScmProviderRepository repository,
                ScmFileSet fileSet,
                CommandParameters parameters) {
            record("blame", parameters);
            return new BlameScmResult("blame", List.of());
        }

        @Override
        public InfoScmResult info(ScmProviderRepository repository, ScmFileSet fileSet, CommandParameters parameters) {
            record("info", parameters);
            return new InfoScmResult("info", List.of());
        }

        @Override
        public RemoteInfoScmResult remoteInfo(
                ScmProviderRepository repository,
                ScmFileSet fileSet,
                CommandParameters parameters) {
            record("remoteInfo", parameters);
            return new RemoteInfoScmResult("remoteInfo", Map.of(), Map.of());
        }

        @Override
        public void addListener(ScmLogger logger) {
            super.addListener(logger);
        }

        private void record(String operation, CommandParameters parameters) {
            lastOperation = operation;
            lastParameters = parameters;
        }

        private static List<ScmFile> filesFrom(ScmFileSet fileSet) {
            return fileSet.getFileList().stream()
                    .map(file -> new ScmFile(FilenameUtils.normalizeFilename(file.getPath()), ScmFileStatus.MODIFIED))
                    .toList();
        }
    }
}
