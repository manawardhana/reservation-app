(ns reservation-app.server
  (:require
   [clojure.pprint :as pprint]
   [clojure.spec.alpha :as s]
   [spec-tools.spec :as spec]
   [reitit.coercion.spec]

   [reservation-app.db :refer [db]]
   [reservation-app.db.bootstrap :as dbfns]
   [reservation-app.specs :as specs]

   [clojure.string :refer [trim]]

   [buddy.hashers :as hashers]
   [buddy.sign.jwt :as jwt]

   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]

   [tick.core :as t]))
;http://localhost:3000/api-docs/index.html

(def max-page-limit 5)

(create-ns 'reservation-app.person)
(alias 'person-spec 'reservation-app.person)

(defn get-error-fields [spec data]
  (->> (s/explain-data spec data)
       :clojure.spec.alpha/problems
       (mapv :in)
       (mapv #(mapv name %))))

(def user-routes
  [["" {:swagger {:tags [:Person]}
        :coercion reitit.coercion.spec/coercion}
    ["/user/log-in"
     {:post {:summary "Log in"
             :swagger {:operationId :log-in-post}
             :parameters {:body :person-spec/log-in-post}
             :handler (fn [ {{:keys [email password mobile-phone] :as log-in-post} :body-params}]
                        (let [person (if (some? email)
                                       (dbfns/person-by-email db {:email email})
                                       (if (some? mobile-phone)
                                         (dbfns/person-by-email db {:mobile-phone mobile-phone})
                                         nil))
                              sub-person (select-keys person [:id
                                                              :first-name
                                                              :last-name
                                                              :email
                                                              :mobile-phone])
                              formatter (t/formatter "E, dd MMM yyyy HH:mm:ss z")
                              token-expiry (t/format formatter (-> (t/>> (t/instant (t/now))
                                                                         (t/new-duration 10 :minutes))
                                                                   (t/in "GMT")))]
                          (if (and (some? person)
                                   (-> password
                                       (hashers/verify (:password person))
                                       :valid))
                            {:status 200
                             :body {:status 200
                                    :body (merge sub-person
                                                 {:auth-token (jwt/sign
                                                               (assoc sub-person :expiry token-expiry)
                                                               signing-secret)
                                                  :expiry token-expiry})}}
                            {:status 422
                             :body {:status 422
                                    :message "Malformed entity"
                                    :error-fields (get-error-fields :person-spec/person person)}})))}}]]])

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
            :parameters {:query (s/keys :req-un [::specs/page ::specs/limit])}
            :handler (fn [{{{:keys [page limit]} :query} :parameters :as request}]
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

                       (if (s/valid? :person-spec/person-update person)
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
            :parameters {:path ::specs/path-params}
            :responses {200 {:body :person-spec/person}
                        404 {:body string?}}
            :handler (fn [{{{:keys [person-id]} :path} :parameters}]
                       (let [person (dbfns/person-by-id db {:id person-id})]
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

;curl -X POST http://localhost:3000/user/log-in \
;-H 'Content-Type: application/json' \
;-d '{"email":"manawardhana@gmail.com", "password":"secret111"}'

