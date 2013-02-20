#!/bin/bash

# default to 100 per thread
numRequestsPerThread=100

while getopts "u:o:n:" opt; do
  case $opt in
    u)
	  url=$OPTARG
      ;;
    o)
      output=$OPTARG
      ;;
    n)
      numRequestsPerThread=$OPTARG
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      ;;
  esac
done

if [ -z "$url" ] || [ -z "$output" ]; then
	echo $'\a'Missing required option.
	echo "$0 -u [URL] -o [OUTPUT FOLDER] -n [NUM REQUESTS PER THREAD (Optional: Default=100)]"
	exit
fi

echo "URL: $url"
echo "Num Requests per Thread: $numRequestsPerThread"
echo "Output Folder: $output"

echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 1 thread"
java -Xmx256m -jar build/libs/ws-client-0.1-SNAPSHOT.jar $url 1 $numRequestsPerThread $output/wsclient_1_thread.json
echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 8 threads"
java -Xmx256m -jar build/libs/ws-client-0.1-SNAPSHOT.jar $url 8 $numRequestsPerThread $output/wsclient_8_thread.json
echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 16 threads"
java -Xmx256m -jar build/libs/ws-client-0.1-SNAPSHOT.jar $url 16 $numRequestsPerThread $output/wsclient_16_thread.json
echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 32 threads"
java -Xmx256m -jar build/libs/ws-client-0.1-SNAPSHOT.jar $url 32 $numRequestsPerThread $output/wsclient_32_thread.json
echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 64 threads"
java -Xmx256m -jar build/libs/ws-client-0.1-SNAPSHOT.jar $url 64 $numRequestsPerThread $output/wsclient_64_thread.json
echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 96 threads"
java -Xmx256m -jar build/libs/ws-client-0.1-SNAPSHOT.jar $url 96 $numRequestsPerThread $output/wsclient_96_thread.json
echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 128 threads"
java -Xmx256m -jar build/libs/ws-client-0.1-SNAPSHOT.jar $url 128 $numRequestsPerThread $output/wsclient_128_thread.json
echo ">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 160 threads"
java -Xmx256m -jar build/libs/ws-client-0.1-SNAPSHOT.jar $url 160 $numRequestsPerThread $output/wsclient_160_thread.json