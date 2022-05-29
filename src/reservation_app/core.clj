(ns reservation-app.core
  (:gen-class)
  (:require [reservation-app.db.db :refer [db]]
            [reservation-app.db.sql.db :as dbfns]
            [clojure.pprint :as pprint]
            [clojure.string :as string]
            [clojure.java.jdbc]
            ))

(defn -main []

  (println "\n\"The Princess Bride\" HugSQL Example App\n\n")
(reservation-app.db.sql.db/insert-person
   {
    :first-name    "Tharaka"
    :last-name     "Manawardhana"
    :email         "manawardhana@gmail.com"
    :mobile-phone  "0457925280"
    :verified      true
    }
   )

  (println "\n\nTHE END\n"))
