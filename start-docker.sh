#!/bin/sh

SCRIPT=$(find . -type f -name mobile-token-proxy)
exec $SCRIPT \
  $HMRC_CONFIG
