#!/bin/bash -x

sshCommand="ssh"
update=false
tomcatVersion="7.0.42"


if [ -z $GIT_COMMAND ]; then
    GIT_COMMAND='git clone -b git://github.com/katzseth22202/WSPerfLab.git'
fi

while getopts "h:s:t:u" opt; do
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
    t)
      tomcatVersion="$OPTARG"
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      ;;
  esac
done

if [ -z "$hostname" ]; then
	echo $'\a'-h required for hostname
	echo "$0 -h [HOSTNAME] -t [tomcat version(optional defaults to 7.0.40] -s [SSH COMMAND (optional)] -u (to update only)"
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
	eval "$sshCommand $hostname '$GIT_COMMAND'"
	echo "--- Download Tomcat 7"
	eval "$sshCommand $hostname 'wget http://mirrors.gigenet.com/apache/tomcat/tomcat-7/v${tomcatVersion}/bin/apache-tomcat-${tomcatVersion}.tar.gz'"
	echo "--- Extract Tomcat 7"
	eval "$sshCommand $hostname 'tar xzvf apache-tomcat-${tomcatVersion}.tar.gz'"
	echo "--- Delete demo apps from Tomcat 7"
	eval "$sshCommand $hostname '/bin/rm -R apache-tomcat-${tomcatVersion}/webapps/docs/ apache-tomcat-${tomcatVersion}/webapps/examples/ apache-tomcat-${tomcatVersion}/webapps/host-manager/ apache-tomcat-${tomcatVersion}/webapps/manager/'"
	echo "--- Add perf.test.backend.hostname property to catalina.sh"
	eval "$sshCommand $hostname 'cd apache-tomcat-${tomcatVersion}/bin; echo \"99a100,101\" >> catalina.patch'"
	eval "$sshCommand $hostname 'cd apache-tomcat-${tomcatVersion}/bin; echo \"> \" >> catalina.patch'"
	eval "$sshCommand $hostname 'cd apache-tomcat-${tomcatVersion}/bin; patch catalina.sh catalina.patch'"
fi

echo "--- Build WSPerfLab"
eval "$sshCommand $hostname 'cd WSPerfLab/; ./gradlew clean build'"
echo "--- Copy ws-java-servlet-blocking.war to Tomcat 7"
eval "$sshCommand $hostname 'cp WSPerfLab/ws-impls/ws-java-servlet-blocking/build/libs/ws-java-servlet-blocking-rxjava-*-SNAPSHOT.war apache-tomcat-${tomcatVersion}/webapps/ws-java-servlet-blocking-rxjava.war'"
echo "--- Start Tomcat 7"
eval "$sshCommand $hostname 'cd apache-tomcat-${tomcatVersion}/bin/; ./startup.sh'"