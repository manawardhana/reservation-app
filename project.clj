(defproject reservation_app "0.1.0-SNAPSHOT"
  :description "Simple web application for reservations."
  :url "http://example.com/FIXME"
  :main reservation-app.server
  :license {:name "(c) Tharaka Manawardhana"
            :url "mailto:manawardhana@gmail.com"}

  :dependencies [
                 [org.clojure/clojure "1.10.3"]
                 ;[nrepl "0.9.0"]
                 ;[cider/cider-nrepl "0.28.5"]
                 [integrant "0.7.0"]

                 [cider/cider-nrepl "0.28.5"]

                 ; Database
                 [com.h2database/h2 "2.1.212"]
                 [com.layerware/hugsql "0.5.3"]

                 ; Web
                 [ring "1.9.5"]
                 [metosin/reitit "0.5.18"]
                 [com.cognitect/transit-cljs "0.8.256"]


                 [paginator-clj "0.1.0-SNAPSHOT"]

                 ;security
                 [buddy/buddy-hashers "1.8.158"]
                 [buddy/buddy-sign "3.4.333"]

                 ;utils
                 [tick "0.5.0"]
                 ]
  :plugins [[refactor-nrepl "3.5.2"]
            [cider/cider-nrepl "0.28.3"]]
  :repl-options {:init-ns reservation-app.core})
