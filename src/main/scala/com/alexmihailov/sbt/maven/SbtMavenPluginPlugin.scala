package com.alexmihailov.sbt.maven

import com.alexmihailov.sbt.maven.tools.{AnnotationsMojoDescriptorExtractor, PluginDescriptorGenerator}
import org.apache.maven.artifact.DefaultArtifact
import org.apache.maven.plugin.descriptor.PluginDescriptor
import org.apache.maven.tools.plugin.DefaultPluginToolsRequest
import org.apache.maven.tools.plugin.generator.GeneratorUtils.toComponentDependencies
import sbt.*
import sbt.Keys.*
import sbt.librarymanagement.Configurations.Compile
import sbt.plugins.JvmPlugin

import java.io.File
import java.nio.file.Files

object SbtMavenPluginPlugin extends AutoPlugin {

  override def trigger = noTrigger

  override def requires = JvmPlugin

  object autoImport {
    val mavenGeneratePluginXml = taskKey[Seq[File]]("Generate the maven plugin xml")
    val mavenPluginGoalPrefix = settingKey[String]("Maven plugin goalPrefix")
    val mavenPluginEncoding = settingKey[String]("Maven plugin encoding")
    val mavenPluginSkipErrorNoDescriptorsFound = settingKey[Boolean]("Skip error when descriptors not found")
    val mavenTestArgs = settingKey[Seq[String]]("Maven test arguments")
    val mavenTest = inputKey[Unit]("Run the maven tests")
    val mavenClasspath = taskKey[Seq[File]]("The maven classpath")
  }

  import autoImport.*
  override lazy val projectSettings: Seq[Setting[?]] = inConfig(Compile)(mavenGeneratePluginXmlSettings) ++
    mavenTestSettings

  private def mavenGeneratePluginXmlSettings: Seq[Setting[?]] = Seq(
    mavenPluginEncoding := "UTF-8",
    mavenPluginSkipErrorNoDescriptorsFound := false,
    mavenGeneratePluginXml := {
      val extractor = new AnnotationsMojoDescriptorExtractor((Compile / classDirectory).value)
      val dependenciesArtifacts = update.value.configurations
        .filter { c => isRuntimeDep(Option(c.configuration.name)) }
        .flatMap { c => c.modules }
        .flatMap { m => m.artifacts }
      val cv = CrossVersion(scalaVersion.value, scalaBinaryVersion.value)
      val artifacts = allDependencies.value
        .filter { p => isRuntimeDep(p.configurations) }
        .map { d =>
          val versioned = cv(d)
          val artifact = dependenciesArtifacts.find { a => a._1.name.equals(d.name) }
          new DefaultArtifact(
            versioned.organization,
            versioned.name,
            versioned.revision,
            null,
            artifact.map { i => i._1.`type` }.getOrElse("jar"),
            artifact.flatMap { i => i._1.classifier }.getOrElse(""), // if null - NPE
            null
          )
        }
      val components = toComponentDependencies(scala.collection.JavaConverters.seqAsJavaList(artifacts))

      val pid = if (crossPaths.value) cv(projectID.value) else projectID.value
      val pi = projectInfo.value

      val pluginDescriptor = new PluginDescriptor()
      pluginDescriptor.setName(pi.nameFormal)
      pluginDescriptor.setDescription(pi.description)
      pluginDescriptor.setGroupId(pid.organization)
      pluginDescriptor.setArtifactId(pid.name)
      pluginDescriptor.setVersion(pid.revision)
      pluginDescriptor.setGoalPrefix(mavenPluginGoalPrefix.value)
      pluginDescriptor.setDependencies(components)

      val request = new DefaultPluginToolsRequest(null, pluginDescriptor)
      request.setEncoding(mavenPluginEncoding.value)
      request.setSkipErrorNoDescriptorsFound(mavenPluginSkipErrorNoDescriptorsFound.value)

      extractor.execute(request).forEach { mojo => pluginDescriptor.addMojo(mojo) }

      val generator = new PluginDescriptorGenerator()
      val destinationDirectory = (Compile / resourceManaged).value / "META-INF" / "maven"
      generator.execute(destinationDirectory, request)

      Seq(destinationDirectory / "plugin.xml")
    },
    mavenGeneratePluginXml := mavenGeneratePluginXml.dependsOn(compile).value,
    resourceGenerators += mavenGeneratePluginXml.taskValue
  )

  private def mavenTestSettings: Seq[Setting[?]] = Seq(
    mavenClasspath := Seq.empty,
    mavenTestArgs := Seq(
      "-Xmx768m",
      "-XX:MaxMetaspaceSize=384m",
      "-Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2", // avoid TLS 1.3 => issues w/ jdk 11
      "-Dorg.slf4j.simpleLogger.showLogName=false",
      "-Dorg.slf4j.simpleLogger.showThreadName=false"
    ),
    mavenTest / sourceDirectory := sourceDirectory.value / "maven-test",
    mavenTest := {
      import sbt.complete.Parsers.*

      val toRun = (OptSpace ~> StringBasic).?.parsed
      val runClasspath = mavenClasspath.value
      if (runClasspath.isEmpty) {
        sys.error("The taskKey 'mavenClasspath' is not defined.")
      }
      runMavenTests(
        (mavenTest / sourceDirectory).value,
        mavenClasspath.value,
        mavenTestArgs.value,
        toRun,
        streams.value.log
      )
    }
  )

  private def runMavenTests(
      testDirectory: File,
      mavenClasspath: Seq[File],
      mavenTestArgs: Seq[String],
      toRun: Option[String],
      log: Logger
  ): Unit = {
    val testsToRun: Seq[File] = toRun
      .fold(testDirectory.listFiles().toSeq.filter(_.isDirectory)) { dir => Seq(testDirectory / dir) }
      .filter(testDir => (testDir / "test").exists)

    val results = testsToRun.map { test =>
      log.info(s"${scala.Console.BOLD} Executing: $test ${scala.Console.RESET}")
      val mavenExecutions = IO.readLines(test / "test").map(_.trim).filter(_.nonEmpty)
      val testDir = Files.createTempDirectory("maven-test").toFile
      try {
        IO.copyDirectory(test, testDir)
        val args = Seq(
          "-cp",
          mavenClasspath.map(_.getAbsolutePath).mkString(File.pathSeparator),
          s"-Dmaven.multiModuleProjectDirectory=${testDir.getAbsolutePath}"
        ) ++
          mavenTestArgs ++
          Seq(
            "org.apache.maven.cli.MavenCli",
            "--no-transfer-progress" // Do not show Maven download progress
          )
        log.info(s"Running maven test ${test.getName} with arguments ${args.mkString(" ")}")
        test.getName -> mavenExecutions.foldLeft(true) { (success, execution) =>
          if (success) {
            log.info(s"Executing mvn $execution")
            val rc = Fork.java(ForkOptions().withWorkingDirectory(testDir), args ++ execution.split(" +"))
            rc == 0
          } else {
            false
          }
        }
      } finally {
        IO.delete(testDir)
      }
    }
    results.collect { case (name, false) =>
      name
    } match {
      case Nil         => // success
      case failedTests => sys.error(failedTests.mkString("Maven tests failed: ", ",", ""))
    }
  }

  private def isRuntimeDep(configuration: Option[String]) = {
    configuration.fold(true) {
      case "compile" => true
      case "runtime" => true
      case _         => false
    }
  }
}
