# shitsuke

`shitsuke`（仕付け）is the Kotoba shared **UI design system**: design tokens,
hiccup renderer, style (CSS) layer, portable re-frame seam, and pure-hiccup
component primitives. One set of `.cljc` view code renders two ways — SSR
(`shitsuke.hiccup/->html`, clj/babashka) and live browser (reagent + re-frame,
cljs) — the dual-render contract proven by `kami-mangaka-reader-clj`.

The implementation is portable `.cljc` (JVM / ClojureScript / SCI / babashka).
It owns no filesystem, network, or host effects. Real `reagent` / `re-frame` /
`shadow-css` are build/dev aliases only; the core has zero third-party runtime
deps (same split as `dot` / `kasane` / `slides`).

```text
shitsuke = tokens + hiccup + style + re-frame seam + components
```

## Boundaries

| layer | role |
|---|---|
| `shitsuke.tokens` | design-token IR + resolver + `:root` CSS-var emitter; `from-slides-design` adapter |
| `shitsuke.hiccup` | dependency-free hiccup → HTML string renderer (SSR twin of the view contract) |
| `shitsuke.style` | token → CSS custom properties + stable `shitsuke__*` class-name registry |
| `shitsuke.re-frame` | tiny re-frame-shaped runtime (7-fn portable subset) for JVM/SSR/WASM |
| `shitsuke.re-frame.core` | host seam: real re-frame (cljs) ‖ mini runtime (clj) |
| `shitsuke.reagent.core` | host seam: real reagent (cljs) ‖ `hiccup/->html` (clj) |
| `shitsuke.components` | pure-hiccup UI primitives (button/field/input/toolbar/mode-tabs/…) |
| host build | shadow-css `:pages` extraction, reagent/re-frame `:cljs` aliases |

## Dual render (the contract)

```clojure
(require '[shitsuke.components :as c]
         '[shitsuke.hiccup :as h])

(def view [:div [:c/button "Go" {:act :go}]]) ; pure hiccup data

;; SSR (clj / babashka):
(h/->html view)

;; Browser (cljs): the SAME `view` is returned by a reagent component and
;; mounted via shitsuke.reagent.core/render; state via shitsuke.re-frame.core.
```

## Tests

```bash
clojure -M:test
```

## Design

See `docs/design.md` for the layer-by-layer API and `docs/adr/0001-shitsuke-design-system.md`
for the decision record.
