package bamboomigrate.playjson

import bamboomigrate.Migration
import shapeless.ops.hlist.LeftFolder
import shapeless.{::, DepFn1, HList, Poly, Poly2}
import play.api.libs.json._

object PlayJson {
	/** Create a combined Reads from a list of migrations **/
	trait CombinedReads[Migrations <: HList, CombineReads <: Poly] extends DepFn1[Migrations] with Serializable

	object CombinedReads {
		def apply[Migrations <: HList, CombineReads <: Poly](implicit combinedReads: CombinedReads[Migrations, CombineReads]):
		Aux[Migrations, CombineReads, combinedReads.Out] = combinedReads

		type Aux[Migrations <: HList, CombineReads <: Poly, Out0] = CombinedReads[Migrations, CombineReads] { type Out = Out0 }

		implicit def atLeastOne[First, Second, Migrations <: HList, CombineReads <: Poly, OutP]
		(implicit firstReads: Reads[First],
		 secondReads: Reads[Second],
		 folder: LeftFolder.Aux[Migrations, Reads[Second], CombineReads, OutP]): Aux[Migration[First, Second] :: Migrations, CombineReads, OutP]=
			new CombinedReads[Migration[First, Second] :: Migrations, CombineReads] {
				type Out = OutP
				def apply(migrations: Migration[First, Second] :: Migrations): Out = {
					val initialReads: Reads[Second] = implicitly[Reads[Second]].orElse(implicitly[Reads[First]].map(migrations.head.migrate))
					folder(migrations.tail, initialReads): Out
				}
			}
	}

	/** Create a fallback Reads from a list of migrations, trying to use the Reads for the latest version and if that fails, tries the one for the version before and so on **/
	object combineReadsByFallback extends Poly2 {
		implicit def combine[From, To: Reads]: Case.Aux[Reads[From], Migration[From, To], Reads[To]] =
			at{ (fromReads: Reads[From], migration: Migration[From, To]) =>
				implicitly[Reads[To]].orElse( fromReads.map(migration.migrate) )
			}
	}

	/**
	  * Creates a circe Reads from the given list of migrations.
	  * The reads will try to read json into the target type of the last migration.
	  * If that fails it will try the target type of the penultimate migration and so on until it succeeds or there is no migration left.
	  * It is required that there (implicitly) exists a reads for each type of the given list of migrations
	  * @param migrations The list of migrations
	  * @return The fallback reads
	  */
	def createFallbackReads[MigrationList <: HList, FallbackReadsResult](migrations: MigrationList)(
		implicit combineReads: CombinedReads.Aux[MigrationList, combineReadsByFallback.type, FallbackReadsResult]
	): FallbackReadsResult = combineReads(migrations)
}