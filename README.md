# WSPerfLab
=========

Project for testing web-service implementations.

The intent of the test design is to simulate behavior typical of real-world web service applications.

This includes things such as:

- parallel network execution to backend services
- multiple (5 in this case) backend services
- conditional request flows (C and D require data from A, E requires data from B)
- JSON deserialization and serialization
- work such as math, iteration, string manipulation (not just proxying a stream of bytes)

# Structure

The test setup will consist of the 3 layers:

```ws-backend-mock <- ws-impls <- ws-client```

The [ws-backend-mock](ws-backend-mock) is a simple Java backend app accepting request arguments to affect its response size and latency.

The various [test implementations](ws-impls) are intended to each implement the same logic with different architectures, languages and frameworks.

The [ws-client](ws-client) will drive the traffic and capture performance metrics.

Metrics to be captured are:

- client-side end-to-end latency for entire trip including network
- response payload size

# Test Implementations

Information about test cases and implementation requirements can be found in the [ws-impls README](ws-impls)</a>.


# Statistics and Report

