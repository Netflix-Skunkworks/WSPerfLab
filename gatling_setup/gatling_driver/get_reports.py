# you should run this once, regardless of the impl installed, to copy the mocks we round robin against.
import sys
import getopt
import os
from subprocess import call
import time

scp = 'scp'

def usage():
    print """
    -h, --help - print this message
    -s, --scp - override scp command to ssh_to_use
    -r, --remote_hosts - the list of remote hosts to copy from, one per line
    -f, --fetch - clobber existing gatling and refetch, also deletes any downloaded reports
    """

try:
    opts, args = getopt.getopt(sys.argv[1:], "hfs:r:", ["--help", "--fetch",--scp=", "--remote_hosts="])
except Exception:
    # print help information and exit:
    print(sys.exc_info()[:1]) # will print something like "option -a not recognized"
    usage()
    sys.exit(2)

remote_host_file=None
fetch = False

for o, a in opts:
    if o in ("-h", "--help"):
        usage()
        sys.exit()
    elif o in ("-f", "--force"):
        fetch=True
    elif o in ("-s", "--scp"):
        scp=a
    elif o in ("-r", "--remote_hosts"):
        remote_host_file= a
    else:
        assert False, "unhandled option" + o + " -- " + usage()

if (remote_host_file is None):
    print "please specify remote_hosts"
    usage()
    sys.exit(2)

instance_ids = [line.strip() for line in open(remote_host_file)]
print "instance_ids %(id)s" % { 'id': str(instance_ids)}

def verboseCall(command):
    print "executing %(cmd)s" % { 'cmd' : command}
    call(command, shell=True)
if (fetch):
    verboseCall("rm -rf gatling-plugin-maven-demo")

if (!os.exists("gatling-plugin-maven-demo")):
    verboseCall("git clone git://github.com/excilys/gatling-maven-plugin-demo.git")
    verboseCall("cp -f src/test/resources/gatling.conf gatling-plugin-maven-demo/src/test/resources/gatling.conf")

gatling_results_dir = "gatling-plugin-maven-demo/target/gatling_results/gatling_results_dir"

if (os.path.exists(gatling_results_dir)):
    new_dir = "%(dir)s%(t)d" % {'dir' : gatling_results_dir, 't': time.time()}
    print "renaming existing results to %(new_dir)s" % { 'new_dir' : new_dir }
    os.rename(gatling_results_dir, new_dir)

verboseCall("mkdir -p " + gatling_results_dir)
verboseCall("chmod 777 " + gatling_results_dir)




remoteCopyTarget = "WSPerfLab/gatling_setup/gatling_driver/gatling-maven-plugin-demo/latest_simulation.log"
for instance_id in instance_ids:
    verboseCall("%(scp)s %(instance_id)s:%(remoteCopyTarget)s %(gatling_results_dir)s/%(instance_id)s.log" % {'scp' : scp,
        'instance_id' : instance_id, 'remoteCopyTarget' : remoteCopyTarget,
        'gatling_results_dir' : gatling_results_dir})
### IMPORTANT NOTE ###
###In this script, we run in gatling in reportsOnly mode, so this won't actually execute the gatling simulation - just generates the reports
###On the remote host running gatling, we use the downloaded gatling maven as is
###On the local host, we run the edited version checked into git.
###The main difference is that on local host, gatling.conf has reportsOnly uncommented and set
verboseCall("python install_maven.py")
verboseCall("./mvn gatling:execute -Dgatling.simulationClass=driver.LoadDriver")
