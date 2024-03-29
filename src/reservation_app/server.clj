(ns reservation-app.server
  (:require
   [clojure.pprint :refer [pprint]]
   [clojure.string :refer [join split upper-case]]
   [reitit.ring.malli]
   [reitit.coercion.malli]
   [malli.core :as m]
   [malli.error :as me]

   [clj-http.client :as client]
   [reservation-app.db :refer [db]]
   [reservation-app.db.bootstrap :as dbfns]
   [reservation-app.specs :as scm]

   [buddy.hashers :as hashers]
   [buddy.sign.jwt :as jwt]

   [reitit.swagger :as swagger]
   [reitit.swagger-ui :as swagger-ui]

   [ring.middleware.cookies :refer [wrap-cookies]]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [muuntaja.core :as mm]
   [reitit.ring.coercion :as coercion]
   [reitit.ring :as ring]

   [tick.core :as t]))

(def signing-secret "ycpOKsMw+SpDuRpYl34GGHVDnW9or6Cvf0zGy+J8iCzCnd9YtbWUnuh8bjTRoZqh3U0yP9wwJ6URRU3ammWx11RGWN3q5o3Gq5jutuddIUtBeNVEYY/VB92SMX0vZO0jLrLSTXf249SNY4VVL+VdPDyJXuVlYKLfc/ORXgeNOeg")
(def max-page-limit 5)

(def recaptcha-project-id "vbv-dhana-bookin-1678544627785")
(def recaptcha-site-key "6LeX6_AkAAAAAIphqFGGpVAF1VJoENGmJajlzucx")
(def recaptcha-api-key "AIzaSyBVfue2zrjPq28cFRNxgR7WZtfZlyagk10")

(defn recaptcha-assessment [token action]
  (get
   (client/post
    (str "https://recaptchaenterprise.googleapis.com/v1/projects/" recaptcha-project-id "/assessments")
    {:query-params {:key recaptcha-api-key}
     :content-type :json
     :as :json
     :body (str "{\"event\": {\"token\": \"" token
                "\", \"siteKey\": \"" recaptcha-site-key
                "\", \"expectedAction\": \"" action "\"}}")})
   :body))

(defn is-human? [token action]
  (let [assesment-response (recaptcha-assessment token action)]
    (println (assesment-response :riskAnalysis))
    (-> assesment-response
        :riskAnalysis
        :score
        (= 0.9))))

(defn get-error-fields [spec data]
  (-> (m/explain spec data)
      (me/humanize)
      :humanized))

(def user-routes
  [["" {:swagger {:tags [:Person]}
        :coercion reitit.coercion.malli/coercion}
    ["/user/log-in"
     {:post {:summary "Log in"
             :swagger {:operationId :log-in-post}
             :parameters {:body scm/log-in #_:person-spec/log-in-post}
             :handler (fn [{{:keys [email password mobile-phone captcha-token]} :body-params}]
                        (if-not (is-human? captcha-token "log_in")
                          {:body {:status 403
                                  :message "Captcha test failed"}}
                          (let [person (if (some? email)
                                         (dbfns/person-by-email db {:email email})
                                         (if (some? mobile-phone)
                                           (dbfns/person-by-mobile-phone db {:mobile-phone mobile-phone})
                                           nil))
                                sub-person (select-keys person [:id
                                                                :first-name
                                                                :last-name
                                                                :email
                                                                :mobile-phone
                                                                :permitted-tasks])
                                formatter (t/formatter "E, dd MMM yyyy HH:mm:ss z")
                                token-expiry (t/format formatter (-> (t/>> (t/instant (t/now))
                                                                           (t/new-duration 30 :minutes))
                                                                     (t/in "GMT")))]
                            (if (and (some? person)
                                     (-> password
                                         (hashers/verify (:password person))
                                         :valid))
                              {:status 200
                               :body {:status 200
                                      :body (merge sub-person
                                                   {:auth-token (jwt/sign
                                                                 (assoc sub-person :expiry token-expiry)
                                                                 signing-secret)
                                                    :expiry token-expiry})}}
                              {:status 422
                               :body {:status 422
                                      :message "Malformed entity"
                                      :error-fields (get-error-fields scm/person person)}}))))}}]
    [ "/user/cookie-log-in"
     ;; When the user refreshes the page, the browser state must get user information back.
     ;; This route allows exchanging a valid cookie for user information.
     {:post {:summary "Cookie Log in"
              :swagger {:operationId :cookie-log-in-post}
              :handler (fn [{user :custom-auth}]
                          (print :custom-auth)(pprint user)
                          (let [person (dbfns/person-by-id db {:id (:id user)})
                                 sub-person (select-keys person [:id
                                                                  :first-name
                                                                  :last-name
                                                                  :email
                                                                  :mobile-phone
                                                                  :permitted-tasks])]
                             {:status 200
                               :body {:status 200
                                       :body (merge sub-person
                                                     {:auth-token (:auth-token user)
                                                       :expiry (:expiry user)})}}))}}]]])

(def person-routes
  [["" {:swagger {:tags [:Person]}
        :coercion reitit.coercion.malli/coercion}

    ["/person"
     {:post {:summary "Create Person"
             :swagger {:operationId :person-post}
             :parameters {:body scm/person-create #_:person-spec/person-create}
             :handler (fn [{person-post :body-params}]
                        (if-not (is-human? (get person-post :captcha-token) "register")
                          {:status 412
                           :body {:status 412
                                  :message "Preconditions Failed: Request couldn't be verified as originated from a Human."}}

                         (let [password-hash (hashers/derive (person-post :password1))
                               person (merge person-post {:can-log-in false
                                                          :verified false
                                                          :deleted false
                                                          :permitted-tasks (join "," [(name :log-in)
                                                                                      (name :request-appointment)])
                                                          :password password-hash})

                               person-by-email (dbfns/person-by-email db {:email (person :email)})
                               person-by-mobile-phone (dbfns/person-by-mobile-phone db {:mobile-phone (person :mobile-phone)})]

                           (cond

                             person-by-email
                             {:status 409
                              :body {:status 409
                                     :message (str "An account already exists with the given email address: " (:email person) ".")
                                     :errors {:email ["Email address is already in use."]}}}

                             person-by-mobile-phone
                             {:status 409
                              :body {:status 409
                                     :message (str "An account already exists with the given mobile phone number" (:email person) ".")
                                     :errors {:mobile-phone ["Mobile Phone Number is already in use."]}}}


                             (m/validate scm/person-create person-post)
                             (do (dbfns/insert-person db person)
                               {:status 201 #_created
                                :body {:test "test"}})

                             :else
                             {:status 422
                              :body {:status 422
                                     :message "Malformed entity"
                                     :error-fields (get-error-fields scm/person person)}}))))}




;curl -X GET -vvv 'http://localhost:3000/person?limit=5&page=1'
      :get {:summary "Get Person List"
            :swagger {:operationId :person-list}
            :responses {};200 {:body [:* scm/person-list-item]}}
            :parameters {:query [:map [:page :int] [:limit :int]]} ; #_(s/keys :req-un [::specs/page ::specs/limit])
            :handler (fn [{{{:keys [page limit]} :query} :parameters}]
                       (let [persons (dbfns/list-person db {:limit limit
                                                            :offset (-> page (- 1) (* limit))})]
                         {:status (if (empty? persons) 204 200)
                          :body (map #(dissoc % :password :otp :created-at) persons)}))}}]

    ["/person/:person-id"
     {:put {:summary "Update Person"
            :swagger {:operationId :person-put}
            :parameters {:path {:person-id :int #_:person-spec/id}
                         :body scm/person-update #_:person-spec/person-update}
            :coercion reitit.coercion.malli/coercion
            :handler (fn [{person :body-params
                           {:keys [person-id]} :path-params}]

                       (if (m/validate scm/person-update person) #_(s/valid? :person-spec/person-update person)
                         (do
                           (dbfns/clj-expr-generic-update
                            db {:table "person"
                                :updates (select-keys person [:first-name
                                                              :last-name
                                                              :post-code])
                                :id person-id})
                           {:status 200
                            :body {:test "test"}})
                         {:status 422
                          :body {:status 422
                                 :message "Malformed entity"
                                 :error-fields (get-error-fields scm/person person)}}))}
      :get {:summary "Get Person Resource"
            :swagger {:operationId :person-get}
            :parameters {:path [:map [:person-id {:optional true} :int]]}
            :responses {200 {:body scm/person-edit-form-fill}
                        404 {:body :string}}
            :handler (fn [{{{:keys [person-id]} :path} :parameters}]
                       (let [person (dbfns/person-by-id db {:id person-id})
                             person2 (-> person
                                         (dissoc :otp)
                                         (dissoc :password)
                                         (dissoc :permitted-tasks)
                                         (dissoc :created-at)
                                         (dissoc :verified))]
                         (print :get>>)
                         (pprint person2)
                         (cond
                           (nil? person) {:status 404 :body "404 Not Found"}
                           (m/validate scm/person-edit-form-fill person) {:status 200 :body person2})))}}]
                           

    ["/person/:person-id/password"
     {:put {:summary "Change Password"
            :swagger {:operationId :person-password-put}
            :parameters {:path {:person-id :int}
                         :body scm/person-password-update}
            :coercion reitit.coercion.malli/coercion
            :handler (fn [{{:keys [password1 password2 otp old-password]} :body-params
                           {:keys [person-id]} :path-params}]
                       (let [person (dbfns/person-by-id db {:id person-id})]
                         (if (and (= password1 password2)
                                  (some? person)
                                  (or (and (some? otp)
                                           (-> otp
                                               (hashers/verify (:otp person))
                                               :valid))
                                      (and (some? old-password)
                                           (-> old-password
                                               (hashers/verify (:password person))
                                               :valid))))
                           (do
                             (dbfns/clj-expr-generic-update
                              db {:table "person"
                                  :updates {:password (hashers/derive password1)}
                                  :id person-id})
                             {:status 200
                              :body {:test "test"}})
                           {:status 422
                            :body {:status 422
                                   :message "Malformed entity"
                                   :error-fields (get-error-fields scm/person person)}})))}}]

    ["/person/password/reset-request"
     {:post {:summary "Password reset request"
             :swagger {:operationId :person-password-reset}
             :parameters {:body scm/person-password-reset}
             :coercion reitit.coercion.malli/coercion
             :handler (fn [{{:keys [email mobile-phone]} :body-params}]
                        (let [otp (str (java.util.UUID/randomUUID))
                              person (if (some? email)
                                       (dbfns/person-by-email db {:email email})
                                       (if (some? mobile-phone)
                                         (dbfns/person-by-email db {:mobile-phone mobile-phone})
                                         nil))]
                          (println otp)
                          (if (some? person)
                            (do
                              (dbfns/clj-expr-generic-update
                               db {:table "person"
                                   :updates {:otp (hashers/derive otp)}
                                   :id (:id person)})
                              (if (some? email)
                                (println :todo :email)
                                (when (some? mobile-phone)
                                  (println :todo :mobile-phone)))
                              {:status 202
                               :body {:test "test"}})
                            {:status 422
                             :body {:status 422
                                    :message "Malformed entity"
                                    :error-fields (get-error-fields scm/person person)}})))}}]]])

(def calendar-routes
  [["" {:swagger {:tags [:Person]}
        :coercion reitit.coercion.malli/coercion}
    ["/calendar/booking-request"
     {:post {:summary "Create Booking Request"
             :swagger {:operationId :booking-request-post}
             :parameters {:body scm/booking-request-post}
             :handler (fn [{booking-request-post :body-params
                            user :custom-auth}]

                        (let [booking-request (-> booking-request-post
                                                  (assoc :requested-by (:id user))
                                                  (assoc :approved-by nil)
                                                  (assoc :status "REQUESTED"))]
                          (if (m/validate scm/booking-request-post booking-request-post)

                            (if-let [booking-insert (dbfns/insert-appointment-request db booking-request)] ;TODO naming consistency
                              (do
                               (dbfns/insert-appointment-slot-event db
                                {:apt-date (:apt-date booking-request)
                                 :slot-name (str (:apt-date booking-request) "-"
                                                 (:booking-option booking-request))
                                 :event-type "create-booking-request"
                                 :event-detail ""
                                 :user-comments (:requesters-comments booking-request)
                                 :subject-id (:id booking-insert)
                                 :user-id (:id user)})

                               {:status 201 #_created
                                 :body {:booking-id (:id booking-insert)
                                        :apt-date (:apt-date booking-request)
                                        :booking-option (:booking-option booking-request)}})
                              {:status 500 #_created
                                :body {:message "Request was unsuccessful."}})

                            {:status 422
                             :body {:status 422
                                    :message "Malformed entity"
                                    :error-fields (get-error-fields scm/booking-request booking-request)}})))}

      :get {:summary "Get Booking Request List"
            :swagger {:operationId :booking-request-list}
            :responses {200 {:body [:* scm/booking-request]}}
            :parameters {:query [:map [:from-date :string][:to-date :string]]}
            :handler (fn [{{{:keys [from-date to-date]} :query} :parameters}] ;todo limit duration
                       (let [booking-requests (dbfns/list-booking-request db {:from-date (str from-date)
                                                                              :to-date (str to-date)})]
                         {:status (if (empty? booking-requests) 204 200)
                          :body (map (fn [b] (update b :apt-date #(-> % str (subs 0 10)))) ;TODO handle dates properly
                                     booking-requests)}))}}]

    ["/calendar/booking-request-with-person-detail/:request-id"
     {:get {:summary "Get Booking Request with Person Detail"
            :swagger {:operationId :booking-request-with-person-detail-get}
            :responses {200 {:body scm/booking-request-with-person-detail}}
            :parameters {:path {:request-id :int}}
            :handler (fn [{{{request-id :request-id} :path} :parameters}] ;todo limit duration
                       (let [booking-request (dbfns/get-booking-request-with-person db {:request-id request-id})]
                         (pprint :>>>>>booking-request)
                         (pprint booking-request)
                         {:status (if (empty? booking-request) 204 200)
                          :body (update booking-request :apt-date #(-> % str (subs 0 10)))}))}}]

    ["/calendar/booking-request-with-person-detail"
     {:get {:summary "Get Booking Request List with Person Detail"
            :swagger {:operationId :booking-request-list-with-person-detail}
            :responses {200 {:body [:* scm/booking-request-with-person-detail]}}
            :parameters {:query [:map [:from-date :string][:to-date :string]]}
            :handler (fn [{{{:keys [from-date to-date]} :query} :parameters}] ;todo limit duration
                       (let [booking-requests (dbfns/list-booking-request-with-person db {:from-date (str from-date)
                                                                                          :to-date (str to-date)})]
                         {:status (if (empty? booking-requests) 204 200)
                          :body (map (fn [b] (update b :apt-date #(-> % str (subs 0 10)))) ;TODO handle dates properly
                                     booking-requests)}))}}]
    ["/calendar/booking-request-approval"
     {:post {:summary "Approve/Reject Booking Request"
             :swagger {:operationId :booking-request-approval}
             :parameters {:body scm/booking-request-approval}
             :handler (fn [{approval :body-params
                            user :custom-auth}]

                        (let [booking-request (dbfns/get-booking-request-with-person
                                               db {:request-id (:request-id approval)})
                              approved-bookings (dbfns/get-booking-requests-by-date-n-status-n-booking-option
                                                 db {:apt-date (:apt-date booking-request)
                                                     :booking-option (booking-request :booking-option)
                                                     :status "APPROVED"})
                              approve-fn #(dbfns/clj-expr-generic-update db
                                           {:table "appointment_request"
                                            :updates {:approved-by (:id user)
                                                      :approvers-comments (:approvers-comments approval)
                                                      :status (-> approval :action name upper-case)}
                                            :id (:request-id approval)})]
                          (cond

                            (and (pos? (count approved-bookings))
                                 (= (-> approval :action upper-case) "APPROVED"))
                            {:status 409
                             :body {:status 409
                                    :message "Approved booking exists for the date."}}

                            (approve-fn)
                            (do
                              (try
                                (dbfns/insert-appointment-slot-event db
                                 {:apt-date (:apt-date booking-request)
                                  :slot-name (str (:apt-date booking-request) "-"
                                                 (:booking-option booking-request))
                                  :event-type (-> approval :action name upper-case)
                                  :event-detail ""
                                  :user-comments (:approvers-comments approval)
                                  :subject-id (:request-id approval)
                                  :user-id (:id user)})
                                (catch Exception e (str "caught exception: " (.getMessage e))))

                              {:status 200 #_created
                               :body {:id (:request-id approval)
                                      :request-status (-> approval :action name upper-case)}})

                            :else
                            {:status 422
                             :body {:status 422
                                    :message "Malformed entity"}})))}}]]])

(def swagger-routes
  ["" {:no-doc true}
   ["/swagger.json" {:get (swagger/create-swagger-handler)}]
   ["/api-docs/*" {:get (swagger-ui/create-swagger-ui-handler)}]])
(defn wrap-custom-auth [handler]
  (println :wrap-custom-auth)
  (fn [request]
    (let [auth-token (get-in request [:cookies "auth-token" :value])]
      (handler (if (some? auth-token)
                 (-> request
                     (assoc :auth-token auth-token)
                     (assoc :custom-auth (jwt/unsign auth-token signing-secret))
                     (as-> r (update-in r [:custom-auth :permitted-tasks] #(map keyword (split % #",")))))
                 request)))))

(defn wrap-authorization [handler]
  (fn [request]
    (let [authenticated? (some? (get request :custom-auth))
          user-tasks (get-in request [:custom-auth :permitted-tasks])
          authorized?
          (case ((juxt #(get-in % [:reitit.core/match :template]) :request-method)
                 request)
            ["/api-docs/*" :get] true
            ["/swagger.json" :get] true
            ["/user/log-in" :post] true
            ["/person" :post] true
            ["/person" :get] (some #{:* :list-persons} user-tasks)

            (request :custom-auth))]
      (clojure.pprint/pprint (get request :custom-auth))
      (if authorized?
        (handler request)
        (handler request)
        #_{:status (if authenticated? 403 401)
           :bdoy {:message "User is not authorized to perform this task."}}))))

(defn wrap-error-messages [handler]
  (fn [request]
    (let [response (handler request)]
      (if (= (get-in response [:body :type])
             :reitit.coercion/request-coercion)
        (-> response
            (assoc :body {:errors (-> response :body :humanized)})
            (assoc :status 422))
        response))))

(def app
  (ring/ring-handler
   (ring/router
    [person-routes
     user-routes
     calendar-routes
     swagger-routes]
    {:data {:muuntaja mm/instance
            :middleware [wrap-params
                         wrap-cookies
                         wrap-keyword-params
                         wrap-custom-auth
                         wrap-authorization
                         muuntaja/format-middleware
                         wrap-error-messages
                         coercion/coerce-exceptions-middleware
                         coercion/coerce-request-middleware
                         coercion/coerce-response-middleware]}})




   (ring/create-default-handler)))

;curl -X POST http://localhost:3000/person \
;-H 'Content-Type: application/json' \
;-d '{"deleted":false,"email":"manawardhana@gmail.com","last-name":"Manawardhana","post-code":"2077","mobile-phone":"0457925280","password1":"secret","password2":"secret","can-log-in":false,"first-name":"Tharaka","id":1,"created-at":"2022-07-10T13:09:45Z","verified":true} '

;curl -X PUT http://localhost:3000/person/1 \
;-H 'Content-Type: application/json' \
;-d '{"deleted":false,"email":"manawardhana+1@gmail.com","last-name":"Manawardhana","post-code":"2000","mobile-phone":"0457925280","password1":"secret1","password2":"secret1","can-log-in":false,"first-name":"Tharaka","created-at":"2022-07-10T13:09:45Z","verified":true} '

;curl -X PUT http://localhost:3000/person/1/password \
;-H 'Content-Type: application/json' \
;-d '{"password1":"secret111","password2":"secret111","otp":"162d65bc-638b-4dd7-af7d-6cdd21481445"}'

;curl -X POST http://localhost:3000/person/password/reset-request \
;-H 'Content-Type: application/json' \
;-d '{"email":"manawardhana@gmail.com"}'

;curl -X POST http://localhost:3000/person/password/reset-request \
;-H 'Content-Type: application/json' \
;-d '{"mobile-phone":"0457925280"}'

;curl -X POST http://localhost:3000/user/log-in \
;-H 'Content-Type: application/json' \
;-d '{"email":"manawardhana@gmail.com", "password":"secret111"}'

;curl -X POST http://localhost:3000//calendar/booking-request \
;-H 'Cookie: auth-token=eyJhbGciOiJIUzI1NiJ9.eyJpZCI6MiwiZmlyc3QtbmFtZSI6IlRoYXJha2ExIiwibGFzdC1uYW1lIjoiTWFuYXdhcmRoYW5hMSIsImVtYWlsIjoibWFuYXdhcmRoYW5hKzFAZ21haWwuY29tIiwibW9iaWxlLXBob25lIjoiMDQ1NzkyNTI4MCIsImV4cGlyeSI6IlR1ZSwgMjEgRmViIDIwMjMgMTY6MjY6MzggR01UIn0.uxnFmnV8gszaYqtli74sPjLWwIQJBZv-Tad1BzAFPP8' \
;-H 'Content-Type: application/json' \
;-d '{"requested-by":1, "apt-date":"2023-03-27", "booking-option":"MORNING", "requesters-comments":"Please"}'

