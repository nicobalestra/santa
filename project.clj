(defproject santa "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [compojure "1.2.0"]
                 [ring/ring-defaults "0.1.2"]
                 [com.novemberain/monger "3.0.0-rc2"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-jetty-adapter "1.2.2"]
                 [clj-http "1.0.1"]
                 [cheshire "5.3.1"]
                 [com.draines/postal "1.11.3"]
                 [environ "1.0.1"]
                 ]

  :plugins [[lein-ring "0.8.13"]
            [lein-environ "1.0.1"]]
  :ring {:handler santa.core.handler/app}
  :uberjar-name "santa.jar"
  :hooks [environ.leiningen.hooks]
  :main santa.core.handler
  :profiles  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                                  [ring-mock "0.1.5"]]
                    :env {:database-url "mongodb://santadb:lithium@ds033123.mongolab.com:33123/heroku_6zg9nq7x"}}
              :production {:env {:production true}}})
