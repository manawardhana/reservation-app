(ns reservation-app.db
  (:require [hugsql.core :as hugsql]))

(def db
  {:classname   "org.h2.Driver"
   :subprotocol "h2:tcp"
   :subname     "localhost/~/dhana_booking;MODE=MySQL"
   :user        "sa"
   :password    "secret"
   })
