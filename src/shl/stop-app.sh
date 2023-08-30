#!/bin/bash

PID=$(ps -ef | grep Tevis | grep -v grep | awk '{print $2}')
kill -15 $PID