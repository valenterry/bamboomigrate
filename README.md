
# Bamboomigrate
[![Build Status](https://api.travis-ci.org/valenterry/bamboomigrate.png?branch=master)](https://travis-ci.org/valenterry/bamboomigrate) 
[![Maven Central](https://img.shields.io/maven-central/v/de.willscher/bamboomigrate_2.11.svg)](https://maven-badges.herokuapp.com/maven-central/de.willscher/bamboomigrate_2.11)<sup>(Scala 2.11)</sup> 
[![Maven Central](https://img.shields.io/maven-central/v/de.willscher/bamboomigrate_2.12.svg)](https://maven-badges.herokuapp.com/maven-central/de.willscher/bamboomigrate_2.12)<sup>(Scala 2.12)</sup>

## Table of Contents
1. [About](#about)
2. [How to use](#how-to-use)
    1. [SBT](#sbt)
    2. [Maven](#maven)
    3. [Why Bamboomigrate](#why-bamboomigrate)
    4. [Basic example](#basic-example)
    5. [Documentation](#documentation)
3. [Summary](#summary)
4. [Known issues](#known-issues)

## About

Bamboomigrate is a Scala library that allows to transforms types into other types (for instance case classes), purely at compile time.

It makes heavy use of [shapeless] and its typelevel programming features.
Apart from that, Bamboomigrate is written in plain Scala and does not use any kind of macro magic or runtime reflection,
trying to explore what is possible with the Scala typesystem alone.

While the intended main usage is to provide a convenient way to deserialize multiple historical versions of json into the same case class,
it is designed to be extended for different purposes.

[shapeless]: https://github.com/milessabin/shapeless

## How to use

Bamboomigrate is built for Scala 2.11 and 2.12, with dependencies of shapeless and also [play-json] (together with [play-json-derived-codecs]) and [circe] for third party json (de)serialization support.
The latest stable version is 0.2. You can easily include it via SBT or Maven.

[play-json]: https://github.com/playframework/play-json
[play-json-derived-codecs]: https://github.com/julienrf/play-json-derived-codecs
[circe]: https://circe.github.io/circe/

### SBT

Add the following to your built.sbt:
~~~ scala
libraryDependencies += "de.willscher" %% "bamboomigrate" % "0.2"
~~~

### Maven

For 2.11 add to your pom.xml:
~~~ xml
<dependency>
  <groupId>de.willscher</groupId>
  <artifactId>bamboomigrate_2.11</artifactId>
  <version>0.2</version>
</dependency>
~~~

For 2.12 add to your pom.xml:
~~~ xml
<dependency>
  <groupId>de.willscher</groupId>
  <artifactId>bamboomigrate_2.12</artifactId>
  <version>0.2</version>
</dependency>
~~~

### Why Bamboomigrate

The idea for Bamboomigrate stems from the various issues that arise when a code base is changed over time.
An application (built from some sources of some specific point in time) often needs to handle data in a specified format, for instance json or xml.
In practice it will need to query a NoSQL database or receive a web request, both of it resulting in some json which needs to be parsed.
In the most simple case there is only one kind of json-format to handle.
However, in most real world applications, the format of json will change over time.
In the internal model - usually implemented with case classes) - fields are getting added, removed or changed, but older json versions of that model might still exist in the database or older clients. 
This makes handling this json more complex over time because multiple versions need to be considered and parsed.

It is hard enough to make a big application work in the simple case with just one kind of json for each internal model even though Scalas powerful typesystem makes this burden much lighter and aids in typesafe (de)serialization json into case classes. 

However, as soon as compatibility with old json must be taken into account, most applications lose typesafety.
It is now up to the developer to make sure that changes to important case classes don't break compatibility with old json versions.
The developer must either implement fallback mechanisms or he needs to get rid of the old json versions e.g. by converting them to newer versions.
The latter is often hard, error-prone or even impossible for external clients or big databases.
Even worse, if a developer who is not familiar with the codebase changes a case class and forgets to add the correct fallback mechanisms, then runtime errors can occur and break the application.  
Tests can help but it is easy to forget to write them, keep them up to date and work them out in a way that no errors can slip through.
 
Bamboomigrate solves both the tedious manual implementation of implementing fallbacks and the danger of forgetting to add fallbacks.  

### Basic example

Consider an application for user administration.
In the beginning there was a case class to describe the users:

~~~ scala
case class User(firstname: String, familyname: String, birthdate: Long)`
~~~

Over time, we needed to change the model of our user step by step.
We renamed `firstname` to `givenname`, added the number of `logins`, removed the birthdate due to compliance rules and added a `nickname`.
These changes made it so that in our application a user now looks like that:

~~~ scala
case class User(givenname: String, familyname: String, nickname: Option[String], logins: Int)
~~~

With Bamboomigrate we model these changes over time in the following way (in this example we decide to use play-json):
~~~ scala
import play.api.libs.json._

//How the user looked when he was first created
case class InitialUser(firstname: String, familyname: String, birthdate: Long)

//How the user is currently used everywhere in our application
case class User(givenname: String, familyname: String, nickname: Option[String], logins: Int)

object User {
   import bamboomigrate._, playjson._
   import shapeless.syntax.singleton.mkSingletonOps

   implicit val userReads = PlayJson.createFallbackReads(                //Create a fallback-reads for the following migrations
      Migration.between[InitialUser, User](                              //from the class InitialUser to the target class User
         RenameStep('firstname ->> "", 'givenname ->> "") ::             //We first change the name of the firstname-field
         AppendStep('logins ->> 0) ::                                    //Then we add a logins-field at the end of the class, giving 42 as default value
         RemoveStep('birthdate.narrow) ::                                //Remove the birthday field, we are not allowed to use this anymore
         InsertAtStep(Nat(2), 'nickname ->> (None: Option[String]) ) ::  //Add a nickname-field behind familyname, with None as default value
         HNil
      )
   )
}
~~~

We can now safely parse *all* versions of user-json that ever existed at some point in time in our business.
Some examples:
~~~ scala
val user1: JsResult[User] = Json.parse("""
   {"firstname": "miles", "familyname": "sabin", "birthdate": 123456 }
""").validate[User]
//user1 = JsSuccess(User(miles,sabin,None,0),)

val user2: JsResult[User] = Json.parse("""
   {"givenname": "travis", "familyname": "brown", "birthdate": 123456, "logins": 5 }
""").validate[User]
//user2 = JsSuccess(User(travis,brown,None,5),)

val user3: JsResult[User] = Json.parse("""
   {"givenname": "jeremy r.", "familyname": "smith", "nickname": "jeremyrsmith", "logins": 42 }
""").validate[User]
//user3 = JsSuccess(User(jeremy r.,smith,Some(jeremyrsmith),42),)
~~~

So far so good. But what happens when someone makes a change to the `User` to add a karma value to the user?
(karma describes if the user is rather famous or not)
Without Bamboomigrate, reading the json in our previous exmaples will fail at runtime and might break the application.
On the other hand, if we use Bamboomigrate our application won't even compile in the beginning. Let's try this out and change the `User`:
~~~ scala
case class User(karma: Double, givenname: String, familyname: String, nickname: Option[String], logins: Int)
~~~
We get an error at compile time (formatted):

```
Error:(14, 39) Unable to migrate from base InitialUser to target User using steps
   bamboomigrate.RenameStep[Symbol with shapeless.tag.Tagged[String("firstname")],Symbol with shapeless.tag.Tagged[String("givenname")]] ::
   bamboomigrate.AppendStep[Symbol with shapeless.tag.Tagged[String("logins")],Int] ::
   bamboomigrate.RemoveStep[Symbol with shapeless.tag.Tagged[String("birthdate")]] ::
   bamboomigrate.InsertAtStep[shapeless.Succ[shapeless.Succ[shapeless._0]],Symbol with shapeless.tag.Tagged[String("nickname")],Option[String]] ::
   shapeless.HNil.
Check that transforming the specified base step by step (in order) of the provided steps really leads to the specified base
```
It tells us that using the `RenameStep`, `AppendStep` and so on cannot successfully transform an `InitialUser` to an `User`.
To fix the error, we are forced to add another transformation `PrependStep('karma ->> 20.0)` with a specified default karma value of 20 like so:
~~~ scala
implicit val userReads = PlayJson.createFallbackReads(
   Migration.between[InitialUser, User](
      RenameStep('firstname ->> "", 'givenname ->> "") ::
      AppendStep('logins ->> 0) ::
      RemoveStep('birthdate.narrow) ::
      InsertAtStep(Nat(2), 'nickname ->> (None: Option[String]) ) ::
      PrependStep('karma ->> 20.0) :: // <- here we add the karma-field at the beginning of the class
      HNil
   )
)
~~~

Now our code will not only compile again. By adding the new transformation, Bamboomigrate is also able to deserialize all versions of json that existed before adding the karma-field.
For instance, the first of our json examples will still be successfully parsed, but the result will correspond to the updated `User` class:
~~~ scala
val user1: JsResult[User] = Json.parse("""
   {"firstname": "miles", "familyname": "sabin", "birthdate": 123456 }
""").validate[User]
//user1 = JsSuccess(User(20.0,miles,sabin,None,0),)
~~~

### Documentation

More detailed examples and more documentation of the API can be found here:

Coming soon... for now, taking a look into [the tests] might help

[the tests]: src/test/scala/MigrationTests.scala

## Summary

Bamboomigrate forces us to add support for older versions of our models.
If we forget something, our program stops compiling and we can't ship broken software.
We also don't have to write a lot of boilerplate code ourselves.
When writing software, we should aim to only write down the code that really reflects our intend.
In this case our intend is to change a class in a defined way to support older versions.
We should therefore model our class to reflect the current state and additionally describe, in which way we accomplished to get to this state.
Bamboomigrate helps us doing that, typesafe without macros nor reflection.

Feel free to contribute or to get in touch! :)

## Known issues

- Compiletimes can get slow when using many transformation steps.
Putting the case class and its compagnion object into its own file can help a bit.
Using the typelevel compiler with the `-Yinduction-heuristics` flag greatly improves performance.
See [pull request 5649] and this projects `built.sbt` for reference.
- The syntax for some steps looks alien (e.g. for the `RenameStep`).
This is due to literal types not beeing available in the langage yet.
See [literal types SIP] for reference.
- The API is partly still poorly documentated. Feel free to improve it. ;)

[literal types SIP]: http://docs.scala-lang.org/sips/pending/42.type.html
[pull request 5649]: https://github.com/scala/scala/pull/5649