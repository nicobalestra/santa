(ns santa.core.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response wrap-json-params]]
            [monger.core :as mg]
            [monger.collection :as mc]))

(defonce coll "santa")

(defn- get-db []
  (-> (mg/connect)
      (mg/get-db "test")))

(defn login [params]
  (let [username (get params "username")
        db (get-db)
        user (mc/find-one-as-map db coll {:email username})
        ret {:success (not (nil? (:email user)))}]
(println "Request " (type params))
        (println "The user " user)
        (println "Returning " (resp/response ret))
    (resp/response ret)))


(defroutes app-routes
  (GET "/" [] (resp/content-type 
  					(resp/resource-response "index.html" {:root "public"})
  					"text/html"))
  (POST "/login" {params :json-params} (login params))
  (route/resources "/")
  (route/not-found "Not Found")
 
  )

(def app
	(-> app-routes
  		(wrap-json-body)
      (wrap-json-params)
      (wrap-json-response)
  ))
