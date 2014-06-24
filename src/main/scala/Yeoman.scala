package com.banno

import sbt._
import sbt.File
import Keys._

object Yeoman {

  val yeomanWatchSources = TaskKey[Seq[File]]("yeoman-watch-sources")
  val yeomanBowerDirectory = SettingKey[File]("yeoman-bower-directory")
  val yeomanNpmDirectory = SettingKey[File]("npm-directory")
  val yeomanDirectory = SettingKey[File]("yeoman-directory")
  val yeomanGruntfile = SettingKey[String]("yeoman-gruntfile")
  val yeomanGulpfile = SettingKey[String]("yeoman-gulpfile")
  val yeomanBowerJson = SettingKey[String]("yeoman-bower-json")
  val yeomanPackageJson = SettingKey[String]("yeoman-package-json")
  val grunt = TaskKey[Unit]("Task to run grunt")
  val npm = TaskKey[Unit]("Task to run npm")
  val bower = TaskKey[Unit]("Task to run bower")
  val gulp = TaskKey[Unit]("Task to run gulp")

  val settings: Def.SettingsDefinition = Seq(
    yeomanDirectory <<= (baseDirectory in Compile) {
      _ / "src" / "main" / "client"
    },
    yeomanBowerDirectory <<= (baseDirectory in Compile) {
      _ / "bower_components"
    },
    yeomanNpmDirectory <<= (baseDirectory in Compile) {
      _ / "node_modules"
    },
    yeomanGruntfile := "Gruntfile.js",
    yeomanGulpfile := "gulpfile.js",
    yeomanBowerJson := "bower.json",
    yeomanPackageJson := "package.json",
    yeomanWatchSources <<= (baseDirectory in Compile, yeomanDirectory, yeomanGruntfile, yeomanGulpfile, yeomanBowerJson, yeomanPackageJson, yeomanBowerDirectory, yeomanNpmDirectory) map { (base, yeoman, gruntFile, gulpFile, bowerJson, packageJson, bowerDirectory, npmDirectory) =>
      val maybeWatchSourceBase = maybeWatchSourceFile(base) _
      val maybeGruntFileToWatch = maybeWatchSourceBase(gruntFile)
      val maybeGulpFileToWatch = maybeWatchSourceBase(gulpFile)
      val maybeBowerJsonToWatch = maybeWatchSourceBase(bowerJson)
      val maybePackageJsonToWatch = maybeWatchSourceBase(packageJson)
      val maybeBowerDependenciesToWatch = maybeWatchSourceDirectory(bowerDirectory)
      val maybeNpmDependenciesToWatch = maybeWatchSourceDirectory(npmDirectory)

      (yeoman ***).get ++ maybeGruntFileToWatch.getOrElse(Seq.empty) ++ maybeGulpFileToWatch.getOrElse(Seq.empty) ++ maybeBowerJsonToWatch.getOrElse(Seq.empty) ++ maybePackageJsonToWatch.getOrElse(Seq.empty) ++ maybeBowerDependenciesToWatch.getOrElse(Seq.empty) ++ maybeNpmDependenciesToWatch.getOrElse(Seq.empty)
    },
    watchSources <<= (watchSources, yeomanWatchSources) map { (ws, yws) => ws ++ yws },
    npm := {
      val base = (baseDirectory in Compile).value
      val packageJson = yeomanPackageJson.value
      if ((base / packageJson).exists) runNpm(base)
    },
    bower := {
      val base = (baseDirectory in Compile).value
      val bowerJson = yeomanBowerJson.value
      if ((base / bowerJson).exists) runBower(base)
    },
    grunt := {
      val base = (baseDirectory in Compile).value
      val gruntFile = yeomanGruntfile.value
      if ((base / gruntFile).exists) runGrunt(base, gruntFile)
    },
    gulp := {
      val base = (baseDirectory in Compile).value
      val gulpFile = yeomanGulpfile.value
      if ((base / gulpFile).exists) runGulp(base)
    },
    bower <<= bower.dependsOn(npm),
    grunt <<= grunt.dependsOn(npm),
    grunt <<= grunt.dependsOn(bower),
    gulp <<= gulp.dependsOn(npm),
    gulp <<= gulp.dependsOn(bower),
    update <<= update.dependsOn(npm),
    update <<= update.dependsOn(bower),
    (compile in Compile) <<= (compile in Compile).dependsOn(grunt),
    (compile in Compile) <<= (compile in Compile).dependsOn(gulp),
    commands <++= yeomanDirectory {
      base =>
        Seq(
          "grunt",
          "bower",
          "yo",
          "npm").map(cmd(_, base))
    })

  private def maybeWatchSourceFile(baseDirectory: File)(filenameToWatch: String): Option[Seq[File]] = if ((baseDirectory / filenameToWatch).exists) Some(Seq(baseDirectory / filenameToWatch)) else None

  private def maybeWatchSourceDirectory(directory: File): Option[Seq[File]] = if (directory.exists) Some(Seq(directory)) else None

  private def runYeomanTool(base: File, tool: String, maybeWithFile: Option[String] = None, additionalArgs: List[String] = Nil) = {
    if (System.getProperty("os.name").startsWith("Windows")) {
      val process: ProcessBuilder = Process("cmd" :: "/c" :: tool :: maybeWithFile.map { f => "--gruntfile=" + f }.getOrElse { "" } :: additionalArgs, base)
      println(s"Will run: ${process.toString} in ${base.getPath}")
      Some(process.run)
    } else {
      val process: ProcessBuilder = Process(tool :: maybeWithFile.map { f => "--gruntfile=" + f }.getOrElse { "" } :: additionalArgs, base)
      println(s"Will run: ${process.toString} in ${base.getPath}")
      Some(process.run)
    }
  }

  private def runBower(base: File, args: List[String] = Nil) = runYeomanTool(base, "bower", None, "--save" :: args)

  private def runNpm(base: File, args: List[String] = Nil) = runYeomanTool(base, "npm", None, "--save" :: args)

  private def runGrunt(base: File, gruntFile: String, args: List[String] = Nil) = runYeomanTool(base, "grunt", Some(s"--gruntFile=$gruntFile"), args)

  private def runGulp(base: File, args: List[String] = Nil) = runYeomanTool(base, "gulp", None, args)

  private def runYo(base: File, args: List[String] = Nil) = runYeomanTool(base, "yo", None, args)

  import scala.language.postfixOps
  private def cmd(name: String, base: File): Command = {
    if (!base.exists()) (base.mkdirs())
    Command.args(name, "<" + name + "-command>") {
      (state, args) =>
        if (System.getProperty("os.name").startsWith("Windows")) {
          Process("cmd" :: "/c" :: name :: args.toList, base) !<
        } else {
          Process(name :: args.toList, base) !<
        }
        state
    }
  }
}

