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

  // change this to test new instance
  val targetURL = "http://ec2-54-202-104-228.us-west-2.compute.amazonaws.co\
m:9000/"

    // your code starts here
  val scn = scenario("My scenario")
            .repeat(500) {
               exec(http("My Page")
                 .get(targetURL))
                 .headers(headers_1)
                 .check(status.is(200)))
//                 .pause(250 milliseconds)

           }



  setUp(scn.inject(nothingFor(5 seconds),
      ramp (10000 users) over (10 seconds)))
  // your code ends here
}
