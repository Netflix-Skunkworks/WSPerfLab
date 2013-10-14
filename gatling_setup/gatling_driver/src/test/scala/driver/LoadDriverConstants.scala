package driver

object LoadDriverConstants {
    import java.lang.ThreadLocal
    import java.util.Random

    val repetitions = 250
    val warmup = 5
    val totalUsers = 8000
    val rampTime = totalUsers/1000+1
    var file = "/tmp/loadtesturl.txt"

    val randomPerThread : ThreadLocal[Random] = {
        new ThreadLocal[Random] {
            override protected def initialValue() : Random =  { new Random }
        }
    }

    /** grab the host name from /tmp/servers.txt (from grab_ids.py) - since there should only be 1 ws_impl, just grab the first host */
    val url : String = {
        import scala.io.Source
        val source = Source.fromFile(file)
        val line = source.getLines.find(_ => true).getOrElse("NO HOST SPECIFIED")
        source.close
        line
    }

    def nextURL() : String = {
        val id = randomPerThread.get.nextInt(90000)+10000 // pattern from validate.py script [10000, 100000) range
        return url + "testA?id=" + id
    }
}