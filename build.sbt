enablePlugins(TutPlugin)

import java.nio.file._
import java.nio.file.attribute._
import macroRevolver._

lazy val baseSettings = Seq(
  organization := "com.bumnetworks",
  version := "0.0.3",
  crossScalaVersions := Seq("2.12.2", "2.11.11"),
  scalaVersion := crossScalaVersions.value.head,
  initialCommands := """
    import literargs._
    import scala.reflect.runtime.universe._
    import cats._, cats.implicits._, cats.data._
  """,
  scalacOptions ++= Seq(
    //"-Yquasiquote-debug",
    "-deprecation",
    "-unchecked",
    "-feature",
    "-language:higherKinds"
  )) ++ scalariformSettings

lazy val deps = Seq(
 libraryDependencies ++= Seq(
   "org.typelevel" %% "cats" % "0.9.0",
   "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.6",
   "org.specs2" %% "specs2-core" % "3.9.1" % "test",
   "org.specs2" %% "specs2-matcher-extra" % "3.9.1" % "test"))

lazy val updateReadme = taskKey[Unit]("copy tut-generated README.md to project root")

lazy val publishSettings = Seq(
  publishTo <<= (version) {
    v =>
    val repo = file(".") / ".." / "repo"
    Some(Resolver.file("repo",
      if (v.trim.endsWith("SNAPSHOT")) repo / "snapshots"
      else repo / "releases"))
  }
)

lazy val core = project
  .in(file("."))
  .settings(baseSettings)
  .settings(MacroRevolverPlugin.useMacroParadise)
  .settings(deps)
  .settings(name := "literargs", moduleName := "literargs")
  .settings(updateReadme := {
    val README = "README.md"
    tut.value.foreach {
      case (generated, README) =>
        Files.copy(
          Paths.get(generated.toURI),
          Paths.get(baseDirectory.value.toURI).resolve(README),
          StandardCopyOption.REPLACE_EXISTING
        )
      case _ =>
    }
  })
  .settings(publishSettings)

lazy val tests = project
  .in(file("tests"))
  .settings(baseSettings)
  .settings(deps)
  .settings(name := "tests", moduleName := "literargs-tests")
  .settings(publish := {})
  .settings(MacroRevolverPlugin.useMacroParadise)
  .settings(MacroRevolverPlugin.testCleanse)
  .dependsOn(core)

lazy val literargs = project
  .aggregate(core, tests)
  .settings(baseSettings)
