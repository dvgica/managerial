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

val scala212Version = "2.12.20"
val scala213Version = "2.13.16"
val scala3Version = "3.3.6"
val scalaVersions =
  Seq(
    scala213Version,
    scala212Version,
    scala3Version
  )

def subproject(name: String) = Project(
  id = name,
  base = file(name)
).settings(
  scalaVersion := scala213Version,
  crossScalaVersions := scalaVersions,
  libraryDependencies += "org.scalameta" %% "munit" % "1.2.0" % Test
)

lazy val managerial = subproject("managerial")

lazy val managerialTwitterUtil =
  subproject("managerial-twitter-util")
    .dependsOn(managerial)
    .settings(
      libraryDependencies += "com.twitter" %% "util-core" % "24.2.0" % Provided exclude (
        "org.scala-lang.modules",
        "scala-collection-compat_3"
      )
    )

lazy val root = project
  .in(file("."))
  .aggregate(
    managerial,
    managerialTwitterUtil
  )
  .settings(
    publish / skip := true,
    crossScalaVersions := Nil
  )

ThisBuild / crossScalaVersions := scalaVersions
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("8"))
ThisBuild / githubWorkflowBuildPreamble := Seq(
  WorkflowStep.Sbt(
    List("scalafmtCheckAll", "scalafmtSbtCheck"),
    name = Some("Check formatting with scalafmt")
  )
)
ThisBuild / githubWorkflowTargetTags ++= Seq("v*")
ThisBuild / githubWorkflowPublishTargetBranches :=
  Seq(RefPredicate.StartsWith(Ref.Tag("v")))

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
