#!/bin/bash
mv client/client.key client/client.pkcs1.key
openssl pkcs8 -topk8 -inform PEM -outform PEM -nocrypt -in client/client.pkcs1.key -out client/client.key
