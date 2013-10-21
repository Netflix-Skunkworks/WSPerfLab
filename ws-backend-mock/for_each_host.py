import sys
from subprocess import call

def usage():
    print """
       expected usage is to take a file of instance-ids, one per line and pipe them to for_each_host.py <cmd>
       cmd is the command to run.   Note that before running <cmd> we first interpolate using python rules %%(instance_id)s
       cat instance_ids.txt | python for_each_hosts.py "ssh %%(instance_id)s ./install.sh
       would run ssh host1 ./install.sh
            ssh host2 ./install.sh
            etc  if instance_ids.txt had
                host1
                host2
    """

if (len(sys.argv) < 2):
    usage()
    sys.exit(2)

instance_ids = [line.strip() for line in sys.stdin]
for instance_id in instance_ids:
    command = sys.argv[1] % { 'instance_id' : instance_id}
    print command
    call(command, shell=True)
print "done"
