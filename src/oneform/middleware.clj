(ns oneform.middleware
  (:require [oneform.session :as session]
            [oneform.layout :refer [*app-context* error-page]]
            [taoensso.timbre :as timbre]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [clj-time.core :as time]
            [selmer.middleware :refer [wrap-error-page]]
            [prone.middleware :refer [wrap-exceptions]]
            [ring.util.response :refer [redirect]]
            [ring.middleware.reload :as reload]
            [ring.middleware.defaults :refer [site-defaults wrap-defaults]]
            [ring.middleware.anti-forgery :refer [wrap-anti-forgery]]
            [ring.middleware.session-timeout :refer [wrap-idle-session-timeout]]
            [ring.middleware.session.memory :refer [memory-store]]
            [ring.middleware.format :refer [wrap-restful-format]]
            [buddy.sign.jws :as jws]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.auth.accessrules :refer [restrict]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]
))

(def secret "mysupersecret")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Semantic response helpers    ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn ok [d] {:status 200 :body d})
(defn bad-request [d] {:status 400 :body d})


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; (defn wrap-servlet-context [handler]                               ;;
;;   (fn [request]                                                     ;;
;;     (binding [*servlet-context*                                    ;;
;;               (if-let [context (:servlet-context request)]         ;;
;;                 ;; If we're not inside a servlet environment       ;;
;;                 ;; (for example when using mock requests), then    ;;
;;                 ;; .getContextPath might not exist                 ;;
;;                 (try (.getContextPath context)                     ;;
;;                      (catch IllegalArgumentException _ context)))] ;;
;;       (handler request))))                                         ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn wrap-context [handler]
  (fn [request]
    (binding [*app-context*
              (if-let [context (:servlet-context request)]
                ;; If we're not inside a servlet environment
                ;; (for example when using mock requests), then
                ;; .getContextPath might not exist
                (try (.getContextPath context)
                     (catch IllegalArgumentException _ context))
                ;; if the context is not specified in the request
                ;; we check if one has been specified in the environment
                ;; instead
                (:app-context env))]
      (handler request))))
(defn wrap-internal-error [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable t
        (timbre/error t)
        (error-page {:status 500
                     :title "Something very bad has happened!"
                     :message "We've dispatched a team of highly trained gnomes to take care of the problem."})))))


(defn wrap-dev [handler]
  (if (env :dev)
    (-> handler
        reload/wrap-reload
        wrap-error-page
        wrap-exceptions)
    handler))

(defn wrap-csrf [handler]
  (wrap-anti-forgery
    handler
    {:error-response
     (error-page
       {:status 403
        :title "Invalid anti-forgery token"})}))


;; Create an instance of auth backend.
(def auth-backend (jws-backend {:secret secret :options {:alg :hs512}}))

(defn wrap-auth [handler]
  (-> handler
      (wrap-authentication auth-backend)
;;      (wrap-authorization auth-backend)
;;      (wrap-json-response {:pretty false})
;;      (wrap-json-body {:keywords? true :bigdecimals? true})
      ))

(defn on-error [request response]
  (error-page
    {:status 403
     :title (str "Access to " (:uri request) " is not authorized")}))

(defn wrap-restricted [handler]
  (restrict handler {:handler authenticated?
                     :on-error on-error}))

(defn wrap-formats [handler]
  (wrap-restful-format handler :formats [:json-kw :transit-json :transit-msgpack]))

(defn wrap-base [handler]
  (-> handler
      wrap-dev
      (wrap-idle-session-timeout
        {:timeout (* 60 30)
         :timeout-response (redirect "/")})
      wrap-formats
      wrap-auth
      (wrap-defaults
        (-> site-defaults
            (assoc-in [:security :anti-forgery] false)
            (assoc-in  [:session :store] (memory-store session/mem))))
      wrap-context
      wrap-internal-error))
