import sbt._
import Keys._

object MyBuild extends Build {
  val bannoSnapshots = "Banno Snapshots Repo" at "http://nexus.banno.com/nexus/content/repositories/snapshots"
  val bannoReleases = "Banno Releases Repo" at "http://nexus.banno.com/nexus/content/repositories/releases"

  lazy val root = Project(id = "root",
                          base = file("."),
                          settings = Project.defaultSettings ++ myBuildSettings)

  val myBuildSettings = Seq(sbtPlugin := true,
                            name := "banno-sbt-plugin",
                            version := "1.0-SNAPSHOT",
                            organization := "com.banno",
                            shellPrompt <<= (name) { (name) => _ => name + " > " },

                            publishArtifact in (Compile, packageSrc) := false,
                            publishArtifact in (Compile, packageDoc) := false,

                            resolvers := Seq(
                              "Banno External Repo" at "http://nexus.banno.com/nexus/content/groups/external/",
                              bannoSnapshots,
                              bannoReleases
                            ),
                            externalResolvers <<= resolvers map { rs =>
                              Resolver.withDefaultResolvers(rs, mavenCentral = true)
                            },

                            libraryDependencies += "net.databinder" %% "dispatch-http" % "0.8.8",

                            scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:postfixOps"),

                            publishTo <<= (version) { v =>
                              if (v.trim.endsWith("SNAPSHOT")) Some(bannoSnapshots)
                              else Some(bannoReleases)
                            },
                            credentials += Credentials(Path.userHome / ".ivy2" / ".banno_credentials"),
                            publishMavenStyle := true
                          ) ++
                        Seq(
                          addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.10.1"),
                          addSbtPlugin("com.github.gseitz" % "sbt-release" % "0.8"),
                          addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.1"))
}
