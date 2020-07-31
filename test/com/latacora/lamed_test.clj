(ns com.latacora.lamed-test
  (:require
   [clojure.test :as t]
   [com.latacora.lamed :as l]
   [clj-http.lite.client :as http]
   [clojure.java.io :as io]
   [cheshire.core :as json]
   [taoensso.timbre :refer [spy]]))

;; https://docs.aws.amazon.com/lambda/latest/dg/runtimes-api.html

(def fake-env
  (delay 
    {"AWS_LAMBDA_RUNTIME_API" "127.0.0.1"
     "_HANDLER" "" ;; TODO
     "LAMBDA_TASK_ROOT" ""})) ;; TODO

(def next-invocation-req
  {::fn `http/request
   ::args [{:method "GET"
            ::l/path "/runtime/invocation/next"
            :url "http://127.0.0.1/2018-06-01/runtime/invocation/next"}]})


(def request-id
  "8476a536-e9f4-11e8-9739-2dfe598c3fcd")

(def deadline-ms
  1542409706888)

(def trace-id
  "Root=1-5bef4de7-ad49b0e87f6ef6c87fc2e700;Parent=9a9197af755a6419;Sampled=1")

(def lambda-arn
  "arn:aws:lambda:us-east-2:123456789012:function:custom-runtime")
(def headers
  {"lambda-runtime-aws-request-id" request-id
   "lambda-runtime-deadline-ms" (str deadline-ms)
   "lambda-runtime-invoked-function-arn" lambda-arn
   "lambda-runtime-trace-id" trace-id})

(def first-ctx
  #::l{:request-id request-id
       :invoked-function-arn lambda-arn
       :deadline-ms deadline-ms
       :trace-id trace-id
       :headers headers
       :body (-> "example-lambda-request.json"
                 io/resource
                 io/reader
                 slurp)})

;; potential headers:
;; * Lambda-Runtime-Aws-Request-Id – The request ID, which identifies the request that triggered the function invocation. For example, 8476a536-e9f4-11e8-9739-2dfe598c3fcd.
;; * Lambda-Runtime-Deadline-Ms – The date that the function times out in Unix time milliseconds. For example, 1542409706888.
;; * Lambda-Runtime-Invoked-Function-Arn – The ARN of the Lambda function, version, or alias that's specified in the invocation. For example, arn:aws:lambda:us-east-2:123456789012:function:custom-runtime.
;; * Lambda-Runtime-Trace-Id – The AWS X-Ray tracing header. For example, Root=1-5bef4de7-ad49b0e87f6ef6c87fc2e700;Parent=9a9197af755a6419;Sampled=1.
;; * Lambda-Runtime-Client-Context – For invocations from the AWS Mobile SDK, data about the client application and device.
;; * Lambda-Runtime-Cognito-Identity – For invocations from the AWS Mobile SDK, data about the Amazon Cognito identity provider.
;; clj-http.lite.client deals with the case ambiguity in http by lowercasing all
;; the headers
(def http-responses
  {`http/request
   [{:status 200 ;; tktk?
     :headers headers
     :body (-> "example-lambda-request.json" io/resource slurp)}]})

(defn ^:private mock
  [events the-fn & args]
  (let [n (->> @events (filter (comp #{the-fn} ::fn)) count)]
    (swap! events conj {::fn the-fn ::args args})
    (-> the-fn http-responses (nth n nil))))

(t/deftest next-invocation-tests
  (let [events (atom [])]
    (with-redefs
      [l/env fake-env
       http/request (fn [& args] (apply mock events `http/request args))]
      ;; Simulate getting an event
      (t/is (= [] @events))
      (t/is (= first-ctx (#'l/next-invocation!)))
      (t/is (= next-invocation-req (last @events)))

      ;; Simulate success
      (let [body (json/encode {:my-result 1})
            path (format "/runtime/invocation/%s/response" request-id)]
        (#'l/invocation-response! first-ctx body)
        (t/is (= {::fn `http/request
                  ::args [{:method "POST"
                           ::l/path path
                           :url (str "http://127.0.0.1/2018-06-01" path)
                           :body body}]}
                 (last @events)))))))
