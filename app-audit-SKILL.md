---
name: app-audit
description: >
  Perform an exhaustive professional audit of any frontend application — regardless of domain,
  stack, size, or complexity. Trigger for: "audit my app", "deep code review", "security review",
  "performance review", "UX review", "accessibility audit", "review before launch",
  "optimize my app", "improve the design", "make it more professional", "standardize my code",
  "check i18n", "review my AI integration", "improve my features", "my app is messy",
  "restructure my app", "polish my app", "what should I build next", "help me improve my app",
  "my app feels incoherent", "clean up my codebase", "evaluate my existing features",
  "my app grew messy over time", "unify my app's design",
  or when a user shares a source file for serious analysis. Also trigger for PWAs, React/Vue/Svelte/vanilla JS apps,
  calculators, dashboards, trackers, tools, games, medical/fintech/legal/e-commerce/SaaS/creative/AI-powered apps.
  Covers: domain correctness, security, privacy, performance, state management, UI/UX,
  visual design, design language, brand identity, commercial readiness, product aesthetics,
  visual differentiation, polish, standardization, accessibility, browser compat, code quality,
  data integrity, i18n, AI/LLM risks, architecture, existing feature evaluation & evolution,
  competitive research, R&D planning, app coherence restoration, and codebase restructuration.
  For app-specific context files (pre-filled §0), see references/.
---

# Professional App Audit — Universal Framework

---

## QUICK START — How to Use This Skill

> **For Claude**: When this skill activates, follow these steps:
> 1. **Use `AskUserQuestion`** to present the §TRIAGE options (unless the user already specified a mode)
> 2. **Use `TodoWrite`** to create a progress tracker with one todo per audit part
> 3. **Use `Agent` (subagent_type: Explore)** to read the entire codebase in parallel before writing any findings
> 4. **Fill §0** by extracting from code first, then asking the user only for what can't be extracted
> 5. **Work in parts** — output one part per response, update the todo list after each
>
> **For the User**: Just say one of the trigger phrases (e.g., "audit my app", "review before launch", "help me improve my features") and Claude will guide you through the options. You can also jump directly to a mode: "do a full audit", "run R&D mode", "polish my app".

---

## SKILL MAP — Quick Reference

> **Read this first.** This is a 2,800+ line skill. You never need all of it. Use this map.

### Section Index

| Section | Purpose | When to Read |
|---------|---------|-------------|
| §TRIAGE | Route to the right mode | **Always first** — ask user which mode |
| §0 | App identity, tech stack, constraints, design, domain rules | **Always** — fill before any work |
| §I | Classify domain, architecture, size, aesthetic axes | **Full audit** — determines depth |
| §II | 10 Iron Laws governing auditor behavior | **Full audit** — governs every finding |
| §III | Execution plan, part structure, pre-flight checklist | **Full audit** — determines pacing |
| §IV | 120+ checks across 15 categories (A–O) | **Full audit** — the core reference |
| §V–VII | Finding format, deliverables, summary dashboard | **Full audit** — templates |
| §VIII | Cross-cutting patterns spanning multiple categories | **Full audit** — check after all dimensions |
| §IX | Final mandate — binding audit contract | **Full audit** — closing reference |
| §X | Existing feature evaluation + new feature planning | **R&D&I mode** — "what to improve and why" |
| §XI | Deep comprehension + coherence restoration + polish | **Polish mode** — "make it one thing again" |

### Common Execution Paths

```
"Audit my app"
  → §TRIAGE → §0 → §I–IX → §VII
  Claude: read §I-§IX as you work through parts. Output in parts per §III.

"Help me improve my features" / "What should I build next?"
  → §TRIAGE → §0 (lightweight) → §I.1 classification only → §X
  Claude: §X.0 (existing features) is mandatory. §X.1 (competitive) if web search available.

"My app is messy, restructure it" / "Polish my app"
  → §TRIAGE → §0 → §XI
  Claude: §XI.0 (comprehension) is MANDATORY — do not skip. Prior audit recommended.

"Full treatment"
  → §0 → §I–IX → §X → §XI
  Claude: this is 15+ parts. Confirm with user after Part 1.

"Continue from audit → now improve"
  → Load prior audit findings → §X (builds on audit) → §XI (builds on both)
```

### Claude Execution Notes

- **§X and §XI can be run independently.** They don't require a full audit. But they work better with one.
- **§X.0 (existing feature eval) always runs before §X.1 (competitive research).** Look inward first.
- **§XI.0 (comprehension) is non-negotiable.** Never skip it — it's what prevents "clean but soulless" restructuring.
- **When in doubt about which section applies:** ask the user. Use the `AskUserQuestion` tool with the triage options.
- **For apps > 3,000 lines:** always confirm with user after completing §0 + §I before continuing.

### Claude Code Tool Integration Protocol

> **These instructions are specific to Claude Code (CLI/web).** Use the right tool for each audit task.

#### Tool Usage Map

| Audit Task | Tool to Use | Why |
|------------|-------------|-----|
| **Read the codebase** | `Agent` (subagent_type: Explore) | Explores files in parallel without bloating the main context |
| **Search for patterns** (e.g., hardcoded strings, security issues) | `Grep` or `Glob` | Fast, targeted codebase searches |
| **Ask user which mode** | `AskUserQuestion` | Presents triage options with descriptions |
| **Track audit progress** | `TodoWrite` | Creates visible progress tracker for multi-part audits |
| **Research competitors/sources** | `WebSearch` / `WebFetch` | Live web research for §X.1 competitive analysis |
| **Present findings** | Direct text output | Findings are displayed directly to the user |
| **Implement fixes** | `Edit` / `Write` | Apply recommended changes to code |

#### Multi-Part Audit Progress Protocol

At the start of any multi-part audit, create a progress tracker:

```
TodoWrite([
  { content: "Fill §0 App Context Block", status: "in_progress", activeForm: "Filling §0 context" },
  { content: "§I Adaptive Calibration", status: "pending", activeForm: "Classifying domain and architecture" },
  { content: "Part 1: Core Logic & Domain", status: "pending", activeForm: "Auditing core logic" },
  { content: "Part 2: State & Data Integrity", status: "pending", activeForm: "Auditing state management" },
  ...continue for each part
])
```

Update status to `completed` as each part finishes. The user sees real-time progress.

#### Parallel Research Strategy

For large codebases (> 2,000 lines), launch parallel Explore agents to read different modules simultaneously:

```
Agent(subagent_type: Explore, prompt: "Read all UI/fragment files in app/src/main/java/.../ui/")
Agent(subagent_type: Explore, prompt: "Read all utility/service files in app/src/main/java/.../utils/")
Agent(subagent_type: Explore, prompt: "Read all data model files in app/src/main/java/.../data/")
```

This prevents context window bloat while ensuring thorough coverage.

### Platform Awareness

> This skill was originally written for web/frontend apps but applies to **any frontend platform**. When auditing non-web apps, adapt the terminology:

| Web Concept | Android/Kotlin Equivalent | iOS/Swift Equivalent |
|-------------|--------------------------|---------------------|
| CSS variables / design tokens | `colors.xml`, `themes.xml`, `dimens.xml` | Asset catalogs, `UIColor` extensions |
| `localStorage` | `SharedPreferences`, Room DB | `UserDefaults`, CoreData |
| Components / React state | Fragments, ViewModels, LiveData | ViewControllers, SwiftUI State |
| `innerHTML` / XSS | `WebView.loadData()` injection | `WKWebView` injection |
| Bundle size | APK size, DEX method count | IPA size |
| Service Workers | WorkManager, Foreground Services | Background Tasks |
| CSS animations | `MotionLayout`, `ObjectAnimator`, `MotionUtil` | `UIView.animate`, SwiftUI `.animation` |
| `border-radius` | `cornerRadius` in ShapeableImageView / MaterialCardView | `layer.cornerRadius` |
| Tailwind/CSS classes | XML attributes, Material Design 3 theme | SwiftUI modifiers |
| `prefers-reduced-motion` | `Settings.Global.ANIMATOR_DURATION_SCALE` | `UIAccessibility.isReduceMotionEnabled` |

**When auditing Android apps specifically:**
- Check `AndroidManifest.xml` for permission issues (§C)
- Audit `proguard-rules.pro` for security implications
- Review `build.gradle` for dependency versions (§O4)
- Check `values/` and `values-night/` for theme completeness (§E)
- Review navigation graph (`nav_graph.xml`) for flow coherence (§H)
- Check for proper lifecycle handling in Fragments/ViewModels (§B)
- Verify Material Design 3 component usage and theming (§E, §L)

**When auditing iOS apps specifically:**
- Check `Info.plist` for permission descriptions
- Review asset catalogs for theme support
- Audit `Podfile`/`Package.swift` for dependencies
- Check for proper SwiftUI/UIKit lifecycle handling

---

## §TRIAGE — MANDATORY AUDIT ROUTING (execute BEFORE reading the rest of this skill)

**Always ask first** using `AskUserQuestion`. Example invocation:

```
AskUserQuestion({
  questions: [{
    question: "What kind of audit would you like?",
    header: "Audit mode",
    options: [
      { label: "Full App Audit", description: "Code, security, performance, accessibility, UX, design, architecture, domain correctness, i18n, AI risks, future projections" },
      { label: "Design & Aesthetic Audit", description: "Deep visual analysis — color science, typography, motion, brand identity, competitive positioning" },
      { label: "R&D & Improvement", description: "Existing feature health evaluation, competitive analysis, improvement prioritization, R&D roadmap" },
      { label: "Polish & Restructure", description: "Deep app comprehension, coherence fracture healing, systematic polish, codebase restructuring" }
    ],
    multiSelect: false
  }]
})
```

| Option | What You Get |
|--------|-------------|
| **Full App Audit** | Code, security, performance, accessibility, UX, design, architecture, domain correctness, i18n, AI risks, future projections |
| **Design & Aesthetic Audit** | Deep visual analysis — color science, typography, motion, brand identity, competitive positioning |
| **Research, Development & Improvement** | Existing feature health evaluation, competitive analysis, improvement prioritization, R&D roadmap |
| **Polishing & Restructuration** | Deep app comprehension, coherence fracture healing, systematic polish, codebase restructuring |
| **Both (Companion Mode)** | Full audit + design audit. Longest option. |

**Routing:**

| Selection | What Claude Does |
|-----------|-----------------|
| Full App Audit | Continue from §ORCHESTRATION. Do NOT load `design-aesthetic-audit-SKILL.md`. |
| Design & Aesthetic Audit | Stop this skill. Load `design-aesthetic-audit-SKILL.md`. |
| R&D & Improvement | Fill §0 → lightweight §I classification → skip to §X. If prior audit exists, reference findings. |
| Polishing & Restructuration | Fill §0 → skip to §XI. Prior audit strongly recommended. If none, do Parts 1–3 first. |
| Companion Mode | Continue this skill + load `design-aesthetic-audit-SKILL.md`. Follow companion protocol. |

**Skip triage when:** user explicitly names which mode, has already selected, or says "continue" / "next part" during an in-progress session.

---

## ORCHESTRATION — How This Skill Works

This skill adapts to the app's domain, stakes, architecture, and aesthetic identity. The auditor holds all specialist lenses simultaneously:

| Area | Lenses |
|------|--------|
| **Code** | Senior engineer · Security researcher · Performance engineer · QA lead |
| **Domain** | Domain specialist · Compliance officer · Forward-looking architect |
| **Design** | Visual designer · Product designer · Brand strategist · Copywriter |
| **UX** | UX designer · Accessibility specialist · Adversarial tester |
| **Strategy** | R&D strategist · Restructuring engineer · Refactoring expert |

A wrong displayed number is not just a logic bug — it is simultaneously a UX trust failure, a data integrity gap, and potentially a security issue depending on stakes.

---

## §0. APP CONTEXT BLOCK

> **Fill before writing any findings.** Extract what you can from source code. Ask the user only for what cannot be extracted. Verify domain rules with the user — the code may be wrong.

```yaml
# ─── CROSS-AUDIT CONTINUITY ───────────────────────────────────────────────────
# Complete this block only when this is NOT the first audit of this app.
# Its purpose: prevent silent contradiction between audit sessions.
# A finding that contradicts a previously confirmed domain rule is a CONFLICT, not a correction —
# and must be surfaced explicitly, not silently applied.
Prior Audit Reference:
  Version Audited:    # The version number from the previous audit's §0
  Session Date:       # Approximate date of the prior audit (helps identify which session)
  Confirmed Rules:    # List every domain rule confirmed [§0-CONFIRMED] in the prior session
    - # e.g. "BASE_RATE = 0.008 — confirmed by user, session 1"
    - # e.g. "MAX_SESSIONS = 5 — confirmed by user, session 1"
  Confirmed Findings: # Any findings from the prior audit confirmed as real bugs
    - # e.g. "F-007: rounding error in dose calculation — confirmed CRITICAL"
  Conflicts Flagged:  # Any place where this session's findings differ from the prior session's
    - # Format: "CONFLICT: [prior session claimed X] vs [this session finds Y] — needs user confirmation"

# ─── IDENTITY ─────────────────────────────────────────────────────────────────
App Name:      # e.g. "InvoiceFlow" / "HealthTrack" / "PixelEditor"
Version:       # e.g. "v2.1.4" — is this a single source of truth or scattered across the codebase?
Domain:        # e.g. "Invoice creation for EU freelancers" / "Medication dosage calculator"
Audience:      # e.g. "Small business owners" / "Nurses" / "Casual gamers" / "Data analysts"
Stakes:        # LOW (hobby/entertainment) | MEDIUM (productivity, money-adjacent) |
               # HIGH (real financial transactions, legal records) |
               # CRITICAL (medical, safety-critical, legal compliance required)
               # Stakes is a severity multiplier — wrong data in a CRITICAL app is always CRITICAL.

# ─── TECH STACK ───────────────────────────────────────────────────────────────
Framework:     # e.g. "React 18 (CDN)" / "Vue 3 + Vite" / "Vanilla JS" / "Svelte"
Styling:       # e.g. "Tailwind CSS (CDN)" / "CSS Modules" / "Styled Components" / "Plain CSS"
State:         # e.g. "useReducer + localStorage" / "Zustand" / "Redux Toolkit"
Persistence:   # e.g. "localStorage only" / "IndexedDB" / "REST API + localStorage cache"
Workers:       # e.g. "Blob Web Worker + Blob SW" / "Workbox SW" / "None"
Visualization: # e.g. "Recharts" / "D3.js" / "Chart.js" / "None"
Build:         # e.g. "Zero build tools — CDN only" / "Vite 5" / "Webpack 5"
External APIs: # e.g. "None" / "Stripe" / "OpenWeather" / "Anthropic Claude API"
AI/LLM:        # e.g. "None" / "OpenAI GPT-4o via fetch" / "Claude claude-sonnet-4-6, streaming"

# ─── MOBILE / NATIVE STACK (fill if applicable) ──────────────────────────────
Platform:      # e.g. "Android" / "iOS" / "Flutter" / "React Native" / "Web only"
Language:      # e.g. "Kotlin 1.9.0" / "Swift 5.9" / "Dart 3.2" / "TypeScript"
Min SDK:       # e.g. "Android 29 (10)" / "iOS 15.0" / "N/A"
Target SDK:    # e.g. "Android 35 (15)" / "iOS 17.0" / "N/A"
Architecture:  # e.g. "MVVM (ViewModel + LiveData)" / "MVI (Compose)" / "VIPER" / "BLoC"
UI Framework:  # e.g. "Material Design 3 (XML Views)" / "Jetpack Compose" / "SwiftUI" / "UIKit"
Navigation:    # e.g. "Navigation Component 2.7.4" / "NavigationStack" / "go_router"
DI:            # e.g. "Manual" / "Hilt/Dagger" / "Koin" / "Swinject"
Testing:       # e.g. "JUnit 4 + Espresso" / "XCTest" / "flutter_test"
CI/CD:         # e.g. "GitHub Actions" / "Fastlane" / "Bitrise" / "None"

# ─── PLATFORM & LOCALE ────────────────────────────────────────────────────────
Target Platforms: # e.g. "Desktop-first" / "Mobile-first PWA" / "Both (responsive)"
                  # Determines: touch targets, viewport units, safe-area-inset, hover media query
Locale / i18n:    # e.g. "English only, US" / "EN + FR + DE, LTR" / "Multi-locale + RTL (AR, HE)"
                  # None = i18n ignored. Any locale = §N activated.
Performance Budget: # e.g. "None defined" / "LCP < 2.5s, TTI < 5s on 3G" / "Bundle < 200kb gz"

# ─── ARCHITECTURE CONSTRAINTS ─────────────────────────────────────────────────
# Non-negotiable. Every recommendation must respect these — even if they are suboptimal.
# Suggestions to change constraints are welcome but must be clearly marked as architectural
# proposals, not standard findings.
Constraints:
  - # e.g. "Single-file: ALL code in App.jsx — no multi-file imports"
  - # e.g. "Zero build tools — no bundling, minification, tree-shaking"
  - # e.g. "CDN-only: React/libs loaded from CDN at runtime, no npm"
  - # e.g. "localStorage: sole persistence, 5MB limit, no server"

# ─── DESIGN IDENTITY ──────────────────────────────────────────────────────────
# The app's intentional visual and interactive character.
# This protects the aesthetic from being "standardized" into something generic.
# Extract from the code, or ask the user.
Design Identity:
  Theme:         # e.g. "Dark-first with cyan/teal accent" / "Clean minimal light" / "Playful colorful"
  Personality:   # e.g. "Precise and informative" / "Warm and approachable" / "Sleek and premium"
  Signature:     # e.g. "Animated background particles, OLED pitch-black mode, glowing accents"
                 # These are PROTECTED — the audit improves them but never removes them.

  # ── PRODUCT & COMMERCIAL VISUAL IDENTITY ─────────────────────────────────
  # Used by §E8, §E9, §F6, §L4. Inferred from code + domain if not provided.
  # NOTE: Commercial fields (Visual Reference, Monetization Tier, Conversion) are
  # only activated for paid/freemium/professional products. Fan/free/community tools
  # use the aesthetic-fidelity framing instead — see §I.4 Aesthetic Context Analysis.
  Visual Reference:      # 2–3 apps or sources whose visual quality/feel this should match
                         # Paid tools: "Linear, Vercel" / Fan tools: "the official companion for X" or "the visual language of X itself"
  Emotional Target:      # The feeling the app should produce at first glance
                         # e.g. "Fast + trustworthy + premium" / "Warm + delightful + approachable"
                         # e.g. "Feels like it belongs to this world" / "Made by someone who really knows this"
  Visual Differentiator: # What makes this app visually memorable
                         # Paid: distinctive vs competitors / Free/Community: authentic to its subject and audience
  Monetization Tier:     # "Free (no revenue intent)" / "Free (community tool)" / "Freemium SaaS"
                         # "Paid consumer ($5–$30/mo)" / "Professional B2B ($50–$500/mo)" / "Enterprise"
                         # Determines which §E8 framing activates. "Free" = aesthetic framing only.
  Distribution Channel:  # Determines first-impression quality requirements
                         # e.g. "Direct URL share" / "App Store listing" / "Reddit/Discord community share"
                         # / "Product Hunt" / "Enterprise sales demo" / "GitHub README"

# ─── DOMAIN RULES ─────────────────────────────────────────────────────────────
# Every formula, constant, rate, threshold, and business rule the code MUST implement correctly.
# This is the specification. Wrong values here → wrong findings.
Domain Rules:
  - # List each as: RULE_NAME = value / formula / description
  - # e.g. "TAX_RATE = 0.21 (21% VAT, exclusive)"
  - # e.g. "All monetary values stored as integer cents — never float"
  - # e.g. "MAX_RETRY_ATTEMPTS = 3, TIMEOUT_MS = 10000"
  - # e.g. "Server timezone UTC+8, no DST; America: UTC-5/UTC-4 (DST applies)"

# ─── TEST VECTORS ─────────────────────────────────────────────────────────────
# Known input → expected output pairs for the app's core calculations.
# Pre-supply these if you have them — §A2 requires ≥3 for probability/financial apps.
# The audit will verify these against actual code output. Wrong output = finding.
# Format: "Input: {values} → Expected output: {result} — Source: {where this comes from}"
Test Vectors:
  - # e.g. "Input: principal=1000, rate=0.05, years=10 → Expected: 1628.89 — Source: formula spec"
  - # e.g. "Input: weight=70kg, dose_factor=0.5 → Expected: 35mg — Source: medical reference"
  - # e.g. "Input: items=[A,B,C], pity=89 → Expected: guarantee triggers at 90 — Source: official rates"

# ─── CRITICAL USER WORKFLOWS ──────────────────────────────────────────────────
# The 5–10 most important end-to-end user journeys. The audit traces each one step-by-step.
Workflows:
  1: # "New user → onboarding → first core action → result"
  2: # "Returning user → load state → update → verify output updates"
  3: # "Import external data → preview → confirm → verify correctness"
  4: # "Export → fresh device → import → verify round-trip fidelity"
  5: # "Power user with maximum data → performance still acceptable"
  # Add more as needed.

# ─── KNOWN ISSUES ─────────────────────────────────────────────────────────────
Known Issues:
  - # What the developer already suspects is broken

# ─── AUDIT SCOPE ──────────────────────────────────────────────────────────────
Audit Focus:   # e.g. "Full audit" / "Prioritize security and correctness" / "Design + polish only"
Out of Scope:  # e.g. "Backend code (not provided)" / "Third-party payment widget"

# ─── GROWTH CONTEXT ───────────────────────────────────────────────────────────
# Used by §O Projection Analysis. The audit reasons about the app's future, not just its present.
App Maturity:             # e.g. "Prototype/MVP" / "Active development" / "Stable/maintenance mode"
Expected Scale:           # e.g. "Single user forever" / "10–50 users" / "Scaling to thousands"
Likeliest Next Features:  # Top 3–5 features most likely to be added next
                          # e.g. "User accounts", "CSV export", "Collaboration", "Mobile app"
Planned Constraint Changes: # e.g. "Moving from localStorage to a backend in 6 months"
                             # e.g. "None planned" / "Adding authentication next sprint"
```

---

## I. ADAPTIVE CALIBRATION

Before writing any finding, classify the app along three axes. These classifications determine which dimensions get the deepest scrutiny and where severity multipliers apply.

### §I.1. Domain Classification → Severity Amplification

| Domain | Amplify These Dimensions | Stakes Default |
|--------|--------------------------|----------------|
| Medical / Health | §A1 Logic, §A-Rules, §B3 Validation, §C6 Compliance | CRITICAL |
| Financial / Fintech / Billing | §A1 Logic, §J1 Financial Precision, §C1 Auth, §C6 Compliance | HIGH→CRITICAL |
| Gambling-adjacent / Gacha | §A2 Probability, §L5 Ethical Design, §C6 Age/Compliance | MEDIUM→HIGH |
| E-commerce / Payments | §C1 Auth, §C2 XSS, §J1 Financial, §C6 PCI/GDPR | HIGH |
| Web3 / Crypto / Wallet | §C1 Key/Seed handling, §C2 Injection, §A1 Tx math, §C6 Jurisdiction | HIGH→CRITICAL |
| Social / Presence / Multi-user | §C5 Privacy, §J4 Real-time, §C6 GDPR/CCPA | MEDIUM→HIGH |
| Productivity / SaaS | §B State, §D Performance, §H Flows, §K Ops | MEDIUM |
| Creative / Media / Tools | §E Design, §D Assets, §G Compatibility | LOW→MEDIUM |
| Data / Analytics / Dashboards | §A1 Math, §B Data, §I Visualization | MEDIUM→HIGH |
| Game / Companion / Fan Tool | §A Domain Data, §E Design, §C Attribution | LOW→MEDIUM |
| AI / LLM-Powered | §K5 AI Integration, §C2 Prompt Injection, §C5 Privacy, §A1 Output Correctness | MEDIUM→HIGH |

### §I.2. Architecture Classification → Failure Modes

| Architecture | Primary Failure Modes to Hunt |
|--------------|-------------------------------|
| Single-file monolith (CDN React) | Dead code accumulation, blob Worker browser incompatibility, no code-splitting, CSS specificity at scale |
| Multi-file SPA (Vite/Webpack) | Bundle bloat, stale chunks, tree-shaking failures, import cycles |
| SSR / Next.js | Hydration mismatches, server/client state divergence, SEO gaps |
| Vanilla JS | Global scope pollution, event listener leaks, DOM coupling |
| PWA (any) | SW versioning, cache poisoning, offline-first edge cases |
| LocalStorage-only | Quota exhaustion, schema migration gaps, concurrent-tab conflicts |
| Backend-connected | Race conditions, optimistic update failures, token leaks, CORS |
| Android (MVVM/Kotlin) | Fragment lifecycle leaks, ViewModel scope misuse, coroutine cancellation gaps, permission model gaps, process death state loss, ProGuard stripping |
| Android (Compose) | Recomposition storms, state hoisting confusion, side-effect lifecycle, LazyColumn performance |
| iOS (UIKit) | Retain cycles, main-thread violations, lifecycle misuse, deep-link handling |
| iOS (SwiftUI) | @State/@StateObject confusion, view identity issues, NavigationStack complexity |
| Cross-platform (Flutter/RN) | Bridge bottleneck, platform-specific fallback gaps, native module versioning |

### §I.3. App Size → Audit Scope

| Lines of Code | Parts | Depth |
|---------------|-------|-------|
| < 500 | 4–5 | All dimensions, condensed findings |
| 500–2,000 | 6–8 | Full dimensions, moderate findings |
| 2,000–6,000 | 8–12 | Full dimensions, detailed findings |
| 6,000–15,000 | 12–16 | Full dimensions + domain deep dives |
| > 15,000 | 16+ | Full dimensions + per-module sub-audits |

### §I.4. Aesthetic Context Analysis — Five Independent Axes

> Aesthetic goals are derived from five independent dimensions that combine in any configuration. A meditation app, a nurse's calculator, a gacha companion, and a B2B dashboard all require different aesthetic reasoning. **Classify all five axes before writing any §E/§F/§L finding.**

---

#### AXIS 1 — Commercial Intent
*What role does trust-building and conversion play in this app's visual goals?*

| Level | Signal | Aesthetic implication |
|-------|--------|-----------------------|
| **Revenue-generating** | Paid tier, subscription, in-app purchase, ad-supported | Visual trust signals actively matter — every design choice either supports or undermines willingness to pay or engage financially |
| **Institutional / grant-funded** | Non-profit, government, educational, healthcare org | Credibility and legitimacy signals matter — visual design must communicate seriousness and compliance, not commercial appeal |
| **Non-revenue / freely given** | Free tool, open-source, community gift | Commercial signals are irrelevant or actively harmful — visual goals shift entirely to craft, clarity, and authenticity |

---

#### AXIS 2 — Use Intensity & Emotional Context
*What is the user's cognitive and emotional state when using this app? This determines how much the design can demand of their attention.*

| Mode | Examples | Aesthetic implication |
|------|----------|-----------------------|
| **Focus-critical / high-frequency** | Daily work tool, professional instrument, developer IDE companion | Design must be nearly invisible — efficiency, scanability, and zero-distraction above all. Every animation is a tax. |
| **High-stakes / low-frequency** | Medical dosing calculator, legal document tool, emergency reference | Cognitive load reduction is the primary aesthetic goal. Visual noise = danger. Calm, high-contrast, unambiguous. |
| **Emotionally sensitive** | Mental health tool, grief support, therapy companion, crisis resource | Safety, warmth, and calm are structural requirements — not stylistic choices. Harsh colors, abrupt transitions, or playful copy cause harm. |
| **Creative / exploratory** | Art generator, music composition tool, design sandbox, writing tool | The aesthetic can be expressive and surprising — discovery and inspiration are valid goals. Delight is functional, not decorative. |
| **Learning / progressive** | Educational app, tutorial tool, skill trainer | Visual design must communicate progress, reward effort, and reduce intimidation. Pacing and encouragement are aesthetic requirements. |
| **Leisure / casual** | Entertainment tracker, hobby companion, quiz app, idle tool | Delight is primary. Friction that would be tolerable in a work tool is unacceptable here. Polish and playfulness are both appropriate. |
| **Occasional / transactional** | Unit converter, flight tracker, calculator, lookup tool | Get in, get the answer, get out. Visual complexity above "clean and immediate" is waste. |

---

#### AXIS 3 — Audience Relationship to the Subject
*How much does the audience already know? This determines vocabulary, visual complexity, information density, and what "belonging" looks like.*

| Relationship | Examples | Aesthetic implication |
|--------------|----------|-----------------------|
| **Domain expert / practitioner** | Clinicians, engineers, developers, financial analysts | Information density is a feature. Precision vocabulary is required — generic language signals the tool doesn't understand the domain. Visual complexity can be high if it serves the data. |
| **Enthusiast / community member** | Hobbyists, fans, dedicated amateurs | Community vocabulary and aesthetic norms signal insider status. Generic design signals the maker is an outsider. Not knowing the community's natural language is a credibility failure. |
| **Casual / general public** | Anyone unfamiliar with the domain | Progressive disclosure is mandatory. Domain jargon must either be avoided or clearly explained. Intimidation is a design failure. |
| **Mixed / bridging** | A tool that serves both experts and beginners | Progressive disclosure architecture is critical. The design must not condescend to experts or overwhelm novices simultaneously. This is one of the hardest design problems — acknowledge it explicitly. |

---

#### AXIS 4 — Subject Visual Identity
*Does the subject the app is built around have an established visual language? This determines whether visual fidelity is a goal.*

| Identity strength | Examples | Aesthetic implication |
|-------------------|----------|-----------------------|
| **Strong established aesthetic** | A specific game, show, sport league, musical genre, cultural movement | The app has an opportunity — and arguably an obligation — to honor the visual language of its subject. Palette, typography weight, motion character, and copy tone should feel *inspired by* the source, not arbitrarily chosen. |
| **Community-defined aesthetic norms** | Hacker/developer culture, speedrunning community, tabletop RPG players, street photography | The community has visual conventions that signal insider status. Violating them (accidentally going corporate, or too polished, or wrong era) reads as not understanding the culture. |
| **Domain-defined visual conventions** | Medical interfaces, financial terminals, academic tools, engineering dashboards | Professional domains have established visual conventions that communicate seriousness and domain-appropriateness. Departing from them without reason creates distrust. |
| **Neutral / no established identity** | Abstract SaaS, general productivity tool, new utility | Visual language must be invented rather than inherited. This is both more freedom and more responsibility — there's no reference to honor, so coherence must come entirely from within. |
| **The aesthetic IS the subject** | Generative art tool, music visualizer, color palette builder, typography explorer | The product's visual output is its primary value. The UI chrome must recede — the subject is the visual experience, not the container. Overdesigning the interface steals attention from what the tool produces. |

---

#### AXIS 5 — Aesthetic Role in the Product's Value
*Is design serving function, communicating identity, or is it the product itself?*

| Role | Examples | Aesthetic implication |
|------|----------|-----------------------|
| **Aesthetic IS the value** | Generative art, music player, creative tool, animation playground | The interface aesthetic is inseparable from the product. Compromise here is product failure. |
| **Aesthetic amplifies the value** | Well-designed productivity app, thoughtfully crafted community tool | Good aesthetic makes a working product better — more trusted, more enjoyable, more recommended. Standard design investment applies. |
| **Aesthetic communicates identity** | Portfolio, cultural tool, brand-representing product, community-facing gift | The design is a statement about the maker and their relationship to the subject or audience. Authenticity matters as much as polish. |
| **Aesthetic must stay invisible** | Emergency tool, high-stress professional instrument, accessibility-first interface, crisis resource | Design that draws attention to itself is actively harmful here. The goal is zero interference with the user's cognitive task. Every "nice" design touch must be audited against: does this distract? |

---

#### APPLYING THE FIVE AXES

**Step 1**: Classify each axis from §0 signals, code, and domain. Record the classification explicitly at the start of the audit.

**Step 2**: Identify any axis conflicts. Some combinations create genuine tension that must be resolved deliberately:
- *High commercial intent + emotional sensitivity* (wellness app with subscription) → commercial signals must be handled with exceptional care — trust comes from emotional safety first, conversion second
- *Expert audience + strong subject identity* (developer tool with hacker culture norms) → density and terseness ARE the aesthetic — over-polishing violates community norms
- *Aesthetic IS the subject + high-stakes use* (medical imaging tool with visualization) → the visualization must be designed with craft; the UI chrome around it must disappear
- *Mixed audience + domain aesthetic conventions* (medical app for patients AND clinicians) → requires two visual registers in one product — acknowledge this as an architectural design challenge

**Step 3**: Derive the **Aesthetic Goal Profile** — a one-sentence summary for this specific app:
> *"The aesthetic goal for [app] is: [primary goal derived from axes], which means [2–3 specific implications for findings]."*

Example profiles:
- A free community tool for enthusiasts of a visually distinctive subject, used leisurely: *"Craft and subject fidelity — the app should feel made by someone who genuinely knows this world, not a polished product trying to monetize a community."*
- A high-frequency professional tool for domain experts, used in focus-critical contexts: *"Invisible efficiency — every design element must serve information retrieval speed. Delight is a distraction. Density is a feature."*
- A paid creative tool for mixed audiences: *"Expressive trust — the aesthetic must feel inspiring enough to justify the cost, accessible enough for newcomers, and restrained enough not to compete with what the user creates."*
- A free wellness app used in emotionally sensitive contexts: *"Calm safety — warmth and visual quiet are functional requirements. Nothing jarring, nothing demanding, nothing that competes with the user's emotional state."*

**Step 4**: Use the Aesthetic Goal Profile as the filter for every finding in §E, §F, and §L. A finding that contradicts the profile is wrong regardless of how valid it would be for a different app.

---

#### THE FIVE-AXIS LENS REFERENCE

Throughout §E, §F, and §L, findings are marked with the axis they primarily serve:

- `[A1]` Commercial intent axis — applies when revenue or institutional credibility is a goal
- `[A2]` Use context axis — applies when emotional state or cognitive load shapes the requirement  
- `[A3]` Audience axis — applies when expertise level shapes vocabulary or density
- `[A4]` Subject identity axis — applies when the subject has visual conventions to honor
- `[A5]` Aesthetic role axis — applies when aesthetic investment level or restraint is the question

When a finding is tagged `[A1]` — **skip or substantially reframe it if the app is non-revenue.** When a finding is tagged `[A4]` — **skip or substantially reframe it if the subject has no established visual identity.** And so on. The tags make the conditionality explicit rather than requiring the auditor to remember it.

---

### §I.5. Domain Rule Extraction

When the user doesn't provide domain rules, extract them from the code — but apply strict source discipline. Every extracted value is `[CODE]` until the user confirms it, at which point it becomes `[§0-CONFIRMED]`. Never assume a code value is correct just because it is present.

```javascript
// Named constants → immediate §0 candidates
const TAX_RATE = 0.21           // → [CODE: line N] — verify with user → becomes [§0-CONFIRMED] only after user confirms
const MAX_ITEMS = 50            // → [CODE: line N] — is this a spec value or an implementation guess?
const MAX_SESSIONS = 5          // → [CODE: line N] — verify against authoritative spec

// Hardcoded numbers in formulas → red flags requiring verification
if (score > 100) { ... }                   // → [CODE: line N] — why 100? Spec rule or implementation assumption?
const rate = 0.08 + (i - 50) * ramp       // → [CODE: line N] — why 0.08? Why 50? Need source. DO NOT assume from memory.
const total = items * 1.21                 // → [CODE: line N] — hardcoded tax rate? Which jurisdiction? DO NOT recall from training.
const dose = weight * 0.5                  // → [CODE: line N] — CRITICAL: DO NOT guess the correct coefficient. Ask.
```

Present extracted rules as:
> ✓ `MAX_SESSIONS = 5` [CODE: line 342] — §0 confirms `[§0-CONFIRMED]`. Verified.
> ⚠ `ramp_divisor = 15` [CODE: line 989] — not in §0. Flagging for user confirmation before any findings use this value.
> 🚨 `dose = weight * 0.5` [CODE: line 1204] — coefficient in code but unconfirmed. CRITICAL until confirmed. Not recalling a "correct" value from training — asking user to confirm the intended coefficient and its source reference.
> 🔲 `BASE_RATE` — needed for §A1 assessment but absent from code and §0. Cannot assess correctness. Deferring finding and flagging as audit gap.

**Domain-specific escalation triggers** — when these patterns appear in code, escalate automatically:

| Code Pattern | Auto-Escalation |
|-------------|-----------------|
| `dose`, `dosage`, `medication`, `mg`, `mcg` | All §A1 findings → CRITICAL minimum |
| `payment`, `charge`, `billing`, `stripe` | All §C1, §C2 findings → CRITICAL minimum |
| `balance`, `transaction`, `transfer` | All §A1, §B3 findings → CRITICAL minimum |
| `float` used for monetary values | Automatic CRITICAL finding — money is never float |
| `age`, `minor`, `children` | All §C6 compliance findings → HIGH minimum |
| `password`, `token`, `secret` in localStorage | Automatic CRITICAL finding |
| Probability displayed without worst-case | §A2, §L5 — HIGH if real money involved |

---

### §I.6. Knowledge & Source Integrity

> Domain data fabrication is the most damaging audit error — worse than a missed bug, because fabricated facts produce false findings. Every domain fact must be sourced, evaluated, and cited.

#### Source Classification

Every domain fact must carry one of four source tags:

| Tier | Tag | Meaning | Action |
|------|-----|---------|--------|
| Code-verified | `[CODE: line N]` | Value read directly from the provided source file | Ground truth for code *behavior* — still needs verification against §0 to confirm it is *correct* |
| User-confirmed | `[§0-CONFIRMED]` | Value provided or confirmed by the user in §0 | The specification — code is tested against this |
| Web-sourced | `[WEB: source, version, date]` | Value found via live web search | Quality tier determines whether it supports a finding — see Web Source Quality below |
| Training-recalled | `[UNVERIFIED]` | Value recalled from training data only | Never use as a finding basis — present as a question to the user |

**Source hierarchy for correctness claims** (strongest → weakest):
`[§0-CONFIRMED]` → `[WEB: official-docs]` → `[WEB: patch-notes]` → `[WEB: official-wiki]` → `[CODE]` alone → `[WEB: community-wiki]` → `[WEB: forum]` → `[UNVERIFIED]`

Only `[§0-CONFIRMED]` and `[WEB: official-docs/patch-notes]` are strong enough to assert "the code is wrong." Everything below supports a question or a flag — not a finding.

#### Web Source Quality

Web search has its own failure modes: outdated pages, version-mismatched docs, community wikis with unverified edits, and multiple sources contradicting each other on the same value.

| Quality | Tag | Examples | Supports correctness finding? |
|---------|-----|----------|-------------------------------|
| Official documentation | `[WEB: official]` | Developer's own API docs, official site, medical publisher, standards body | Yes — after version check |
| Official patch / release notes | `[WEB: patch-notes, vX.Y]` | Dated changelogs from the developer | Yes — if version matches the app under audit |
| Developer-maintained wiki | `[WEB: official-wiki]` | Wiki explicitly maintained by the studio or org | Yes — with date check |
| Community wiki | `[WEB: community-wiki, date]` | Player-maintained wikis, fan wikis | Conditional — only if recently verified and internally sourced |
| Forum / community post | `[WEB: forum, date]` | Reddit, Discord, community guides | No — lead only; requires corroboration from a higher-quality source |
| Aggregator / secondary | `[WEB: aggregator]` | Sites summarizing other sources | No — locate and cite the original source directly |

**Seven rules for web-sourced domain facts:**

1. **Prefer official sources always.** Do not cite a wiki when official docs exist.
2. **Always record version and date.** Tag as `[WEB: official-docs, v1.8, 2024-03]`. A correct value from last year may be wrong today.
3. **When sources disagree — surface the conflict, never pick a winner silently.**
   > "⚠ SOURCE CONFLICT: Official API docs [WEB: official, 2025-01] state `MAX_TOKENS = 4096`. Community wiki [WEB: community-wiki, 2024-06] states `8192`. Please confirm which applies to your integration."
4. **When community wiki contradicts official docs — defer to official docs and flag the discrepancy.**
5. **When only community/forum sources exist — do not assert correctness. Ask.**
   > "🔲 `BASE_RATE = 0.006` [CODE: line 412]. Only a community guide found — no official reference. Flagged as audit gap."
6. **Never silently prefer a web value over the code value.** A discrepancy must be surfaced, not auto-corrected.
7. **Never use training memory as a tiebreaker when web sources conflict.** The user arbitrates — not the auditor.

**Domain categories where web sources are especially unreliable:**
- Game mechanics and live-service constants: frequently updated per-patch; wiki edits lag announcements; community speculation presented as fact is endemic
- Third-party API limits: frequently tier-dependent and silently changed; documented limit ≠ enforced limit
- Medical reference ranges: must cite peer-reviewed publication or official clinical guideline — no wikis, no forums
- Community-derived formulas (datamining, reverse-engineering): approximations, not specifications — tag `[WEB: community-derived]` and flag uncertainty explicitly

**Domain categories where training data is especially unreliable — always treat as `[UNVERIFIED]` unless sourced:**
- Game mechanics, live-service constants, per-patch values (rates, thresholds, drop tables, cooldowns)
- Third-party API rate limits, pricing tiers, model context windows, service SLAs
- Medical reference ranges, drug interaction rules, clinical formula coefficients
- Financial regulation specifics (tax rates, reporting thresholds, jurisdiction rules)

#### Scenario Reference

| Situation | Correct output |
|-----------|----------------|
| Code matches §0 or official web source | ✓ `RATE = 0.006` [CODE: line 412] matches [§0-CONFIRMED] / [WEB: official, v1.8, 2024-11]. Verified. |
| Code value, no §0, official web source found | ⚠ Official docs confirm this value. Flagging for user confirmation that this is the correct reference version. |
| Code value, web sources conflict | ⚠ SOURCE CONFLICT: Official docs: X. Community wiki: Y. Cannot assert correctness — please confirm which source applies. |
| Code value, only community sources | 🔲 Only community sources found. Flagged as audit gap; cannot confirm correctness. |
| Value needed, no source anywhere | 🔲 Value needed for §A1 assessment. No §0, no reliable web source found. Deferring correctness finding — audit gap. |

#### Cross-Session Consistency

When a second or subsequent audit occurs on the same app or a different version:
1. Surface the Prior Audit Continuity block from §0 at the top of Part 1
2. Prior `[§0-CONFIRMED]` rules carry forward as confirmed until explicitly contradicted by new §0 input
3. Any conflict between this session's findings and a prior confirmed rule surfaces immediately as `[CONFLICT]` — never silently resolved in either direction
4. Do not re-derive previously confirmed domain rules from training data — re-derive only from code + new user confirmation
5. Version differences are real: a constant correct in v1.2 may have changed in v1.3. Always check whether the app version has changed and flag which confirmed rules may be version-specific

---

### §I.7. Adaptive Analysis Protocols

#### Mid-Audit Reclassification Triggers
During the audit, if any of the following are discovered, **STOP and reclassify before continuing**. The initial §0 classification was based on incomplete information; these discoveries change the audit's severity baseline:

| Discovery | Reclassification Action |
|-----------|------------------------|
| Undisclosed financial transaction code | Escalate Stakes → HIGH; activate §K1, §C1 |
| Undisclosed health / dosage calculations | Escalate Stakes → CRITICAL immediately |
| State that persists PII to localStorage | Activate full §C5, §C6 GDPR review |
| CDN scripts without SRI in a payment/auth context | Immediate CRITICAL — supply chain attack surface |
| Code quality varies dramatically by section | Flag multiple-author / rush-commit sections; elevate confidence threshold for those sections |
| Dead code > 20% of total codebase | Adjust P10 scope — dead code analysis becomes primary; active/dead boundary must be mapped before other dimensions |
| Hardcoded credentials found | Surface as CRITICAL immediately; do not proceed without developer acknowledgment |
| Imports or calls to modules not present in provided files | Note as audit gap — affected findings are [THEORETICAL] until missing code is provided |

#### Partial Codebase Protocol
When only part of the codebase is provided:
1. Explicitly list what is **not** provided — `backend/`, auth module, worker file, etc.
2. Flag findings that depend on missing code as `[THEORETICAL]` — cannot confirm without full context
3. Do not assume missing code is correct — record its absence as a named audit gap
4. State which dimensions are affected by each gap and what would be needed to close it
5. Ask the user to provide the missing files before proceeding with deeply affected dimensions

#### Novel Pattern Protocol
When encountering a pattern not covered by the audit taxonomy:
1. Describe the pattern precisely — what it does, what it appears to intend, what it resembles
2. Classify by analogy: "behaves like a state machine implemented via X instead of Y"
3. Apply the nearest applicable dimension's criteria and note the approximation explicitly
4. Flag: `[NON-STANDARD PATTERN — audit criteria approximated via §X analogy]`
5. Note what cannot be assessed without understanding the original intent

#### Code Quality Variance Signal
If code quality varies significantly between sections — professional in some areas, rushed or inconsistent in others:
- This is a strong signal of **multiple authors**, **time-pressure commits**, or **copy-paste from external sources**
- Identify which sections have lower quality and apply elevated scrutiny to those sections
- Critical risk flag: if lower-quality sections are also the ones handling higher-stakes logic (payments, medical, auth), this is the **highest-risk combination** in the codebase — escalate all findings in those sections by one severity level

#### Signal Correlation — Connecting Distant Patterns
Some bugs are only visible when two distant code locations are read together:
- A constant defined early, used incorrectly hundreds of lines later
- A validation rule in one component that contradicts business logic in another
- State initialized correctly but reset incorrectly in a cleanup function far away
- A security assumption in one layer silently violated by a different layer

**Protocol**: For every validation rule, find every place that validates the same concept — do they all agree? For every security assumption, trace whether anything downstream silently violates it. Cross-reference findings across sections before finalizing severity.

---

## II. AUDIT PHILOSOPHY — THE IRON LAWS

### Law 1 — Specificity Is Non-Negotiable
Every finding names the exact function, variable, line number (or `near functionName`), CSS class, or data value. "Improve error handling" is not a finding. "`handleImport()` near line 847 calls `JSON.parse()` without a try/catch — any non-JSON clipboard paste throws an uncaught TypeError that crashes the React tree" is a finding.

### Law 2 — Bugs Before Refactors, Always
If a function has a bug AND is poorly structured: fix the bug first, with a minimal targeted change. The structural improvement is a separate, lower-priority recommendation. Never bundle them — it multiplies regression risk and obscures what actually changed.

### Law 3 — Honesty Over Completeness
`[THEORETICAL]` with clear reasoning is infinitely more valuable than `[CONFIRMED]` that is fabricated. Never invent line numbers, function names, or behavior. When uncertain, say so explicitly and explain why you suspect the issue.

Every domain fact must carry a source tag. Any fact tagged `[UNVERIFIED]` is a question to the user, not a finding. The full source classification system, web source quality tiers, and scenario reference are in **§I.6**.

### Law 4 — The Feature Preservation Contract
Every working feature is innocent until proven broken. The Feature Preservation Ledger (built in Part 1) is a binding contract: **no recommendation may break, remove, or diminish a working feature.** This applies to optimization, polishing, standardization, and refactoring recommendations equally. If a simplification would remove a working feature — it is rejected, full stop.

### Law 5 — The Identity Preservation Contract
The app's intentional design character (from §0 Design Identity) must not be erased by the audit. Polishing means making the existing vision more refined and consistent — not replacing it with generic conventions. A dark cyberpunk aesthetic with glowing accents gets polished *as* a dark cyberpunk aesthetic, not converted into a neutral gray corporate dashboard.

### Law 6 — Stakes Multiply Severity
Wrong numbers in a hobby tracker → MEDIUM. The same wrong numbers in a medical dosing app → CRITICAL. Use the Stakes field from §0 as a severity multiplier across all categories.

### Law 7 — The Golden Question
Before every modification recommendation:
> *"If the developer applies this change at 2 AM without carefully reviewing it, what is the worst realistic outcome?"*
> If the answer is anything other than "nothing bad" — add explicit warnings, reduce scope, or split into safer atomic steps.

### Law 8 — Minimum Footprint
Every fix recommendation must use the smallest safe change that resolves the problem. A 3-line targeted fix is strictly preferred over a refactor that happens to fix the problem as a side effect. Scope creep in a fix is itself a risk: it expands the surface area that can introduce regressions, conflicts with the Feature Preservation Contract, and makes the change harder to review. If a larger structural improvement is also needed, it is a separate, explicitly lower-priority recommendation.

### Law 9 — Project Forward
Every audit finding exists in time. Before closing each section, ask:
> *"If this issue is not fixed, what does it cost in 6 months when the codebase is 2× larger?"*
> A small validation gap that costs 5 minutes to fix today may require a data migration affecting thousands of records in 6 months. A naming inconsistency that is mildly confusing now becomes a maintenance trap when the team grows. An unabstracted localStorage call that takes 2 minutes to add now becomes a 2-week refactor before user accounts can be added.
> Time-amplified findings are marked with **⏱ COMPOUNDS** — these should be prioritized above their individual severity suggests, because the cost of inaction is not fixed.

### Law 10 — Knowledge Integrity

Domain data that cannot be sourced from code, §0, or an official web search **must not be used as a finding basis**. See **§I.6** for the full source classification system, web source quality tiers, and scenario reference. The three valid paths to a correctness finding: (1) user confirms in §0 → `[§0-CONFIRMED]`, (2) official docs found via web search → `[WEB: official, vX.Y, date]`, (3) code value present, no external confirmation → flag as unverified, ask user.

**Never:** state a value from memory then issue findings based on it. **Never:** use a community source as sole basis for "the code is wrong." **Always:** source every domain fact explicitly.

---

### Compound Finding Chains
Some bugs are individually minor but form chains of escalating harm when combined. Always look for:
- Validation gap [LOW] → invalid value in engine [MEDIUM] → wrong output displayed [HIGH] → user makes bad real-world decision [CRITICAL given stakes]
- Missing cache invalidation [LOW] → stale data served [MEDIUM] → user acts on outdated info [HIGH] → financial/health consequence [CRITICAL]

When a chain exists: document it, escalate the combined severity, and number the chain.

---

## III. EXECUTION PLAN

### Pre-Flight Checklist (Mandatory — Before Any Finding)

> **Claude Code**: Use `Agent` (subagent_type: Explore) to read the entire codebase in parallel. For large apps, launch multiple agents targeting different directories. Use `TodoWrite` to create the audit progress tracker. Use `AskUserQuestion` for any §0 fields you can't extract from code.

```
[ ] Read the entire source file(s) top-to-bottom without skipping
    → Claude Code: Use Agent(Explore) for large codebases, Glob + Read for small ones
[ ] Classify: domain type, architecture pattern, app size → determine part count
[ ] Extract all domain rules from code → verify against §0 → flag discrepancies
    → Claude Code: Use Grep to search for constants, magic numbers, formulas
[ ] Identify all architectural constraints → acknowledge them explicitly
    → Claude Code: Read build config (package.json / build.gradle / Podfile) first
[ ] Extract Design Identity from code if not provided → confirm with user
    → Claude Code: Read theme files, color resources, style definitions
[ ] Build Feature Preservation Ledger (every named feature: status + safety flags)
[ ] Map each critical workflow from §0 through the actual code
[ ] Identify top 5 risk areas based on domain classification
[ ] Announce: domain class, architecture class, planned part count, top-risk areas
[ ] For apps > 3,000 lines: wait for user acknowledgment before Part 2
    → Claude Code: Use AskUserQuestion to confirm before proceeding
[ ] Create progress tracker with TodoWrite listing all planned parts
```

### Part Structure

| Part | Focus | Non-Negotiable Deliverables |
|------|-------|-----------------------------|
| **P1** | Pre-Flight · Inventory · Architecture | Feature Preservation Ledger, Constraint Map, Design Identity Confirmation, Domain Rule Verification Table, Workflow Map, Audit Plan |
| **P2** | Domain Logic & Business Rules | Rule-by-Rule Verification, Formula Test Vectors, Data Accuracy Report, Temporal/Timezone Audit |
| **P3** | Security · Privacy · Compliance | Threat Model, Sensitive Data Inventory, Attack Surface Map, Compliance Gap List |
| **P4** | State · Data Integrity · Persistence | State Schema Audit, Validation Gap Report, Data Flow Diagram, Corruption Paths |
| **P5** | Performance · Memory · Loading | Web Vitals Estimate, Resource Budget Table, Memory Leak Inventory, Computation Bottlenecks |
| **P6** | Visual Design · Polish · Design System | Design Token Audit, Visual Rhythm Analysis, Component Quality Scorecard, Polish Gap Inventory |
| **P7** | UX · Information Architecture · Copy | Flow Analysis, IA Audit, Copy Quality Inventory, Interaction Pattern Audit |
| **P8** | Accessibility | Full WCAG 2.1 AA Checklist, Screen Reader Trace, Keyboard Nav Map, ARIA Correctness |
| **P9** | Browser · Platform · Compatibility | Cross-Browser Matrix, PWA Audit, Mobile/Touch Audit, Network Resilience Matrix |
| **P10** | Code Quality · Architecture · Optimization | Dead Code Inventory, Duplication Map, Naming Audit, Structural Analysis, Optimization Opportunities |
| **P11** | AI / LLM Integration *(activated when External APIs or AI/LLM field in §0 references any AI provider)* | Prompt Injection Surface, Output Sanitization Audit, Streaming Error Handling, Token/Cost Risk, Hallucination Exposure |
| **P12** | Internationalization & Localization | Hardcoded String Inventory, Locale-Sensitive Format Audit, RTL Audit, i18n Completeness Report |
| **P13** | Development Scenario Projection *(§O — see Growth Context in §0)* | Scale Cliff Analysis, Feature Addition Risk Map, Technical Debt Compounding Map, Dependency Decay Forecast, Constraint Evolution Analysis, Maintenance Trap Inventory |
| **P14+** | Domain Deep Dives | App-specific: probability math, financial precision, medical logic, AI integration, API contracts, etc. |
| **P-R&D** | Research, Development & Improvement *(§X)* | Existing Feature Health Audit, Feature Gap Matrix, Improvement Prioritization, R&D Roadmap, Experimentation Protocol |
| **P-POL** | Polishing & Restructuration *(§XI)* | App Comprehension Record, Coherence Fracture Map, Polish Passes (0–6), Code Restructuring, Architecture Evolution, Quality Gates |
| **Final** | Summary Dashboard | Findings table, Root Cause Analysis, Compound Chains, Quick Wins, Optimization Roadmap, Polish Roadmap |

---

## IV. AUDIT DIMENSIONS

> 120+ dimensions across 15 categories. Every dimension applies to every app.
> Domain Classification (§I.1) determines depth and severity multipliers.

---

### CATEGORY A — Domain Logic & Correctness

The most consequential category. An app that looks polished but produces wrong output is harmful. Every point here verifies against §0 Domain Rules.

#### §A1. BUSINESS RULE & FORMULA CORRECTNESS
- **Constants verification**: Every named constant vs §0 expected value. Flag every discrepancy immediately, regardless of size.
- **Formula reproduction**: For every non-trivial formula — reproduce it by hand with known inputs and compare to actual code output.
- **Operator precision**: `>` vs `>=`, `&&` vs `||`, `Math.floor` vs `Math.round` vs `Math.ceil` — each one changes behavior at boundaries.
- **Order of operations**: Integer vs float division? Parenthesization correct? Associativity assumptions?
- **Precision strategy**: Does rounding happen at computation, at display, or both? Are rounding errors accumulating across a multi-step pipeline?
- **Units consistency**: Values that look similar but differ (percentages as 0–1 vs 0–100, ms vs s, cents vs dollars) — always handled correctly?
- **Domain invariants**: Properties that must always hold true (probabilities sum to 1.0, totals match line items, age ≥ 0) — are they enforced or just hoped for?
- **Boundary values**: Test the exact edges: `0`, `1`, `max_valid`, `max_valid + 1`, `-1`, `null`, `undefined`, `NaN`, empty string.

#### §A2. PROBABILITY & STATISTICAL CORRECTNESS *(deepened for gambling/gacha/actuarial/analytics)*
- **Model validity**: Is the mathematical model appropriate for the actual stochastic process (Markov, independence, memoryless)?
- **CDF integrity**: Does the cumulative distribution reach ≥0.9999 within the supported domain? Residual probability accounted for?
- **Expected value verification**: Computed EV matches closed-form solution where one exists?
- **Monte Carlo adequacy**: Sufficient trial count for the required precision? Standard error reported to user?
- **Numerical stability**: Underflow at very small probabilities? Overflow at very large inputs?
- **Known-good test vectors**: ≥3 manually-verified {input → expected output} pairs tested against the engine.
- **Uncertainty communication**: Results labeled as estimates? Confidence intervals disclosed?

#### §A3. TEMPORAL & TIMEZONE CORRECTNESS
- **UTC offset correctness**: Every region's offset verified. Named timezones vs hardcoded offsets (named are safer).
- **DST transitions**: For any region with DST (US, EU, AU, etc.) — spring-forward and fall-back handled? Countdowns crossing a DST boundary?
- **Epoch arithmetic**: Timestamps in ms vs s — mixed? Far-future overflow?
- **Relative time**: "X days until" — off-by-one at midnight? Timezone-aware?
- **Scheduled events**: Daily/weekly resets — correct simultaneously for all supported timezones?
- **Stale temporal data**: Hardcoded dates that were correct at write-time but have since passed?

#### §A4. STATE MACHINE CORRECTNESS
- **Reachable invalid states**: Can the app arrive at a combination of state values that has no defined meaning?
- **Transition completeness**: Every event from every state — is the transition defined, or does some combination produce undefined behavior?
- **Guard conditions**: Transitions that should only fire under certain conditions — are guards actually enforced?
- **Race conditions**: Rapid clicks, concurrent tabs, Worker messages arriving simultaneously — state consistency maintained?
- **Idempotency**: Actions safe to repeat (refresh, double-click, re-import) — produce the same result?

#### §A5. EMBEDDED DATA ACCURACY
- **Named entity correctness**: Every named item (character, product, rate, rule, material) verified against authoritative source in §0.
- **Staleness**: Data accurate as of which version/date? Is that version documented? What has changed since?
- **Cross-reference integrity**: Entity A references Entity B — B exists and has the expected attributes?
- **Completeness**: All expected entities present? Gaps in coverage?
- **Relationship correctness**: Parent-child, lookup, many-to-many — all bidirectionally consistent?

#### §A6. ASYNC & CONCURRENCY BUG PATTERNS

These bugs are invisible in single-path testing but surface reliably in real usage.

- **Stale closure captures (React)**: `useEffect` callbacks capturing state/props via closure — if the dependency array is missing or incomplete, the effect runs with stale values. Classic symptom: a `setInterval` inside `useEffect` reads state that never updates after the initial render.
- **`async` in `forEach`**: `array.forEach(async item => { ... })` — `forEach` does not await Promises. All async operations fire simultaneously and errors are silently swallowed. Use `for...of` with `await` or `Promise.all()` instead.
- **Promise swallowing**: `.catch(() => {})` or bare `.catch(console.error)` with no recovery path — the operation silently fails, the app continues in a broken state the user cannot detect or recover from.
- **Unhandled Promise rejections**: `async` functions called without `await` and without `.catch()` — the rejection is unhandled. Modern browsers fire an `unhandledrejection` event but give the user no feedback. Search for every `asyncFn()` call pattern (no `await`, no `.then/.catch`).
- **Race condition on sequential async calls**: Two rapid user actions each fire an async request — the second resolves first, then the first overwrites the newer result. Fix: use `AbortController` to cancel the previous request, or track a request sequence number and discard stale responses.
- **Missing `useEffect` cleanup**: Effect creates a subscription, event listener, timer, or WebSocket — but the cleanup function (`return () => { ... }`) is absent. Causes resource leaks and React's "state update on unmounted component" warning.
- **Concurrent state writes from multiple effects**: Multiple `useEffect` hooks each calling `setState` on the same state slice, triggered by the same event — one effect silently overwrites another's result. Execution order is deterministic but non-obvious to future maintainers.
- **`setTimeout`/`setInterval` drift**: Using `setInterval` for a countdown timer — each tick drifts slightly due to JS event loop variance. After minutes, a visible desynchronization appears. Fix: use absolute timestamp deltas (`targetTime - Date.now()`).
- **Async constructor / mount pattern**: Critical initialization logic placed in `useEffect` — the component renders once with empty/default state before the effect runs. If no loading state is shown, the user sees a flash of wrong data or empty UI.

#### §A7. JAVASCRIPT TYPE COERCION & IMPLICIT CONVERSION TRAPS

JS silently converts types in ways that produce wrong results without throwing errors — the code runs, the numbers look plausible, and the bug is invisible until edge cases hit.

- **`==` vs `===`**: `"0" == false` → `true`. `null == undefined` → `true`. `[] == false` → `true`. `"" == 0` → `true`. Any `==` comparison with non-identical types is a potential silent misclassification. Search the entire codebase for `==` (not `===`) and assess each one.
- **`+` operator with mixed types**: `"5" + 3` → `"53"`. Any `+` operation touching user input or API response data (which arrives as strings) silently concatenates instead of adding. Always explicitly convert: `Number(input) + 3` or `parseInt(input, 10) + 3`.
- **`parseInt` without radix**: Always `parseInt(str, 10)`. Also: `parseInt("3.5px")` → `3` — stops at the first non-numeric character. Is that the intended behavior for inputs like "3.5rem", "10%", "N/A"?
- **`parseFloat` on formatted numbers**: `parseFloat("1,234.56")` → `1`. Any user-formatted or locale-formatted number string must be normalized (strip commas, currency symbols) before parsing.
- **Falsy value cascade**: `0`, `""`, `null`, `undefined`, `NaN`, and `false` are all falsy. `if (count)` is `false` when `count === 0` — a common off-by-one source for zero-item states. `if (name)` is `false` when `name === ""`. Use explicit comparisons: `count !== null && count !== undefined` or `count != null` (intentional `!=`).
- **NaN propagation**: `NaN !== NaN` — the only value not equal to itself. `isNaN("hello")` returns `true`; so does `isNaN(undefined)` — they mean different things. Use `Number.isNaN()` for strict detection. Any arithmetic involving NaN silently produces NaN, which propagates through the entire calculation pipeline, ultimately displaying as `NaN` or `0` (after `|| 0` guards) with no error.
- **Array/object truth inconsistency**: `[]` and `{}` are truthy. `Boolean([])` → `true`, but `[] == false` → `true` via type coercion. Conditionals that expect to distinguish "no data" from "empty array" must use `.length` checks, not truthiness.
- **Numeric string comparisons**: `"10" > "9"` → `false` (string comparison: `"1" < "9"`). If sort comparators or range checks operate on uncoerced string inputs, ordering silently fails for numbers ≥ 10.
- **`typeof null === "object"`**: Historical JS bug, unfixable. `if (typeof x === "object")` is true for `null`. Always add `&& x !== null` for any object type check.
- **Implicit global variable creation**: A variable assigned without `let`/`const`/`var` inside a function silently becomes a property on `window`. Is `"use strict"` enabled globally to catch this class at runtime?

---

### CATEGORY B — State Management & Data Integrity

#### §B1. STATE ARCHITECTURE
- **Schema completeness**: Every field — type, valid range, default value, null/undefined behavior, documented purpose.
- **Normalization**: Any piece of data represented in two places that can diverge? Single source of truth for everything?
- **Derived state staleness**: Computed values re-derived on demand vs cached — if cached, what invalidates the cache?
- **Initialization correctness**: Default state valid for both fresh install and state-restored-from-storage?
- **Reset completeness**: State reset/clear — leaves orphaned storage keys? Misses any field?

#### §B2. PERSISTENCE & STORAGE
- **Completeness**: Every user-meaningful state field persisted? Any transient UI state accidentally persisted?
- **Schema versioning**: Version identifier in stored data? Migration logic for schema evolution across app versions?
- **Quota management**: localStorage size monitored? User warned approaching 5MB? `QuotaExceededError` caught gracefully?
- **Concurrent write safety**: Multiple tabs writing simultaneously — race condition? Data loss?
- **Cold start validation**: Persisted state parsed and validated against current schema before use? Handles corrupted state from a previous bug?
- **Sensitive data in storage**: Tokens, passwords, PII stored unencrypted in localStorage?

#### §B3. INPUT VALIDATION & SANITIZATION
- **Coverage**: Every user-facing input validated — none bypassed?
- **Type enforcement**: Silent type coercion (`"0" == 0`, `parseInt(undefined)`) producing wrong values?
- **Range enforcement**: Min/max limits — enforced at input layer, computation layer, or display layer (or none)?
- **Boundary testing**: For each input: test `0`, `max`, `max+1`, `-1`, `""`, `null`, `NaN` — what happens?
- **NaN/Infinity propagation**: Division by zero? `parseInt("")` returning NaN silently becoming 0 in downstream math?
- **Validation UX**: Error messages tell the user what went wrong and what they should enter instead.

#### §B4. IMPORT & EXPORT INTEGRITY
- **Import safety**: `JSON.parse` in try/catch everywhere? Prototype pollution via `__proto__`/`constructor`/`prototype` keys?
- **Size enforcement**: Maximum import size enforced before parsing begins?
- **Schema validation**: Imported data validated against expected schema — not blindly spread into state?
- **Preview before commit**: User sees what will change before confirming?
- **Rollback capability**: Pre-import state snapshot saved? Import undoable?
- **Round-trip fidelity**: `export → import → export` — both exports identical?
- **Partial import**: Can user import a subset without overwriting unrelated state?
- **Export completeness**: 100% of user state in export? Anything missing?
- **Self-describing schema**: Version field and field descriptions in export JSON, so external tools can parse it?

#### §B5. DATA FLOW MAP
Produce a text diagram: `User Input → Validation → State → Computation → Display`
At every arrow: What can go wrong? What protection exists? What is the gap?

#### §B6. MUTATION & REFERENCE INTEGRITY

Mutation bugs are among the hardest to find — the code looks correct but silently operates on shared references, causing distant, non-reproducible state corruption.

- **Direct state mutation (React/Vue)**: `state.items.push(item)` or `state.count++` mutates the existing reference — the framework's reconciler sees the same reference and may not re-render, or renders with partially updated state. Always produce new references: `setState(prev => ({ ...prev, items: [...prev.items, item] }))`.
- **`Object.assign` shallow copy trap**: `Object.assign({}, state)` creates a shallow copy — nested objects and arrays are still shared references. Mutating a nested property mutates both the copy and the original. Use structured clone, spread recursively, or an immutability library for nested state.
- **`Array.sort()` and `Array.reverse()` mutate in place**: Calling `items.sort(compareFn)` in a render path or derived value mutates the source array. Use `[...items].sort(compareFn)` to sort a copy.
- **Shared default parameter objects**: `function createItem(options = DEFAULT_OPTIONS)` where `DEFAULT_OPTIONS` is a module-level object — if any caller mutates `options`, subsequent callers receive the already-mutated object as their "default". Always spread defaults: `{ ...DEFAULT_OPTIONS, ...options }`.
- **Closure accumulation across calls**: A function closes over an array or object and mutates it on every call — each invocation accumulates state from all previous calls, not starting fresh. Particularly subtle in callbacks registered during module initialization.
- **Props mutation (React)**: Directly mutating a prop value (e.g. `props.items.push(...)`) instead of triggering a parent state update — violates unidirectional data flow and causes stale state across renders in ways that are very difficult to trace.
- **Synthetic event object pooling (React < 17)**: Accessing `event.target.value` inside a `setTimeout` or async callback — React's synthetic event pool reuses the event object, so accessing it after the handler returns returns `null`. React 17+ removed pooling, but if React version is unknown: check all async event accesses.
- **Immer `produce` misuse**: Mutations outside the Immer draft context, returning both a mutation and a value from the same producer, or forgetting to return from a non-mutating producer — all cause silent state corruption that is extremely difficult to trace.

---

### CATEGORY C — Security & Trust

#### §C1. AUTHENTICATION & AUTHORIZATION
- **Credential storage**: Passwords, tokens, API keys — never in localStorage unencrypted, never in source code.
- **Hash comparison**: Client-side hash comparisons — constant-time? Hash visible in source (extractable for offline brute-force)?
- **Lockout bypass**: Attempt-rate limiting stored in localStorage — clearable by user to reset counter?
- **Session management**: Token expiry handled? Idle logout? Session fixation?
- **Privilege escalation**: Can a user manipulate localStorage/state to access features beyond their authorization?

#### §C2. INJECTION & XSS
- **innerHTML / dangerouslySetInnerHTML**: Any use? Is the content user-supplied or from an external source?
- **DOM-based XSS**: User strings inserted into `className`, `href`, `src`, `style`, `data-*` attributes?
- **Dynamic code execution**: `eval()`, `Function()`, `setTimeout(string)`?
- **URL injection**: User-controlled values concatenated into URLs? Open redirect?
- **CSS injection**: User values in inline `style` strings?

#### §C3. PROTOTYPE POLLUTION & IMPORT SAFETY
- **JSON.parse safety**: Every parse call in try/catch — including ones that "can't fail"?
- **Prototype pollution**: Imported objects merged/spread without filtering `__proto__`, `constructor`, `prototype`?
- **Property collision**: Imported data keys capable of shadowing expected application properties?

#### §C4. NETWORK & DEPENDENCIES
- **All HTTPS**: Mixed-content risk from any HTTP resource?
- **SRI (Subresource Integrity)**: `integrity` attributes on CDN `<script>` and `<link>` tags? Without SRI, a CDN compromise serves malicious code.
- **External data tracking**: Third-party image hosts, CDNs — user IP/referrer logged without disclosure?
- **CORS**: External API CORS handling correct? Credentials in cross-origin requests?
- **CSP**: Content Security Policy present? `unsafe-inline`/`unsafe-eval` requirements that undermine it?

#### §C5. PRIVACY & DATA MINIMIZATION
- **PII inventory**: What personal data is collected, stored, or transmitted? Is each piece necessary?
- **URL leakage**: State in hash/query params leaks via browser history, referrer headers, server logs?
- **Third-party fingerprinting**: CDNs, analytics, presence systems — disclosed to user?
- **Export sensitivity**: Export JSON contains data the user didn't know was being recorded?

#### §C6. COMPLIANCE & LEGAL
- **GDPR/CCPA**: Personal data processed? Right to deletion? Privacy policy linked?
- **Age restrictions**: Gambling-adjacent, adult, or violence content — age gating present?
- **IP/Copyright**: Third-party copyrighted assets used? Attribution and disclaimer present?
- **Financial regulations**: App gives financial advice? Regulatory disclaimer?
- **Medical regulations**: App gives health guidance? "Not medical advice" disclaimer prominent?
- **Accessibility law**: ADA/EN 301 549 obligations relevant to this app?

#### §C7. MOBILE-SPECIFIC SECURITY *(activated for Android/iOS apps)*
- **Permission audit**: Are all declared permissions actually used? Over-requesting permissions signals privacy issues and can trigger store rejection.
  - Android: Check `AndroidManifest.xml` `<uses-permission>` vs actual usage in code
  - iOS: Check `Info.plist` usage descriptions vs actual API calls
- **Exported components**: Android — are Activities/Services/BroadcastReceivers unnecessarily exported? (`android:exported="true"` without intent filters = attack surface)
- **Data storage security**: Sensitive data in SharedPreferences/UserDefaults without encryption? Use EncryptedSharedPreferences / Keychain.
- **WebView security**: `setJavaScriptEnabled(true)` + `addJavascriptInterface()` = injection surface. `setAllowFileAccess(true)` = local file read risk.
- **Network security config**: Android — is `android:networkSecurityConfig` present? Does it allow cleartext traffic unnecessarily?
- **ProGuard/R8 rules**: Are security-critical classes excluded from obfuscation appropriately? Are reflection-dependent classes kept?
- **Content Provider exposure**: Android — `android:exported` on ContentProviders without proper permission checks = data leak.
- **Deep link validation**: Are deep links/app links validated against expected patterns, or can arbitrary URIs trigger sensitive actions?
- **Clipboard security**: Sensitive data (passwords, tokens) copied to clipboard without timeout/clearing?

---

### CATEGORY D — Performance & Resources

#### §D1. RUNTIME PERFORMANCE
- **Main thread blocking**: Computations >50ms on the main thread — UI freeze during execution?
- **Worker offloading**: Expensive algorithms in a Worker? Message passing correct? Fallback if Worker unavailable?
- **Unnecessary re-renders** (React): Every component that re-renders when it shouldn't. `memo()` comparators correct? Missing `useCallback`/`useMemo` deps?
- **List virtualization**: Grids/lists with 100+ items — virtualization needed? Jank with current approach?
- **Layout thrashing**: Reading `offsetWidth`/`scrollHeight` inside a write loop? Forces repeated reflows.
- **Debounce/throttle**: High-frequency events (input, scroll, resize) handled without overwhelming the main thread?
- **Cold start computations**: Expensive work triggered on mount instead of lazily on demand?

#### §D2. WEB VITALS & LOADING
- **LCP**: Largest element on first load — blocked by scripts? Image without preload?
- **FID/INP**: Long tasks during load — time to interactive?
- **CLS**: Images without `width`/`height`? Dynamic content injected above existing content? Font reflow?
- **Render-blocking scripts**: CDN scripts without `defer`/`async` — which ones block first paint?
- **FOUC**: CSS loaded after content renders?
- **Parse time**: Large single-file apps — JS parse/compile on low-end mobile (4× CPU throttle)?
- **Resource hints**: `preconnect`/`dns-prefetch` for CDN origins? `preload` for hero images?

#### §D3. RESOURCE BUDGET
Produce this table for the app:

| Resource | Source | Est. Size | Load Strategy | Critical Path? | Optimization? |
|----------|--------|-----------|--------------|----------------|--------------|
| App code | inline/CDN | ? kb | blocking | yes | ? |
| Framework | CDN | ~130kb gz | blocking | yes | lighter alt? |
| … | … | … | … | … | … |
| **Total** | | **? kb** | | | |

- 3G first-load estimate (total / ~1.5 Mbps)?
- What % of app code executes in a typical session (unused code ratio)?

#### §D4. MEMORY MANAGEMENT
- **Closure leaks**: Closures holding references to large objects that should be GC'd?
- **Event listener leaks**: Every `addEventListener` has a corresponding `removeEventListener` in cleanup?
- **Timer leaks**: Every `setInterval`/`setTimeout` cleared on unmount?
- **Worker lifecycle**: Terminated when no longer needed? Multiple instances accidentally spawned?
- **Blob URL revocation**: `URL.createObjectURL` — matching `URL.revokeObjectURL` called?
- **Computation array retention**: Heavy tables (DP, MC) released after use or held in closure?
- **Canvas/WebGL cleanup**: Contexts and canvases disposed on unmount?

#### §D5. MOBILE-SPECIFIC PERFORMANCE *(activated for Android/iOS apps)*
- **Coroutine/async lifecycle**: Are coroutines properly scoped to ViewModel/Fragment lifecycle? Orphaned coroutines running after fragment destruction?
- **RecyclerView / LazyColumn optimization**: ViewHolder pattern correct? DiffUtil used? No nested scrolling conflicts? ViewType reuse?
- **Image loading**: Thumbnails appropriately sized? Image caching configured? Large bitmaps loaded on main thread?
- **Database queries on main thread**: Room/CoreData queries dispatched to background? No `runBlocking` on main thread?
- **Fragment transaction overhead**: Excessive fragment replacements causing layout thrashing? Proper use of `replace` vs `add`?
- **APK/IPA size**: ProGuard/R8 shrinking enabled? Unused resources stripped? Large assets that could be on-demand?
- **Process death recovery**: Is state saved via `onSaveInstanceState` / ViewModel SavedState for critical user data? Process death = complete state loss without this.
- **ANR risk**: Any operation > 5s on main thread triggers ANR dialog (Android). Check file I/O, network calls, heavy computation.
- **Battery impact**: Unnecessary background work? Wake locks held too long? Location updates too frequent?

---

### CATEGORY E — Visual Design Quality & Polish

> This category treats visual design as a professional discipline, not an afterthought.
> The goal is to make the app's existing design vision more **refined, consistent, and polished** — not to replace it with generic conventions.
> §0 Design Identity is protected throughout. All findings improve toward the app's own aesthetic, not away from it.

> **Deep visual work:** When this audit's §E findings reveal systemic visual design issues — or when the user specifically requests a design audit, asks to "make it feel like [X]", or references a named aesthetic — the `design-aesthetic-audit-SKILL.md` skill should be invoked as a companion. It covers 95 sections of visual-design-specific analysis (component character, copy alignment, illustration, data viz, token architecture, state design, responsive character, source material intelligence) that go well beyond what §E covers here. Route to it via §COMPANION in that skill, which maps directly to §E/P6 in this audit.

#### §E1. DESIGN TOKEN SYSTEM
- **Spacing scale**: Is every padding and margin value from a coherent mathematical scale (4/8/12/16/24/32/48/64)? List every one-off value like `p-[13px]` or `margin: 7px`. Each one is a token debt.
- **Color palette architecture**: Is the color system built on a small set of intentional tokens, or are there dozens of slightly-different hardcoded values? List near-duplicate colors and consolidate candidates.
- **Typography scale**: List every unique `font-size` in the codebase. Do they form an intentional modular scale (e.g., 12/14/16/20/24/32px), or are there arbitrary in-between values?
- **Font weight semantics**: Is each weight (`normal`, `medium`, `semibold`, `bold`) used for a consistent semantic purpose? Mixing `font-bold` and `font-semibold` for "emphasis" is token inconsistency.
- **Border radius system**: Are `rounded-*` values consistent by component type? Cards all use the same radius? Buttons the same? Inconsistency in radius reads as unprofessional at a subconscious level.
- **Shadow hierarchy**: Is there a shadow scale (e.g., `sm` for cards, `md` for modals, `lg` for popovers)? Or arbitrary per-component shadows?
- **Z-index governance**: Is stacking order explicitly managed? List every z-index value used. Collisions between layers (modals, toasts, dropdowns, sticky headers)?
- **Animation token set**: Are duration values from a consistent set (e.g., 100/200/300/500ms)? Are easing curves consistent for the same type of motion?
- **Token naming as documentation**: Are token names semantic (what they *mean*) rather than presentational (what they *look like*)? `--color-action-primary` scales to theming and dark mode; `--color-blue-500` does not. A well-named token system is itself product documentation — and for paid/multi-tenant products, also scales to whitelabeling and multi-brand use. For any product nature, naming tokens semantically reduces the cost of every future visual change.

#### §E2. VISUAL RHYTHM & SPATIAL COMPOSITION
- **Vertical rhythm**: Is there consistent spacing between sections, between cards, between form groups? Inconsistent vertical spacing destroys the feeling of order even when individual components look fine.
- **Density consistency**: Similar components (cards, list items, table rows) have similar internal density. One card with 24px padding and another with 12px padding on the same screen reads as broken.
- **Alignment grid**: Do elements align to a consistent invisible grid? Are there elements that appear to "float" without visual anchoring?
- **Whitespace intention**: Is whitespace used actively to group related items and separate unrelated ones? Or is it applied without rhythm (some areas cramped, others sparse)?
- **Proportion**: Do related elements (label + value, icon + text, header + content) feel proportionally balanced?
- **Focal point clarity**: On every key screen — is there one clear visual focal point that draws the eye first? If the answer is "everything has equal visual weight," the design has no hierarchy and users don't know where to look. Identify the intended focal point on each primary view, then assess whether the current visual treatment actually draws the eye there.
- **Visual weight distribution**: Is visual mass (size, color saturation, contrast, bold weight) distributed intentionally across the screen? Heavy visual elements clustering in one corner makes the layout feel unbalanced. Scan each primary view for unintentional visual weight accumulation.

#### §E3. COLOR CRAFT & CONTRAST
- **Color harmony**: Does the accent color work harmoniously with the background and surface colors? Is there a clear hierarchy: background → surface → elevated surface → accent?
- **Dark mode craft**: For dark themes — are dark surfaces using near-black with slight hue (e.g., `#0f1117` with a hint of blue) rather than pure black (except intentional OLED)? Pure neutral dark often reads as less refined than dark with character.
- **Accent consistency**: Is the accent color used consistently as an emphasis signal? Or does it appear so frequently that it loses meaning?
- **Color temperature coherence**: Does the palette stay within a consistent temperature range? A warm orange accent on a cool blue-gray dark surface creates subconscious tension unless intentional.
- **WCAG contrast compliance**: Every text/background combination meets 4.5:1 (normal text) or 3:1 (large/bold). Pay special attention to: muted grays on dark, colored text on colored backgrounds, placeholder text on inputs.
- **Non-text contrast**: UI components (input borders, icon buttons, focus rings) meet 3:1 (WCAG 1.4.11).
- **State colors**: Hover, active, disabled, error, success, warning — distinct, consistent, and on-brand?
- **Color psychology alignment**: Does the palette's psychological character match the app's emotional target (§0)? Blues and cool grays signal reliability and precision — appropriate for financial and medical tools. Warm oranges and greens signal energy and growth — appropriate for gamified or wellness tools. Misalignment between color psychology and domain creates subconscious friction.
- **Color saturation calibration**: Oversaturated colors (`#FF0000`, `#00FF00`) signal low craft regardless of product nature — a pure green is less refined than a calibrated `#14b8a6`. Assess the saturation and lightness of the palette: does it feel purposeful, or do any values feel like the first pick from a color wheel? *For paid/professional tools*: this directly affects trust and willingness to pay. *For fan/creative tools*: this affects whether the palette feels artistically considered or placeholder-level. The standard changes; the question doesn't.

#### §E4. TYPOGRAPHY CRAFT
- **Heading hierarchy**: Is there a clear visual hierarchy between h1/h2/h3/body/caption levels? Can a user scan the page and immediately identify the most important information?
- **Line length**: Body text lines ideally 45–75 characters. Very short or very long lines hurt readability.
- **Line height**: Body text typically 1.4–1.6× for readability. Tight line height on dense text reads as cramped.
- **Font pairing**: If using multiple typefaces — do they complement or conflict? Consistent use of primary/secondary/monospace roles?
- **Letter spacing**: Display/heading text often benefits from slightly negative tracking (`-0.01em` to `-0.03em`) for refinement. Is this applied consistently to large text?
- **Text rendering**: `-webkit-font-smoothing: antialiased` applied for crispness on dark backgrounds?
- **Label quality**: Form labels, column headers, section titles — concise, sentence-case consistently applied, unambiguous?
- **Typography as character signal** `[A2][A3][A4]`: The typeface choice communicates personality before a single word is read. Assess whether the typeface matches the personality in §0, using the axis profile to determine what "correct" means for this specific app:
  - *High commercial intent (A1)*: Typeface credibility matters — a humanist sans (Inter, Plus Jakarta Sans) signals approachability; a geometric sans (DM Sans, Geist) signals precision; a transitional serif signals authority. Wrong tier here is a trust problem.
  - *Strong subject visual identity (A4)*: Does the typeface feel tonally coherent with the subject? A gritty crime drama tool using a soft rounded font, a classical music app using a harsh display face, a hiking companion using a cold corporate sans — all represent tonal mismatches between typeface and subject.
  - *Expert/practitioner audience (A3)*: Type density and precision are signals of domain competence. A clinical tool with oversized, rounded type feels like it's talking down to experts. A financial terminal with a decorative display font feels wrong.
  - *Emotionally sensitive / high-stakes context (A2)*: Typeface warmth, weight, and size directly affect emotional register. Sharp, compressed, or overly stylized typefaces increase anxiety in sensitive contexts.
  - *Aesthetic IS the product (A5)*: Typeface is part of the output's visual experience — the bar is highest here.
  Whatever the context — if the typeface contradicts the intended personality, name a specific alternative that would serve it better within the app's constraints.
- **Type craft signals** `[A1][A3][A5]`: The relevant refinements depend on the app's axis profile:
  - *High commercial / professional audiences*: tabular nums for aligned number columns (`font-variant-numeric: tabular-nums`), optical size adjustments for display text, consistent lining vs oldstyle figures, proper typographic quotes.
  - *Expert/dense-data contexts*: monospaced or tabular numerals for scannable data columns, appropriate weight for scanability under time pressure.
  - *Aesthetic-primary / creative tools*: OpenType features as expressive tools — ligatures, alternates, stylistic sets — used intentionally.
  - *Any product*: Is there any typographic personality (weight contrast, tracked caps, a purposeful accent) that makes the app feel designed rather than defaulted? Intentionality — not prestige — is the goal.

#### §E5. COMPONENT VISUAL QUALITY
- **Button states completeness**: Every button variant has all five states: default, hover, active/pressed, focus (keyboard-visible), disabled. Missing states feel broken during interaction.
- **Input field states**: Default, focus, filled, error, disabled. The focus ring must be clearly visible.
- **Card design quality**: Internal padding consistent. Border or shadow — not both unless intentional. Corner radius consistent. Content alignment consistent across all instances.
- **Badge/chip/tag design**: Consistent padding, radius, typography across all instances.
- **Modal/dialog quality**: Consistent backdrop opacity, border/shadow, corner radius, header/body/footer structure. Close button always in same position and same size.
- **Icon quality**: All icons from the same family at the same base size. Mixed icon families are visually noisy. Icons sized to optical weight, not just pixel dimensions.
- **Divider usage**: Lines/dividers used consistently — not as decoration but as structural separators. Too many dividers fragment the layout.
- **Image presentation**: Images consistently cropped (same aspect ratios for same context), with consistent corner radius treatment.

#### §E6. INTERACTION DESIGN QUALITY
- **Hover feedback**: Every interactive element has a perceptible hover state that communicates interactivity. Elements that look interactive but have no hover state confuse users.
- **Active/pressed feedback**: Pressing a button should feel physically responsive — typically a slight scale-down or color deepening.
- **Transition quality**: Transitions should feel deliberate and smooth. Abrupt appearance/disappearance, or overly long/bouncy transitions, break the professional feel.
- **Loading state quality**: Spinners vs skeleton screens — skeleton screens preserve layout and feel more polished for content-loading. Spinners are appropriate for actions.
- **Animation narrative**: Every motion should tell a story about the relationship between UI states. An element sliding in from the left implies it came from somewhere left. Fade-in from nothing implies it was created. Are animations telling the right story?
- **Empty state design**: Empty states are a design opportunity — they should be designed, not blank. Clear visual, helpful message, a clear call to action.
- **Error state design**: Inline errors positioned immediately adjacent to the field that caused them. Not just color — includes icon and descriptive text.
- **Animation as character signal** `[A2][A4][A5]`: The right motion vocabulary is derived from the axis profile — not from a product category:
  - *Focus-critical / high-stakes / high-frequency use (A2)*: Motion is a cognitive tax. Every animation must justify itself — does it serve the user's task, or serve visual interest? Lean toward 100–150ms, ease-out, nothing bouncy or attention-seeking.
  - *Emotionally sensitive contexts (A2)*: Abrupt or jarring transitions increase anxiety. Slow, smooth, and predictable motion is a safety requirement here, not a style preference.
  - *Creative / exploratory / leisure contexts (A2)*: Expressive motion is appropriate — spring physics, slight overshoot, personality without chaos.
  - *Strong subject visual identity (A4)*: The motion character can honor the subject's tonal register — urgency, calm, playfulness, weight — whatever the subject carries. This is a fidelity opportunity, not a decoration question.
  - *Aesthetic IS the product (A5)*: Animation may be the primary value — assess it as output quality, not UI polish.
  - *Any context*: Simple and consistent beats complex and inconsistent. One well-chosen transition applied throughout outperforms five different ones.
  Name the 1–2 specific timing or easing changes that would bring the motion vocabulary into alignment with this app's axis profile.
- **Delight moments** `[A1][A2][A4]`: The highest-impact moments for craft investment are derived from the use context and subject identity — not from a product tier:
  - *High-frequency tools (A2)*: The small moment that makes a daily tool feel good to use — a snappy response, a clean success state, an efficient transition at the right place.
  - *Emotionally sensitive tools (A2)*: Warmth and gentleness at key moments — a kind empty state, a calm confirmation, nothing abrupt when the user is vulnerable.
  - *Creative / expressive tools (A2)*: Moments that feel generative and alive — the tool responding as a collaborator, not just executing commands.
  - *Strong subject identity (A4)*: Moments that feel authentic to the subject and community — a result displayed in a way that resonates with how the audience experiences this subject.
  - *Any app*: The moment the app delivers its primary value — is it presented with any intentionality, or does the result just appear? Even a free utility benefits from treating its output moment with care.
  For each high-impact moment — is there any brief, purposeful visual acknowledgment? If not, it is a craft gap regardless of axes.
- **Physical responsiveness**: The best interfaces feel physical. Buttons compress, drawers slide, modals lift. Assess whether the interaction model feels flat and digital or has a quality of physical responsiveness — and whether that matches the product's intended personality.

#### §E7. OVERALL VISUAL PROFESSIONALISM
- **Design coherence**: Does the app feel like it was designed as a whole, or like different sections were designed independently? Incoherence is visible even when users can't articulate it.
- **Attention to detail**: Pixel-perfect alignment? No 1-pixel misalignments on borders? No slight gaps where elements should touch? Details matter at the professional level.
- **Brand consistency**: Is the app's visual identity consistent from section to section? Would a user recognize a new section as part of the same app?
- **Polish delta**: For each section — list specific changes that would move it from "functional" to "intentional" within the existing design language and axis profile.
- **Polish level assessment** `[A1][A2][A5]`: The right polish standard depends on the axes — not on a tier label:
  - *High commercial intent (A1)*: Verify these credibility signals — consistent 4/8-based spacing — subtle shadows with intentional offset and blur — smooth 200–300ms transitions — letter-spacing on headings — antialiased type — hover states that feel physical — skeleton loaders that mirror content shape — contextual empty states — confirmation animations on success.
  - *Focus-critical / invisible-aesthetic contexts (A2)*: The polish goal inverts — the absence of distraction IS the polish. Assess how little the interface demands of the user's attention while still feeling finished and trustworthy.
  - *Emotionally sensitive contexts (A2)*: Polish means warmth and safety — gentle corners, calm palette, generous spacing, transitions that feel unhurried. Clinical sharpness is a polish failure here.
  - *Aesthetic-primary contexts (A5)*: Polish means the UI chrome recedes so the output shines. Evaluate how well the interface disappears in favor of what it produces.
  - *Any app*: The universal baseline — is there one detail that clearly took extra effort? Does the app look *intentional* rather than defaulted? Is spacing consistent enough that nothing feels accidental?

#### §E8. PRODUCT AESTHETICS — DERIVED FROM AXIS PROFILE

> **This section is driven entirely by the Five-Axis profile from §I.4.** There are no fixed branches for "paid" vs "free" — instead, each question activates based on which axes are present. Run every tagged item whose axis is active in the profile; skip or substantially reframe items whose axis is inactive.

---

**`[A1]` COMMERCIAL INTENT ACTIVE** *(revenue-generating or institutional)*:
- **The first-impression credibility test**: Before the user reads a single word — does the composition signal "trusted tool" or "rough prototype"? List the 3 visual elements most undermining this credibility and the specific change that would fix each.
- **Visual trust hierarchy**: Does the palette feel stable and intentional? Does the typography feel appropriate for the domain? Does spacing feel designed or accidental? Trust is communicated visually before it is read.
- **Competitive visual benchmark**: Name the 2–3 most credible tools in this category. Compared to them — what does this app do better, at parity, or worse, in craft specifics?
- **Conversion or commitment blockers**: In any paid, sign-up, or institutional commitment flow — identify visual elements that undermine the user's confidence: unclear primary action, visual hierarchy that buries the CTA, absence of legitimacy signals.
- **Distribution channel fit** `[A1]`: What first-impression surface matters most for this app — App Store screenshot, sales demo, marketing page, Product Hunt listing? Is the visual design compelling in that specific context?

---

**`[A2]` USE CONTEXT: FOCUS-CRITICAL OR HIGH-STAKES**:
- **Cognitive load audit**: Identify every visual element that demands attention beyond what the user's task requires. Decorative elements, animations, color variety, complex backgrounds — each one is a cost. List everything that should be eliminated or minimized.
- **Information scannability**: Under time pressure or stress, can the user find the critical number, status, or action within 2 seconds? Is the most important information visually dominant?
- **Visual noise inventory**: List every element that could be removed, reduced, or quieted without losing functional information. In high-stakes contexts, visual noise is not a minor polish issue — it is a functional failure.

**`[A2]` USE CONTEXT: EMOTIONALLY SENSITIVE**:
- **Safety signals**: Does the visual design feel safe? Assess: corner radius (sharp corners feel clinical), color temperature (cold blues feel institutional), spacing (cramped layouts feel anxious), animation speed (fast transitions feel jarring). Identify the 2–3 specific changes that most increase felt safety.
- **Warmth calibration**: Is the palette warm enough for this emotional context without feeling saccharine? Is the typography gentle without being unreadable? Does the empty state feel welcoming or clinical?
- **Tone-design coherence**: Does the visual language match the emotional register the copy is attempting? A warm, reassuring message delivered inside a harsh, clinical layout creates dissonance.

**`[A2]` USE CONTEXT: CREATIVE OR EXPRESSIVE**:
- **Inspiration quality**: Does the interface itself feel inspiring, or purely functional? In a creative tool, the environment shapes the output — a beautiful, expressive interface puts users in a creative mindset.
- **Expressive range**: Is there room in the visual design for personality and surprise? Or is everything so controlled that the app feels sterile?
- **Chrome vs canvas**: How much visual space does the interface take from the user's creative work? Is the UI chrome earning its space?

**`[A2]` USE CONTEXT: LEISURE OR CASUAL**:
- **Delight calibration**: For a leisure tool, friction that would be acceptable in a professional context is not acceptable here. Is the experience genuinely pleasurable? Is there any moment of unexpected delight?
- **Low-stakes visual permission**: Leisure contexts allow more visual personality, playfulness, and even imperfection — provided it is intentional. Assess whether the current design uses this freedom, or applies professional-tool austerity where it isn't needed.

---

**`[A3]` AUDIENCE: EXPERT / PRACTITIONER**:
- **Density as respect**: Information density is a feature for expert users, not a flaw. Assess whether the current density level respects the expertise of the audience or talks down to them with excessive whitespace and simplified presentation.
- **Vocabulary accuracy**: Every label, stat name, unit, and domain term is a trust signal. One wrong term signals that the maker doesn't understand the domain. Audit every piece of domain vocabulary for precision.
- **Power-user surface area**: Are advanced capabilities accessible without being buried? Expert users should be able to do in 2 clicks what a novice does in 5 steps.

**`[A3]` AUDIENCE: MIXED OR BRIDGING**:
- **Progressive disclosure integrity**: The design must serve both expert and novice simultaneously. Is the complexity ladder clearly implemented — default view for novices, accessible depth for experts — without condescending to one or overwhelming the other?
- **Dual-register visual design**: Assess whether the visual design has a successful strategy for serving two different expertise levels. If it doesn't — this is a structural design problem that visual polish cannot fix.

---

**`[A4]` SUBJECT HAS STRONG VISUAL IDENTITY**:
- **Palette coherence**: Identify the dominant visual tones associated with the subject and assess whether the app's palette is *inspired by*, neutral to, or in conflict with them. Give a specific, actionable palette direction — not just "make it darker" but the specific character shift that would increase coherence.
- **Typographic tone**: Does the typeface feel tonally coherent with the subject? Identify a tonal mismatch if it exists and name a specific alternative.
- **Motion character**: Does the animation vocabulary honor the subject's energy, weight, and atmosphere? Name the specific adjustment that would increase alignment.
- **Iconography and visual register**: Do any custom icons or decorative elements feel consistent with the subject's visual language? Generic stock illustrations feel detached from a subject with a strong identity.

**`[A4]` COMMUNITY AESTHETIC NORMS EXIST**:
- **Insider signal audit**: What visual choices communicate that the maker is genuinely part of this community — familiar with its vocabulary, conventions, and tastes? What choices inadvertently signal an outsider? List both.
- **Anti-corporate check**: Does the visual design feel like it belongs to the community, or like it's trying to productize the community? Flag any design choices that feel like a startup trying to monetize a subculture — regardless of whether the product is actually paid.

**`[A4]` SUBJECT IS NEUTRAL / NO ESTABLISHED IDENTITY**:
- **Invented coherence**: With no subject identity to reference, the visual language must be invented entirely from within. Is there a coherent internal logic — a design concept or metaphor running through the product? If not, identify the strongest available candidate.

---

**`[A5]` AESTHETIC IS THE VALUE**:
- **Chrome restraint**: The UI interface around the product's output must recede as much as possible. Every pixel of interface competes with the product's own aesthetic output. Identify every non-essential UI element and recommend minimum-footprint alternatives.
- **Output quality assessment**: The visual quality of what the product *produces* — not just the container — must be assessed as a design output. Is it beautiful? Is it surprising? Does it feel like the tool is a creative collaborator?
- **Signature output quality**: Can a user immediately tell this output came from this tool? Is the output aesthetically distinctive?

**`[A5]` AESTHETIC MUST STAY INVISIBLE**:
- **Distraction inventory**: Every element that draws attention to itself is a failure. List every visual element that is "nice" but competes with the user's task — and recommend eliminating or reducing each.
- **Trust-through-clarity**: In invisible-aesthetic contexts, trust comes entirely from clarity and reliability, not from polish. Is every element present because it is functionally necessary? Are there any decorative elements that should be removed entirely?

---

**UNIVERSAL** *(always apply)*:
- **The "made with intent" test**: Does the app look like every visual decision was made deliberately — or like some things were shipped at their default? Identify the 3 visual elements most clearly signaling unintentional defaulting, and the specific changes that would make them look chosen.
- **App icon / favicon quality**: Legible at 16×16 and all required sizes? Visually coherent with the app's design language? Distinct enough to be identified in a browser tab or home screen?
- **Visual coherence across sections**: Does the app feel designed as a whole? Would a user recognize a new screen as part of the same product?

#### §E9. VISUAL IDENTITY & RECOGNIZABILITY

> Identity means different things depending on the axis profile. For commercial products it is competitive differentiation. For community products it is subject fidelity. For creative products it is the distinctiveness of the output itself. Apply the questions whose axis is active.

- **Visual signature** `[A1][A4][A5]`: Can a user identify this app from a partial screenshot — a fragment of color, a component shape, a motion pattern, or the visual character of its output? Identify what could become a distinctive visual signature, or what already is one.
  - *Commercial (A1)*: Is the signature distinctive within the product category, or generic among competitors?
  - *Subject identity (A4)*: Does the signature feel like it belongs to the subject — or does it feel imported from a different visual world?
  - *Aesthetic-primary (A5)*: Is the output itself visually distinctive? Could the user recognize output from this tool versus a competing one?
- **Visual metaphor coherence** *(all)*: Is there a consistent design concept or visual logic running through the product — a coherent internal language? If one exists, is it consistent throughout? If none exists, what is the strongest candidate based on the subject, audience, and use context?
- **Accent color intentionality** `[A1][A4]`: Is the accent color purposeful — a calibrated hue with intentional saturation, not the first pick from a wheel? *Commercial*: Is it distinctive within the competitive landscape? *Subject identity*: Does it feel tonally connected to the subject?
- **Emotional arc design** *(all)*: Does the visual language guide users through the right emotional journey for this specific app and audience? Map the intended emotional arc (e.g., focus → confidence → satisfaction for a work tool; curiosity → discovery → delight for an exploratory tool; calm → trust → relief for a sensitive-context tool), then assess whether the visual transitions, state changes, and feedback moments support it.
- **Anti-genericness audit** *(all)*: Identify visual elements that make the app look interchangeable with a dozen others — same default palette, same component style, same layout conventions with no adaptation to the subject or audience. For each: what is the specific, minimal change that would make this element more distinctly *this* app?

#### §E10. DATA STORYTELLING & VISUAL COMMUNICATION

> Numbers and data are not just displayed — they are communicated. This section evaluates whether the app's visual language helps users understand, not just see.

- **Numbers as visual elements**: Are the most important metrics in the app displayed with visual weight proportional to their importance? A key output number in the same size and weight as a label fails visual information design. Identify every key number and whether its typographic treatment matches its significance.
- **Hierarchy of insight**: For data-forward apps — is there a visual path from "raw input" → "computed result" → "actionable insight"? Or does the user have to parse a flat grid of equal-weight numbers to find the answer to their question?
- **Chart design quality**: Every chart should answer a specific question. For each chart: state the question it is designed to answer — then assess whether the visual encoding (chart type, scale, color, label placement) answers that question as directly as possible. Common failures: pie charts for comparing more than 4 values, line charts for categorical data, bar charts where a table would communicate more precisely.
- **Progressive complexity revelation**: Does the design guide users from simple overview → detailed drill-down → power-user controls? Or does it present full complexity immediately? The visual design should embody progressive disclosure — not just the UX architecture.
- **Data density calibration**: Assess whether the information density is calibrated for the target audience. A tool for analysts can be dense; a tool for casual users must be generous with whitespace and explanation. Is the current density right? What is the cost to the app's usability of the current density choice?
- **Empty → populated visual storytelling**: The transition from empty state to populated state is one of the most important visual moments in the product. Does populating data feel like the app coming alive, or does it feel like a spreadsheet being filled in? Identify the specific visual improvements — animation, color, layout shift — that would make this transition feel more meaningful.
- **Error as communication**: Error states should communicate clearly, not just signal failure. Does the visual design of error states match their urgency? A critical error and a mild warning should look visually distinct. Are error states designed with the same craft as the default states?

---

### CATEGORY F — UX, Information Architecture & Copy

#### §F1. INFORMATION ARCHITECTURE
- **Navigation model**: Do tab/menu labels and icons match users' mental model of the content? Would a new user find what they're looking for?
- **Content hierarchy**: Most important information visually prominent? Clear visual path from "input" to "output" to "action"?
- **Progressive disclosure**: Advanced/infrequently-used options hidden behind expandable sections? Or are all options shown at once overwhelming the user?
- **Categorization logic**: Is content grouped in ways that feel natural to the target audience? Groups should reflect user mental models, not implementation structure.
- **Section depth**: Is the navigation hierarchy the right depth — not so flat that everything is at the same level, not so deep that users lose track of where they are?

#### §F2. USER FLOW QUALITY
- **Friction audit**: For each workflow in §0 — count the steps. Are any steps unnecessary, confusable, or surprising? Every unnecessary step is a design failure.
- **Default value quality**: Are default values the most common/sensible choice? Good defaults dramatically reduce user effort.
- **Action reversibility**: Can users undo or go back from every action? Irreversible actions are acceptable if the user is clearly warned with enough context to make an informed decision.
- **Confirmation dialog quality**: Destructive confirmations tell the user specifically what will be destroyed and whether it is recoverable — not just "Are you sure?".
- **Feedback immediacy**: Does every action produce immediate visual feedback? Clicks that feel unresponsive damage trust.
- **Perceived performance**: During recomputation — does the UI show stale data, blank space, or a skeleton? Which is chosen, and is it the right choice?
- **Keyboard shortcuts**: For power users — are common actions keyboard-accessible? Are shortcuts discoverable (tooltip mentions it)?

#### §F3. ONBOARDING & FIRST USE
- **First impression**: On the very first visit, does the user understand what the app does and what to do first? Without tooltips or documentation?
- **Onboarding quality**: Does the onboarding teach by doing (interactive) or just describe (passive)? Interactive is more effective.
- **Onboarding re-entry**: Can users replay the onboarding? Can they access help at any time?
- **Empty state → filled state**: The transition from "no data" to "data present" — is it visually satisfying? Does it feel like the app is gaining value?
- **Progressive complexity**: Does the app reveal complexity incrementally, or does it present everything at once?
- **Activation path clarity** `[A1][A2][A3]`: Is the visual hierarchy guiding the user toward their first meaningful interaction? What "meaningful" means depends on the axes:
  - *High commercial intent (A1)*: The path to first value must be visually direct — identify any elements that distract from or delay the activation moment.
  - *Expert audience (A3)*: Experts should reach their first productive action faster than novices, not be forced through the same beginner scaffolding.
  - *Casual / emotionally sensitive audiences (A3/A2)*: Is the function obvious without reading anything? Is the first step gentle enough not to intimidate?
- **First success moment design** `[A2][A4]`: The moment the user first achieves something meaningful is the highest-value moment in the product. Is it visually acknowledged? The right acknowledgment depends on context:
  - *Focus-critical tools*: A quiet, efficient confirmation — not celebration, just closure.
  - *Creative / leisure tools*: A moment of genuine visual satisfaction — the result feels like an output worth having.
  - *Community / subject tools*: The result presented in a way that resonates with how the community experiences the subject — using the right vocabulary, the right visual weight.
  - *Emotional / sensitive tools*: A warm, gentle affirmation — not enthusiasm, just reassurance.
- **Time-to-function legibility** *(all)*: Can a new user tell within 10 seconds what they will be able to do? This is a visual clarity question — the app's core function should be visually legible, not just textually stated.

#### §F4. COPY QUALITY
- **Tone consistency**: Does every piece of UI copy feel like it came from the same voice? List any copy that sounds notably different from the rest.
- **Clarity**: Every label, tooltip, placeholder, error message, and heading — is it unambiguous? Could a user unfamiliar with the domain understand it?
- **Conciseness**: UI copy should be as short as possible while remaining clear. List every piece of copy that could be tightened.
- **Terminology consistency**: The same concept always called the same thing. List every synonym pair or inconsistency.
- **Capitalization convention**: Title Case for navigation and headings, Sentence case for body text and labels — applied consistently?
- **Action verb quality**: Buttons should use strong, specific verbs: "Save draft" not "Submit", "Delete account" not "Confirm", "Import history" not "OK".
- **Empty state copy**: Empty states have a clear, helpful, action-oriented message — not blank or just "No data found."
- **Error message copy**: Human-readable, no jargon, explains the cause, explains what to do next.
- **Copy as commitment asset** `[A1]`: *Activate only for revenue-generating or institutional products.* In any paid, sign-up, or commitment flow — copy is conversion infrastructure. Does the CTA communicate value ("Start building") or just request action ("Sign up")? Does the copy build confidence or just inform? For each commitment-adjacent CTA, suggest a more compelling alternative.
- **Copy as domain fluency signal** `[A3][A4]`: *Activate when the audience has domain expertise or the subject has community vocabulary.* Does the copy use the community's or domain's natural vocabulary accurately — the terms, shorthand, and framing that practitioners and enthusiasts actually use? Copy that describes the subject the way a press release would, or uses domain terms loosely, signals distance from the audience. List any copy that feels written by an outsider and suggest alternatives that feel more native.
- **Copy as emotional register** `[A2]`: *Activate for emotionally sensitive, creative, or high-stakes contexts.* Does the copy's tone match the emotional context of use? Clinical language in a wellness tool, playful language in a high-stakes professional tool, bureaucratic language in a creative tool — all represent tone-design mismatches. Identify any copy that is tonally wrong for the use context.
- **Brand voice extraction** *(all)*: Based on the copy that exists, extract a 3-adjective voice descriptor. Then identify every piece of copy that violates this voice — too formal, too casual, too generic, or out of register for this app's axis profile.

#### §F5. MICRO-INTERACTION QUALITY
- **Hover states communicate intent**: Every interactive element has a hover state that feels intentional (cursor change, color shift, underline, elevation change).
- **Loading states**: Async operations have immediate feedback — even a short 200ms delay without feedback feels broken.
- **Success confirmation**: Successful actions are confirmed visually — save, copy, export, submit all acknowledge completion.
- **Scroll behavior**: Scroll-to-content after navigation? Scroll position preserved on back navigation? Smooth scrolling where appropriate?
- **Focus indicator quality**: Visible and styled to match the app's design language — not just the browser default blue rectangle (unless the design is minimal).

#### §F6. ENGAGEMENT, DELIGHT & EMOTIONAL DESIGN

> The goal of this section is derived entirely from the axis profile. "Engagement" means radically different things depending on whether the app is a high-frequency work tool, an emotionally sensitive companion, a community gift, or a creative instrument. Apply the questions whose axis is active.

**UNIVERSAL** *(all apps)*:
- **Reward moments**: When the user achieves something meaningful — does the UI visually acknowledge it? Even a brief, quiet confirmation transforms a functional interaction into a satisfying one. List every "achievement moment" in the core workflow and assess whether it has any visual acknowledgment. The *form* of that acknowledgment should match the axis profile — not all success moments should be celebrations.
- **Personality moments**: Are there interactions that reveal the app's character — an empty state with genuine voice, a micro-animation that feels considered, a transition that feels right? These are what users remember and describe to others. Identify 2–3 places where a personality moment would feel authentic to this app's axis profile.
- **Notification quality**: Any notification, badge, or alert indicator — designed with the same craft as the rest of the product? Unstyled browser alerts break the contract regardless of product nature.

**`[A1]` COMMERCIAL INTENT**:
- **Progress and investment visibility**: Can users see how far they've come, how much they've built? Progress signals create retention pull. Does the app leverage this without resorting to manipulative patterns?
- **Shareable outcomes**: Would a user want to share something they produced or achieved in this app? What is the most naturally shareable moment, and is it visually compelling enough to share?

**`[A2]` EMOTIONAL SENSITIVITY**:
- **Emotional safety in transitions**: Every state change — loading, error, empty, success — should feel emotionally appropriate to someone in a vulnerable state. Is there any moment that feels jarring, cold, or clinical where warmth was needed?
- **Absence of pressure patterns**: Are there any visual elements that create urgency, scarcity, or anxiety — even unintentionally? Countdown timers, red badges, aggressive empty states — all create pressure that is inappropriate in emotionally sensitive contexts.
- **"Feels like support" quality**: Does the app feel like it is on the user's side? What specific visual or copy choices most contribute to — or detract from — this feeling?

**`[A2]` CREATIVE / EXPLORATORY CONTEXTS**:
- **Discovery encouragement**: Does the interface visually invite exploration — or does it present a flat list of functions? Are there visual cues that suggest "there is more here to discover"?
- **Creative momentum**: Does the visual design maintain creative flow — or does it interrupt it with friction, confirmations, or loading states that break the user's concentration?

**`[A3][A4]` COMMUNITY / SUBJECT CONTEXTS**:
- **Community shareable moments**: Is there a moment in the app compelling enough that a user would screenshot it and share it in their community's space — a forum, a Discord, a social feed? Identify that moment and assess its visual quality for sharing.
- **Authentic delight**: Are there details that reward genuine familiarity with the subject — a label using community shorthand, a display that reflects how insiders think about the subject, a detail in an empty state that speaks directly to this audience's experience? These signals are disproportionately valuable for establishing insider credibility.
- **Integrity over manipulation**: Retention mechanics (streaks, FOMO, aggressive notifications) are tonally wrong when the app is a community gift or free tool — and often wrong even for paid tools. Flag any patterns that prioritize the product's engagement metrics over the user's actual experience.

---

### CATEGORY G — Accessibility

#### §G1. WCAG 2.1 AA COMPLIANCE

**Perceivable:**
- **1.1.1** — Every meaningful image has descriptive `alt` text. Decorative images have `alt=""`.
- **1.3.1** — Semantic HTML: `<button>`, `<nav>`, `<main>`, `<header>`, `<h1>–<h6>`, `<label>`, `<table>` used correctly — not `<div>` for everything.
- **1.3.2** — DOM reading order matches visual order.
- **1.3.3** — No instruction relies solely on sensory characteristic ("click the red button").
- **1.4.1** — Color not the only signal — status/error/success also conveyed by icon or text.
- **1.4.3** — All text: 4.5:1 contrast (normal), 3:1 (large/bold ≥18px or ≥14px bold).
- **1.4.4** — Text readable at 200% zoom without horizontal scroll.
- **1.4.11** — UI components and focus rings: 3:1 against adjacent colors.
- **1.4.13** — Tooltips dismissible, persistent, hoverable.

**Operable:**
- **2.1.1** — Every interactive element keyboard-reachable and operable.
- **2.1.2** — No keyboard traps (except intentional modal focus trapping).
- **2.4.1** — Skip navigation link for keyboard users to skip repeated content.
- **2.4.3** — Logical focus order follows visual reading order.
- **2.4.7** — Visible focus indicator on every interactive element.
- **2.4.11** — Focused element not fully obscured by sticky headers/overlays (WCAG 2.2).
- **2.5.3** — Button visible label text is included in the accessible name.
- **2.5.5** — Touch targets ≥44×44px CSS.

**Understandable:**
- **3.1.1** — `lang` attribute on `<html>`.
- **3.2.1** — No unexpected context change on focus.
- **3.3.1** — Input errors identified in text, not just color.
- **3.3.2** — Every input has an associated label or clear instruction.

**Robust:**
- **4.1.2** — Custom interactive components: correct ARIA roles, names, states.
- **4.1.3** — Dynamic status messages (toasts, counters, results) announced via `aria-live`.

#### §G2. SCREEN READER TRACE
- Simulate reading the primary user workflow in DOM order only (no visual reference).
- Modal open/close: focus moves to modal on open, returns to trigger on close?
- Dynamic updates (results, timers, validation): announced via appropriate `aria-live` polarity?
- Icon-only buttons: `aria-label` present?
- Custom tabs, dropdowns, sliders: correct ARIA patterns (`role="tab"`, `aria-selected`, etc.)?

#### §G3. KEYBOARD NAVIGATION
- Tab through the full app — every interactive element reachable in logical order?
- Custom components (date pickers, sliders, carousels) — arrow key navigation?
- Modal focus trapping — Tab cycles within modal, cannot escape to page behind?
- Escape key closes dialogs/modals/dropdowns?
- Visible focus style matches the app design (not just default browser ring)?

#### §G4. REDUCED MOTION
- `prefers-reduced-motion: reduce` honored for ALL animations:
  - CSS transitions and `@keyframes` animations
  - JavaScript `requestAnimationFrame` loops
  - Canvas animations (requires explicit JS media query check — often missed)
  - Video/GIF autoplay
- Reduced motion removes non-essential animation but preserves state communication.

---

### CATEGORY H — Browser Compatibility & Platform

#### §H1. CROSS-BROWSER MATRIX

Build this table for the specific APIs and features the app uses:

| Feature Used | Chrome | Safari/iOS | Firefox | Samsung | Edge | Fallback? |
|-------------|--------|------------|---------|---------|------|-----------|
| Blob Worker/SW | ✓ | ✗ | ✗ | ? | ✓ | Required |
| `crypto.randomUUID` | ✓ | 15.4+ | 92+ | ? | ✓ | Math.random fallback |
| `backdrop-filter` | ✓ | ✓`-webkit-` | 70+ | ? | ✓ | Graceful skip |
| `navigator.vibrate` | ✓ | ✗ | ✓ | ✓ | ✓ | No-op |
| `CSS.supports()` | ✓ | ✓ | ✓ | ✓ | ✓ | — |
| `optional chaining ?.` | ✓ | 14+ | 74+ | ? | ✓ | — |
| `gap` in flexbox | ✓ | 14.1+ | 63+ | ? | ✓ | — |
| *[App-specific APIs]* | | | | | | |

For every ✗ or uncertain cell: does the app crash or degrade gracefully?

#### §H2. PWA & SERVICE WORKER
- **Blob SW incompatibility**: Firefox and Safari reject Blob URL service workers — graceful fallback?
- **Cache strategy correctness**: Cache-first for static assets, network-first for data, stale-while-revalidate for semi-static?
- **Version cleanup**: Old caches purged on app update? User ever stuck on stale JS?
- **Update notification**: User notified when a new version is deployed? Can they act on it?
- **Offline completeness**: Core functionality works offline? Network-dependent features fail gracefully?
- **Manifest completeness**: Required icon sizes (192×192, 512×512, 180×180 iOS), `display`, `theme_color`, `background_color`, `start_url`, `scope`.
- **Install prompt**: `beforeinstallprompt` handled? iOS Add to Home Screen flow (no event) documented?

#### §H3. MOBILE & TOUCH
- **iOS Safari quirks**: `position: fixed` + virtual keyboard? `100vh` including address bar (use `dvh`)?
- **Android**: Back gesture in PWA — navigates back or exits app?
- **Touch vs hover**: Hover-only interactions blocked by `@media (hover: hover)`?
- **Safe area insets**: `env(safe-area-inset-*)` respected in fixed/absolute elements on notched devices?
- **Pinch-to-zoom**: `user-scalable=no` present? (Accessibility violation — WCAG 1.4.4)
- **Swipe gestures**: Conflict with native scroll? Threshold too sensitive for intentional scroll?

#### §H4. NETWORK RESILIENCE
- **CDN failure**: React/framework CDN unavailable — blank page or meaningful error?
- **Error boundaries**: React Error Boundaries around CDN-dependent components?
- **Third-party image failure**: Image host down — placeholder shown? Layout preserved?
- **Reconnection**: Back online after offline — state sync correct? Presence reconnects?
- **Timeout handling**: Operations that can hang indefinitely — timeout and error gracefully?

---

### CATEGORY I — Code Quality & Architecture

#### §I1. DEAD CODE & WASTE
- **Unused functions**: Defined but never called?
- **Unused constants**: Defined but never referenced?
- **Unreachable branches**: `if (CONSTANT === false)`, conditions that can never be true given state machine?
- **Commented-out code**: Old implementation left as dead comments — delete or document why it's kept.
- **Unused CDN libraries**: Loaded but never used?
- **Development artifacts**: `console.log`, `debugger`, `TODO`, `FIXME`, `HACK` — inventory and prioritize.

#### §I2. NAMING QUALITY
- **Casing conventions**: `camelCase` (variables/functions), `PascalCase` (components/classes), `SCREAMING_SNAKE` (constants) — consistent?
- **Semantic accuracy**: Functions that do more than their name says? Names that imply something different from actual behavior?
- **Boolean naming**: `is`/`has`/`can`/`should` prefix for boolean variables and props?
- **Event handler naming**: `on{Event}` for callbacks, `handle{Event}` for internal handlers — consistent?
- **Magic numbers**: Every unexplained numeric literal that should be a named constant. List all.
- **Unclear abbreviations**: What is `wsv`? `ctr`? `tmp2`? Either expand or document.

#### §I3. ERROR HANDLING COVERAGE
For every `try/catch` and every async operation:
- **Caught**: Is the exception caught, or does it bubble up to crash the app?
- **Logged**: Is there a trace for debugging (even in development)?
- **Surfaced**: Does the user see a meaningful message, or does the error disappear silently?
- **Recovered**: Does the app return to a valid, operable state?
- **Error boundaries**: React Error Boundaries at the right granularity — not just one global boundary that blanks the whole app?

#### §I4. CODE DUPLICATION
- **Logic duplication**: Same calculation in multiple places — which copy gets the bug fix?
- **UI pattern duplication**: Same component structure copied 3+ times — should be parameterized.
- **Constant duplication**: Same value hardcoded in multiple places — one change misses the others.
- **Copy-paste divergence**: Duplicated code where one copy was updated and the other wasn't — this is where bugs hide.

#### §I5. COMPONENT & MODULE ARCHITECTURE
- **Single responsibility**: Each component does one clearly-defined thing.
- **God components**: Components >300 lines doing multiple unrelated things — natural split points?
- **Prop drilling**: Props passed through 4+ intermediate components — context or composition?
- **Reusability**: Near-duplicate components that could be unified with a well-designed prop API.
- **Dependency direction**: Lower-level components not importing from higher-level state/context.

#### §I6. DOCUMENTATION & MAINTAINABILITY
- **Algorithm comments**: Non-trivial algorithms (probability engines, optimizers, state machines) have comments explaining the math, assumptions, inputs, outputs, and edge cases.
- **Lying comments**: Comments that describe what the code *used to* do before a refactor.
- **Architecture decisions**: Key choices documented with rationale (why single-file? why no server? why this state model?).
- **Section organization**: For large files — section index? Navigable by grep?
- **Changelog**: Version history maintained?

---

### CATEGORY J — Data Presentation & Portability

#### §J1. NUMBER & DATA FORMATTING
- **Numeric display consistency**: Same number formatted the same way everywhere (1,234 vs 1234)?
- **Percentage precision**: Contextual — 2dp for small values (<10%), 1dp for medium, 0dp for 100%? Consistent?
- **Date/time formatting**: Single format across all views? ISO 8601 for data, human-readable for display?
- **Currency formatting**: Locale-correct? Correct decimal places for the currency?
- **Null/zero/empty representation**: Consistent — `0`, `—`, `N/A`, hidden — same treatment for same meaning throughout?
- **Unit labels**: "45 items" not just "45". No ambiguous bare numbers.
- **Significant figures**: Contextual precision — casual context shows `~2.4 hrs`, not `2.41739012...`.

#### §J2. DATA VISUALIZATION QUALITY
- **Data correctness**: Chart data points map to correct domain values? Off-by-one errors?
- **Axis honesty**: Y-axis starting at 0 (unless explicitly justified)? No misleading truncation?
- **Scale choice**: Logarithmic vs linear — appropriate for the data range and user question?
- **Small value visibility**: Values near zero visible at default scale, or crushed to invisibility?
- **Tooltip accuracy**: Tooltip values match underlying computed values (not re-approximated)?
- **Visual vs computed agreement**: For every displayed number — the value shown equals the value computed.
- **Responsive correctness**: Labels overlap at narrow widths? Chart reflows on resize?
- **Colorblind safety**: Colors distinguishable without hue? (Use shape, pattern, or label as secondary encoding)

#### §J3. ASSET MANAGEMENT
- **Third-party image hosts**: Reliability? Rate limiting? GDPR implications?
- **Format modernity**: WebP/AVIF vs legacy PNG/JPEG?
- **Lazy loading**: `loading="lazy"` for below-fold images?
- **Error handling**: `onError` fallback image? No broken-image glyphs in the UI?
- **Alt text quality**: Meaningful descriptions (not filenames, not "image").
- **PWA icons**: All required sizes (192, 512, 180 for iOS)?

#### §J4. REAL-TIME DATA FRESHNESS
- **Staleness indicators**: Data that changes frequently (prices, statuses, counts) — does the UI communicate age? "Last updated 3m ago" vs silently stale?
- **Poll / push strategy**: Polling interval appropriate for data volatility? WebSocket reconnect on disconnect?
- **Optimistic updates**: Local state updated immediately, then confirmed or rolled back on server response — rollback path implemented?
- **Cache invalidation**: When does a cached response get considered stale? Can the user force-refresh?
- **Timestamp handling**: Server timestamps compared to client clock — timezone mismatch? Clock skew?
- **Race condition on rapid refresh**: Two in-flight requests, older response arrives after newer — does old data overwrite new?
- **Loading vs stale distinction**: Is there a visual difference between "this data is loading" and "this data might be outdated"?

---

### CATEGORY K — Specialized Domain Depths

Activate at maximum depth based on §0 Stakes and §I.1 Domain Classification.

#### §K1. FINANCIAL PRECISION
- **Integer cents rule**: All monetary values stored as integer cents/pence? Never float. `0.1 + 0.2 ≠ 0.3` in IEEE 754.
- **Rounding discipline**: Explicit rounding at defined points — not relying on floating-point truncation.
- **Tax application order**: Before or after discount? Correct for jurisdiction?
- **Rounding rule**: Banker's rounding (round-half-to-even) vs standard rounding — which is legally required?
- **Multi-currency**: FX rate freshness? Which rate used for conversion?
- **Atomicity**: Can a partial operation (interrupted payment, network failure mid-transaction) leave state inconsistent?
- **Audit trail**: Financial actions logged immutably?

#### §K2. MEDICAL / HEALTH PRECISION
- **Formula source**: Every clinical formula cited against a published medical reference.
- **Unit safety**: Imperial/metric mixing? `mg` vs `mcg` confusion? `kg` vs `lbs`?
- **Dangerous value flagging**: Clinically dangerous values flagged prominently, not just displayed.
- **Disclaimer visibility**: "Not medical advice" prominent and impossible to miss.
- **HIPAA/equivalent**: Health data stored locally or transmitted? Regulatory requirements?
- **Uncertainty communication**: Model limitations stated? Estimates vs exact values labeled?

#### §K3. PROBABILITY & GAMBLING-ADJACENT
- **Model appropriateness**: Mathematical model valid for the actual stochastic process?
- **Worst-case disclosure**: Expected value shown alongside worst-case. Not just the average.
- **Spending escalation UX**: Does the UI design (with or without intent) guide users toward spending more?
- **Age verification**: Gambling-adjacent mechanics — is age gating present or required?
- **Jurisdiction**: Gambling regulations vary by country — is this app subject to any?

#### §K4. REAL-TIME & COLLABORATIVE
- **Conflict resolution strategy**: Two users editing simultaneously — last-write-wins, merge, or lock?
- **Presence accuracy**: Online/offline status stale? Reconnect latency?
- **Message ordering**: Out-of-order messages handled correctly?
- **Optimistic update rollback**: If a server operation fails — does the UI correctly roll back?

#### §K5. AI / LLM INTEGRATION
*(Activate when External APIs or AI/LLM field in §0 references any AI provider)*
- **Prompt injection via user input**: User-controlled text concatenated into a prompt — can a user inject instructions that change model behavior? Sanitize or clearly separate user content from system instructions.
- **Output sanitization**: AI-generated text inserted into the DOM via `innerHTML` or `dangerouslySetInnerHTML`? AI output can contain adversarial HTML/JS. Always treat LLM output as untrusted user input — escape or sanitize before rendering.
- **Markdown rendering XSS**: AI output rendered via a markdown library — is the library configured to sanitize HTML? (e.g., `marked` with `sanitize: true`, or `DOMPurify` post-process)
- **Token cost runaway**: Is there a `max_tokens` limit on every request? Can a user trigger unbounded completion chains (recursive calls, loops, tool use without depth limit)?
- **API key exposure**: API key in frontend source code, localStorage, or URL params → extractable by any user. Keys must go through a backend proxy.
- **Model fallback**: If the primary model is unavailable or returns an error — graceful fallback or error message? No silent empty state?
- **Latency handling**: LLM calls are slow (1–30s). Is there a visible streaming indicator or progress state? Can the user cancel? Does the UI remain interactive?
- **Hallucination disclosure**: App presents AI-generated content as fact? Caveat required.
- **PII in prompts**: Does the prompt include user PII (name, health data, financial data)? Data processor obligations under GDPR/CCPA?
- **Rate limiting / retry**: 429 responses from the API — exponential backoff with jitter? User-visible message vs silent hang?
- **Streaming edge cases**: Partial chunk handling — what happens if stream cuts mid-token? Partial JSON in structured outputs?

---

### CATEGORY L — Optimization, Standardization & Polish Roadmap

> This category does not find bugs. It identifies opportunities to improve the app beyond "working" to "exceptional" — without introducing bugs, without removing features, without denaturing the design identity.

#### §L1. CODE OPTIMIZATION OPPORTUNITIES
- **Algorithm efficiency**: Are there O(n²) operations where O(n log n) or O(n) is achievable without architectural change?
- **Memoization gaps**: Expensive pure computations called repeatedly with the same inputs — should be memoized.
- **Redundant computation**: Multiple places computing the same derived value — unify to a single derivation.
- **Bundle size reduction**: Dead imports? Lighter library alternatives that fit within the architectural constraints?
- **CSS optimization**: Unused CSS classes? Specificity conflicts? Long selector chains?
- **Render optimization**: Components that render on every global state change despite depending on only a small slice of state?

#### §L2. CODE STANDARDIZATION
- **Consistent patterns**: For similar problems (data fetching, error handling, form validation) — is one pattern used throughout, or multiple ad-hoc approaches?
- **Utility consolidation**: Repeated utility functions (date formatting, number formatting, string manipulation) that should be in a shared module.
- **Constant registry**: All domain constants in one place? Or scattered throughout the file?
- **Component API consistency**: Similar components with inconsistent prop naming (`onClose` vs `handleClose` vs `dismiss`)? Standardize.
- **Import/dependency order**: Consistent grouping and ordering of imports/CDN dependencies?
- **Error handling pattern**: Consistent try/catch shape and error reporting throughout — not a different approach in every async call.

#### §L3. DESIGN SYSTEM STANDARDIZATION
> The goal: move from "many components that each look fine individually" to "one coherent design system."
- **Token consolidation plan**: For every one-off spacing/color/radius value found in §E1 — provide the standardized token it should use and what currently uses that token.
- **Component variant audit**: For every component type (button, card, badge, input, modal) — list all existing variants, identify variants that should be merged or unified, identify missing variants the app needs but lacks.
- **Pattern library gap**: For components used ≥3 times without a shared implementation — recommend extraction to a shared component.
- **Theme variable completeness**: Every value that changes with theme (light/dark/OLED/brand) should be a CSS variable or theme token, never hardcoded. List every hardcoded value that bypasses the theme system.
- **Design system as product asset**: A coherent design system enables faster iteration, safer changes, and visual coherence as the product grows — for any product nature. Assess: does the current system have enough structure to support adding 5 new components without breaking the visual language? If not — what minimal token/component foundations would make it robust?
- **Theming readiness** *(paid/multi-tenant products only)*: If the monetization tier or distribution model involves multiple brands, clients, or customization — are brand-identity values (primary color, radius personality, font) isolated in a small set of root tokens that could be swapped per tenant? Flag this only when relevant — this is not a goal for a single-user or community tool.

#### §L4. COPY & CONTENT STANDARDIZATION
- **Voice guide**: Describe the app's copy voice in 3 adjectives, then list any copy that violates this voice.
- **Terminology dictionary**: For every key concept in the app, the canonical name. List synonyms used inconsistently.
- **Capitalization audit**: List every label, button, and heading — flag inconsistent capitalization.
- **Punctuation consistency**: Trailing periods in labels? Em-dashes vs hyphens? Consistent quotation marks?
- **Number/unit style**: Spelled-out numbers vs digits ("three" vs "3")? Consistent in same context?
- **CTA optimization**: Are calls-to-action specific enough? "Get started" → "Create invoice" → "Create your first invoice" — each more specific and more effective.
- **Brand voice guide deliverable** *(all)*: Based on the copy audit, produce a minimal voice guide for this specific app — derived from its axis profile, not a generic template. The guide should include:
  ```
  Voice: [adjective 1 / adjective 2 / adjective 3]
  Derived from: [Axis 1: commercial/non-revenue] × [Axis 2: use context] × [Axis 3: audience]
  
  This app sounds like: "[example]"  not  "[anti-example]"
  This app sounds like: "[example]"  not  "[anti-example]"
  
  Always: [rule 1], [rule 2], [rule 3]
  Never:  [rule 1], [rule 2], [rule 3]
  
  [A3/A4 if active] Domain/community vocabulary:
    Use: [terms the audience actually uses]
    Avoid: [generic substitutes that signal distance]
  
  [A2 if emotional/sensitive context] Tone floor:
    Never use language that: [specific tone restrictions for this context]
  ```
- **Copy quality as context-appropriate signal** *(all)*: Generic, utilitarian copy signals low craft in any context — but what "low craft" means varies by axis. For commercial tools it signals low ambition; for community tools it signals unfamiliarity with the subject; for sensitive-context tools it signals emotional tone-deafness; for expert-audience tools it signals domain ignorance. Identify the highest-priority rewrites based on the most active axes in this app's profile.

#### §L5. INTERACTION & EXPERIENCE POLISH
- **Transition coherence**: Every transition tells the correct spatial/relational story. Elements that appear from nowhere should instead grow, slide, or fade from a logical direction.
- **Delight opportunities**: Are there interactions that are currently functional but could be made memorable without adding visual noise? (Examples: subtle success animations, satisfying completion states, smooth drag interactions)
- **State change communication**: When something important changes (new calculation result, data saved, error cleared) — is the change communicated as an event, not just a static update?
- **Scroll experience**: Is scroll behavior intentional? Smooth scroll where appropriate? Scroll position preserved and restored correctly?
- **Loading sequence**: For multi-stage loading — does the sequence feel progressive (each stage appears in order) or jarring (everything appears at once)?
- **The craft implementation checklist** `[A1][A2][A5]` — derived from the axis profile:
  - *High commercial intent (A1)*: `transform: scale(0.97)` on button press — `transition: all 0.2s ease-out` on interactive surfaces — skeleton loaders that mirror actual content layout — `font-variant-numeric: tabular-nums` on number columns — focus rings styled to match the design language — hover states with appropriate cursor changes — contextual empty states — integrated notification system — success confirmation that closes the interaction loop.
  - *Focus-critical contexts (A2)*: Every transition under 150ms — zero decorative animation — information-forward layout with no competing visual elements — instant feedback on every interaction — nothing moves that doesn't need to move.
  - *Emotionally sensitive contexts (A2)*: All transitions 200–400ms minimum — ease-in-out curves only — no abrupt appearance/disappearance — warm confirmation states — gentle empty states — no red for anything non-critical.
  - *Aesthetic-primary contexts (A5)*: UI chrome transitions under 100ms so attention stays on the output — output presentation given full visual investment — no interface element competes with what the tool produces.
  - *Any app — universal baseline*: Is there at least one detail that clearly took extra effort? Does the app look intentional rather than defaulted? Is spacing consistent enough that nothing feels accidental? Do transitions feel considered rather than left at browser defaults?
- **Motion budget**: Every animation in the app consumes attention. Total the number of simultaneous animated elements a user might see at once. More than 2–3 simultaneous animations competes for attention and degrades perceived quality. Identify any views where the motion budget is exceeded and recommend which animations to reduce or remove.

#### §L6. PERFORMANCE POLISH
- **Render jank identification**: Identify specific interactions where frame drops are likely and suggest targeted fixes within architecture constraints.
- **Perceived performance improvements**: Even without changing actual speed — optimistic UI, instant visual feedback, skeleton screens that match real content shape, progressive disclosure of complex results.
- **Startup sequence optimization**: What is the minimum viable first render? What can be deferred? Can the critical path be reduced without changing functionality?
- **Memory footprint reduction**: Identify data structures that could be more memory-efficient without changing behavior.

#### §L7. ACCESSIBILITY POLISH *(beyond compliance — toward excellence)*
- **Landmark structure**: Is the page structure clear to screen reader users navigating by landmark? `<main>`, `<nav>`, `<aside>`, `<header>`, `<footer>` used intentionally?
- **Heading hierarchy excellence**: Not just technically correct — does the heading structure help a screen reader user understand the page structure and navigate efficiently?
- **ARIA live region tuning**: Are `aria-live` regions set to the right politeness level (`polite` for informational, `assertive` only for genuinely urgent)?
- **Focus choreography**: For complex interactions (modals, multi-step flows, wizards) — does focus movement tell a coherent spatial story?
- **Color-independent comprehension**: Can every piece of meaning in the app be understood in grayscale?

---

### CATEGORY M — Deployment & Operations

#### §M1. VERSION & UPDATE MANAGEMENT
- **Version single source of truth**: App version in one place in the codebase?
- **Schema migration**: State schema changes across versions — migration from old to new handled?
- **Rollback strategy**: Bad deploy — how do users get back to a working state?
- **Cache busting**: Static assets get new URLs when content changes?

#### §M2. OBSERVABILITY
- **Error reporting**: Uncaught exceptions — sent to error monitoring? At minimum, logged to console in a structured way?
- **Debug mode**: Development-only logging gated behind a flag (not `console.log` left in production)?
- **State inspection**: Can a developer inspect current application state without browser devtools?
- **Admin action logging**: Privileged actions logged? Immutable audit trail?

#### §M3. FEATURE FLAGS & GRADUAL ROLLOUT
- **Flag inventory**: List every `if (FEATURE_FLAG)` or `if (process.env.FEATURE_X)` in the codebase. Are the flags documented?
- **Dead flags**: Flags that are always true or always false in production — dead code that should be cleaned up?
- **Flag coupling**: Feature flags that must be toggled together — is this documented? Toggleing one without the other creates a broken state?
- **Emergency kill switch**: For risky or AI-powered features — is there a runtime flag to disable without a deploy?
- **A/B test cleanup**: Concluded experiments with flag code still in place — when is it scheduled for cleanup?

---

### CATEGORY N — Internationalization & Localization

> Activate at full depth whenever §0 `Locale / i18n` is not "English only" or is omitted.
> Even English-only apps should pass the hardcoded-strings check — future i18n cost compounds with every unchecked string.

#### §N1. HARDCODED STRING INVENTORY
- **User-visible strings in source**: Every string rendered in the UI that is hardcoded in JS/JSX/HTML rather than in a locale resource — list all.
- **Pluralization logic**: `"1 item" / "2 items"` — handled with `Intl.PluralRules` or equivalent? Not `count === 1 ? "item" : "items"` (fails in many languages).
- **Concatenated UI strings**: `"You have " + count + " messages"` — word order varies by language; must use a template/message format, not concatenation.
- **Hardcoded error messages**: Error strings in catch blocks, validation messages, toast content — all extractable?
- **Screen reader only text**: `aria-label`, `alt`, `title` — hardcoded or localizable?

#### §N2. LOCALE-SENSITIVE FORMATTING
- **Number formatting**: Uses `Intl.NumberFormat` (or equivalent) for display? Decimal separator differs: `.` (EN) vs `,` (DE, FR). Thousands separator differs. Hardcoded `toFixed(2)` is not locale-safe for display.
- **Date/time formatting**: Uses `Intl.DateTimeFormat` (or equivalent) for display? Month/day order, 24h vs 12h, calendar system all vary by locale.
- **Currency display**: `$1,234.56` is US-only. `Intl.NumberFormat` with `style: 'currency'` handles locale-correct formatting.
- **Collation/sorting**: String `sort()` uses byte order — not correct for non-ASCII text. `Intl.Collator` for locale-aware alphabetical sort.
- **Relative time**: `Intl.RelativeTimeFormat` for "3 days ago" style strings?
- **List formatting**: `"A, B, and C"` (Oxford comma, EN) vs `"A, B et C"` (FR). `Intl.ListFormat` handles this.

#### §N3. RTL (Right-to-Left) LAYOUT
*(Activate only if §0 Locale includes Arabic, Hebrew, Persian, Urdu, or other RTL languages)*
- **`dir="rtl"` on `<html>`**: Set dynamically per locale?
- **CSS logical properties**: `margin-inline-start` / `padding-inline-end` instead of `margin-left` / `padding-right` — the latter don't flip in RTL.
- **Flexbox direction**: `flex-direction: row` items reverse in RTL — intentional?
- **Icon mirroring**: Directional icons (arrows, chevrons, progress indicators) — should they flip in RTL? (Checkmarks and warning icons should not.)
- **Text alignment**: `text-align: left` should be `text-align: start` for RTL safety.
- **Canvas/SVG**: Custom rendering code — does it have RTL awareness?
- **Third-party components**: Date pickers, dropdowns, data grids — do they respect `dir="rtl"`?

#### §N4. LOCALE LOADING & PERFORMANCE
- **Bundle size**: All locale data bundled upfront vs loaded on demand? Loading all locales adds significant weight.
- **Fallback chain**: Missing key in current locale → falls back to default locale → falls back to key name? No blank UI?
- **Locale detection**: Browser `navigator.language` used for detection? User override persisted to storage?
- **Dynamic locale switch**: App re-renders fully in new locale without page reload? State preserved across switch?

---

### CATEGORY O — Development Scenario Projection

> This category looks **forward**, not backward. Every other category diagnoses what is wrong today.
> This category answers: what will this codebase become under normal development pressure, growth, and time?
> The output is not a list of bugs — it is a map of the future the developer is currently building toward,
> with specific forks where a small choice now prevents an expensive problem later.

#### §O1. SCALE CLIFF ANALYSIS

For every data-intensive, storage-bound, or computation-bound operation, identify the data volume at which it transitions from "works fine" → "noticeably slow" → "crashes or becomes unusable". Express as concrete thresholds, not vague warnings.

For each identified cliff:
```
Operation:       {e.g. "Filtering items list", "localStorage write on save", "O(n²) sort"}
Location:        {specific function / component}
Current safe range:  {works acceptably up to N items / N KB / N concurrent actions}
Warning zone:    {degrades noticeably between N and M — user perceives lag}
Cliff edge:      {fails, freezes, or loses data above M}
Trigger:         {the specific user action or growth event that crosses this threshold}
Current trajectory: {estimated time to reach cliff at normal usage pace}
Fix window:      {how long the developer has before this becomes urgent}
```

Common cliff locations to analyze:
- **localStorage quota** (5MB hard cap): current payload size × growth rate per user action
- **O(n²) operations**: any sort + filter combination, nested loops over the same list, or `find()` inside a `map()`
- **Unvirtualized DOM lists**: lists rendered without virtualization — beyond 200–500 items, scroll jank becomes severe; beyond 2,000, the browser may freeze on initial render
- **Bundle parse time on mobile**: single-file apps growing past 500KB uncompressed are measurably slow to parse on mid-range Android (simulate with 4× CPU throttle)
- **Re-render cascade**: a global state change that re-renders the entire tree — harmless at small scale, increasingly expensive as component count grows
- **Regex performance on large inputs**: pathological backtracking on user-provided strings

#### §O2. FEATURE ADDITION RISK MAP

Based on §0 `Likeliest Next Features` and reasonable inference from the app's domain and trajectory, identify the top 5 features most likely to be added — then analyze exactly what in the current codebase will break, resist, or require expensive redesign when each is added.

For each anticipated feature:
```
Feature:               {name}
Probability:           HIGH / MEDIUM (based on domain norms, code signals, §0 roadmap)

Current code that conflicts or must change:
  - {specific function/pattern} at {location} — {why it conflicts with this feature}
  - {specific assumption} baked into {component} — {why it breaks under this feature}
  - {data structure choice} — {why it requires redesign for this feature}

Pre-adaptation cost (fix now, before feature exists):  Trivial / Small / Medium / Large
Post-addition cost (fix after feature is already built): {estimated 3–10× higher — why}

Pre-adaptation recommendation:
  {The minimal abstraction, interface, or structural change that opens the door for this feature
   without breaking any current behavior. This is not the feature itself — it is the preparation.}
```

Example conflicts to look for:
- **User accounts**: state stored flat (no `userId` scope) → all data must be re-keyed; no concept of "current user" in state schema → every component that reads state must be updated
- **Undo/redo**: state mutations applied directly → no command history; immutable state + command pattern required
- **Multi-device sync**: localStorage as sole persistence → no sync surface; no conflict resolution strategy
- **Theming / white-label**: hardcoded brand colors throughout → cannot swap theme without touching hundreds of values
- **Server-side rendering**: `window`/`document` accessed at module level → crashes during SSR; `localStorage` calls not guarded → crashes on server

#### §O3. TECHNICAL DEBT COMPOUNDING MAP

Not all technical debt is equal. Some debt is inert — it stays roughly the same cost to fix forever. **Compounding debt grows in cost with every new line of code built on top of it.** Identify which current issues are compounding — these must be prioritized above their individual severity suggests.

Compounding debt markers:
- **Foundation coupling**: Logic that other features are being built directly on top of, without an abstraction layer. Every new feature deepens the coupling, making the foundation progressively harder to change.
- **Terminology divergence**: The same concept named differently in different sections — as the codebase grows and more developers touch it, the confusion multiplies with every new file that references both names.
- **Schema without migration infrastructure**: A stored data schema with no version field and no migration logic — every schema change risks silently breaking all existing users' stored state. The cost to add migration infrastructure compounds with every release that ships without it.
- **Test debt on changing code**: Frequently-modified logic with no test coverage. Every untested change increases the probability of an undetected regression. This compounds — the longer it goes without tests, the more likely existing behavior is already wrong, and the harder it is to add tests without first understanding what "correct" means.
- **Copy-paste architecture that has already diverged**: Duplicated logic where the copies are now subtly different. Each new feature must be applied to every copy; each copy is an independent bug surface. The longer this persists, the more the copies diverge.
- **Magic constants without a registry**: Domain-critical numbers scattered through the code without centralization. Every new formula that uses one of these values may use a different hardcoded version — silent inconsistency that compounds with every new formula.

For each identified compounding debt item:
```
Debt:                     {description}
Location:                 {where it lives in the codebase}
Current cost to fix:      Trivial / Small / Medium / Large
Cost multiplier (6 months): {estimated — e.g. "3× harder after user accounts are added"}
Compounding trigger:      {the specific event or feature that causes the cost to jump}
Pre-emption recommendation: {the specific, minimal change that breaks the compounding cycle}
⏱ COMPOUNDS
```

#### §O4. DEPENDENCY DECAY FORECAST

For every external dependency (CDN script, npm package, third-party API), assess its forward risk profile.

| Dependency | Version | Maintenance Status | Risk Level | Specific Risk | Recommended Action |
|-----------|---------|-------------------|-----------|--------------|-------------------|
| {name} | {ver} | Active / Slow / Abandoned / Security history | LOW / MED / HIGH | {specific concern} | {action} |

Risk factors to assess for each:
- **Abandonment indicators**: No releases in 18+ months; single maintainer with reduced activity; issue response time > weeks; no responses to CVEs
- **Breaking change trajectory**: Frequent major versions; poor deprecation communication; current version many majors behind latest
- **Security history**: Prior CVEs — how quickly were they patched? Are there open unpatched vulnerabilities?
- **CDN single-point-of-failure**: Loaded from CDN without `integrity` attribute and with no fallback — a CDN compromise or outage causes catastrophic failure. Single CDN dependency for the entire app framework is a HIGH risk for any app with uptime expectations.
- **API version sunset**: External API endpoints with announced deprecation dates; versioned endpoints where the used version is no longer current
- **Framework compatibility drift**: Library last tested with framework version N; app now runs N+2; breaking changes in between are silent

#### §O5. CONSTRAINT EVOLUTION ANALYSIS

Based on §0 `Planned Constraint Changes` and natural growth pressure, analyze the migration complexity when the app outgrows each current constraint. The goal is to identify pre-adaptations — small, low-cost changes that make the eventual migration from 2 weeks of work to 2 days.

For each constraint likely to evolve:
```
Current Constraint:     {e.g. "localStorage-only persistence"}
Evolution Trigger:      {the growth or feature requirement that forces this change}
Migration Complexity:   LOW / MEDIUM / HIGH / PROHIBITIVE (if attempted without pre-adaptation)

Migration obstacles (specific — function/pattern names):
  - {what in the current code assumes this constraint and must be refactored}
  - {what data transformation is required for existing users' stored data}

Pre-adaptation opportunity:
  {The abstraction, interface, or structural change that can be added now at low cost
   that converts the eventual migration from a rewrite into a substitution.
   Cost now: {Trivial/Small}. Avoided cost later: {Medium/Large}.}
```

Key constraint evolutions to analyze by architecture type:
- **localStorage → backend API**: Are all storage read/writes behind a service/repository abstraction? Or called directly from components? Direct calls mean every component must be updated during migration.
- **Single-user → multi-user**: Is data stored with user scope (`userId` prefix) or flat? Flat storage requires a data migration affecting every existing user.
- **CDN imports → build pipeline**: Do imports use bare specifiers (`import React from 'react'`) compatible with bundlers? Any `eval()`, `new Function()`, or string-based dynamic imports that break tree-shaking?
- **Hardcoded locale → multi-locale**: What is the cost of string extraction? Are date/number formats centralized or scattered?
- **Monolith → modular**: Circular dependency chains? Implicit shared global state between would-be modules? Which features are genuinely isolated vs. deeply entangled?

#### §O6. MAINTENANCE TRAP INVENTORY

Identify every location in the codebase that is disproportionately risky to modify — where a developer making what appears to be a simple change is at high risk of introducing a non-obvious regression.

For each maintenance trap:
```
Trap name:          {short descriptive name}
Location:           {specific function, component, or section}
Why it's a trap:    {the specific coupling, hidden dependency, or non-obvious behavior
                     that makes this section dangerous to touch}
Symptom signature:  {the error or failure mode a developer would see after accidentally breaking it}
Safe modification protocol: {the specific step-by-step check a developer must do before
                              and after any change to this section}
Defusion recommendation:    {the refactor that eliminates the trap — labeled separately from bug fixes}
```

Common maintenance trap patterns to scan for:
- **Functions with hidden side effects**: appear to compute a value but secretly mutate shared state, write to storage, or trigger network calls as a side effect — callers assume they are pure
- **Order-dependent initialization**: works only if functions or modules are called in a specific sequence, but nothing in the code enforces or documents this order
- **Load-bearing "magic" values**: constants whose specific values are non-obvious but critical — changing them "slightly" breaks unrelated functionality
- **Deep prop chains**: a prop value that flows through 5+ component layers — renaming or reshaping it requires updating every intermediate component
- **CSS specificity landmines**: a rule that overrides another rule via specificity, not structure — changing either rule breaks the other without any connection being visible in the source
- **Global state assumed by multiple components**: state that two or more components both read and write, with no coordination mechanism — changes to the write pattern silently break the read pattern

#### §O7. BUS FACTOR & KNOWLEDGE CONCENTRATION

Identify code sections that are effectively a **black box** — where the implementation is only safely modifiable by whoever wrote it, or where the only documentation is "don't touch this."

For each high-risk knowledge concentration:
```
Location:           {specific function/section}
Knowledge gap:      {what a new developer cannot understand from reading the code alone}
Bus factor risk:    {what breaks or becomes unmaintainable if the author is unavailable}
Minimum documentation:  {the specific comment or documentation that would make this safe
                          for a developer unfamiliar with the section to modify}
```

---

**§O — Required Output: Scenario Projection Summary**

This table must appear at the end of the Projection Analysis part:

| Scenario | Likelihood | Time Horizon | Current Readiness | Pre-adaptation Cost | Without Pre-adaptation |
|----------|-----------|-------------|-------------------|--------------------|-----------------------|
| {e.g. "User reaches 500+ items"} | HIGH | 3 months | NOT READY — cliff at 200 items | Small | Large refactor under pressure |
| {e.g. "Adding user accounts"} | HIGH | 6 months | PARTIAL — no user scoping in schema | Medium | Data migration + full state redesign |
| {e.g. "Moving to a backend"} | MEDIUM | 12 months | NOT READY — no storage abstraction | Small | Every component must be updated |
| {e.g. "React CDN major version bump"} | MEDIUM | 18 months | READY — no deprecated API usage | Trivial | Small |
| {e.g. "Second developer on the team"} | HIGH | Now | NOT READY — 3 maintenance traps, 2 knowledge concentration zones | Small docs effort | High onboarding risk |

---

## V. FINDING FORMAT

Every finding follows this exact format. No exceptions. No vague findings.

```
[SEVERITY] [CONFIDENCE] — {Short descriptive title}

Category:         {§Letter.Number} — {Category and Dimension Name}
Location:         Line {N} in {function/component name} / "near `functionName()`" if line unknown
Domain Impact:    {Connects finding to the app's actual purpose and user stakes}

Description:
  {What is wrong or suboptimal. References the exact variable, function, value, or CSS class.
   Never generic. "The `handleImport` function" not "the import function".}

Evidence:
  {For [CONFIRMED]: direct code reference or execution trace.
   For [LIKELY]: strong circumstantial code evidence.
   For [THEORETICAL]: the reasoning chain explaining why this is a risk.}

User Impact:
  {Concrete scenario: what actually happens to a real user because of this issue.}

Recommendation:
  {Specific, actionable. Before/after code snippet where feasible.
   If multiple approaches exist, rank them by safety and effort.
   Tag recommendations that change behavior with ⚠ BEHAVIOR CHANGE.
   Tag recommendations that touch design identity with ✦ IDENTITY-SENSITIVE.
   Tag findings whose cost grows over time with ⏱ COMPOUNDS.}

Effort:        Trivial (<5 min) | Small (<1 hr) | Medium (<1 day) | Large (>1 day)
Risk:          {What could break if this recommendation is applied incorrectly}
Cross-refs:    {Other finding IDs that should be fixed together or in sequence}
Automated Test: {What test type (unit/integration/E2E/snapshot) and what assertion would catch a regression of this finding — e.g., "Unit test: `expect(calculateTax(100, 'FR')).toBe(20)` — currently returns 21 due to wrong rate"}
```

### Severity Scale

| Level | Meaning | When to Apply |
|-------|---------|---------------|
| **CRITICAL** | App-breaking crash, data loss, security breach, wrong output causing real harm | XSS vector, import corrupts all state, wrong financial/medical calculation, prototype pollution |
| **HIGH** | Major feature broken, significant user harm, serious accessibility failure, important data wrong | Broken workflow, missing critical validation, modal not keyboard-accessible, wrong domain data |
| **MEDIUM** | Feature partially wrong, degraded UX, moderate accessibility issue, noticeable inconsistency | Missing loading state, color contrast below 4.5:1, inconsistent number formatting, NaN not handled |
| **LOW** | Minor inconsistency, cosmetic issue, non-critical opportunity | Spacing inconsistency, slightly wrong animation timing, minor dead code |
| **NIT** | Style, naming, purely aesthetic with no UX impact | Typo in comment, inconsistent quote style, unused `console.log` in dev path |

**Stakes multiplier**: For CRITICAL-stakes apps, apply one level upward to findings in §A, §B, §C, §K.

**Optimization/Polish findings**: Use severity to express the improvement value, not a defect level. A HIGH polish finding means a high-value design improvement opportunity.

### Confidence Scale

| Level | Meaning |
|-------|---------|
| **[CONFIRMED]** | Verified by reading code and tracing execution path |
| **[LIKELY]** | Strong code evidence; near-certain but not runtime-verified |
| **[THEORETICAL]** | Architectural risk that cannot be confirmed without runtime testing |

---

## VI. REQUIRED DELIVERABLES

### Tier 1 — Must Complete (Parts 1–4)

| Deliverable | Format | Contents |
|-------------|--------|---------|
| **Feature Preservation Ledger** | Table | Feature · Status (Working/Broken/Partial/Unknown) · Dependencies · Safe to Modify · Safe to Remove · Notes |
| **Design Identity Record** | Summary | Confirmed design character, protected signature elements, any ambiguities resolved with user |
| **Architecture Constraint Map** | Table | Constraint · Why it exists · What breaks if violated · How recommendations respect it |
| **Domain Rule Verification Table** | Table | Rule from §0 · Code value/implementation · Match (✓/✗/⚠) · Finding ID if mismatch |
| **Workflow Trace Report** | Per-workflow | Each step · Code location · Bugs found at step · Pass/Fail |
| **Data Integrity Report** | Table | Input · Validation gap · Invalid values possible · Downstream corruption |
| **Priority Action Items** | Two-tier | Tier A: Quick Wins (CRITICAL/HIGH + Trivial/Small) · Tier B: Strategic (remaining CRITICAL/HIGH by user impact) |
| **Scenario Projection Summary** | Table | Scenario · Likelihood · Time Horizon · Current Readiness · Pre-adaptation Cost · Cost Without Pre-adaptation |

### Tier 2 — Should Complete (Parts 5–10)

| Deliverable | Contents |
|-------------|---------|
| **Sensitive Data Inventory** | Every stored/transmitted datum: classification, protection, risk |
| **Data Flow Diagram** | `Input → Validation → State → Computation → Display` with gap annotations at every arrow |
| **Graceful Degradation Matrix** | Dependency · Failure mode · User impact · Current fallback · Quality (Good/Partial/None/Crash) |
| **Resource Budget Table** | Resource · Size · Load strategy · Critical path? · Optimization opportunity |
| **Web Vitals Estimate** | LCP, FID/INP, CLS — each with bottleneck and fix |
| **WCAG 2.1 AA Scorecard** | Criterion · Pass/Fail/N/A · Evidence · Fix |
| **Cross-Browser Matrix** | Feature × Browser: Pass/Fail/Partial/Unknown |
| **Design Token Inventory** | Every unique spacing, color, radius, shadow, z-index, transition — with consolidation plan |
| **Component Quality Scorecard** | Every component type: variant completeness, state completeness, visual consistency grade |
| **Copy Quality Inventory** | Every piece of UI copy: voice consistency, clarity, conciseness, suggested rewrites |
| **i18n Readiness Report** | Hardcoded string count, locale-unsafe format calls, RTL gaps, estimated i18n migration effort |

### Tier 3 — Complete if Time Allows (Parts 11+)

| Deliverable | Contents | Applies To |
|-------------|---------|------------|
| **Optimization Roadmap** | Code efficiency, render performance, bundle size — ranked by effort vs impact | All |
| **Design System Standardization Plan** | Token consolidation, component unification, pattern library gaps | All |
| **Polish Delta Report** | Per section: specific changes that move from "functional" to "intentional/professional" (framing adapted to product nature) | All |
| **Brand Voice Guide** | Voice adjectives, always/never rules, copy rewrites — adapted to product nature (community authenticity vs conversion copy) | All |
| **Commercial Readiness Assessment** | First-impression audit, competitive benchmark, monetization-tier alignment gap | Paid/Freemium/B2B only |
| **Thematic Fidelity Assessment** | Source-material color/type/tone alignment, community authenticity audit, fan credibility signals | Fan/Community tools only |
| **Visual Identity Report** | Brand signature, color/type/motion alignment to personality, differentiation or fidelity opportunities | All (framing varies) |
| **Missing Tests Matrix** | Critical code paths → test type (unit/integration/E2E) → priority | All |
| **Architecture Evolution Roadmap** | (1) Safe incremental improvements · (2) Medium-term refactors · (3) Long-term goals | All |
| **Domain-Specific Deep Dive** | Per §K dimensions activated by domain classification | All |

---

## VII. SUMMARY DASHBOARD (Final Part)

### Findings Table

| Category | Total | CRIT | HIGH | MED | LOW | NIT | Quick Wins |
|----------|-------|------|------|-----|-----|-----|------------|
| A — Logic | | | | | | | |
| B — State | | | | | | | |
| C — Security | | | | | | | |
| D — Performance | | | | | | | |
| E — Visual Design | | | | | | | |
| E8 — Product Aesthetics | | | | | | | |
| E9 — Brand Identity | | | | | | | |
| E10 — Data Storytelling | | | | | | | |
| F — UX/Copy | | | | | | | |
| F6 — Engagement/Delight | | | | | | | |
| G — Accessibility | | | | | | | |
| H — Compatibility | | | | | | | |
| I — Code Quality | | | | | | | |
| J — Data/Viz | | | | | | | |
| K — Domain | | | | | | | |
| L — Optimization | | | | | | | |
| M — Ops | | | | | | | |
| N — i18n/L10n | | | | | | | |
| O — Projection | | | | | | | |
| **Total** | | | | | | | |

### Root Cause Analysis

```
RC-{N}: {Root Cause Name}
Findings affected: F-001, F-007, F-012 (list all)
Description: The upstream condition that, if fixed, resolves multiple downstream findings
Fix leverage: Fixing this one root cause replaces {N} individual fixes
```

### Compound Finding Chains

```
Chain-{N}: {Name}
Combined Severity: {Escalated beyond individual findings}
  Step 1: [F-003] [LOW]  — {description}
  Step 2: [F-011] [MED]  — {description}
  Step 3: [F-019] [HIGH] — {description}
  Combined: {User harm scenario} → {Severity at stakes level}
```

### Positive Verifications

{N} critical paths confirmed working correctly:
- `{feature}` — verified via {method} — no issues found

### Top 10 Quick Wins

Highest (severity × user impact) with lowest effort — fix these first:

| # | ID | Title | Severity | Effort | Impact |
|---|----|----|---------|--------|--------|
| 1 | | | | | |

### Remediation Roadmap

```
IMMEDIATE — before next release:
  [ ] F-{id} {title} — Effort: {X} — Risk: {Y}

SHORT-TERM — next sprint:
  [ ] F-{id} ...

POLISH SPRINT — standalone improvement sprint:
  [ ] Design token consolidation — Effort: Medium
  [ ] Copy standardization — Effort: Small
  [ ] Component variant completion — Effort: Medium

MEDIUM-TERM — next 1–3 months:
  [ ] ...

ARCHITECTURAL — 6+ months:
  [ ] ...
```

---

## VIII. CROSS-CUTTING CONCERN MAP

> Patterns that span multiple categories. Check each one after completing all audit dimensions.

| Concern | Sections | Failure Chain |
|---------|----------|---------------|
| **Data Integrity** | | |
| Floating-point precision | §A1, §A2, §J1 | Calculation drift → wrong display → user decisions |
| Validation gap chain | §B3, §A1, §F4 | Missing validation → wrong logic → wrong display → user harm |
| Input boundary cascade | §A1, §B3, §D1 | Out-of-range value → engine crash or wrong silent result |
| Type coercion in validation | §A7, §B3 | String input → `+` concatenates → invalid value passes → corrupts downstream |
| NaN/Infinity propagation | §A7, §B3, §J1 | Silent NaN → propagates through pipeline → wrong display |
| **State & Persistence** | | |
| Storage limits | §B2, §I1 | Quota exceeded → silent data loss → corrupted reload |
| Concurrent state modification | §A4, §B2 | Multiple tabs / rapid actions → race condition → data corruption |
| Stale closure cascade | §A6, §B1 | Missing useEffect deps → stale state → wrong computation → wrong display |
| Mutation through abstractions | §B6, §B1 | Shallow copy → child mutates nested object → parent state silently corrupted |
| Import/export chain | §B4, §C3, §C5 | Malformed import → prototype pollution → state corruption |
| **Security** | | |
| AI output injection | §K5, §C2 | LLM output via innerHTML → XSS from adversarial model output |
| Domain data fabrication | §A1, §K1–K5, §I.5 | Unverified domain fact used as finding basis → developer acts on false info |
| **Visual & Design** | | |
| Theme completeness | §E1, §E3, §L3 | Hardcoded color bypassing theme → inconsistency + a11y failure |
| Design token fragmentation | §E1, §L3 | One-off values throughout → visual inconsistency + maintenance burden |
| Design-nature mismatch | §E8, §E9, §L3 | Polish misaligned with product nature → blocks conversion / feels inauthentic |
| Color psychology conflict | §E3, §E9 | Palette emotion mismatched to domain → subconscious friction |
| Brand identity absence | §E9, §E7 | No visual signature → indistinguishable from competitors |
| Copy inconsistency | §F4, §L4 | Same concept named differently → user confusion |
| Copy-tier mismatch | §L4, §F4 | Generic copy undermines trust built by visual design |
| Delight debt | §F6, §L5 | No personality or reward moments → product feels transactional |
| **Accessibility & Compatibility** | | |
| Semantic HTML gap | §G1, §G2, §G3 | `<div>` buttons → no keyboard, no screen reader, no WCAG |
| Reduced motion gap | §E6, §G4 | CSS respects prefers-reduced-motion but canvas/JS doesn't |
| **Infrastructure** | | |
| Worker reliability | §D1, §H2, §H4 | Blob Worker incompatibility → missing fallback → wrong results |
| External dependency failure | §H2, §H4, §J3 | CDN/image host down → crash vs graceful degrade |
| Stale cache on deploy | §H2, §M1 | SW serves old JS with new schema → silent corruption |
| Timezone/DST | §A3, §A5 | Wrong DST offset → wrong dates/countdowns |
| Locale assumption | §N1, §N2, §J1 | Hardcoded formats → wrong display in non-English locales |
| Feature flag coupling | §M3, §A4 | Flags toggled independently when they must be together → broken state |
| **Growth & Evolution** | | |
| Compounding constraint | §O5, §B2 | Direct localStorage calls everywhere → migration requires touching every component |
| Scale cliff invisibility | §O1, §D1 | O(n²) works at dev volume → cliff invisible until production |
| Cross-audit contradiction | §0, §I.5 | Second audit silently produces different value for same rule |
| **§X/§XI Specific** | | |
| R&D-audit disconnect | §X.2, §VII | Improvement plan ignores audit findings → new features on broken foundation |
| Existing feature blindness | §X.0, §X.2 | New feature excitement → existing improvements perpetually defer |
| Polish regression cascade | §XI.2, §XI.5 | Polish one dimension, degrade another → caught only by quality gates |
| Restructuring-during-polish | §XI.3, §XI.2 | Code + visual changes mixed → regression source ambiguous |
| Feature preservation gap | §XI.1, §XI.5 | Ledger incomplete → polish breaks unlisted feature |
| Coherence fracture cascade | §XI.0, §X.0 | Healing one fracture reveals deeper one → re-analyze after each heal |
| Vision drift | §XI.0, §XI.5 | Vision forgotten by step 15 → later steps optimize for code, not product |

---

## IX. FINAL MANDATE

**Every finding must be specific enough that the developer can implement it without asking a follow-up question.**

"Improve error handling" fails. "`handleImport()` near line 847 calls `JSON.parse(pastedText)` without try/catch — any non-JSON clipboard content crashes the React tree. Wrap in try/catch, display toast: 'Clipboard content is not valid JSON.'" passes.

**Every domain fact carries a source tag:** `[CODE]`, `[§0-CONFIRMED]`, or `[UNVERIFIED]`. A finding based on `[UNVERIFIED]` data is a question, not a finding.

**Cross-session continuity:** Prior `[§0-CONFIRMED]` rules carry forward. Conflicts are surfaced as `[CONFLICT]` — the developer arbitrates.

**The audit serves the app's vision, not a generic standard.** The Five-Axis framework (§I.4) determines what "good" looks like for *this specific app*. A nurse's calculator, a meditation app, a gacha companion, and a B2B dashboard all require different aesthetic reasoning.

**Time dimension:** ⏱ COMPOUNDS findings are highest-leverage because their cost grows with delay. §O Scenario Projection is a first-class deliverable.

**Audit connects to action.** §X determines *what* to improve. §XI determines *how* to make it coherent. Both protect the Feature Preservation Ledger and Design Identity.

**Execution:** Follow §III. Begin with Part 1. For apps > 3,000 lines, confirm with user after Part 1. Part 1 = read entire codebase → classify → extract domain rules → confirm design identity → build Feature Preservation Ledger → announce plan → wait.

---

## X. RESEARCH, DEVELOPMENT & IMPROVEMENT PROTOCOL

> **Evaluates existing features** (which may have drifted, stagnated, or been half-finished) **and new feature opportunities** — then produces a unified, prioritized development plan. The most impactful improvement is often not a new feature — it is an existing feature made twice as good.
>
> **Prerequisite**: §0 + §I classification (lightweight if standalone, full if post-audit).
>
> **Execution order**: §X.0 (look inward) → §X.1 (look outward) → §X.2 (prioritize) → §X.3 (deliverable).

---

### §X.0. EXISTING FEATURE DEEP EVALUATION

> Before looking outward (competitors, new features), look inward. Apps grow feature by feature, each built at a specific point in time. Over months, features drift: UX evolves but old features don't update, two features overlap, a feature shipped at MVP was never revisited.

#### X.0.1 — Feature Health Audit

For every feature in the app, evaluate across six dimensions:

```yaml
Feature: {name}
  # ── FUNCTIONAL ──
  Correctness:     SOLID / FRAGILE / BROKEN
    # Does it produce the right output? Always, or only on the happy path?
  
  # ── UX ──
  Usability:       INTUITIVE / ADEQUATE / CONFUSING / HOSTILE
    # Can a user accomplish the task without guessing? Has the UX evolved with the app?
  Discoverability: OBVIOUS / FINDABLE / HIDDEN / ORPHANED
    # Can users find this feature? Or has navigation growth buried it?
  
  # ── DESIGN ──
  Visual coherence: INTEGRATED / DATED / INCONSISTENT / ALIEN
    # Does it visually belong to the current version of the app, or an older era?
  
  # ── STRATEGIC ──
  User value:      CORE / IMPORTANT / MINOR / VESTIGIAL
    # If removed, would users notice? Would they leave?
  Completion:      COMPLETE / 80% DONE / HALF-BAKED / STUB
    # Was this feature fully realized, or shipped at MVP and never revisited?
  
  # ── DRIFT (most important dimension) ──
  Drift from current standard: NONE / MILD / SIGNIFICANT
    # Compared to the app's best features — how far has this one fallen behind?
    # This inconsistency is invisible to the developer but obvious to the user.
```

#### X.0.2 — Feature Relationship Map

Features don't exist in isolation. Map how they depend on, overlap with, and sometimes contradict each other:

```yaml
Dependencies:     # Feature A requires Feature B to function
  - "{A} → {B}: {what breaks if B changes — e.g. 'Export depends on Import's data model'}"

Overlaps:         # Feature A and Feature B do similar things
  - "{A} ↔ {B}: {how they overlap — e.g. 'Quick Add and Full Editor both create entries 
     with different validation rules, built in different sprints, never reconciled'}"

Contradictions:   # Feature A and Feature B imply different mental models
  - "{A} ✕ {B}: {the conflict — e.g. 'Settings has auto-save toggle but Editor always 
     shows a manual Save button — user can't tell which is happening'}"

Orphans:          # Features disconnected from the rest of the app
  - "{feature}: {why it's disconnected — e.g. 'Analytics page exists but nothing links to it'}"

Missing Bridges:  # Features that should connect but don't
  - "{A} ⇥ {B}: {the missing link — e.g. 'Items created in A can't be referenced from B'}"
```

#### X.0.3 — Feature Evolution Assessment

For every feature rated below SOLID + INTUITIVE + INTEGRATED + CORE + COMPLETE:

| Action | When | Meaning |
|--------|------|---------|
| **ELEVATE** | Valuable but below current quality standard | Bring to the standard of the app's best features. No scope change — quality uplift only. |
| **EVOLVE** | Works but users need more than it offers | Add depth/options. The feature's *scope* expands. |
| **CONSOLIDATE** | Two+ features overlap significantly | Merge into one coherent feature that does both jobs better. |
| **REIMAGINE** | Fundamental UX approach is wrong | Redesign from the user's perspective. Same goal, different interaction model. Higher risk/reward. |
| **DEPRECATE** | Vestigial — low usage, no strategic value | Plan graceful removal. Migrate data/expectations first. |
| **LEAVE** | Healthy, coherent, well-integrated | No action. Confirm explicitly so the developer knows it was evaluated, not skipped. |

```yaml
Feature: {name}
  Action:      {ELEVATE / EVOLVE / CONSOLIDATE / REIMAGINE / DEPRECATE / LEAVE}
  Rationale:   {why — tied to health audit findings}
  Current:     {1–2 sentences: what the feature does now and how it feels}
  Target:      {1–2 sentences: what the feature should become}
  Changes:
    - {concrete change — e.g. "Replace 3-step modal with inline editor matching Feature Y's pattern"}
    - {concrete change — e.g. "Add validation feedback that was missing"}
    - {concrete change — e.g. "Update visual style to current design tokens"}
  Effort:      LOW / MEDIUM / HIGH
  User impact: {specific experience improvement — not abstract quality}
  Risk:        {what could break or regress}
```

#### X.0.4 — Feature Coherence Score

Rate the app's feature coherence as a whole:

```yaml
Feature Coherence:
  Total features:                {N}
  At current standard:           {N} ({%})
  With significant drift:        {N} ({%})
  Overlapping pairs:             {N}
  Contradicting pairs:           {N}
  Orphaned features:             {N}
  Missing bridges:               {N}
  
  Rating: HIGH / MEDIUM / LOW / CRITICAL
    # HIGH:     ≥80% at standard, no contradictions, no orphans
    # MEDIUM:   ≥60% at standard, ≤1 contradiction, ≤1 orphan
    # LOW:      <60% at standard, or ≥2 contradictions/orphans
    # CRITICAL: The app feels like multiple apps stitched together
  
  Narrative: {2–3 sentences — does the app feel like one product or a patchwork? Where do seams show?}
```

**LOW or CRITICAL coherence → §XI must include a holistic coherence pass** (not just code restructuring).

---

### §X.1. COMPETITIVE & LANDSCAPE RESEARCH

> Internal state understood — now look outward. Understand what exists, what users expect, and where the gaps are.
>
> **Claude execution note**: Use `WebSearch` for competitor discovery — launch parallel searches for different competitor aspects. Use `WebFetch` to analyze competitor websites/app store listings. If web search is unavailable, use `AskUserQuestion` to ask the user to list 2–3 competitors and describe their strengths/weaknesses. Skip §X.1.1–X.1.3 if the developer explicitly says they don't care about competitors — go straight to §X.2 with only §X.0 findings as input.
>
> **For mobile apps**: Search app stores (Google Play, App Store) for competitor analysis. Use `WebFetch` on store listing URLs to extract feature lists, ratings, and user reviews.

#### X.1.1 — Direct Competitor Inventory

For the app's domain (from §0), identify the closest alternatives — tools solving the same problem for the same audience.

```
Competitor-{N}: {Name}
  URL / Platform:       {where it lives}
  Overlap:              {which of this app's features it also covers}
  Differentiation:      {what it does that this app does NOT}
  Weakness:             {where this app is already stronger}
  UX Model:             {key interaction patterns — how does it structure the user's workflow?}
  Monetization:         {how it sustains itself}
  Visual Tier:          {Rough visual quality: Prototype / Functional / Polished / Premium}
  User Sentiment:       {From reviews, forums, app stores — what do users love? What do they complain about?}
```

**Minimum**: 3 competitors for any app with commercial intent. 2 for community/free tools. 0 only for genuinely novel concepts — and even then, identify adjacent-domain tools.

#### X.1.2 — Feature Gap Matrix

Map features across this app and its competitors to identify gaps and opportunities — for both *missing* features and *existing features that competitors do better*:

```
| Feature / Capability          | This App       | Competitor A | Competitor B | Competitor C | Opportunity |
|-------------------------------|----------------|-------------|-------------|-------------|-------------|
| {e.g. "Offline mode"}        | ✗ Missing      | ✓           | ✗           | ✓           | HIGH — two competitors offer it, users expect it |
| {e.g. "Export to PDF"}       | ✓ Basic        | ✓ Advanced  | ✓ Basic     | ✓ Advanced  | UPGRADE — feature exists but competitors' versions are significantly better |
| {e.g. "AI-assisted input"}   | ✗ Missing      | ✗           | ✗           | ✗           | DIFFERENTIATOR — nobody offers it yet |
| {e.g. "Search"}              | ✓ Broken UX    | ✓ Excellent | ✓ Good      | ✓ Good      | CRITICAL UPGRADE — feature exists but is embarrassingly behind competitors |
| {e.g. "Custom themes"}       | ✓ Unique       | ✗           | ✗           | ✗           | STRENGTH — exclusive feature, no competition |
```

**Classify each row**:
- `PARITY` — table-stakes, must-have, this app already has it at competitive quality
- `UPGRADE` — feature exists but competitors do it noticeably better
- `CRITICAL UPGRADE` — feature exists but is so far behind competitors that it actively hurts the app's credibility
- `OPPORTUNITY` — users want it, some competitors have it, this app doesn't
- `DIFFERENTIATOR` — nobody offers it yet — blue ocean
- `STRENGTH` — this app does it and competitors don't — protect and promote this
- `OVER-SERVED` — this app has it, nobody else does, but it's unclear if users actually value it

**The distinction between UPGRADE and new OPPORTUNITY is crucial.** Upgrading an existing feature that users already rely on is almost always higher-leverage than adding a new feature — because the user base, the UX patterns, and the data model already exist. The developer just needs to make them better.

#### X.1.3 — User Signal Synthesis

Collect and structure all available signals about what users actually want, need, and struggle with. These signals outrank the developer's intuition and the auditor's analysis.

| Signal Source | What to Extract |
|---------------|-----------------|
| **User feedback** (direct messages, emails, form submissions) | Explicit requests, complaints, praise — verbatim where possible |
| **App store / review site reviews** | Recurring themes in positive and negative reviews — not individual outliers |
| **Community discussions** (Reddit, Discord, forums about the domain) | What problems do people describe? What workarounds do they use? What do they wish existed? |
| **Support tickets / bug reports** | Patterns — which features generate the most confusion or the most requests? |
| **Usage analytics** (if available) | Most-used features, abandoned flows, bounce points, session duration |
| **Competitor reviews** | What users praise and criticize about alternatives — these are proxy signals for this app's roadmap |

**Output**: A ranked list of **User-Validated Needs** — needs that appear in ≥2 independent signal sources. Single-source requests are listed separately as **Unvalidated Signals**.

#### X.1.4 — Technology & Approach Research

For each high-priority improvement or new feature, research the best available approaches before committing to an implementation:

```
Improvement: {e.g. "Add real-time collaboration"}
  Approaches Considered:
    1. {e.g. "CRDTs via Yjs"} — Pros: {X} / Cons: {Y} / Effort: {Z}
    2. {e.g. "Operational Transform via ShareDB"} — Pros: {X} / Cons: {Y} / Effort: {Z}
    3. {e.g. "Simple last-write-wins with polling"} — Pros: {X} / Cons: {Y} / Effort: {Z}
  Recommended Approach: {N} — Rationale: {why this approach wins for this specific app's constraints}
  Architecture Impact: {what existing code must change to support this}
  Risk Assessment: {what could go wrong — integration risk, performance risk, UX risk}
```

**Critical constraint**: All recommendations must respect §0 Architectural Constraints. An approach that requires abandoning a constraint is an *architectural proposal*, clearly marked as such — not a standard recommendation.

---

### §X.2. IMPROVEMENT PRIORITIZATION

> Existing feature improvements compete with new features on equal terms. Correct for "new feature bias" — an existing feature used daily has a larger impact surface than a new feature used weekly by some.
>
> **Claude execution note**: Present the Impact × Effort matrix to the user as a table. Let them react before producing the sequenced roadmap — they may disagree with impact/effort assessments.

#### X.2.1 — Unified Improvement Inventory

Before prioritizing, build a single list that contains *every* potential improvement from *every* source — existing features and new features side by side, evaluated on equal terms:

| Source | Type | Examples |
|--------|------|----------|
| §X.0 Feature Health Audit | Existing feature improvement | ELEVATE, EVOLVE, CONSOLIDATE, REIMAGINE actions |
| §X.1 Competitive Research | Existing feature upgrade OR new feature | UPGRADE, CRITICAL UPGRADE, OPPORTUNITY, DIFFERENTIATOR |
| §X.1.3 User Signal Synthesis | Either | Validated user needs — may point to existing feature frustrations or new feature requests |
| Audit findings (§IV) | Existing feature fix | Outstanding MEDIUM/LOW findings not yet resolved |
| Developer's own roadmap | Usually new feature | Ideas the developer has been planning |
| §O Scenario Projection | Architectural pre-adaptation | Infrastructure work that enables future features |

**Every item in this inventory is classified**:
```
Item: {description}
  Type:         EXISTING-ELEVATE / EXISTING-EVOLVE / EXISTING-CONSOLIDATE / EXISTING-REIMAGINE / NEW-FEATURE / INFRASTRUCTURE / BUG-FIX
  Source:       {which analysis identified this — §X.0 / §X.1 / user signal / audit / developer roadmap}
  Feature(s):   {which existing feature(s) this affects, or "NEW: {feature name}"}
```

#### X.2.2 — Impact × Effort Matrix

Place every item from the inventory on the matrix:

| | LOW EFFORT | HIGH EFFORT |
|---|---|---|
| **HIGH IMPACT** | **DO FIRST** — quick wins, ship this week | **PLAN CAREFULLY** — strategic investments requiring planning |
| **LOW IMPACT** | **FILL GAPS** — low-hanging fruit for downtime | **DEFER OR DROP** — revisit when effort drops or impact rises |

```yaml
Item: {description}
  Type:        {EXISTING-ELEVATE / EXISTING-EVOLVE / EXISTING-CONSOLIDATE / NEW-FEATURE / INFRASTRUCTURE / BUG-FIX}
  Source:      {§X.0 / audit F-XXX / user signal / competitive gap / developer roadmap}
  Impact:      HIGH / MEDIUM / LOW — {specific reason tied to user value}
  Effort:      HIGH / MEDIUM / LOW — {specific scope assessment}
  Quadrant:    {DO FIRST / PLAN CAREFULLY / FILL GAPS / DEFER}
  Dependencies: {what must be done first}
  # For existing feature improvements:
  Current pain:    {what users experience today}
  Risk of inaction: {what happens if this stays as-is for 6 months}
```

#### X.2.3 — Strategic Sequencing

Order matters. Some improvements unlock others. Some become impossible after others ship. Build the optimal sequence:

```
Phase 1 — Foundation (Sprint 1–2):
  Goal: {what user-facing improvement this phase delivers}
  Items: {list with IDs and types — EXISTING-ELEVATE, NEW-FEATURE, etc.}
  Unlocks: {what becomes possible after this phase}
  
Phase 2 — Core Value (Sprint 3–5):
  Goal: {what user-facing improvement this phase delivers}
  Items: {list with IDs and types}
  Depends on: Phase 1 items {specific IDs}
  
Phase 3 — Differentiation (Sprint 6–8):
  Goal: {what user-facing improvement this phase delivers}
  Items: {list with IDs and types}
  Depends on: Phase 2 items {specific IDs}
  
Phase 4+ — Growth & Polish:
  {Continue as needed}
```

**Sequencing rules**:
1. Bug fixes before everything — always. A new feature on a broken foundation compounds technical debt.
2. Existing feature CRITICAL UPGRADEs before new features — a feature that embarrasses the app every time a user touches it is a higher priority than any feature that doesn't exist yet.
3. CONSOLIDATE actions early — merging overlapping features simplifies the codebase and the user's mental model, which makes everything that follows cheaper and cleaner.
4. Infrastructure before features that depend on it — always. Don't build collaboration before auth.
5. ELEVATE actions alongside new features — bringing old features to the current standard can often be batched efficiently when the developer is already in that area of the code.
6. User-validated needs before developer-intuited features — unless the developer has strong domain expertise that users cannot articulate.
7. Differentiators before parity features — when possible. Parity features attract users who are already comparison-shopping; differentiators attract users who weren't looking.
8. REIMAGINE actions are planned like new features — because they effectively are. They need the same research, design, and testing rigor.

#### X.2.4 — Experimentation Protocol

For improvements where the right approach is genuinely uncertain, define experiments instead of committing to full implementation:

```
Experiment: {e.g. "Does AI-assisted input actually improve user completion rates?"}
  Hypothesis: {specific, falsifiable — e.g. "Adding AI suggestions to the input form will reduce average completion time by 30%"}
  Minimum Viable Test: {the smallest possible implementation that tests the hypothesis}
  Success Metric: {what you will measure and what threshold constitutes success}
  Time-Box: {maximum time to invest before evaluating — e.g. "3 days of development, 2 weeks of observation"}
  Kill Criteria: {what result means you stop and try a different approach}
  Rollback Plan: {how to cleanly remove the experiment if it fails}
```

**When to experiment vs. commit**: Experiment when the improvement is high-effort AND the impact is uncertain. Commit when the improvement is well-understood (bug fix, parity feature, user-validated need with clear solution).

---

### §X.3. R&D ROADMAP DELIVERABLE

```yaml
R&D ROADMAP — {App Name}

EXISTING FEATURE HEALTH:
  At current standard:       {N}/{total} ({%})
  Significant drift:         {N}
  Coherence rating:          {HIGH/MED/LOW/CRITICAL}
  Actions: ELEVATE({N}), EVOLVE({N}), CONSOLIDATE({N}), REIMAGINE({N}), DEPRECATE({N})

COMPETITIVE POSITION:
  Strengths:                 {top 3}
  Existing features behind:  {N UPGRADE items}
  Missing features:          {N OPPORTUNITY + DIFFERENTIATOR items}
  Unique strengths:          {N STRENGTH items}

USER-VALIDATED PRIORITIES:
  1. {need} — Sources: {signals}
  2. {need} — Sources: {signals}
  3. {need} — Sources: {signals}

UNIFIED INVENTORY:
  Total items: {N} — Existing({N}, {%}) / New({N}, {%}) / Infrastructure({N}, {%})

PHASES:
  Phase 1: {goal} — {N items} — {effort estimate}
  Phase 2: {goal} — {N items} — {effort estimate}
  Phase 3: {goal} — {N items} — {effort estimate}

EXPERIMENTS: {N} defined — {total time-box}

DEFERRED: {items not being pursued, with rationale}
```

---

## XI. APP POLISHING & RESTRUCTURATION PROTOCOL

> **Transforms an app that has become messy, incoherent, or fragmented** through organic growth back into a unified, intentional product — then polishes it to the quality it deserves. Not just code cleanup — this restructures the whole app: logic, navigation flow, design language, mental model, and codebase.
>
> **The problem**: You build a solid v1, then add features, fix bugs, add more features, refactor one part, add another feature. After enough iterations, the app has more features and fewer bugs — but it no longer feels like *one thing*. Different eras of development coexist. The user's mental model of "how this app works" no longer matches the actual structure.
>
> **Core principle**: You cannot restructure what you do not understand. §XI.0 (comprehension) is mandatory.
>
> **Prerequisite**: §0 + §I classification. Prior audit strongly recommended. If none, do Parts 1–3 first.
>
> **Execution order**: §XI.0 (understand) → §XI.1 (inventory) → §XI.2 (polish passes) → §XI.3 (code restructure) → §XI.4 (architecture) → §XI.5 (quality gates) → §XI.6 (deliverable).

---

### §XI.0. DEEP COMPREHENSION PHASE — MANDATORY BEFORE ANY CHANGE

> **The failure mode of restructuration is not "broke something"** — quality gates catch that. It is "restructured the app into something clean but soulless." This phase prevents it by forcing the auditor to internalize the app as a *product*, not just code.

#### XI.0.1 — Purpose & Identity Internalization

> **Claude execution note**: Fill this by reading the entire codebase first. Do NOT copy §0 — derive these answers from understanding the whole app. Output the completed record to the user and ask: "Does this accurately capture what your app is trying to be?" Adjust based on their response before proceeding.

Answer these before any restructuration:

```yaml
APP COMPREHENSION RECORD:

  Core Purpose:
    # One sentence. Not "it's a React app that..." but "it helps [who] do [what] when [context]"
    # e.g. "It helps Wuthering Waves players decide when to pull by showing their pity status"

  User Mental Model:
    # How does the user think about this app? What's its "shape" to them?
    # e.g. "A personal tracker — 'my pull history, my pity count, my chances'"

  Core Loop:
    # The user's primary repeated interaction — the heartbeat
    # e.g. "Log a pull → see updated pity → check probability → decide to pull again"

  Emotional Contract:
    # What the user feels when the app works well
    # e.g. "In control. Informed. Like they have an edge."

  Design Personality:
    # If this app were a person, how would it talk?
    # e.g. "Confident and precise, like a trusted advisor who knows the game inside-out"

  Best-in-App Standard:
    # Which part of this app is the best? This is the target — everything else rises to this level.
    # e.g. "The main dashboard is excellent. The settings page feels like a different app."

  Growth Archaeology:
    # Reconstruct the probable development timeline from code evidence:
    # - Which features were built first? (simpler patterns, older conventions)
    # - Which were added later? (newer patterns, sometimes better, sometimes hastier)
    # - Where did the developer change their mind mid-implementation?
    # - Where did a quick fix become permanent?
    # This is not judgment — it's understanding. Every "messy" part has a history.
```

**This record is the North Star.** Every §XI decision is tested against it: "Does this change make the app more like what's described here, or less?"

#### XI.0.2 — Coherence Fracture Analysis

> **Claude execution note**: This is the diagnostic that drives all of §XI.2 Pass 1.5. Be thorough here — every fracture you miss will survive restructuration. Present fractures to the user grouped by type and ask for confirmation before proceeding to fixes.

Identify *exactly* where and how coherence broke down. Each fracture becomes a restructuration task in Pass 1.5.

**Five fracture types to map:**

```yaml
LOGIC FRACTURES — the app's internal logic contradicts itself
  L-{N}:
    Where:     {features/flows involved}
    History:   {how it probably happened}
    Impact:    {what the user experiences — confusion, distrust, workaround}
    Example:   {specific instance — e.g. "Settings has 'metric units' toggle but workout
               log uses hardcoded imperial. Built before settings existed."}

FLOW FRACTURES — the user's journey hits seams
  F-{N}:
    Where:     {navigation paths/transitions}
    History:   {e.g. "feature added as new page instead of integrated into existing flow"}
    Impact:    {disorientation, dead ends, unexpected jumps}

DESIGN FRACTURES — different visual eras coexist
  D-{N}:
    Era A:     {visual conventions of the older part}
    Era B:     {visual conventions of the newer part}
    Boundary:  {where the user crosses from one era to the other}

CONVENTION FRACTURES — same problem solved differently in different places
  C-{N}:
    Pattern A: {how it's done here} — used in: {list}
    Pattern B: {how it's done there} — used in: {list}
    Canonical: {which one should win, and why}

MENTAL MODEL FRACTURES — the app's conceptual model is inconsistent
  M-{N}:
    Model A:   {what this part implies about how things work}
    Model B:   {what that part implies — contradicting Model A}
    Example:   {e.g. "Inventory treats items as flat list with tags. Crafting treats
               them as tree with categories. Two mental models of the same data."}
```

#### XI.0.3 — Unified Vision Statement

> **Claude execution note**: Write this and present it to the user. This becomes the North Star for every change in §XI. Reference it explicitly when making major decisions. If the user revises it, update all downstream work.

Write a single paragraph describing what this app should feel like when restructuration is complete. Not a feature list — a product description.

```yaml
UNIFIED VISION — {App Name}:
  # A paragraph describing the app as it should be. What it feels like to use.
  # How it flows. What its personality is. What makes it coherent.
  #
  # e.g. "Whispering Wishes feels like a single, confident tool built by someone
  # who plays daily. Every screen speaks the same visual language. The user never
  # thinks about where things are — navigation mirrors how players think about
  # gacha (banners → history → pity → probability). Every interaction gives
  # immediate feedback. The import flow matches the dashboard's quality. A new
  # user understands it in 30 seconds. A power user never hits a wall."
```

**Reference this vision explicitly in every major restructuration decision.** When in doubt: "Does this change bring us closer?"

---

### §XI.1. PRE-POLISH INVENTORY

#### XI.1.1 — Current State & Quality Target

```yaml
App State at Polish Start:
  Version:              {from §0}
  Outstanding CRITICAL: {count — must fix BEFORE polish begins}
  Outstanding HIGH:     {count — fixed in Pass 1}
  Debt Zones:           {3–5 worst areas: technical, design, and code}

Quality Baseline → Target (rate 1–5):
  Correctness:    {__}/5 → {__}/5  # e.g. "all CRITICAL/HIGH fixed, test vectors passing"
  Robustness:     {__}/5 → {__}/5  # e.g. "every async op has error handling"
  Performance:    {__}/5 → {__}/5  # e.g. "LCP < 2s, no jank on core interactions"
  Visual Polish:  {__}/5 → {__}/5  # e.g. "consistent tokens throughout, smooth transitions"
  Code Quality:   {__}/5 → {__}/5  # e.g. "zero dead code, consistent naming"
  UX Clarity:     {__}/5 → {__}/5  # e.g. "zero-doc onboarding, clear empty states"
  Accessibility:  {__}/5 → {__}/5  # e.g. "full WCAG 2.1 AA, keyboard-navigable"
```

#### XI.1.2 — Feature Preservation Ledger (Refresh)

Refresh from prior audit or build now. **Every named feature** gets an entry:

```yaml
Feature: {name}
  Status:           WORKING / PARTIALLY WORKING / BROKEN
  Tested:           YES / NO
  Polish priority:  HIGH / MEDIUM / LOW / SKIP
  Restructure:      YES ({reason}) / NO
  Risk during work: HIGH (shared state) / MEDIUM (complex) / LOW (isolated)
```

**The ledger is the contract**: No WORKING feature may degrade during polish. Every pass ends with ledger verification.

---

### §XI.2. SYSTEMATIC POLISH PASSES

> Passes are ordered foundational → cosmetic. Surface polish on a broken foundation is waste. Each pass has a single focus and a verification step.
>
> **Claude execution note**: Do NOT attempt all passes in one response. Work through one pass at a time, verify, then proceed. **Pass 1.5 is the most important** — it's where coherence is restored. Passes 2–6 are standard polish. If the user is impatient, Passes 0 + 1 + 1.5 are the minimum viable restructuration.

#### Pass 0 — Critical Fix Pass *(mandatory if outstanding CRITICAL findings exist)*

**Scope**: Fix every CRITICAL-severity finding from the audit. Nothing else.
**Why first**: A CRITICAL finding means the app produces wrong results, loses data, or has an exploitable security hole. Polishing an app that is fundamentally broken is performative.

```
For each CRITICAL finding:
  Finding ID:     F-{XXX}
  Fix:            {specific code change}
  Verification:   {how to confirm the fix works — test vector, manual check, or automated test}
  Regression check: {which features in the ledger could be affected — verify each one}
```

**Exit criteria**: Zero CRITICAL findings. Feature Preservation Ledger re-verified.

#### Pass 1 — Structural Integrity Pass

**Scope**: Fix HIGH-severity findings. Resolve data integrity issues. Ensure every feature works correctly under normal conditions.

**Checklist**:
- [ ] All HIGH findings from audit resolved
- [ ] All domain rules verified against §0 — every formula produces the correct output
- [ ] All state transitions are clean — no orphaned state, no zombie listeners, no stale closures
- [ ] All persistence operations are safe — write-read round-trip verified, quota handling confirmed
- [ ] All error paths are handled — no uncaught exceptions in any user-reachable path
- [ ] Feature Preservation Ledger re-verified — every WORKING feature still works

**Exit criteria**: App is *correct and robust* under normal usage. Not yet polished, not yet restructured — but trustworthy.

#### Pass 1.5 — Holistic Coherence Restructuration *(the core of §XI — driven by the Fracture Map)*

> **Claude execution note**: This is where the real restructuration happens. Work through fractures one at a time, verifying after each. When modifying app flow or mental model, explain your reasoning to the user first — these are high-impact changes.

**Scope**: Heal every fracture from §XI.0.2. Operates at the *app level* — changes how features relate, how the user moves through the app, how the conceptual model works.

**Why before visual polish**: Visual coherence is impossible on a fragmented foundation.

**1.5a — Logic Fracture Healing**: For each L-{N} — determine canonical logic, make specific changes, verify both halves now agree.

**1.5b — Flow Restructuration**: Map current navigation vs. ideal navigation (based on user mental model from §XI.0.1). For each seam where the user feels like they "left the app":
```yaml
Change F-{N}:
  Current:    {what happens now}
  Target:     {what should happen — tied to user mental model}
  Rationale:  {from Unified Vision}
  Complexity: LOW / MEDIUM / HIGH
```

**1.5c — Convention Unification**: For each C-{N} — choose canonical pattern, list every instance to update, assess migration risk.

**1.5d — Mental Model Alignment**: For each M-{N} — choose single canonical model, identify which features change their conceptual approach, assess user disorientation risk.

**1.5e — Design Era Unification**: For each D-{N} — identify target era (current/best), list every element to update.

**Exit criteria**: A user can navigate the entire app without hitting a seam. Test by narrating a user journey aloud — if you say "and here the pattern changes," the pass is not complete.

#### Pass 2 — Visual Coherence Pass

**Scope**: Make the design system coherent — not "prettier," but *consistent*.

- **2a — Design Token Consolidation**: For each one-off value → map to nearest token or document as intentional exception.
- **2b — Component Variant Unification**: List all instances of each component type → unify variants that should match.
- **2c — Color System**: Every color mapped to a token or flagged as rogue. Theme completeness. Contrast ratios verified.
- **2d — Typography**: Sizes, weights, line-heights mapped to a type scale. One style per heading level.
- **2e — Spacing Rhythm**: Vertical rhythm verified. Horizontal alignment verified.

**Exit criteria**: Every visual decision traceable to a token or an intentional exception. The app looks designed as a system.

#### Pass 3 — Interaction Polish Pass

**Scope**: Make every interaction feel responsive, intentional, and complete.

- **3a — State Change Communication**: Every action → visible feedback within 100ms. Loading/success/failure states for all async ops. Smooth transitions between states.
- **3b — Transition & Motion**: Consistent durations and easing curves. Logical spatial origins. `prefers-reduced-motion` respected. Motion budget ≤2–3 simultaneous animations per view.
- **3c — Empty & Edge States**: Designed empty states (not blank). Helpful error messages (not generic). Skeleton loaders matching content shape. Intentional overflow behavior.
- **3d — Micro-Interactions**: Button press feedback. Styled focus rings. Hover states with cursor changes. Distinct selection states.

**Exit criteria**: Zero moments of "that felt unfinished" when moving through the app.

#### Pass 4 — Copy & Content Polish Pass

**Scope**: Every word is clear, consistent, and matches the app's voice.

- **4a — Terminology Unification**: One word per concept — no synonyms for the same thing.
- **4b — Voice Alignment**: Every label, tooltip, error, and empty state matches the brand voice guide.
- **4c — Microcopy Optimization**: Specific CTAs ("Save your changes" not "Save"). Error messages that tell users what to do. Tooltips that add information. Confirmation dialogs that explain consequences.

**Exit criteria**: The interface reads as if written by someone who understood the user.

#### Pass 5 — Performance Polish Pass

**Scope**: Make the app feel fast.

- Render jank eliminated on core interactions
- Expensive computations memoized or debounced
- Assets optimized (format, dimensions, lazy loading)
- Startup optimized — critical path minimized, non-critical deferred
- Perceived performance: optimistic UI, skeleton screens, progressive loading

**Exit criteria**: Common operations feel instant. Complex operations feel responsive. No interaction takes >100ms to acknowledge.

#### Pass 6 — Accessibility Polish Pass

**Scope**: Beyond compliance — genuinely usable by everyone.

- Keyboard navigation intuitive — tab order follows visual order, focus trapping in modals
- Screen reader coherent — landmarks, headings, ARIA labels tell a complete story
- Color never the only information carrier
- Touch targets ≥44×44px on mobile
- Reduced motion fully respected

**Exit criteria**: Keyboard-only and screen reader users can accomplish every task without confusion.

---

### §XI.3. CODEBASE RESTRUCTURATION

> Pass 1.5 restructured the app's logic, flow, and mental model. This section ensures the *code* reflects that coherence. Code organized differently from the product's conceptual model creates a maintenance trap.
>
> **Claude execution note**: This is where you actually move, rename, extract, and reorganize code. Work in small, verifiable steps. Never restructure more than one module between verifications. If the app is a single file, extract outward from safest (constants) to riskiest (state).

#### XI.3.1 — Principles

1. **Code structure mirrors app structure.** If the user thinks of three main areas, the code has three main modules. Module names match feature names.
2. **Never restructure and add features simultaneously.** Regressions become undetectable.
3. **Every step independently verifiable.** Step 3 can be reverted without losing steps 1–2.
4. **Restructuring preserves behavior exactly.** Behavioral changes belong in §X.
5. **Clarity over cleverness.** A new developer reading the file tree should guess what each module does.

#### XI.3.2 — Dead Code Elimination

For each dead code block: location, type (UNREACHABLE / COMMENTED-OUT / UNUSED EXPORT / VESTIGIAL), confidence (CERTAIN / HIGH / MEDIUM), and removal verification.

**Rule**: Remove CERTAIN-confidence first. HIGH after developer confirmation. MEDIUM flagged and left.

#### XI.3.3 — Module & Component Restructuring

```yaml
Current Structure: {e.g. "Single App.jsx, 4200 lines, all components inline"}
Code-Concept Alignment: {how well current structure matches the product's conceptual model}

Target Structure:
  # Organized by product domain, not technical concern:
  # /features/banner-tracker/  — everything for tracking banners
  # /features/pull-history/    — everything for pull logging
  # /shared/                   — tokens, common components, utilities
  # /app/                      — root layout, navigation, global state
  Rationale: {why this structure — tied to user mental model from §XI.0.1}

Extraction Sequence:
  # Safest first: constants → pure utils → hooks → leaf components → 
  # composite components (bottom-up) → state management (last)
  Step 1: {what} — Risk: {L/M/H} — Verification: {check}
  Step 2: {what} — Risk: {L/M/H} — Depends on: {step 1}
```

#### XI.3.4 — State Architecture Restructuring

```yaml
Current: {e.g. "47 useState calls, 12 levels of prop-drilling, 3 context providers"}
Target:  {e.g. "Domain state in useReducer + context, UI state local, derived via useMemo"}

Migration Rules:
  - Never change state shape and consumers simultaneously
  - Introduce new system alongside old, migrate consumers one at a time, remove old
  - Every intermediate state (old + new coexisting) must be fully functional

Steps:
  Step 1: {migration} — Affected: {components} — Verify: {output unchanged}
  Step 2: ...
```

#### XI.3.5 — Dependency & Import Restructuring

Resolution order (safest first): Remove unused imports → Standardize import order → Break circular imports (verify each) → Replace heavy dependencies (behavioral verification required).

#### XI.3.6 — API & Interface Normalization

For inconsistent internal APIs (component props, function signatures, hook interfaces):
```yaml
Inconsistency: {e.g. "onClose vs handleClose vs dismiss — all mean the same thing"}
  Standard:    {canonical name — e.g. "onClose"}
  Instances:   {locations to update}
```

**Order**: Rename → Reshape → Remove. Each step independently verified.

---

### §XI.4. ARCHITECTURE EVOLUTION

> For apps that need to grow beyond their current architecture. This is not restructuring (which preserves behavior) — this is *evolving* the architecture to support new capabilities.

```yaml
Architecture Evolution Plan:
  Current:     {from §0 — e.g. "Single-file PWA, localStorage, CDN React, no auth"}
  Target:      {e.g. "Multi-file, Supabase backend, Vite build, auth + multi-user"}
  Horizon:     {timeline}

  Phase A — {name}:
    Prerequisite: {what must be true first}
    Deliverable:  {what the app can do after this that it couldn't before}
    Risk:         {data migration, feature regression, user disruption}
    Rollback:     {how to revert if it fails}
  Phase B — ...

Data Migration (when storage changes):
  Current schema:    {data shape in current system}
  Target schema:     {data shape in new system}
  Strategy:          {step-by-step migration path}
  Edge cases:        {corrupted data, declined migration, multi-device reconciliation}
  Fallback:          {old system continues working if migration fails}
```

---

### §XI.5. QUALITY GATES

#### Per-Step (after every individual change):
```
[ ] Feature Preservation Ledger: all WORKING features still work
[ ] No unintended visual changes outside this step's scope
[ ] Console: no new errors or warnings
[ ] Keyboard navigation still works
[ ] No new performance jank
```

#### Per-Pass (after completing an entire pass):
```
[ ] All per-step verifications passed
[ ] Target dimension improved (or held) — no other dimension degraded
[ ] Commit/checkpoint created — pass is independently revertible
```

#### Final Gate (§XI complete):

```
[ ] Every Quality Target from §XI.1.1 is met or exceeded
[ ] Feature Preservation Ledger: 100% of WORKING features still working
[ ] Zero CRITICAL or HIGH findings remain

COHERENCE VERIFICATION:
[ ] Every Logic Fracture from §XI.0.2 is healed — unified logic throughout
[ ] Every Flow Fracture is healed — user journey has no seams
[ ] Every Convention Fracture is healed — one pattern for each problem type
[ ] Every Mental Model Fracture is healed — one conceptual model throughout
[ ] Every Design Fracture is healed — one visual era throughout
[ ] The Unified Vision Statement (§XI.0.3) accurately describes the app as it now exists

POLISH VERIFICATION:
[ ] Design system is internally consistent — no rogue tokens, no orphaned styles
[ ] Copy is consistent — no terminology conflicts, no voice violations
[ ] Accessibility baseline met — WCAG 2.1 AA throughout
[ ] Performance baseline met — all core interactions within budget
[ ] Code quality baseline met — no dead code, consistent patterns, clear naming
[ ] Code structure mirrors product structure — a developer can navigate the code by thinking about features

HOLISTIC CHECK — the most important test:
[ ] A new user opening this app for the first time experiences ONE product, not a patchwork
[ ] A developer opening this codebase for the first time can understand its organization in 5 minutes
[ ] The app's best feature and its worst feature are now within one quality tier of each other
[ ] The developer looks at the result and says: "This is still my app — but the version I always wanted it to be"
```

---

### §XI.6. POLISH & RESTRUCTURATION DELIVERABLE

```yaml
POLISH & RESTRUCTURATION REPORT — {App Name}

COMPREHENSION:
  Purpose:    {one sentence from §XI.0.1}
  Vision:     {one sentence from §XI.0.3}
  Root cause: {key insight about how fragmentation happened}

COHERENCE HEALED:
  Logic({N}) | Flow({N}) | Design({N}) | Convention({N}) | Mental Model({N})
  Coherence: {before} → {after}

QUALITY: Baseline → Target → Achieved
  Correctness:   {__} → {__} → {__}
  Robustness:    {__} → {__} → {__}
  Performance:   {__} → {__} → {__}
  Visual Polish: {__} → {__} → {__}
  Code Quality:  {__} → {__} → {__}
  UX Clarity:    {__} → {__} → {__}
  Accessibility: {__} → {__} → {__}

PASSES:
  0-Critical({N} fixes) | 1-Structural({N}) | 1.5-Coherence({N} fractures)
  2-Visual({N}) | 3-Interaction({N}) | 4-Copy({N}) | 5-Perf({N}) | 6-A11y({N})

CODE RESTRUCTURING:
  Dead code removed: {N lines} | Modules extracted: {N}
  Code-concept alignment: {before → after}
  State simplified: {describe} | APIs normalized: {N}

ARCHITECTURE: {phases completed, data migrated, next phase}

FEATURES: {N}/{N} verified | {N} regressions caught (resolved) | {N} improved

VISION CHECK: Does the app match the Unified Vision? {YES / PARTIALLY — gaps: ...}
```
