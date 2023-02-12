(ns reservation-app.core
  (:gen-class)
  (:require [reservation-app.db :refer [db]]
            [reservation-app.db.bootstrap :as dbfns]
            [reservation-app.server :as server]
            [clojure.string :as string]
            [clojure.java.jdbc]

            [ring.adapter.jetty :as jetty]
            [ring.middleware.cookies :refer [wrap-cookies]]
            [ring.middleware.params :refer [wrap-params]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [muuntaja.core :as m]
            [reitit.ring.coercion :as coercion]
            [reitit.ring :as ring]

            [buddy.sign.jwt :as jwt]
            ))

(def signing-secret "ycpOKsMw+SpDuRpYl34GGHVDnW9or6Cvf0zGy+J8iCzCnd9YtbWUnuh8bjTRoZqh3U0yP9wwJ6URRU3ammWx11RGWN3q5o3Gq5jutuddIUtBeNVEYY/VB92SMX0vZO0jLrLSTXf249SNY4VVL+VdPDyJXuVlYKLfc/ORXgeNOeg")

(comment #_(dbfns/drop-appointment-request-table db)
#_(dbfns/drop-appointment-slot-event-table db)
#_(dbfns/drop-person-table db)

#_(dbfns/create-person-table db)
#_(dbfns/create-appointment-request-table db)
#_(dbfns/create-appointment-slot-event-table db))

(defn reset-db []
  (dbfns/drop-appointment-slot-event-table db)
  (dbfns/drop-appointment-request-table db)
  (dbfns/drop-person-table db))

(defn create-db-schema []
  (dbfns/create-person-table db)
  (dbfns/create-appointment-slot-event-table db)
  (dbfns/create-appointment-request-table db))

(defn wrap-custom-auth [handler]
  (fn [request]
    (let [auth-token (get-in request [:cookies "auth-token" :value])]
      (handler (if (some? auth-token)
                 (assoc request :custom-auth (jwt/unsign auth-token signing-secret))
                 request)))))

(def app
  (ring/ring-handler
   (ring/router
    [server/person-routes
     server/user-routes
     server/swagger-routes]
    {:data {:muuntaja m/instance
            :middleware [wrap-params
                         wrap-cookies
                         wrap-keyword-params
                         wrap-custom-auth
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
