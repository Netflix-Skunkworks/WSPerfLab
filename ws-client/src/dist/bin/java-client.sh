#! /bin/bash +x

#############################################################
################# ws-client options #####################
REQUEST_TIMEOUT_MS=60000
MAX_BACKEND_CONNECTIONS_PER_HOST=2000
COLLECT_INDIVIDUAL_RESULTS="true"

WS_CLIENT_OPTS=" -Dasync.client.request.timeout.ms=${REQUEST_TIMEOUT_MS} -Dasync.client.max.conn.per.host=${MAX_BACKEND_CONNECTIONS_PER_HOST} -Dasync.client.collect.individual.results=${COLLECT_INDIVIDUAL_RESULTS} "
export WS_CLIENT_OPTS=${WS_CLIENT_OPTS}
#############################################################

SCRIPT_DIR=$(dirname $BASH_SOURCE)
SCRIPTNAME=`basename $0`
ACTUAL_SCRIPT_NAME='ws-client'

while getopts "u:c:r:" opt; do
  case $opt in
    u)
	  uri=$OPTARG
      ;;
    c)
      concurrent_clients=$OPTARG
      ;;
    r)
      requests=$OPTARG
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      ;;
  esac
done

if [ -z "$uri" ]; then
	echo $'\a'-u required for test case uri
	echo "$0 -u [test uri] -c [Number of concurrent clients] -r (Total expected requests)"
	exit
fi

if [ -z "$concurrent_clients" ]; then
	echo $'\a'-c required for concurrent clients
	echo "$0 -u [test uri] -c [Number of concurrent clients] -r (Total expected requests)"
	exit
fi

if [ -z "$requests" ]; then
	echo $'\a'-r required for number of requests
	echo "$0 -u [test uri] -c [Number of concurrent clients] -r (Total expected requests)"
	exit
fi

. $SCRIPT_DIR/$ACTUAL_SCRIPT_NAME $uri $concurrent_clients $requests

LOG_DIR=$SCRIPT_DIR/../logs/
ERR_LOG_FILE_NAME="ws-java-client-err.log"

if [ -s $LOG_DIR/$LOG_FILE_NAME ]; then
    echo "There are some errors in the error log file $ERR_LOG_FILE_NAME"
fi
