package driver

object LoadDriverConstants {
    val repetitions = 500
    val warmup = 5
    val totalUsers = 10000
    val rampTime = totalUsers/1000+1
    var file = "/tmp/loadtesturl.txt"

    /** grab the host name from /tmp/servers.txt (from grab_ids.py) - since there should only be 1 ws_impl, just grab the first host */
    val url : String = {
        import scala.io.Source
        val source = Source.fromFile(file)
        val line = source.getLines.find(_ => true).getOrElse("NO HOST SPECIFIED")
        source.close
        line
    }
}