(ns santa.core.email-manager
  (:require [postal.core :as mail]
  			[santa.core.db :as db]))
(def send-match-body (str "<h1>Happy Christmas</h1>"
						  "<br/>Hello ${username}, it's Secret Santa picker here.<br/>"
						  "As you know it's that time of the year..."
						  "This year you will be the Secret Santa for <b>${match}</b> :) <br/>"
						  "<br/><img src='${avatarUrl}'><br/>"
				    	  "Here's some ideas for what you can buy to your wonderful colleague:<br/>"
    				      "<br/><b>${preferences}</b>"))

(defn fill-var [txt var-name var-value]
	(println "Fill var with txt=" txt " var-name=" var-name " var-value=" var-value)
	(clojure.string/replace txt (str "${" var-name "}") var-value))

(defn email-match [drawer]
	(let [match (:match drawer)]
	 (mail/send-message 
		{:from "Secret Santa Picker <mailer@lithium.com>"
         :to [(:email drawer)]
	     :subject "It's Secret Santa time"
	     :body [
	     	{:type "text/html"
	         :content (-> send-match-body
	         			(fill-var "username" (:name drawer))
	         			(fill-var "match" (:name match))
	         			(fill-var "preferences" (:preferences match))
	         			(fill-var "avatarUrl" (get-in match [:litho :url-icon] "https://gapyear.s3.amazonaws.com/images/made/images/advertiser_files/santa_claus_582_388.jpg")))}]})))



(defn send-code-to [user]
	(if (and (not (nil? (:num-codes user))) (> (:num-codes user) 10)) 
		{:success false :message "Maximum attempt of retrieving your code reached."}
		(do 
		  (mail/send-message 
						{:from "Secret Santa Picker <mailer@lithium.com>"

	                       :to [(:email user)]
	                       :subject "Your secret santa picker code"
	                       :body [
	                       		{:type "text/html"
	                       	 	:content (str "Hi " (:name user) "!<br/>Here is your Secret Santa access password: '<b>" (:ID user) "</b>'") }
	                        ]})

			 (db/increase-requested-code user)
			 {:success true})))
