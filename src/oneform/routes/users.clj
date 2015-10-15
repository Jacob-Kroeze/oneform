(ns oneform.routes.users
  (:require [compojure.route :as route]
            [compojure.core :refer :all]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [ring.util.response :refer [response redirect content-type]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.params :refer [wrap-params]]
            [clj-time.core :as time]
            [buddy.sign.jws :as jws]
            [buddy.auth :refer [authenticated? throw-unauthorized]]
            [buddy.auth.backends.token :refer [jws-backend]]
            [buddy.auth.middleware :refer [wrap-authentication wrap-authorization]]))

(defonce secret "mysupersecret")

(def authdata {:admin "secret"
               :test "secret"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Semantic response helpers    ;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn ok [d] {:status 200 :body d})
(defn bad-request [d] {:status 400 :body d})


(defn login
  [request]
;;  (println request)
  (let [username (get-in request [:body-params "username"])
        password (get-in request [:body-params "password"])
        valid? (some-> authdata
                       (get (keyword username))
                       (= password))]
    (if valid?
      (let [claims {:user (keyword username)
                    :exp (time/plus (time/now) (time/seconds 36000)) ;; about a month
                    }
            token (jws/sign claims secret {:alg :hs512})]
        (ok {:token token}))
      (bad-request {:message "wrong auth data"}))))

(defroutes login-routes
  (context (:app-context env) request
           (POST "/login" [] login)))
