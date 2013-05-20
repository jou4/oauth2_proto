(ns auth.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as response]
            [ring.middleware.session :as session]
            [clojure.data.json :as json]))

(defn static-file [path]
  (response/file-response (str "public/" path)))

(def clients
  [{:client_id "client_a" :client_secret "client_secret_a"}
   {:client_id "client_b" :client_secret "client_secret_b"}])

(def users
  [{:username "taro" :password "taro"}
   {:username "jiro" :password "jiro"}])

(def codes
  ["code_a" "code_b" "code_c"])

(def tokens
  [{:access_token "token_a" :expires_in 3600 :refresh_token "refresh_token_a"}
   {:access_token "token_b" :expires_in 3600 :refresh_token "refresh_token_b"}
   {:access_token "token_c" :expires_in 3600 :refresh_token "refresh_token_c"}])

(def issue-codes (ref []))
(def issue-tokens (ref []))

(defn rand-elem [coll]
  (let [i (rand-int (count coll))]
    (nth coll i)))

(defn rand-code []
  (rand-elem codes))

(defn rand-token []
  (rand-elem tokens))

(defn client-valid?
  [client_id client_secret]
  (not (empty? (filter #(and (= client_id (% :client_id))
                             (= client_secret (% :client_secret)))
                       clients)))
  [client_id]
  (not (empty? (filter #(= client_id (% :client_id))
                       clients))))

(defn login [req]
  (println "login -----------------")
  (println req)
  (let [params (req :params)
        session (req :session)
        username (params :username)
        password (params :password)
        u (first (filter #(and (= username (% :username))
                               (= password (% :password)))
                         users))]
    (if (empty? u)
      (response/redirect "/login")
      (-> (response/redirect (str "/" (session :auth_type)))
        (assoc-in [:session] session)
        (assoc-in [:session :username] username)))))

(defn login? [req]
  (not (empty? (:username (:session req)))))

(defn auth-req [req]
  (println "auth-req -----------------")
  (println req)
  (let [params (req :params)
        response_type (params :response_type)
        client_id (params :client_id)
        redirect_uri (params :redirect_uri)
        scope (params :scope)
        state (params :state)
        session (if (empty? client_id)
                  (req :session)
                  (conj (req :session) {:response_type response_type
                                        :client_id client_id
                                        :redirect_uri redirect_uri
                                        :scope scope
                                        :state state
                                        :auth_type "auth"}))]
    (if (login? req)
      (-> (static-file "grant.html")
              (assoc-in [:session] session))
      (-> (response/redirect "/login")
        (assoc-in [:session] session)))))

(def auth)

(defn auth-req2 [req]
  (println "auth-req2 -----------------")
  (println req)
  (let [params (req :params)
        response_type (params :response_type)
        client_id (params :client_id)
        redirect_uri (params :redirect_uri)
        scope (params :scope)
        state (params :state)
        session (if (empty? client_id)
                  (req :session)
                  (conj (req :session) {:response_type response_type
                                        :client_id client_id
                                        :redirect_uri redirect_uri
                                        :scope scope
                                        :state state
                                        :auth_type "auth2"}))]
    (if (login? req)
      (auth (-> req
              (assoc-in [:session] session)
              (assoc-in [:params :allow] "allow")))
      (-> (response/redirect "/login")
        (assoc-in [:session] session)))))

(defn auth [req]
  (println "auth -----------------")
  (println req)
  (let [allow ((req :params) :allow)
        session (req :session)
        redirect_uri (session :redirect_uri)
        state (session :state)
        code (rand-code)]
    (if (empty? allow)
      (response/redirect (str redirect_uri "?error=access_denied&state=" state))
      (do
        (dosync (alter issue-codes conj {:code code
                                         :client_id (session :client_id)
                                         :username (session :username)}))
        (response/redirect (str redirect_uri "?code=" code "&state=" state))))))

(defn lookup-issue-code [code]
  (first (filter #(= code (% :code)) @issue-codes)))

(defn lookup-issue-token [token]
  (first (filter #(= token (% :access_token)) @issue-tokens)))

(defn token [req]
  (println "token -----------------")
  (println req)
  (let [code (-> req :params :code)
        client_id (-> req :params :client_id)
        client_secret (-> req :params :client_secret)
        redirect_uri (-> req :params :redirect_uri)
        grant_type (-> req :params :grant_type)]
    (println (str "/token " client_id " " client_secret))
    ; authenticate the client
    (if-not (client-valid? client_id client_secret)
      (json/write-str {:error "invalid_client"})
      ; authorization code is valid?
      (let [issue-code (lookup-issue-code code)]
        (if (or (empty? issue-code) (not (= client_id (issue-code :client_id))))
          (json/write-str {:error "invalid_grant"})
          (let [token (rand-token)]
            (dosync (alter issue-tokens conj (conj issue-code token)))
            (json/write-str (conj token {:token_type "Bearer"}))))))))

(defn token-valid [req]
  (println "token-valid -----------------")
  (println req)
  (let [access_token (-> req :params :access_token)
        issue-token (lookup-issue-token access_token)]
    (if (empty? issue-token)
      (json/write-str {:error "invalid_grant"})
      (json/write-str {:username (issue-token :username)}))))

(defn show-issue-codes [req]
  (json/write-str @issue-codes))
(defn show-issue-tokens [req]
  (json/write-str @issue-tokens))
(defn clear-issue-codes [req]
  (dosync (ref-set issue-codes []))
  (show-issue-codes req))
(defn clear-issue-tokens [req]
  (dosync (ref-set issue-tokens []))
  (show-issue-tokens req))

(defroutes app-routes
  (GET  "/" [] (response/redirect "/auth"))
  (GET  "/auth" [] auth-req)
  (GET  "/auth2" [] auth-req2)
  (POST "/auth" [] auth)
  (GET  "/login" [] (static-file "login.html"))
  (POST "/login" [] login)
  (GET  "/token" [] token)
  (POST "/token" [] token)
  (GET  "/token_valid" [] token-valid)
  (GET  "/issue_codes" [] show-issue-codes)
  (GET  "/issue_codes_clear" [] clear-issue-codes)
  (GET  "/issue_tokens" [] show-issue-tokens)
  (GET  "/issue_tokens_clear" [] clear-issue-tokens)
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (handler/site app-routes)
    session/wrap-session))
