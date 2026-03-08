# RenPlay

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![API](https://img.shields.io/badge/API-28%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=28)

Android-лаунчер для запуска визуальных новелл на движке [Ren'Py](https://www.renpy.org/). Позволяет запускать игры напрямую из директорий во внутренней памяти устройства.

## Возможности
* Статистика игрового времени.
* Создание ярлыков игр на главном экране.

## Скриншоты
<p align="center">
  <img src="docs/screenshots/1.png" width="30%" />
  <img src="docs/screenshots/2.png" width="30%" />
  <img src="docs/screenshots/3.png" width="30%" />
</p>

## Установка
APK файлы доступны на странице [Releases](https://github.com/Reset171/RenPlay/releases).

## Использование
1. Распакуйте игру на телефон (внутри должна быть папка `game`).
2. В приложении нажмите `+` и выберите эту папку.

## Сборка
Для самостоятельной сборки потребуется JDK 21 и Android SDK (API 34).

1. Клонируйте репозиторий:
   ```bash
   git clone https://github.com/Reset171/RenPlay.git
   ```
2. Создайте файл конфигурации ключей `key.properties` в корне проекта. Для тестовой сборки достаточно указать заглушки:
   ```properties
   keyAlias=debug
   keyPassword=debug
   storePassword=debug
   storeFile=debug.keystore
   ```
3. Запустите сборку:
   ```bash
   ./gradlew assembleDebug
   ```

## Лицензия
Проект распространяется под лицензией [GPLv3](LICENSE). 
В приложении используются сторонние компоненты (SDL2, Python, Ren'Py и др.), их лицензии указаны в разделе "О программе" внутри приложения.