#! /bin/bash +x

SCRIPT_DIR=$(dirname $BASH_SOURCE)
LOG_DIR=$SCRIPT_DIR/../logs/
mkdir -p $LOG_DIR

NOW=$(date +"%F")
LOG_FILE_NAME="ws-java-client-${NOW}.log"
ERR_LOG_FILE_NAME="ws-java-client-err.log"
./java-client.sh -u $1 -c $2 -r $3 > $LOG_DIR/$LOG_FILE_NAME 2>$LOG_DIR/$ERR_LOG_FILE_NAME

