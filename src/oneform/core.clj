(ns oneform.core
  (:require [oneform.handler :refer [app init destroy parse-port]]
            [immutant.web :as immutant]
            [oneform.db.migrations :as migrations]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]])
  (:gen-class))

(defn http-port [port]
  (parse-port (or port (env :port) 3000)))
(defn http-host [host]
  (or host (env :host) "0.0.0.0"))
(defonce server (atom nil))

(defn start-server [host port]
  (init)
  (reset! server (immutant/run app :host host :port port)))

(defn stop-server []
  (when @server
    (destroy)
    (immutant/stop @server)
    (reset! server nil)))

(defn start-app [[host port]]
  (.addShutdownHook (Runtime/getRuntime) (Thread. stop-server))
  (start-server (http-host host) (http-port port))
  (timbre/info "server started on port:" (:port @server)))

(defn -main [& args]
  (cond
    (some #{"migrate" "rollback"} args) (migrations/migrate args)
    :else (start-app args)))
  
