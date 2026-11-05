/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_maven_model;

import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Developer;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Notifier;
import org.apache.maven.model.Organization;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Maven model's project governance metadata subsystem to assemble support and build-status contacts for a developer portal.
public class ProjectGovernanceDirectoryTest {
    @Test
    @Timeout(60)
    public void assemblesProjectSupportAndBuildStatusDirectory() throws Exception {
        Model project = readModel("""
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>com.example.platform</groupId>
                  <artifactId>orders-api</artifactId>
                  <version>4.0.0</version>
                  <organization>
                    <name>Example Platform</name>
                    <url>https://platform.example.com</url>
                  </organization>
                  <issueManagement>
                    <system>GitHub</system>
                    <url>https://github.com/example/orders-api/issues</url>
                  </issueManagement>
                  <ciManagement>
                    <system>Buildkite</system>
                    <url>https://builds.example.com/orders-api</url>
                    <notifiers>
                      <notifier>
                        <type>mail</type>
                        <address>orders-ci@example.com</address>
                        <sendOnError>true</sendOnError>
                        <sendOnFailure>true</sendOnFailure>
                        <sendOnSuccess>false</sendOnSuccess>
                        <sendOnWarning>true</sendOnWarning>
                      </notifier>
                    </notifiers>
                  </ciManagement>
                  <mailingLists>
                    <mailingList>
                      <name>Users</name>
                      <subscribe>mailto:orders-users-subscribe@example.com</subscribe>
                      <unsubscribe>mailto:orders-users-unsubscribe@example.com</unsubscribe>
                      <post>mailto:orders-users@example.com</post>
                      <archive>https://lists.example.com/orders-users</archive>
                      <otherArchives>
                        <otherArchive>https://archive.example.com/orders-users</otherArchive>
                      </otherArchives>
                    </mailingList>
                  </mailingLists>
                  <developers>
                    <developer>
                      <id>jlee</id>
                      <name>Jordan Lee</name>
                      <email>jordan.lee@example.com</email>
                      <organization>Example Platform</organization>
                      <timezone>-8</timezone>
                      <roles>
                        <role>maintainer</role>
                        <role>release-manager</role>
                      </roles>
                    </developer>
                  </developers>
                  <contributors>
                    <contributor>
                      <name>Casey Morgan</name>
                      <email>casey.morgan@example.com</email>
                      <roles>
                        <role>documentation</role>
                      </roles>
                    </contributor>
                  </contributors>
                </project>
                """);

        Organization organization = project.getOrganization();
        assertNotNull(organization);
        assertEquals("Example Platform", organization.getName());
        assertEquals("https://platform.example.com", organization.getUrl());

        IssueManagement issueManagement = project.getIssueManagement();
        assertNotNull(issueManagement);
        assertEquals("GitHub", issueManagement.getSystem());
        assertEquals("https://github.com/example/orders-api/issues", issueManagement.getUrl());

        CiManagement ciManagement = project.getCiManagement();
        assertNotNull(ciManagement);
        assertEquals("Buildkite", ciManagement.getSystem());
        assertEquals("https://builds.example.com/orders-api", ciManagement.getUrl());
        Notifier notifier = ciManagement.getNotifiers().get(0);
        assertEquals("mail", notifier.getType());
        assertEquals("orders-ci@example.com", notifier.getAddress());
        assertTrue(notifier.isSendOnError());
        assertTrue(notifier.isSendOnFailure());
        assertTrue(notifier.isSendOnWarning());

        MailingList mailingList = project.getMailingLists().get(0);
        assertEquals("Users", mailingList.getName());
        assertEquals("mailto:orders-users@example.com", mailingList.getPost());
        assertEquals("https://lists.example.com/orders-users", mailingList.getArchive());
        assertEquals("https://archive.example.com/orders-users", mailingList.getOtherArchives().get(0));

        Developer developer = (Developer) project.getDevelopers().get(0);
        assertEquals("jlee", developer.getId());
        assertEquals("Jordan Lee", developer.getName());
        assertEquals("-8", developer.getTimezone());
        assertEquals(2, developer.getRoles().size());
        assertEquals("release-manager", developer.getRoles().get(1));

        Contributor contributor = project.getContributors().get(0);
        assertEquals("Casey Morgan", contributor.getName());
        assertEquals("documentation", contributor.getRoles().get(0));
    }

    private static Model readModel(String pom) throws IOException, XmlPullParserException {
        return new MavenXpp3Reader().read(new StringReader(pom));
    }
}
