(ns reservation-app.server
  (:require
   [reitit.ring :as ring]
   [ring.adapter.jetty :as jetty]
   [integrant.core :as ig]))

(def system-config
  {:reservation-app.server/jetty   {:port    3000
                     :join?   false
                     :handler (ig/ref :reservation-app.server/handler)}
   :reservation-app.server/handler {}})

(defmethod ig/init-key :reservation-app.server/jetty [_ {:keys [port join? handler]}]
  (println "server running in port" port)
  (jetty/run-jetty handler {:port port :join? join?}))

(defmethod ig/halt-key! :reservation-app.server/jetty [_ server]
  (.stop server))

(defmethod ig/init-key :reservation-app.server/handler [_ _]
  (ring/ring-handler
   (ring/router
    ["/ping" {:get {:handler (fn [_] {:status 200 :body "pong!"})}}])))

(defn -main []
  (ig/init system-config))
