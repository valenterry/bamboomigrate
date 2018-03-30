name := "bamboomigrate"

organization := "de.willscher"

scalaVersion := "2.12.4-bin-typelevel-4"
scalaOrganization in ThisBuild := "org.typelevel"

crossScalaVersions := Seq("2.11.8", "2.12.4-bin-typelevel-4")

def scalacOptionsVersion(scalaVersion: String) = {
	Seq(
		"-deprecation",                      // Emit warning and location for usages of deprecated APIs.
		"-encoding", "UTF-8",                // Specify character encoding used by source files.
		"-explaintypes",                     // Explain type errors in more detail.
		"-feature",                          // Emit warning and location for usages of features that should be imported explicitly.
		"-language:existentials",            // Existential types (besides wildcard types) can be written and inferred
		"-language:experimental.macros",     // Allow macro definition (besides implementation and application)
		"-language:higherKinds",             // Allow higher-kinded types
		"-language:implicitConversions",     // Allow definition of implicit functions called views
		"-unchecked",                        // Enable additional warnings where generated code depends on assumptions.
		"-Xcheckinit",                       // Wrap field accessors to throw an exception on uninitialized access.
		"-Xfatal-warnings",                  // Fail the compilation if there are any warnings.
		"-Xfuture",                          // Turn on future language features.
		"-Xlint:adapted-args",               // Warn if an argument list is modified to match the receiver.
		"-Xlint:by-name-right-associative",  // By-name parameter of right associative operator.
		"-Xlint:delayedinit-select",         // Selecting member of DelayedInit.
		"-Xlint:doc-detached",               // A Scaladoc comment appears to be detached from its element.
		"-Xlint:inaccessible",               // Warn about inaccessible types in method signatures.
		"-Xlint:infer-any",                  // Warn when a type argument is inferred to be `Any`.
		"-Xlint:missing-interpolator",       // A string literal appears to be missing an interpolator id.
		"-Xlint:nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
		"-Xlint:nullary-unit",               // Warn when nullary methods return Unit.
		"-Xlint:option-implicit",            // Option.apply used implicit view.
		"-Xlint:package-object-classes",     // Class or object defined in package object.
		"-Xlint:poly-implicit-overload",     // Parameterized overloaded implicit methods are not visible as view bounds.
		"-Xlint:private-shadow",             // A private field (or class parameter) shadows a superclass field.
		"-Xlint:stars-align",                // Pattern sequence wildcard must align with sequence component.
		"-Xlint:type-parameter-shadow",      // A local type parameter shadows a type already in scope.
		"-Xlint:unsound-match",              // Pattern match may not be typesafe.
		"-Yno-adapted-args",                 // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
		"-Ypartial-unification",             // Enable partial unification in type constructor inference
		"-Ywarn-dead-code",                  // Warn when dead code is identified.
		"-Ywarn-inaccessible",               // Warn about inaccessible types in method signatures.
		"-Ywarn-infer-any",                  // Warn when a type argument is inferred to be `Any`.
		"-Ywarn-nullary-override",           // Warn when non-nullary `def f()' overrides nullary `def f'.
		"-Ywarn-nullary-unit",               // Warn when nullary methods return Unit.
		"-Ywarn-numeric-widen",              // Warn when numerics are widened.
		"-Ywarn-value-discard"               // Warn when non-Unit expression results are unused.
	) ++ (CrossVersion.partialVersion(scalaVersion):Option[(Int, Int)]) match {
		case list if list.last == (2, 11) => Nil
		case list if list.last == (2, 12) => Seq("-Yinduction-heuristics")
		case other => throw new Exception(s"Expected to find scala version 2.11 or 2.11 but did not found this within $other")
	}
}

scalacOptions := scalacOptionsVersion(scalaVersion.value)


libraryDependencies ++= Seq(
	"com.chuusai" %% "shapeless" % "2.3.2",
	"com.lihaoyi" %% "utest" % "0.6.3" % "test",
	"com.typesafe.play" %% "play-json" % "2.6.7",
	"org.julienrf" %% "play-json-derived-codecs" % "4.0.0-RC1"
)

val circeVersion = "0.8.0"

libraryDependencies ++= Seq(
	"io.circe" %% "circe-core",
	"io.circe" %% "circe-generic",
	"io.circe" %% "circe-parser",
	"io.circe" %% "circe-shapes"
).map(_ % circeVersion)

testFrameworks += new TestFramework("utest.runner.Framework")