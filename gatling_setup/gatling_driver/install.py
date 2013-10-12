import os
from subprocess import call, PIPE

def callWithErrorHandling(command):
    print "calling command"
    try :
        retcode = call(command, shell=True)
        if (retcode != 0):
            raise Exception("command failed, return %(code)d %(cmd)s" % {"code" : retcode, "cmd" : command})
    except Exception:
        print "exception on " + command
        raise

gatling_name = "gatling-maven-plugin-demo"
git_repo = "git://github.com/excilys/gatling-maven-plugin-demo.git"
driver_dir = "src/test/scala/driver"
ws_perf_loc = "$HOME/WSPerfLab/gatling_setup/gatling_driver"
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
callWithErrorHandling("cd " + gatling_name + "; ../mvn gatling:execute -Dgatling.simulationClass=driver.LoadDriver")

