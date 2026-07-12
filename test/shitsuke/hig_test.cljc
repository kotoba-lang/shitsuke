(ns shitsuke.hig-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [shitsuke.hig :as hig]))

(deftest appearance-completeness-test
  (testing "every semantic color has both light and dark values"
    (doseq [[token v] hig/semantic-colors]
      (is (string? (:light v)) (str token " missing :light"))
      (is (string? (:dark v)) (str token " missing :dark"))))
  (testing "every palette entry has both light and dark values"
    (doseq [[token v] hig/system-palette]
      (is (string? (:light v)) (str token " missing :light"))
      (is (string? (:dark v)) (str token " missing :dark"))))
  (testing "dark override map covers exactly the light color/palette tokens"
    (is (= (set (keys (get hig/default-hig-tokens :hig/color)))
           (set (keys (get hig/dark-hig-tokens :hig/color)))))
    (is (= (set (keys (get hig/default-hig-tokens :hig/palette)))
           (set (keys (get hig/dark-hig-tokens :hig/palette)))))))

(deftest text-styles-test
  (testing "all 11 Apple text styles present"
    (is (= 11 (count hig/text-styles)))
    (is (= (set hig/text-style-order) (set (keys hig/text-styles)))))
  (testing "display stack for >= 20px, text stack below"
    (is (str/includes? (get-in hig/default-hig-tokens [:hig/text :title3 :font-family])
                       "SF Pro Display"))
    (is (str/includes? (get-in hig/default-hig-tokens [:hig/text :headline :font-family])
                       "SF Pro Text"))))

(deftest css-variables-test
  (let [css (hig/css-variables)]
    (is (str/starts-with? css ":root {"))
    (is (str/includes? css "--hig-color-label: #000000;"))
    (is (str/includes? css "--hig-color-tint: #007AFF;"))
    (is (str/includes? css "--hig-palette-indigo: #5856D6;"))
    (is (str/includes? css "--hig-text-body-font-size: 17px;"))
    (is (str/includes? css "--hig-text-large-title-line-height: 41px;"))
    (is (str/includes? css "--hig-spacing-content-margin: 16px;"))
    (is (str/includes? css "--hig-radius-capsule: 999px;"))
    (is (str/includes? css "--hig-hairline: 0.5px;"))))

(deftest dark-css-variables-test
  (let [css (hig/dark-css-variables)]
    (testing "media query + both forced-appearance blocks"
      (is (str/includes? css "@media (prefers-color-scheme: dark)"))
      (is (str/includes? css ":root[data-appearance=\"dark\"]"))
      (is (str/includes? css ":root[data-appearance=\"light\"]")))
    (testing "dark values emitted"
      (is (str/includes? css "--hig-color-label: #FFFFFF;"))
      (is (str/includes? css "--hig-color-tint: #0A84FF;"))
      (is (str/includes? css "--hig-palette-green: #30D158;")))
    (testing "forced-light block resets to light values"
      (is (str/includes? css "--hig-color-label: #000000;")))))

(deftest resolve-overrides-test
  (testing "resolve-hig-tokens honors overrides, keeps other defaults"
    (let [r (hig/resolve-hig-tokens {:hig/color {:tint "#FF00AA"}})]
      (is (= "#FF00AA" (get-in r [:hig/color :tint])))
      (is (= "#000000" (get-in r [:hig/color :label])))
      (is (= "4px" (get-in r [:hig/spacing :1])))))
  (testing "resolve-dark-hig-tokens = defaults + dark + overrides"
    (let [r (hig/resolve-dark-hig-tokens nil)]
      (is (= "#FFFFFF" (get-in r [:hig/color :label])))
      (is (= "0.5px" (:hig/hairline r))))
    (let [r (hig/resolve-dark-hig-tokens {:hig/color {:tint "#123456"}})]
      (is (= "#123456" (get-in r [:hig/color :tint])))))
  (testing "overrides flow into emitted CSS"
    (is (str/includes? (hig/css-variables {:hig/color {:tint "#FF00AA"}})
                       "--hig-color-tint: #FF00AA;"))
    (is (str/includes? (hig/dark-css-variables {:hig/color {:tint "#123456"}})
                       "--hig-color-tint: #123456;"))))

(deftest base-css-test
  (let [css (hig/base-css)]
    (is (str/starts-with? css "@layer kotoba.hig {"))
    (is (str/includes? css "color-scheme: light dark;"))
    (is (str/includes? css "background: var(--hig-color-system-background);"))
    (is (str/includes? css "color: var(--hig-color-label);"))
    (is (str/includes? css "-webkit-font-smoothing: antialiased;"))
    (is (str/includes? css "margin: 0 0 12px;"))
    (is (str/includes? css "border-top: var(--hig-hairline) solid var(--hig-color-separator);"))
    (is (str/includes? css "color-mix(in srgb, var(--hig-color-tint) 25%, transparent)"))
    (is (str/includes? css "outline: 2px solid var(--hig-color-tint);"))
    (is (str/includes? css "@media (prefers-reduced-motion: reduce)"))))

(deftest text-style-classes-test
  (let [css hig/text-style-classes]
    (is (str/starts-with? css "@layer kotoba.hig {"))
    (doseq [style hig/text-style-order]
      (is (str/includes? css (str ".hig-" (name style) " {"))
          (str "missing class for " style)))
    (is (str/includes? css "font-size: var(--hig-text-caption2-font-size);"))))

(deftest hig-css-bundle-test
  (let [css (hig/hig-css)]
    (testing "starts with the cascade-layer order declaration"
      (is (str/starts-with? css "@layer kotoba.hig, kotoba.glass;")))
    (testing "contains vars, dark blocks, base CSS, and utility classes"
      (is (str/includes? css "--hig-color-tint: #007AFF;"))
      (is (str/includes? css "@media (prefers-color-scheme: dark)"))
      (is (str/includes? css ":root[data-appearance=\"light\"]"))
      (is (str/includes? css "text-rendering: optimizeLegibility;"))
      (is (str/includes? css ".hig-large-title {")))))

(deftest inline-style-test
  (is (str/starts-with? (hig/inline-style "x") "<style>"))
  (is (str/includes? (hig/inline-style) "@layer kotoba.hig, kotoba.glass;"))
  (is (= [:style [:hiccup/raw "x"]] (hig/inline-style-hiccup "x"))))
