#!/bin/sh

if [ -d "$APP_BASE/maven/repository" ]; then
  echo "Copying offline repo"
  cp -rf $APP_BASE/maven/repository/* $APP_BASE/system/
fi

if [ -d "$APP_BASE/maven/kars" ]; then
  ln -s $APP_BASE/maven/kars/* $i $APP_BASE/deploy/
fi
