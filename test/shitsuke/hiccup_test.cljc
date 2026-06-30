(ns shitsuke.hiccup-test
  (:require [clojure.test :refer [deftest is testing]]
            [shitsuke.hiccup :as h]))

(deftest esc-test
  (is (= "&amp;&lt;&gt;&quot;" (h/esc "&<>\"")))
  (is (= "plain" (h/esc "plain")))
  (is (= "42" (h/esc 42))))

(deftest tag-sugar-test
  (is (= "<div class=\"a b\" id=\"x\"></div>" (h/->html [:div.a.b#x])))
  (is (= "<span class=\"y\">hi</span>" (h/->html [:span.y "hi"])))
  (is (= "<p></p>" (h/->html [:p]))))

(deftest attrs-test
  (is (= "<a href=\"https://x\">l</a>" (h/->html [:a {:href "https://x"} "l"])))
  (is (= "<input disabled>" (h/->html [:input {:disabled true}])))
  (is (= "<input>" (h/->html [:input {:disabled false}])))
  (testing "class as vec"
    (is (= "<div class=\"a b\">n</div>" (h/->html [:div {:class ["a" "b"]} "n"]))))
  (testing "tag-sugar class merges with attr :class"
    (is (= "<div class=\"a c\">n</div>" (h/->html [:div.a {:class "c"} "n"])))))

(deftest void-tags-test
  (is (= "<img src=\"p.png\">" (h/->html [:img {:src "p.png"}])))
  (is (= "<br>" (h/->html [:br]))))

(deftest raw-test
  (is (= "<svg/>" (h/->html [:hiccup/raw "<svg/>"]))))

(deftest seq-and-nil-test
  (is (= "<p>a</p><p>b</p>" (h/->html [[:p "a"] [:p "b"]]))) ; seq of nodes at top
  (is (= "" (h/->html nil)))
  (is (= "<ul><li>1</li></ul>" (h/->html [:ul (list [:li "1"])]))))

(deftest escaping-test
  (is (= "<p>1 &lt; 2</p>" (h/->html [:p "1 < 2"])))
  (is (= "<p>42</p>" (h/->html [:p 42]))))

(deftest ssr-parity-test
  "Same hiccup data renders to a stable HTML string — the SSR twin of the
  reagent view contract. If this changes, the component snapshots change too."
  (let [view [:div.card [:button {:data-act "go"} "Go"] [:p "hi & bye"]]]
    (is (= "<div class=\"card\"><button data-act=\"go\">Go</button><p>hi &amp; bye</p></div>"
           (h/->html view)))))

(deftest style-map-test
  "reagent :style map renders to a CSS string for SSR (dual-render style support)."
  (is (= "<div style=\"font-size:10px;color:#fff;\"></div>"
         (h/->html [:div {:style {:font-size "10px" :color "#fff"}}])))
  (testing "string style still passes through"
    (is (= "<div style=\"color:red\"></div>"
           (h/->html [:div {:style "color:red"}]))))
  (testing "nil/false props in style map are skipped"
    (is (= "<div style=\"color:#fff;\"></div>"
           (h/->html [:div {:style {:color "#fff" :font-size nil :display false}}])))))
