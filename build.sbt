inThisBuild(
  List(
    organization := "ca.dvgi",
    homepage := Some(url("https://github.com/dvgica/managerial")),
    licenses := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "dvgica",
        "David van Geest",
        "david.vangeest@gmail.com",
        url("http://dvgi.ca")
      )
    )
  )
)

val scala212Version = "2.12.14"
val scala213Version = "2.13.6"
val scala3Version = "3.0.0"
val scalaVersions = Seq(scala3Version, scala213Version, scala212Version)

lazy val root = project
  .in(file("."))
  .settings(
    name := "managerial",
    scalaVersion := scala3Version,
    crossScalaVersions := scalaVersions,
    Compile / run / fork := true,
    libraryDependencies += "org.scalameta" %% "munit" % "0.7.26" % Test
  )

ThisBuild / crossScalaVersions := scalaVersions
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches +=
  RefPredicate.StartsWith(Ref.Tag("v"))

ThisBuild / githubWorkflowPublish := Seq(
  WorkflowStep.Sbt(
    List("ci-release"),
    env = Map(
      "PGP_PASSPHRASE" -> "${{ secrets.PGP_PASSPHRASE }}",
      "PGP_SECRET" -> "${{ secrets.PGP_SECRET }}",
      "SONATYPE_PASSWORD" -> "${{ secrets.SONATYPE_PASSWORD }}",
      "SONATYPE_USERNAME" -> "${{ secrets.SONATYPE_USERNAME }}"
    )
  )
)
