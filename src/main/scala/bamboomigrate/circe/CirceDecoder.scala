package bamboomigrate.circe

import bamboomigrate.{Migration, TypelevelUtils}
import io.circe.Decoder
import shapeless.ops.hlist.LeftFolder
import shapeless.{::, DepFn1, HList, LabelledGeneric, Poly, Poly2}

object CirceDecoder {
	/** Create a combined Decoder from a list of migrations **/
	trait CombinedDecoder[Migrations <: HList, CombineDecoders <: Poly] extends DepFn1[Migrations] with Serializable

	object CombinedDecoder {
		def apply[Migrations <: HList, CombineDecoders <: Poly](implicit combinedDecoders: CombinedDecoder[Migrations, CombineDecoders]):
		Aux[Migrations, CombineDecoders, combinedDecoders.Out] = combinedDecoders

		type Aux[Migrations <: HList, CombineDecoders <: Poly, Out0] = CombinedDecoder[Migrations, CombineDecoders] { type Out = Out0 }

		implicit def atLeastOne[First, Second, Migrations <: HList, CombineDecoders <: Poly, OutP]
		(implicit firstDecoder: Decoder[First],
		 secondDecoder: Decoder[Second],
		 folder: LeftFolder.Aux[Migrations, Decoder[Second], CombineDecoders, OutP]): Aux[Migration[First, Second] :: Migrations, CombineDecoders, OutP]=
			new CombinedDecoder[Migration[First, Second] :: Migrations, CombineDecoders] {
				type Out = OutP
				def apply(migrations: Migration[First, Second] :: Migrations): Out = {
					val initialDecoder: Decoder[Second] = Decoder[Second].or(Decoder[First].map(migrations.head.migrate))
					folder(migrations.tail, initialDecoder): Out
				}
			}
	}

	/** Create a fallback Decoder from a list of migrations, trying to use the decoder for the latest version and if that fails, tries the one for the version before and so on **/
	object combineDecodersByFallback extends Poly2 {
		implicit def combine[From, To: Decoder]: Case.Aux[Decoder[From], Migration[From, To], Decoder[To]] =
			at{ (fromDecoder: Decoder[From], migration: Migration[From, To]) =>
				Decoder[To].or( fromDecoder.map(migration.migrate)):Decoder[To]
			}
	}

	/**
	  * Creates a circe Decoder from the given list of migrations.
	  * The decoder will try to decode json into the target type of the last migration.
	  * If that fails it will try the target type of the penultimate migration and so on until it succeeds or there is no migration left.
	  * It is required that there (implicitly) exists a decoder for each type of the given list of migrations
	  * @param migrations The list of migrations
	  * @return The fallback decoder
	  */
	def createFallbackDecoder[MigrationList <: HList, FallbackDecoderResult](migrations: MigrationList)(
		implicit combineDecoders: CombinedDecoder.Aux[MigrationList, combineDecodersByFallback.type, FallbackDecoderResult]
	): FallbackDecoderResult = combineDecoders(migrations)
}