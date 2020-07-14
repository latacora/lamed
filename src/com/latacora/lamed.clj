(ns com.latacora.lamed
  (:require
   [cheshire.core :as json]
   [clj-http.lite.client :as http]
   [clojure.set :as set])
  (:gen-class))

(defn ^:private request
  [{::keys [path] :as request}]
  (let [api (System/getenv "AWS_LAMBDA_RUNTIME_API")
        url (str "http://" api path)]
    (-> request
        (assoc :url url)
        (http/request))))

(defn next-invocation!
  "Gets the next Lambda invocation."
  []
  (request
   {:method "GET"
    ::path "/runtime/invocation/next"}))

(defn init-error!
  "Reports an initialization error to the Lambda API."
  [exc]
  )

(defn invocation-response!
  "Reports an invocation response to the Lambda API."
  [req resp]
  (let [path "/runtime/invocation/%s/response"]
    ))

(defn invocation-error!
  "Reports an invocation error to the Lambda API."
  [req resp]
  (let [path "/runtime/invocation/%s/error"]
    (request
     {:method "POST"
      ::path (format path (:aws-id-tktktk req))})))

(defn init!
  "Runs the Lambda init steps and start delegating to `handler`."
  [handler]
  (let [env-keys ["_HANDLER" "LAMBDA_TASK_ROOT" "AWS_LAMBDA_RUNTIME_API"]]
    (select-keys (System/getenv) env-keys)))

(defn delegate!
  "Start acquiring Lambda invocations and passing them to the given handler."
  [handler]
  (loop [ctx (next-invocation!)]
    (try
      (handler ctx)
      (catch Exception e (invocation-error! e))
      (finally
        (recur (next-invocation!))))))
