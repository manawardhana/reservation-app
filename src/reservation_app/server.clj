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

            [clojure.string :refer [trim]]

            [buddy.hashers :as hashers]
            ))
;http://localhost:3000/api-docs/index.html

(comment #_(dbfns/drop-appointment-request-table db)
#_(dbfns/drop-appointment-slot-event-table db)
#_(dbfns/drop-person-table db)

#_(dbfns/create-person-table db)
#_(dbfns/create-appointment-request-table db)
#_(dbfns/create-appointment-slot-event-table db)
)


(def person-entity
  {:reservation-app.entity.person/id
   {:error-msgs
    {:en {:* "Id should be a number."}
     :si {:* "Id සඳහා අංකයක් අවශ්‍යයි."}}}
   :reservation-app.model.person/first-name
   {:error-msgs
    {:en {:* "Please enter the first name."}
     :si {:* "මුල් නම ඉංග්‍රීසියෙන් යොදන්න."}}}
   :reservation-app.model.person/post-code
   {:error-msgs
    {:en {:* "Please enter a valid post code."}
     :si {:* "වලංගු තැපැල් කේතයක් සඳහන් කරන්න."}}}
   :reservation-app.model.person/last-name
   {:error-msgs
    {:en {:* "Please provide the last name."}
     :si {:* "අවසන් නම/වාසගම ඉංග්‍රීසියෙන් යොදන්න."}}}
   :reservation-app.model.person/mobile-phone
   {:error-msgs
    {:en {:* "Please provide a valid australian mobile phone number."}
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
(def post-code-regex #"^[0-9]{4}$")

(s/def :person-spec/email-type (s/and string? #(re-matches email-regex %)))
(s/def :person-spec/post-code-type (s/and string? #(re-matches post-code-regex %)))

(s/def :person-spec/id int?)
(s/def :person-spec/first-name (s/and string?
                                      #(> (count (trim %)) 2)))
(s/def :person-spec/last-name string?)

(s/def :person-spec/email :person-spec/email-type)
(s/def :person-spec/post-code :person-spec/post-code-type)
(s/def :person-spec/password (s/nilable string?))
(s/def :person-spec/password1 (s/nilable string?))
(s/def :person-spec/password2 (s/nilable string?))
(s/def :person-spec/otp (s/nilable string?))
(s/def :person-spec/old-password (s/nilable string?))
(s/def :person-spec/can-log-in boolean?)
(s/def :person-spec/mobile-phone (s/and string?
                                        #(> (count (trim %)) 9)))


(s/def :person-spec/deleted boolean?)
;(s/def :reservation-app.person/created-at ?)



;(s/def :person-spec/person-sub (s/keys :req-un [:person-spec/email]))
(s/def :person-spec/person-create (s/and
                                   (s/keys :req-un [:person-spec/first-name
                                                    :person-spec/last-name
                                                    :person-spec/mobile-phone
                                                    :person-spec/post-code]
                                           :opt-un [:person-spec/password1
                                                    :person-spec/password2
                                                    :person-spec/email])
                                   (fn [m] (= (get m :password1)
                                              (get m :password2)))))
(s/def :person-spec/person (s/and
                            (s/keys :req-un [:person-spec/id
                                             :person-spec/first-name
                                             :person-spec/last-name
                                             :person-spec/can-log-in
                                             :person-spec/mobile-phone
                                             :person-spec/post-code
                                             :person-spec/deleted]
                                    :opt-un [:person-spec/password1
                                             :person-spec/password2
                                             :person-spec/email])
                            (fn [m] (= (get m :password1)
                                       (get m :password2)))))

(s/def :person-spec/person-update (s/and
                                   (s/keys :opt-un [:person-spec/password
                                                    :person-spec/first-name
                                                    :person-spec/can-log-in
                                                    :person-spec/mobile-phone
                                                    :person-spec/post-code
                                                    :person-spec/last-name
                                                    :person-spec/password1
                                                    :person-spec/password2
                                                    :person-spec/email])))

(s/def :person-spec/person-password-update
  (s/keys :req-un [:person-spec/password1
                   :person-spec/password2]
          :opt-un [:person-spec/old-password
                   :person-spec/otp]))

(s/def :person-spec/person-password-reset
  (s/keys :req-un []
          :opt-un [:person-spec/email
                   :person-spec/mobile-phone]))

(s/def ::person-id spec/int?)
(s/def ::path-params (s/keys :req-un [::person-id]))

(s/def ::page spec/int?)
(s/def ::limit spec/int?)

(defn get-error-fields [spec data]
  (->> (s/explain-data spec data)
       :clojure.spec.alpha/problems
       (mapv :in)
       (mapv #(mapv name %))))
;bcrypt+sha512$5cf57b05a3e16f6fdc29d49f53ed5ac0$12$233e76efcf08b17745b31996c5d5e06e96a1019e4444fe10
(def person-routes
  [["" {:swagger {:tags [:Person]}
        :coercion reitit.coercion.spec/coercion}
    ["/person"
     {:post {:summary "Create Person"
             :swagger {:operationId :person-post}
             :parameters {:body :person-spec/person-create }
             :handler (fn [ {{:keys [first-name :or other] :as person-post} :body-params}]
                        (print "Person POST")
                        (clojure.pprint/pprint person-post)
                        (let [password-hash (hashers/derive (person-post :password1))
                              person (merge person-post {:can-log-in false
                                                         :verified false
                                                         :deleted false
                                                         :password password-hash})]
                          (if (s/valid? :person-spec/person-create person-post)
                            (do (dbfns/insert-person db person)
                                {:status 201 #_created
                                 :body {:test "test"}})
                            {:status 422
                             :body {:status 422
                                    :message "Malformed entity"
                                    :error-fields (get-error-fields :person-spec/person person)}})))}
      ;curl -X GET -vvv 'http://localhost:3000/person?limit=5&page=1'
      :get {:summary "Get Person List"
            :swagger {:operationId :person-list}
            :responses { 200 { :body (s/coll-of :person-spec/person)}}
            :parameters {:query (s/keys :req-un [::page ::limit])}
            :handler (fn [{{{:keys [page limit]} :query} :parameters}]
                       (let [persons (dbfns/list-person db {:limit limit
                                                            :offset (-> page (- 1) (* limit))})]
                         {:status (if (empty? persons) 204 200)
                          :body persons}))}}]
    ;:responses {200 {:body (s/keys :req-un [::total])}}
    ["/person/:person-id"
     {:put {:summary "Update Person"
            :swagger {:operationId :person-put}
            :parameters {:path {:person-id :person-spec/id}
                         :body :person-spec/person-update}
            :coercion reitit.coercion.spec/coercion
            :handler (fn [ {{:keys [first-name :or other] :as person} :body-params
                            {:keys [person-id]} :path-params}]
;                       (clojure.pprint/pprint person)
                       (if (s/valid? :person-spec/person person)
                         (do
                           (dbfns/clj-expr-generic-update
                            db {:table "person"
                                :updates (select-keys person [:first-name
                                                              :last-name
                                                              :post-code])
                                :id person-id})
                           {:status 200
                            :body {:test "test"}})
                         {:status 422
                          :body {:status 422
                                 :message "Malformed entity"
                                 :error-fields (get-error-fields :person-spec/person person)}}))}
      :get {:summary "Get Person Resource"
            :swagger {:operationId :person-get}
            :parameters {:path ::path-params}
            :responses {200 {:body :person-spec/person}
                        404 {:body string?}}
            :handler (fn [{{{:keys [person-id]} :path} :parameters}]
                       (let [person (dbfns/person-by-id db {:id person-id})]
                         (clojure.pprint/pprint person)
                         (cond
                           (nil? person) {:status 404 :body "404 Not Found"}
                           ;TODO handle DB connection issues
                           (s/valid? :person-spec/person person) {:status 200 :body person})))}}]
    ["/person/:person-id/password"
     {:put {:summary "Change Password"
            :swagger {:operationId :person-password-put}
            :parameters {:path {:person-id :person-spec/id}
                         :body :person-spec/person-password-update}
            :coercion reitit.coercion.spec/coercion
            :handler (fn [ {{:keys [password1 password2 otp old-password] :as passwords} :body-params
                            {:keys [person-id]} :path-params}]
                       (let [person (dbfns/person-by-id db {:id person-id})]
                         (if (and (= password1 password2)
                                  (some? person)
                                  (or (and (some? otp)
                                           (-> otp
                                               (hashers/verify (:otp person))
                                               :valid))
                                      (and (some? old-password)
                                           (-> old-password
                                               (hashers/verify (:password person))
                                               :valid))))
                           (do
                             (dbfns/clj-expr-generic-update
                              db {:table "person"
                                  :updates {:password (hashers/derive password1)}
                                  :id person-id})
                             {:status 200
                              :body {:test "test"}})
                           {:status 422
                            :body {:status 422
                                   :message "Malformed entity"
                                   :error-fields (get-error-fields :person-spec/person person)}})))}}]

    ["/person/password/reset-request"
     {:post {:summary "Password reset request"
            :swagger {:operationId :person-password-reset}
            :parameters {:body :person-spec/person-password-reset}
            :coercion reitit.coercion.spec/coercion
            :handler (fn [{{:keys [email mobile-phone]} :body-params
                            {:keys [person-id]} :path-params}]
                       (let [otp (str (java.util.UUID/randomUUID))
                             person (if (some? email)
                                      (dbfns/person-by-email db {:email email})
                                      (if (some? mobile-phone)
                                        (dbfns/person-by-email db {:mobile-phone mobile-phone})
                                        nil))]
                         (println otp)
                         (if (some? person)
                           (do
                             (dbfns/clj-expr-generic-update
                              db {:table "person"
                                  :updates {:otp (hashers/derive otp)}
                                  :id (:id person)})
                             (if (some? email)
                               (println :todo :email)
                               (if (some? mobile-phone)
                                 (println :todo :mobile-phone)))
                             {:status 202
                              :body {:test "test"}})
                           {:status 422
                            :body {:status 422
                                   :message "Malformed entity"
                                   :error-fields (get-error-fields :person-spec/person person)}})))}}]]])

(def swagger-routes
["" {:no-doc true}
        ["/swagger.json" {:get (swagger/create-swagger-handler)}]
        ["/api-docs/*" {:get (swagger-ui/create-swagger-ui-handler)}]])
;curl -X POST http://localhost:3000/person \
;-H 'Content-Type: application/json' \
;-d '{"deleted":false,"email":"manawardhana@gmail.com","last-name":"Manawardhana","post-code":"2077","mobile-phone":"0457925280","password1":"secret","password2":"secret","can-log-in":false,"first-name":"Tharaka","id":1,"created-at":"2022-07-10T13:09:45Z","verified":true} '

;curl -X PUT http://localhost:3000/person/1 \
;-H 'Content-Type: application/json' \
;-d '{"deleted":false,"email":"manawardhana+1@gmail.com","last-name":"Manawardhana","post-code":"2000","mobile-phone":"0457925280","password1":"secret1","password2":"secret1","can-log-in":false,"first-name":"Tharaka","created-at":"2022-07-10T13:09:45Z","verified":true} '

;curl -X PUT http://localhost:3000/person/1/password \
;-H 'Content-Type: application/json' \
;-d '{"password1":"secret111","password2":"secret111","otp":"162d65bc-638b-4dd7-af7d-6cdd21481445"}'

;curl -X POST http://localhost:3000/person/password/reset-request \
;-H 'Content-Type: application/json' \
;-d '{"email":"manawardhana@gmail.com"}'

;curl -X POST http://localhost:3000/person/password/reset-request \
;-H 'Content-Type: application/json' \
;-d '{"mobile-phone":"0457925280"}'

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
