#!/bin/bash

sshCommand="ssh"
update=false
version='2.2.0'

if [ -z $GIT_COMMAND ]; then
    GIT_COMMAND='git clone -b gatling_setup git://github.com/katzseth22202/WSPerfLab.git'
fi

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
echo "play version: $version"
echo "SSH command: $sshCommand"

if $update ; then
	echo "--- Kill all java processes"
	eval "$sshCommand $hostname 'sudo killall java'"
	echo "--- Update from Git"
	eval "$sshCommand $hostname 'cd WSPerfLab/; git pull'"
	echo "--- Remove previously installed ws-java-servlet-blocking.war"
else
	echo "--- Shutdown Netflix Tomcat"
	eval "$sshCommand $hostname 'sudo /etc/init.d/nflx-tomcat stop'" # need to use a different AMI so this can be removed
	echo "--- Kill all java processes"
	eval "$sshCommand $hostname 'sudo killall java'"
	echo "--- Git clone WSPerfLab"
	eval "$sshCommand $hostname '$GIT_COMMAND'"
	echo "--- Download play"
	eval "$sshCommand $hostname 'wget http://downloads.typesafe.com/play/${version}/play-${version}.zip'"
	echo "--- Unzip play"
	eval "$sshCommand $hostname 'unzip play-${version}.zip'"
	echo "--- Link play"
	eval "$sshCommand $hostname 'ln -s 'ln -s play-${version}/play WSPerfLab/ws-impls/ws-play-scala/play"
fi

echo "--- Stage play"
eval "$sshCommand $hostname 'cd WSPerfLab/ws-impls/ws-play-scala; ./play clean compile stage"
echo "--- Run play"
eval "$sshCommand $hostname 'WSPerfLab/ws-impls/ws-play-scala/target/universal/stage/bin; bash ws-play-scala -Dhttp.port=8080&"