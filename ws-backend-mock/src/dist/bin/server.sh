#! /bin/bash +x

#############################################################
################# ws-backend-mock options #####################


: ${SERVER_PORT:=8888}

echo "Using server port ${SERVER_PORT}"

BACKEND_MOCK_OPTS=""
#############################################################

SCRIPT_DIR=$(dirname $BASH_SOURCE)
SCRIPTNAME=`basename $0`
ACTUAL_SCRIPT_NAME='ws-backend-mock'

PIDFILE=${SCRIPT_DIR}/${SCRIPTNAME}.pid

start() {
    echo "Starting mock backend server...."
    if [ -f ${PIDFILE} ]; then
       #verify if the process is actually still running under this pid
       OLDPID=`cat ${PIDFILE}`
       RESULT=`ps -ef | grep ${OLDPID} | grep ${ACTUAL_SCRIPT_NAME}`

       if [ -n "${RESULT}" ]; then
         echo "Mock backendserver is already running! Exiting"
         exit 255
       else
         echo "Stale pid file ${PIDFILE}, removing"
         rm -f $PIDFILE
         echo "Removed stale pid file ${PIDFILE}"
       fi
    fi

    PID=`ps -ef | grep ${SCRIPTNAME} | head -n1 |  awk ' {print $2;} '`
    echo ${PID} > ${PIDFILE}
    export BACKEND_MOCK_OPTS=${BACKEND_MOCK_OPTS}

    . $SCRIPT_DIR/ws-backend-mock $SERVER_PORT
    echo "Started mock backend server...."
}

stop() {
    echo "Stopping mock backend server...."
    if [ -f ${PIDFILE} ]; then
        OLDPID=`cat ${PIDFILE}`
        echo "Mock backendserver PID: ${OLDPID}"
        RESULT=`ps -ef | grep ${OLDPID} | grep ${ACTUAL_SCRIPT_NAME}`
        if [ -n "${RESULT}" ]; then
            echo "Sending terminate signal to the server pid: ${OLDPID}"
            kill -2 $OLDPID
            sleep 2
            RESULT=`ps -ef | grep ${OLDPID} | grep ${ACTUAL_SCRIPT_NAME}`
            if [ -n "${RESULT}" ]; then
                echo "Server did not respond to interrupt, sending kill -9"
                kill -9 $OLDPID
                cleanup
            fi
        fi
    fi
    echo "Stopped mock backend server...."
}

cleanup() {
    if [ "$CMD" != "stop" ];then
        echo "Cleaning up mock backend server after shutdown...."

        if [ -f ${PIDFILE} ]; then
            rm -f ${PIDFILE}
        fi
        echo "Cleaned up mock backend server after shutdown...."
    fi
}

trap cleanup SIGHUP SIGINT SIGTERM

CMD=$1
case "$CMD" in
        start)
            start
            ;;

        stop)
            stop
            ;;

        *)
            echo $"Usage: $0 {start|stop}"
            exit 1

esac