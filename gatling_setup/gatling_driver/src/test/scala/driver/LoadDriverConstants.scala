package driver

object LoadDriverConstants {
    import java.lang.ThreadLocal
    import java.util.Random

    val repetitions = 10000
    val warmup = 5
    val usersPerRamp = 750
    val rampTime = 5
    val runTime = 25
    var urlfile = "/tmp/loadtesturl.txt"

    val randomPerThread : ThreadLocal[Random] = {
        new ThreadLocal[Random] {
            override protected def initialValue() : Random =  { new Random }
        }
    }


    /** grab the host name from /tmp/servers.txt (from grab_ids.py) - since there should only be 1 ws_impl, just grab the first host */
    val url : String = {
        import scala.io.Source
        val source = Source.fromFile(urlfile)
        val line = source.getLines.find(_ => true).getOrElse("NO HOST SPECIFIED")
        source.close
        if (line.endsWith("/")) {
            line
        }
        else {
            line+"/"
        }
    }

    def nextURL() : String = {
        val id = randomPerThread.get.nextInt(90000)+10000 // pattern from validate.py script [10000, 100000) range
        url + "testA?id=" + id
    }
}