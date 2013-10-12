import getopt
import sys
from subprocess import call, PIPE

call("mkdir -p logs", shell=True)

def callRemotely(command_file):
    command = "python run_commands_remotely.py -s %(ssh)s -r %(remote_host_file)s -c %(command_file)s >> logs/%(command_file)s.log" \
        % { 'ssh' : ssh, 'remote_host_file' : remote_host_file, 'command_file' : command_file}
    print "executing " + command
    call(command, shell=True)


ssh = 'ssh'

def usage():
    print """
    -h, --help - print this message
    -s, --ssh - override ssh command to ssh_to_use
    -r, --remote_host_file - list of remote hosts to execute the command, one per line
    -u, --load_url- the target URL to test
    -f, --fetch_git_changes - clobber and redownload git changes
    """

try:
    opts, args = getopt.getopt(sys.argv[1:], "hfs:r:u:", ["help", "--fetch_git_changes", "ssh=", "remote_host_file=", "load_url="])
except Exception:
    # print help information and exit:
    print(sys.exc_info()[:1]) # will print something like "option -a not recognized"
    usage()
    sys.exit(2)

remote_host_file = None
load_url = None
fetch_git_changes = False

for o, a in opts:
    if o == "-":
        verbose = True
    elif o in ("-h", "--help"):
        usage()
        sys.exit()
    elif o in ("-s", "--ssh"):
        ssh=a
    elif o in ("-r", "--remote_host_file"):
        remote_host_file = a
    elif o in ("-u", "--load_url"):
        load_url = a
    elif o in ("-f", "--fetch_git_changes"):
        fetch_git_changes = True
    else:
        assert False, "unhandled option" + o + " -- " + usage()

if (remote_host_file is None):
    print "specify remote_host_file"
    usage()
    sys.exit(2)
if (load_url is None):
    print "specify required load_url"
    usage()
    sys.exit(2)
if (fetch_git_changes):
    callRemotely("clean_up.remote.sh")
callRemotely("clone_gatling.remote.sh")
run_cmd = "cd WSPerfLab/gatling_setup/gatling_driver; python run_gatling_local.py %(load_url)s" % {'load_url': load_url}
load_tmp_file = "load.remote.sh"
f=open(load_tmp_file, "w")
f.write(run_cmd)
f.close()
callRemotely(load_tmp_file)




