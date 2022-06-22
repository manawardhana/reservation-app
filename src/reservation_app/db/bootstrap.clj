(ns reservation-app.db.bootstrap
  (:require [hugsql.core :as hugsql]))

(hugsql/def-db-fns "reservation_app/db/sql/db.sql")


;; For most HugSQL usage, you will not need the sqlvec functions.
;; However, sqlvec versions are useful during development and
;; for advanced usage with database functions.
(hugsql/def-sqlvec-fns "reservation_app/db/sql/db.sql")
