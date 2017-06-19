#!/bin/bash
#ant clean-java build-java

#ant hstore-prepare -Dproject=$1 -Dhosts="localhost:0:0"

ant hstore-benchmark -Dproject=$1 -Dclient.threads_per_host=1 -Dglobal.sstore=true -Dglobal.sstore_scheduler=true -Dclient.blocking=false -Dclient.warmup=10000 -Dclient.duration=300000 -Dclient.txnrate=$2 $3
