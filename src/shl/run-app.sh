#!/bin/bash
DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd "${DIR}"

java -Dapp.name="Tevis" -Dlog4j2.contextSelector="org.apache.logging.log4j.core.async.AsyncLoggerContextSelector" -cp config/:bin/*:lib/* com.dataspark.networkds.Application 2>&1
