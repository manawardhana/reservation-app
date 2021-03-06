(defproject reservation_app "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :main reservation-app.server
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}

  :dependencies [
                 [org.clojure/clojure "1.10.3"]
                 [integrant "0.7.0"]

                 ; Database
                 [com.h2database/h2 "2.1.212"]
                 [com.layerware/hugsql "0.5.3"]

                 ; Web
                 [ring "1.9.5"]
                 [metosin/reitit "0.5.18"]

                 ]
  :repl-options {:init-ns reservation-app.core})
