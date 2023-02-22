(ns reservation-app.core
  (:gen-class)
  (:require [reservation-app.db :refer [db]]
            [reservation-app.db.bootstrap :as dbfns]
            [reservation-app.server :as server]
            [clojure.java.jdbc]

            [ring.adapter.jetty :as jetty]

            ))


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

(defn start []
  (jetty/run-jetty #'server/app {:port 3000, :join? false})
  (println "Server running in port 3000"))

(defn -main []
  (start))
