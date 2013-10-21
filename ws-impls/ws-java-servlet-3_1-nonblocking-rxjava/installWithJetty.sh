#!/bin/bash

sshCommand="ssh"
update=false
jettyVersion="9.1.0.RC0"

while getopts "h:s:t:b:u" opt; do
  case $opt in
    h)
	  hostname=$OPTARG
      ;;
    b)
 	  backend=$OPTARG
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
	echo "$0 -h [HOSTNAME] -b [BACKEND_HOSTNAME ie http://ec2-54-234-88-75.compute-1.amazonaws.com:8080] -t [tomcat version(optional defaults to 7.0.40] -s [SSH COMMAND (optional)] -u (to update only)"
	exit
fi

if [ -z "$backend" ]; then
	echo $'\a'-b required for backend hostname
	echo "$0 -h [HOSTNAME] -b [BACKEND_HOSTNAME ie http://ec2-54-234-88-75.compute-1.amazonaws.com:8080] -t [tomcat version(optional defaults to 7.0.40] -s [SSH COMMAND (optional)] -u (to update only)"
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
	echo "--- Add perf.test.backend.hostname property to catalina.sh"
	eval "$sshCommand $hostname 'cd jetty-distribution-${jettyVersion}/bin; echo \"99a100,101\" >> catalina.patch'"
	eval "$sshCommand $hostname 'cd jetty-distribution-${jettyVersion}/bin; echo \"> JAVA_OPTS=\\\"\\\$JAVA_OPTS -Dperf.test.backend.hostname=$backend/ws-backend-mock\\\"\" >> catalina.patch'"
	eval "$sshCommand $hostname 'cd jetty-distribution-${jettyVersion}/bin; echo \"> \" >> catalina.patch'"
	eval "$sshCommand $hostname 'cd jetty-distribution-${jettyVersion}/bin; patch catalina.sh catalina.patch'"
fi

echo "--- Build WSPerfLab"
eval "$sshCommand $hostname 'cd WSPerfLab/; ./gradlew clean build'"
echo "--- Copy ws-java-servlet-3_1-nonblocking-rxjava.war to Jetty ${jettyVersion}"
eval "$sshCommand $hostname 'cp WSPerfLab/ws-impls/ws-java-servlet-3_1-nonblocking-rxjava/build/libs/ws-java-servlet-3_1-nonblocking-rxjava-*-SNAPSHOT.war jetty-distribution-${jettyVersion}/webapps/ws-java-servlet-3_1-nonblocking-rxjava.war'"
echo "--- Start Jetty ${jettyVersion}"
eval "$sshCommand $hostname 'cd jetty-distribution-${jettyVersion}/bin/; ./jetty.sh start'"