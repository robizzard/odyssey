#!/bin/bash
zip -r odyssey-current.zip app AUTHORS.md build.gradle CHANGELOG.md CONTRIBUTING.md fastlane gradle gradle.properties gradlew gradlew.bat images LICENSE Privacy Policy.md README.md settings.gradle  \
    -x '*/__pycache__/*' '*/.pytest_cache/*' '*.pyc'
echo
echo Final zip:
echo
ls -lh odyssey-current.zip
echo
