(ns santa.core.email-manager
  (:require [santa.core.db :as db])
  (:use [postmark.core :only (postmark)]))
(def api-token (get (System/getenv) "POSTMARK_API_TOKEN"))

(def send-match-body (str "<h1>Happy Christmas</h1>"
						  "<br/>Hello ${username}, it's Secret Santa picker here.<br/>"
						  "As you know it's that time of the year..."
						  "This year you will be the Secret Santa for <b>${match}</b> :) <br/>"
						  "<br/><img src='${avatarUrl}'><br/>"
				    	  "Here's some ideas for what would make ${match} happy:<br/>"
    				      "<br/><b>${preferences}</b><br/>"
    				      "<br/>Thanks and Happy Christmas <br/><b>Secret Santa Picker</b><br/>"
    				      "<img src='http://www.myfunnyanimals.com/wp-content/uploads/2008/11/funny-santa-claus.jpg'>"))
(def send-code-body (str "Hi ${username} "
	                     "!<br/>Here is your Secret Santa access password: '<b>${password}</b>'<br/>"
	                     "Please head to the <a href='https://lithium-santa-picker.herokuapp.com'>Secret Santa Picker</a> website " 
	                     "to select who you are going to make happy this year :)<br/><br/>"
						 "Thanks and Happy Christmas ho ho ho<br/><br/><b>Secret Santa Picker</b><br/>"
						 "<img src='http://www.myfunnyanimals.com/wp-content/uploads/2008/11/funny-santa-claus.jpg'>"))

(def send-message (postmark api-token "nico.balestra@lithium.com"))

(defn fill-var [txt var-name var-value]
	(println "Fill var with txt=" txt " var-name=" var-name " var-value=" var-value)
	(clojure.string/replace txt (str "${" var-name "}") var-value))

(defn email-match [drawer]
	(let [match (:match drawer)]
	 (send-message 
		{:to [(:email drawer)]
	     :subject "It's Secret Santa time"
	     :html (-> send-match-body
	         			(fill-var "username" (:name drawer))
	         			(fill-var "match" (:name match))
	         			(fill-var "preferences" (:preferences match))
	         			(fill-var "avatarUrl" 
	         				(get-in match 
	         					[:litho :url-icon] 
	         "https://gapyear.s3.amazonaws.com/images/made/images/advertiser_files/santa_claus_582_388.jpg")))})))



(defn send-code-to [user]
	(if (and (not (nil? (:num-codes user))) (> (:num-codes user) 10)) 
		{:success false :message "Maximum attempt of retrieving your code reached."}
		(do 
		  (send-message {  :to [(:email user)]
	                       :subject "Your secret santa picker code"
	                       :html (-> send-code-body
	                       			(fill-var "username" (:name user))
	                       			(fill-var "password" (str (:ID user))))})
			 (db/increase-requested-code user)
			 {:success true})))
