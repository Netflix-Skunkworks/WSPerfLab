#!/usr/bin/env python

import argparse
import json
import urllib2
import time
import random


def validateWsPerfLabImplementation(baseUrl):
    try:
        seed = random.randint(10000, 99999)
        url = baseUrl + "/testA?id=" + str(seed)
        print "Validating WsPerfLab Implementation at URL: " + url
        print ""
        data = executeRequest(createRequest(url, 'GET'))
        
        # print "Data: " + data
        
        # assert response header with int: server_response_time
        meta = data[1]
        
        if(meta.getheader('server_response_time') is None):
            raise Exception("Validation Failed! => missing 'server_response_time' response header.")
        
        if(meta.getheader('load_avg_per_core') is None):
            raise Exception("Validation Failed! => missing 'load_avg_per_core' response header.")
        
        if(meta.getheader('os_arch') is None):
            raise Exception("Validation Failed! => missing 'os_arch' response header.")
        
        if(meta.getheader('os_name') is None):
            raise Exception("Validation Failed! => missing 'os_name' response header.")
        
        if(meta.getheader('os_version') is None):
            raise Exception("Validation Failed! => missing 'os_version' response header.")
        
        # print data[0]
        jsonData = json.loads(data[0])
        #print "ResponseKey: " + str(jsonData['responseKey'])
        #print "Expected: " + str(expectedResponseKeyForTestCaseA(seed))
        
        if jsonData['responseKey'] != expectedResponseKeyForTestCaseA(seed):
            raise Exception("Validation Failed! => ResponseKey Invalid.")
        
        # now check for JSON keys
        validateKey(jsonData, 'delay', 5)
        validateKey(jsonData, 'itemSize', 5)
        validateKey(jsonData, 'numItems', 5)
        validateKey(jsonData, 'items', 129)
        
        # if we didn't fail validation report success
        print "Successful Validation"    
        
        print ""
    except Exception as e:
        print "Error => " + str(e)
        print ""


def validateKey(jsonData, key, size):
    if key not in jsonData:
        raise Exception("Validation Failed! => Missing '" + key + "' key")
    if len(jsonData[key]) != size:
        raise Exception("Validation Failed! => '" + key + "' list size != " + str(size))

# Get the expected responseKey for TestCaseA
# for the given seed number
def expectedResponseKeyForTestCaseA(seed):
    requestResponseKey = ((seed / 37) + 5739375) * 7
    x = ((requestResponseKey / 37) + 5739375) * 7
    return x * 3

# Create the HTTP Request object for the given url and args.
# If the Content-Type needs to be different, override it on the request object returned from this method.
# For example:
#    request.add_header("Content-Type", "text/csv")
def createRequest(url, method):
    request = urllib2.Request(url)
    # headers and cookies
    #request.add_header("Accept", "application/json")
    #request.add_header("Content-Type", "application/json")
        
    if method != None:
        # urllib2 somehow doesn't natively support setting methods so we override it
        request.get_method = lambda: method
    else:
        method = 'GET' # so we can use it in the curl command below
    
    return request

# Execute the request, perform error handling if needed
# return tuple [data, info] 
# The info object can be seen here: http://www.voidspace.org.uk/python/articles/urllib2.shtml#info-and-geturl
# For example: info.getheader('headerName')
def executeRequest(request):
    # execute request
    
    #print "Executing " + request.get_method() + " => " + request.get_full_url() + " ..."
    #print ""
    
    try:
        response = urllib2.urlopen(request)
        return response.read(), response.info(), response.code
    except Exception as e:
        if hasattr(e, 'code'):
            print "Error => HTTP Status Code: " + str(e.code)
        else:
            print "Error => Unknown Exception Occurred: " + str(e)
        
        if hasattr(e, 'read'):
            errorOutput = e.read()
        
            try:
                print "Error => Response Body:"
                print ""
                print errorOutput
                print ""
            except Exception as e2:
                print "Error => No JSON Response Body"


# main execution flow
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Validate a WSPerfLab implementation.')
    parser.add_argument('url', help='base url such as: http://localhost:8888/ws-java-servlet-blocking')
    args = parser.parse_args()
    
    validateWsPerfLabImplementation(args.url)
    


