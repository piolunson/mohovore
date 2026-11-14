# Homovore 3

Połączony zbiór funkcji z **Homovore 1** (`homovore-72a4151c-1.2.1.jar`) i **Homovore 2**
(`homovore-2-1.0.0_(3).jar`), zbudowany na zdeobfuskowanych źródłach z `decompiled-named/homovore1`
i `decompiled-named/homovore2`.

## Reguły scalania

1. **Baza = Homovore 1.** Cały framework h1 (event bus, `Setting`, GUI, menedżery, mixiny,
   moduły) jest zachowany bez zmian w paczce `dev.leonetic`.
2. **Powtórki → Homovore 1.** Moduł h2 o tej samej funkcji co w h1 **nie jest rejestrowany**
   w rejestrze h2 (`dev.leonetic.v2.feature.module.ModuleManager`). Jego klasa zostaje w drzewie,
   bo framework h2 (mixin/GUI) odwołuje się do niej statycznie.
3. **PistonCrystal → Homovore 2.** Plik `dev/leonetic/features/modules/combat/PistonCrystalModule.java`
   (h1) usunięty wraz z importem i rejestracją; aktywny jest
   `dev.leonetic.v2.feature.module.impl.PistonCrystalModule` (h2).
4. **Kod h2 = paczka `dev.leonetic.v2`.** Przemianowanie paczki (`dev.leonetic` →
   `dev.leonetic.v2`) usuwa kolizje klas i mixinów, bo oba klienty używają tej samej
   paczki `dev.leonetic.mixin`.

## Decyzje o powtórkach

Dokładne odpowiedniki (h2 wyłączony): AutoLog, AutoMine, AutoTotem, AutoTrap, AutoXp,
BreakIndicators, ClickGui, FastUse, Fullbright, Hud, InstantRekit, LogoutSpots, Nametags,
NoRender, NoRotate, Notifications, PearlBlocker, Phase, Scaffold, ShulkerPreview, Sprint,
SwordGap, Tablist, Velocity, ViewModel.

Odpowiedniki po funkcji (h2 wyłączony, h1 zostaje): `AnchorPlacer` → `AutoAnchor`,
`CrystalPlacer` → `AutoCrystal`, `MiddleClick` → `MiddleClickExtra`.

Bliskie, ale uznane za **osobne** funkcje (h2 aktywne): `PopChams` (h1 ma `PopEffects`),
`HotbarRefill` (h1 ma `Replenish`), `AutoElytra` (h1 ma `ElytraFly`/`ElytraAssist`),
`SelfTrap` (h1 ma `Surround`), `Chams`, `KillAura`, `Tracers`, `Trails`, `BetterCam`, `Fixes`,
`Bounce`, `AirPlace`, `BasePlace`, `FallTrap`, `DoubleMine`, `Freecam`, `NoClimb`,
`NoJumpDelay`, `PacketLimits`, `RegearBlocker`, `SoundBlocker`, `AutoEat`, `AntiAfk`,
`KeyAction`, `CustomSounds`, `Server`.

## Zawartość

- `src/main/java/dev/leonetic/**` – 251 plików `.java`, framework i moduły h1
  (55 modułów w rejestrze `ModuleManager`; moduły HUD, np. `ArmorHudModule`, obsługuje
  `HudClientModule`, nie `ModuleManager`)
- `src/main/java/dev/leonetic/v2/**` – 285 plików `.java`, framework h2 + **27 aktywnych modułów**:
  AirPlace, AntiAfk, AutoEat, AutoElytra, BasePlace, BetterCam, Bounce, Chams, CustomSounds,
  DoubleMine, FallTrap, Fixes, Freecam, HotbarRefill, KeyAction, KillAura, NoClimb, NoJumpDelay,
  PacketLimits, PistonCrystal, PopChams, RegearBlocker, SelfTrap, Server, SoundBlocker, Tracers, Trails.
  Pozostałe 28 klas modułów h2 jest w drzewie, ale **nieaktywne** (wyłączone z rejestru).
- `src/main/resources/` – `fabric.mod.json` (id `homovore-3`), `homovore.mixins.json` (h1),
  `homovore-3.mixins.json` (h2, paczka `dev.leonetic.v2.mixin`), `homovore-3.accesswidener`
  (unia wpisów), assety `assets/homovore` i `assets/homovore-2`, cztery zagnieżdżone jary.

## Deobfuskacja (Yarn)

Źródła są **zremapowane na mappings Yarn 1.21.11+build.6**:

1. `tiny-remapper 0.15.0` remapuje oryginalne jary `intermediary -> named`
   (classpath: klient Minecraft 1.21.11 zremapowany `official -> intermediary`),
2. `Vineflower 1.12.0` dekompiluje zremapowane jary do `decompiled-named/`,
3. osobny przebieg poprawia to, czego remapper nie rusza — literały `method_…`/`field_…`
   w `@Inject`/`@At`, pola `@Shadow` (mapowane po klasie docelowej miksinu)
   oraz nazwy klas zagnieżdżonych (`Outer$class_123`).

Efekt: w `homovore3/**/*.java` **nie ma ani jednego** `class_`, `method_` ani `field_`
(0 wystąpień). Przykład: `mc.field_1724.method_5628()` → `mc.player.getId()`,
`class_7274` → `SimpleOption$CyclingCallbacks$ValueSetter`.

Pliki `accesswidener` pozostają w przestrzeni intermediary — tak ma być, AW adresuje klasy
Minecrafta po nazwach intermediary w czasie builda.

Drzewa źródłowe: `decompiled-named/homovore1` i `decompiled-named/homovore2`
(252 i 285 plików `.java`), obok starszych, niezremapowanych `decompiled/` i `remapped/` (jary).

## Ograniczenia (ważne)

- **To nie jest zbudowany ani przetestowany mod.** Brakuje `build.gradle` i zależności
  Fabric/Minecraft (m.in. Fabric API, brigadier, gson), więc mimo nazwanych źródeł
  kompilacja wymaga postawienia projektu Gradle/Loom.
- **Dwa frameworki współistnieją** (h1 w `dev.leonetic`, h2 w `dev.leonetic.v2`) zamiast jednego
  ujednoliconego. Pełny port modułów h2 na API h1 to osobny, duży refaktor — nie został wykonany.
- Deduplikacja działa na poziomie **rejestru modułów**, nie przez usunięcie kodu h2. Część
  mixinów/GUI h2 sięga po wyłączone moduły (np. `NoRender`, `Velocity`, `HudModule`), więc
  uruchomienie takiego builda wymagałoby null-checków albo portu.
- Oba klienty mają własne entrypointy, event busy i GUI (m.in. ten sam domyślny bind ClickGUI),
  a ich mixiny celują w te same klasy Minecrafta — w jednym jarze wymaga to dalszej integracji.
