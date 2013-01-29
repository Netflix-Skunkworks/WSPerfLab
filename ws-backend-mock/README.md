# Mock Backend

A Java webapp with a servlet for mocking JSON web service responses.

The purpose of this is to allow our various middle-tier implementations to hit a backend over the network and receive data back to simulate actual behavior.

What happens inside this service is not important as long as it returns JSON and takes time to simulate a service dependency doing something.

# Request Arguments

### id

Number used to generate a 'responseKey' for the ws-client to assert correct implementation and flow of requests.

### delay

The delay in milliseconds to add to the server side response to simulate server-side latency.

Default: 50ms

### numItems

The number of items to return in a JSON Array.

Default: 10

### itemSize

The length in characters of each item in the list.

Default: 128


Example Requests:

```
http://hostname/ws-backend-simulation/mock.json?id=123
http://hostname/ws-backend-simulation/mock.json?id=123&delay=500
http://hostname/ws-backend-simulation/mock.json?id=123&numItems=25&itemSize=256
http://hostname/ws-backend-simulation/mock.json?id=123&numItems=25&itemSize=256&delay=400
```

Example Response:

```json
{
  "responseKey":41262963,
  "delay": 50,
  "itemSize": 128,
  "numItems": 10,
  "items": [
    "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut ",
    "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut ",
    "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut ",
    "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut ",
    "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut ",
    "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut ",
    "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut ",
    "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut ",
    "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut ",
    "Lorem ipsum dolor sit amet, consectetur adipisicing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut "
  ]
}
```

# Run

To run from command line:

```
../gradlew jettyRun
```
