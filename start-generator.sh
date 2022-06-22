#!/bin/bash

#docker-compose -f ./setup/load-generator/docker-compose-load-generator.yml \
#  down

#docker-compose -f ./setup/load-generator/docker-compose-load-generator.yml \
#  up --build --force-recreate

#ENV_FILE="./setup/.env"

#if [ -f $ENV_FILE ]; then
#  while read line; do
#    if [[ ! "$line" =~ ^\# ]] && [[ "$line" =~ .*= ]]; then
#      echo "export ${line//[$'\r\n']}"
#      export ${line//[$'\r\n']}
#    fi
#  done <$ENV_FILE
#fi

#echo $TEST_RUNNERS_INCREASING_LOAD_RUNNER_ACTIVE
#echo $PWD/setup/load-generator/load-generator.env
# export HOLDER_ACAPY_URLS="http://`docker network inspect aries-load-test | jq '.[].Containers |  to_entries[].value | select(.Name|test("^agents_holder-acapy_.")) | .IPv4Address' -r | paste -sd, - | sed 's/\/[0-9]*/:10010/g' | sed 's/,/, http:\/\//g'`"

docker stop load-generator-1
docker rm load-generator-1
docker build -t aries-load-generator .
docker run -it --rm --name load-generator-1 \
  -v $PWD/setup/load-generator/cert:/grpc \
  --network aries-load-test \
  --env-file=$PWD/.generator-env \
  -p 8080:8080 \
  --add-host host.docker.internal:host-gateway \
  --log-driver "json-file" \
  aries-load-generator