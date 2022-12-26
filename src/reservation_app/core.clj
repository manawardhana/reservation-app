(ns reservation-app.core
  (:gen-class)
  (:require [reservation-app.db :refer [db]]
            [reservation-app.db.bootstrap :as dbfns]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [clojure.java.jdbc]

            ))

(defn reset-db []
  (dbfns/drop-appointment-slot-event-table db)
  (dbfns/drop-appointment-request-table db)
  (dbfns/drop-person-table db)
  )


(defn create-db-schema []
  (dbfns/create-person-table db)
  (dbfns/create-appointment-slot-event-table db)
  (dbfns/create-appointment-request-table db)
  )


(defn -main []

  (reset-db)
  (create-db-schema)



  (dbfns/insert-person db
   {
    :first-name    "Tharaka"
    :last-name     "Manawardhana"
    :email         "manawardhana@gmail.com"
    :mobile-phone  "0457925280"
    :verified      true
    }
   )
  )
