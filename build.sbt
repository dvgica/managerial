inThisBuild(
  List(
    organization := "ca.dvgi",
    homepage := Some(uri("https://github.com/dvgica/managerial")),
    licenses := List("Apache-2.0" -> uri("http://www.apache.org/licenses/LICENSE-2.0")),
    developers := List(
      Developer(
        "dvgica",
        "David van Geest",
        "david.vangeest@gmail.com",
        uri("http://dvgi.ca")
      )
    )
  )
)

val scala212Version = "2.12.21"
val scala213Version = "2.13.18"
val scala3Version = "3.3.8"
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
  libraryDependencies += "org.scalameta" %% "munit" % "1.3.5" % Test
)

lazy val managerial = subproject("managerial")

lazy val managerialTwitterUtil =
  subproject("managerial-twitter-util")
    .dependsOn(managerial)
    .settings(
      libraryDependencies += ("com.twitter" %% "util-core" % "24.2.0" % Provided)
        .exclude("org.scala-lang.modules", "scala-collection-compat_3")
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
ThisBuild / githubWorkflowJavaVersions := Seq(JavaSpec.temurin("17"))
ThisBuild / githubWorkflowBuildPreamble := Seq(
  WorkflowStep.Sbt(
    List("scalafmtCheckAll", "scalafmtSbtCheck"),
    name = Some("Check formatting with scalafmt")
  )
)
// One setting drives both the pull_request and push triggers. Left at the
// default of "**", a branch in this repo with an open PR fires both and every
// job runs twice. For pull_request this filters on the base branch, so "main"
// still covers every PR; for push it means only main and the release tags.
ThisBuild / githubWorkflowTargetBranches := Seq("main")
ThisBuild / githubWorkflowTargetTags := Seq("v*")

// The generated Clean workflow deletes build artifacts, and this build stopped
// uploading any when githubWorkflowArtifactUpload was disabled. It is rendered
// as a bare `on: push`, so it was also running on every branch.
ThisBuild / githubWorkflowIncludeClean := false
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

// sbt 2 puts targets under target/out/jvm/scala-<version>/, so the generated
// artifact upload steps embed whichever Scala version is active. That makes
// githubWorkflowCheck fail in every cross-build job but one.
ThisBuild / githubWorkflowArtifactUpload := false

// In sbt 2, `test` only runs tests that failed before, were not run, or whose
// dependencies changed. Its results are cached in ~/.cache/sbt, which CI
// restores across runs, so a job can report success having run nothing.
// `testFull` runs every test.
ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(List("testFull"), name = Some("Build project"))
)

// Mergify and branch protection match on check names, which for a matrix job
// embed the Scala version. This job depends on the matrix as a whole, so its
// check name is stable, and needs.build.result is only "success" when every
// leg passed. always() makes it report a conclusion even when the matrix
// fails, rather than being skipped.
ThisBuild / githubWorkflowAddedJobs := Seq(
  WorkflowJob(
    id = "build-success",
    name = "Build and Test Success",
    steps = List(
      WorkflowStep.Run(
        List("exit 1"),
        name = Some("Fail if the build matrix did not succeed"),
        cond = Some("needs.build.result != 'success'")
      )
    ),
    cond = Some("always()"),
    needs = List("build"),
    // sbt-github-actions always emits an os/scala/java matrix, and GitHub
    // appends the matrix values to the check name. Empty dimensions produce a
    // workflow GitHub rejects, so the scala dimension is a constant rather
    // than a real version: this job builds nothing and never reads it, and it
    // keeps the check name stable as crossScalaVersions changes.
    scalas = List("all"),
    javas = List(JavaSpec.temurin("17"))
  )
)
