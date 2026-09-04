val scalaV = "2.13.18"

lazy val commonSettings = Seq(
  organization := "rzk.scala",
  version      := "1.0-SNAPSHOT",
  scalaVersion := scalaV,
  scalacOptions ++= Seq(
    "-encoding",
    "utf8",
    "-Xfatal-warnings",
    "-deprecation",
    "-unchecked",
    "-explaintypes",
    "-Ymacro-annotations",
    "-language:implicitConversions",
    "-language:higherKinds",
    "-language:existentials",
    "-language:postfixOps",
    "-Wunused:imports"
  ),
  semanticdbEnabled := true,
  semanticdbVersion := scalafixSemanticdb.revision,
  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),
  addCompilerPlugin(("org.typelevel" % "kind-projector" % "0.13.4").cross(CrossVersion.full))
)

val catsVersion = "2.13.0"
val circeVersion = "0.14.16"
val doobieVersion = "1.0.0-RC13"
val http4sVersion = "0.23.35"
val pureconfigVersion = "0.17.10"
val flywayVersion = "12.11.0"
val scalaTestVersion = "3.2.20"
val testcontainersVersion = "0.44.1"

// ── dependency groups ──────────────────────────────────────────────────────

// cats-core only (pure FP vocabulary). cats-effect is intentionally NOT here — it
// rides in transitively for the effectful modules via doobie / http4s.
val catsCoreDeps = Seq("org.typelevel" %% "cats-core" % catsVersion)

val circeDeps = Seq(
  "io.circe" %% "circe-core"           % circeVersion,
  "io.circe" %% "circe-generic"        % circeVersion,
  "io.circe" %% "circe-parser"         % circeVersion,
  "io.circe" %% "circe-generic-extras" % "0.14.4"
)

val doobieDeps = Seq(
  "org.typelevel" %% "doobie-core"     % doobieVersion,
  "org.typelevel" %% "doobie-hikari"   % doobieVersion,
  "org.typelevel" %% "doobie-postgres" % doobieVersion
)
val doobieTestDeps = Seq("org.typelevel" %% "doobie-scalatest" % doobieVersion % Test)

// `api`: route DSL + Router/middleware + circe entity codec (no concrete server backend here)
val http4sApiDeps = Seq(
  "org.http4s" %% "http4s-server" % http4sVersion,
  "org.http4s" %% "http4s-dsl"    % http4sVersion,
  "org.http4s" %% "http4s-circe"  % http4sVersion
)
// `app`: the concrete server that actually binds a port (ember-client was unused — dropped)
val http4sServerDeps = Seq("org.http4s" %% "http4s-ember-server" % http4sVersion)

val enumeratumDeps = Seq("com.beachape" %% "enumeratum" % "1.9.8")

// Scalar's browser bundle for /docs, served from the classpath (no CDN). scalar-core is
// Scalar's first-party artifact (not the community npm->Maven mirror) — its version tracks
// the Scalar JS release directly, and it ships the bundle at a version-less classpath path
// (META-INF/resources/webjars/scalar/scalar.js), so no version needs threading into code.
// Used purely as a webjar for that asset, not for its Java rendering API (which needs
// jackson-databind at runtime; this project has none).
val scalarApiReferenceVersion = "0.6.65"
val scalarApiReferenceDeps = Seq("com.scalar.maven" % "scalar-core" % scalarApiReferenceVersion)

val pureconfigDeps = Seq(
  "com.github.pureconfig" %% "pureconfig"             % pureconfigVersion,
  "com.github.pureconfig" %% "pureconfig-cats-effect" % pureconfigVersion
)

val flywayDeps = Seq(
  "org.flywaydb" % "flyway-core"                % flywayVersion,
  "org.flywaydb" % "flyway-database-postgresql" % flywayVersion
)

val loggingDeps = Seq("com.typesafe.scala-logging" %% "scala-logging" % "3.9.6")
val loggingRuntimeDeps = Seq("org.slf4j"           % "slf4j-reload4j" % "2.0.18") // binding — app only

val commonsIoDeps = Seq("commons-io" % "commons-io" % "2.22.0")
val icuDeps = Seq("com.ibm.icu"      % "icu4j"      % "78.3")

// testkit helpers live in src/main, so ScalaTest/scalactic are COMPILE deps here.
// http4s-core gives Response[IO] and transitively brings cats-effect.
val testkitDeps = catsCoreDeps ++ Seq(
  "org.scalactic" %% "scalactic"   % scalaTestVersion,
  "org.scalatest" %% "scalatest"   % scalaTestVersion,
  "org.http4s"    %% "http4s-core" % http4sVersion
)

val testingDeps = Seq(
  "org.scalactic" %% "scalactic" % scalaTestVersion % Test,
  "org.scalatest" %% "scalatest" % scalaTestVersion % Test,
  "org.scalamock" %% "scalamock" % "7.5.5"          % Test
)

val itDeps = Seq(
  "org.scalatest" %% "scalatest"                       % scalaTestVersion      % Test,
  "com.dimafeng"  %% "testcontainers-scala-scalatest"  % testcontainersVersion % Test,
  "com.dimafeng"  %% "testcontainers-scala-postgresql" % testcontainersVersion % Test
)

// ── modules ────────────────────────────────────────────────────────────────

lazy val domain = project
  .in(file("modules/domain"))
  .settings(
    name := "flight-database-domain",
    commonSettings,
    libraryDependencies ++= circeDeps ++ enumeratumDeps ++ testingDeps
  )

// Pure, domain-independent generic extensions (double, iterable, option, try_, path,
// generic string). Cross-compilable in principle; the real crossProject + path/commons-io
// extraction is deferred to frontend kickoff.
lazy val syntax = project
  .in(file("modules/syntax"))
  .settings(
    name := "flight-database-syntax",
    commonSettings,
    libraryDependencies ++= catsCoreDeps ++ commonsIoDeps ++ testingDeps
  )

// Shared test-support helpers (matchers, ioresult, response) under flightdatabase.test.syntax.
// Helpers are in src/main, hence ScalaTest as a compile dep. Consumed by other modules'
// Test scopes via `testkit % Test`.
lazy val testkit = project
  .in(file("modules/testkit"))
  .dependsOn(domain)
  .settings(
    name := "flight-database-testkit",
    commonSettings,
    libraryDependencies ++= testkitDeps
  )

lazy val persistence = project
  .in(file("modules/persistence"))
  .dependsOn(domain, syntax)
  .settings(
    name := "flight-database-persistence",
    commonSettings,
    libraryDependencies ++= doobieDeps ++ flywayDeps ++ pureconfigDeps ++ loggingDeps ++ icuDeps ++ testingDeps
  )

lazy val api = project
  .in(file("modules/api"))
  .dependsOn(domain, syntax, testkit % Test)
  .settings(
    name := "flight-database-api",
    commonSettings,
    // circe in transitively via domain until step 5
    libraryDependencies ++= http4sApiDeps ++ enumeratumDeps ++ scalarApiReferenceDeps ++ testingDeps
  )

lazy val app = project
  .in(file("modules/app"))
  .dependsOn(api, persistence)
  .settings(
    name := "flight-database-app",
    commonSettings,
    libraryDependencies ++=
      pureconfigDeps ++ http4sServerDeps ++ loggingDeps ++ loggingRuntimeDeps ++ testingDeps
  )

lazy val persistenceIt = project
  .in(file("modules/persistence-it"))
  .dependsOn(persistence, testkit % Test)
  .settings(
    name           := "flight-database-persistence-it",
    publish / skip := true,
    commonSettings,
    libraryDependencies ++= itDeps ++ doobieTestDeps,
    scalafixConfigSettings(Test)
  )

// TODO: Add frontend module

lazy val root = (project in file("."))
  .aggregate(domain, syntax, testkit, persistence, api, app, persistenceIt)
  .settings(
    name := "flight-database",
    commonSettings
  )
