(ns shitsuke.components-test
  (:require [clojure.test :refer [deftest is testing]]
            [shitsuke.components :as c]
            [shitsuke.hiccup :as h]))

(defn html [hic] (h/->html hic))

(deftest button-test
  (is (= "<button class=\"shitsuke__button\" type=\"button\" data-act=\"go\">Go</button>"
         (html (c/button "Go" {:act :go}))))
  (testing "namespaced act preserves the namespace"
    (is (= "<button class=\"shitsuke__button\" type=\"button\" data-act=\"cart/add\">Add</button>"
           (html (c/button "Add" {:act :cart/add})))))
  (testing "disabled true emits bare attribute"
    (is (= "<button class=\"shitsuke__button\" type=\"button\" disabled>Go</button>"
           (html (c/button "Go" {:disabled true}))))
    (is (= "<button class=\"shitsuke__button\" type=\"button\">Go</button>"
           (html (c/button "Go" {:disabled false}))))))

(deftest field-test
  (let [v (c/field "Name" (c/input {:id "n"}) {:for-id "n"})]
    (is (= "<div class=\"shitsuke__field\"><label for=\"n\">Name</label><input id=\"n\" class=\"shitsuke__input\" type=\"text\" value=\"\"></div>"
           (html v)))))

(deftest textarea-test
  (is (= "<textarea id=\"t\" class=\"shitsuke__textarea\" rows=\"6\">hello</textarea>"
         (html (c/textarea {:id "t" :value "hello"})))))

(deftest select-test
  (let [v (c/select [["a" "A"] ["b" "B"]] {:value "a"})]
    (is (= "<select class=\"shitsuke__select\"><option value=\"a\" selected>A</option><option value=\"b\">B</option></select>"
           (html v)))))

(deftest mode-tabs-test
  (let [v (c/mode-tabs [[:visual "Visual"] [:edn "EDN"]] :visual)]
    (is (clojure.string/includes? (html v) "class=\"shitsuke__tab shitsuke__tab--active\""))
    (is (clojure.string/includes? (html v) "data-act=\"visual\""))))

(deftest thumb-test
  (is (clojure.string/includes? (html (c/thumb "p" true)) "shitsuke__thumb shitsuke__thumb--active"))
  (is (clojure.string/includes? (html (c/thumb "p" false {:act :sel})) "data-act=\"sel\"")))

(deftest pane-test
  (is (clojure.string/includes? (html (c/pane true "x")) "hidden"))
  (is (not (clojure.string/includes? (html (c/pane false "x")) "hidden"))))

(deftest card-test
  (is (= "<section class=\"shitsuke__card\">body</section>"
         (html (c/card "body")))))
