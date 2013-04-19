import sbt._
import sbt.Keys._
import sbt.Tests.Setup
import sbtassembly.Plugin._
import AssemblyKeys._

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
    settings = Defaults.defaultSettings ++ junit ++ assemblySettings ++ Seq(
      organization := "sdfs",
      version := "1.0-SNAPSHOT",
      scalaVersion := "2.10.0",
      javacOptions ++= Seq(
        "-source", "1.7",
        "-target", "1.7"
      ),
      libraryDependencies ++= Seq(
        "com.google.guava" % "guava" % "14.0-rc3",
        "net.sourceforge.argparse4j" % "argparse4j" % "0.3.2",
        "org.scala-lang" % "jline" % "2.10.0" exclude("org.fusesource.jansi", "jansi"),
        "org.apache.commons" % "commons-math3" % "3.1.1",
        "com.google.code.findbugs" % "jsr305" % "2.0.1",
        "ch.qos.logback" % "logback-classic" % "1.0.9"
      ),
      libraryDependencies ++= Seq(
        "junit" % "junit" % "4.11",
        "com.dadrox" %% "sbt-junit" % "0.1",
        "org.mockito" % "mockito-all" % "1.9.5"
      ) map (_ % "test"),
      compileOrder := CompileOrder.ScalaThenJava,
      jarName in assembly := "sdfs.jar",
      assembleArtifact in packageScala := false
    )
  )

}
