# Different Implementations of Web Service Test Cases

This contains the various implementations of the web service test cases intended for load testing.

# Validation

To confirm that an implementation is returning valid responses, use the validate.py script.

Successful Example:

```
$ ./validate.py http://localhost:8888/ws-java-servlet-blocking
Validating WsPerfLab Implementation at URL: http://localhost:8888/ws-java-servlet-blocking/testA?id=24169

Successful Validation
```

Failed Examples:

```
$ ./validate.py http://localhost:8888/ws-java-servlet-blocking
Validating WsPerfLab Implementation at URL: http://localhost:8888/ws-java-servlet-blocking/testA?id=34565

Error => Validation Failed! => Missing 'items' key


$ ./validate.py http://localhost:8888/ws-java-servlet-blocking
Validating WsPerfLab Implementation at URL: http://localhost:8888/ws-java-servlet-blocking/testA?id=71576

Error => Validation Failed! => ResponseKey Invalid.
```

# Implementation Requirements

### Test Case A

An HTTP GET will request /testA?id={uuid} which is expected to perform the following:

- A) GET http://hostname:port/mock.json?numItems=2&itemSize=50&delay=50&id={uuid}
- B) GET http://hostname:port/mock.json?numItems=25&itemSize=30&delay=150&id={uuid}
- C) GET http://hostname:port/mock.json?numItems=1&itemSize=5000&delay=80&id={a.responseKey}
- D) GET http://hostname:port/mock.json?numItems=1&itemSize=1000&delay=1&id={a.responseKey}
- E) GET http://hostname:port/mock.json?numItems=100&itemSize=30&delay=4&id={b.responseKey}

The conditional flow of requests is demonstrated in this diagram:

<img src="https://raw.githubusercontent.com/Netflix-Skunkworks/WSPerfLab/master/artifacts/wsperflab-testa.png" width="860" height="260">

The JSON response will include a 'responseKey' value which is generated via:

```
c.responseKey + d.responseKey + e.responseKey
```

The expected response will look like this (without pretty print):

```json
{
  "responseKey":6502981934456555896, 
  "delay": [ { "a": 50 },{ "b": 90 },{ "c": 1 },{ "d": 1 },{ "e": 150 } ],
  "itemSize": [ { "a": 50 },{ "b": 30 },{ "c": 5000 },{ "d": 1000 },{ "e": 30 } ],
  "numItems": [ { "a": 2 },{ "b": 25 },{ "c": 1 },{ "d": 1 },{ "e": 100 } ],
  "items": [
    "Lorem ipsum dolor sit amet, consectetur adipisicin",
    "Lorem ipsum dolor sit amet, consectetur adipisicin",
    "Lorem ipsum dolor sit amet, co",
    "Lorem ipsum dolor sit amet, co",
    "... aggregate items from each of the 5 backend requests ...",
    "Lorem ipsum dolor sit amet, co"
  ]
}
```


### Test Case B

Less network, add ThreadLocal, ConcurrentHashMap, volatile, AtomicReference type behavior.

Not completed yet.

