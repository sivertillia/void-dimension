# The Void Dimension — відновлений проєкт розробки

Це декомпільований і зібраний у повноцінний Gradle-проєкт мод `void_dimension` v1.0.0
(автор FinnK42) для **Minecraft 1.21.1 / NeoForge 21.1.234**.

## Що всередині
- `src/main/java/com/finnk42/void_dimension/` — декомпільований Java-код (5 класів)
- `src/main/resources/` — оригінальні assets, data (вимір, біом, рецепти, ачівки), моделі, текстури, `META-INF/neoforge.mods.toml`
- `build.gradle`, `gradle.properties`, `settings.gradle` + gradle wrapper — офіційний NeoForge MDK

## Як почати
1. Розпакувати архів.
2. Відкрити папку як проєкт в **IntelliJ IDEA** (File → Open → обрати папку). Gradle сам підтягне NeoForge та Minecraft.
   Або в терміналі: `./gradlew build` (Linux/Mac) чи `gradlew.bat build` (Windows).
3. Запуск гри для тестів: `./gradlew runClient`.
4. Готовий jar з'явиться в `build/libs/`.

## Важливо про якість коду
Декомпіляція чиста (код не був обфускований), але CFR додав багато зайвих
явних приведень типів, напр. `(String)"active"`, `(Property)ACTIVE`.
Вони компілюються, але захаращують код. У IntelliJ прибираються в один клік:
**Code → Inspect Code** (або **Analyze → Code Cleanup**) → інспекція
«Redundant type cast» → Fix all. Коментарі/Javadoc не відновлюються — їх у байткоді немає.
