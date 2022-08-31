(ns reservation-app.db
  (:require [hugsql.core :as hugsql]
            [hugsql.adapter]
            [clojure.string]))

(def db
  {:classname   "org.h2.Driver"
   :subprotocol "h2:tcp"
   :subname     "localhost/~/dhana_booking;MODE=MySQL"
   :user        "sa"
   :password    "secret"
   })

(defn log-sqlvec [sqlvec]
  (println "SQL =======>>>")
  (println (->> sqlvec
              (map #(clojure.string/replace (or % "") #"\n" ""))
              (clojure.string/join " ; "))))

(defn log-command-fn [this db sqlvec options]
  (log-sqlvec sqlvec)
  (condp contains? (:command options)
    #{:!} (hugsql.adapter/execute this db sqlvec options)
    #{:? :<!} (hugsql.adapter/query this db sqlvec options)))

(defmethod hugsql.core/hugsql-command-fn :! [sym] `log-command-fn)
(defmethod hugsql.core/hugsql-command-fn :<! [sym] `log-command-fn)
(defmethod hugsql.core/hugsql-command-fn :? [sym] `log-command-fn)
