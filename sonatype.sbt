val sonatypeCredentials = for {
	username <- sys.props.get("SONATYPE_USERNAME").orElse(sys.env.get("SONATYPE_USERNAME"))
	password <- sys.props.get("SONATYPE_PASSWORD").orElse(sys.env.get("SONATYPE_PASSWORD"))
} yield Credentials("Sonatype Nexus Repository Manager", "oss.sonatype.org", username, password)

//If we cannot find the sonatype credentials we just pass none which makes "sbt publish" fail with unauthorized. Not nice but works. :(
credentials ++= sonatypeCredentials.toList

pgpSecretRing := file("keys/secring.asc") //Yep, public
pgpPublicRing := file("keys/pubring.asc")
pgpPassphrase := sys.props.get("SONATYPE_SECRETKEY_PHRASE").orElse(sys.env.get("SONATYPE_SECRETKEY_PHRASE")).map(_.toCharArray)