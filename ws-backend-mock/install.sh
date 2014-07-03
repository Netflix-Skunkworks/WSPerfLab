#!/bin/bash

sshCommand="ssh"
update=false
tomcatVersion="7.0.54"
gitRepo="benjchristensen"

while getopts "h:s:t:u" opt; do
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
      tomcatVersion=$OPTARG
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
echo "Using tomcat version: $tomcatVersion"
echo "SSH command: $sshCommand"

if $update ; then
	echo "--- Kill all java processes"
	eval "$sshCommand $hostname 'sudo killall java'"
	echo "--- Update from Git"
	eval "$sshCommand $hostname 'cd WSPerfLab/; git pull'"
	echo "--- Remove previously installed ws-backend-mock.war"
	eval "$sshCommand $hostname '/bin/rm -R apache-tomcat-${tomcatVersion}/webapps/ws*'"
else
	echo "--- Shutdown Netflix Tomcat"
	eval "$sshCommand $hostname 'sudo /etc/init.d/nflx-tomcat stop'" # need to use a different AMI so this can be removed
	echo "--- Kill all java processes"
	eval "$sshCommand $hostname 'sudo killall java'"
	echo "--- Git clone WSPerfLab"
	eval "$sshCommand $hostname 'git clone git://github.com/$gitRepo/WSPerfLab.git'"
	echo "--- Download Tomcat 7"
	eval "$sshCommand $hostname 'wget http://mirrors.gigenet.com/apache/tomcat/tomcat-7/v${tomcatVersion}/bin/apache-tomcat-${tomcatVersion}.tar.gz'"
	echo "--- Extract Tomcat 7"
	eval "$sshCommand $hostname 'tar xzvf apache-tomcat-${tomcatVersion}.tar.gz'"
	echo "--- Delete demo apps from Tomcat 7"
	eval "$sshCommand $hostname '/bin/rm -R apache-tomcat-${tomcatVersion}/webapps/docs/ apache-tomcat-${tomcatVersion}/webapps/examples/ apache-tomcat-${tomcatVersion}/webapps/host-manager/ apache-tomcat-${tomcatVersion}/webapps/manager/'"
fi

echo "--- Build WSPerfLab"
eval "$sshCommand $hostname 'cd WSPerfLab/; ./gradlew build'"
echo "--- Copy ws-backend-mock.war to Tomcat 7"
eval "$sshCommand $hostname 'cp WSPerfLab/ws-backend-mock/build/libs/ws-backend-mock-0.1-SNAPSHOT.war apache-tomcat-${tomcatVersion}/webapps/ws-backend-mock.war'"
eval "$sshCommand $hostname 'cp WSPerfLab/ws-backend-mock/server.xml apache-tomcat-${tomcatVersion}/conf/server.xml'"
eval "$sshCommand $hostname 'chmod 600 apache-tomcat-${tomcatVersion}/conf/server.xml'"
echo "--- Start Tomcat 7"
eval "$sshCommand $hostname 'cd apache-tomcat-${tomcatVersion}/bin/; ./startup.sh'"
