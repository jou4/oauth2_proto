(ns client.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [ring.util.response :as response]
            [ring.middleware.session :as session]
            [clj-http.client :as client]
            [clj-http.util :as httputil]
            [clojure.data.json :as json]))

(def client_id "client_a")
(def client_secret "client_secret_a")

(def auth_uri "http://localhost:3000/auth")
(def auth2_uri "http://localhost:3000/auth2")
(def token_uri "http://localhost:3000/token")
(def api_uri "http://localhost:4000/api")
(def redirect_uri "http://localhost:5000/code")

(defn static-file [path]
  (response/file-response (str "public/" path)))

(defn access-token [req]
  (-> req :session :access_token))

(defn- gen-auth-uri [uri]
  (str uri
       "?response_type=code"
       "&client_id=" (httputil/url-encode client_id)
       "&redirect_uri=" (httputil/url-encode redirect_uri)
       "&scope=" (httputil/url-encode "USERINFO ENTRYPOST")
       "&state=" (httputil/url-encode "SAMPLE_STATE")))

(defn auth [req]
  (let [uri (gen-auth-uri auth_uri)]
    (response/redirect uri)))

(defn auth2 [req]
  (let [uri (gen-auth-uri auth2_uri)]
    (response/redirect uri)))

(defn api [req]
  (let [token (access-token req)
        res (client/get api_uri {:query-params {:access_token token}})]
    (res :body)))

(defn show-token [req]
  (let [token (access-token req)]
    (if (empty? token)
      "You have no AccessToken."
      (str "<h1>AccessToken: " token "</h1>")))) 

(defn show-session [req]
  (json/write-str (req :session)))

(defn receive-code [req]
  (let [code (-> req :params :code)
        state (-> req :params :state)]
    (println (str "receive code: " code " -- " state))
    (println "request token...")
    (let [res (client/post token_uri
                           {:query-params {:code code
                                           :client_id client_id
                                           :client_secret client_secret
                                           :redirect_uri redirect_uri
                                           :grant_type "authorization_code"}})
          body (json/read-str (res :body))
          token (body "access_token")
          refresh_token (body "refresh_token")
          expires_in (body "expires_in")
          token_type (body "token_type")]
      (let [session (conj (req :session) {:code code
                                          :state state
                                          :access_token token
                                          :refresh_token refresh_token
                                          :expires_in expires_in
                                          :token_type token_type})]
        (println (res :body))
        (println session)
        (-> (response/redirect "/")
          (assoc-in [:session] session))))))

(defroutes app-routes
  (GET "/" [] (static-file "top.html"))
  (GET "/auth" [] auth) 
  (GET "/auth2" [] auth2) 
  (GET "/api" [] api) 
  (GET "/token" [] show-token) 
  (GET "/session" [] show-session) 
  (GET "/code" [] receive-code) 
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
