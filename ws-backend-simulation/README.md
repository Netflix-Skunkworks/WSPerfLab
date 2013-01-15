# Mock Backend

A Java webapp with a servlet for mocking JSON web service responses.

The purpose of this is to allow our various middle-tier implementations to hit a backend over the network and receive data back to simulate actual behavior.

What happens inside this service is not important as long as it returns JSON and takes time to simulate a service dependency doing something.

# Request Arguments

### delay

The delay in milliseconds to add to the server side response to simulate server-side latency.

Default: 50ms

### numItems

The number of items to return in a JSON Array.

Default: 10

### itemSize

The length in characters of each item in the list.

Default: 128


Examples:

```
http://hostname/ws-backend-simulation/mock.json
http://hostname/ws-backend-simulation/mock.json?delay=500
http://hostname/ws-backend-simulation/mock.json?numItems=25&itemSize=256
http://hostname/ws-backend-simulation/mock.json?numItems=25&itemSize=256&delay=400
```
