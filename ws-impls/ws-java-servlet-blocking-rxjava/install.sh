#!/bin/bash

sshCommand="ssh"
update=false

while getopts "h:s:b:u" opt; do
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
    \?)
      echo "Invalid option: -$OPTARG" >&2
      ;;
  esac
done

if [ -z "$hostname" ]; then
	echo $'\a'-h required for hostname
	echo "$0 -h [HOSTNAME] -b [BACKEND_HOSTNAME ie http://ec2-54-234-88-75.compute-1.amazonaws.com:8080] -s [SSH COMMAND (optional)] -u (to update only)"
	exit
fi

if [ -z "$backend" ]; then
	echo $'\a'-b required for backend hostname
	echo "$0 -h [HOSTNAME] -b [BACKEND_HOSTNAME ie http://ec2-54-234-88-75.compute-1.amazonaws.com:8080] -s [SSH COMMAND (optional)] -u (to update only)"
	exit
fi

echo "Install to host: $hostname"
echo "SSH command: $sshCommand"

if $update ; then
	echo "--- Kill all java processes"
	eval "$sshCommand $hostname 'sudo killall java'"
	echo "--- Update from Git"
	eval "$sshCommand $hostname 'cd WSPerfLab/; git pull'"
	echo "--- Remove previously installed ws-java-servlet-blocking.war"
	eval "$sshCommand $hostname '/bin/rm -R apache-tomcat-7.0.37/webapps/ws*'"
else
	echo "--- Shutdown Netflix Tomcat"
	eval "$sshCommand $hostname 'sudo /etc/init.d/nflx-tomcat stop'" # need to use a different AMI so this can be removed
	echo "--- Kill all java processes"
	eval "$sshCommand $hostname 'sudo killall java'"
	echo "--- Git clone WSPerfLab"
	eval "$sshCommand $hostname 'git clone git://github.com/benjchristensen/WSPerfLab.git'"
	echo "--- Download Tomcat 7"
	eval "$sshCommand $hostname 'wget http://mirrors.gigenet.com/apache/tomcat/tomcat-7/v7.0.37/bin/apache-tomcat-7.0.37.tar.gz'"
	echo "--- Extract Tomcat 7"
	eval "$sshCommand $hostname 'tar xzvf apache-tomcat-7.0.37.tar.gz'"
	echo "--- Delete demo apps from Tomcat 7"
	eval "$sshCommand $hostname '/bin/rm -R apache-tomcat-7.0.37/webapps/docs/ apache-tomcat-7.0.37/webapps/examples/ apache-tomcat-7.0.37/webapps/host-manager/ apache-tomcat-7.0.37/webapps/manager/'"
	echo "--- Add perf.test.backend.hostname property to catalina.sh"
	eval "$sshCommand $hostname 'cd apache-tomcat-7.0.37/bin; echo \"99a100,101\" >> catalina.patch'"
	eval "$sshCommand $hostname 'cd apache-tomcat-7.0.37/bin; echo \"> JAVA_OPTS=\\\"\\\$JAVA_OPTS -Dperf.test.backend.hostname=$backend/ws-backend-mock\\\"\" >> catalina.patch'"
	eval "$sshCommand $hostname 'cd apache-tomcat-7.0.37/bin; echo \"> \" >> catalina.patch'"
	eval "$sshCommand $hostname 'cd apache-tomcat-7.0.37/bin; patch catalina.sh catalina.patch'"
fi

echo "--- Build WSPerfLab"
eval "$sshCommand $hostname 'cd WSPerfLab/; ./gradlew clean build'"
echo "--- Copy ws-java-servlet-blocking.war to Tomcat 7"
eval "$sshCommand $hostname 'cp WSPerfLab/ws-impls/ws-java-servlet-blocking/build/libs/ws-java-servlet-blocking-rxjava-*-SNAPSHOT.war apache-tomcat-7.0.37/webapps/ws-java-servlet-blocking-rxjava.war'"
echo "--- Start Tomcat 7"
eval "$sshCommand $hostname 'cd apache-tomcat-7.0.37/bin/; ./startup.sh'"