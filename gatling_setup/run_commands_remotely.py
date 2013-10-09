import sys
from subprocess import Popen, PIPE
import time

#assumes server location created by callin grab_instance_ids script
server_location = "/tmp/gatling_servers.txt"
poll_time = 15 #seconds to wait before repolling

cmd_arg = sys.argv[1]
print "using %(cmd)s" % { 'cmd' : cmd_arg}

instance_ids = [line.strip().split()[0] for line in open(server_location)]
print "instance_ids %(id)s" % { 'id': str(instance_ids)}


running_procs = [
    Popen(cmd_arg % {'instance_id' : instance_id},
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