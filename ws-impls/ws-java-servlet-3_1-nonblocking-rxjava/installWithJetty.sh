#!/bin/bash

sshCommand="ssh"
update=false
jettyVersion="9.1.0.RC0"
gitRepo="benjchristensen"

while getopts "h:s:t:b:u" opt; do
  case $opt in
    h)
	  hostname=$OPTARG
      ;;
	r)
      gitRepo=$OPTARG
      ;;
    s)
      sshCommand=$OPTARG
      ;;
    u)
      update=true
      ;;
    t)
      jettyVersion=$OPTARG
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      ;;
  esac
done

if [ -z "$hostname" ]; then
	echo $'\a'-h required for hostname
	echo "$0 -h [HOSTNAME] -s [SSH COMMAND (optional)]  -r [Github repo username (optional: defaults to 'benjchristensen')] -u (to update only)"
	exit
fi

echo "Install to host: $hostname"
echo "Using tomcat version: $jettyVersion"
echo "SSH command: $sshCommand"

if $update ; then
	echo "--- Kill all java processes"
	eval "$sshCommand $hostname 'sudo killall java'"
	echo "--- Update from Git"
	eval "$sshCommand $hostname 'cd WSPerfLab/; git pull'"
	echo "--- Remove previously installed ws-java-servlet-3_1-nonblocking-rxjava.war"
	eval "$sshCommand $hostname '/bin/rm -R jetty-distribution-${jettyVersion}/webapps/ws*'"
else
	echo "--- Kill all java processes"
	eval "$sshCommand $hostname 'sudo killall java'"
	echo "--- Git clone WSPerfLab"
	eval "$sshCommand $hostname 'git clone git://github.com/benjchristensen/WSPerfLab.git'"
	echo "--- Download Jetty ${jettyVersion}"
	eval "$sshCommand $hostname 'wget http://mirrors.ibiblio.org/pub/mirrors/eclipse/jetty/${jettyVersion}/dist/jetty-distribution-${jettyVersion}.tar.gz'"
	echo "--- Extract Jetty ${jettyVersion}"
	eval "$sshCommand $hostname 'tar xzvf jetty-distribution-${jettyVersion}.tar.gz'"
	echo "--- Add perf.test.backend.hostname property to jetty.sh"
	eval "$sshCommand $hostname 'cd jetty-distribution-${jettyVersion}/bin; echo \"43a44,46\" >> jetty_sh.patch'"
	eval "$sshCommand $hostname 'cd jetty-distribution-${jettyVersion}/bin; echo \"> \" >> jetty_sh.patch'"
	eval "$sshCommand $hostname 'cd jetty-distribution-${jettyVersion}/bin; echo \"> JAVA_OPTIONS=\\\"-Xms1024m -Xmx1024m -server -XX:+UseConcMarkSweepGC\\\"\" >> jetty_sh.patch'"
	eval "$sshCommand $hostname 'cd jetty-distribution-${jettyVersion}/bin; echo \"> \" >> jetty_sh.patch'"
	eval "$sshCommand $hostname 'cd jetty-distribution-${jettyVersion}/bin; patch jetty.sh jetty_sh.patch'"
fi

echo "--- Build WSPerfLab"
eval "$sshCommand $hostname 'cd WSPerfLab/; ./gradlew clean build'"
echo "--- Copy ws-java-servlet-3_1-nonblocking-rxjava.war to Jetty ${jettyVersion}"
eval "$sshCommand $hostname 'cp WSPerfLab/ws-impls/ws-java-servlet-3_1-nonblocking-rxjava/build/libs/ws-java-servlet-3_1-nonblocking-rxjava-*-SNAPSHOT.war jetty-distribution-${jettyVersion}/webapps/ws-java-servlet-3_1-nonblocking-rxjava.war'"
echo "--- Start Jetty ${jettyVersion}"
eval "$sshCommand $hostname 'cd jetty-distribution-${jettyVersion}/bin/; ./jetty.sh start'"