(ns reservation-app.core
  (:gen-class)
  (:require [reservation-app.db :refer [db]]
            [reservation-app.db.bootstrap :as dbfns]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [clojure.java.jdbc]
            ))

(defn -main []

  #_(dbfns/create-person-table db)
  #_(dbfns/create-appointment-slot-event-table db)
  #_(dbfns/create-appointment-request-table db)


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
