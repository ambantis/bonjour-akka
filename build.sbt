import com.typesafe.sbt.SbtNativePackager._
import NativePackagerKeys._

organization := "com.ambantis"

name := "hello"

version := "0.1.0"

scalaVersion := "2.11.2"

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-unchecked",
  "-feature",
  "-deprecation",
  "-language:postfixOps"
)

resolvers += "Typesafe Repo" at "https://repo.typesafe.com/typesafe/releases"

libraryDependencies := {
  val akkaVersion = "2.3.5"
  val scalatestVersion = "2.2.1"
  Seq(
    "com.typesafe.akka" %% "akka-actor"      % akkaVersion,
    "com.typesafe.akka" %% "akka-remote"     % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j"      % akkaVersion,
    "com.typesafe.akka" %% "akka-kernel"     % akkaVersion,
    "com.typesafe.akka" %% "akka-testkit"    % akkaVersion % "test",
    "org.scalatest"     %% "scalatest"       % scalatestVersion % "test",
    "ch.qos.logback"    % "logback-classic" % "1.0.13"
  )
}

parallelExecution in Test := false

ideaExcludeFolders ++= Seq(
  ".idea",
  ".idea_modules"
)

packageArchetype.java_application

