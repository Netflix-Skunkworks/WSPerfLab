#This program runs remote commands.  The commands to run should come on standard input

import sys
import os.path
from subprocess import Popen, PIPE
import time
import getopt

cat_file = "/tmp/cmd.txt"

ssh_to_use = 'ssh'

def usage():
    print """
    -h, --help - print this message
    -s, --ssh - override ssh command to ssh_to_use
    -r, --remote_host_file - list of remote hosts to execute the command, one per line
    -c, --command_file - list of commands to execute, one per line
    """

try:
    opts, args = getopt.getopt(sys.argv[1:], "hs:r:c:", ["help", "ssh=", "remote_host_file=", "command_file="])
except Exception:
    # print help information and exit:
    print(sys.exc_info()[:1]) # will print something like "option -a not recognized"
    usage()
    sys.exit(2)

remote_host_file = None
command_file = None

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
    elif o in ("-c", "--command_file"):
        command_file = a
    else:
        assert False, "unhandled option" + o + " -- " + usage()

if (remote_host_file is None):
    print "specify remote_host_file"
    usage()
    sys.exit(2)
if (command_file is None):
    print "specify command_file"
    usage()
    sys.exit(2)

poll_time = 1 #seconds to wait before repolling

cmd = '; '.join([line.strip() for line in open(command_file)])
print "using: %(cmd)s" % { 'cmd' : cmd}
f=open(cat_file, "w")
f.write(cmd)
f.close()

instance_ids = [line.strip() for line in open(remote_host_file)]
print "instance_ids %(id)s" % { 'id': str(instance_ids)}


running_procs = dict()
for instance_id in instance_ids:
    running_procs[instance_id] = Popen("cat %(cat_file)s | %(ssh)s %(instance_id)s" %
        {'instance_id' : instance_id, 'cat_file': cat_file, 'ssh' : ssh },
        stdout=PIPE, stderr=PIPE, shell=True)

while running_procs:
    for instance_id in running_procs:
        proc = running_procs[instance_id]
        retcode = proc.poll()
        if retcode is not None: # Process finished.
            del running_procs[instance_id]
            break
    else: #no proceses were completed
        time.sleep(poll_time)
        continue

    if retcode != 0:
        print "proc failed, code =%(c)d, for instance=%(id)s" % {'c' : retcode, 'id' : instance_id}
    for line in proc.stderr:
        print "error %(id)s %(line)s" % {'id' : instance_id, 'line' : line}
    for line in proc.stdout:
        print "output %(id)s %(line)s" % {'id' : instance_id, 'line' : line}