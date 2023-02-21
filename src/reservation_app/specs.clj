(ns reservation-app.specs
  (:require
   [clojure.spec.alpha :as s]
   [spec-tools.spec :as spec]

   [clojure.string :refer [trim]])
  (:import [java.time LocalDate]))

; Person Spec
(def email-regex #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}$")
(def post-code-regex #"^[0-9]{4}$")

(s/def :person-spec/email-type (s/and string? #(re-matches email-regex %)))
(s/def :person-spec/post-code-type (s/and string? #(re-matches post-code-regex %)))

(s/def :person-spec/id int?)
(s/def :person-spec/first-name (s/and string?
                                      #(> (count (trim %)) 2)))
(s/def :person-spec/last-name string?)

(s/def :person-spec/email :person-spec/email-type)
(s/def :person-spec/post-code :person-spec/post-code-type)
(s/def :person-spec/password (s/nilable string?))
(s/def :person-spec/password1 (s/nilable string?))
(s/def :person-spec/password2 (s/nilable string?))
(s/def :person-spec/otp (s/nilable string?))
(s/def :person-spec/old-password (s/nilable string?))
(s/def :person-spec/can-log-in boolean?)
(s/def :person-spec/mobile-phone (s/and string?
                                        #(> (count (trim %)) 9)))

(s/def :person-spec/deleted boolean?)
;(s/def :reservation-app.person/created-at ?)

;(s/def :person-spec/person-sub (s/keys :req-un [:person-spec/email]))
(s/def :person-spec/person-create (s/and
                                   (s/keys :req-un [:person-spec/first-name
                                                    :person-spec/last-name
                                                    :person-spec/mobile-phone
                                                    :person-spec/post-code]
                                           :opt-un [:person-spec/password1
                                                    :person-spec/password2
                                                    :person-spec/email])
                                   (fn [m] (= (get m :password1)
                                              (get m :password2)))))
(s/def :person-spec/person (s/and
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

(s/def :person-spec/person-update (s/keys :req-un []
                                          :opt-un [;:person-spec/password
                                                   :person-spec/first-name
                                                   :person-spec/last-name
                                                   :person-spec/can-log-in
                                                   :person-spec/mobile-phone
                                                   :person-spec/post-code
;                                                  :person-spec/password1
;                                                  :person-spec/password2
                                                   :person-spec/email]))

(s/def :person-spec/log-in-post (s/keys :req-un [:person-spec/password]
                                        :opt-un [:person-spec/email
                                                 :person-spec/mobile-phone]))

(s/def :person-spec/person-password-update
  (s/keys :req-un [:person-spec/password1
                   :person-spec/password2]
          :opt-un [:person-spec/old-password
                   :person-spec/otp]))

(s/def :person-spec/person-password-reset
  (s/keys :req-un []
          :opt-un [:person-spec/email
                   :person-spec/mobile-phone]))

(s/def ::person-id spec/int?)
(s/def ::path-params (s/keys :req-un [::person-id]))

(s/def ::page spec/int?)
(s/def ::limit spec/int?)

(s/def :booking-spec/id spec/int?)
(s/def :booking-spec/requested-by spec/int?)
(s/def :booking-spec/approved-by spec/int?)
(s/def :booking-spec/apt-date spec/string?)
(s/def :booking-spec/booking-option spec/string?)
(s/def :booking-spec/requesters-comments spec/string?)

;; Booking Specs
(s/def :booking-spec/booking-request (s/keys :req-un [:booking-spec/id
                                                      :booking-spec/requested-by
                                                      :booking-spec/apt-date
                                                      :booking-spec/booking-option]
                                             :opt-un [:booking-spec/requesters-comments
                                                      :booking-spec/approved-by]))

(s/def :booking-spec/booking-request-create (s/keys :req-un [:booking-spec/requested-by
                                                             :booking-spec/apt-date
                                                             :booking-spec/booking-option]
                                                    :opt-un [:booking-spec/requesters-comments
                                                             :booking-spec/approved-by]))

(s/def :booking-spec/booking-request-post (s/keys :req-un [:booking-spec/apt-date
                                                           :booking-spec/booking-option]
                                                  :opt-un [:booking-spec/requesters-comments]))
