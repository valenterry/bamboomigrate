val sonatypeCredentials = for {
	username <- Option(sys.props("SONATYPE_USERNAME"))
	password <- Option(sys.props("SONATYPE_PASSWORD"))
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)

//If we cannot find the sonatype credentials we just pass none which makes "sbt publish" fail with unauthorized. Not nice but works. :(
credentials ++= sonatypeCredentials.map(credentials => List(credentials)).getOrElse(Nil)
