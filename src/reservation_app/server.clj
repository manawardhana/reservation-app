(ns reservation-app.server
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [muuntaja.core :as m]
            [reitit.ring.coercion :as coercion]
            [reitit.ring :as ring]
            [reitit.swagger :as swagger]
            [reitit.swagger-ui :as swagger-ui]

            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [reitit.coercion.spec]

            [reservation-app.db :refer [db]]
            [reservation-app.db.bootstrap :as dbfns]

            [clojure.string :refer [trim]]))

(def person-entity
  {:reservation-app.entity.person/id
   {:error-msgs
    {:en {:* "Id should be a number."}
     :si {:* "Id සඳහා අංකයක් අවශ්‍යයි."}}}
   :reservation-app.model.person/first-name
   {:error-msgs
    {:en {:* "First name is missing."}
     :si {:* "මුල් නම ඉංග්‍රීසියෙන් යොදන්න."}}}
   :reservation-app.model.person/last-name
   {:error-msgs
    {:en {:* "Last name must be provided."}
     :si {:* "අවසන් නම/වාසගම ඉංග්‍රීසියෙන් යොදන්න."}}}
   :reservation-app.model.person/mobile-phone
   {:error-msgs
    {:en {:* "Last name must be provided."}
     :si {:* "වලන්ගු ජන්ගම දුරකතන අංකයක් (ඔස්ට්‍රෙලියනු) ඇතුලත් කරන්න."}}}
   :reservation-app.model.person/password
   {:error-msgs
    {:en {:* "Please provide a valid strong password."}
     :si {:* "ශක්තිමත් මුරපදයක් (පාස්වර්ඩ්) ඇතුලත් කරන්න."}}}
   :reservation-app.model.person/email
   {:error-msgs
    {:en {:* "Please provide a valid email address."}
     :si {:* "වලන්ගු විද්‍යුත් ලිපිනය ඇතුලත් කරන්න."}}}
   }
  )

(def max-page-limit 5)

(create-ns 'reservation-app.person)
(alias 'person-spec 'reservation-app.person)

; Person Spec
(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
(s/def :person-spec/email-type (s/and string? #(re-matches email-regex %)))

(s/def :person-spec/id int?)
(s/def :person-spec/first-name (s/and string?
                                      #(> (count (trim %)) 2)))
(s/def :person-spec/last-name string?)

(s/def :person-spec/email :person-spec/email-type)
(s/def :person-spec/password (s/nilable string?))
(s/def :person-spec/password-salt (s/nilable string?))
(s/def :person-spec/can-log-in boolean?)
(s/def :person-spec/mobile-phone (s/and string?
                                        #(> (count (trim %)) 9)))


(s/def :person-spec/deleted boolean?)
;(s/def :reservation-app.person/created-at ?)



;(s/def :person-spec/person-sub (s/keys :req-un [:person-spec/email]))

(s/def :person-spec/person (s/keys
                                :req-un [:person-spec/id
                                         :person-spec/first-name
                                         :person-spec/last-name
                                         :person-spec/can-log-in
                                         :person-spec/mobile-phone
                                         :person-spec/deleted]
                                :opt-un [:person-spec/password
                                         :person-spec/password-salt
                                         :person-spec/email]))
(s/def :person-spec/person-update (s/keys
                                   :opt-un [:person-spec/password
                                            :person-spec/first-name
                                            :person-spec/can-log-in
                                            :person-spec/mobile-phone
                                            :person-spec/last-name
                                            :person-spec/password-salt
                                            :person-spec/email]))
(s/def ::person-id spec/int?)
(s/def ::path-params (s/keys :req-un [::person-id]))

(s/def ::page spec/int?)
(s/def ::limit spec/int?)

(defn get-error-fields [spec data]
  (->> (s/explain-data spec data)
       :clojure.spec.alpha/problems
       (mapv :in)
       (mapv #(mapv name %))))

(def person-routes
  [["" {:swagger {:tags [:Person]}
        :coercion reitit.coercion.spec/coercion}
    ["/person" {:post {;:parameters {:body-params :person-spec/person }
                                        :handler (fn [ {{:keys [first-name :or other] :as person} :body-params}]
                                                   (clojure.pprint/pprint person)
                                                   (print "EXPLAIN STR")
                                (clojure.pprint/pprint(s/explain-str :person-spec/person person))
                                                   (print "EXPLAIN DATA")
                                (clojure.pprint/pprint (s/explain-data :person-spec/person person))
                                (if (s/valid? :person-spec/person person)
                                  (do
                                    (dbfns/insert-person db person)
                                    {:status 201 #_created
                                     :body {:test "test"}})
                                  {:status 422
                                   :body {:status 422
                                          :message "Malformed entity"
                                          :error-fields (get-error-fields :person-spec/person person)}}))}


               :get {:summary "Get Person List"
                     ;curl -X GET -vvv 'http://localhost:3000/person?limit=5&page=1'
                         :responses { 200 { :body (s/coll-of :person-spec/person)}}
                         :parameters {:query (s/keys :req-un [::page ::limit])}
                     :handler (fn [{{{:keys [page limit]} :query} :parameters}]
                                (let [persons (dbfns/list-person db {:limit limit
                                                                     :offset (-> page (- 1) (* limit))})]
                                  {:status (if (empty? persons) 204 200)
                                   :body persons}))}}]

    ["/person/:person-id" {
                                        ;:responses {200 {:body (s/keys :req-un [::total])}}

                           :put {:parameters {:path {:person-id :person-spec/id}
                                              :body :person-spec/person-update}
                                 :coercion reitit.coercion.spec/coercion
                                 :handler (fn [ {{:keys [first-name :or other] :as person} :body-params}]
                                                   (print "UPDATE")
                                ;(clojure.pprint/pprint person)
;                                (clojure.pprint/pprint(s/explain-str :person-spec/person person))
                                ;(clojure.pprint/pprint (s/explain-data :person-spec/person person))
                                ;(if (s/valid? :person-spec/person person)
                                  (do
                                    (dbfns/clj-expr-generic-update db {:table "person"
                                                                       :updates (select-keys person [:first-name
                                                                                                     :last-name])
                                                                       :id (:id person)})
                                    {:status 200
                                     :body {:test "test"}})
                                  #_{:status 422
                                   :body {:status 422
                                          :message "Malformed entity"
                                          :error-fields (get-error-fields :person-spec/person person)}})}

                           :get {:summary "Get Person Resource"
                                 :responses {200 {:body :person-spec/person}
                                             404 {:body string?}}
                                 :parameters {:path ::path-params}
                                 :handler (fn [{{{:keys [person-id]} :path} :parameters}]
                                            (let [person (dbfns/person-by-id db {:id person-id})]
                                              (clojure.pprint/pprint person)
                                              (cond
                                                (nil? person) {:status 404 :body "404 Not Found"}
                                                ;TODO handle DB connection issues
                                                (s/valid? :person-spec/person person) {:status 200 :body person})))}}]]])
(def swagger-routes
["" {:no-doc true}
        ["/swagger.json" {:get (swagger/create-swagger-handler)}]
        ["/api-docs/*" {:get (swagger-ui/create-swagger-ui-handler)}]])
;curl -X POST http://localhost:3000/person/2 \
;-H 'Content-Type: application/json' \
;-d '{"deleted":false,"email":"manawardhana@gmail.com","last-name":"Manawardhana","mobile-phone":"0457925280","password_salt":null,"password":null,"can-log-in":false,"first-name":"Tharaka","id":1,"created-at":"2022-07-10T13:09:45Z","verified":true} '

(def app
  (ring/ring-handler
   (ring/router
    [person-routes
     swagger-routes]

      {:data {:muuntaja m/instance
              :middleware [wrap-params
                           wrap-keyword-params
                           muuntaja/format-middleware
                           coercion/coerce-exceptions-middleware
                           coercion/coerce-request-middleware
                           coercion/coerce-response-middleware]}})
    (ring/create-default-handler)))

(defn start []
  (jetty/run-jetty #'app {:port 3000, :join? false})
  (println "Server running in port 3000"))


(defn -main []
  (start))
