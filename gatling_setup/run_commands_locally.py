#generates a shell script from input, and then outputs to /tmp/local_shell
#then, executes the script
#takes as input the servers file, substitues instance_id  in the command pattern
import sys

local_shell = "/tmp/local_shell.sh"
server_location = sys.argv[1]

cmd = '; '.join([line.strip() for line in (sys.stdin)])
print "using: %(cmd)s" % { 'cmd' : cmd}

instance_ids = [line.strip().split()[0] for line in open(server_location)]
command_list = list()

for instance_id in instance_ids:
    command_list.append(cmd % {'instance_id' : instance_id})
shell_file = open(local_shell, "w")
shell_file.writelines(["%s\n" % item  for item in command_list])
shell_file.close()

