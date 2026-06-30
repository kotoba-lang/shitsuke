(ns shitsuke.style-test
  (:require [clojure.test :refer [deftest is testing]]
            [shitsuke.style :as s]
            [shitsuke.hiccup :as h]))

(deftest class-name-test
  (is (= "shitsuke__button" (s/class-name :button)))
  (is (= "shitsuke__mode-tabs" (s/class-name :mode-tabs))))

(deftest root-css-test
  (let [css (s/root-css)]
    (is (clojure.string/starts-with? css ":root {"))
    (is (clojure.string/includes? css "--shitsuke-colors-ink:"))))

(deftest inline-style-test
  (let [tag (s/inline-style)]
    (is (clojure.string/starts-with? tag "<style>"))
    (is (clojure.string/ends-with? tag "</style>"))))

(deftest inline-style-hiccup-renders-test
  "The raw CSS must survive ->html unescaped (it is trusted markup)."
  (let [html (h/->html (s/inline-style-hiccup (s/root-css)))]
    (is (clojure.string/starts-with? html "<style>"))
    (is (clojure.string/includes? html "--shitsuke-colors-ink:"))
    (is (not (clojure.string/includes? html "&amp;")))))
