(ns ws.client.ws-client
  (:gen-class)
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]))

(declare send-requests)

(defn run-load-test
  "Execute load test against given URL

  Examples:
    (run-load-test \"http://localhost:8888/ws-java-servlet-blocking/testA\") ; to use the number of CPUs for numThreads
    (run-load-test \"http://localhost:8888/ws-java-servlet-blocking/testA\" 2) ; to run with 2 threads
  "
  ([url] (run-load-test url (-> (Runtime/getRuntime) .availableProcessors)))
  ([url num-threads]
    (println "Starting load test")
    ; response-log-agent: used for sending response logs to and serializing them to output
    (let [response-log-agent (agent 0)]
	    (with-open [log-writer (writer "/tmp/test2.txt")]
	      (println "Log file opened")
	      (http/with-connection-pool {:timeout 500 :threads 20 :insecure? false :default-per-route 20}
	        (let [futures (doall (for [i (range num-threads)]
	                             (future
	                               (println "send-requests starting in thread: " (Thread/currentThread))
	                               (send-requests url 10 log-writer response-log-agent num-threads))))]
	          (println "starting")
	          (doseq [f futures]
	            ; wait for each future to complete
	            (deref f))
	          ; (println (deref f 900 #(future-cancel f))) ; this isn't working (returns function but doesn't execute it)
	          (println "Done load test.")))))))
	  

(defmacro duration-in-millis
  "Evaluates expr and records the time it took in millis.
   Passes the time as the first argument to 'on-complete' then returns the expression response."
  [expr on-complete]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     (~on-complete (/ (double (- (. System (nanoTime)) start#)) 1000000.0))
     ret#))

(defn- send-response-log
  "Function that will 'send-off' for asynchronously logging response metrics.
   This function itself should not nothing blocking."
  [time-in-millis response-log-agent log-writer num-threads]
  (send-off response-log-agent (fn [count]
              (.write log-writer (str "timestamp:" (System/currentTimeMillis) " threads: " num-threads " duration: " time-in-millis "\n"))
              (if (= 10 count) 
                (do 
                  (.flush log-writer); flush writer every 10 log entries
                  0) ; return 0 as the value to reset count
                (inc count) ; increment the agent value as a counter
              )
              )))

(defn- send-requests
  "Send an HTTP request 'count' times and log metrics after each response."
  [url count log-writer response-log-agent num-threads]
  (dotimes [n count] 
      (let
        ; we generate a random id to seed the request with a different arg each request
        [url (str url "?id=" (rand-int 999999))]
        ; perform http/get inside duration-in-millis macro which passes the
        ; duration of the http/get function to the following function 
        (duration-in-millis (http/get url) #(send-response-log % response-log-agent log-writer num-threads))
        )))


