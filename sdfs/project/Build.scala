import sbt._
import sbt.Keys._
import sbt.Tests.Setup
import sbtassembly.Plugin._
import AssemblyKeys._
import de.johoop.findbugs4sbt.FindBugs._
import de.johoop.findbugs4sbt.ReportType

object Build extends sbt.Build {

  val junit = {
    val framework = new TestFramework("com.dadrox.sbt.junit.JunitFramework")
    Seq(
      testFrameworks += framework,
      testOptions in Test ++= Seq(
        Tests.Argument(framework, "-vo", "-tv"),
        Setup( cl =>
          cl.loadClass("sdfs.TestLogConfiguration").getMethod("configureLogging").invoke(null)
        )
      )
    )
  }

  lazy val project = Project(
    id = "sdfs",
    base = file("."),
    settings = Defaults.defaultSettings ++ junit ++ assemblySettings ++ findbugsSettings ++ Seq(
      organization := "sdfs",
      version := "1.0-SNAPSHOT",
      scalaVersion := "2.10.0",
      javacOptions ++= Seq(
        "-source", "1.7",
        "-target", "1.7"
      ),
      libraryDependencies ++= Seq(
        "com.google.guava" % "guava" % "14.0.1",
        "net.sourceforge.argparse4j" % "argparse4j" % "0.3.2",
        "org.scala-lang" % "jline" % "2.10.0" exclude("org.fusesource.jansi", "jansi"),
        "com.google.code.findbugs" % "jsr305" % "2.0.1",
        "ch.qos.logback" % "logback-classic" % "1.0.9",
        "com.typesafe" % "config" % "1.0.0",
        "io.netty" % "netty-all" % "4.0.0.CR1"
      ),
      libraryDependencies ++= Seq(
        "junit" % "junit" % "4.11",
        "com.dadrox" %% "sbt-junit" % "0.1",
        "org.mockito" % "mockito-all" % "1.9.5"
      ) map (_ % "test"),
      compileOrder := CompileOrder.ScalaThenJava,
      jarName in assembly := "sdfs.jar",
      assembleArtifact in packageScala := false,
      findbugsReportType := ReportType.Html,
      findbugsReportName := "findbugs.html"
    )
  )

}
