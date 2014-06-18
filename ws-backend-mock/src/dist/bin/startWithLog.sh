#! /bin/bash +x

SCRIPT_DIR=$(dirname $BASH_SOURCE)
LOG_DIR=$SCRIPT_DIR/../logs/
mkdir -p $LOG_DIR

NOW=$(date +"%F")
LOG_FILE_NAME="ws-backend-mock-${NOW}.log"
./server.sh start > $LOG_DIR/$LOG_FILE_NAME 2>&1
