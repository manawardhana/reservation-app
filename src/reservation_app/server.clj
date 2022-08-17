(ns reservation-app.server
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [muuntaja.core :as m]
            [reitit.ring.coercion :as coercion]
            [reitit.ring :as ring]

            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [reitit.coercion.spec]

            [reservation-app.db :refer [db]]
            [reservation-app.db.bootstrap :as dbfns]
            ))




;; wrap into Spec Records to enable runtime conforming
(s/def ::x spec/int?)
(s/def ::y spec/int?)
(s/def ::total spec/int?)



; Person Spec
(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
(s/def :reservation-app.person/email-type (s/and string? #(re-matches email-regex %)))

(s/def :reservation-app.person/id int?)
(s/def :reservation-app.person/first-name string?)
(s/def :reservation-app.person/last-name string?)
(s/def :reservation-app.person/email :reservation-app.person/email-type)
(s/def :reservation-app.person/password (s/nilable string?))
(s/def :reservation-app.person/password-salt (s/nilable string?))
(s/def :reservation-app.person/can-log-in boolean?)
(s/def :reservation-app.person/mobile-phone string?)

(s/def :reservation-app.person/deleted boolean?)
;(s/def :reservation-app.person/created-at ?)




(s/def :reservation-app/person (s/keys
                                :req-un [:reservation-app.person/id
                                         :reservation-app.person/first-name
                                         :reservation-app.person/last-name
                                         :reservation-app.person/can-log-in
                                         :reservation-app.person/mobile-phone
                                         :reservation-app.person/deleted]
                                :opt-un [:reservation-app.person/password
                                         :reservation-app.person/password-salt
                                         :reservation-app.person/email]))




(s/def ::person-id spec/int?)
(s/def ::path-params (s/keys :req-un [::person-id]))

(def person-routes
  ["/person" {:coercion reitit.coercion.spec/coercion
             ; :middleware [[params/wrap-params][wrap-keyword-params]]
              }
   ["/:person-id" {
                   ;:responses {200 {:body (s/keys :req-un [::total])}}

                   :get {:summary "Get Person Resource"
                         :responses { 200 { :body :reservation-app/person}}
                         :parameters {:path ::path-params}
                         :handler (fn [{{{:keys [person-id]} :path} :parameters}]
                                    {:status 200
                                     :body (dbfns/person-by-id db {:id person-id})})}
                   :post {:summary "Post Person Resource"
                          ;:parameters {:body-params :reservation-app/person }
                          :handler (fn [ {{:keys [first-name :or other] :as person} :body-params}]
                                     (clojure.pprint/pprint person)
                                     (clojure.pprint/pprint(s/explain-data :reservation-app/person person))
                                     (if (s/valid? :reservation-app/person person)
                                       (do
                                         (dbfns/insert-person db person)
                                         {:status 200
                                          :body {:test "test"}})
                                       {:status 422
                                        :body {:message "Malformed entity"}}))}}]])

;curl -X POST http://localhost:3000/person/2 \
;-H 'Content-Type: application/json' \
;-d '{"deleted":false,"email":"manawardhana@gmail.com","last-name":"Manawardhana","mobile-phone":"0457925280","password_salt":null,"password":null,"can-log-in":false,"first-name":"Tharaka","id":1,"created-at":"2022-07-10T13:09:45Z","verified":true} '

  ; "/spec" {:coercion reitit.coercion.spec/coercion}
  ; ["/plus" {:responses {200 {:body (s/keys :req-un [::total])}}
  ;           :get {:summary "plus with query-params"
  ;                 :parameters {:query (s/keys :req-un [::x ::y])}
  ;                 :handler (fn [{{{:keys [x y]} :query} :parameters}]
  ;                            {:status 200
  ;                             :body {:total (+ x y)}})}
  ;           :post {:summary "plus with body-params"
  ;                  :parameters {:body (s/keys :req-un [::x ::y])}
  ;                  :handler (fn [{{{:keys [x y]} :body} :parameters}]
  ;                             {:status 200
  ;                              :body {:total (+ x y)}})}}]




(def app
  (ring/ring-handler
   (ring/router
    [person-routes

     ]
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
  (println "server running in port 3000"))


(defn -main []
  (start))

;{:name "Tharaka" :address {:unit 2 :number 10 :street "Albert Street" :city "Hornsby"}}
