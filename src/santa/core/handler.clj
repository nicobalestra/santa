(ns santa.core.handler
  (:refer-clojure :exclude [select find sort])
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response wrap-json-params]]
            [ring.adapter.jetty :as jetty]
            [monger.core :as mg]
            [monger.collection :as mc]
            [monger.operators :refer :all]
            [monger.query :refer :all]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [clojure.string :refer [lower-case]]
            [postal.core :as mail]
            [environ.core :refer [env]]
            )
  (:import org.bson.types.ObjectId))

(defonce coll "santa")

(defn- parseInt [num]
	(try
		(Integer/parseInt num)
		(catch Exception e -1)))

(defn- get-db []
  (-> (mg/connect)
      (mg/get-db "test")))

(defn login [params]
  (let [username (get params "username")
  		username (lower-case username)
        password (get params "password")
  		db (get-db)
        user (mc/find-one-as-map db coll {$and [{:email username} {:ID (parseInt password)}]})
        ret {:success (not (nil? (:email user))) :id (str (:_id user))}]
    (println "Query for username " username " and password " password)
    (println "The user " user)
    (resp/response ret)))


(defn- find-match 
"Given the drawer we scan for the database in search of users who haven't been picked up yet (picker is nil).
If we find one, we pick the first one and we'll assign it to the drawer. We then make an http call searching for 
Lithium avatar to put on the page (this should never fail or at least should never block the operation)."
  [drawer]
  (println "Find a match for " drawer)
  (let [db 	  (get-db)
        match (with-collection db coll
          		(find {$and [{:picked-by {$exists false}}
                      		 {:email {$ne (str (:email drawer))}}]})
          		(sort (array-map :ID 1))
          		(limit 10)) 
         ;match (mc/find-one-as-map db coll {$and [{:picked-by {$exists false}}
                         						  ;{:email {$ne (str (:email drawer))}}]} )
  		match (rand-nth match)
  		]
          (println "Match for user " (:email drawer) " is " match)
          (if match
          	(do
          		(mc/update-by-id db coll (:_id drawer) {$set {:match (:email match)}})
          		(mc/update-by-id db coll (:_id match)  {$set {:picked-by (:email drawer)}})
          		{:match (:email match)})
          	{:match ""}
          )))

(defn- extract-litho-data 
"Filter only the data we really need out of the data queried from Lithium"
	[user]
	(let [profiles (get-in user [:response :user :profiles :profile])
		  get-profile (fn [name]
		  				(:$ (first (filter #(= (:name %) name)  profiles))))
		  signature (get-profile "signature")
		  userId (get-in user [:response :user :id :$])]
	{:signature signature
	 :userId userId}
	))

(defn- add-avatar [user avatar]
	(println "Adding avatar information to user " user)
	(println "Avatar " avatar)
	(let [url (get-in avatar [:response :image :url :$])]
		(assoc user :url-icon url))
	)

(defn- fill-litho-data 
"Query the Lithium community to retrieve data about the user and store'em on the database"
	[person]
	(if-not (:litho person)
		(let [rest (str "https://community.lithium.com/restapi/vc/users/login/" 
						(:loginName person) 
						"?restapi.response_format=json")
			  rest-avatar (str "https://community.lithium.com/restapi/vc/users/login/" 
							   (:loginName person) 
							   "/profiles/avatar?restapi.response_format=json")
			  resp (client/get rest)
			  user (parse-string (:body resp) true)
			  user (extract-litho-data user)
			  resp (client/get rest-avatar)
			  avatar (parse-string (:body resp) true)
			  user (add-avatar user avatar)
			  db   (get-db)]
			
			(println "Resp from litho: " user)
			(mc/update-by-id db coll (:_id person) {$set {:litho user}})
			(assoc person :litho user))
		person))

(defn- get-person-by-email [email]
	(let [db (get-db)
		  person (mc/find-one-as-map db coll {:email email})
		  person (fill-litho-data person)]
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

(defn- send-code-to [user]
	(if (and (not (nil? (:num-codes user))) (> (:num-codes user) 10)) 
		{:success false :message "Maximum attempt of retrieving your code reached."}
		(do 
		  (let [db (get-db)]
			(mail/send-message {:from "Secret Santa Picker <mailer@lithium.com>"
	                       :to [(:email user)]
	                       :subject "Your secret santa picker code"
	                       :body [
	                       		{:type "text/html"
	                       	 	:content (str "Hi " (:name user) "!<br/>Here is your Secret Santa access code: '" (:ID user) "'") }
	                       	 ]
	                       })

			(mc/update-by-id db coll (:_id user) {$set {:num-codes (if (nil? (:num-codes user)) 1 (inc (:num-codes user)))}})
			{:success true}
			))))

(defn- get-code [params]
	(let [email (get params "username")
		  db (get-db)
		  user (mc/find-one-as-map db coll {:email email})
		]
		(println "Send code to " user)
		(if user
			(resp/response (send-code-to user))
			(resp/response {:success false :message (str "User with email " email " not found!")})
		)))


(defroutes app-routes
  (GET "/" [] (resp/content-type 
  					(resp/resource-response "index.html" {:root "public"})
  					"text/html"))
  (POST "/login" {params :json-params} (login params))
  (GET "/draw/:id" [id] (draw id))
  (GET "/colleague/:email" [email] (get-person-by-email email))
  (POST "/getcode" {params :json-params} (get-code params))
  (route/resources "/")
  (route/not-found "Not Found")
 
  )

(def app
	(-> app-routes
  		(wrap-json-body)
      (wrap-json-params)
      (wrap-json-response)
  ))

(defn -main [& [port]]
  (let [port (Integer. (or port (env :port) 5000))]
    (jetty/run-jetty #'app {:port port :join? false})))

