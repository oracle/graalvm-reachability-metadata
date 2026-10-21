/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_typesafe_slick.slick_2_13

import org.junit.jupiter.api.Assertions.{assertEquals, assertTrue}
import org.junit.jupiter.api.{Test, Timeout}
import slick.jdbc.H2Profile.api._
import slick.lifted.ProvenShape
import slick.model.ForeignKeyAction

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/** Exercises Slick's schema constraint and referential-action subsystem for workspace retention.
  * Document paths are unique within a workspace, and removing a workspace cascades to its documents.
  */
class WorkspaceRetentionTest {
  class Workspaces(tag: Tag) extends Table[(Int, String)](tag, "RETENTION_WORKSPACES") {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey)
    def name: Rep[String] = column[String]("NAME")
    def * : ProvenShape[(Int, String)] = (id, name)
  }

  class Documents(tag: Tag, workspaces: TableQuery[Workspaces])
      extends Table[(Int, Int, String, String)](tag, "RETENTION_DOCUMENTS") {
    def id: Rep[Int] = column[Int]("ID", O.PrimaryKey)
    def workspaceId: Rep[Int] = column[Int]("WORKSPACE_ID")
    def path: Rep[String] = column[String]("PATH")
    def body: Rep[String] = column[String]("BODY")
    def workspace = foreignKey("RETENTION_WORKSPACE_FK", workspaceId, workspaces)(
      _.id, onDelete = ForeignKeyAction.Cascade
    )
    def uniquePath = index("RETENTION_DOCUMENT_PATH_UQ", (workspaceId, path), unique = true)
    def * : ProvenShape[(Int, Int, String, String)] = (id, workspaceId, path, body)
  }

  @Test
  @Timeout(60)
  def enforcesScopedPathsAndPurgesOnlyTheDeletedWorkspace(): Unit = {
    val workspaces: TableQuery[Workspaces] = TableQuery(tag => new Workspaces(tag))
    val documents: TableQuery[Documents] = TableQuery(tag => new Documents(tag, workspaces))
    val schema = workspaces.schema ++ documents.schema
    val database: Database = Database.forURL(
      "jdbc:h2:mem:workspace_retention;DB_CLOSE_DELAY=-1",
      driver = "org.h2.Driver"
    )
    val originalDocuments: Seq[(Int, Int, String, String)] = Seq(
      (101, 1, "readme.txt", "Retired workspace guide"),
      (102, 1, "plans.txt", "Retired workspace plans"),
      (201, 2, "readme.txt", "Active workspace guide")
    )
    val scenario = for {
      _ <- schema.create
      _ <- workspaces ++= Seq((1, "Retired team"), (2, "Active team"))
      _ <- documents ++= originalDocuments
      duplicate <- (documents += ((103, 1, "readme.txt", "Accidental overwrite"))).asTry
      afterDuplicate <- documents.sortBy(_.id).result
      orphan <- (documents += ((104, 99, "orphan.txt", "Unknown workspace"))).asTry
      afterOrphan <- documents.sortBy(_.id).result
      removed <- workspaces.filter(_.id === 1).delete
      remainingWorkspaces <- workspaces.sortBy(_.id).result
      remainingDocuments <- documents.sortBy(_.id).result
      retiredDocuments <- documents.filter(_.workspaceId === 1).length.result
    } yield (duplicate, afterDuplicate, orphan, afterOrphan, removed,
      remainingWorkspaces, remainingDocuments, retiredDocuments)

    try {
      val (duplicate, afterDuplicate, orphan, afterOrphan, removed,
        remainingWorkspaces, remainingDocuments, retiredDocuments) = Await.result(
        database.run(scenario.andFinally(schema.drop)),
        30.seconds
      )
      assertTrue(duplicate.isFailure, "A workspace cannot have two documents at the same path")
      assertEquals(originalDocuments, afterDuplicate)
      assertTrue(orphan.isFailure, "Documents must belong to an existing workspace")
      assertEquals(originalDocuments, afterOrphan)
      assertEquals(1, removed)
      assertEquals(Seq((2, "Active team")), remainingWorkspaces)
      assertEquals(Seq((201, 2, "readme.txt", "Active workspace guide")), remainingDocuments)
      assertEquals(0, retiredDocuments)
    } finally {
      database.close()
    }
  }
}
