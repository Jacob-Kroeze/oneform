(ns oneform.routes.api
  (:require [oneform.layout :as layout]
            [oneform.db.core :as db]
            [oneform.util.sanitize-filename :as sf]
            [environ.core :refer [env]]
            [compojure.core :refer [defroutes context GET POST PUT DELETE]]
            [compojure.coercions :refer [as-uuid]]
            [ring.util.http-response :refer [ok]]
            [ring.util.response :refer [redirect file-response] :as r]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.walk :as walk]
            [digest]
            ))

(def file-store "/tmp/")

(defn strip-non-numeric [string]
;;  (last (clojure.string/split string #"<|>"))
  (let [any-number (re-matcher #"\d+" (or string ""))
        id (re-find any-number)]
    id))


(defn to-xml [data]
  "Convert a valid collection to XML."
  (clojure.string/replace
   (with-out-str 
     (xml/emit-element data))
   #"\n" ""))




(defn add-tags 
  "Recursively return {:tag keyword :content values} for a map"
  [init coll]
  (let [add-map-tags 
        (fn [init m]
          (assoc init :content
                 (vec (for [[k v] m
                            :let [key {:tag k}]]
                        (conj key (add-tags {} v))))))
        add-vec-tags 
        (fn [init vector]
          (assoc init :content
                 (vec (for [v vector]
                        (conj {:tag "value"} (add-tags {} v))))))]  

    (cond (map? coll) (add-map-tags init coll)
          (vector? coll) (add-vec-tags init coll)
          :else {:content [(clojure.string/escape
                            (str coll)
                            {\< "&lt;", \> "&gt;", \& "&amp;" \% "&#37;"})]})))
(defn gold-files-pending-xml
  "Return a response that contains xml formatted gold-files that are not in laserfiche repo"
  []
  (r/content-type  {:body
                    (to-xml
                     (add-tags {:tag "repo-files"}
                               (into [] 
                                     (db/run
                                       db/read-gold-files-pending-save-to-laserfiche))))}
                   "application/xml"))

(defroutes api-routes
  (context (:app-context env) request
           (GET "/schema/api.json" []
                {:body
                 (:data (first (db/run db/read-api-json-schema)))})
           (GET "/form/schema-index/:tablename" [tablename]
                (let [tname (clojure.string/replace tablename "-" "_")
                      doc (:schema (:data (first
                                           (db/run db/read-form-schema
                                             {:tablename tname}))) )]
                  {:status (if doc 200 404)
                   :body {:title (str tname " index")
                          :type ["array"]
                          :items doc }}))
           (GET "/form/options-index/:tablename" [tablename]
                (let [tname         (clojure.string/replace tablename "-" "_")
                      doc           (:options (:data (first
                                                      (db/run db/read-form-schema
                                                        {:tablename tname}))) )
                      array-options {:items  {:fields (:fields doc)}} ]
                  {:status (if doc 200 404)
                   :body array-options }))
           (GET "/form/data/:tablename" [tablename uuid]
                (let [tname (clojure.string/replace tablename "-" "_") 
                      item (get-in  (db/run db/read-form-document-by-uuid {:tablename tname :uuid uuid})
                                    [:set :data])
                      items (map 
                             #(get-in % [:set :data])
                             (db/run db/read-form-document {:tablename tname}))]
                  {:body (if uuid item items)}))
           (GET "/form/:component/:tablename" [component tablename]
                (let [tname (clojure.string/replace tablename "-" "_")
                      doc       ((keyword component)
                                 (:data
                                  (first (db/run db/read-form-schema
                                           {:tablename tname}))))]
                  {:status (if doc 200 404)
                   :body doc}))
           (GET "/form/:tablename" [tablename]
                (let [tname (clojure.string/replace tablename "-" "_")
                      doc   (:data
                             (first (db/run db/read-form-schema
                                      {:tablename tname})))]
                  {:status (if doc 200 404)
                   :body doc}))
           (POST "/forms/:tablename" [tablename :as {data :body-params}]
                 (let [saved (db/run db/save-document {:tablename (clojure.string/replace tablename
                                                                                          "-"
                                                                                          "_")
                                                       :data      data})]
                   {:status (if saved 200 304)
                    :body saved}))
           (GET "/form-schemas/:uuid" [uuid :<< as-uuid ]
                {:body  
                 (:data
                  (first (db/run db/read-form-schema-by-uuid {:uuid uuid})))})
           (GET "/form-schemas" []
                (let [tname "form_schemas"
                      items (map #(update
                                   (select-keys % [:uuid :table_name])
                                   :uuid str ) (db/run db/read-form-schemas))
                      ;; each map value needs to be form-builder/table_name/uuid
                      items-urls (for [ v items]
                                   {:uuid (str  (:app-context env) "/form-builder/" (:uuid v))
                                    :table_name (clojure.string/replace (:table_name v) #"_|-" " ")})]
                  {:body items-urls}))
           (POST "/echo" [])
           (POST "/file-upload"  [files_files]
                 {:status 200
                  :body {:files
                         ;; this should be using the for macro so you can read it?
                         (vec (map #(let [f (str (digest/sha1 (:tempfile %))
                                                 "-"
                                                 (sf/sanitize (:filename %)))
                                          do-copy (io/copy (:tempfile %)
                                                           (io/file (str file-store f)))
                                          m {:name         (:filename %)
                                             :size         (:size %)
                                             :url          (str (:app-context env) "/files/" f)
                                             :thumbnailUrl (str (:app-context env) "/files/" f)
                                             :deleteUrl    (str (:app-context env) "/files/" f)
                                             :deleteType   "DELETE"}]
                                      m)
                                   files_files))}})
           (GET "/files/:filename" [filename]
                (file-response (str file-store filename)))
           (DELETE "/files/:filename" [filename]
                   {:status 200
                    :body {:files
                           [{filename (io/delete-file (str file-store filename))}]}})
           (GET "/usc-id" [q]
                ;;TODO fuzzy match
                {:body
                 (db/run db/fuzzy-search-by-name-for-usc-id {:q q})})
           (POST "/gold-file" {data :params}
                 ;; TODO: we shouldn't need to keywordize-keys. Middleware is
                 ;; being lazy here.
                 ;; redirect or communicate how to go to the view of a gold-file.
                 (let [clean-data (update-in (clojure.walk/keywordize-keys data)
                                             [:usc_id]
                                             strip-non-numeric)
                       record (db/run db/create-gold-file<! {:data clean-data })]
                   {:status (if record 200 304)
                    :body (:uuid record)}))
           (POST "/schemas" {schema :params}
                 (let [schema_name (clojure.string/replace (get-in schema ["options" "formName"] "")
                                                   "-"
                                                   "_")
                       saved (db/run db/create-form-schema
                               {:schema (clojure.walk/keywordize-keys schema)
                                :schema_name schema_name})] 
                   {:status (if saved  200 500 )
                   :body saved}))))


(defroutes secure-api-routes
  (context (:app-context env) request
           (GET "/lf/gold-files-pending.xml" []
                (gold-files-pending-xml))
           (GET "/lf/mail-files-pending.xml" []
                (gold-files-pending-xml)) 
           (GET "/gold-file/:uuid" [uuid]
                uuid)
           (PUT "/document/:uuid" [uuid :<< as-uuid :as {data :body-params}]
;;                (println data)
                (let [saved? (get data "saved")
                      result (first (db/run db/document_update_callback {:uuid uuid :data data}))]
                  {:headers
                   {"Saved" saved?}
                   :body
                   result}))))
