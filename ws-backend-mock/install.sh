#!/bin/bash

sshCommand="ssh"
update=false

while getopts "h:s:u" opt; do
  case $opt in
    h)
	  hostname=$OPTARG
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
	echo "$0 -h [HOSTNAME] -s [SSH COMMAND (optional)] -u (to update only)"
	exit
fi

echo "Install to host: $hostname"
echo "SSH command: $sshCommand"

if $update ; then
	echo "--- Kill all java processes"
	eval "$sshCommand $hostname 'sudo killall java'"
	echo "--- Update from Git"
	eval "$sshCommand $hostname 'cd WSPerfLab/; git pull'"
	echo "--- Remove previously installed ws-backend-mock.war"
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
fi

echo "--- Build WSPerfLab"
eval "$sshCommand $hostname 'cd WSPerfLab/; ./gradlew clean build'"
echo "--- Copy ws-backend-mock.war to Tomcat 7"
eval "$sshCommand $hostname 'cp WSPerfLab/ws-backend-mock/build/libs/ws-backend-mock-0.1-SNAPSHOT.war apache-tomcat-7.0.37/webapps/ws-backend-mock.war'"
echo "--- Start Tomcat 7"
eval "$sshCommand $hostname 'cd apache-tomcat-7.0.37/bin/; ./startup.sh'"