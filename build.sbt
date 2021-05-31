val scala212Version = "2.12.14"
val scala213Version = "2.13.6"
val scala3Version = "3.0.0"
val scalaVersions = Seq(scala3Version, scala213Version, scala212Version)

lazy val root = project
  .in(file("."))
  .settings(
    organization := "codes.dvg",
    name := "managerial",
    version := "0.1.0",
    scalaVersion := scala3Version,
    crossScalaVersions := scalaVersions,
    Compile / run / fork := true,
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.26" % Test
  )

ThisBuild / crossScalaVersions := scalaVersions
ThisBuild / githubWorkflowPublishTargetBranches := Seq()
