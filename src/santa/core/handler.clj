(ns santa.core.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.util.response :as resp]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response wrap-json-params]]
            [ring.adapter.jetty :as jetty]
            [clj-http.client :as client]
            [cheshire.core :refer :all]
            [clojure.string :refer [lower-case]]
            [postal.core :as mail]
            [environ.core :refer [env]]
            [santa.core.db :as db]
            [santa.core.email-manager :as email]
            ))



(defn login [params]
  (let [username (get params "username")
  		  username (lower-case username)
        password (get params "password")
        user     (db/get-user username password)
        ret      {:success (not (nil? (:email user))) :id (str (:_id user))}]
    (println "Query for username " username " and password " password)
    (println "The user " user)
    (resp/response ret)))


(defn- find-match 
"Given the drawer we scan for the database in search of users who haven't been picked up yet (picker is nil).
If we find one, we pick the first one and we'll assign it to the drawer. We then make an http call searching for 
Lithium avatar to put on the page (this should never fail or at least should never block the operation)."
  [drawer]
  (println "Find a match for " drawer)
  (let [match (db/find-match drawer) 
  		  match (rand-nth match)]
          (println "Match for user " (:email drawer) " is " match)
          (when match
             (db/store-match drawer match)
              match)))

(defn- extract-litho-data 
"Filter only the data we really need out of the data queried from Lithium"
	[user]
	(let [profiles (get-in user [:response :user :profiles :profile])
		    get-profile (fn [name] (:$ (first (filter #(= (:name %) name)  profiles))))
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

(defn- call-lithium [url]
	(try
		(let [resp (client/get (str "http://community.lithium.com" url))]
			(parse-string (:body resp) true)
		)
	 (catch Exception e 
	 	{:response {:error (str "Unable to invoke Lithium: " e)}}
	 	)))

(defn- fill-litho-data 
"Query the Lithium community to retrieve data about the user and store'em on the database"
	[person]
	(if-not (:litho person)
		(let [rest (str "/restapi/vc/users/login/" 
						(:loginName person) 
						"?restapi.response_format=json")
			  rest-avatar (str "/restapi/vc/users/login/" 
							   (:loginName person) 
							   "/profiles/avatar?restapi.response_format=json")
			  user (call-lithium rest)
			  user (extract-litho-data user)
			  avatar (call-lithium rest-avatar)
			  user (add-avatar user avatar)
			  db   (db/get-db)]
			(println "Resp from litho: " user)
      (db/add-litho-data person user)
			(assoc person :litho user))
		person))

(defn- get-person-by-email [email]
	(let [person (db/get-user-by-email email)
		    person (fill-litho-data person)]
		  (println "Call to get-person-by-email returns " person)
		  (resp/response {:colleague (dissoc person :_id)})))

(defn draw [drawer-id]
  (let [drawer (db/get-user-by-id drawer-id)
        match (if (:match drawer)
  				      (do 
  					      (println "This person already drawn. Returning the old match " (:match drawer))
  					      (db/get-user-by-email (:match drawer)))
                (find-match drawer))
        drawer (assoc drawer :match match)]
        (println "I should look for a pick for drawer " drawer )
        (println "Match found: " match)
        (email/email-match (fill-litho-data drawer))
        (resp/response {:match (:email match)})))


(defn- get-code [params]
	(let [email (get params "username")
		    user (db/get-user-by-email email)]
		(println "Send code to " user)
		(if user
			(resp/response (email/send-code-to user))
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

