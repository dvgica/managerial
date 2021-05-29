val scala212Version = "2.12.14"
val scala213Version = "2.13.6"
val scala3Version = "3.0.0"

lazy val root = project
  .in(file("."))
  .settings(
    organization := "codes.dvg",
    name := "managerial",
    version := "0.1.0",
    scalaVersion := scala3Version,
    crossScalaVersions := Seq(scala3Version, scala213Version, scala212Version),
    Compile / run / fork := true
  )
