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

