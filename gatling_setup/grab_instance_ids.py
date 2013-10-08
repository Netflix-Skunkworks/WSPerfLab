import subprocess
import sys

if (len(sys.argv) != 2):
    print "usage grab_instance_ids <cluster_name>"
    sys.exit(1)
    
cmd = "oq-lin " + sys.argv[1]

process = subprocess.Popen(cmd, stdout=subprocess.PIPE, shell=True)
process.wait()
for line in process.stdout:
    elems = line.split()
    if ((len(elems) > 6) and (elems[1].startswith("i"))):
        print elems[1] + "\t" + elems[2]
