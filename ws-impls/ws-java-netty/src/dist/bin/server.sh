#! /bin/bash +x

#############################################################
################# ws-java-netty options #####################


: ${SERVER_PORT:=8798}
SERVER_LOG="false"
CLIENT_LOG="false"

: ${BACKEND_HOST:="localhost"}
: ${BACKEND_PORT:=8080}

echo "Using backend host ${BACKEND_HOST} and port ${BACKEND_PORT}"
BACKEND_MAX_CONN_PER_TEST=2000

LOG_LEVEL="INFO"

WS_JAVA_NETTY_OPTS="-Dhttp.server.port=${SERVER_PORT} -Dserver.log.enable=${SERVER_LOG} -Dclient.log.enable=${CLIENT_LOG} -Dperf.test.backend.host=${BACKEND_HOST} -Dperf.test.backend.port=${BACKEND_PORT} -Dperf.test.backend.host.maxconn.per.test=${BACKEND_MAX_CONN_PER_TEST} -D-Dorg.slf4j.simpleLogger.defaultLogLevel=${LOG_LEVEL}"
#############################################################

SCRIPT_DIR=$(dirname $BASH_SOURCE)
SCRIPTNAME=`basename $0`
ACTUAL_SCRIPT_NAME='ws-java-netty'

PIDFILE=${SCRIPT_DIR}/${SCRIPTNAME}.pid

start() {
    echo "Starting netty based ws server...."
    if [ -f ${PIDFILE} ]; then
       #verify if the process is actually still running under this pid
       OLDPID=`cat ${PIDFILE}`
       RESULT=`ps -ef | grep ${OLDPID} | grep ${ACTUAL_SCRIPT_NAME}`

       if [ -n "${RESULT}" ]; then
         echo "Netty based server is already running! Exiting"
         exit 255
       else
         echo "Stale pid file ${PIDFILE}, removing"
         rm -f $PIDFILE
         echo "Removed stale pid file ${PIDFILE}"
       fi
    fi

    PID=`ps -ef | grep ${SCRIPTNAME} | head -n1 |  awk ' {print $2;} '`
    echo ${PID} > ${PIDFILE}
    export WS_JAVA_NETTY_OPTS=${WS_JAVA_NETTY_OPTS}

    . $SCRIPT_DIR/ws-java-netty
    echo "Started netty based ws server...."
}

stop() {
    echo "Stopping netty based ws server...."
    if [ -f ${PIDFILE} ]; then
        OLDPID=`cat ${PIDFILE}`
        echo "Netty based server PID: ${OLDPID}"
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
    echo "Stopped netty based ws server...."
}

cleanup() {
    if [ "$CMD" != "stop" ];then
        echo "Cleaning up netty based server after shutdown...."

        if [ -f ${PIDFILE} ]; then
            rm -f ${PIDFILE}
        fi
        echo "Cleaned up netty based server after shutdown...."
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