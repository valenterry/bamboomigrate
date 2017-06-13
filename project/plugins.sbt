logLevel := Level.Warn

resolvers += Resolver.bintrayRepo("tek", "maven")
addCompilerPlugin("tryp" %% "splain" % "0.1.24")

addSbtPlugin("org.xerial.sbt" % "sbt-sonatype" % "1.1")
addSbtPlugin("com.jsuereth" % "sbt-pgp" % "1.0.0")
