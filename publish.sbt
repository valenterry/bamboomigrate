/*
	This will publish to sonatype / maven central
	Use `sbt -Dsonatype.username=$user -Dsonatype.password=$password "+ publish"` to publish IF you have sonatype access.
	The env vars above will be set when building/publishing with travis CI. Also see sonatype.sbt
 */

publishMavenStyle := true

pomIncludeRepository := { _ => false }

publishTo := {
	val nexus = "https://oss.sonatype.org/"
	if (isSnapshot.value)
		Some("snapshots" at nexus + "content/repositories/snapshots")
	else
		Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

pomExtra := (
	<url>https://github.com/valenterry/bamboomigrate</url>
		<licenses>
			<license>
				<name>MIT License</name>
				<url>http://www.opensource.org/licenses/mit-license.php</url>
				<distribution>repo</distribution>
			</license>
		</licenses>
		<scm>
			<url>git@github.com:valenterry/bamboomigrate.git</url>
			<connection>scm:git:git@github.com:valenterry/bamboomigrate.git</connection>
		</scm>
		<developers>
			<developer>
				<id>valenterry</id>
				<name>Valentin Willscher</name>
			</developer>
		</developers>
	)