package com.banno
import sbtrelease.Version
import scala.concurrent.duration._
import scala.concurrent._, ExecutionContext.Implicits.global

object Nexus {
  import org.apache.ivy.util.url.CredentialsStore
  import dispatch._
  import Http._

  val versionLinkRegex = "a href=\".+?\">([0-9.]+?)/</a>".r

  def latestReleasedVersionFor(groupId: String, artifactId: String): Option[String] = {
    try {
      val versionStrings: Future[List[String]] =
        Http(releaseDirectoryPath(groupId, artifactId) OK as.String).map(versionLinkRegex.findAllMatchIn(_).map(_.group(1)).toList)
      val result = Await.result(versionStrings, 1 minutes)
      val versions = result.flatMap(Version.apply(_: String))
      VersionUtil.newestVersion(versions).map(_.string)
    } catch {
      case StatusCode(404) => None
    }
  }

  def releaseDirectoryPath(org: String, name: String) = {
    val metadataPath = org.replaceAll("\\.", "/") + "/" + name + "/"
    nexusBase / "content/repositories/releases/" / metadataPath
  }

  lazy val nexusBase = :/("nexus.banno.com") / "nexus"

  def nexusAuthenticated(req: Req) = {
    val creds = CredentialsStore.INSTANCE.getCredentials("Sonatype Nexus Repository Manager", "nexus.banno.com")
    req as (creds.getUserName, creds.getPasswd)
  }
}
