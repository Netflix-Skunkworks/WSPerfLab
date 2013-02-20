(ns ws.client.ws_client
  (:gen-class)
  (:require [clj-http.client :as http]
            [clojure.java.io :as io]))

(declare run-load-test)
(declare send-requests)
(declare get-formatted-date)

(defn -main
  [url numThreads requests-per-thread log-path]
  (run-load-test url (Integer/parseInt numThreads) (Integer/parseInt requests-per-thread) log-path)
  (System/exit 0))


(defn run-load-test
  "Execute load test against given URL. Blocks while performing test."
  ([url num-threads requests-per-thread log-path]
    (println "\tStarting load test => " url)
    (println "\t\t => log:" log-path)
    (println "\t\t => threads:" num-threads)
    (println "\t\t => requests-per-thread:" requests-per-thread)
    ; response-log-agent: used for sending response logs to and serializing them to output
    (let [response-log-agent (agent -1)]
	    (with-open [log-writer (io/writer log-path)]
        ; hand write some JSON 
        (.write log-writer (str "{"))
        (.write log-writer (str "\n\t\"request\" : \"" url "\","))
        (.write log-writer (str "\n\t\"start_time\" : \"" (get-formatted-date (new java.util.Date)) "\","))
        (.write log-writer (str "\n\t\"num_threads\" : " num-threads ","))
        (.write log-writer (str "\n\t\"requests-per-thread\" : " requests-per-thread ","))
        (.write log-writer (str "\n\t\"requests\" : [\n"))
        ; start the load test and output logs into the JSON
	      (http/with-connection-pool {:timeout 500 :threads num-threads :insecure? false :default-per-route num-threads}
	        (let [futures (doall (for [i (range num-threads)]
	                             (future
	                               ;(println "send-requests starting in thread: " (Thread/currentThread))
	                               (send-requests url requests-per-thread log-writer response-log-agent num-threads))))]
	          ;(println "starting")
	          (doseq [f futures]
	            ; wait for each future to complete
	            (deref f))
	          ; (println (deref f 900 #(future-cancel f))) ; trying to have a timeout that cancels but this isn't working (returns function but doesn't execute it)
           ; wait for all response-logs to be written
           (await response-log-agent)
           ; finish the JSON log file
           (.write log-writer (str "],"))
           (.write log-writer (str "\n\t\"end_time\" : \"" (get-formatted-date (new java.util.Date)) "\""))
           (.write log-writer (str "\n}\n"))
	          (println "\tDone load test.")))))))
	  
; run load test against default server with 2 threads
(comment (run-load-test "http://localhost:8888/ws-java-servlet-blocking/testA" 2 10 "/tmp/testA1.log"))
(comment (run-load-test "http://ec2-50-19-75-61.compute-1.amazonaws.com:8080/ws-java-servlet-blocking/testA" 4 20 "/tmp/testA2.log"))
(comment (run-load-test "http://ec2-50-19-75-61.compute-1.amazonaws.com:8080/ws-java-servlet-blocking/testA" 4 200 "/tmp/testA2.log"))

; format dates like "Fri, 15 Feb 2013 15:13:41"
(def date-format (new java.text.SimpleDateFormat "EEE, dd MMM yyyy HH:MM:ss"))

(defn get-formatted-date
  [date]
  (.format date-format date))

(defmacro duration-in-millis
  "Evaluates expr and records the time it took in millis.
   Returns a tuple of expression response and time in millis."
  [expr]
  `(let [start# (. System (nanoTime))
         ret# ~expr]
     [ret# (/ (double (- (. System (nanoTime)) start#)) 1000000.0)]))

(defn- send-response-log
  "Function that will 'send-off' for asynchronously logging response metrics.
   This function itself should not nothing blocking."
  [total-time server-time load-avg-per-core response-log-agent log-writer num-threads]
  (send-off response-log-agent (fn [count]
              (let [prefix 
                    (if (= -1 count)
                      ; first execution (only tabs)
                      (do (inc count) "\t\t")
                      ; all other executions (comma, newline and tabs)
                      ",\n\t\t")]
                (.write log-writer (str prefix 
                                        "{\"timestamp\" : " (System/currentTimeMillis) 
                                        ", \"total_time\" : " total-time 
                                        ", \"server_time\" : " server-time
                                        ", \"load-avg-per-core\" : " load-avg-per-core
                                        "}"))
                (print ".") ; show progress
                (flush) ; so we see the dot
                (if (= 10 count)
                  (do 
                    (.flush log-writer); flush writer every 10 log entries
                    0) ; return 0 as the value to reset count
                  (inc count) ; increment the agent value as a counter
                  ))
              )))

(defn- get-header
  [response header-name]
  (get (:headers response) header-name))

(defn- send-requests
  "Send an HTTP request 'count' times and log metrics after each response."
  [url count log-writer response-log-agent num-threads]
  (dotimes [n count] 
      (let
        ; we generate a random id to seed the request with a different arg each request
        [url (str url "?id=" (rand-int 999999))]
        ; perform http/get inside duration-in-millis macro that returns [response time-in-millis] 
        (let [r (duration-in-millis (http/get url))]
          ; send the time-in-millis and server_response_time
          (send-response-log (r 1) (get-header (r 0) "server_response_time") (get-header (r 0) "load_avg_per_core") response-log-agent log-writer num-threads))
        )))