(ns santa.core.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response wrap-json-params]]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [clojure.string :refer [lower-case]]
            )
  (:import org.bson.types.ObjectId))

(defonce coll "santa")

(defn- get-db []
  (-> (mg/connect)
      (mg/get-db "test")))

(defn login [params]
  (let [username (get params "username")
  		username (lower-case username)
        db (get-db)
        user (mc/find-one-as-map db coll {:email username})
        ret {:success (not (nil? (:email user))) :id (str (:_id user))}]
    (println "The user " user)
    (resp/response ret)))


(defn- find-match 
"Given the drawer we scan for the database in search of users who haven't been picked up yet (picker is nil).
If we find one, we pick the first one and we'll assign it to the drawer. We then make an http call searching for 
Lithium avatar to put on the page (this should never fail or at least should never block the operation)."
  [drawer]
  (println "Find a match for " drawer)
  (let [db (get-db)
          match (mc/find-one-as-map db coll {$and [{:picked-by {$exists false}}
                                                   {:email {$ne (str (:email drawer))}}]})]
          (println "Match found: " match)
          (if match
          	(do
          		(mc/update-by-id db coll (:_id drawer) {$set {:match (:email match)}})
          		(mc/update-by-id db coll (:_id match)  {$set {:picked-by (:email drawer)}})
          		{:match (:email match)})
          	{:match ""}
          )))

(defn- extract-litho 
"Filter only the data we really need out of the data queried from Lithium"
	[user]
	(let [profiles (get-in user [:response :user :profiles :profile])
		  get-profile (fn [name]
		  				(:$ (first (filter #(= (:name %) name)  profiles))))
		  signature (get-profile "signature")
		  icon (get-profile "url_icon")
		  userId (get-in user [:response :user :id :$])]
	{:signature signature
	 :url-icon icon
	 :userId userId}
	))

(defn- fill-litho 
"Query the Lithium community to retrieve data about the user and store'em on the database"
	[person]
	(if-not (:litho person)
		(let [rest (str "https://community.lithium.com/restapi/vc/users/login/" 
						(:login person) 
						"?restapi.response_format=json")
			  resp (client/get rest)
			  user (parse-string (:body resp) true)
			  user (extract-litho user)
			  db   (get-db)]
			
			(println "Resp from litho: " user)
			(mc/update-by-id db coll (:_id person) {$set {:litho user}})
			(assoc person :litho user))
		person))

(defn- get-person-by-email [email]
	(let [db (get-db)
		  person (mc/find-one-as-map db coll {:email email})
		  person (fill-litho person)]
		  (println "Call to get-person-by-email returns " person)
		  (resp/response {:colleague (dissoc person :_id)})))

(defn draw [drawer-id]
  (let [db (get-db)
        drawer (mc/find-map-by-id db coll (ObjectId. drawer-id))
        match (if (:match drawer)
  				(do 
  					(println "This person already drawn. Returning the old match " (:match drawer))
  					{:match (:match drawer)})
				(find-match drawer))
        ]
        (println "I should look for a pick for drawer " drawer )
        (println "Match found: " match)

        (resp/response match)))

(defroutes app-routes
  (GET "/" [] (resp/content-type 
  					(resp/resource-response "index.html" {:root "public"})
  					"text/html"))
  (POST "/login" {params :json-params} (login params))
  (GET "/draw/:id" [id] (draw id))
  (GET "/colleague/:email" [email] (get-person-by-email email))
  (route/resources "/")
  (route/not-found "Not Found")
 
  )

(def app
	(-> app-routes
  		(wrap-json-body)
      (wrap-json-params)
      (wrap-json-response)
  ))
