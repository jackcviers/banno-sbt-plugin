package com.banno
import sbt._
import Keys._
import aether.Aether

object BannoNexus {
  val bannoSnapshots = "Banno Snapshots Repo" at "http://nexus.banno.com/nexus/content/repositories/snapshots"
  val bannoReleases = "Banno Releases Repo" at "http://nexus.banno.com/nexus/content/repositories/releases"

  val clearLocalBannoArtifacts = TaskKey[Unit]("clear-local-banno-artifacts", "Clear locally published and downloaded artifacts from ivy cache")

  val settings: Seq[Setting[_]] = Seq(
    resolvers := Seq(
      "Banno External Repo" at "http://nexus.banno.com/nexus/content/groups/external/",
      bannoSnapshots,
      bannoReleases
    ),
    externalResolvers <<= resolvers map { rs =>
      Resolver.withDefaultResolvers(rs, mavenCentral = false)
    },
    publishTo <<= (version) { v =>
      if (v.trim.endsWith("SNAPSHOT")) {
        Some(bannoSnapshots)
      } else {
        Some(bannoReleases)
      }
    },
    credentials += Credentials(Path.userHome / ".ivy2" / ".banno_credentials"),
    clearLocalBannoArtifacts := {
      import IO._
      import Path._
      delete(userHome / ".ivy2" / "cache" / "com.banno")
      delete(userHome / ".ivy2" / "local" / "com.banno")
      delete(userHome / ".ivy2" / "cache" / "scala_2.9.1" / "sbt_0.12" / "com.banno")
      delete(userHome / ".ivy2" / "cache" / "scala_2.9.2" / "sbt_0.12" / "com.banno")
      delete(userHome / ".ivy2" / "cache" / "scala_2.10.0" / "sbt_0.12" / "com.banno")
      delete(userHome / ".ivy2" / "cache" / "scala_2.10.1" / "sbt_0.12" / "com.banno")
      delete(userHome / ".ivy2" / "cache" / "scala_2.10" / "sbt_0.13" / "com.banno")
    }
  ) ++ Aether.aetherPublishSettings
}
