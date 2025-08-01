package ai.nixiesearch.e2e

import ai.nixiesearch.api.SearchRoute.{SuggestRequest, SuggestResponse}
import ai.nixiesearch.api.{IndexModifyRoute, SearchRoute}
import ai.nixiesearch.config.{Config, InferenceConfig}
import ai.nixiesearch.config.mapping.IndexName
import ai.nixiesearch.util.Tags.EndToEnd
import ai.nixiesearch.util.{DatasetLoader, EnvVars, SearchTest}
import cats.effect.IO
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import io.circe.syntax.*
import org.http4s.{Entity, Method, Request, Uri}
import scodec.bits.ByteVector
import cats.effect.unsafe.implicits.global

import java.io.File

class MoviesSuggestionEndToEndTest extends AnyFlatSpec with Matchers with SearchTest {
  lazy val pwd  = System.getProperty("user.dir")
  lazy val conf =
    Config.load(new File(s"$pwd/src/test/resources/datasets/movies/config.yaml"), EnvVars(Map.empty)).unsafeRunSync()
  lazy val mapping                        = conf.schema(IndexName.unsafe("movies"))
  val docs                                = Nil
  override def inference: InferenceConfig = conf.inference

  it should "load docs and suggest" taggedAs (EndToEnd.Index) in withIndex { nixie =>
    {
      lazy val docs = DatasetLoader.fromFile(s"$pwd/src/test/resources/datasets/movies/movies.jsonl.gz", mapping, 1000)
      val indexApi  = IndexModifyRoute(nixie.indexer)
      val searchApi = SearchRoute(nixie.searcher)

      val jsonPayload  = docs.map(doc => doc.asJson.noSpaces).mkString("\n")
      val indexRequest = Request[IO](
        method = Method.PUT,
        uri = Uri.unsafeFromString("http://localhost:8080/movies/_index"),
        entity = Entity.strict(ByteVector.view(jsonPayload.getBytes()))
      )
      indexApi.index(indexRequest).unsafeRunSync()
      indexApi.flush().unsafeRunSync()

      val searchRequest = Request[IO](
        method = Method.POST,
        uri = Uri.unsafeFromString("http://localhost:8080/movies/_suggest"),
        entity = Entity.strict(
          ByteVector.view(
            SuggestRequest(query = "man", fields = List("title"), count = 20).asJson.noSpaces.getBytes()
          )
        )
      )
      val response = searchApi.suggest(searchRequest).unsafeRunSync().as[SuggestResponse].unsafeRunSync()

      response.suggestions.size shouldBe 20
    }
  }

}
