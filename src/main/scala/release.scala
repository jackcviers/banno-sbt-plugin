import sbt._
import java.util.Properties

sealed case class BannoDep(groupId: String, artifactId: String, snapshotVersion: String) {
  def propKey = groupId + "." + artifactId
}
  
trait VariableBannoDepVersions extends BasicDependencyProject with SnapshotOrRelease {
  var bannoDependencies: Set[BannoDep] = Set()
  
  override def libraryDependencies = super.libraryDependencies ++ bannoDependenciesAsModuleIds

  def bannoDependency(groupId: String, artifactId: String): Unit = {
    bannoDependency(groupId, artifactId, "1.0-SNAPSHOT")
  }

  def bannoDependency(groupId: String, artifactId: String, snapshotVersion: String) : Unit = {
    bannoDependencies += BannoDep(groupId, artifactId, snapshotVersion)
  }

  def bannoDependenciesToUpdate: Set[BannoDep] = bannoDependencies

  def updateBannoReleaseVersionsAction = task {
    val newVersions = bannoVersions
    bannoDependenciesToUpdate foreach { dep =>
      val latestVersion = Nexus.latestReleasedVersionFor(dep.groupId, appendScalaVersion(dep.artifactId)).getOrElse(throw new RuntimeException("Unable to find release for " + dep))
      newVersions.setProperty(dep.propKey, latestVersion)                         
    }
    log.info("Setting Banno Versions to:")
    newVersions.list(new java.io.PrintWriter(new LoggerWriter(log, Level.Info)))
    FileUtilities.writeStream(bannoVersionsPath.asFile, log) { stream =>
      newVersions.store(stream, null)
      None
    }
    Git.commit(bannoVersionsPath.toString,
               "Updating banno dependencies to released versions",
               log)
  }

  def bannoDependenciesNeedUpdated: Boolean =
    bannoDependenciesToUpdate exists { dep =>
      val latestVersion = Nexus.latestReleasedVersionFor(dep.groupId, appendScalaVersion(dep.artifactId))
                                      .getOrElse(throw new RuntimeException("Unable to find release for " + dep))
      bannoVersions.get(dep.propKey) != latestVersion
    }

  lazy val bannoVersionsPath = rootProject.info.builderPath / "banno-versions.properties"
  
  protected def bannoVersions: Properties = {
    val bannoVersions = new Properties()
    FileUtilities.readStream(bannoVersionsPath.asFile, log) { stream =>
      bannoVersions.load(stream)
      None                                                   
    }
    bannoVersions
  }
  
  private def versionForBannoDep(dep: BannoDep): String= {
    if (isSnapshot) {
      dep.snapshotVersion
    } else {
      bannoVersions.getProperty(dep.propKey)                        
    }
  }

  
  private def bannoDependenciesAsModuleIds(): Set[ModuleID] = {
    bannoDependencies map { dep =>
      val BannoDep(groupId, artifactId, snapshotVersion) = dep
      ModuleID(groupId, appendScalaVersion(artifactId), versionForBannoDep(dep))
    }
  }
  
  private def appendScalaVersion(artifactId: String): String = {
    artifactId + "_" + buildScalaVersion
  }
  
}

trait ReleaseVersioning extends BasicDependencyProject {
  
  def versionSnapshotToReleaseAction = task {
    modifyVersion("Updating version to release") { currentVersion =>
      val nextVersionMaybe = lastVersion.filter(v => v.major == currentVersion.major && v.minor == currentVersion.minor)
                                        .map(v => v.incrementMicro)      
      nextVersionMaybe.getOrElse(currentVersion.incrementMicro.withExtra(None))                              
    }
  }

  def versionReleaseToSnapshotAction = task {
    modifyVersion("Updating to snapshot version") { v => BasicVersion(v.major, v.minor, None, Some("SNAPSHOT"))}
  }

  private def modifyVersion(msgHeader: String)(f: (BasicVersion) => Version): Option[String] = {
    projectVersion.get match {
      case Some(version: BasicVersion) =>
        val newVersion = f(version)
        log.info("Changing version to " + newVersion)
        rootProject.projectVersion() = newVersion
        rootProject.saveEnvironment()
        Git.commit("project/build.properties", "%s: %s".format(msgHeader, newVersion), log)
      case weird => 
        Some("Can't modify " + weird)
    }
  }

  
  def lastVersion: Option[BasicVersion] = sortedVersionTags.lastOption orElse lastVersionInNexus
  
  val knownScalaVersions = List("2.9.1", "2.9.0-1", "2.8.1", "2.7.7")
  def lastVersionInNexus: Option[BasicVersion] = {
    val latestVersions = knownScalaVersions.flatMap { scalaVersion =>
      val lastVersionStr = Nexus.latestReleasedVersionFor(organization, normalizedName + "_" + scalaVersion)
      lastVersionStr.map { v =>
        Version.fromString(v) match {
          case Right(version: BasicVersion) => version
          case value => throw new RuntimeException("Unable to parse version: " + value)
        }
      }
    }
    latestVersions.sort { (a,b) => a.micro.get >= b.micro.get }.firstOption
  }
  
  def tagVersionAction = task {
    Git.tag(versionTagName, "Tagging release version: " + version, log)
  }

  def sortedVersionTags = {
    val versionTags = Git.listTags(log).map(Version.fromString(_)).flatMap {
      case Right(b: BasicVersion) => Some(b)
      case _ => None
    }
    versionTags sort { (b1, b2) => b1.major < b2.major || b1.minor.get < b2.minor.get || b1.micro.get < b2.micro.get }
  }
  
  def lastVersionTag = {
    sortedVersionTags.last.toString
  }

  val CHANGED_FILES_TO_IGNORE = Set("project/build.properties", "project/banno-versions.properties")
  def hasChangedSinceLastRelease(): Boolean = lastVersion.map(v =>
    Git.isDifference("refs/tags/" + v.toString, CHANGED_FILES_TO_IGNORE, log)).getOrElse(true)

  def versionTagName: String = version.toString
}

trait GitMergeAndPush extends Project {
  self: ReleaseVersioning =>
    
  def gitMergeAndPushAction = task {
    if (Git.hasRemote("origin", log)) {
      val head = Git.currentHeadSHA(log)
      
      Git.checkout("master", log) orElse
      Git.pull(log) orElse
      Git.merge(head, log)
      Git.push("master", log)
      Git.push(self.lastVersionTag, log)
      
    } else {
      None
    }
  }
}

trait RunSequential extends Project {
  def runSequential(taskNames: Seq[String]): Option[String] =
    taskNames.foldLeft(None: Option[String]) { (result, taskName) => result orElse act(taskName) }
}

trait BannoReleaseProcess extends BasicScalaProject with VariableBannoDepVersions with ReleaseVersioning with GitMergeAndPush with RunSequential {
  lazy val updateBannoReleaseVersions = super.updateBannoReleaseVersionsAction
  lazy val versionSnapshotToRelease = super.versionSnapshotToReleaseAction
  lazy val tagVersion = super.tagVersionAction
  lazy val versionReleaseToSnapshot = super.versionReleaseToSnapshotAction
  lazy val gitMergeAndPush = super.gitMergeAndPushAction

  lazy val preReleaseActions = List(updateBannoReleaseVersions,
                                    versionSnapshotToRelease)
  
  lazy val releaseActions = List(clean,
                                 cleanLib,
                                 update,
                                 test,
                                 publish)
  
  lazy val postReleaseActions = List(tagVersion,
                                     versionReleaseToSnapshot,
                                     gitMergeAndPush)

  def taskIfChanged(actions: List[Task]) = task {
    if (hasChangedSinceLastRelease || bannoDependenciesNeedUpdated) {
      runSequential(actions.map(_.name))
    } else {
      log.info("Nothing has changed since last release. Not doing anything.")
      None
    }
  } describedAs "The Banno Release Process"
  
  lazy val preRelease = taskIfChanged(preReleaseActions)
  lazy val runRelease = taskIfChanged(releaseActions)
  lazy val postRelease = taskIfChanged(postReleaseActions)
  override def releaseAction = taskIfChanged(preReleaseActions ++ releaseActions ++ postReleaseActions)
}

trait BannoMultiReleaseProcess extends BasicDependencyProject with ReleaseVersioning with VariableBannoDepVersions with GitMergeAndPush with RunSequential {
  lazy val updateBannoReleaseVersionsMulti = super.updateBannoReleaseVersionsAction
  lazy val versionSnapshotToReleaseMulti = super.versionSnapshotToReleaseAction
  lazy val tagVersionMulti = super.tagVersionAction
  lazy val versionReleaseToSnapshotMulti = super.versionReleaseToSnapshotAction
  lazy val gitMergeAndPushMulti = super.gitMergeAndPushAction
  
  lazy val parentPreTasks = List(updateBannoReleaseVersionsMulti,
                                 versionSnapshotToReleaseMulti)
  lazy val releaseModuleTasks = List("clean", "clean-lib", "update", "test", "publish")
  lazy val parentPostTasks = List(tagVersionMulti,
                                  versionReleaseToSnapshotMulti,
                                  gitMergeAndPushMulti)
  
  lazy val releaseMulti = task {
    if (hasChangedSinceLastRelease || bannoDependenciesNeedUpdated) {
      runSequential(parentPreTasks.map(_.name)) orElse
      runSequential(releaseModuleTasks) orElse
      runSequential(parentPostTasks.map(_.name))
    } else {
      log.info("Nothin has changed since last release. Not doing anything.")
      None
    }
  } describedAs "The Banno Release Process"
  
  override def bannoDependenciesToUpdate: Set[BannoDep] = Set(subProjects.values.toList.flatMap {
    case proj: VariableBannoDepVersions => proj.bannoDependencies
  }: _*)
}
