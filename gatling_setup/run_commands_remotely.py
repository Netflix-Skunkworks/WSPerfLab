#This program runs remote commands, then dumps output on completion to one large file
#it expects one argument, the path to oq-instances, most likely created with the grab_instance_ids script
#the commands should be sent on standard input and then they will be concatenated and run
#to include the instance_id in a command, use standard Python interpolation %(instance_id)s

import sys
import os.path
from subprocess import Popen, PIPE
import time

cat_file = "/tmp/cmd.txt"


#assumes server location created by callin grab_instance_ids script
if (len(sys.argv) < 1):
    print "usage run_gatling_remotely <server_file_path>, commands should come on standard input"

server_location = sys.argv[1]
poll_time = 1 #seconds to wait before repolling

cmd = '; '.join([line.strip() for line in (sys.stdin)])
print "using: %(cmd)s" % { 'cmd' : cmd}
f=open(cat_file, "w")
f.write(cmd)
f.close()

instance_ids = [line.strip().split()[0] for line in open(server_location)]
print "instance_ids %(id)s" % { 'id': str(instance_ids)}

running_procs = dict()
for instance_id in instance_ids:
    running_procs[instance_id] = Popen("cat %(cat_file)s | oq-ssh %(instance_id)s" %
        {'instance_id' : instance_id, 'cat_file': cat_file },
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