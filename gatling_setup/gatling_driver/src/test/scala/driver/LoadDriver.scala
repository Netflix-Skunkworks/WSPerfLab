package driver

import io.gatling.core.Predef._
import io.gatling.http.Predef._
import io.gatling.jdbc.Predef._
import io.gatling.http.Headers.Names._
import scala.concurrent.duration._
import bootstrap._
import assertions._

class LoadDriver extends Simulation {
  val headers_1 = Map(
      "Keep-Alive" -> "1200")

    // your code starts here
  val scn = scenario("My scenario")
            .repeat(LoadDriverConstants.repetitions) {
               exec(http("My Page")
                 .get(LoadDriverConstants.url)
                 .headers(headers_1)
                 .check(status.is(200)))
            }



  setUp(scn.inject(nothingFor(LoadDriverConstants.warmup seconds),
      ramp (LoadDriverConstants.totalUsers users) over (LoadDriverConstants.rampTime seconds)))
  // your code ends here
}
