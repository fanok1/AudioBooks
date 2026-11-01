# AudioBooks — правила для агентов

Android-приложение (Java 8, Gradle 8.13, JDK 17). Как устроен движок источников и как добавить сайт —
`docs/howto.md` («Adding an audiobook site»); план и статус — `tasks/plan.md` §12, `tasks/todo.md`,
`tasks/open-issues.md`. Сведения о машине и эмуляторе — `C:\work\audiobook\readme.md`.

## Сборка и тесты

Окружение загружать в той же команде PowerShell, что и сборку (переменные не сохраняются между вызовами). Удобнее всего
обёртка `g.ps1`: загружает JDK/SDK, запускает Gradle без демона (`--no-daemon`, память освобождается сразу), пишет
полный лог в `build/g.log` и печатает только итоговые строки (ошибки, сводку тестов, BUILD):

```powershell
powershell -NoProfile -File C:/Users/user/AppData/Local/audiobook-audit-tools/g.ps1 C:/work2000/AudioBooks :app:testDebugUnitTest
```

или напрямую:

```powershell
. "C:/Users/user/AppData/Local/audiobook-audit-tools/environment.ps1"; Set-Location C:/work2000/AudioBooks; .\gradlew.bat :app:testDebugUnitTest --console=plain -q
```

- Одна сборка Gradle за раз. Не запускать Gradle параллельно в разных копиях проекта (git worktree, копии во временных
  папках): каждая поднимает свой демон (`-Xmx1536m`) и свои процессы R8/lint. После прямого запуска —
  `.\gradlew.bat --stop`. Собирать пачкой: один прогон, когда законченный кусок готов, а не после каждой правки.
- Не запускать `gradlew clean`: он стирает собранные APK и улики. `assembleRelease` и так очищает
  `app/build/outputs/apk/release` целиком: нужные APK хранить в `test-apk/` (git их не видит).
- Release подписывается ключом из `keystore.properties` (не коммитится, пароли не читать и не выводить).

## Эмулятор: как не уронить систему

**История.** Эмулятор (`audiobook_audit_api34`, `emulator-5554`) ускоряется драйвером ядра Google **AEHD 2.2**
(`aehd.sys`, служба `aehd`). Без ускорения сам процесс эмулятора падал (`0xC0000005` в `qemu-system-x86_64`), с AEHD он
работает, но драйвер ядра делает сбой системным:
- в октябрьских аудитах (`C:\work\audiobook\notes\worker-rules*.md`) ускоренный эмулятор уже **ронял ОС, когда с ним
  работали параллельно**; с тех пор правило: рабочие агенты не запускают эмулятор и не вызывают `adb`, проверку на
  устройстве делает только оркестратор, один, одной последовательной фазой в конце, когда больше ничего не работает
  (`progress-3-issues.md`);
- 30.10.2026 в 18:11 это повторилось: синий экран `0x3B SYSTEM_SERVICE_EXCEPTION` (`c0000005`, нарушение доступа в коде
  ядра; дамп `C:\Windows\MEMORY.DMP`), когда агенты гоняли эмулятор через `adb`, а рядом шли другие workflow с
  несколькими сборками Gradle и живыми проверками сайтов.

Правила обязательны для основной сессии, субагентов и workflow.

1. **Субагенты и workflow не запускают эмулятор и не вызывают `adb`** — писать это в каждый бриф. Проверку на
   устройстве делает только основная сессия сама, и только когда владелец её попросил.
2. **Одна фаза на устройстве, в конце, без соседей.** Перед ней дождаться окончания всех агентов, workflow, сборок и
   живых проверок; остановить демоны Gradle (`.\gradlew.bat --stop`). Пока работает эмулятор — никаких сборок Gradle,
   lint, R8, бенчмарков и фоновых агентов. Нужна сборка — сначала остановить эмулятор (п. 6).
3. **Только один эмулятор.** Сначала проверить, что уже запущено:
   ```powershell
   . "C:/Users/user/AppData/Local/audiobook-audit-tools/environment.ps1"; adb devices -l; Get-Process qemu-system*, emulator* -ErrorAction SilentlyContinue
   ```
   Если `emulator-5554` есть — использовать его. Второй экземпляр, второй AVD, второй порт не запускать никогда.
4. **Запуск — только штатно**, с проверенными параметрами (ускорение on, 2 ГБ, 2 ядра, без окна и звука):
   ```powershell
   powershell -NoProfile -File C:/Users/user/AppData/Local/audiobook-audit-tools/emu.ps1 start
   powershell -NoProfile -File C:/Users/user/AppData/Local/audiobook-audit-tools/emu.ps1 wait
   ```
   `start` вызывает `start-audit-emulator.ps1` (проверяет ускорение и переиспользует `emulator-5554`), `wait` ждёт
   `sys.boot_completed` не дольше 8 минут и отключает анимации. Не менять `-memory`, `-cores`, `-gpu`, не использовать
   `-accel off`, `-wipe-data`, снапшоты, окно, звук. Если `emulator -accel-check` не сообщает, что AEHD работает, —
   эмулятор не запускать, сообщить владельцу.
5. **Шаги `emu.ps1` для этого репозитория:** только `start`, `wait`, `status`, `stop`. Шаг `smoke` написан для старого
   репозитория `C:\work\audiobook`: он удаляет приложение и ставит пакет `com.fanok.audiobooks`, а здесь пакет
   `com.fanok.books`. Шаг `connected` (`connectedDebugAndroidTest`) ставит debug-сборку, что требует удаления
   приложения, — только с разрешения владельца и с `-Dir C:/work2000/AudioBooks`.
6. **Короткий сеанс и обязательная остановка.** Ограниченный список шагов, без бесконечных циклов скриншотов и
   `screenrecord`; скриншоты — `adb shell screencap -p /sdcard/x.png` и `adb pull` (PowerShell 5.1 портит двоичный
   вывод через `>`). В конце — `emu.ps1 stop` (`adb -s emulator-5554 emu kill`) и проверить, что `emulator-5554`
   пропал из `adb devices`. Процесс `qemu-system-*` не убивать принудительно, если он не завис.
7. **Данные приложения на эмуляторе не трогать:** без `adb uninstall`, `pm clear`, `-wipe-data`. На эмуляторе стоит
   release-сборка с ключом владельца: ставить только release APK этим ключом (`adb install -r` сохраняет данные).
   Debug APK и `connectedDebugAndroidTest` требуют удаления приложения — только с разрешения владельца.
8. **Если машина упала:** эмулятор снова не запускать; посмотреть причину
   (`Get-WinEvent -FilterHashtable @{LogName='System'; Id=1001,41} -MaxEvents 5`) и сообщить владельцу.

AEHD снимается с поддержки Google 31.12.2026; рекомендованная замена — Windows Hypervisor Platform (WHPX). Переход
требует прав администратора и перезагрузки — только по решению владельца. Подробности о машине, AVD и восстановлении
эмулятора — `C:\work\audiobook\readme.md` («Running and managing the emulator», «Why the emulator crashed»).

## Git

Коммит и push — только по просьбе владельца. Основная ветка — `master`, слияния веток — `--no-ff` с сообщением
`Merge branch '<ветка>': <что вошло>`.
