(ns com.latacora.lamed
  (:require
   [cheshire.core :as json]
   [byte-streams :as bs]
   [taoensso.timbre :as log]
   [clj-http.lite.client :as http])
  (:gen-class))

(delay
  (def ^:private env (into {} (System/getenv))))


(defn ^:private request
  [{::keys [path] :as request}]
  (let [api (env "AWS_LAMBDA_RUNTIME_API")
        url (str "http://" api path)]
    (-> request
        (assoc :url url)
        (http/request))))

(defn ^:private next-invocation!
  "Gets the next Lambda invocation."
  []
  (let [{:keys [headers body]} (request
                                {:method "GET"
                                 ::path "/runtime/invocation/next"})]
    {::request-id (headers "lambda-runtime-aws-request-id")
     ::invoked-function-arn (headers "lambda-runtime-invoked-function-arn")
     ::deadline-ms (Long/parseLong (headers "lambda-runtime-deadline-ms"))
     ::trace-id (headers "lambda-runtime-trace-id")
     ::headers headers
     ::body body}))

(defn ^:private init-error!
  "Reports an initialization error to the Lambda API."
  [{::keys [request-id]} e]
  (throw (ex-info "init-error! not implemented" {})))

(defn ^:private invocation-response!
  ;; TODO: is the response string sent back to lambda necessarily JSON? I think
  ;; so, in which case maybe this fn should do serialization for you. On the
  ;; other hand, down the line we want to make it easy to take existing
  ;; RequestStreamHandlers, which would mean we have to support a bytes-like API
  ;; at the lowest level.
  "Reports an invocation response to the Lambda API."
  [{::keys [request-id]} response-string]
  (request
   {:method "POST"
    ::path (format "/runtime/invocation/%s/response" request-id)
    :body response-string}))

(defn ^:private invocation-error!
  "Reports an invocation error to the Lambda API."
  [{::keys [request-id]} e]
  (request
   {:method "POST"
    ::path (format "/runtime/invocation/%s/error" request-id)
    :body (str e)}))

(defn ^:private init!
  "Runs the Lambda init steps and start delegating to `handler`."
  [handler]
  ;; TODO: not ... really... implemented? I think this really wants me to call
  ;; setenv maybe? but we don't care about _HANDLER (we only have the 1
  ;; handler), I think we don't care about LAMBDA_TASK_ROOT because it'll
  ;; already be our cwd, so I think it's safe to ignore these?
  (let [env-keys ["_HANDLER" "LAMBDA_TASK_ROOT" "AWS_LAMBDA_RUNTIME_API"]]
    (select-keys env env-keys)))

(defn delegate!
  "Start acquiring Lambda invocations and passing them to the given handler."
  [handler]
  (loop [ctx (next-invocation!)]
    (try
      (invocation-response! ctx (handler ctx))
      (catch Exception e (invocation-error! ctx e)))
    ;; can't recur in `(finally ...)`, because that's not a tail position.
    (recur (next-invocation!))))

(defn ctx->body-ins
  [ctx]
  (-> ctx
      :body
      bs/to-input-stream))

(defn delegate-streams!
  "Start acquiring Lambda invocations and passing them to the given handler."
  [handler]
  (loop [ctx (next-invocation!)
         ins (ctx->body-ins ctx)]
    (try
      (with-open [ous (java.io.ByteArrayOutputStream)]
        (handler ins ous ctx)
        (invocation-response! ctx (.toByteArray ous)))
      (catch Exception e (invocation-error! ctx e)))
    ;; can't recur in `(finally ...)`, because that's not a tail position.
    (recur (next-invocation!) (ctx->body-ins ctx))))

