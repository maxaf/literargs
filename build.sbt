import macroRevolver._

lazy val baseSettings = Seq(
  organization := "com.bumnetworks",
  version := "0.0.1-SNAPSHOT",
  scalaVersion := "2.11.8",
  initialCommands := """
    import literargs._
    import scala.reflect.runtime.universe._
    import cats._, cats.implicits._, cats.data._
  """,
  scalacOptions ++= Seq(
    "-deprecation",
    "-unchecked",
    "-feature",
    "-language:higherKinds"
  )) ++ scalariformSettings ++ tutSettings

lazy val deps = Seq(
 libraryDependencies ++= Seq(
   "org.typelevel" %% "cats" % "0.7.2",
   "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4",
   "org.specs2" %% "specs2-core" % "3.8.5" % "test"))

lazy val core = project
  .in(file("."))
  .settings(baseSettings)
  .settings(MacroRevolverPlugin.useMacroParadise)
  .settings(deps)
  .settings(name := "literargs", moduleName := "literargs")

lazy val tests = project
  .in(file("tests"))
  .settings(baseSettings)
  .settings(deps)
  .settings(name := "tests", moduleName := "literargs-tests")
  .settings(MacroRevolverPlugin.useMacroParadise)
  .settings(MacroRevolverPlugin.testCleanse)
  .dependsOn(core)

lazy val literargs = project
  .aggregate(core, tests)
  .settings(baseSettings)
