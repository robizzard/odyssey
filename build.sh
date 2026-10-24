#!/bin/bash

set -eou pipefail


source adb_settings.sh

chmod +x gradlew
./gradlew --warning-mode all assembleDebug
adb_phone install -r app/build/outputs/apk/debug/app-debug.apk
