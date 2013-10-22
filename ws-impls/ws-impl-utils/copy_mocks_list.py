# you should run this once, regardless of the impl installed, to copy the mocks we round robin against.
import sys
import getopt
from subprocess import call

scp = 'scp'

def usage():
    print """
    -h, --help - print this message
    -s, --scp - override scp command to ssh_to_use
    -i, --impl_host - the impl host we're copying to
    -m, ---mock_host_file - a file containing mock host names, one per line
    """

try:
    opts, args = getopt.getopt(sys.argv[1:], "hs:i:m:", ["--help", "--scp=", "--impl_host=", "--mock_host_file="])
except Exception:
    # print help information and exit:
    print(sys.exc_info()[:1]) # will print something like "option -a not recognized"
    usage()
    sys.exit(2)

mock_host_file=None
impl_host=None

for o, a in opts:
    if o in ("-h", "--help"):
        usage()
        sys.exit()
    elif o in ("-s", "--scp"):
        scp=a
    elif o in ("-i", "--impl_host"):
        impl_host = a
    elif o in ("-m", "--mock_host_file"):
        mock_host_file=a
    else:
        assert False, "unhandled option" + o + " -- " + usage()

if (impl_host is None):
    print "please specify implementation host"
    usage()
    sys.exit(2)

if (mock_host_file is None):
    print "please specify mock host names file (should contain mock host names, one per line)"
    usage()
    sys.exit(2)

command = "%(scp)s %(mock_host_file)s %(impl_host)s:/tmp/wsmock_servers.txt" % {'scp' : scp, 'mock_host_file' : mock_host_file, 'impl_host' : impl_host}
print "executing %(cmd)s" % { 'cmd' : command}
call(command, shell=True)

