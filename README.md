## Sirinium — расписание 

<img src="images/aff9fa41-2c67-4ca2-868a-bdb0e83b4e81.webp" alt="Иконка/баннер" width="220" />

<a href="https://www.rustore.ru/catalog/app/com.dlab.sirinium" target="_blank">
  <img src="https://upload.wikimedia.org/wikipedia/commons/8/84/RuStore_logo.svg" alt="Доступно в RuStore" height="40" />
  
</a>

Нативное Android‑приложение для просмотра расписания учебных групп и преподавателей b виджетами «Следующая пара».

### Возможности
- Поиск и выбор группы или преподавателя
- Просмотр расписания по дням/неделям
- Виджеты 2×2 и 4×1: показывают ближайшую будущую пару
- Автообновление и уведомления перед началом пары
- Светлая/тёмная/системная тема

### Скриншоты
Файлы из каталога `images/`:

<img src="images/photo_5316894093831438084_w.jpg" alt="Фото 8084" width="320" />
<img src="images/photo_5316894093831438085_w.jpg" alt="Фото 8085" width="320" />
<img src="images/photo_5316894093831438086_w.jpg" alt="Фото 8086" width="320" />
<img src="images/photo_5316894093831438087_w.jpg" alt="Фото 8087" width="320" />
<img src="images/photo_5316894093831438088_w.jpg" alt="Фото 8088" width="320" />
<img src="images/photo_5316894093831438089_w.jpg" alt="Фото 8089" width="320" />
<img src="images/photo_5316894093831438090_w.jpg" alt="Фото 8090" width="320" />
<img src="images/photo_5316894093831438091_w.jpg" alt="Фото 8091" width="320" />


### Виджеты
- 2×2 — крупный номер аудитории с цветовой индикацией типа занятия
- 4×1 — день недели, дата, предмет, время и аудитория
- Всегда показывают ближайшую будущую пару для выбранной группы или преподавателя (если на сегодня пары закончились, показывается первая пара ближайшего дня)

### Сборка и запуск
1. Откройте проект в Android Studio
2. Установите Android SDK 24+ (целевой SDK 35)
3. Запустите конфигурацию `app` (debug/release)

### Настройки API
По умолчанию приложение обращается к API:
- `GET /api/groups` — список групп
- `GET /api/teachers` — словарь ID→ФИО
- `GET /api/schedule?group={КXXXX-..}&week={offset}` — расписание группы
- `GET /api/teacherschedule?id={teacherId}&week={offset}` — расписание преподавателя

Базовый URL настраивается в `RetrofitClient`.

### Разрешения
- INTERNET — загрузка данных расписания
- POST_NOTIFICATIONS — уведомления (Android 13+)
- RECEIVE_BOOT_COMPLETED — восстановление автообновлений после перезагрузки

### Технологии
- Kotlin, Jetpack Compose, Material 3
- WorkManager, Room, DataStore
- Retrofit/OkHttp
- App Widgets

### Вклад и ошибки
PR и Issues приветствуются. Если нашли баг:
- опишите шаги воспроизведения;
- приложите логи (Logcat) и скрин/видео.

### Лицензия
MIT.

## Разработчики
https://github.com/alphadvolj
https://github.com/nnqnn

