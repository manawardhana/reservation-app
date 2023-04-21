(ns reservation-app.specs
  (:require
   [malli.core :as m]
   [malli.util :as mu]

   [clojure.string :refer [trim]]))

(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
(def post-code-regex #"^[0-9]{4}$")


(defn validate-phone [phone]
  (and (re-matches #"\(|\)|\d{10}" phone)
       (some #{"04" "05"} [(subs phone 0 2)])))

(def person-properties
  [:map
   [:id :int]
   [:first-name :string]
   [:last-name :string]
   [:can-log-in :boolean]
   [:mobile-phone
    [:and :string
     [:fn {:error/message "A valid Australian Mobile Phone Number must be given."} validate-phone]]]
   [:post-code
    [:and :string
     [:re {:error/message "Valid post code must be entered."} #"\(|\)|\d{4}"]]]
   [:deleted :boolean]
   [:password1
    [:and
     {:error/message "Password must be given."} :string
     [:re {:error/message "Password must contain at least 8 characters, one upper case letter, one lower case letter and a number and a symbol like: # @ & *."}
      #"^(?=.*?[A-Z])(?=.*?[a-z])(?=.*?[0-9])(?=.*?[#?!@$%^&*-]).{8,}$"]]]
   [:password2 :string]
   [:email [:re {:error/message "Email is not valid"}
            email-regex]]])

(def password-equality-fn
  [:fn {:error/message "passwords don't match"}
   (fn [{:keys [password1 password2]}]
     (= password1 password2))])

(def person
  (m/schema [:and person-properties
                  password-equality-fn]))
             
(def person-create
  (m/schema [:and (-> person-properties
                     (mu/dissoc :id)
                     (mu/dissoc :deleted)
                     (mu/dissoc :can-log-in)
                     (mu/assoc :captcha-token :string))
             password-equality-fn]))

(def person-update
  (m/schema (-> person-properties
                  (mu/dissoc :id)
                  (mu/dissoc :password1)
                  (mu/dissoc :password2))))
                  ;(dissoc :can-log-in)
                  ;
(def person-list-item
  (m/schema (-> person-properties
                  (mu/dissoc :password))))

(def person-edit-form-fill
  (m/schema (-> person-properties
                  (mu/dissoc :password1)
                  (mu/dissoc :password2))))
(def log-in
  (m/schema [:map
             [:password :string]
             [:captcha-token :string]
             [:email {:optional true} [:re email-regex]]
             [:mobile-phone {:optional true} :string]]))


(def booking-request
  (m/schema [:map
             [:id :int]
             [:requested-by :int]
             [:apt-date :string]
             [:status :string]
             [:booking-option :string]
             [:requesters-comments {:optional :true} :string]
             [:approvers-comments {:optional :true} [:maybe :string]]
             [:approved-by {:optional :true} [:maybe :int]]]))

(def booking-request-with-person-detail
  (-> booking-request
      (mu/assoc :first-name :string)
      (mu/assoc :last-name :string)))

(def booking-request-post
  (-> booking-request
      (mu/dissoc :status)
      (mu/dissoc :id)
      (mu/dissoc :approved-by)
      (mu/dissoc :requested-by)))


(def booking-request-create
  (-> booking-request
      (mu/dissoc :id)
      (mu/dissoc :status)))

(def booking-reqeust-post
  (-> booking-request-create
     (mu/dissoc :requested-by)
     (mu/dissoc :approved-by)))

(def person-password-update
  (m/schema [:map
             [:password1 :string]
             [:password2 :string]
             [:old-password {:optional true} :string]
             [:otp {:optional true} :string]]))

(def person-password-reset
  (m/schema [:map
             [:email {:optional :true} [:re email-regex]]
             [:mobile-phone :string]]))

(def booking-request-approval
  (m/schema [:map
             [:request-id :int]
             [:approvers-comments {:optional :true} [:maybe :string]]
             [:action [:enum "approved" "rejected" "canceled"]]]))



;(s/def :person-spec/email-type (s/and string? #(re-matches email-regex %)))
;(s/def :person-spec/post-code-type (s/and string? #(re-matches post-code-regex %)))
;
;(s/def :person-spec/id int?)
;(s/def :person-spec/first-name (s/and string?
;                                      #(> (count (trim %)) 2))
;(s/def :person-spec/last-name string?)
;
;(s/def :person-spec/email :person-spec/email-type)
;(s/def :person-spec/post-code :person-spec/post-code-type)
;(s/def :person-spec/password (s/nilable string?))
;(s/def :person-spec/password1 (s/nilable string?))
;(s/def :person-spec/password2 (s/nilable string?))
;(s/def :person-spec/otp (s/nilable string?))
;(s/def :person-spec/old-password (s/nilable string?))
;(s/def :person-spec/can-log-in boolean?)
;(s/def :person-spec/mobile-phone (s/and string?
;                                        #(> (count (trim %)) 9))
;(s/def :person-spec/captcha-token string?)
;
;(s/def :person-spec/deleted boolean?)
;(s/def :reservation-app.person/created-at ?)


;(def person-id (m/schema :int))
;(def path-params (m/schema [:map
                             ;[:person-id :int]]))
;(def page (m/schema :int))
;(def limit (m/schema :int))
;
;(def from-date (m/schema :string))
;(def to-date (m/schema :string))




;(s/def :person-spec/person-sub (s/keys :req-un [:person-spec/email]))
#_(s/def :person-spec/person-create (s/and
                                     (s/keys :req-un [:person-spec/first-name
                                                      :person-spec/last-name
                                                      :person-spec/mobile-phone
                                                      :person-spec/post-code]
                                             :opt-un [:person-spec/password1
                                                      :person-spec/password2
                                                      :person-spec/captcha-token
                                                      :person-spec/email])
                                     (fn [m] (= (get m :password1)
                                                (get m :password2)))))
#_(s/def :person-spec/person (s/and
                              (s/keys :req-un [:person-spec/id
                                               :person-spec/first-name
                                               :person-spec/last-name
                                               :person-spec/can-log-in
                                               :person-spec/mobile-phone
                                               :person-spec/post-code
                                               :person-spec/deleted]
                                      :opt-un [:person-spec/password1
                                               :person-spec/password2
                                               :person-spec/email])
                              (fn [m] (= (get m :password1)
                                         (get m :password2)))))

#_(s/def :person-spec/person-update (s/keys :req-un []
                                            :opt-un [;:person-spec/password
                                                     :person-spec/first-name
                                                     :person-spec/last-name
                                                     :person-spec/can-log-in
                                                     :person-spec/mobile-phone
                                                     :person-spec/post-code
;                                                  :person-spec/password1
;                                                  :person-spec/password2
                                                     :person-spec/email]))

#_(s/def :person-spec/log-in-post (s/keys :req-un [:person-spec/password
                                                   :person-spec/captcha-token]
                                          :opt-un [:person-spec/email
                                                   :person-spec/mobile-phone]))


#_(s/def :person-spec/person-password-update
    (s/keys :req-un [:person-spec/password1
                     :person-spec/password2]
            :opt-un [:person-spec/old-password
                     :person-spec/otp]))



#_
(s/def :person-spec/person-password-reset
  (s/keys :req-un []
          :opt-un [:person-spec/email
                   :person-spec/mobile-phone]))

;(s/def ::person-id spec/int?)
;(s/def ::path-params (s/keys :req-un [::person-id]))
;
;(s/def ::page spec/int?)
;(s/def ::limit spec/int?)
;
;(s/def ::from-date spec/string?) ; todo date
;(s/def ::to-date spec/string?) ; todo date
;
;(s/def :booking-spec/id spec/int?)
;(s/def :booking-spec/requested-by spec/int?)
;(s/def :booking-spec/approved-by (s/nilable spec/int?))
;(s/def :booking-spec/apt-date spec/string?) ; todo date
;(s/def :booking-spec/booking-option spec/string?)
;(s/def :booking-spec/status spec/string?)
;(s/def :booking-spec/requesters-comments spec/string?)

;; Booking Specs


#_(s/def :booking-spec/booking-request (s/keys :req-un [:booking-spec/id
                                                           :booking-spec/requested-by
                                                           :booking-spec/apt-date
                                                           :booking-spec/status
                                                           :booking-spec/booking-option]
                                                  :opt-un [:booking-spec/requesters-comments
                                                           :booking-spec/approved-by]))
#_
(s/def :booking-spec/booking-request-create (s/keys :req-un [:booking-spec/requested-by
                                                             :booking-spec/apt-date
                                                             :booking-spec/booking-option]
                                                    :opt-un [:booking-spec/requesters-comments
                                                             :booking-spec/approved-by]))
#_
(s/def :booking-spec/booking-request-post (s/keys :req-un [:booking-spec/apt-date
                                                           :booking-spec/booking-option]
                                                  :opt-un [:booking-spec/requesters-comments]))
