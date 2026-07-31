(ns shitsuke.kotoba-parity-test
  "Byte-equality gate between shitsuke.tokens / shitsuke.hig and their
  `.kotoba` form-A ports (kotoba/tokens_core.kotoba, kotoba/hig_core.kotoba),
  the third step of the design-system migration in ADR-2607270100 section 10
  (css → html → shitsuke → liquid-glass-ui → kotoba-ui).

  Dual-render seams (reagent/re-frame) stay on the .cljc host side — only the
  pure token → CSS-string pipeline is gated here.

  The port is compiled and executed through the KIR interpreter in this same
  JVM. Each case is a zero-argument `.kotoba` function; no typed value is
  marshalled from Clojure. Map walks are key-sorted on both sides.

  T5.2: multi-arg pure folded into guest records; cases call via record-new."
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]
            [kotoba.compiler.core :as compiler]
            [kotoba.kir :as ir]
            [shitsuke.hig :as hig]
            [shitsuke.tokens :as tokens]))

(def tokens-source (slurp "kotoba/tokens_core.kotoba"))
(def hig-source (slurp "kotoba/hig_core.kotoba"))

(defn- kotoba-literal [s]
  (str \" (-> s (str/replace "\\" "\\\\") (str/replace "\"" "\\\"")) \"))

(defn- compile-cases
  [port-source cases]
  (let [defs (for [[name body] cases]
               (str "(defn " name " [] :string " body ")"))
        kir (:kir (compiler/compile-source
                   (str port-source "\n" (str/join "\n" defs)) :wasm32-kotoba-v1 {}))]
    (into {} (map (fn [[name _]] [name (ir/execute kir (symbol name) [])]) cases))))

(defn- typed-map-literal [m]
  (str "(typed-map-new [:map :keyword :string] "
       (str/join " " (mapcat (fn [[k v]] [(pr-str k) (kotoba-literal (str v))])
                             (sort-by (comp str key) m)))
       ")"))

(defn- tok-group-css [group m]
  (str "(group-css (record-new [:ref :tok/group-css] "
       (kotoba-literal group) " " (typed-map-literal m) "))"))

(defn- tok-nested-decl [group k prop value]
  (str "(nested-decl (record-new [:ref :tok/nested-decl] "
       (kotoba-literal group) " " (kotoba-literal k) " "
       (kotoba-literal prop) " " (kotoba-literal value) "))"))

(defn- hig-group-css [group m]
  (str "(group-css (record-new [:ref :hig/group-css] "
       (kotoba-literal group) " " (typed-map-literal m) "))"))

(defn- hig-nested-css [group k props]
  (str "(nested-css (record-new [:ref :hig/nested-css] "
       (kotoba-literal group) " " (kotoba-literal k) " "
       (typed-map-literal props) "))"))

(defn- cljc-scalar-group-css
  "Key-sorted reconstruction of shitsuke.tokens/pair->css for scalar groups."
  [prefix group m]
  (->> (sort-by (comp str key) m)
       (map (fn [[k v]]
              (str "  --" prefix "-" (name group) "-" (name k) ": " v ";")))
       (str/join "\n")))

(defn- cljc-nested-css
  "Key-sorted reconstruction of nested pair->css (type / text styles)."
  [prefix group k props]
  (->> (sort-by (comp str key) props)
       (map (fn [[pk pv]]
              (str "  --" prefix "-" (name group) "-" (name k) "-" (name pk) ": " pv ";")))
       (str/join "\n")))

;; --- tokens ---------------------------------------------------------------

(deftest tokens-normalize-hex-matches
  (let [actual (compile-cases tokens-source
                              {"n1" "(normalize-hex \"496B9A\")"
                               "n2" "(normalize-hex \"#496B9A\")"
                               "n3" "(normalize-hex \"FFFFFF\")"})]
    (is (= (tokens/normalize-hex "496B9A") (get actual "n1")))
    (is (= (tokens/normalize-hex "#496B9A") (get actual "n2")))
    (is (= (tokens/normalize-hex "FFFFFF") (get actual "n3")))))

(deftest tokens-scalar-group-is-byte-identical
  (let [colors (into (sorted-map) (get tokens/default-tokens :shitsuke/colors))
        spacing (into (sorted-map) (get tokens/default-tokens :shitsuke/spacing))
        breakpoints (into (sorted-map) (get tokens/default-tokens :shitsuke/breakpoints))
        actual (compile-cases tokens-source
                              {"colors" (tok-group-css "colors" colors)
                               "spacing" (tok-group-css "spacing" spacing)
                               "bp" (tok-group-css "breakpoints" breakpoints)
                               "root" (str "(root-css " (tok-group-css "colors" colors) ")")
                               "c_var" "(css-var-name (record-new [:ref :tok/css-var-name] \"colors\" \"ink\"))"
                               "c_decl" "(scalar-decl (record-new [:ref :tok/scalar-decl] \"colors\" \"ink\" \"#17202A\"))"})]
    (testing "css-var-name / scalar-decl"
      (is (= "--shitsuke-colors-ink" (get actual "c_var")))
      (is (= "  --shitsuke-colors-ink: #17202A;" (get actual "c_decl"))))
    (testing "full scalar groups"
      (is (= (cljc-scalar-group-css "shitsuke" "colors" colors) (get actual "colors")))
      (is (= (cljc-scalar-group-css "shitsuke" "spacing" spacing) (get actual "spacing")))
      (is (= (cljc-scalar-group-css "shitsuke" "breakpoints" breakpoints) (get actual "bp"))))
    (testing "root wrapper"
      (is (= (str ":root {\n" (cljc-scalar-group-css "shitsuke" "colors" colors) "\n}")
             (get actual "root"))))
    (testing "default-tokens corpus embeds match key-sorted defaults"
      (is (str/includes? (tokens/css-variables) "--shitsuke-colors-ink: #17202A;"))
      (let [from-port (compile-cases tokens-source {"d" "(default-scalar-root)"})]
        (is (str/includes? (get from-port "d") "--shitsuke-colors-ink: #17202A;"))
        (is (str/includes? (get from-port "d") "--shitsuke-spacing-4: 16px;"))
        (is (str/includes? (get from-port "d") "--shitsuke-breakpoints-md: 940px;"))))))

(deftest tokens-nested-type-decl-matches
  (let [title {:font-family "Aptos Display, system-ui, sans-serif"
               :font-size "38px"
               :font-weight "700"
               :color "var(--shitsuke-colors-ink)"}
        actual (compile-cases tokens-source
                              {"t_fs" (tok-nested-decl "type" "title" "font-size" "38px")
                               "t_ff" (tok-nested-decl "type" "title" "font-family"
                                                       "Aptos Display, system-ui, sans-serif")
                               "t_block"
                               (let [color (tok-nested-decl "type" "title" "color"
                                                            "var(--shitsuke-colors-ink)")
                                     ff (tok-nested-decl "type" "title" "font-family"
                                                         (:font-family title))
                                     fs (tok-nested-decl "type" "title" "font-size" "38px")
                                     fw (tok-nested-decl "type" "title" "font-weight" "700")]
                                 (str "(string-concat " color
                                      " (string-concat \"\\n\" "
                                      "(string-concat " ff
                                      " (string-concat \"\\n\" "
                                      "(string-concat " fs
                                      " (string-concat \"\\n\" " fw "))))))"))})]
    (is (= "  --shitsuke-type-title-font-size: 38px;" (get actual "t_fs")))
    (is (= "  --shitsuke-type-title-font-family: Aptos Display, system-ui, sans-serif;"
           (get actual "t_ff")))
    (is (= (cljc-nested-css "shitsuke" "type" "title" title)
           (get actual "t_block")))
    (is (str/includes? (tokens/css-variables) "--shitsuke-type-title-font-size: 38px;"))))

;; --- hig ------------------------------------------------------------------

(deftest hig-layer-order-and-text-style-props-match
  (let [actual (compile-cases hig-source
                              {"layer" "(layer-order-css)"
                               "body_props" "(text-style-props-token \"body\")"
                               "h1_props" "(text-style-props (record-new [:ref :hig/text-style-props] \"large-title\" \"700\"))"
                               "class_body" "(text-style-class \"body\")"
                               "class_d3" "(text-style-class \"display3\")"
                               "wrap" "(layer-wrap (text-style-class \"body\"))"})]
    (is (= hig/layer-order-css (get actual "layer")))
    (testing "text-style-props matches .cljc private emission shape"
      (is (str/includes? (hig/base-css) (str/trim-newline (get actual "body_props"))))
      (is (str/includes? (hig/base-css)
                         "font-family: var(--hig-text-large-title-font-family);"))
      (is (str/includes? (get actual "h1_props")
                         "font-weight: 700;"))
      (is (str/includes? (get actual "h1_props")
                         "font-family: var(--hig-text-large-title-font-family);")))
    (testing "utility class skeleton"
      (is (str/includes? hig/text-style-classes ".hig-body {"))
      (is (str/includes? hig/text-style-classes ".hig-display3 {"))
      (is (str/includes? (get actual "class_body") ".hig-body {"))
      (is (str/includes? (get actual "class_d3")
                         "font-size: var(--hig-text-display3-font-size);"))
      (is (str/starts-with? (get actual "wrap") "@layer kotoba.hig {")))))

(deftest hig-scalar-and-nested-groups-match
  (let [colors {:label "#000000" :system-background "#FFFFFF" :tint "#007AFF"}
        spacing {:1 "4px" :4 "16px" :content-margin "16px"}
        radius {:capsule "999px" :xs "6px"}
        body-text {:font-family "var(--hig-font-text)"
                   :font-size "17px"
                   :font-weight "400"
                   :line-height "22px"}
        d3 {:font-family "var(--hig-font-display)"
            :font-size "clamp(40px, 5vw + 8px, 64px)"
            :font-weight "700"
            :letter-spacing "-0.02em"
            :line-height "calc(1em + 4px)"}
        actual (compile-cases hig-source
                              {"colors" (hig-group-css "color" colors)
                               "spacing" (hig-group-css "spacing" spacing)
                               "radius" (hig-group-css "radius" radius)
                               "hairline" "(group-scalar-decl (record-new [:ref :hig/group-scalar-decl] \"hairline\" \"0.5px\"))"
                               "body" (hig-nested-css "text" "body" body-text)
                               "d3" (hig-nested-css "text" "display3" d3)
                               "sample" "(sample-root)"})]
    (is (= (cljc-scalar-group-css "hig" "color" colors) (get actual "colors")))
    (is (= (cljc-scalar-group-css "hig" "spacing" spacing) (get actual "spacing")))
    (is (= (cljc-scalar-group-css "hig" "radius" radius) (get actual "radius")))
    (is (= "  --hig-hairline: 0.5px;" (get actual "hairline")))
    (is (= (cljc-nested-css "hig" "text" "body" body-text) (get actual "body")))
    (is (= (cljc-nested-css "hig" "text" "display3" d3) (get actual "d3")))
    (testing "sample root embeds live in the .cljc default emission"
      (let [css (hig/css-variables)
            sample (get actual "sample")]
        (is (str/includes? css "--hig-color-label: #000000;"))
        (is (str/includes? sample "--hig-color-label: #000000;"))
        (is (str/includes? sample "--hig-text-display3-font-size: clamp(40px, 5vw + 8px, 64px);"))
        (is (str/includes? sample "--hig-hairline: 0.5px;"))
        (is (str/includes? css "--hig-text-display3-font-size: clamp(40px, 5vw + 8px, 64px);"))))))
