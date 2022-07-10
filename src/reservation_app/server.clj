(ns reservation-app.server
  (:require [ring.adapter.jetty :as jetty]
            [ring.middleware.params :as params]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [muuntaja.core :as m]
            [reitit.ring.coercion :as coercion]
            [reitit.ring :as ring]

            [clojure.spec.alpha :as s]
            [spec-tools.spec :as spec]
            [reitit.coercion.spec]

            ))




;; wrap into Spec Records to enable runtime conforming
(s/def ::x spec/int?)
(s/def ::y spec/int?)
(s/def ::total spec/int?)

(def routes
  ["/spec" {:coercion reitit.coercion.spec/coercion}
   ["/plus" {:responses {200 {:body (s/keys :req-un [::total])}}
             :get {:summary "plus with query-params"
                   :parameters {:query (s/keys :req-un [::x ::y])}
                   :handler (fn [{{{:keys [x y]} :query} :parameters}]
                              {:status 200
                               :body {:total (+ x y)}})}
             :post {:summary "plus with body-params"
                    :parameters {:body (s/keys :req-un [::x ::y])}
                    :handler (fn [{{{:keys [x y]} :body} :parameters}]
                               {:status 200
                                :body {:total (+ x y)}})}}]])


(def app
  (ring/ring-handler
    (ring/router
      [routes]
      {:data {:muuntaja m/instance
              :middleware [params/wrap-params
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
