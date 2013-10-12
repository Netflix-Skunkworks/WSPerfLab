import os
import sys
from subprocess import call, PIPE

if (len(sys.argv) < 2):
    raise Exception("usage run_gatling_local <URL_TO_STRESS_TEST>")

targetURL = sys.argv[1]

def callWithErrorHandling(command):
    print "calling command %(cmd)s" % {'cmd' : command }
    try :
        call(command, shell=True)
    except Exception:
        print "exception on " + command
        raise

gatling_name = "gatling-maven-plugin-demo"
git_repo = "git://github.com/excilys/gatling-maven-plugin-demo.git"
driver_dir = "src/test/scala/driver"
ws_perf_loc = "$HOME/WSPerfLab/gatling_setup/gatling_driver"
target_file_location = "/tmp/loadtesturl.txt"

f=open(target_file_location, "w")
f.write(targetURL)
f.close()

callWithErrorHandling("cd %(dir)s" % {"dir" : ws_perf_loc} )
if (os.path.exists("gatling-maven-plugin-demo")):
    print "already have gatling installed, skipping"
else:
    callWithErrorHandling(
        "git clone " + git_repo)
copy_driver_dir = gatling_name + "/" + driver_dir
callWithErrorHandling("rm -rf " + copy_driver_dir)
callWithErrorHandling("cp -rf " + driver_dir + " " + copy_driver_dir)
callWithErrorHandling("python install_maven.py")
callWithErrorHandling("cd " + gatling_name + "; ../mvn gatling:execute -Dgatling.simulationClass=driver.LoadDriver; bash ../link_latest_log.sh")

