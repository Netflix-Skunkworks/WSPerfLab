#!/bin/bash

sshCommand="ssh"
update=false
tomcatVersion="7.0.42"
gitRepo="benjchristensen"

while getopts "h:s:t:u:r:c" opt; do
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
    c)
      connector=$OPTARG
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      ;;
  esac
done

if [ -z "$connector" ]; then
	echo $'\a'-c required for connector 
	echo 	Options include: JavaBIO JavaNIO NativeAPR
fi

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
	echo "--- Remove previously installed ws-java-servlet-blocking.war"
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
	eval "$sshCommand $hostname 'cp apache-tomcat-${tomcatVersion}/conf/server.xml apache-tomcat-${tomcatVersion}/conf/server.orig'"
	eval "$sshCommand $hostname 'cp WSPerfLab/ws-impls/ws-java-servlet-blocking/server_$connector-Connector.xml apache-tomcat-${tomcatVersion}/conf/server.xml'"
fi

echo "--- Build WSPerfLab"
eval "$sshCommand $hostname 'cd WSPerfLab/; ./gradlew clean build'"
echo "--- Copy ws-java-servlet-blocking.war to Tomcat 7"
eval "$sshCommand $hostname 'cp WSPerfLab/ws-impls/ws-java-servlet-blocking/build/libs/ws-java-servlet-blocking-*-SNAPSHOT.war apache-tomcat-${tomcatVersion}/webapps/ws-java-servlet-blocking.war'"
echo "--- Start Tomcat 7"
eval "$sshCommand $hostname 'cd apache-tomcat-${tomcatVersion}/bin/; ./startup.sh'"