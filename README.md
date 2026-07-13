# shitsuke

`shitsuke`（仕付け）is the Kotoba shared **UI design system**: design tokens,
hiccup renderer, style (CSS) layer, portable re-frame seam, pure-hiccup
component primitives, and small host-independent editor kernels. One set of `.cljc` view code renders two ways — SSR
(`shitsuke.hiccup/->html`, clj/babashka) and live browser (reagent + re-frame,
cljs) — the dual-render contract proven by `kami-mangaka-reader-clj`.

The implementation is portable `.cljc` (JVM / ClojureScript / SCI / babashka).
It owns no filesystem, network, or host effects. Real `reagent` / `re-frame` /
`shadow-css` are build/dev aliases only; the core has zero third-party runtime
deps (same split as `dot` / `kasane` / `slides`).

```text
shitsuke = tokens + hiccup + style + re-frame seam + components + editor kernels
```

## Boundaries

| layer | role |
|---|---|
| `shitsuke.tokens` | design-token IR + resolver + `:root` CSS-var emitter; `from-slides-design` adapter |
| `shitsuke.hig` | Apple-HIG semantic token layer: 11 text styles + fluid display scale (`:display1/2/3`), semantic colors (light+dark), palette, spacing/radius, element base CSS — `--hig-*` vars inside cascade layer `kotoba.hig` |
| `shitsuke.hiccup` | dependency-free hiccup → HTML string renderer (SSR twin of the view contract) |
| `shitsuke.style` | token → CSS custom properties + stable `shitsuke__*` class-name registry |
| `shitsuke.re-frame` | tiny re-frame-shaped runtime (7-fn portable subset) for JVM/SSR/WASM |
| `shitsuke.re-frame.core` | host seam: real re-frame (cljs) ‖ mini runtime (clj) |
| `shitsuke.reagent.core` | host seam: real reagent (cljs) ‖ `hiccup/->html` (clj) |
| `shitsuke.components` | pure-hiccup UI primitives (button/field/input/toolbar/mode-tabs/…) |
| `kotoba.editor` | portable editor state helpers: selection, undo/redo, nudge, alignment |
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

## `shitsuke.hig` — Apple-HIG base layer

`shitsuke.hig` is the single source of truth for Apple Human Interface
Guidelines-grade typography (the 11 UIKit text styles), font stacks
(`--hig-font-text` / `--hig-font-display` / `--hig-font-mono`), semantic
colors (light + dark), system palette, 4pt-grid spacing, radius, and
element-level base CSS. It emits `--hig-*` CSS custom properties and element
rules inside the CSS cascade layer `kotoba.hig`
(`(hig-css overrides dark-overrides)` = the full bundle; the order
declaration is `@layer kotoba.hig, kotoba.glass;`). Monospace UI (code
panes, EDN editors) uses `var(--hig-font-mono)` or the `.hig-mono` utility
class (mono stack + footnote size) — no hand-written mono stacks in apps.

Hero/marketing type uses the opt-in **display scale** above `:large-title`:
`:display3` / `:display2` / `:display1` (w700, display stack, tight
tracking) with **fluid** `clamp()` font sizes — e.g. `:display3` is
`clamp(40px, 5vw + 8px, 64px)` (min = 62.5% of max, ramping 640→1120px
viewport; line-height `calc(1em + 4px)` = 64/68 at max). Apply via the
`.hig-display1/2/3` utility classes or the `--hig-text-display*-*` vars —
no hand-written viewport clamps in apps. `base-css` is unchanged (`h1`
stays `:large-title`). See `docs/design.md` for the formula table.

- **liquid-glass-ui** fills the `kotoba.glass` layer with its material
  (translucency/vibrancy) styles on top of this base.
- **Apps** consume both via **kotoba-ui**; app CSS stays *unlayered*, so it
  always beats both layers.
- Additive to `shitsuke.tokens` — v1 `--shitsuke-*` vars stay for existing
  consumers.

## Tests

```bash
clojure -M:test
```

## Design

See `docs/design.md` for the layer-by-layer API and `docs/adr/0001-shitsuke-design-system.md`
for the decision record.
