#!/usr/bin/python2.7

import argparse
import os
import string
import sys
from subprocess import call


# java -jar build/libs/ws-client-0.1-SNAPSHOT.jar "http://ec2-50-19-75-61.compute-1.amazonaws.com:8080/ws-java-servlet-blocking/testA" 8 500 output.json


def run_test(url, output_folder, num_requests_per_thread):
    print("URL: " + url)
    print("Num Requests per Thread: " + str(num_requests_per_thread))
    print(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 1 thread")
    call(["java", "-Xmx256m", "-jar", "build/libs/ws-client-0.1-SNAPSHOT.jar", url, str(1), str(num_requests_per_thread), output_folder + "/wsclient_1_thread.json"])
    print(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 8 threads")
    call(["java", "-Xmx256m", "-jar", "build/libs/ws-client-0.1-SNAPSHOT.jar", url, str(8), str(num_requests_per_thread), output_folder + "/wsclient_8_thread.json"])
    print(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 16 threads")
    call(["java", "-Xmx256m", "-jar", "build/libs/ws-client-0.1-SNAPSHOT.jar", url, str(16), str(num_requests_per_thread), output_folder + "/wsclient_16_thread.json"])
    print(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 32 threads")
    call(["java", "-Xmx256m", "-jar", "build/libs/ws-client-0.1-SNAPSHOT.jar", url, str(32), str(num_requests_per_thread), output_folder + "/wsclient_32_thread.json"])
    print(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 64 threads")
    call(["java", "-Xmx256m", "-jar", "build/libs/ws-client-0.1-SNAPSHOT.jar", url, str(64), str(num_requests_per_thread), output_folder + "/wsclient_64_thread.json"])
    print(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 96 threads")
    call(["java", "-Xmx256m", "-jar", "build/libs/ws-client-0.1-SNAPSHOT.jar", url, str(96), str(num_requests_per_thread), output_folder + "/wsclient_96_thread.json"])
    print(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 128 threads")
    call(["java", "-Xmx256m", "-jar", "build/libs/ws-client-0.1-SNAPSHOT.jar", url, str(128), str(num_requests_per_thread), output_folder + "/wsclient_128_thread.json"])
    print(">>>>>>>>>>>>>>>>>>>>>>>>>>>>> Test: 160 threads")
    call(["java", "-Xmx256m", "-jar", "build/libs/ws-client-0.1-SNAPSHOT.jar", url, str(160), str(num_requests_per_thread), output_folder + "/wsclient_160_thread.json"])


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=("Run WSPerfLab client."))
    parser.add_argument('-o', '--output_folder', metavar='[output file]', help="Folder to put response logs in for each portion of this test.", default='./wsClientOutput')
    parser.add_argument('-url', metavar='[url of server to test]', required=True, help="URL such as: http://ec2-50-19-75-61.compute-1.amazonaws.com:8080/ws-java-servlet-blocking/testA")
    parser.add_argument('-r', '--num_requests_per_thread', metavar='[requests per thread]', type=int, help="Number of requests per thread before finishing.", default='100')
                        
    args = vars(parser.parse_args())
    run_test(str(args['url']), str(args['output_folder']), args['num_requests_per_thread'])
