(ns santa.core.db
	(:refer-clojure :exclude [select find sort])
	(:require [monger.core :as mg]
		      [monger.collection :as mc]
              [monger.operators :refer :all]
              [monger.query :refer :all])
	(:import org.bson.types.ObjectId))

(defonce coll "santa")

(defn- parseInt [num]
	(try
		(Integer/parseInt num)
		(catch Exception e -1)))


(defn get-db []
  (-> (mg/connect)
      (mg/get-db "santadb")))

(defn get-user [username password]
	(mc/find-one-as-map (get-db) coll {$and [{:email username} {:ID (parseInt password)}]}))

(defn get-user-by-email [email]
	(mc/find-one-as-map (get-db) coll {:email email}))

(defn find-match [drawer-email]
	(with-collection (get-db) coll
         (find {$and [{:picked-by {$exists false}}
              		  {:email {$ne (str (:email drawer-email))}}]})
         (sort (array-map :ID 1))
         (limit 10)))

(defn store-match [drawer match]
	(let [db (get-db)]
	  (mc/update-by-id db coll (:_id drawer) {$set {:match (:email match)}})
      (mc/update-by-id db coll (:_id match)  {$set {:picked-by (:email drawer)}})
      {:match (:email match)}))

(defn get-user-by-id [id]
	(mc/find-map-by-id (get-db) coll (ObjectId. id)))

(defn add-litho-data [person litho-user]
	(mc/update-by-id (get-db) coll (:_id person) {$set {:litho litho-user}}))

(defn increase-requested-code [user]
	(mc/update-by-id (get-db) 
					 coll 
					 (:_id user) 
					 {$set {:num-codes (if (nil? (:num-codes user)) 1 (inc (:num-codes user)))}}))