#! /bin/bash +x

#############################################################
################# ws-java-rxnetty options #####################


: ${EVENT_LOOP_COUNT:=4}
: ${SERVER_PORT:=8888}
SERVER_LOG="false"
CLIENT_LOG="false"

: ${BACKEND_HOST:="127.0.0.1"}
: ${BACKEND_PORT:=8989}

echo "Using event loop count: ${EVENT_LOOP_COUNT} server port ${SERVER_PORT} backend host ${BACKEND_HOST} and port ${BACKEND_PORT}"

WS_JAVA_RXNETTY_OPTS="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005"
#############################################################

SCRIPT_DIR=$(dirname $BASH_SOURCE)
SCRIPTNAME=`basename $0`
ACTUAL_SCRIPT_NAME='ws-java-rxnetty'

PIDFILE=${SCRIPT_DIR}/${SCRIPTNAME}.pid

start() {
    echo "Starting rx-netty based ws server...."
    if [ -f ${PIDFILE} ]; then
       #verify if the process is actually still running under this pid
       OLDPID=`cat ${PIDFILE}`
       RESULT=`ps -ef | grep ${OLDPID} | grep ${ACTUAL_SCRIPT_NAME}`

       if [ -n "${RESULT}" ]; then
         echo "rx-netty based server is already running! Exiting"
         exit 255
       else
         echo "Stale pid file ${PIDFILE}, removing"
         rm -f $PIDFILE
         echo "Removed stale pid file ${PIDFILE}"
       fi
    fi

    PID=`ps -ef | grep ${SCRIPTNAME} | head -n1 |  awk ' {print $2;} '`
    echo ${PID} > ${PIDFILE}
    export WS_JAVA_RXNETTY_OPTS=${WS_JAVA_RXNETTY_OPTS}

    . $SCRIPT_DIR/ws-java-rxnetty $EVENT_LOOP_COUNT $SERVER_PORT $BACKEND_HOST $BACKEND_PORT
    echo "Started rx-netty based ws server...."
}

stop() {
    echo "Stopping rx-netty based ws server...."
    if [ -f ${PIDFILE} ]; then
        OLDPID=`cat ${PIDFILE}`
        echo "rx-netty based server PID: ${OLDPID}"
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
    echo "Stopped rx-netty based ws server...."
}

cleanup() {
    if [ "$CMD" != "stop" ];then
        echo "Cleaning up rx-netty based server after shutdown...."

        if [ -f ${PIDFILE} ]; then
            rm -f ${PIDFILE}
        fi
        echo "Cleaned up rx-netty based server after shutdown...."
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