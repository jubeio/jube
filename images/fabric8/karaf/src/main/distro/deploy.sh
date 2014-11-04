#!/bin/sh

if [ -d "$APP_DIR/repository" ]; then
  echo "Copying offline repo"
  cp -rf $APP_DIR/repository/* $APP_DIR/system/
fi

if [ -d "$APP_DIR/kars" ]; then
  ln -s $APP_DIR/kars/* $i $APP_DIR/deploy/
fi