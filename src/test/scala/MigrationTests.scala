import Transform._
import shapeless.record.Record
import utest._

object MigrationTests extends TestSuite {
	import shapeless._
	import shapeless.syntax.singleton._

	import TypelevelUtils.labelledGenericIdentity

	val tests = this{
		//Some common test data helper
		val dummy1FieldValue = "dummy1"
		val dummy1Field = 'dummy1 ->> dummy1FieldValue
		val dummy2FieldValue = 42
		val dummy2Field = 'dummy2 ->> dummy2FieldValue
		val testInstanceLG = dummy1Field :: dummy2Field :: HNil
		val testField = 'addedField ->> "testValue" //We use this as a field that is beeing added to the testInstaceLg above
		val testField2 = 'addedField2 ->> 123.456 //If we need another, different testField

		"Application of transformation step should generate correct record for "-{
			"prepend"- {
				val result = Transform.transformationByAnyStep.apply(testInstanceLG, PrependStep(testField))
				val expected = testField :: testInstanceLG
				assert(result == expected)
			}
			"append"- {
				val result = Transform.transformationByAnyStep.apply(testInstanceLG, AppendStep(testField))
				val expected = testInstanceLG :+ testField
				assert(result == expected)
			}
			"insertAt (at the beginning)"- {
				val result = Transform.transformationByAnyStep.apply(testInstanceLG, InsertAtStep(Nat(0), testField))
				val expected = testField :: dummy1Field :: dummy2Field :: HNil
				assert(result == expected)
			}
			"insertAt (in between existing fields)"- {
				val result = Transform.transformationByAnyStep.apply(testInstanceLG, InsertAtStep(Nat(1), testField))
				val expected = dummy1Field :: testField :: dummy2Field :: HNil
				assert(result == expected)
			}
			"insertAt (at the end)"- {
				val result = Transform.transformationByAnyStep.apply(testInstanceLG, InsertAtStep(Nat(2), testField))
				val expected = dummy1Field :: dummy2Field :: testField :: HNil
				assert(result == expected)
			}
			"remove"- {
				val result = Transform.transformationByAnyStep.apply(testInstanceLG, RemoveStep('dummy1.narrow))
				val expected = dummy2Field :: HNil
				assert(result == expected)
			}
			"rename (should not change value of field)"- {
				val result = Transform.transformationByAnyStep.apply(testInstanceLG, RenameStep('dummy1.narrow -> 'dummy1renamed.narrow))
				val expected = 'dummy1renamed ->> dummy1FieldValue :: dummy2Field :: HNil
				assert(result == expected)
			}
			"replace"- {
				val result = Transform.transformationByAnyStep.apply(testInstanceLG, ReplaceStep('dummy1.narrow -> 'dummy1renamed.narrow, (oldValue: String) => oldValue + "someNewValue"))
				val expected = 'dummy1renamed ->> (dummy1FieldValue + "someNewValue") :: dummy2Field :: HNil
				assert(result == expected)
			}
			"changeType"- {
				val result = Transform.transformationByAnyStep.apply(testInstanceLG, ChangeTypeStep('dummy1.narrow, (_: String) => 42.24))
				val expected = 'dummy1 ->> 42.24 :: dummy2Field :: HNil
				assert(result == expected)
			}
			"full transformation"- {
				val result = Transform.transformationByAnyStep.apply(
					testInstanceLG,
					FullTransformStep { (oldLG: Record.`'dummy1 -> String, 'dummy2 -> Int`.T) => testField :: oldLG(1) :: testField2 :: HNil }
				)
				val expected = testField :: dummy2Field :: testField2 :: HNil
				assert(result == expected)
			}
			"simple sequence"- {
				val result = Transform.transformationByAnyStep.apply(
					testInstanceLG,
					SequenceStep( PrependStep(testField) :: HNil)
				)
				val expected = testField :: testInstanceLG
				assert(result == expected)
			}
			"sequence with multiple transformations depending on each other"- {
				val result = Transform.transformationByAnyStep.apply(
					testInstanceLG,
					SequenceStep(
						PrependStep('nameByPrependStep ->> "valueByPrependStep") ::
						//RenameStep('nameByPrependStep ->> "tossWay", 'nameByRenameStep ->> "tossWay") ::
						RenameStep('nameByPrependStep.narrow -> 'nameByRenameStep.narrow) ::
						PrependStep('secondNameByPrependStep ->> "secondValueByPrependStep") ::
						HNil
					)
				)
				val expected = ('secondNameByPrependStep ->> "secondValueByPrependStep") :: ('nameByRenameStep ->> "valueByPrependStep") :: testInstanceLG
				assert(result == expected)
			}
		}
		"Creating a migration list for"- {
			case class TestClass(dummy1: String)
			val dummyValue = "dummy1Value"
			val testInstance = TestClass("dummy1Value")

			"a generic target type"- {
				"with an empty transformation step list"-{
					val migrationsResult = Migration.startingFrom[TestClass](HNil)
					assert(migrationsResult == HNil)
				}
				"with a list of only one transformation step"-{
					val migrations = Migration.startingFrom[TestClass](PrependStep('addedField ->> "testValue") :: HNil)
					val resultByMigration = migrations.head.migrate(testInstance)
					val expectedInstanceByMigrating = ('addedField ->> "testValue") :: ('dummy1 ->> dummyValue) :: HNil
					assert(resultByMigration == expectedInstanceByMigrating)
				}
				"with a list of multiple transformation steps"-{
					val migrations = Migration.startingFrom[TestClass](
						PrependStep('addedField ->> "testValue") ::
						AppendStep('addedField2 ->> "testValue2") ::
						AppendStep('addedField3 ->> "testValue3") ::
						HNil
					)
					val resultByMigration = (migrations(0).migrate _ andThen migrations(1).migrate _ andThen migrations(2).migrate _)( testInstance )
					val expectedInstanceByMigrating = ('addedField ->> "testValue") :: ('dummy1 ->> dummyValue) :: ('addedField2 ->> "testValue2") :: ('addedField3 ->> "testValue3") :: HNil
					assert(resultByMigration == expectedInstanceByMigrating)
				}
			}
			"a case class type"- {
				"with an empty transformation step list is not allowed"-{
					case class TargetClass(dummy1: String)
					compileError("Migration.between[TestClass, TargetClass](HNil)")
				}
				"with a list of only one transformation step"-{
					case class TargetClass(dummy1: String, addedField: String)
					val migrations = Migration.between[TestClass, TargetClass](AppendStep('addedField ->> "testValue") :: HNil)
					val resultByMigration = migrations.head.migrate(testInstance)
					val expectedInstanceByMigrating = TargetClass(dummy1 = dummyValue, addedField = "testValue")
					assert(resultByMigration == expectedInstanceByMigrating)
				}
				"with a list of multiple transformation steps"-{
					case class TargetClass(addedField: String, dummy1: String, addedField2: String)
					val migrations = Migration.between[TestClass, TargetClass](
						PrependStep('addedField ->> "testValue") ::
						AppendStep('addedField2 ->> "testValue2") ::
						HNil
					)
					val resultByMigration = (migrations(0).migrate _ andThen migrations(1).migrate _)( testInstance )
					val expectedInstanceByMigrating = TargetClass(dummy1 = dummyValue, addedField = "testValue", addedField2 = "testValue2")
					assert(resultByMigration == expectedInstanceByMigrating)
				}
				"with a list of multiple transformation steps that are not a migration path from TestClass to TargetClass is not allowed"-{
					case class TargetClass(addedField: String, dummy1: String, haveYouForgottenMe: Int, addedField2: String)
					compileError(
						"""
						Migration.between[TestClass, TargetClass](
							PrependStep('addedField ->> "testValue") ::
							|//We "forgot" this step, so the migration path should be illtyped: AppendStep('haveYouForgottenMe ->> 42) ::
							AppendStep('addedField2 ->> "testValue2") ::
							HNil
						)
						"""
					)
				}
			}
		}
		"Creating a combined migration" -{
			"from an empty list of migrations is not allowed" -{
				compileError( """Migration.combinedMigration(HNil)""")
			}
			"from a list of migrations" -{
				case class TestClass(dummy1: String)
				case class TargetClass(addedField: String, dummy1: String, addedField2: String)

				val testMigrations = Migration.between[TestClass, TargetClass](
					PrependStep('addedField ->> "testValue") ::
					AppendStep('addedField2 ->> "testValue2") ::
					HNil
				)
				val combinedMigration = Migration.combinedMigration(testMigrations)

				val migrationResult = combinedMigration.migrate(TestClass(dummy1 = "initialDummy1"))
				val expectedInstanceByMigrating = TargetClass(addedField = "testValue", dummy1 = "initialDummy1", addedField2 = "testValue2")

				assert(migrationResult == expectedInstanceByMigrating)
			}
		}
		"A fallback decoder" -{
			import io.circe.parser.decode
			import io.circe.generic.auto._
			case class TestClass(dummy1: String)

			"can be created from a list with one migration" -{
				case class TargetClass(dummy1: String, dummy2: String)
				val dummy2MigrationValue = "dummy2MigrationValue"
				val testMigrations = Migration.instance{(obj:TestClass) => TargetClass(dummy1 = obj.dummy1, dummy2 = dummy2MigrationValue)} :: HNil
				val fallbackDecoder = CatsDecoder.createFallbackDecoder(testMigrations)

				"and parse the json for TestClass" -{
					val decodeResult = decode[TargetClass](
						"""{
						  |"dummy1": "dummy1JsonValue"
						  |}
						""".stripMargin)(fallbackDecoder)
					val expectedDecodeResult = Right(TargetClass(dummy1 = "dummy1JsonValue", dummy2 = dummy2MigrationValue))
					assert(decodeResult == expectedDecodeResult)
				}
				"and parse the json for TargetClass"- {
					val decodeResult = decode[TargetClass](
						"""{
						  |"dummy1": "dummy1JsonValue",
						  |"dummy2": "dummy2JsonValue"
						  |}
						""".stripMargin)(fallbackDecoder)
					val expectedDecodeResult = Right(TargetClass(dummy1 = "dummy1JsonValue", dummy2 = "dummy2JsonValue"))
					assert(decodeResult == expectedDecodeResult)
				}
			}

			"can be created from a list with multiple migrations" -{
				case class TargetClass(prependedField: String, dummy1: String, dummy2: String)
				val dummy2DefaultValue = "dummy2DefaultValue"
				val prependedFieldDefaultValue = "prependedFieldDefaultValue"
				val testMigrations =Migration.between[TestClass, TargetClass](
					PrependStep('prependedField ->> prependedFieldDefaultValue) ::
					AppendStep('dummy2 ->> dummy2DefaultValue) ::
					HNil
				)
				val fallbackDecoder = CatsDecoder.createFallbackDecoder(testMigrations)

				"and parse the json for TestClass" -{
					val decodeResult = decode[TargetClass](
						"""{
						  |"dummy1": "dummy1JsonValue"
						  |}
						""".stripMargin)(fallbackDecoder)
					val expectedDecodeResult = Right(TargetClass(dummy1 = "dummy1JsonValue", dummy2 = dummy2DefaultValue, prependedField = prependedFieldDefaultValue))
					assert(decodeResult == expectedDecodeResult)
				}
				"and parse the json for the intermediate type like: `prependedField: String, dummy1: String`"- {
					val decodeResult = decode[TargetClass](
						"""{
						  |"dummy1": "dummy1JsonValue",
						  |"prependedField": "prependedFieldJsonValue"
						  |}
						""".stripMargin)(fallbackDecoder)
					val expectedDecodeResult = Right(TargetClass(dummy1 = "dummy1JsonValue", dummy2 = dummy2DefaultValue, prependedField = "prependedFieldJsonValue"))
					assert(decodeResult == expectedDecodeResult)
				}
				"and parse the json for TargetClass"- {
					val decodeResult = decode[TargetClass](
						"""{
						  |"dummy1": "dummy1JsonValue",
						  |"prependedField": "prependedFieldJsonValue",
						  |"dummy2": "dummy2JsonValue"
						  |}
						""".stripMargin)(fallbackDecoder)
					val expectedDecodeResult = Right(TargetClass(dummy1 = "dummy1JsonValue", dummy2 = "dummy2JsonValue", prependedField = "prependedFieldJsonValue"))
					assert(decodeResult == expectedDecodeResult)
				}
			}

			"cannot be created from a list with no migrations" - {
				compileError(""" Transform.combinedDecoder(HNil) """)
			}
		}
		//Works with more fields, but takes waaay to long. The scalac flag -Yinduction-heuristics greatly helps in reducing compile times, but it still takes time with many fields.
		"Work with huge amounts of anything (steps, migrations, fields in classes)"-{
			case class BaseClass(field0: String)
			case class TargetClass(field0: String,
								   field1: String, field2: String, field3: String, field4: String, field5: String/*,
								   field6: String, field7: String, field8: String, field9: String, field10: String,
								   field11: String, field12: String, field13: String, field14: String, field15: String,
								   field16: String, field17: String, field18: String, field19: String, field20: String,
								   field21: String, field22: String, field23: String, field24: String, field25: String,
								   field26: String, field27: String, field28: String, field29: String, field30: String,
								   field31: String, field32: String, field33: String, field34: String, field35: String,
								   field36: String, field37: String, field38: String, field39: String, field40: String*/)

			val migrations = Migration.between[BaseClass, TargetClass](
				AppendStep('field1 ->> "field1Value") :: AppendStep('field2 ->> "field2Value") :: AppendStep('field3 ->> "field3Value") ::
				AppendStep('field4 ->> "field4Value") :: AppendStep('field5 ->> "field5Value")/* :: AppendStep('field6 ->> "field6Value") ::
					AppendStep('field7 ->> "field7Value") :: AppendStep('field8 ->> "field8Value") :: AppendStep('field9 ->> "field9Value") ::
					AppendStep('field10 ->> "field10Value") :: AppendStep('field11 ->> "field11Value") :: AppendStep('field12 ->> "field12Value") ::
					AppendStep('field13 ->> "field13Value") :: AppendStep('field14 ->> "field14Value") :: AppendStep('field15 ->> "field15Value") ::
					AppendStep('field16 ->> "field16Value") :: AppendStep('field17 ->> "field17Value") :: AppendStep('field18 ->> "field18Value") ::
					AppendStep('field19 ->> "field19Value") :: AppendStep('field20 ->> "field20Value") :: AppendStep('field21 ->> "field21Value") ::
					AppendStep('field22 ->> "field22Value") :: AppendStep('field23 ->> "field23Value") :: AppendStep('field24 ->> "field24Value") ::
					AppendStep('field25 ->> "field25Value") :: AppendStep('field26 ->> "field26Value") :: AppendStep('field27 ->> "field27Value") ::
					AppendStep('field28 ->> "field28Value") :: AppendStep('field29 ->> "field29Value") :: AppendStep('field30 ->> "field30Value") ::
					AppendStep('field31 ->> "field31Value") :: AppendStep('field32 ->> "field32Value") :: AppendStep('field33 ->> "field33Value") ::
					AppendStep('field34 ->> "field34Value") :: AppendStep('field35 ->> "field35Value") :: AppendStep('field36 ->> "field36Value") ::
					AppendStep('field37 ->> "field37Value") :: AppendStep('field38 ->> "field38Value") :: AppendStep('field39 ->> "field39Value") ::
					AppendStep('field40 ->> "field40Value")*/ :: HNil
			)
			val combinedMigration = Migration.combinedMigration(migrations)
			val expectedInstanceByMigrating = TargetClass( field0 = "field0Value",
				field1 = "field1Value", field2 = "field2Value", field3 = "field3Value", field4 = "field4Value",
				field5 = "field5Value"/*, field6 = "field6Value", field7 = "field7Value", field8 = "field8Value",
				field9 = "field9Value", field10 = "field10Value", field11 = "field11Value", field12 = "field12Value",
				field13 = "field13Value", field14 = "field14Value", field15 = "field15Value", field16 = "field16Value",
				field17 = "field17Value", field18 = "field18Value", field19 = "field19Value", field20 = "field20Value",
				field21 = "field21Value", field22 = "field22Value", field23 = "field23Value", field24 = "field24Value",
				field25 = "field25Value", field26 = "field26Value", field27 = "field27Value", field28 = "field28Value",
				field29 = "field29Value", field30 = "field30Value", field31 = "field31Value", field32 = "field32Value",
				field33 = "field33Value", field34 = "field34Value", field35 = "field35Value", field36 = "field36Value",
				field37 = "field37Value", field38 = "field38Value", field39 = "field39Value", field40 = "field40Value"*/
			)

			val migrationResult = combinedMigration.migrate(BaseClass("field0Value"))

			assert(migrationResult == expectedInstanceByMigrating)
		}
	}
}