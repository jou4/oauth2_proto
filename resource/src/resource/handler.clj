(ns resource.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.string :as string]
            [clj-http.client :as client]
            [clojure.data.json :as json]))

(def token_valid_uri "http://localhost:3000/token_valid")

(defn access-token [req]
  (let [token (-> req :params :access_token)]
    (if (empty? token)
      (let [auth ((req :headers) "authorization")
            tmp (if (empty? auth) [] (string/split auth #"\s+"))]
        (if (= 2 (count tmp))
          (nth tmp 1)
          nil))
      token)))

(defn query-token [token]
  (let [res (client/get token_valid_uri {:query-params {:access_token token}})]
    (json/read-str (res :body))))

(defn api [req]
  (let [token (access-token req)]
    (if (empty? token)
      (json/write-str {:error "access_denied"})
      (let [result (query-token token)]
        (if (contains? result "error")
          (json/write-str {:error "invalid_access_token"})
          (json/write-str result))))))

(defroutes app-routes
  (GET "/api" [] api)
  (GET "/token" [] access-token)
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
