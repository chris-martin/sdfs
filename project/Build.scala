import sbt._
import sbt.Keys._

object Build extends sbt.Build {

  def project(id: String, base: String = null)(settings: Settings): Project = {
    Project(id = id, base = file(Option(base).getOrElse(id))).settings(settings: _*)
  }

  def ls(f: File): Seq[File] = {
    val these = f.listFiles
    these ++ these.filter(_.isDirectory).flatMap(ls)
  }

  lazy val root = project("root", ".") {
    val turninTask = TaskKey[File]("turnin")
    baseSettings ++ Seq(
      turninTask <<= (baseDirectory, target, streams) map {
        (base: File, target: File, streams: TaskStreams) => {
          val zip: File = target / "sdfs-francis-martin.zip"
          val inputs: Seq[(File, String)] =
            Seq("README.md", "AUTHORS.md").map { path => (base / path, path) } ++
            Seq((base / "report/target/sdfs.pdf", "report.pdf")) ++
            (ls(base / "pki") ++ (base / "project").listFiles ++ ls(base / "sdfs/src")).map { file =>
              (file, "source/" + file.relativeTo(base).get.toPath)
            }
          println(inputs.mkString("\n"))
          IO.delete(zip)
          IO.zip(inputs, zip)
          zip: File
        }
      }
    )
  }.aggregate(sdfs, report)

  lazy val sdfs = project("sdfs") {
    baseSettings ++ Seq(
      javacOptions ++= Seq(
        "-source", "1.7",
        "-target", "1.7"
      ),
      libraryDependencies ++= Seq(
        "com.google.guava" % "guava" % "14.0.1",
        "commons-io" % "commons-io" % "2.4",
        "net.sourceforge.argparse4j" % "argparse4j" % "0.3.2",
        "org.scala-lang" % "jline" % "2.10.0" exclude("org.fusesource.jansi", "jansi"),
        "com.google.code.findbugs" % "jsr305" % "2.0.1",
        "ch.qos.logback" % "logback-classic" % "1.0.9",
        "com.typesafe" % "config" % "1.0.0",
        "io.netty" % "netty" % "3.6.5.Final",
        "joda-time" % "joda-time" % "2.2",
        "javax.inject" % "javax.inject" % "1"
      ),
      libraryDependencies ++= Seq(
        "org.mockito" % "mockito-all" % "1.9.5"
      ) map (_ % "test")
    ) ++ {
      import sbt.Tests.Setup
      val framework = new TestFramework("com.dadrox.sbt.junit.JunitFramework")
      Seq(
        testFrameworks += framework,
        testOptions in Test ++= Seq(
          Tests.Argument(framework, "-vo", "-tv"),
          Setup( cl =>
            cl.loadClass("sdfs.TestLogConfiguration").getMethod("configureLogging").invoke(null)
          )
        ),
        libraryDependencies ++= Seq(
          "junit" % "junit" % "4.11",
          "com.dadrox" %% "sbt-junit" % "0.1"
        ) map (_ % "test")
      )
    } ++ {
      import de.johoop.findbugs4sbt.FindBugs, FindBugs._
      import de.johoop.findbugs4sbt.ReportType._
      FindBugs.findbugsSettings ++ Seq(
        findbugsReportType := Html,
        findbugsReportName := "findbugs.html"
      )
    } ++ {
      import sbtassembly.Plugin, Plugin._
      import AssemblyKeys._
      Plugin.assemblySettings ++ Seq(
        jarName in assembly := "sdfs.jar",
        assembleArtifact in packageScala := false
      )
    }
  }

  lazy val report = project("report") {
    val latexTask = TaskKey[File]("latex")
    baseSettings ++ Seq(
      latexTask <<= (baseDirectory, target, streams) map {
        (baseDirectory: File, target: File, streams: TaskStreams) => {
          Process(
            Seq("latexmk", "-g", "-pdf", (baseDirectory / "src/main/latex/sdfs").getAbsolutePath),
            Some(target)
          ) ! streams.log match {
            case 0 => ()
            case n => sys.error("Trouble running latexmk.  Exit code: " + n)
          }
          target / "sdfs.pdf"
        }
      },
      watchSources <<= baseDirectory map {
        (baseDirectory: File) => {
          Seq(baseDirectory / "src/main/latex")
        }
      },
      compile <<= (compile in Compile) dependsOn latexTask
    )
  }

  lazy val baseSettings: Settings = Seq(
    organization := "sdfs",
    version := "1.0-SNAPSHOT"
  )

  type Settings = Seq[Setting[_]]

}
