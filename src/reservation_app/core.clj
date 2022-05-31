(ns reservation-app.core
  (:gen-class)
  (:require [reservation-app.db :refer [db]]
            [reservation-app.db.db :as dbfns]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [clojure.java.jdbc]
            ))

(defn -main []

  (dbfns/create-person-table db)
  (dbfns/create-appointment-slot-event-table db)
  (dbfns/create-appointment-request-table db)


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
