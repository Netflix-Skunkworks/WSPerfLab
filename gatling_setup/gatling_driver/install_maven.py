#!/usr/bin/python

#downloads maven if necessary.

import os
import urllib
from subprocess import call
import sys

maven_name = "mvn"
apache_bin_maven_name = "apache-maven-3.1.1"
apache_bin_gz = apache_bin_maven_name + "-bin.tar.gz"
if (os.path.exists(maven_name) or os.path.lexists(maven_name)):
    print "maven already installed"
    sys.exit(0)
url_to_maven = "http://www.poolsaboveground.com/apache/maven/maven-3/3.1.1/binaries/" + apache_bin_gz
if (os.path.exists(apache_bin_gz) or os.path.exists(apache_bin_maven_name)):
    print "skipping download, already downloaded " + url_to_maven
else:
    print "starting download of maven, may take a moment, url = " + url_to_maven
    call(["wget " + url_to_maven], shell=True)
    call(["tar -xvf " + apache_bin_gz], shell=True)
    if (not os.path.exists(apache_bin_maven_name)):
        print "failed to download " + url_to_maven
        sys.exit(1)
    print "downloaded maven"
os.symlink(apache_bin_maven_name + "/bin/mvn", maven_name)
print "linked " + apache_bin_maven_name + " to " + maven_name


