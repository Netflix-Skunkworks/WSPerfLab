#This program runs remote commands, then dumps output on completion to one large file
#it expects one argument, the path to oq-instances, most likely created with the grab_instance_ids script
#the commands should be sent on standard input and then they will be concatenated and run
#to include the instance_id in a command, use standard Python interpolation %(instance_id)s

import sys
import os.path
from subprocess import Popen, PIPE
import time


#assumes server location created by callin grab_instance_ids script
if (len(sys.argv) < 1):
    print "usage run_gatling_remotely <server_file_path>, commands should come on standard input"

server_location = sys.argv[1]
if not server_location:
    server_location = "/tmp/servers.txt"
poll_time = 5 #seconds to wait before repolling

cmd = '; '.join([line.strip() for line in (sys.stdin)])
print "using %(cmd)s" % { 'cmd' : cmd}

instance_ids = [line.strip().split()[0] for line in open(server_location)]
print "instance_ids %(id)s" % { 'id': str(instance_ids)}


running_procs = [
    Popen(cmd % {'instance_id' : instance_id},
        stdout=PIPE, stderr=PIPE, shell=True)
    for instance_id in instance_ids]

while running_procs:
    for proc in running_procs:
        retcode = proc.poll()
        if retcode is not None: # Process finished.
            running_procs.remove(proc)
            break
    else: # No process is done, wait a bit and check again.
        time.sleep(poll_time)
        continue

    # Here, `proc` has finished with return code `retcode`
    if retcode != 0:
        print "proc failed, proc=%(p)s and code =%(c)d" % {'p' : str(proc), 'c' : retcode}
    for line in proc.stdout:
        print line