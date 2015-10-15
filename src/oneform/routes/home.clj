(ns oneform.routes.home
  (:require [oneform.layout :as layout]
            [compojure.core :refer [defroutes context GET]]
            [environ.core :refer [env]]
            [ring.util.http-response :refer [ok]]
            [ring.util.response :as r]
            [ring.middleware.anti-forgery :as csrf]
            [clojure.java.io :as io]))

(defn home-page []
  (layout/render "home.html"))
(defn gold-file-page []
  (layout/render "gold-file.html" {:csrf-token csrf/*anti-forgery-token*
                                   :schema-url (str (:app-context env) "/schema/api.json")
                                   :lookup-url (str (:app-context env) "/usc-id")
                                   :files-endpoint (str (:app-context env) "/file-upload")
                                   :form-action-endpoint (str (:app-context env) "/gold-file")}))
(defn form-builder-page [uuid]
  (layout/render "form-builder.html" {:csrf-token csrf/*anti-forgery-token*
                                      :files-endpoint (str (:app-context env) "/file-upload")
                                      :form-action-endpoint (str (:app-context env) "/schemas")
                                      :form-schemas-url 
                                      (clojure.string/join "/"
                                                           (remove clojure.string/blank?
                                                                   [(:app-context env) 
                                                                    "form-schemas"
                                                                    uuid]))}))
(defn form-reader-page [form-name uuid]
  (layout/render "form-reader.html" {:csrf-token csrf/*anti-forgery-token*
                                     :files-endpoint (str (:app-context env) "/file-upload")
                                     :lookup-url (str (:app-context env) "/usc-id")
                                     :form-action-endpoint (str (:app-context env) "/forms/" form-name)
                                     :form-name form-name}))
(defn form-index-page [form-name uuid]
  (layout/render "form-index.html" {:csrf-token csrf/*anti-forgery-token*
                                    :files-endpoint (str (:app-context env) "/file-upload")
                                    :lookup-url (str (:app-context env) "/usc-id")
                                    :form-action-endpoint (str (:app-context env) "/forms/" form-name)
                                    :form-name form-name}))
(defn form-schemas-index-page []
  (layout/render "form-schemas-index.html" {:csrf-token csrf/*anti-forgery-token*
                                    :files-endpoint (str (:app-context env) "/file-upload")
                                    :lookup-url (str (:app-context env) "/usc-id")
                                    :form-action-endpoint (str (:app-context env) "/form_schemas/")
                                    :form-name "form_schemas"}))

(defroutes home-routes
  (context (:app-context env) request
           (GET "/" [] (home-page))
           (GET "/gold-file" [] (-> (gold-file-page)
                                    (r/header "X-CSRF-TOKEN" csrf/*anti-forgery-token*)
                                    (r/set-cookie "CSRF-TOKEN" csrf/*anti-forgery-token*)))
           (GET "/docs" [] (ok (-> "docs/docs.md" io/resource slurp)))
           (GET "form-builder/new" [] (form-builder-page nil))
           (GET "/form-builder/:uuid" [uuid] (form-builder-page uuid))
           (GET "/form-reader/:form-name" [form-name uuid] (form-reader-page form-name uuid))
           (GET "/form-index/:form-name" [form-name uuid] (form-index-page form-name uuid))
           (GET "/form-schemas-index" [] (form-schemas-index-page))))


